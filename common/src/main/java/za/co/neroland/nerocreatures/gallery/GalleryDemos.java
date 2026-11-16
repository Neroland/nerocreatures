package za.co.neroland.nerocreatures.gallery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;
import za.co.neroland.nerocreatures.entity.boss.FacetRules;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisuals;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * Demo plates: a creative player who steps onto one of the Menagerie's gold plates sees that slot's
 * ability played once as a <b>script of visuals</b> (a pose, particles, a sound and the listed prop
 * blocks inside the slot's prop box). A demo never calls a species' goal or ability code: it moves,
 * hurts, heals or affects nobody, drops nothing, and writes no statistic, gate, ownership row or link
 * event.
 *
 * <p>Survival and adventure players, mobs, items and redstone do nothing: the plate is a plain vanilla
 * block wired to nothing. The cost for everyone else is one block-state read every 2 ticks while in
 * creative.
 *
 * <p>State is transient and in memory only: the last plate per player and the cooldown per exhibit
 * (weakly keyed), and the running list. Server thread only.
 */
public final class GalleryDemos {

    /** How a trigger went. */
    public enum Result { STARTED, NO_SLOT, MISSING, COOLDOWN, BUSY }

    private static final Map<ServerPlayer, BlockPos> LAST_PLATE = new WeakHashMap<>();
    private static final Map<Entity, Long> COOLDOWN = new WeakHashMap<>();
    private static final Map<String, Long> CRUST_COOLDOWN = new HashMap<>();
    private static final List<Running> RUNNING = new ArrayList<>();

    private GalleryDemos() {
    }

    /** One demo in progress. {@code exhibit} is null for the crust stand. */
    private static final class Running {
        final ServerLevel level;
        final BlockPos origin;
        final MenagerieLayout.Demo demo;
        @Nullable
        final Entity exhibit;
        final List<Entity> extras;
        final int savedStage;
        final int savedVariant;
        int age;

        Running(ServerLevel level, BlockPos origin, MenagerieLayout.Demo demo, @Nullable Entity exhibit,
                List<Entity> extras) {
            this.level = level;
            this.origin = origin;
            this.demo = demo;
            this.exhibit = exhibit;
            this.extras = extras;
            CreatureVisuals v = visuals(exhibit);
            this.savedStage = v == null ? 0 : v.stage();
            this.savedVariant = v == null ? 0 : v.variant();
        }
    }

    // --- tick ------------------------------------------------------------------------------------

    /** Called once per server tick (from the spawn engine's tick hook). */
    public static void serverTick(MinecraftServer server) {
        if (server.getTickCount() % 2 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                checkPlate(player);
            }
        }
        if (!RUNNING.isEmpty()) {
            tickRunning();
        }
    }

    private static void checkPlate(ServerPlayer player) {
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            LAST_PLATE.remove(player);
            return;
        }
        //? if >=1.21.6 {
        ServerLevel level = player.level();
        //?} else {
        /*ServerLevel level = player.serverLevel();
        *///?}
        BlockPos feet = player.blockPosition();
        if (!level.getBlockState(feet).is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
            LAST_PLATE.remove(player);
            return;
        }
        if (feet.equals(LAST_PLATE.get(player))) {
            return; // standing still does not replay; stepping off and on does
        }
        LAST_PLATE.put(player, feet.immutable());
        GalleryDisplays.Anchor anchor = GalleryDisplays.nearestAnchor(level, player.position(),
                MenagerieLayout.ANCHOR_RADIUS);
        if (anchor == null || anchor.grid()) {
            return;
        }
        BlockPos o = anchor.origin();
        Optional<String> slot = MenagerieLayout.plateAt(feet.getX() - o.getX(), feet.getY() - o.getY(),
                feet.getZ() - o.getZ());
        slot.ifPresent(id -> trigger(level, o, id));
    }

    // --- trigger ---------------------------------------------------------------------------------

    /** Steps 5 to 8 of a plate press for a named slot: find the exhibit, check cooldown and load, run. */
    public static Result trigger(ServerLevel level, BlockPos origin, String slot) {
        Optional<MenagerieLayout.Demo> demo = MenagerieLayout.demo(slot);
        Optional<MenagerieLayout.Box> box = MenagerieLayout.slotBox(slot);
        if (demo.isEmpty() || box.isEmpty()) {
            return Result.NO_SLOT;
        }
        long now = level.getGameTime();
        Entity exhibit = null;
        List<Entity> extras = new ArrayList<>();
        String crustKey = level.dimension().identifier() + "@" + origin.asLong();
        if (MenagerieLayout.CRUST.equals(slot)) {
            Long until = CRUST_COOLDOWN.get(crustKey);
            if (until != null && now < until) {
                return Result.COOLDOWN;
            }
        } else {
            List<Mob> found = level.getEntitiesOfClass(Mob.class, aabb(origin, box.get()),
                    e -> e.isAlive() && GalleryDisplays.tags(e).contains(MenagerieLayout.SLOT_TAG_PREFIX + slot));
            if (found.isEmpty()) {
                return Result.MISSING;
            }
            exhibit = found.get(0);
            for (int i = 1; i < found.size(); i++) {
                extras.add(found.get(i)); // the Bloom Host's sporeling shares its slot tag
            }
            Long until = COOLDOWN.get(exhibit);
            if (until != null && now < until) {
                return Result.COOLDOWN;
            }
            if (slot.equals("bloom_sovereign_p2")) {
                for (String pod : MenagerieLayout.POD_SUFFIXES) {
                    extras.addAll(level.getEntitiesOfClass(Mob.class,
                            aabb(origin, new MenagerieLayout.Box(-55, 55, -52, -35, 0, 12)),
                            e -> GalleryDisplays.tags(e).contains(MenagerieLayout.SLOT_TAG_PREFIX + pod)));
                }
            }
        }
        int inLevel = 0;
        for (Running r : RUNNING) {
            if (r.level == level) {
                inLevel++;
            }
        }
        if (inLevel >= MenagerieLayout.MAX_RUNNING_DEMOS) {
            return Result.BUSY;
        }
        long until = now + demo.get().ticks() + MenagerieLayout.DEMO_COOLDOWN_EXTRA;
        if (exhibit != null) {
            COOLDOWN.put(exhibit, until);
        } else {
            CRUST_COOLDOWN.put(crustKey, until);
        }
        Running run = new Running(level, origin, demo.get(), exhibit, extras);
        RUNNING.add(run);
        start(run);
        return Result.STARTED;
    }

    /** Stops every running demo of the gallery at {@code origin} (its end step runs) and forgets cooldowns. */
    public static void stopAt(ServerLevel level, BlockPos origin) {
        Iterator<Running> it = RUNNING.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            if (r.level == level && r.origin.equals(origin)) {
                finish(r);
                it.remove();
                if (r.exhibit != null) {
                    COOLDOWN.remove(r.exhibit);
                }
            }
        }
        CRUST_COOLDOWN.remove(level.dimension().identifier() + "@" + origin.asLong());
    }

    /** Running demos (for the budget test and the probe). */
    public static int running() {
        return RUNNING.size();
    }

    /** Forgets everything (server stop). */
    public static void reset() {
        RUNNING.clear();
        COOLDOWN.clear();
        CRUST_COOLDOWN.clear();
        LAST_PLATE.clear();
    }

    private static void tickRunning() {
        Iterator<Running> it = RUNNING.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            r.age++;
            if (r.exhibit != null && r.exhibit.isRemoved()) {
                it.remove();
                continue;
            }
            step(r);
            if (r.age >= r.demo.ticks()) {
                finish(r);
                it.remove();
            }
        }
    }

    // --- scripts ---------------------------------------------------------------------------------

    @Nullable
    private static CreatureVisuals visuals(@Nullable Entity entity) {
        return entity instanceof CreatureVisualsHolder holder ? holder.visuals() : null;
    }

    private static VisualPose pose(String clip) {
        return switch (clip) {
            case "ability" -> VisualPose.ABILITY;
            case "ability_2" -> VisualPose.ABILITY_2;
            case "rest" -> VisualPose.REST;
            default -> VisualPose.IDLE;
        };
    }

    private static void start(Running r) {
        MenagerieLayout.Demo demo = r.demo;
        CreatureVisuals v = visuals(r.exhibit);
        if (v != null && !demo.clip().isEmpty()) {
            v.playPose(pose(demo.clip()), demo.ticks());
        }
        if (r.exhibit != null) {
            sound(r.level, r.exhibit.position(), demo.sound());
            burst(r, 12);
        }
        String slot = demo.slot();
        switch (slot) {
            case "lumen_moth" -> setProp(r, 0, "minecraft:light[level=12]", Blocks.AIR.defaultBlockState());
            case "magma_wader" -> crust(r, prop(r, 0), "slag");
            case "spore_drifter" -> wheat(r, 7);
            case "lode_tortoise", "scrap_hulk", "drift_leviathan", "rime_yak", "prism_stag" -> {
                if (v != null) {
                    v.setStage(0);
                }
            }
            case "slag_beetle" -> {
                if (v != null) {
                    v.setStage(1);
                }
            }
            case "bloom_host" -> {
                for (Entity extra : r.extras) {
                    CreatureVisuals ev = visuals(extra);
                    if (ev != null) {
                        ev.playPose(VisualPose.ABILITY, demo.ticks());
                    }
                }
            }
            case "void_manta" -> {
                if (r.exhibit != null) {
                    Vec3 p = r.exhibit.position();
                    particles(r.level, "void_blink", p.add(-4, 0.5, 0), 12, 0.3);
                    particles(r.level, "void_blink", p.add(4, 0.5, 0), 12, 0.3);
                }
            }
            case "asteroid_worm", "cinder_tyrant_p1" -> crackRing(r, slot.equals("asteroid_worm") ? 1 : 3);
            case MenagerieLayout.CRUST -> {
                String[] variants = {"slag", "rime", "prism"};
                for (int i = 0; i < 3; i++) {
                    crust(r, prop(r, i), variants[i]);
                }
            }
            default -> {
            }
        }
        if (slot.startsWith("facet_colossus") && v != null) {
            v.setVariant(FacetRules.visualVariant(slot.endsWith("p3"), 0));
        }
    }

    private static void step(Running r) {
        int t = r.age;
        String slot = r.demo.slot();
        CreatureVisuals v = visuals(r.exhibit);
        if (r.exhibit != null && t % 10 == 0 && t < r.demo.ticks()) {
            burst(r, 6);
        }
        switch (slot) {
            case "lode_tortoise" -> {
                if (v != null) {
                    v.setStage(Math.min(3, t / 20));
                }
            }
            case "scrap_hulk" -> {
                if (v != null) {
                    v.setStage(t >= 80 ? 0 : Math.min(3, t / 20));
                }
            }
            case "drift_leviathan" -> {
                if (v != null) {
                    v.setStage(Math.min(3, t * 4 / r.demo.ticks()));
                }
            }
            case "rime_yak", "prism_stag" -> {
                if (v != null && t == 40) {
                    v.setStage(r.savedStage);
                }
            }
            case "slag_beetle" -> {
                if (v != null && t == 60) {
                    v.setStage(0);
                    if (r.exhibit != null) {
                        particles(r.level, "slag_ember", r.exhibit.position().add(0, 0.5, 0), 20, 0.4);
                    }
                }
            }
            case "spore_drifter" -> {
                if (t == 60) {
                    wheat(r, 2);
                }
            }
            case "rime_matriarch_p1", "rime_matriarch_p3" -> beam(r);
            case "rime_matriarch_p2" -> {
                if (t % 10 == 0 && r.exhibit != null) {
                    ring(r.level, "rime_mote", r.exhibit.position().add(0, 0.1, 0), 1 + Math.min(2, t / 20));
                }
            }
            case "bloom_sovereign_p2" -> {
                if (t % 5 == 0 && r.exhibit != null) {
                    for (Entity pod : r.extras) {
                        line(r.level, "bloom_mote", pod.position().add(0, 1, 0), r.exhibit.position().add(0, 2, 0), 6);
                    }
                }
            }
            case "magnet_crab" -> {
                if (t % 5 == 0 && r.exhibit != null) {
                    Vec3 c = r.exhibit.position().add(0, 0.4, 0);
                    for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                        line(r.level, "magnet_spark", c.add(d[0], 0.5, d[1]), c, 4);
                    }
                }
            }
            case "gravity_leech" -> {
                if (t % 5 == 0 && r.exhibit != null) {
                    MenagerieLayout.Slot sl = MenagerieLayout.slot(slot).orElse(null);
                    if (sl != null) {
                        Vec3 target = new Vec3(r.origin.getX() + sl.plateX() + 0.5, r.origin.getY() + 4,
                                r.origin.getZ() + sl.plateZ() + 0.5);
                        line(r.level, "gravity_ripple", r.exhibit.position().add(0, 0.3, 0), target, 6);
                    }
                }
            }
            case "facet_colossus_p1" -> {
                if (v != null && t % 13 == 0) {
                    v.setVariant(FacetRules.visualVariant(false, t / 13));
                }
            }
            case "facet_colossus_p2" -> {
                if (v != null && t % 20 == 0) {
                    int mask = 1 << ((t / 20) % FacetRules.FACETS);
                    mask |= 1 << (((t / 20) + 2) % FacetRules.FACETS);
                    v.setForm(FacetRules.decoyForm(mask));
                    v.setStage(FacetRules.decoyStage(mask));
                }
            }
            case "facet_colossus_p3" -> beam(r);
            default -> {
            }
        }
    }

    /** The end step: every prop the demo placed is restored and the exhibit's look is put back. */
    private static void finish(Running r) {
        String slot = r.demo.slot();
        CreatureVisuals v = visuals(r.exhibit);
        switch (slot) {
            case "lumen_moth" -> {
                BlockPos p = prop(r, 0);
                if (r.level.getBlockState(p).is(Blocks.LIGHT)) {
                    r.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            case "spore_drifter" -> wheat(r, 2);
            default -> {
            }
        }
        if (v != null) {
            if (!slot.equals("lode_tortoise")) {
                v.setStage(r.savedStage);
            } else {
                v.setStage(3); // the tortoise's shell stays full
            }
            v.setVariant(r.savedVariant);
            if (slot.equals("facet_colossus_p2")) {
                v.setForm(0);
                v.setStage(0);
            }
            if (MenagerieLayout.HIDDEN_AT_REST.contains(slot)) {
                v.holdPose(VisualPose.REST);
            }
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static AABB aabb(BlockPos o, MenagerieLayout.Box box) {
        return new AABB(o.getX() + box.minX(), o.getY() + box.minY(), o.getZ() + box.minZ(),
                o.getX() + box.maxX() + 1, o.getY() + box.maxY() + 1, o.getZ() + box.maxZ() + 1);
    }

    private static BlockPos prop(Running r, int index) {
        List<int[]> props = MenagerieLayout.demoProps(r.demo.slot());
        int[] p = props.get(Math.min(index, props.size() - 1));
        return r.origin.offset(p[0], p[2], p[1]);
    }

    /** Places a prop only where the slot's prop box holds {@code expected} (air for new props). */
    private static void setProp(Running r, int index, String state, BlockState expected) {
        BlockPos p = prop(r, index);
        if (r.level.getBlockState(p).equals(expected)) {
            r.level.setBlock(p, MenagerieBuilder.state(state), 2 | 16);
        }
    }

    private static void crust(Running r, BlockPos pos, String variant) {
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        if (crust == null || !r.level.getBlockState(pos).isAir()) {
            return;
        }
        BlockState state = MenagerieBuilder.state("nerocreatures:ephemeral_crust[variant=" + variant + ",restore=air]");
        crust.place(r.level, pos, state, 100);
    }

    private static void wheat(Running r, int age) {
        for (int i = 0; i < 3; i++) {
            BlockPos p = prop(r, i);
            BlockState s = r.level.getBlockState(p);
            if (s.is(Blocks.WHEAT)) {
                r.level.setBlock(p, s.setValue(CropBlock.AGE, Math.min(7, age)), 2);
            }
        }
    }

    private static void burst(Running r, int count) {
        if (r.demo.particle().isEmpty() || r.exhibit == null) {
            return;
        }
        Vec3 p = r.exhibit.position();
        particles(r.level, r.demo.particle(), p.add(0, r.exhibit.getBbHeight() * 0.6, 0), count,
                Math.max(0.3, r.exhibit.getBbWidth() * 0.5));
    }

    private static void beam(Running r) {
        if (r.exhibit == null || r.age % 4 != 0) {
            return;
        }
        Vec3 from = r.exhibit.position().add(0, r.exhibit.getBbHeight() * 0.7, 0);
        Vec3 to = new Vec3(from.x, r.origin.getY() + 1.5, r.origin.getZ() - 36 - 3 + 0.5);
        line(r.level, r.demo.particle().isEmpty() ? "prism_glint" : r.demo.particle(), from, to, 10);
    }

    private static void crackRing(Running r, int radius) {
        if (r.exhibit == null) {
            return;
        }
        BlockPos under = r.exhibit.blockPosition().below();
        BlockState floor = r.level.getBlockState(under);
        if (floor.isAir()) {
            floor = Blocks.STONE.defaultBlockState();
        }
        Vec3 c = r.exhibit.position();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16.0;
            r.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), c.x + Math.cos(a) * radius,
                    c.y + 0.1, c.z + Math.sin(a) * radius, 4, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private static void ring(ServerLevel level, String particle, Vec3 c, int radius) {
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16.0;
            particles(level, particle, c.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0.05);
        }
    }

    private static void line(ServerLevel level, String particle, Vec3 from, Vec3 to, int points) {
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.lerp(to, i / (double) points);
            particles(level, particle, p, 1, 0.02);
        }
    }

    private static void particles(ServerLevel level, String id, Vec3 at, int count, double spread) {
        ParticleOptions options = particle(id);
        if (options != null) {
            level.sendParticles(options, at.x, at.y, at.z, count, spread, spread * 0.6, spread, 0.02);
        }
    }

    @Nullable
    private static ParticleOptions particle(String id) {
        Identifier key = Identifier.tryParse(id.indexOf(':') < 0 ? "nerocreatures:" + id : id);
        if (key == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.PARTICLE_TYPE.getValue(key) instanceof ParticleOptions options ? options : null;
    }

    private static void sound(ServerLevel level, Vec3 at, String id) {
        if (id.isEmpty()) {
            return;
        }
        Identifier key = Identifier.tryParse("nerocreatures:" + id);
        if (key == null || !BuiltInRegistries.SOUND_EVENT.containsKey(key)) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(key);
        level.playSound(null, at.x, at.y, at.z, event, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }
}
