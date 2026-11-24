package za.co.neroland.nerocreatures.probe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.budget.BudgetSampler;
import za.co.neroland.nerocreatures.budget.CreatureBudget;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;
import za.co.neroland.nerocreatures.entity.ai.SlagLobRules;
import za.co.neroland.nerocreatures.entity.alpha.AlphaData;
import za.co.neroland.nerocreatures.entity.alpha.AlphaRules;
import za.co.neroland.nerocreatures.entity.alpha.AlphaSystem;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.ArenaCrystalRules;
import za.co.neroland.nerocreatures.entity.boss.MatriarchRules;
import za.co.neroland.nerocreatures.entity.greenxertz.BloomHost;
import za.co.neroland.nerocreatures.entity.greenxertz.BloomRules;
import za.co.neroland.nerocreatures.entity.luna.NoduleRules;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * DEV-ONLY budget probe (0.5.0 creature budget check). Lives in the TEST source set, so it is never in a
 * release jar; it is only added to the dev mod by {@code gradlew :neoforge:<mc>:runBudgetProbe
 * -PbudgetProbe}, which also sets {@code -Dnerocreatures.budgetProbe=true}. Without that property it
 * does nothing.
 *
 * <p>On a throwaway flat dev world with no players it: (A) spawns one of every roster species and
 * measures {@link CreatureBudget} for 600 ticks; (B) builds a 200-creature mixed world (plus vanilla
 * sporeling sources), forces alphas to the cap and stress-rolls the natural alpha path, then measures
 * 1200 ticks with far-tick slowing off (worst case) and 1200 ticks with the default interval. Hard caps
 * (sporelings, swarms, alphas, nodules, ephemeral crust, tethers) are sampled throughout, and creatures
 * that do not move for 600 ticks while not resting or sitting are counted per species. The area is
 * walled with barriers inside a fully ticking force-loaded ring; vanilla no-action timers are reset
 * (as a nearby player would) and waders/leeches are handed vanilla targets. Results go to
 * {@code budget-probe-<mc>.txt} in the run directory and the log, then the server halts.
 *
 * <p>POPIA/GDPR: species ids and numbers only. Entity runtime ids are used in memory to track
 * movement and are never written; no names, UUIDs or coordinates are logged.
 */
@EventBusSubscriber(modid = NeroCreaturesCommon.MOD_ID)
public final class BudgetProbe {

    private static final Logger LOG = LoggerFactory.getLogger("NeroCreatures/BudgetProbe");
    private static final boolean ENABLED = Boolean.getBoolean("nerocreatures.budgetProbe");
    private static final String MC = System.getProperty("nerocreatures.budgetProbe.mc", "unknown");

    /** The walled test area, blocks. A barrier wall stands on its edge so nothing wanders off. */
    private static final int MIN = -48;
    private static final int MAX = 47;
    /** The force-loaded area: the walled area plus a fully ticking ring of chunks around it. */
    private static final int LOAD_MIN = -64;
    private static final int LOAD_MAX = 63;
    private static final int WALL_HEIGHT = 80;
    private static final int AWAKE_EVERY = 20;
    private static final int STIMULUS_EVERY = 40;
    private static final int SPREAD = 44;
    private static final int SAMPLE_EVERY = 20;
    private static final int CRUST_SCAN_EVERY = 100;
    private static final int STUCK_TICKS = 600;
    private static final int WARMUP = 100;
    /** Probe A runs first, so it also warms the JIT up. */
    private static final int A_WARMUP = 400;
    private static final int A_TICKS = 600;
    private static final int B_TICKS = 1200;
    private static final int HARD_TIMEOUT = 20_000;
    private static final String CRUST_ID = "nerocreatures:ephemeral_crust";
    /** Skip probes A and B and run only the gallery check (-PgalleryOnly). */
    private static final boolean GALLERY_ONLY = Boolean.getBoolean("nerocreatures.budgetProbe.galleryOnly");

    private static Run run;

    private BudgetProbe() {
    }

    @SubscribeEvent
    public static void onPre(ServerTickEvent.Pre event) {
        if (ENABLED && run != null) {
            run.preNanos = System.nanoTime();
        }
    }

    @SubscribeEvent
    public static void onPost(ServerTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        long end = System.nanoTime();
        MinecraftServer server = event.getServer();
        if (run == null) {
            run = new Run(server);
        }
        try {
            run.tick(end);
        } catch (RuntimeException e) {
            LOG.error("[budget-probe] FAILED with an exception; halting", e); // log-privacy: ok test-only probe, no players
            run.out.append("PROBE ERROR: ").append(e).append('\n');
            run.finish();
        }
    }

    // -----------------------------------------------------------------------------------------

    private enum Phase { LOAD, A_SPAWN, A_WARM, A_MEASURE, CLEAR, B_SPAWN, B_WARM, B_NEAR, B_FAR,
        G_BUILD, DONE }

    /** Per-entity movement anchor (runtime id keyed, memory only). */
    private static final class Track {
        final String species;
        double x;
        double y;
        double z;
        long lastMoved;

        Track(String species, Entity e, long now) {
            this.species = species;
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
            this.lastMoved = now;
        }
    }

    /** Cap observations for one window. */
    private static final class Caps {
        int maxSporelings;
        int hostsAtMax;
        boolean sporelingFail;
        int maxAlphas;
        int alphaCap;
        boolean alphaFail;
        int placedSwarms;
        int maxSwarms;
        boolean swarmFail;
        int maxNoduleStage;
        boolean noduleFail;
        int maxCrust;
        int crustCap;
        int maxRime;
        int rimeCap;
        int maxPrism;
        int prismCap;
        boolean crustFail;
        int maxTethered;
        int leeches;
        boolean tetherFail;

        boolean failed() {
            return sporelingFail || alphaFail || swarmFail || noduleFail || crustFail || tetherFail;
        }
    }

    private static final class Window {
        final String label;
        final long start;
        long serverNanos;
        int serverTicks;
        int samples;
        final Map<String, Long> popSum = new TreeMap<>();
        final Caps caps = new Caps();
        final Map<Integer, Track> tracks = new HashMap<>();

        Window(String label, long start) {
            this.label = label;
            this.start = start;
        }
    }

    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final StringBuilder out = new StringBuilder();
        Phase phase = Phase.LOAD;
        long phaseStart;
        final long begun;
        long preNanos;
        Window window;
        int placedSwarms;
        int livestock;
        boolean stimulus;
        boolean anyCapFail;
        final GalleryCheck gallery = new GalleryCheck(new GalleryCheck.Host() {
            @Override
            public ServerLevel level() {
                return Run.this.level;
            }

            @Override
            public StringBuilder out() {
                return Run.this.out;
            }

            @Override
            public void fail() {
                Run.this.anyCapFail = true;
            }
        });

        Run(MinecraftServer server) {
            this.server = server;
            this.level = server.overworld();
            this.begun = server.getTickCount();
            this.phaseStart = begun;
            out.append("NeroCreatures budget probe, MC ").append(MC).append(", NeoForge dedicated dev server, ")
                    .append("flat overworld, no players\n");
            Map<String, String> cfg = new HashMap<>();
            cfg.put("nerocreatures:farTickInterval", "1");
            cfg.put("nerocreatures:alphaChanceMultiplier", "10.0");
            ConfigManager.applyServerValues(cfg);
            command("forceload add " + LOAD_MIN + " " + LOAD_MIN + " " + LOAD_MAX + " " + LOAD_MAX);
            LOG.info("[budget-probe] started on {}", MC);
        }

        long now() {
            return server.getTickCount();
        }

        void command(String cmd) {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, cmd);
        }

        void to(Phase next) {
            phase = next;
            phaseStart = now();
            LOG.info("[budget-probe] phase {}", next);
        }

        void tick(long endNanos) {
            long t = now();
            if (t - begun > HARD_TIMEOUT) {
                out.append("PROBE TIMEOUT in phase ").append(phase).append('\n');
                finish();
                return;
            }
            long age = t - phaseStart;
            if (window != null && preNanos != 0L) {
                window.serverNanos += endNanos - preNanos;
                window.serverTicks++;
            }
            if (phase.ordinal() > Phase.A_SPAWN.ordinal() && phase.ordinal() < Phase.G_BUILD.ordinal()) {
                if (t % AWAKE_EVERY == 0) {
                    keepAwake();
                }
                if (stimulus && t % STIMULUS_EVERY == 0) {
                    stimulate();
                }
            }
            switch (phase) {
                case LOAD -> {
                    if (age >= 60 && chunksLoaded()) {
                        if (GALLERY_ONLY) {
                            to(Phase.G_BUILD);
                        } else {
                            buildWall();
                            to(Phase.A_SPAWN);
                        }
                    }
                }
                case A_SPAWN -> {
                    spawnOneOfEach();
                    to(Phase.A_WARM);
                }
                case A_WARM -> {
                    if (age >= A_WARMUP) {
                        openWindow("A: one of every species (far-tick off)", A_TICKS);
                        to(Phase.A_MEASURE);
                    }
                }
                case A_MEASURE -> {
                    sample(t);
                    if (age >= A_TICKS) {
                        closeWindow();
                        clearArea();
                        to(Phase.CLEAR);
                    }
                }
                case CLEAR -> {
                    if (age >= 40) {
                        to(Phase.B_SPAWN);
                    }
                }
                case B_SPAWN -> {
                    spawnMixed();
                    to(Phase.B_WARM);
                }
                case B_WARM -> {
                    if (age >= WARMUP) {
                        openWindow("B: 200-creature mixed world, far-tick OFF (interval 1, worst case)", B_TICKS);
                        to(Phase.B_NEAR);
                    }
                }
                case B_NEAR -> {
                    sample(t);
                    if (age >= B_TICKS) {
                        closeWindow();
                        Map<String, String> cfg = new HashMap<>();
                        cfg.put("nerocreatures:farTickInterval", "4");
                        ConfigManager.applyServerValues(cfg);
                        openWindow("B: 200-creature mixed world, far-tick DEFAULT (interval 4, no player near)", B_TICKS);
                        to(Phase.B_FAR);
                    }
                }
                case B_FAR -> {
                    sample(t);
                    if (age >= B_TICKS) {
                        closeWindow();
                        clearArea();
                        to(Phase.G_BUILD);
                    }
                }
                case G_BUILD -> {
                    if (age >= 20 && gallery.tick()) {
                        finish();
                    }
                }
                case DONE -> {
                }
            }
        }

        boolean chunksLoaded() {
            for (int cx = LOAD_MIN >> 4; cx <= LOAD_MAX >> 4; cx++) {
                for (int cz = LOAD_MIN >> 4; cz <= LOAD_MAX >> 4; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** A barrier wall on the area edge, so walkers and flyers stay in fully ticking chunks. */
        void buildWall() {
            int y0 = surface(0, 0).getY();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = y0; y < y0 + WALL_HEIGHT; y++) {
                for (int i = MIN - 1; i <= MAX + 1; i++) {
                    level.setBlock(p.set(i, y, MIN - 1), Blocks.BARRIER.defaultBlockState(), 2);
                    level.setBlock(p.set(i, y, MAX + 1), Blocks.BARRIER.defaultBlockState(), 2);
                    level.setBlock(p.set(MIN - 1, y, i), Blocks.BARRIER.defaultBlockState(), 2);
                    level.setBlock(p.set(MAX + 1, y, i), Blocks.BARRIER.defaultBlockState(), 2);
                }
            }
        }

        /**
         * With no player online vanilla mobs stop wandering once their no-action timer passes 100
         * ticks (a player within 32 blocks normally resets it). Reset it, so the probe measures
         * creatures behaving as they would with a player around.
         */
        void keepAwake() {
            List<Mob> mobs = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(Mob.class), Mob::isAlive, mobs, Integer.MAX_VALUE);
            for (Mob m : mobs) {
                m.setNoActionTime(0);
            }
        }

        /**
         * Magma Waders and Gravity Leeches only use their ability on a target, and with no player
         * online they have none: give each an untargeted vanilla animal nearby, so slag crust and
         * tethers (and their caps) are exercised.
         */
        void stimulate() {
            List<Mob> mobs = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && m.getTarget() == null
                    && (id(m.getType()).equals("nerocreatures:magma_wader") || id(m.getType()).equals("nerocreatures:gravity_leech")),
                    mobs, Integer.MAX_VALUE);
            for (Mob m : mobs) {
                AABB box = m.getBoundingBox().inflate(16.0D);
                LivingEntity best = null;
                double bestD = Double.MAX_VALUE;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e.isAlive() && !(e instanceof Player) && id(e.getType()).startsWith("minecraft:"))) {
                    double d = e.distanceToSqr(m);
                    if (d < bestD) {
                        bestD = d;
                        best = e;
                    }
                }
                if (best != null) {
                    m.setTarget(best);
                }
            }
        }

        BlockPos surface(int x, int z) {
            return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }

        List<EntityType<? extends Mob>> roster() {
            List<EntityType<? extends Mob>> types = new ArrayList<>();
            for (RegistryEntry<? extends EntityType<? extends Mob>> entry : ModEntities.roster()) {
                types.add(entry.get());
            }
            return types;
        }

        Mob spawn(EntityType<? extends Mob> type, int x, int z) {
            Mob mob = type.spawn(level, surface(x, z), EntitySpawnReason.COMMAND);
            if (mob == null) {
                out.append("  spawn failed: ").append(id(type)).append('\n');
            }
            return mob;
        }

        void spawnOneOfEach() {
            List<EntityType<? extends Mob>> types = roster();
            int i = 0;
            for (EntityType<? extends Mob> type : types) {
                Mob mob = spawn(type, -30 + (i % 7) * 10, -30 + (i / 7) * 10);
                if (mob != null) {
                    // A is "one of every species active": nobody may be eaten or killed before the window.
                    //? if >=26.3 {
                    mob.setPermanentlyInvulnerable(true);
                    //?} else {
                    /*mob.setInvulnerable(true);
                    *///?}
                    if (keepPersistent(id(type))) {
                        mob.setPersistenceRequired();
                    }
                }
                i++;
            }
            placedSwarms = 1;
            out.append("Probe A spawned ").append(types.size()).append(" species, one each\n");
        }

        void spawnMixed() {
            Map<String, Integer> special = new HashMap<>();
            special.put("nerocreatures:cinder_tyrant", 1);
            special.put("nerocreatures:drift_leviathan", 2);
            special.put("nerocreatures:scrap_hulk", 2);
            special.put("nerocreatures:pirate_captain", 2);
            special.put("nerocreatures:static_swarm", 6);
            special.put("nerocreatures:bloom_host", 13);
            int[][] swarmSpots = { { -40, -40 }, { 0, -40 }, { 40, -40 }, { -40, 40 }, { 0, 40 }, { 40, 40 } };
            Random rnd = new Random(20261115L);
            int total = 0;
            livestock = 0;
            int swarmIndex = 0;
            List<EntityType<?>> sources = new ArrayList<>();
            for (String vanilla : List.of("minecraft:chicken", "minecraft:pig", "minecraft:sheep", "minecraft:cow")) {
                sources.add(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(vanilla)));
            }
            for (EntityType<? extends Mob> type : roster()) {
                String id = id(type);
                int n = special.getOrDefault(id, 6);
                for (int k = 0; k < n; k++) {
                    int x;
                    int z;
                    if (id.equals("nerocreatures:static_swarm")) {
                        x = swarmSpots[swarmIndex][0];
                        z = swarmSpots[swarmIndex][1];
                        swarmIndex++;
                    } else {
                        x = rnd.nextInt(SPREAD * 2 + 1) - SPREAD;
                        z = rnd.nextInt(SPREAD * 2 + 1) - SPREAD;
                    }
                    Mob mob = spawn(type, x, z);
                    if (mob == null) {
                        continue;
                    }
                    total++;
                    if (keepPersistent(id)) {
                        mob.setPersistenceRequired();
                    }
                    int around = id.equals("nerocreatures:bloom_host") ? 6
                            : id.equals("nerocreatures:magma_wader") || id.equals("nerocreatures:gravity_leech") ? 2 : 0;
                    if (around > 0) {
                        // Sporeling sources around each host; ability targets around waders and leeches.
                        for (int s = 0; s < around; s++) {
                            EntityType<?> src = sources.get(s % sources.size());
                            int sx = Math.max(MIN + 2, Math.min(MAX - 2, x + rnd.nextInt(7) - 3));
                            int sz = Math.max(MIN + 2, Math.min(MAX - 2, z + rnd.nextInt(7) - 3));
                            if (src.spawn(level, surface(sx, sz), EntitySpawnReason.COMMAND) != null) {
                                livestock++;
                            }
                        }
                    }
                }
            }
            placedSwarms = swarmIndex;
            stimulus = true;
            // Alphas: force exactly the cap, then stress the natural-spawn path (chance x10) on every other
            // eligible hostile; the cap must hold.
            int cap = alphaCap();
            List<NeroCreatureEntity> creatures = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(NeroCreatureEntity.class), c -> true, creatures, Integer.MAX_VALUE);
            int forced = 0;
            int rolled = 0;
            for (NeroCreatureEntity c : creatures) {
                if (!AlphaSystem.eligible(c) || c.isAlpha()) {
                    continue;
                }
                if (forced < cap) {
                    if (AlphaSystem.forceAlpha(c, level, null)) {
                        forced++;
                    }
                } else {
                    AlphaSystem.onFinalizeSpawn(c, level, EntitySpawnReason.NATURAL);
                    rolled++;
                }
            }
            out.append("Probe B spawned ").append(total).append(" NeroCreatures creatures + ").append(livestock)
                    .append(" vanilla animals (sporeling sources and wader/leech targets); alphas forced to cap ").append(forced).append('/').append(cap)
                    .append(", then ").append(rolled).append(" natural alpha rolls at alphaChanceMultiplier=10\n");
            out.append("  alphas loaded right after the stress rolls: ").append(AlphaSystem.countLoaded(level, 64))
                    .append(" (cap ").append(cap).append(")\n");
        }

        /**
         * Wild overworld swarms vanish when the storm ends and wild moths at daybreak (both by
         * design); pin the ones under test so they are measured for the whole window.
         */
        boolean keepPersistent(String id) {
            return id.equals("nerocreatures:static_swarm") || id.equals("nerocreatures:lumen_moth");
        }

        int alphaCap() {
            return AlphaRules.effectiveCap(AlphaData.get(server).settings().maxPerDimension(),
                    NeroCreaturesConfig.ALPHA_MAX_PER_DIMENSION.get());
        }

        void clearArea() {
            List<Entity> all = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(Entity.class), e -> !(e instanceof Player), all, Integer.MAX_VALUE);
            for (Entity e : all) {
                e.discard();
            }
        }

        void openWindow(String label, int length) {
            window = new Window(label, now());
            window.caps.placedSwarms = placedSwarms;
            window.caps.alphaCap = alphaCap();
            CreatureBudget.start(server, length / 20);
            sample(now());
        }

        void sample(long t) {
            Window w = window;
            if (w == null || (t - w.start) % SAMPLE_EVERY != 0) {
                return;
            }
            List<Mob> mobs = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(Mob.class), Mob::isAlive, mobs, Integer.MAX_VALUE);
            Map<String, Integer> pop = new HashMap<>();
            int sporelings = 0;
            int hosts = 0;
            int swarms = 0;
            int waders = 0;
            int matriarchs = 0;
            int colossi = 0;
            int leeches = 0;
            int tethered = 0;
            int noduleMax = 0;
            for (Mob m : mobs) {
                String id = id(m.getType());
                if (id.startsWith(NeroCreaturesCommon.MOD_ID + ":")) {
                    pop.merge(id, 1, Integer::sum);
                    track(w, m, id, t);
                }
                if (m instanceof BloomHost host) {
                    if (host.isSporeling()) {
                        sporelings++;
                    } else if (host.form() == BloomRules.FORM_HOST) {
                        hosts++;
                    }
                }
                switch (id) {
                    case "nerocreatures:static_swarm" -> swarms++;
                    case "nerocreatures:magma_wader" -> waders++;
                    case "nerocreatures:rime_matriarch" -> matriarchs++;
                    case "nerocreatures:facet_colossus" -> colossi++;
                    case "nerocreatures:gravity_leech" -> leeches++;
                    case "nerocreatures:lode_tortoise" -> {
                        if (m instanceof CreatureVisualsHolder h) {
                            noduleMax = Math.max(noduleMax, h.visuals().stage());
                        }
                    }
                    default -> {
                    }
                }
                if (hasCreatureGravityModifier(m)) {
                    tethered++;
                }
            }
            w.samples++;
            for (Map.Entry<String, Integer> e : pop.entrySet()) {
                w.popSum.merge(e.getKey(), (long) e.getValue(), Long::sum);
            }
            Caps c = w.caps;
            if (sporelings > c.maxSporelings) {
                c.maxSporelings = sporelings;
                c.hostsAtMax = hosts;
            }
            if (sporelings > BloomRules.PER_DIMENSION || sporelings > BloomRules.PER_HOST * hosts) {
                c.sporelingFail = true;
            }
            int alphas = AlphaSystem.countLoaded(level, 64);
            c.maxAlphas = Math.max(c.maxAlphas, alphas);
            if (alphas > c.alphaCap) {
                c.alphaFail = true;
            }
            c.maxSwarms = Math.max(c.maxSwarms, swarms);
            if (swarms > c.placedSwarms) {
                c.swarmFail = true;
            }
            c.maxNoduleStage = Math.max(c.maxNoduleStage, noduleMax);
            if (noduleMax > NoduleRules.MAX_STAGE) {
                c.noduleFail = true;
            }
            c.leeches = Math.max(c.leeches, leeches);
            c.maxTethered = Math.max(c.maxTethered, tethered);
            if (tethered > leeches) {
                c.tetherFail = true;
            }
            if ((t - w.start) % CRUST_SCAN_EVERY == 0) {
                int[] crust = countCrust();
                // Each variant against its own placer: slag per wader, rime (pillars + frozen water) per
                // Rime Matriarch, prism crystals per Facet Colossus.
                c.crustCap = Math.max(c.crustCap, SlagLobRules.MAX_LIVE_CRUST * waders);
                c.rimeCap = Math.max(c.rimeCap,
                        (MatriarchRules.MAX_PILLAR_BLOCKS + MatriarchRules.MAX_FROZEN_BLOCKS) * matriarchs);
                c.prismCap = Math.max(c.prismCap, ArenaCrystalRules.HARD_CAP_BLOCKS * colossi);
                c.maxCrust = Math.max(c.maxCrust, crust[0]);
                c.maxRime = Math.max(c.maxRime, crust[1]);
                c.maxPrism = Math.max(c.maxPrism, crust[2]);
                // Crust outlives the wader that threw it until it reverts on its own, so the bound is
                // the window's peak wader count, not the waders alive at this sample.
                if (crust[0] > c.crustCap || crust[1] > c.rimeCap || crust[2] > c.prismCap) {
                    c.crustFail = true;
                }
            }
        }

        void track(Window w, Mob m, String id, long t) {
            Track tr = w.tracks.get(m.getId());
            if (tr == null) {
                w.tracks.put(m.getId(), new Track(id, m, t));
                return;
            }
            boolean resting = (m instanceof TamableAnimal ta && ta.isOrderedToSit())
                    || (m instanceof CreatureVisualsHolder h && h.visuals().pose() == VisualPose.REST)
                    || m.isNoAi();
            double dx = m.getX() - tr.x;
            double dy = m.getY() - tr.y;
            double dz = m.getZ() - tr.z;
            if (resting || dx * dx + dy * dy + dz * dz > 0.0625D) {
                tr.x = m.getX();
                tr.y = m.getY();
                tr.z = m.getZ();
                tr.lastMoved = t;
            }
        }

        boolean hasCreatureGravityModifier(LivingEntity e) {
            AttributeInstance gravity = e.getAttribute(Attributes.GRAVITY);
            if (gravity == null) {
                return false;
            }
            for (AttributeModifier mod : gravity.getModifiers()) {
                //? if >=1.21 {
                if (NeroCreaturesCommon.MOD_ID.equals(mod.id().getNamespace())) {
                //?} else {
                /*if (mod.name().startsWith(NeroCreaturesCommon.MOD_ID + ":")) {
                *///?}
                    return true;
                }
            }
            return false;
        }

        /** Ephemeral crust blocks in the probe area by variant: {slag, rime, prism}. */
        int[] countCrust() {
            int[] n = new int[3];
            int y0 = surface(0, 0).getY();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = MIN; x <= MAX; x++) {
                for (int z = MIN; z <= MAX; z++) {
                    for (int y = y0 - 3; y <= y0 + 12; y++) {
                        p.set(x, y, z);
                        BlockState state = level.getBlockState(p);
                        if (CRUST_ID.equals(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())))
                                && state.hasProperty(EphemeralCrustBlock.VARIANT)) {
                            n[state.getValue(EphemeralCrustBlock.VARIANT).ordinal()]++;
                        }
                    }
                }
            }
            return n;
        }

        void closeWindow() {
            Window w = window;
            long t = now();
            CreatureBudget.stop(server);
            BudgetSampler.Report report = CreatureBudget.report(server);
            window = null;

            out.append("\n=== ").append(w.label).append(" ===\n");
            out.append(String.format(Locale.ROOT, "window: %d ticks measured by CreatureBudget; whole server tick (MSPT) avg %.3f ms over %d ticks%n",
                    report.ticks(), w.serverTicks == 0 ? 0.0D : w.serverNanos / 1.0e6D / w.serverTicks, w.serverTicks));
            out.append(String.format(Locale.ROOT, "TOTAL creature ms/tick: %.3f (%d creature ticks)%n",
                    report.totalMsPerTick(), report.totalCalls()));
            out.append("species | ms/tick | avg alive | ms/tick per individual | calls/tick\n");
            Map<String, BudgetSampler.Row> byName = new HashMap<>();
            String worst = "-";
            double worstPer = -1.0D;
            for (BudgetSampler.Row row : report.rows()) {
                byName.put(row.name(), row);
                double avg = avgPop(w, row.name());
                double per = avg > 0.0D ? row.msPerTick() / avg : 0.0D;
                if (per > worstPer) {
                    worstPer = per;
                    worst = row.name();
                }
                out.append(String.format(Locale.ROOT, "%s | %.4f | %.1f | %.4f%s | %.2f%n", row.name(), row.msPerTick(),
                        avg, per, per > 0.05D ? " OVER" : "", row.callsPerTick()));
            }
            for (String name : w.popSum.keySet()) {
                if (!byName.containsKey(name)) {
                    out.append(name).append(" | NOT MEASURED (alive avg ")
                            .append(String.format(Locale.ROOT, "%.1f", avgPop(w, name)))
                            .append(") - tick not wired to CreatureBudget\n");
                }
            }
            out.append(String.format(Locale.ROOT, "worst per individual: %s %.4f ms/tick%n", worst, worstPer));
            StringBuilder absent = new StringBuilder();
            for (EntityType<? extends Mob> type : roster()) {
                String name = id(type);
                if (!byName.containsKey(name) && !w.popSum.containsKey(name)) {
                    absent.append(name).append(' ');
                }
            }
            out.append("roster species absent for the whole window (died/removed before it): ")
                    .append(absent.length() == 0 ? "none" : absent.toString().trim()).append('\n');

            Caps c = w.caps;
            out.append("caps:\n");
            out.append(String.format(Locale.ROOT, "  sporelings max %d (cap %d/dimension, %d per host; live hosts at that sample %d) %s%n",
                    c.maxSporelings, BloomRules.PER_DIMENSION, BloomRules.PER_HOST, c.hostsAtMax,
                    c.sporelingFail ? "FAIL" : "ok"));
            out.append(String.format(Locale.ROOT, "  alphas max %d (cap %d) %s%n", c.maxAlphas, c.alphaCap, c.alphaFail ? "FAIL" : "ok"));
            out.append(String.format(Locale.ROOT, "  swarms max %d (placed %d, must never grow) %s%n", c.maxSwarms, c.placedSwarms,
                    c.swarmFail ? "FAIL" : "ok"));
            out.append(String.format(Locale.ROOT, "  nodule stage max %d (cap %d) %s%n", c.maxNoduleStage, NoduleRules.MAX_STAGE,
                    c.noduleFail ? "FAIL" : "ok"));
            out.append(String.format(Locale.ROOT,
                    "  ephemeral crust blocks: slag max %d (cap %d = %d per wader), rime max %d (cap %d = %d per matriarch),"
                            + " prism max %d (cap %d = %d per colossus) %s%n",
                    c.maxCrust, c.crustCap, SlagLobRules.MAX_LIVE_CRUST,
                    c.maxRime, c.rimeCap, MatriarchRules.MAX_PILLAR_BLOCKS + MatriarchRules.MAX_FROZEN_BLOCKS,
                    c.maxPrism, c.prismCap, ArenaCrystalRules.HARD_CAP_BLOCKS, c.crustFail ? "FAIL" : "ok"));
            out.append(String.format(Locale.ROOT, "  tethered entities max %d (leeches %d, one each) %s%n", c.maxTethered, c.leeches,
                    c.tetherFail ? "FAIL" : "ok"));
            anyCapFail |= c.failed();

            Map<String, Integer> stuck = new TreeMap<>();
            Map<String, Integer> tracked = new TreeMap<>();
            for (Map.Entry<Integer, Track> e : w.tracks.entrySet()) {
                Entity live = level.getEntity(e.getKey());
                if (live == null || !live.isAlive()) {
                    continue;
                }
                tracked.merge(e.getValue().species, 1, Integer::sum);
                if (t - e.getValue().lastMoved >= STUCK_TICKS) {
                    stuck.merge(e.getValue().species, 1, Integer::sum);
                }
            }
            out.append("stuck (no move >= ").append(STUCK_TICKS).append(" ticks, not resting/sitting), per species: ");
            if (stuck.isEmpty()) {
                out.append("none");
            }
            for (Map.Entry<String, Integer> e : stuck.entrySet()) {
                out.append(e.getKey()).append(' ').append(e.getValue()).append('/')
                        .append(tracked.getOrDefault(e.getKey(), 0)).append("; ");
            }
            out.append('\n');
            LOG.info("[budget-probe] window done: {}", w.label);
        }

        double avgPop(Window w, String name) {
            return w.samples == 0 ? 0.0D : w.popSum.getOrDefault(name, 0L) / (double) w.samples;
        }

        void finish() {
            if (phase == Phase.DONE) {
                return;
            }
            phase = Phase.DONE;
            out.append("\nRESULT: ").append(anyCapFail ? "FAIL (a hard cap or a gallery check failed)" : "caps held").append('\n');
            String text = out.toString();
            try {
                Files.writeString(Path.of("budget-probe-" + MC + ".txt"), text, StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                LOG.error("[budget-probe] could not write the result file", e); // log-privacy: ok test-only probe
            }
            for (String line : text.split("\n")) {
                LOG.info("[budget-probe] {}", line);
            }
            server.halt(false);
        }
    }

    static String id(EntityType<?> type) {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }
}
