package za.co.neroland.nerocreatures.client;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * CLIENT, creative-debug: an automated, reproducible screenshot pass over the
 * {@code /nerocreatures gallery} scene.
 *
 * <p>{@code /nerocreatures gallery} builds the scene server-side; this harness drives the camera,
 * hides every overlay and writes one PNG per shot via {@link Screenshot#grab}, so the Modrinth
 * gallery can be re-rendered identically after a model or texture change instead of hand-framing
 * twelve shots. The PNGs land in {@code <run dir>/screenshots/nerocreatures/}, which is exactly the
 * folder {@code .github/workflows/modrinth-gallery.yml} mirrors.
 *
 * <p><b>Cross-loader.</b> Loader-agnostic — it touches vanilla client classes only. Each loader wires
 * it from its own client setup: {@link #registerClientCommands(CommandDispatcher)} from the loader's
 * client-command hook (NeoForge/Forge {@code RegisterClientCommandsEvent}, Fabric
 * {@code ClientCommandRegistrationCallback}) and {@link #tick()} from its per-client-tick hook. The
 * command bodies never read the command source, so the registration is generic in the Brigadier
 * source type {@code <S>} and one method serves all three loaders.
 *
 * <p><b>The root is {@code /ncgallery}, and it must not be {@code /nerocreatures}.</b> A client
 * dispatcher root that shares its name with a server command <b>shadows the server subtree</b>: the
 * client parser matches the root, fails on the server-only child ("Incorrect argument for command")
 * and never forwards the command on. Keep the two roots distinct.
 *
 * <h2>Commands</h2>
 *
 * <ul>
 *   <li>{@code /ncgallery capture [time]} — the full pass. Teleports to the fixed origin
 *       {@value #ORIGIN_X}/{@value #ORIGIN_Y}/{@value #ORIGIN_Z}, rebuilds the scene from scratch
 *       ({@code gallery clear} then {@code gallery}), freezes time, weather and clouds, then shoots
 *       the framed shot list.</li>
 *   <li>{@code /ncgallery capture shot <name>} — grab the CURRENT view once, overlays hidden, to
 *       {@code screenshots/nerocreatures/<name>.png}. For a hand-framed money shot.</li>
 * </ul>
 *
 * <p>{@code time} accepts {@code day}/{@code noon}/{@code night}/{@code midnight} or a tick number,
 * and defaults to {@code noon}.
 *
 * <h2>Why a fixed origin in the current world</h2>
 *
 * <p>NeroCreatures ships no dimension of its own — the flat {@code nerospace:capture} backdrop the
 * sibling mod teleports into does not exist here, and depending on another mod for one is out of the
 * question. So the pass stages the scene in whatever dimension the player is in, at a fixed origin
 * <b>{@value #ORIGIN_Y} blocks up</b>: the gallery lays its own pads at the player's feet, so at that
 * height the whole set is a floating platform against open sky and the local terrain is not in the
 * shot. Reproducibility comes from clearing and rebuilding the scene every run rather than from a
 * special world, so a rerun in any world produces the same frames — run it in a superflat creative
 * world for the cleanest backdrop.
 *
 * <p>Outputs are at the game window's native resolution with the HUD, clouds and view-bob suppressed
 * for the duration of the run.
 */
public final class GalleryCaptureHarness {

    /** Ticks to let chunks/entities settle and a fresh frame render before grabbing. */
    private static final int SETTLE_TICKS = 12;

    /** Ticks to wait after teleporting, before building, for the destination chunks to load. */
    private static final int BUILD_WARMUP_TICKS = 120;

    /**
     * Ticks to wait after the build commands before the cleanup sweep. Lets the label-stripping kill
     * finish and its dropped armour stands settle, so the sweep actually catches them.
     */
    private static final int CLEANUP_TICKS = 60;

    /** The fixed staging origin. The gallery builds at the player's feet, so this is the scene origin. */
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Y = 100;
    private static final int ORIGIN_Z = 0;

    /**
     * One framed capture. {@code setup} runs before the warmup (teleport, pin the environment);
     * {@code build} runs after it, once the chunks are loaded (clear, build, strip labels);
     * {@code cleanup} runs after a further {@link #CLEANUP_TICKS} pause (sweep dropped items off the
     * pads); {@code camera}/{@code target} are the pose, and a {@code null} pose means "keep the
     * current view".
     */
    private record Shot(String name, List<String> setup, List<String> build, List<String> cleanup,
            int warmup, Vec3 camera, Vec3 target) {
    }

    private enum Phase { MOVE, WARMUP, CLEANUP, SETTLE, SHOOT }

    private static final Deque<Shot> QUEUE = new ArrayDeque<>();
    private static boolean running;
    private static boolean hudWasHidden;
    private static boolean bobViewWas;
    private static boolean flyingWas;
    private static CloudStatus cloudsWere;
    private static Phase phase = Phase.MOVE;
    private static int warmup;
    private static int cleanupWait;
    private static int settle;
    private static Shot current;

    // The HUD-hide flag and the main render target both DIVERGE across 26.1.2 ↔ 26.2, so they are
    // resolved reflectively rather than referenced at compile time (the same approach the sibling
    // mods use for version-divergent client symbols). 26.1.x: Options#hideGui (boolean) +
    // Minecraft#getMainRenderTarget(). 26.2: Gui#hud → Hud#isHidden()/toggle() +
    // GameRenderer#mainRenderTarget().
    private static boolean hudResolved;
    private static java.lang.reflect.Field optHideGuiField;     // 26.1.x
    private static java.lang.reflect.Field guiHudField;         // 26.2
    private static java.lang.reflect.Method hudIsHiddenMethod;  // 26.2
    private static java.lang.reflect.Method hudToggleMethod;    // 26.2
    private static boolean rtResolved;
    private static java.lang.reflect.Method mcGetRenderTarget;  // 26.1.x
    private static java.lang.reflect.Method grMainRenderTarget; // 26.2

    private GalleryCaptureHarness() {
    }

    // ------------------------------------------------------------------------------------------------
    // Command registration (generic in the Brigadier source type — the bodies never read the source).
    // ------------------------------------------------------------------------------------------------

    /**
     * Registers the client-side {@code /ncgallery capture …} tree. Generic in {@code S} so the same
     * method serves NeoForge/Forge ({@code CommandSourceStack}) and Fabric
     * ({@code FabricClientCommandSource}); every body drives the local client only.
     *
     * <p>The root must NOT be {@code nerocreatures} — see the class docs (it would shadow the server
     * tree, and {@code /nerocreatures gallery} would stop working).
     */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("ncgallery")
                        .then(LiteralArgumentBuilder.<S>literal("capture")
                                .executes(ctx -> startCapture("noon"))
                                .then(LiteralArgumentBuilder.<S>literal("shot")
                                        .then(RequiredArgumentBuilder.<S, String>argument("name",
                                                        StringArgumentType.word())
                                                .executes(ctx -> shotHere(
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(RequiredArgumentBuilder.<S, String>argument("time",
                                                StringArgumentType.word())
                                        .executes(ctx -> startCapture(
                                                StringArgumentType.getString(ctx, "time"))))));
    }

    // ------------------------------------------------------------------------------------------------
    // Run starters.
    // ------------------------------------------------------------------------------------------------

    private static int startCapture(String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            mc.player.sendSystemMessage(Component.literal("Gallery capture already running."));
            return 0;
        }
        QUEUE.clear();
        QUEUE.addAll(galleryQueue(time));
        begin(mc);
        mc.player.sendSystemMessage(Component.literal("Gallery capture: rebuilding the scene, time="
                + time + ", " + QUEUE.size() + " shots → screenshots/nerocreatures/ (overlays hidden)."));
        return Command.SINGLE_SUCCESS;
    }

    private static int shotHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || running) {
            return 0;
        }
        QUEUE.clear();
        QUEUE.add(new Shot(name, List.of(), List.of(), List.of(), 0, null, null)); // current view, as-is
        begin(mc);
        return Command.SINGLE_SUCCESS;
    }

    /** The shot list, with the one-time rebuild and environment pin attached to shot 0. */
    private static List<Shot> galleryQueue(String time) {
        List<Shot> shots = new ArrayList<>(buildShots(ORIGIN_X, ORIGIN_Y, ORIGIN_Z));
        // SETUP (pre-warmup) only teleports to the origin and pins the environment — no building
        // yet, because block placement into not-yet-loaded chunks fails. BUILD (post-warmup)
        // rebuilds from scratch: clear first, so a rerun cannot stack a second set of creatures on
        // the first, then build, then strip the floating cluster labels for clean marketing shots.
        // These are SERVER commands, which is why the world needs cheats.
        List<String> setup = List.of(
                "tp @s " + (ORIGIN_X + 0.5) + " " + ORIGIN_Y + " " + (ORIGIN_Z + 0.5),
                "gamerule advance_time false",    // 26.1 renamed doDaylightCycle → advance_time
                "gamerule advance_weather false", // …and doWeatherCycle → advance_weather
                "time set " + time,
                "weather clear");
        List<String> build = List.of(
                "nerocreatures gallery clear",
                "nerocreatures gallery",
                // Strip the cluster labels BY TAG. Not "every invisible armour stand": the item
                // displays are invisible armour stands too, and this would delete the whole item
                // wall (see CreatureGallery.LABEL_TAG).
                "kill @e[tag=nerocreatures_gallery_label]");
        Shot first = shots.get(0);
        shots.set(0, new Shot(first.name(), setup, build, groundClear(), BUILD_WARMUP_TICKS,
                first.camera(), first.target()));
        return shots;
    }

    private static void begin(Minecraft mc) {
        // screenshots/ is created by Screenshot.grab; the nerocreatures/ subfolder is not.
        new File(mc.gameDirectory, "screenshots/nerocreatures").mkdirs();
        hudWasHidden = isHudHidden(mc);
        setHudHidden(mc, true);
        cloudsWere = mc.options.cloudStatus().get();
        mc.options.cloudStatus().set(CloudStatus.OFF); // clouds scroll with game time → freeze them out
        bobViewWas = mc.options.bobView().get();
        mc.options.bobView().set(false); // kill view-bob so a pinned pose renders the exact same frame
        // Fly, so the camera hovers at the teleport spot instead of falling through the sky while the
        // scene is being built underneath it.
        flyingWas = mc.player != null && mc.player.getAbilities().flying;
        setFlying(mc, true);
        running = true;
        phase = Phase.MOVE;
        current = null;
    }

    /** Toggle creative flight so the camera hovers (no gravity drift) for the duration of a run. */
    private static void setFlying(Minecraft mc, boolean fly) {
        if (mc.player == null) {
            return;
        }
        mc.player.getAbilities().flying = fly && mc.player.getAbilities().mayfly;
        mc.player.onUpdateAbilities();
    }

    // ------------------------------------------------------------------------------------------------
    // Per-client-tick state machine (driven from each loader's client-tick hook).
    // ------------------------------------------------------------------------------------------------

    /** Called once per client tick from each loader's own hook. A no-op unless a run is live. */
    public static void tick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            finish(mc);
            return;
        }

        switch (phase) {
            case MOVE -> {
                final Shot shot = QUEUE.poll();
                current = shot;
                if (shot == null) {
                    finish(mc);
                    return;
                }
                for (String cmd : shot.setup()) { // pre-warmup: teleport + pin time/weather
                    player.connection.sendCommand(cmd);
                }
                warmup = shot.warmup();
                phase = Phase.WARMUP;
            }
            case WARMUP -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player); // keep flying + motionless so the camera does not drift
                if (--warmup <= 0) { // chunks are loaded → safe to clear / build / strip
                    for (String cmd : shot.build()) {
                        player.connection.sendCommand(cmd);
                    }
                    cleanupWait = shot.cleanup().isEmpty() ? 0 : CLEANUP_TICKS;
                    phase = Phase.CLEANUP;
                }
            }
            case CLEANUP -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player); // stay aloft while the dropped label stands settle
                if (--cleanupWait <= 0) {
                    for (String cmd : shot.cleanup()) {
                        player.connection.sendCommand(cmd);
                    }
                    applyPose(player, shot);
                    settle = SETTLE_TICKS;
                    phase = Phase.SETTLE;
                }
            }
            case SETTLE -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot); // re-pin every tick so gravity cannot drift the camera
                if (--settle <= 0) {
                    phase = Phase.SHOOT;
                }
            }
            case SHOOT -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                grab(mc, shot.name());
                phase = Phase.MOVE;
            }
        }
    }

    /** Keep the player flying + motionless during a wait so gravity does not drift the camera. */
    private static void holdStill(Minecraft mc, LocalPlayer player) {
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            setFlying(mc, true); // re-assert if the server toggled it off
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Snap the player (the render camera) to the shot's pose, holding it still. */
    private static void applyPose(LocalPlayer player, Shot shot) {
        if (shot == null) {
            return;
        }
        Vec3 cam = shot.camera();
        Vec3 tgt = shot.target();
        if (cam == null || tgt == null) {
            return; // "keep the current view" shot
        }
        double dx = tgt.x - cam.x;
        double dy = tgt.y - cam.y;
        double dz = tgt.z - cam.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        player.snapTo(cam.x, cam.y, cam.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void grab(Minecraft mc, String name) {
        RenderTarget target = mainRenderTarget(mc);
        if (target == null) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("Capture: could not resolve the render target."));
            }
            return;
        }
        Screenshot.grab(mc.gameDirectory, "nerocreatures/" + name + ".png", target, 1,
                msg -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(msg);
                    }
                });
    }

    private static void finish(Minecraft mc) {
        setHudHidden(mc, hudWasHidden);
        if (cloudsWere != null) {
            mc.options.cloudStatus().set(cloudsWere);
        }
        mc.options.bobView().set(bobViewWas);
        setFlying(mc, flyingWas);
        running = false;
        current = null;
        QUEUE.clear();
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                    Component.literal("Capture done — see screenshots/nerocreatures/."));
        }
    }

    // --- HUD hide + render target (version-divergent → reflective) ----------------------------------

    private static void resolveHud() {
        if (hudResolved) {
            return;
        }
        hudResolved = true;
        try { // 26.1.x: Options#hideGui boolean field
            optHideGuiField = net.minecraft.client.Options.class.getDeclaredField("hideGui");
            optHideGuiField.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            optHideGuiField = null;
        }
        if (optHideGuiField == null) {
            try { // 26.2: Gui#hud → Hud#isHidden()/toggle()
                guiHudField = net.minecraft.client.gui.Gui.class.getDeclaredField("hud");
                guiHudField.setAccessible(true);
                Class<?> hudType = guiHudField.getType();
                hudIsHiddenMethod = hudType.getMethod("isHidden");
                hudToggleMethod = hudType.getMethod("toggle");
            } catch (ReflectiveOperationException | RuntimeException e) {
                guiHudField = null;
                hudIsHiddenMethod = null;
                hudToggleMethod = null;
            }
        }
    }

    private static boolean isHudHidden(Minecraft mc) {
        resolveHud();
        try {
            if (optHideGuiField != null) {
                return optHideGuiField.getBoolean(mc.options);
            }
            if (guiHudField != null && hudIsHiddenMethod != null) {
                Object hud = guiHudField.get(mc.gui);
                return hud != null && Boolean.TRUE.equals(hudIsHiddenMethod.invoke(hud));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through to "not hidden"
        }
        return false;
    }

    private static void setHudHidden(Minecraft mc, boolean hidden) {
        resolveHud();
        try {
            if (optHideGuiField != null) {
                optHideGuiField.setBoolean(mc.options, hidden);
                return;
            }
            if (guiHudField != null && hudIsHiddenMethod != null && hudToggleMethod != null) {
                Object hud = guiHudField.get(mc.gui);
                if (hud != null && !Boolean.valueOf(hidden).equals(hudIsHiddenMethod.invoke(hud))) {
                    hudToggleMethod.invoke(hud); // toggle only when not already in the wanted state
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // best-effort; a missing flag just means the HUD shows in the shot
        }
    }

    /**
     * The main framebuffer: 26.1.x {@code Minecraft#getMainRenderTarget()}, 26.2
     * {@code GameRenderer#mainRenderTarget()}.
     */
    private static RenderTarget mainRenderTarget(Minecraft mc) {
        if (!rtResolved) {
            rtResolved = true;
            try {
                mcGetRenderTarget = Minecraft.class.getMethod("getMainRenderTarget");
            } catch (NoSuchMethodException | RuntimeException e) {
                mcGetRenderTarget = null;
            }
            if (mcGetRenderTarget == null) {
                try {
                    grMainRenderTarget = mc.gameRenderer.getClass().getMethod("mainRenderTarget");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    grMainRenderTarget = null;
                }
            }
        }
        try {
            if (mcGetRenderTarget != null) {
                return (RenderTarget) mcGetRenderTarget.invoke(mc);
            }
            if (grMainRenderTarget != null) {
                return (RenderTarget) grMainRenderTarget.invoke(mc.gameRenderer);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // The shot list. Every coordinate mirrors a cluster position in the server-side
    // command/CreatureGallery — TUNE THE TWO TOGETHER.
    //
    // Framing rules learned on the sibling mod's gallery: get close enough that the subject fills the
    // frame, aim at the subject's MID height so there is little dead sky, and shoot a long row DOWN
    // THE LINE from near one end rather than broadside, so it recedes with perspective. Every
    // creature in the scene faces west, so a camera west of a row sees its front.
    // ------------------------------------------------------------------------------------------------

    private static List<Shot> buildShots(int ox, int oy, int oz) {
        List<Shot> shots = new ArrayList<>();

        // EAST — the frozen roster row (cells at x = ox+14 … ox+50, z = oz-6).
        shots.add(shot("creature_lineup", ox + 9.5, oy + 3.0, oz - 10, ox + 46, oy + 1.8, oz - 5.5));
        shots.add(shot("creature_hostiles", ox + 11.5, oy + 2.4, oz - 11, ox + 24, oy + 1.6, oz - 6));
        shots.add(shot("creature_machines", ox + 33, oy + 2.6, oz - 12, ox + 46, oy + 1.8, oz - 6));
        // …and the live row in its glass vivarium (z = oz).
        shots.add(shot("creature_vivarium", ox + 12, oy + 3.2, oz + 6, ox + 34, oy + 1.8, oz));

        // NORTH — the boss arena (centre ox, oz-30; rim at radius 9, pillars at ±6).
        shots.add(shot("boss_arena", ox + 0.5, oy + 6.5, oz - 15, ox + 0.5, oy + 2.5, oz - 30));
        shots.add(shot("boss_closeup", ox + 2.5, oy + 3.4, oz - 23, ox + 0.5, oy + 2.6, oz - 30));

        // SOUTH — the pets (ox-9 / ox-1, oz+26) and the drone's work area (ox+8, oz+26).
        shots.add(shot("pets", ox - 13, oy + 3.0, oz + 20, ox - 5, oy + 1.8, oz + 26));
        shots.add(shot("drone_work_area", ox + 3, oy + 3.0, oz + 21, ox + 8, oy + 2.0, oz + 26));

        // WEST — the item wall (backdrop at x = ox-34, displays at ox-33, columns oz-5 … oz+5,
        // drops on the two lower rows and spawn eggs on the two above them).
        shots.add(shot("drops", ox - 27, oy + 3.4, oz, ox - 33, oy + 3.4, oz));
        shots.add(shot("spawn_eggs", ox - 27, oy + 7.4, oz, ox - 33, oy + 7.4, oz));

        // The whole set from above, as the "what is in this mod" card.
        shots.add(shot("gallery_overview", ox - 26, oy + 28, oz + 36, ox + 14, oy + 2, oz - 8));

        return shots;
    }

    /** A camera-only shot (no setup/build/cleanup — the rebuild rides on shot 0). */
    private static Shot shot(String name, double cx, double cy, double cz,
            double tx, double ty, double tz) {
        return new Shot(name, List.of(), List.of(), List.of(), 0,
                new Vec3(cx, cy, cz), new Vec3(tx, ty, tz));
    }

    /** Sweep dropped items + XP-orb litter off the pads (run after a {@link #CLEANUP_TICKS} pause). */
    private static List<String> groundClear() {
        return List.of("kill @e[type=item]", "kill @e[type=experience_orb]");
    }
}
