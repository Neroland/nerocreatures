package za.co.neroland.nerocreatures.gallery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;
import za.co.neroland.nerocreatures.block.CapturedCreature;
import za.co.neroland.nerocreatures.block.EggData;
import za.co.neroland.nerocreatures.block.HabitatBlockEntity;
import za.co.neroland.nerocreatures.entity.alpha.AlphaData;
import za.co.neroland.nerocreatures.entity.alpha.AlphaDrop;
import za.co.neroland.nerocreatures.entity.alpha.AlphaSystem;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.FacetRules;
import za.co.neroland.nerocreatures.entity.glacira.FrostLurker;
import za.co.neroland.nerocreatures.entity.greenxertz.BloomHost;
import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisuals;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.entity.wrecks.MimicRules;
import za.co.neroland.nerocreatures.trait.TraitSet;

/**
 * Builds and clears the Menagerie and the compact grid from {@link MenagerieLayout}. Called by the
 * {@code gallery} command and by the dev-only budget probe; it takes a level and an origin, never a
 * player, and writes no player data. Deterministic: no {@code Random} anywhere.
 *
 * <p>Server thread only. One build runs in one tick.
 */
public final class MenagerieBuilder {

    private static final int FLAGS = 2 | 16;

    /** Columns of x per bulldoze or floor step. */
    private static final int SLAB = 10;

    /** Work per server tick for a build or a clear; the rest waits for the next tick. */
    private static final long TICK_BUDGET_NANOS = 25_000_000L;

    private static final List<Job> JOBS = new ArrayList<>();

    private MenagerieBuilder() {
    }

    /**
     * What a build placed, for the chat line, the budget check and the probe.
     *
     * @param micros      total build work, microseconds
     * @param maxTickMicros the longest single tick of build work
     * @param ticks       server ticks the build was spread over
     */
    public record Stats(int creatures, int bossPoses, int items, int blocks, int mobs, int entities,
            List<String> missing, long micros, long maxTickMicros, int ticks, int slowestStep,
            long slowestStepMicros) {

        public boolean overBudget() {
            return this.mobs > MenagerieLayout.MAX_MOBS || this.entities > MenagerieLayout.MAX_ENTITIES
                    || this.blocks > MenagerieLayout.MAX_BLOCKS;
        }
    }

    // --- tick-sliced jobs ------------------------------------------------------------------------

    /**
     * A build or clear split into steps (one floor slab, one bay, one row of slots, ...) and spread
     * over server ticks: each tick runs steps until {@value #TICK_BUDGET_NANOS} ns of work is done.
     */
    private static final class Job {
        final ServerLevel level;
        final W w;
        final Deque<Runnable> steps = new ArrayDeque<>();
        final Consumer<Stats> done;
        int creatures;
        int poses;
        long busyNanos;
        long maxTickNanos;
        int ticks;
        int stepIndex;
        int slowestStep;
        long slowestStepNanos;

        Job(ServerLevel level, W w, Consumer<Stats> done) {
            this.level = level;
            this.w = w;
            this.done = done;
        }

        void runStep() {
            long t0 = System.nanoTime();
            this.steps.poll().run();
            long spent = System.nanoTime() - t0;
            if (spent > this.slowestStepNanos) {
                this.slowestStepNanos = spent;
                this.slowestStep = this.stepIndex;
            }
            this.stepIndex++;
        }

        Stats stats() {
            return new Stats(this.creatures, this.poses, this.w.items, this.w.writes, this.w.mobs, this.w.entities,
                    List.copyOf(this.w.missing), this.busyNanos / 1000L, this.maxTickNanos / 1000L, this.ticks,
                    this.slowestStep, this.slowestStepNanos / 1000L);
        }
    }

    /** Runs pending build and clear steps. Called once per server tick. */
    public static void tick() {
        if (JOBS.isEmpty()) {
            return;
        }
        Job job = JOBS.get(0);
        long start = System.nanoTime();
        do {
            job.runStep();
        } while (!job.steps.isEmpty() && System.nanoTime() - start < TICK_BUDGET_NANOS);
        long spent = System.nanoTime() - start;
        job.busyNanos += spent;
        job.maxTickNanos = Math.max(job.maxTickNanos, spent);
        job.ticks++;
        if (job.steps.isEmpty()) {
            JOBS.remove(0);
            GalleryDisplays.forgetAnchors(job.level);
            job.done.accept(job.stats());
        }
    }

    /** Whether a build or clear is still running. */
    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    /** Forgets every pending job (server stop). */
    public static void reset() {
        JOBS.clear();
    }

    /** Runs a job to the end at once (tests and fallbacks). */
    private static Stats runNow(Job job) {
        long start = System.nanoTime();
        while (!job.steps.isEmpty()) {
            job.runStep();
        }
        job.busyNanos = System.nanoTime() - start;
        job.maxTickNanos = job.busyNanos;
        job.ticks = 1;
        GalleryDisplays.forgetAnchors(job.level);
        return job.stats();
    }

    // --- block-state strings ---------------------------------------------------------------------

    /** {@code namespace:id[prop=value,...]} to a state; unknown blocks are air, unknown properties ignored. */
    public static BlockState state(String spec) {
        String id = spec;
        String props = "";
        int open = spec.indexOf('[');
        if (open >= 0 && spec.endsWith("]")) {
            id = spec.substring(0, open);
            props = spec.substring(open + 1, spec.length() - 1);
        }
        Identifier key = Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.getValue(key);
        BlockState state = block.defaultBlockState();
        if (!props.isEmpty()) {
            for (String pair : props.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                Property<?> property = block.getStateDefinition().getProperty(pair.substring(0, eq).trim());
                if (property != null) {
                    state = with(state, property, pair.substring(eq + 1).trim());
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }

    // --- the writer ------------------------------------------------------------------------------

    /** Block writes relative to the origin, counted. {@code y} is relative to the floor layer F. */
    private static final class W {
        final ServerLevel level;
        final BlockPos o;
        int writes;
        int mobs;
        int entities;
        int items;
        final Set<String> missing = new LinkedHashSet<>();

        W(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.o = origin;
        }

        BlockPos at(int x, int y, int z) {
            return this.o.offset(x, y, z);
        }

        void set(int x, int y, int z, BlockState state) {
            if (this.level.setBlock(at(x, y, z), state, FLAGS)) {
                this.writes++;
            }
        }

        void set(int x, int y, int z, String spec) {
            set(x, y, z, state(spec));
        }

        /** Fences, signs, plates, crops, saplings and fluids: a full update. */
        void upd(int x, int y, int z, String spec) {
            if (this.level.setBlockAndUpdate(at(x, y, z), state(spec))) {
                this.writes++;
            }
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, String spec) {
            BlockState state = state(spec);
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, state);
                    }
                }
            }
        }

        /** Whether the block under the floor layer is solid (fluids and falling blocks stay put). */
        boolean supported(int x, int z) {
            BlockState below = this.level.getBlockState(at(x, -1, z));
            return !below.isAir() && below.getFluidState().isEmpty();
        }

        double ex(double x) {
            return this.o.getX() + x;
        }

        double ey(double y) {
            return this.o.getY() + y;
        }

        double ez(double z) {
            return this.o.getZ() + z;
        }
    }

    // --- build -----------------------------------------------------------------------------------

    /**
     * Starts building the Menagerie with its floor at {@code origin.y}. The work is spread over server
     * ticks; {@code done} gets the stats when the last step has run.
     */
    public static void startMenagerie(ServerLevel level, BlockPos origin, Consumer<Stats> done) {
        W w = new W(level, origin);
        Job job = new Job(level, w, done);
        bulldozeSteps(job.steps, level, origin, MenagerieLayout.BUILD_BOX, new int[2]);
        job.steps.add(() -> {
            GalleryDisplays.anchor(level, origin, false); // first, so a second build nearby is refused
            w.entities++;
        });
        for (int x0 = -55; x0 <= 55; x0 += SLAB) {
            final int a = x0;
            final int b = Math.min(55, x0 + SLAB - 1);
            job.steps.add(() -> w.fill(a, 0, -52, b, 0, 52, GalleryPalette.STREET));
        }
        job.steps.add(() -> streets(w));
        for (MenagerieLayout.Bay bay : MenagerieLayout.BAYS) {
            job.steps.add(() -> bay(w, bay));
        }
        for (MenagerieLayout.Bay bay : MenagerieLayout.BAYS) {
            job.steps.add(() -> {
                for (MenagerieLayout.Slot slot : MenagerieLayout.SLOTS) {
                    if (slot.bay().equals(bay.id()) && slot(w, slot) && !slot.isAlpha()) {
                        job.creatures++;
                    }
                }
            });
        }
        job.steps.add(() -> utilityBays(w));
        job.steps.add(() -> job.poses = bossHall(w));
        job.steps.add(() -> paddock(w));
        job.steps.add(() -> traitWall(w));
        job.steps.add(() -> extraItems(w));
        job.steps.add(() -> {
            for (MenagerieLayout.Label label : MenagerieLayout.labels()) {
                GalleryDisplays.label(level, w.ex(label.x() + 0.5), w.ey(7), w.ez(label.z() + 0.5),
                        Component.translatable(label.key()));
                w.entities++;
            }
            selfCheck(w);
        });
        JOBS.add(job);
    }

    /** Starts the compact grid (no signs, plates, tour, rideable mounts or working blocks). */
    public static void startGrid(ServerLevel level, BlockPos origin, Consumer<Stats> done) {
        W w = new W(level, origin);
        Job job = new Job(level, w, done);
        bulldozeSteps(job.steps, level, origin, MenagerieLayout.GRID_BUILD_BOX, new int[2]);
        job.steps.add(() -> {
            GalleryDisplays.anchor(level, origin, true);
            w.entities++;
        });
        job.steps.add(() -> {
            w.fill(-27, 0, -34, 53, 0, 40, GalleryPalette.STREET);
            for (int z : new int[] {-33, -9, 3, 14, 27}) {
                w.fill(10, 0, z, 53, 0, z, GalleryPalette.KERB);
            }
        });
        job.steps.add(() -> job.creatures = gridExhibits(w));
        job.steps.add(() -> gridItems(w));
        JOBS.add(job);
    }

    private static int gridExhibits(W w) {
        ServerLevel level = w.level;
        int creatures = 0;
        for (MenagerieLayout.GridExhibit g : MenagerieLayout.gridExhibits()) {
            boolean boss = MenagerieLayout.BOSSES.contains(g.entity());
            Mob mob = GalleryDisplays.spawn(level, g.entity(), w.ex(g.x() + 0.5), w.ey(1), w.ez(g.z() + 0.5), 90.0F,
                    g.hover(), "grid_" + g.entity());
            if (mob == null) {
                w.missing.add(g.entity());
                continue;
            }
            w.mobs++;
            w.entities++;
            if (!g.alpha().isEmpty() && mob instanceof NeroCreatureEntity creature) {
                AlphaSystem.forceAlpha(creature, level, g.alpha());
            } else if (g.radiant()) {
                radiant(mob);
            } else {
                plainTraits(mob);
                if (boss && mob instanceof CreatureVisualsHolder holder) {
                    holder.visuals().setPhase(1);
                }
                if (!boss && g.alpha().isEmpty()) {
                    creatures++;
                }
            }
            GalleryDisplays.label(level, w.ex(g.x() + 0.5), w.ey(4 + g.hover()), w.ez(g.z() - 0.5),
                    Component.translatable("entity.nerocreatures." + g.entity()));
            w.entities++;
        }
        return creatures;
    }

    private static void gridItems(W w) {
        ServerLevel level = w.level;
        List<int[]> blocks = MenagerieLayout.gridBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            String id = MenagerieLayout.HABITAT_BLOCKS.get(i);
            w.set(blocks.get(i)[0], 1, blocks.get(i)[1], "nerocreatures:" + id);
        }
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if ("nerocreatures".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                items.add(item);
            }
        }
        items.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).getPath().compareTo(BuiltInRegistries.ITEM.getKey(b).getPath()));
        for (int i = 0; i < items.size(); i++) {
            int row = i / 13;
            int col = i % 13;
            double x = -10 - 2 * row + 0.5;
            double z = -12 + 2 * col + 0.5;
            if (GalleryDisplays.stand(level, w.ex(x), w.ey(1), w.ez(z), 90.0F, new ItemStack(items.get(i)), false) != null) {
                w.items++;
                w.entities++;
            }
        }
    }

    // --- streets, plaza, lights ------------------------------------------------------------------

    private static void streets(W w) {
        for (int x = -55; x <= 55; x++) {
            if (Math.abs(x) <= 4) {
                continue;
            }
            for (int z : new int[] {-28, -3, 3, 28}) {
                w.set(x, 0, z, GalleryPalette.KERB);
            }
        }
        for (int x = -48; x <= 48; x += 8) {
            for (int z : new int[] {-31, 0, 31}) {
                w.set(x, 0, z, GalleryPalette.STREET_LAMP);
            }
        }
        for (int z = -32; z <= 32; z += 8) {
            w.set(0, 0, z, GalleryPalette.STREET_LAMP);
        }
        w.fill(-1, 0, -1, 1, 0, 1, GalleryPalette.PLAZA_CENTRE);
        for (int sx : new int[] {-3, 3}) {
            for (int sz : new int[] {-2, 2}) {
                w.set(sx, 0, sz, GalleryPalette.STREET_LAMP);
            }
        }
        for (int x = -54; x <= 54; x += 6) {
            for (int z : new int[] {-31, 0, 31}) {
                w.set(x, 5, z, GalleryPalette.LIGHT);
            }
        }
        for (int z = -30; z <= 30; z += 6) {
            w.set(0, 5, z, GalleryPalette.LIGHT);
        }
    }

    // --- bays ------------------------------------------------------------------------------------

    private static void bay(W w, MenagerieLayout.Bay bay) {
        GalleryPalette p = GalleryPalette.of(bay.palette());
        MenagerieLayout.Row row = bay.row();
        int zFront = row.z(0);
        int zBack = row.z(11);
        w.fill(bay.minX(), 0, zFront, bay.maxX(), 0, zBack, p.floor());
        w.fill(bay.minX(), 0, zFront, bay.maxX(), 0, zFront, p.threshold());
        for (int x = bay.minX(); x <= bay.maxX(); x++) {
            for (int y = 1; y <= 5; y++) {
                boolean speckle = Math.floorMod(x * 7 + y * 13, 11) == 0;
                w.set(x, y, zBack, speckle ? p.wallAlt() : p.wall());
            }
        }
        if (Math.abs(bay.outerX()) == 54) {
            int endX = bay.outerX() < 0 ? -55 : 55;
            w.fill(endX, 1, zFront, endX, 5, zBack, p.wall());
        }
        if (Math.abs(bay.innerX()) > 5) {
            w.fill(bay.innerX(), 1, row.z(9), bay.innerX(), 5, zBack, p.wall());
        }
        if ("traits".equals(bay.id())) {
            w.fill(bay.minX(), 2, zBack, bay.maxX(), 4, zBack, "minecraft:white_stained_glass");
            w.fill(bay.minX(), 0, row.z(10), bay.maxX(), 0, row.z(10), "minecraft:sea_lantern");
        }
        if ("utility".equals(bay.palette()) || "traits".equals(bay.id())) {
            for (int x = bay.minX() + 2; x <= bay.maxX(); x += 5) {
                w.set(x, 5, row.z(3), GalleryPalette.LIGHT);
                w.set(x, 5, row.z(9), GalleryPalette.LIGHT);
            }
        }
    }

    /** One creature slot: floor dressing, plate, sign, stand, props and the exhibit. */
    private static boolean slot(W w, MenagerieLayout.Slot slot) {
        MenagerieLayout.Bay bay = MenagerieLayout.bay(slot.bay()).orElse(null);
        if (bay == null) {
            return false;
        }
        GalleryPalette p = GalleryPalette.of(bay.palette());
        MenagerieLayout.Row row = bay.row();
        int cx = slot.x();
        boolean leviathan = slot.suffix().equals("drift_leviathan");
        // inlay, lamps, lights
        w.fill(cx - 1, 0, row.z(5), cx + 1, 0, row.z(7), p.inlay());
        for (int dx : new int[] {-2, 2}) {
            w.set(cx + dx, 0, row.z(3), p.lamp());
            if ("nyxara".equals(bay.palette())) {
                w.set(cx + dx, 1, row.z(3), "minecraft:end_rod");
            }
        }
        w.set(cx, 5, row.z(3), GalleryPalette.LIGHT);
        w.set(cx, 5, row.z(9), GalleryPalette.LIGHT);
        // diorama, at most 2 high and never above F+2 in the middle column
        if (leviathan) {
            for (int x = 32; x <= 52; x++) {
                for (int d = 2; d <= 10; d++) {
                    if (Math.floorMod(x * 7 + d * 13, 5) == 0) {
                        w.set(x, 1, row.z(d), "minecraft:moss_carpet");
                    }
                }
            }
        } else if (!p.diorama().isEmpty()) {
            List<String> dio = p.diorama();
            for (int dx = -2; dx <= 2; dx++) {
                for (int d = 8; d <= 10; d++) {
                    int k = Math.floorMod((cx + dx) * 7 + d * 13, 11);
                    if (k % 2 == 0) {
                        w.set(cx + dx, 1, row.z(d), dio.get(0));
                    } else if (k % 3 == 1) {
                        w.set(cx + dx, 1, row.z(d), dio.get(Math.min(1, dio.size() - 1)));
                    } else if (k == 5 && dx != 0) {
                        w.set(cx + dx, 1, row.z(d), dio.get(Math.min(1, dio.size() - 1)));
                        w.set(cx + dx, 2, row.z(d), dio.get(dio.size() - 1));
                    }
                }
            }
        }
        if (!slot.isAlpha()) {
            w.upd(slot.plateX(), 1, slot.plateZ(), GalleryPalette.PLATE);
        }
        sign(w, slot.signX(), 1, slot.signZ(), standing(p, row), p.glowText(), signLines(slot));
        // the stand: spawn egg (base) or guaranteed alpha drop (alpha)
        ItemStack shown = slot.isAlpha() ? alphaDrop(w.level, slot.entity())
                : item(MenagerieLayout.standItem(slot.entity()));
        if (!shown.isEmpty()) {
            if (GalleryDisplays.stand(w.level, w.ex(slot.standX() + 0.5), w.ey(1), w.ez(slot.signZ() + 0.5), row.yaw(),
                    shown, false) != null) {
                w.items++;
                w.entities++;
            }
        } else {
            w.missing.add(slot.isAlpha() ? slot.entity() + " alpha drop" : MenagerieLayout.standItem(slot.entity()));
        }
        props(w, slot, row, p);
        Mob mob = GalleryDisplays.spawn(w.level, slot.entity(), w.ex(slot.entityX()), w.ey(1), w.ez(slot.entityZ()),
                slot.yaw(), slot.hover(), slot.suffix());
        if (mob == null) {
            w.missing.add(slot.entity());
            return false;
        }
        w.mobs++;
        w.entities++;
        dressExhibit(w, slot, mob);
        return true;
    }

    private static void dressExhibit(W w, MenagerieLayout.Slot slot, Mob mob) {
        CreatureVisuals v = mob instanceof CreatureVisualsHolder holder ? holder.visuals() : null;
        plainTraits(mob);
        if (slot.isAlpha()) {
            if (mob instanceof NeroCreatureEntity creature) {
                AlphaSystem.forceAlpha(creature, w.level, slot.alpha());
            }
            if (v != null && slot.entity().equals("cache_mimic")) {
                v.setForm(MimicRules.FORM_AWAKE);
            }
            return;
        }
        if (MenagerieLayout.HIDDEN_AT_REST.contains(slot.suffix())) {
            if (mob instanceof FrostLurker lurker) {
                lurker.setLurking(true);
            }
            if (v != null) {
                v.holdPose(VisualPose.REST);
            }
        }
        if (v != null && slot.entity().equals("lode_tortoise")) {
            v.setStage(3);
        }
        if (slot.entity().equals("bloom_host")) {
            MenagerieLayout.Row row = slot.row();
            Mob sporeling = GalleryDisplays.spawn(w.level, "bloom_host", w.ex(slot.entityX() + MenagerieLayout.SPORELING_DX),
                    w.ey(1), w.ez(row.z(MenagerieLayout.SPORELING_DEPTH) + 0.5), slot.yaw(), 0, slot.suffix());
            if (sporeling instanceof BloomHost host) {
                host.becomeGallerySporeling();
                w.mobs++;
                w.entities++;
            }
        }
    }

    /** Species props and mod blocks in context; all inside the slot's box. */
    private static void props(W w, MenagerieLayout.Slot slot, MenagerieLayout.Row row, GalleryPalette p) {
        int cx = slot.x();
        switch (slot.suffix()) {
            case "lumen_moth" -> w.set(cx, 1, row.z(9), "nerocreatures:lure_beacon");
            case "strata_hound" -> {
                w.set(cx + 1, 1, row.z(8), "minecraft:iron_ore");
                w.set(cx + 2, 1, row.z(6), "nerocreatures:nest");
            }
            case "slag_beetle" -> w.set(cx + 2, 1, row.z(6), "nerocreatures:incubator");
            case "magma_wader" -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int d = 5; d <= 7; d++) {
                        w.set(cx + dx, 0, row.z(d), w.supported(cx + dx, row.z(d)) ? "minecraft:lava"
                                : "minecraft:magma_block");
                    }
                }
            }
            case "rime_yak" -> w.set(cx + 2, 1, row.z(6), "nerocreatures:feeding_trough");
            case "frost_lurker" -> w.set(cx, 1, row.z(6), "minecraft:snow[layers=2]");
            case "spore_drifter" -> {
                for (int dx = -1; dx <= 1; dx++) {
                    w.set(cx + dx, 0, row.z(6), "minecraft:farmland[moisture=7]");
                    w.upd(cx + dx, 1, row.z(6), "minecraft:wheat[age=2]");
                }
                if (w.supported(cx, row.z(7))) {
                    w.set(cx, 0, row.z(7), "minecraft:water");
                    w.set(cx, 1, row.z(7), "minecraft:mud_brick_slab");
                }
                w.set(cx + 2, 1, row.z(6), "nerocreatures:terrarium");
            }
            case "crystal_golem" -> w.set(cx, 3, row.z(10), "nerocreatures:trophy_plaque[facing="
                    + (row.toStreet() > 0 ? "south" : "north") + "]");
            case "terraforming_drone" -> {
                w.set(cx, 0, row.z(6), "minecraft:grass_block");
                w.upd(cx, 1, row.z(6), "minecraft:oak_sapling");
            }
            case "scrap_hulk" -> w.set(cx - 2, 1, row.z(8), "nerocreatures:sonic_ward");
            case "void_manta" -> w.set(cx - 3, 1, row.z(6), "nerocreatures:nest");
            default -> {
            }
        }
    }

    // --- utility bays ----------------------------------------------------------------------------

    private static void utilityBays(W w) {
        GalleryPalette u = GalleryPalette.of("utility");
        String sign = u.sign();
        // Drops: a stepped stand
        for (MenagerieLayout.Stand st : MenagerieLayout.dropStands()) {
            if (st.y() > 1) {
                w.fill(st.x(), 1, st.z(), st.x(), st.y() - 1, st.z(), "minecraft:smooth_stone");
            }
            itemStand(w, st, MenagerieLayout.Row.C.yaw());
        }
        for (int r = 0; r < 5; r++) {
            sign(w, 40, 1, MenagerieLayout.Row.C.z(2 + 2 * r), sign + "[rotation=0]", false,
                    keys("gallery.nerocreatures.sign.drops_row." + r));
        }
        // Gear
        w.fill(-53, 1, MenagerieLayout.Row.B.z(5), -39, 1, MenagerieLayout.Row.B.z(5), "minecraft:smooth_stone");
        for (MenagerieLayout.Stand st : MenagerieLayout.GEAR) {
            ItemStack stack = item(st.item());
            if ("filled".equals(st.variant()) && !stack.isEmpty()) {
                CapturedCreature.of("nerocreatures:lumen_moth", new CompoundTag(), Optional.empty(), false).write(stack);
            }
            if (!stack.isEmpty() && GalleryDisplays.stand(w.level, w.ex(st.x() + 0.5), w.ey(st.y()), w.ez(st.z() + 0.5),
                    MenagerieLayout.Row.B.yaw(), stack, true) != null) {
                w.items++;
                w.entities++;
            } else if (stack.isEmpty()) {
                w.missing.add(st.item());
            }
            String key = "gallery.nerocreatures.sign.item." + st.item() + ("filled".equals(st.variant()) ? "_filled" : "");
            Component name = Component.translatable("item.nerocreatures." + st.item());
            if (st.y() == 1) {
                sign(w, st.x(), 1, MenagerieLayout.Row.B.z(1), sign + "[rotation=8]", false,
                        name, Component.translatable(key + ".a"), Component.translatable(key + ".b"), Component.empty());
            } else {
                sign(w, st.x(), 1, MenagerieLayout.Row.B.z(4), u.wallSign() + "[facing=north]", false,
                        name, Component.translatable(key + ".a"), Component.translatable(key + ".b"), Component.empty());
            }
        }
        sign(w, -37, 1, MenagerieLayout.Row.B.z(1), sign + "[rotation=8]", false, keys("gallery.nerocreatures.sign.gear_note"));
        w.set(-36, 2, MenagerieLayout.Row.B.z(10), "minecraft:target");
        // Hatchery
        w.fill(41, 1, MenagerieLayout.Row.B.z(5), 53, 1, MenagerieLayout.Row.B.z(5), "minecraft:smooth_stone");
        for (MenagerieLayout.Stand st : MenagerieLayout.eggStands()) {
            ItemStack stack = item(st.item());
            if ("radiant".equals(st.variant()) && !stack.isEmpty()) {
                String species = "nerocreatures:" + st.item().substring(0, st.item().length() - "_egg".length());
                new EggData(species, TraitSet.of("radiant", "calm")).write(stack);
            }
            if (stack.isEmpty()) {
                w.missing.add(st.item());
                continue;
            }
            if (GalleryDisplays.stand(w.level, w.ex(st.x() + 0.5), w.ey(st.y()), w.ez(st.z() + 0.5),
                    MenagerieLayout.Row.B.yaw(), stack, false) != null) {
                w.items++;
                w.entities++;
            }
        }
        for (int i = 0; i < MenagerieLayout.EGGS.size(); i++) {
            String egg = MenagerieLayout.EGGS.get(i);
            sign(w, 42 + 2 * i, 1, MenagerieLayout.Row.B.z(1), sign + "[rotation=8]", false,
                    Component.translatable("item.nerocreatures." + egg),
                    Component.translatable("gallery.nerocreatures.sign.egg_food"),
                    Component.translatable("gallery.nerocreatures.sign.egg." + egg), Component.empty());
        }
        w.set(44, 1, MenagerieLayout.Row.B.z(8), "nerocreatures:incubator");
        w.set(47, 1, MenagerieLayout.Row.B.z(8), "nerocreatures:nest");
        w.set(50, 1, MenagerieLayout.Row.B.z(8), "nerocreatures:feeding_trough");
        // Habitat Blocks
        w.fill(-53, 1, MenagerieLayout.BLOCKS_Z, -35, 1, MenagerieLayout.BLOCKS_Z, "minecraft:smooth_stone");
        for (int i = 0; i < MenagerieLayout.HABITAT_BLOCKS.size(); i++) {
            String id = MenagerieLayout.HABITAT_BLOCKS.get(i);
            int x = MenagerieLayout.BLOCKS_FIRST_X + 2 * i;
            String spec = "nerocreatures:" + id + (id.equals("trophy_plaque") ? "[facing=south]" : "");
            if (state(spec).isAir()) {
                w.missing.add(id);
            }
            w.set(x, 2, MenagerieLayout.BLOCKS_Z, spec);
            BlockEntity be = w.level.getBlockEntity(w.at(x, 2, MenagerieLayout.BLOCKS_Z));
            if (be instanceof HabitatBlockEntity habitat) {
                if (id.equals("trophy_plaque")) {
                    habitat.showForGallery(item("apex_trophy"));
                } else if (id.equals("terrarium")) {
                    ItemStack pod = item("capture_pod");
                    if (!pod.isEmpty()) {
                        CapturedCreature.of("nerocreatures:lumen_moth", new CompoundTag(), Optional.empty(), false)
                                .write(pod);
                        habitat.showForGallery(pod);
                    }
                }
            }
            String key = "gallery.nerocreatures.sign.block." + id;
            sign(w, x, 1, MenagerieLayout.Row.E.z(2), sign + "[rotation=0]", false,
                    Component.translatable("block.nerocreatures." + id), Component.translatable(key + ".a"),
                    Component.translatable(key + ".b"), Component.translatable(id.equals("apex_altar")
                            ? "gallery.nerocreatures.sign.altar_note" : "gallery.nerocreatures.sign.use_it"));
        }
        w.upd(MenagerieLayout.CRUST_PLATE_X, 1, MenagerieLayout.CRUST_PLATE_Z, GalleryPalette.PLATE);
        sign(w, -35, 1, MenagerieLayout.Row.E.z(2), sign + "[rotation=0]", false,
                Component.translatable("block.nerocreatures.ephemeral_crust"),
                Component.translatable("gallery.nerocreatures.sign.block.ephemeral_crust.a"),
                Component.translatable("gallery.nerocreatures.sign.block.ephemeral_crust.b"),
                Component.translatable("gallery.nerocreatures.sign.plate"));
        // Field Scanner kiosk
        MenagerieLayout.Stand scanner = MenagerieLayout.KIOSK_SCANNER;
        ItemStack scan = item(scanner.item());
        if (!scan.isEmpty() && GalleryDisplays.stand(w.level, w.ex(scanner.x() + 0.5), w.ey(1), w.ez(scanner.z() + 0.5),
                MenagerieLayout.Row.D.yaw(), scan, true) != null) {
            w.items++;
            w.entities++;
        }
        w.set(-52, 1, MenagerieLayout.Row.D.z(2), "minecraft:lectern");
        int[] kx = {-54, -51, -50};
        for (int i = 0; i < 3; i++) {
            sign(w, kx[i], 1, MenagerieLayout.Row.D.z(1), sign + "[rotation=8]", false,
                    keys("gallery.nerocreatures.sign.kiosk." + (i + 1)));
        }
    }

    private static void itemStand(W w, MenagerieLayout.Stand st, float yaw) {
        ItemStack stack = item(st.item());
        if (stack.isEmpty()) {
            w.missing.add(st.item());
            return;
        }
        if (GalleryDisplays.stand(w.level, w.ex(st.x() + 0.5), w.ey(st.y()), w.ez(st.z() + 0.5), yaw, stack, false) != null) {
            w.items++;
            w.entities++;
        }
    }

    // --- boss hall -------------------------------------------------------------------------------

    private static final String[][] ALCOVE = {
            // floor, speckle a, speckle b, dais
            {"minecraft:polished_blackstone", "minecraft:basalt", "minecraft:netherrack", "minecraft:polished_basalt"},
            {"minecraft:snow_block", "minecraft:blue_ice", "minecraft:blue_ice", "minecraft:packed_ice"},
            {"minecraft:moss_block", "minecraft:mud", "minecraft:mud", "minecraft:mud_bricks"},
            {"minecraft:calcite", "minecraft:amethyst_block", "minecraft:amethyst_block", "minecraft:smooth_basalt"}};

    private static int bossHall(W w) {
        w.fill(-50, 0, -52, 50, 0, -35, "minecraft:deepslate_bricks");
        for (int a = 0; a < 4; a++) {
            int s = MenagerieLayout.ALCOVE_S[a];
            for (int x = s; x <= s + 22; x++) {
                for (int z = -51; z <= -35; z++) {
                    String block = ALCOVE[a][0];
                    if (Math.floorMod(x * 7 + z * 13, 11) == 0) {
                        block = ALCOVE[a][1];
                    } else if (Math.floorMod(x * 5 - z * 3, 7) == 0) {
                        block = ALCOVE[a][2];
                    }
                    w.set(x, 0, z, block);
                }
            }
        }
        w.fill(-50, 1, -52, 50, 8, -52, "minecraft:polished_deepslate");
        for (int x : new int[] {-50, -25, 0, 25, 50}) {
            w.fill(x, 1, -52, x, 8, -40, "minecraft:polished_deepslate");
        }
        // set pieces
        int s0 = MenagerieLayout.ALCOVE_S[0];
        for (int px : new int[] {s0 + 7, s0 + 15}) {
            w.fill(px, 1, -48, px, 4, -48, "minecraft:crying_obsidian");
            w.set(px, 5, -48, "minecraft:glowstone");
        }
        int s1 = MenagerieLayout.ALCOVE_S[1];
        for (int[] pillar : new int[][] {{s1 + 7, -41, 3}, {s1 + 15, -41, 1}, {s1 + 7, -47, 3}, {s1 + 15, -47, 1}}) {
            w.fill(pillar[0], 1, pillar[1], pillar[0], pillar[2], pillar[1], "minecraft:packed_ice");
        }
        int s3 = MenagerieLayout.ALCOVE_S[3];
        for (int px : new int[] {s3 + 7, s3 + 15}) {
            w.fill(px, 1, -41, px, 2, -41, "minecraft:amethyst_block");
            w.set(px, 3, -41, "minecraft:amethyst_cluster");
        }
        int poses = 0;
        for (MenagerieLayout.BossPose pose : MenagerieLayout.BOSS_POSES) {
            int a = pose.alcove();
            int x = pose.x();
            w.fill(x - 2, 1, -46, x + 2, 1, -42, ALCOVE[a][3]);
            if (a == 2) {
                for (int dx = -3; dx <= 3; dx++) {
                    w.set(x + dx, 1, -47, "minecraft:mangrove_roots");
                    w.set(x + dx, 1, -41, "minecraft:mangrove_roots");
                }
            }
            if (a == 3) {
                w.fill(x - 2, 1, -47, x + 2, 3, -47, "minecraft:tinted_glass");
            }
            w.set(x, 9, -44, GalleryPalette.LIGHT);
            w.upd(pose.plateX(), 1, pose.plateZ(), GalleryPalette.PLATE);
            boolean glow = a == 0;
            sign(w, pose.signX(), 1, pose.plateZ(), "minecraft:dark_oak_sign[rotation=0]", glow,
                    Component.translatable("entity.nerocreatures." + pose.boss()),
                    Component.translatable("gallery.nerocreatures.sign.phase", pose.phase()),
                    Component.translatable("boss.nerocreatures." + pose.boss() + "." + pose.phaseId()),
                    Component.translatable("gallery.nerocreatures.sign." + pose.boss() + ".p" + pose.phase()));
            Mob boss = GalleryDisplays.spawn(w.level, pose.boss(), w.ex(x + 0.5), w.ey(2), w.ez(pose.z() + 0.5), 0.0F, 0,
                    pose.suffix());
            if (boss == null) {
                w.missing.add(pose.boss());
                continue;
            }
            w.mobs++;
            w.entities++;
            poses++;
            if (boss instanceof CreatureVisualsHolder holder) {
                CreatureVisuals v = holder.visuals();
                v.setPhase(pose.phase()); // the synced phase is the fight's phase number, 1..3
                if (pose.boss().equals("facet_colossus")) {
                    v.setVariant(FacetRules.visualVariant(false, pose.phase() - 1));
                    if (pose.phase() >= 2) {
                        int mask = 0b010100;
                        v.setForm(FacetRules.decoyForm(mask));
                        v.setStage(FacetRules.decoyStage(mask));
                    }
                }
            }
        }
        for (int a = 0; a < 4; a++) {
            int s = MenagerieLayout.ALCOVE_S[a];
            w.fill(s + 7, 1, -38, s + 7, 2, -38, "minecraft:polished_deepslate");
            w.set(s + 7, 3, -38, "nerocreatures:trophy_plaque[facing=south]");
            if (w.level.getBlockEntity(w.at(s + 7, 3, -38)) instanceof HabitatBlockEntity plaque) {
                plaque.showForGallery(item("apex_trophy"));
            }
        }
        for (MenagerieLayout.Stand st : MenagerieLayout.bossStands()) {
            itemStand(w, st, 0.0F);
        }
        int s2 = MenagerieLayout.ALCOVE_S[2];
        for (int i = 0; i < 2; i++) {
            int[] off = MenagerieLayout.POD_OFFSETS[i];
            Mob pod = GalleryDisplays.spawn(w.level, "bloom_host", w.ex(s2 + off[0] + 0.5), w.ey(1), w.ez(off[1] + 0.5),
                    0.0F, 0, MenagerieLayout.POD_SUFFIXES.get(i));
            if (pod instanceof BloomHost host) {
                host.becomePod();
                w.mobs++;
                w.entities++;
            }
        }
        return poses;
    }

    // --- paddock ---------------------------------------------------------------------------------

    private static void paddock(W w) {
        for (int x = -55; x <= 55; x++) {
            for (int z = 36; z <= 51; z++) {
                if (x == 0 || Math.abs(x) == 55) {
                    continue;
                }
                boolean loper = x < 0;
                String block;
                if (loper) {
                    block = Math.floorMod(x * 7 + z * 13, 11) < 6 && w.supported(x, z) ? "minecraft:red_sand"
                            : "minecraft:smooth_red_sandstone";
                } else {
                    block = Math.floorMod(x * 3 + z * 5, 13) == 0 ? "minecraft:end_stone" : "minecraft:black_concrete";
                }
                w.set(x, 0, z, block);
            }
        }
        // terracotta mounds and end-rod posts
        for (int[] m : new int[][] {{-45, 40, 1}, {-12, 48, 2}, {-28, 49, 1}}) {
            w.fill(m[0] - 1, 1, m[1] - 1, m[0] + 1, m[2], m[1] + 1, "minecraft:terracotta");
        }
        for (int[] e : new int[][] {{10, 40}, {45, 40}, {12, 49}, {44, 49}, {28, 50}}) {
            w.set(e[0], 1, e[1], "minecraft:end_stone");
            w.set(e[0], 2, e[1], "minecraft:end_rod");
        }
        // fences
        for (int x = -55; x <= 55; x++) {
            boolean gate = (x >= -28 && x <= -27) || (x >= 27 && x <= 28);
            w.upd(x, 1, 35, gate ? "minecraft:dark_oak_fence_gate[facing=north]" : "minecraft:dark_oak_fence");
            w.upd(x, 1, 52, "minecraft:dark_oak_fence");
        }
        for (int z = 36; z <= 51; z++) {
            w.upd(-55, 1, z, "minecraft:dark_oak_fence");
            w.upd(55, 1, z, "minecraft:dark_oak_fence");
            w.upd(0, 1, z, "minecraft:dark_oak_fence");
        }
        for (int x = -54; x <= 54; x += 6) {
            for (int z = 38; z <= 50; z += 6) {
                w.set(x, 6, z, GalleryPalette.LIGHT);
            }
        }
        for (MenagerieLayout.PaddockMount m : MenagerieLayout.PADDOCK) {
            Mob mob = GalleryDisplays.spawn(w.level, m.entity(), w.ex(m.entityX()), w.ey(1), w.ez(m.entityZ()), m.yaw(),
                    m.hover(), m.suffix());
            if (mob == null) {
                w.missing.add(m.entity());
                continue;
            }
            mob.addTag(MenagerieLayout.PADDOCK_TAG);
            w.mobs++;
            w.entities++;
            plainTraits(mob);
            if (mob instanceof MountCreature mount) {
                mount.showSaddledForGallery(m.panniers());
            }
            sign(w, m.x(), 1, 34, "minecraft:oak_sign[rotation=8]", false,
                    Component.translatable("entity.nerocreatures." + m.entity()),
                    Component.translatable("gallery.nerocreatures.sign.mount.a"),
                    Component.translatable("gallery.nerocreatures.sign.mount.b"),
                    Component.translatable("gallery.nerocreatures.sign.mount.c"));
        }
    }

    // --- trait wall ------------------------------------------------------------------------------

    private static void traitWall(W w) {
        for (MenagerieLayout.TraitDisplay t : MenagerieLayout.TRAIT_WALL) {
            w.fill(t.minX(), 1, MenagerieLayout.TRAIT_Z, t.maxX() - 1, 1, MenagerieLayout.TRAIT_Z, "minecraft:smooth_quartz");
            Mob mob = GalleryDisplays.spawn(w.level, t.entity(), w.ex(t.x() + 0.5), w.ey(2), w.ez(MenagerieLayout.TRAIT_Z + 0.5),
                    0.0F, t.hover(), t.suffix());
            if (mob == null) {
                w.missing.add(t.entity() + " (radiant)");
            } else {
                w.mobs++;
                w.entities++;
                radiant(mob);
            }
            sign(w, t.x(), 1, MenagerieLayout.TRAIT_SIGN_Z, "minecraft:oak_sign[rotation=0]", false,
                    Component.translatable("entity.nerocreatures." + t.entity()),
                    Component.translatable("trait.nerocreatures.radiant"),
                    Component.translatable("gallery.nerocreatures.sign.radiant.a"),
                    Component.translatable("gallery.nerocreatures.sign.radiant.b"));
        }
        for (int i = 0; i < MenagerieLayout.TRAITS.size(); i++) {
            String trait = MenagerieLayout.TRAITS.get(i);
            sign(w, 54, 2 + i / 4, 18 + i % 4, "minecraft:oak_wall_sign[facing=west]", false,
                    Component.translatable("trait.nerocreatures." + trait),
                    Component.translatable("gallery.nerocreatures.sign.trait." + trait + ".a"),
                    Component.translatable("gallery.nerocreatures.sign.trait." + trait + ".b"), Component.empty());
        }
    }

    // --- registry walk: anything not placed elsewhere --------------------------------------------

    private static void extraItems(W w) {
        Set<String> shown = MenagerieLayout.itemsShown();
        int n = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (!"nerocreatures".equals(key.getNamespace()) || shown.contains(key.getPath())) {
                continue;
            }
            if (n >= 7) {
                w.missing.add(key.getPath());
                continue;
            }
            if (GalleryDisplays.stand(w.level, w.ex(41 + 2 * n + 0.5), w.ey(6), w.ez(MenagerieLayout.Row.C.z(11) + 1.5), 0.0F,
                    new ItemStack(item), false) != null) {
                w.items++;
                w.entities++;
            }
            n++;
        }
    }

    /** The self-check: every roster id, mod block and mod item is placed, else a red line names it. */
    private static void selfCheck(W w) {
        Set<String> entities = MenagerieLayout.entitiesShown();
        for (EntityTypeKey k : modEntityTypes()) {
            if (!entities.contains(k.path())) {
                w.missing.add(k.path());
            }
        }
        Set<String> blocks = MenagerieLayout.blocksShown();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier key = BuiltInRegistries.BLOCK.getKey(block);
            if ("nerocreatures".equals(key.getNamespace()) && !blocks.contains(key.getPath())) {
                w.missing.add(key.getPath());
            }
        }
    }

    private record EntityTypeKey(String path) {
    }

    private static List<EntityTypeKey> modEntityTypes() {
        List<EntityTypeKey> out = new ArrayList<>();
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            out.add(new EntityTypeKey(entry.id()));
        }
        return out;
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static ItemStack item(String path) {
        Identifier key = Identifier.fromNamespaceAndPath("nerocreatures", path);
        return BuiltInRegistries.ITEM.containsKey(key) ? new ItemStack(BuiltInRegistries.ITEM.getValue(key))
                : ItemStack.EMPTY;
    }

    private static ItemStack alphaDrop(ServerLevel level, String entity) {
        List<AlphaDrop> drops = AlphaData.get(level.getServer()).dropsFor("nerocreatures:" + entity);
        if (drops.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier key = Identifier.tryParse(drops.get(0).item());
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
    }

    /** Fixed, non-Radiant traits so a display never rolls a random (possibly Radiant) set on its first tick. */
    private static void plainTraits(Mob mob) {
        if (mob instanceof TameableCreature tame) {
            tame.setTraits(TraitSet.of("calm", "hardy"));
        }
    }

    private static void radiant(Mob mob) {
        if (mob instanceof TameableCreature tame) {
            tame.setTraits(TraitSet.of("radiant", "calm"));
        } else if (mob instanceof CreatureVisualsHolder holder) {
            holder.visuals().setRadiant(true);
        }
    }

    private static String standing(GalleryPalette p, MenagerieLayout.Row row) {
        return p.sign() + "[rotation=" + row.signRotation() + "]";
    }

    private static Component[] keys(String base) {
        return new Component[] {Component.translatable(base + ".l1"), Component.translatable(base + ".l2"),
                Component.translatable(base + ".l3"), Component.empty()};
    }

    /** The four sign lines for a creature slot. */
    private static Component[] signLines(MenagerieLayout.Slot slot) {
        String id = slot.entity();
        Component name = id.equals("terraforming_drone")
                ? Component.translatable("gallery.nerocreatures.sign.terraforming_drone.name")
                : Component.translatable("entity.nerocreatures." + id);
        if (slot.isAlpha()) {
            return new Component[] {name, Component.translatable("gallery.nerocreatures.sign.alpha"),
                    Component.translatable("gallery.nerocreatures.alpha." + slot.alpha()),
                    Component.translatable("gallery.nerocreatures.sign.alpha_drop")};
        }
        String kind = BestiaryCatalog.get(id).map(e -> e.kind().key()).orElse("hostile");
        Component kindLine = kind.equals("drone") ? Component.translatable("gallery.nerocreatures.sign.kind.drone")
                : Component.translatable("gui.nerocreatures.bestiary.kind." + kind);
        return new Component[] {name, kindLine, Component.translatable("gallery.nerocreatures.sign." + id + ".a"),
                Component.translatable("gallery.nerocreatures.sign." + id + ".b")};
    }

    /** A waxed sign with four front lines; glowing white text where the palette says so. */
    private static void sign(W w, int x, int y, int z, String spec, boolean glow, Component... lines) {
        w.upd(x, y, z, spec);
        if (!(w.level.getBlockEntity(w.at(x, y, z)) instanceof SignBlockEntity sign)) {
            return;
        }
        Component[] four = new Component[4];
        for (int i = 0; i < 4; i++) {
            four[i] = i < lines.length && lines[i] != null ? lines[i] : Component.empty();
        }
        DyeColor colour = glow ? DyeColor.WHITE : DyeColor.BLACK;
        //? if >=26.3 {
        SignText text = new SignText(List.of(four), List.of(four), colour, glow);
        sign.setText(text, net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
        //?} else {
        /*SignText text = new SignText(four, four.clone(), colour, glow);
        sign.setText(text, true);
        *///?}
        sign.setWaxed(true);
    }

    // --- clear -----------------------------------------------------------------------------------

    /**
     * Steps 4 to 7 of {@code clear} over a box: empty every container so nothing drops, set every
     * non-air block to air from the top down (the layer below F is never touched), discard every
     * tagged entity (the anchor last), then discard item and XP litter. Untagged entities stay.
     *
     * <p>Spread over server ticks like a build; {@code done} gets {blocks cleared, entities removed}.
     */
    public static void startClear(ServerLevel level, BlockPos origin, MenagerieLayout.Box box, Consumer<int[]> done) {
        int[] result = new int[2];
        Job job = new Job(level, new W(level, origin), stats -> done.accept(result));
        bulldozeSteps(job.steps, level, origin, box, result);
        JOBS.add(job);
    }

    /** {@link #startClear}, run to the end at once. */
    public static int[] bulldoze(ServerLevel level, BlockPos origin, MenagerieLayout.Box box) {
        int[] result = new int[2];
        Job job = new Job(level, new W(level, origin), stats -> { });
        bulldozeSteps(job.steps, level, origin, box, result);
        runNow(job);
        return result;
    }

    /**
     * The clear steps: every container in the box emptied first (slab by slab), then every non-air
     * block to air from the top down (slab by slab; the layer below F is never touched), then the
     * tagged entities (anchor last) and item and XP litter. Counts go to {@code result}.
     */
    private static void bulldozeSteps(Deque<Runnable> steps, ServerLevel level, BlockPos origin,
            MenagerieLayout.Box box, int[] result) {
        int minX = origin.getX() + box.minX();
        int maxX = origin.getX() + box.maxX();
        int minZ = origin.getZ() + box.minZ();
        int maxZ = origin.getZ() + box.maxZ();
        int minY = origin.getY() + box.minY();
        int maxY = origin.getY() + box.maxY();
        for (int x0 = minX; x0 <= maxX; x0 += SLAB) {
            final int a = x0;
            final int b = Math.min(maxX, x0 + SLAB - 1);
            steps.add(() -> emptyContainers(level, a, b, minY, maxY, minZ, maxZ));
        }
        for (int x0 = minX; x0 <= maxX; x0 += SLAB) {
            final int a = x0;
            final int b = Math.min(maxX, x0 + SLAB - 1);
            steps.add(() -> result[0] += toAir(level, a, b, minY, maxY, minZ, maxZ));
        }
        AABB aabb = new AABB(minX, minY - 1, minZ, maxX + 1, maxY + 4, maxZ + 1);
        steps.add(() -> {
            result[1] += discardTagged(level, aabb);
            GalleryDisplays.forgetAnchors(level);
        });
    }

    private static void emptyContainers(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be instanceof HabitatBlockEntity habitat) {
                        habitat.emptyForGallery();
                    } else if (be instanceof Clearable clearable) {
                        clearable.clearContent();
                    }
                }
            }
        }
    }

    private static int toAir(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, FLAGS);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    private static int discardTagged(ServerLevel level, AABB aabb) {
        int removed = 0;
        List<Entity> anchors = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, aabb, e -> MenagerieLayout.clearRemoves(
                GalleryDisplays.tags(e), e instanceof ItemEntity || e instanceof ExperienceOrb))) {
            if (GalleryDisplays.tags(entity).contains(MenagerieLayout.ANCHOR_TAG)) {
                anchors.add(entity);
                continue;
            }
            entity.ejectPassengers(); // a paddock rider is put off first
            entity.discard();         // never kill: no death, loot, XP or death event
            removed++;
        }
        for (Entity anchor : anchors) {
            anchor.discard();
            removed++;
        }
        return removed;
    }
}
