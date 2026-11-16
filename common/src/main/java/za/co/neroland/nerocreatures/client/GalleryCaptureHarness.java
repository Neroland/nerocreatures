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

import za.co.neroland.nerocreatures.gallery.MenagerieLayout;
import za.co.neroland.nerocreatures.gallery.MenagerieShots;

/**
 * CLIENT, creative-debug: an automated, reproducible screenshot pass over the
 * {@code /nerocreatures gallery} Menagerie (or the compact grid).
 *
 * <p>The server builds the scene; this harness drives the camera, hides every overlay and writes one
 * PNG per shot via {@link Screenshot#grab} to {@code <run dir>/screenshots/nerocreatures/}. The shot
 * list comes from {@link MenagerieShots}, which reads the same layout tables as the server builder, so
 * the scene and the camera cannot drift apart.
 *
 * <p><b>Cross-loader.</b> Loader-agnostic — vanilla client classes only. Each loader wires
 * {@link #registerClientCommands(CommandDispatcher)} from its client-command hook and {@link #tick()}
 * from its per-client-tick hook.
 *
 * <p><b>The root is {@code /ncgallery}, and it must not be {@code /nerocreatures}</b>: a client root
 * that shares its name with a server command shadows the server subtree.
 *
 * <h2>Commands</h2>
 *
 * <ul>
 *   <li>{@code /ncgallery capture [time]} — the Menagerie pass (160 shots): teleport to the fixed
 *       origin, {@code gallery clear}, {@code gallery}, strip the labels by tag, then every set.</li>
 *   <li>{@code /ncgallery capture grid [time]} — the grid pass (9 shots).</li>
 *   <li>{@code /ncgallery capture set <name> [time]} — one set: overview, pavilions, creatures,
 *       abilities, alphas, radiant, bosses or rows.</li>
 *   <li>{@code /ncgallery capture shot <name>} — grab the current view once.</li>
 * </ul>
 *
 * <p>Ability shots run {@code nerocreatures gallery demo <slot>} just before the shot, wait the
 * demo's shoot tick, grab, then wait for the rest of the demo so the next shot starts from idle.
 * The run needs a render distance of 8 or more (every part of the Menagerie is within 76 blocks of
 * the origin) and stops with a message if it is lower.
 */
public final class GalleryCaptureHarness {

    /** Ticks to let chunks/entities settle and a fresh frame render before grabbing. */
    private static final int SETTLE_TICKS = 12;

    /** Ticks to wait after teleporting, before building, for the destination chunks to load. */
    private static final int BUILD_WARMUP_TICKS = 120;

    /** Ticks to wait after the build commands before the cleanup sweep. */
    private static final int CLEANUP_TICKS = 60;

    /** The Menagerie spans 76 blocks from its origin: 8 chunks of render distance. */
    private static final int MIN_RENDER_DISTANCE = 8;

    /** The fixed staging origin. The gallery builds at the player's feet, so this is the scene origin. */
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Y = 100;
    private static final int ORIGIN_Z = 0;

    /**
     * One framed capture. {@code setup} runs before the warmup, {@code build} after it, {@code cleanup}
     * after {@link #CLEANUP_TICKS}; {@code pre} runs once the camera is posed, then {@code delay}
     * ticks pass before the grab, and {@code after} ticks after it. A {@code null} pose keeps the view.
     */
    private record Shot(String name, List<String> setup, List<String> build, List<String> cleanup,
            int warmup, Vec3 camera, Vec3 target, List<String> pre, int delay, int after) {
    }

    private enum Phase { MOVE, WARMUP, CLEANUP, SETTLE, PRE_WAIT, SHOOT, AFTER_WAIT }

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
    private static int wait;
    private static Shot current;

    // The HUD-hide flag and the main render target both DIVERGE across 26.1.2 ↔ 26.2, so they are
    // resolved reflectively rather than referenced at compile time.
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

    /** Registers {@code /ncgallery capture …}. Generic in {@code S} so one method serves every loader. */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("ncgallery")
                        .then(LiteralArgumentBuilder.<S>literal("capture")
                                .executes(ctx -> startCapture("menagerie", "", "noon"))
                                .then(LiteralArgumentBuilder.<S>literal("shot")
                                        .then(RequiredArgumentBuilder.<S, String>argument("name",
                                                        StringArgumentType.word())
                                                .executes(ctx -> shotHere(
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(LiteralArgumentBuilder.<S>literal("grid")
                                        .executes(ctx -> startCapture("grid", "", "noon"))
                                        .then(RequiredArgumentBuilder.<S, String>argument("time",
                                                        StringArgumentType.word())
                                                .executes(ctx -> startCapture("grid", "",
                                                        StringArgumentType.getString(ctx, "time")))))
                                .then(LiteralArgumentBuilder.<S>literal("set")
                                        .then(RequiredArgumentBuilder.<S, String>argument("set",
                                                        StringArgumentType.word())
                                                .executes(ctx -> startCapture("menagerie",
                                                        StringArgumentType.getString(ctx, "set"), "noon"))
                                                .then(RequiredArgumentBuilder.<S, String>argument("time",
                                                                StringArgumentType.word())
                                                        .executes(ctx -> startCapture("menagerie",
                                                                StringArgumentType.getString(ctx, "set"),
                                                                StringArgumentType.getString(ctx, "time"))))))
                                .then(RequiredArgumentBuilder.<S, String>argument("time",
                                                StringArgumentType.word())
                                        .executes(ctx -> startCapture("menagerie", "",
                                                StringArgumentType.getString(ctx, "time"))))));
    }

    // ------------------------------------------------------------------------------------------------
    // Run starters.
    // ------------------------------------------------------------------------------------------------

    private static int startCapture(String layout, String set, String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            chat(mc, Component.literal("Gallery capture already running."));
            return 0;
        }
        if (mc.options.renderDistance().get() < MIN_RENDER_DISTANCE) {
            chat(mc, Component.literal("Gallery capture needs a render distance of " + MIN_RENDER_DISTANCE
                    + " or more."));
            return 0;
        }
        List<MenagerieShots.Shot> shots = "grid".equals(layout) ? MenagerieShots.grid()
                : set.isEmpty() ? MenagerieShots.menagerie() : MenagerieShots.set(set);
        if (shots.isEmpty()) {
            chat(mc, Component.literal("No such shot set: " + set + ". Sets: "
                    + String.join(", ", MenagerieShots.SETS) + "."));
            return 0;
        }
        QUEUE.clear();
        QUEUE.addAll(queue(shots, "grid".equals(layout), time));
        begin(mc);
        chat(mc, Component.literal("Gallery capture: rebuilding the " + layout + ", time=" + time + ", "
                + QUEUE.size() + " shots → screenshots/nerocreatures/ (overlays hidden)."));
        return Command.SINGLE_SUCCESS;
    }

    private static int shotHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || running) {
            return 0;
        }
        QUEUE.clear();
        QUEUE.add(new Shot(name, List.of(), List.of(), List.of(), 0, null, null, List.of(), 0, 0));
        begin(mc);
        return Command.SINGLE_SUCCESS;
    }

    /** The shot list, with the one-time rebuild and environment pin attached to shot 0. */
    private static List<Shot> queue(List<MenagerieShots.Shot> layoutShots, boolean grid, String time) {
        List<Shot> shots = new ArrayList<>();
        for (MenagerieShots.Shot s : layoutShots) {
            shots.add(new Shot(s.name(), List.of(), List.of(), List.of(), 0,
                    new Vec3(ORIGIN_X + s.cam()[0], ORIGIN_Y + s.cam()[1], ORIGIN_Z + s.cam()[2]),
                    new Vec3(ORIGIN_X + s.target()[0], ORIGIN_Y + s.target()[1], ORIGIN_Z + s.target()[2]),
                    s.pre(), s.delay(), s.after()));
        }
        // SETUP (pre-warmup) teleports and pins the environment; BUILD (post-warmup, chunks loaded)
        // clears, rebuilds and strips the floating labels BY TAG (the item stands are armour stands
        // too). These are SERVER commands, which is why the world needs cheats.
        List<String> setup = List.of(
                "tp @s " + (ORIGIN_X + 0.5) + " " + ORIGIN_Y + " " + (ORIGIN_Z + 0.5),
                "gamerule advance_time false",    // 26.1 renamed doDaylightCycle → advance_time
                "gamerule advance_weather false", // …and doWeatherCycle → advance_weather
                "time set " + time,
                "weather clear",
                // Builds and clears run over a few server ticks: clear during the warmup, build after
                // it, and strip the labels in the cleanup step once the build has finished.
                "nerocreatures gallery clear");
        List<String> build = List.of(grid ? "nerocreatures gallery grid" : "nerocreatures gallery");
        List<String> cleanup = new ArrayList<>();
        cleanup.add("kill @e[tag=" + MenagerieLayout.LABEL_TAG + "]");
        cleanup.addAll(groundClear());
        Shot first = shots.get(0);
        shots.set(0, new Shot(first.name(), setup, build, cleanup, BUILD_WARMUP_TICKS, first.camera(),
                first.target(), first.pre(), first.delay(), first.after()));
        return shots;
    }

    private static void begin(Minecraft mc) {
        new File(mc.gameDirectory, "screenshots/nerocreatures").mkdirs();
        hudWasHidden = isHudHidden(mc);
        setHudHidden(mc, true);
        cloudsWere = mc.options.cloudStatus().get();
        mc.options.cloudStatus().set(CloudStatus.OFF);
        bobViewWas = mc.options.bobView().get();
        mc.options.bobView().set(false);
        flyingWas = mc.player != null && mc.player.getAbilities().flying;
        setFlying(mc, true);
        running = true;
        phase = Phase.MOVE;
        current = null;
    }

    private static void setFlying(Minecraft mc, boolean fly) {
        if (mc.player == null) {
            return;
        }
        mc.player.getAbilities().flying = fly && mc.player.getAbilities().mayfly;
        mc.player.onUpdateAbilities();
    }

    // ------------------------------------------------------------------------------------------------
    // Per-client-tick state machine.
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
        final Shot shot = current;
        switch (phase) {
            case MOVE -> {
                Shot next = QUEUE.poll();
                current = next;
                if (next == null) {
                    finish(mc);
                    return;
                }
                for (String cmd : next.setup()) {
                    player.connection.sendCommand(cmd);
                }
                warmup = next.warmup();
                phase = Phase.WARMUP;
            }
            case WARMUP -> {
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player);
                if (--warmup <= 0) {
                    for (String cmd : shot.build()) {
                        player.connection.sendCommand(cmd);
                    }
                    cleanupWait = shot.cleanup().isEmpty() ? 0 : CLEANUP_TICKS;
                    phase = Phase.CLEANUP;
                }
            }
            case CLEANUP -> {
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player);
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
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot);
                if (--settle <= 0) {
                    if (shot.pre().isEmpty()) {
                        phase = Phase.SHOOT;
                    } else {
                        for (String cmd : shot.pre()) {
                            player.connection.sendCommand(cmd);
                        }
                        wait = shot.delay();
                        phase = Phase.PRE_WAIT;
                    }
                }
            }
            case PRE_WAIT -> {
                applyPose(player, shot);
                if (--wait <= 0) {
                    phase = Phase.SHOOT;
                }
            }
            case SHOOT -> {
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                grab(mc, shot.name());
                wait = shot.after();
                phase = wait > 0 ? Phase.AFTER_WAIT : Phase.MOVE;
            }
            case AFTER_WAIT -> {
                applyPose(player, shot);
                if (--wait <= 0) {
                    phase = Phase.MOVE;
                }
            }
        }
    }

    /** Keep the player flying + motionless during a wait so gravity does not drift the camera. */
    private static void holdStill(Minecraft mc, LocalPlayer player) {
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            setFlying(mc, true);
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
            return;
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
                chat(mc, Component.literal("Capture: could not resolve the render target."));
            }
            return;
        }
        //? if >=1.21.6 {
        Screenshot.grab(mc.gameDirectory, "nerocreatures/" + name + ".png", target, 1,
        //?} else {
        /*Screenshot.grab(mc.gameDirectory, "nerocreatures/" + name + ".png", target,
        *///?}
                msg -> {
                    if (mc.player != null) {
                        chat(mc, msg);
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
            chat(mc, Component.literal("Capture done — see screenshots/nerocreatures/."));
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

    /** Sweep dropped items + XP-orb litter off the pads (run after a {@link #CLEANUP_TICKS} pause). */
    private static List<String> groundClear() {
        return List.of("kill @e[type=item]", "kill @e[type=experience_orb]");
    }

    /** A local chat line to the capturing player; LocalPlayer.sendSystemMessage exists only from 26.1. */
    private static void chat(Minecraft mc, Component message) {
        //? if >=26.1 {
        mc.player.sendSystemMessage(message);
        //?} else {
        /*mc.player.displayClientMessage(message, false);
        *///?}
    }
}
