package za.co.neroland.nerocreatures.gallery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;

/**
 * The Menagerie, as data: every bay, slot, boss pose, trait-wall display, working row, tour stop and
 * box of the {@code /nerocreatures gallery} scene, as offsets from the invoker's block position
 * ({@code +x} east, {@code +z} south, {@code F} = the floor layer at the invoker's feet).
 *
 * <p>Pure: no Minecraft classes. The server builder ({@link MenagerieBuilder}), the demo plates
 * ({@link GalleryDemos}), the tour, the paddock and the client capture harness all read these tables,
 * so the scene and the camera can never drift apart.
 */
public final class MenagerieLayout {

    private MenagerieLayout() {
    }

    // --- tags ------------------------------------------------------------------------------------

    /** Every entity the gallery places carries this tag; {@code clear} removes by it. */
    public static final String TAG = "nerocreatures_gallery";
    /** Per-exhibit tag prefix; the suffix is the slot id. */
    public static final String SLOT_TAG_PREFIX = "nerocreatures_gallery_slot_";
    /** Floating bay titles; the capture harness strips them by this tag. */
    public static final String LABEL_TAG = "nerocreatures_gallery_label";
    /** The single marker that says where a gallery is. */
    public static final String ANCHOR_TAG = "nerocreatures_gallery_anchor";
    public static final String LAYOUT_MENAGERIE_TAG = "nerocreatures_gallery_layout_menagerie";
    public static final String LAYOUT_GRID_TAG = "nerocreatures_gallery_layout_grid";
    /** Rideable paddock mounts. */
    public static final String PADDOCK_TAG = "nerocreatures_gallery_paddock";

    // --- boxes -----------------------------------------------------------------------------------

    /** An inclusive box of offsets; {@code minY}/{@code maxY} are relative to {@code F}. */
    public record Box(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
        }

        public boolean containsXZ(double x, double z) {
            return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
        }

        public boolean containsBox(Box other, int margin) {
            return other.minX - margin >= minX && other.maxX + margin <= maxX && other.minZ - margin >= minZ
                    && other.maxZ + margin <= maxZ && other.minY >= minY && other.maxY + margin <= maxY;
        }
    }

    public static final Box BUILD_BOX = new Box(-55, 55, -52, 52, 0, 15);
    public static final Box CLEAR_BOX = new Box(-58, 58, -55, 55, 0, 20);
    public static final Box GRID_BUILD_BOX = new Box(-27, 53, -34, 40, 0, 8);
    public static final Box GRID_CLEAR_BOX = new Box(-30, 56, -37, 43, 0, 12);
    /** A 0.4.x scene with no anchor: the old constants, around the invoker. */
    public static final Box LEGACY_CLEAR_BOX = new Box(-40, 58, -44, 36, 0, 16);

    /** {@code gallery} refuses to build with another anchor this close. */
    public static final int NO_STACK_RADIUS = 160;
    /** Tour, demos, plates, the paddock and {@code clear} look this far for an anchor. */
    public static final int ANCHOR_RADIUS = 96;

    // --- budget ----------------------------------------------------------------------------------

    public static final int MAX_MOBS = 96;
    public static final int MAX_ENTITIES = 260;
    public static final int MAX_BLOCKS = 30_000;
    public static final int MAX_RUNNING_DEMOS = 4;

    // --- rows and bays ---------------------------------------------------------------------------

    /** A row of bays; depth {@code d} 0..11 counts from the front edge toward the back wall. */
    public enum Row {
        B(-27, 1, 180.0F), C(-4, -1, 0.0F), D(4, 1, 180.0F), E(27, -1, 0.0F);

        private final int frontZ;
        private final int dir;
        private final float yaw;

        Row(int frontZ, int dir, float yaw) {
            this.frontZ = frontZ;
            this.dir = dir;
            this.yaw = yaw;
        }

        /** The z offset at depth {@code d}. */
        public int z(int d) {
            return this.frontZ + this.dir * d;
        }

        /** +1 when depth runs south, -1 when north. */
        public int dir() {
            return this.dir;
        }

        /** The yaw an exhibit faces (toward its street). */
        public float yaw() {
            return this.yaw;
        }

        /** Standing-sign rotation facing the street (0 = south, 8 = north). */
        public int signRotation() {
            return this.yaw == 0.0F ? 0 : 8;
        }

        /** The unit z step from an exhibit toward its street. */
        public int toStreet() {
            return -this.dir;
        }
    }

    /** A bay: a run of slots in one row half with one palette. */
    public record Bay(String id, Row row, int minX, int maxX, String palette) {

        public int midX() {
            return (this.minX + this.maxX) / 2;
        }

        /** The column next to the concourse. */
        public int innerX() {
            return this.minX < 0 ? this.maxX : this.minX;
        }

        /** The column farthest from the concourse. */
        public int outerX() {
            return this.minX < 0 ? this.minX : this.maxX;
        }
    }

    public static final List<Bay> BAYS = List.of(
            new Bay("luna", Row.C, -54, -25, "luna"),
            new Bay("mars", Row.C, -24, -5, "mars"),
            new Bay("cindara", Row.C, 5, 19, "cindara"),
            new Bay("glacira", Row.C, 20, 39, "glacira"),
            new Bay("drops", Row.C, 40, 54, "utility"),
            new Bay("greenxertz", Row.D, 5, 54, "greenxertz"),
            new Bay("nyxara", Row.D, -24, -5, "nyxara"),
            new Bay("asteroid", Row.D, -49, -25, "asteroid"),
            new Bay("kiosk", Row.D, -54, -50, "utility"),
            new Bay("wrecks_machines", Row.B, -34, -5, "wrecks"),
            new Bay("gear", Row.B, -54, -35, "utility"),
            new Bay("wrecks_crews", Row.B, 5, 39, "wrecks"),
            new Bay("hatchery", Row.B, 40, 54, "utility"),
            new Bay("deep_space", Row.E, -34, -5, "deep_space"),
            new Bay("blocks", Row.E, -54, -35, "utility"),
            new Bay("traits", Row.E, 5, 54, "traits"));

    public static Optional<Bay> bay(String id) {
        for (Bay bay : BAYS) {
            if (bay.id().equals(id)) {
                return Optional.of(bay);
            }
        }
        return Optional.empty();
    }

    // --- creature slots --------------------------------------------------------------------------

    /**
     * One exhibit slot.
     *
     * @param bay      bay id
     * @param suffix   slot tag suffix (also the demo and shot id)
     * @param entity   entity path in the mod namespace
     * @param alpha    alpha modifier id, or {@code ""} for a base form (which gets a plate and an egg)
     * @param x        centre block column
     * @param entityX  the exhibit's entity x
     * @param hover    height above {@code F+1}, 0 for a standing exhibit
     */
    public record Slot(String bay, String suffix, String entity, String alpha, int x, double entityX,
            double hover) {

        public boolean isAlpha() {
            return !this.alpha.isEmpty();
        }

        public Row row() {
            return MenagerieLayout.bay(this.bay).orElseThrow().row();
        }

        public int z() {
            return this.row().z(6);
        }

        public double entityZ() {
            return this.z() + 0.5D;
        }

        public float yaw() {
            return this.row().yaw();
        }

        /** The plate position (base forms only). */
        public int plateX() {
            return this.x;
        }

        public int plateZ() {
            return this.row().z(1);
        }

        public int signX() {
            return this.x + 1;
        }

        public int signZ() {
            return this.row().z(1);
        }

        /** The spawn-egg stand (base form) or alpha-drop stand (alpha). */
        public int standX() {
            return this.x - 1;
        }
    }

    private static Slot s(String bay, String suffix, String entity, String alpha, int x, double ex, double hover) {
        return new Slot(bay, suffix, entity, alpha, x, ex, hover);
    }

    public static final List<Slot> SLOTS = List.of(
            s("luna", "lunar_stalker", "lunar_stalker", "", -52, -51.5, 0),
            s("luna", "lunar_stalker_alpha", "lunar_stalker", "brutal", -47, -46.5, 0),
            s("luna", "lumen_moth", "lumen_moth", "", -42, -41.5, 1.2),
            s("luna", "echo_shrike", "echo_shrike", "", -37, -36.5, 0),
            s("luna", "echo_shrike_alpha", "echo_shrike", "fireproof", -32, -31.5, 0),
            s("luna", "lode_tortoise", "lode_tortoise", "", -27, -26.5, 0),
            s("mars", "dune_loper", "dune_loper", "", -22, -21.5, 0),
            s("mars", "strata_hound", "strata_hound", "", -17, -16.5, 0),
            s("mars", "dust_devil", "dust_devil", "", -12, -11.5, 0),
            s("mars", "dust_devil_alpha", "dust_devil", "ironhide", -7, -6.5, 0),
            s("cindara", "slag_beetle", "slag_beetle", "", 7, 7.5, 0),
            s("cindara", "magma_wader", "magma_wader", "", 12, 12.5, 0),
            s("cindara", "magma_wader_alpha", "magma_wader", "keen", 17, 17.5, 0),
            s("glacira", "glacite_wisp", "glacite_wisp", "", 22, 22.5, 1.2),
            s("glacira", "rime_yak", "rime_yak", "", 27, 27.5, 0),
            s("glacira", "frost_lurker", "frost_lurker", "", 32, 32.5, 0),
            s("glacira", "frost_lurker_alpha", "frost_lurker", "regenerating", 37, 37.5, 0),
            s("greenxertz", "xertz_forager", "xertz_forager", "", 7, 7.5, 0),
            s("greenxertz", "spore_drifter", "spore_drifter", "", 12, 12.5, 1.5),
            s("greenxertz", "bloom_host", "bloom_host", "", 17, 17.5, 0),
            s("greenxertz", "bloom_host_alpha", "bloom_host", "swift", 22, 22.5, 0),
            s("greenxertz", "mossback", "mossback", "", 27, 27.5, 0),
            s("greenxertz", "drift_leviathan", "drift_leviathan", "", 42, 42.5, 4.0),
            s("nyxara", "crystal_golem", "crystal_golem", "", -7, -6.5, 0),
            s("nyxara", "prism_stag", "prism_stag", "", -12, -11.5, 0),
            s("nyxara", "shardback", "shardback", "", -17, -16.5, 0),
            s("nyxara", "shardback_alpha", "shardback", "vital", -22, -21.5, 0),
            s("asteroid", "asteroid_worm", "asteroid_worm", "", -27, -26.5, 0),
            s("asteroid", "asteroid_worm_alpha", "asteroid_worm", "brutal", -32, -31.5, 0),
            s("asteroid", "magnet_crab", "magnet_crab", "", -37, -36.5, 0),
            s("asteroid", "gravity_leech", "gravity_leech", "", -42, -41.5, 0),
            s("asteroid", "gravity_leech_alpha", "gravity_leech", "fireproof", -47, -46.5, 0),
            s("wrecks_machines", "rogue_drone", "rogue_drone", "", -7, -6.5, 1.2),
            s("wrecks_machines", "rogue_drone_alpha", "rogue_drone", "ironhide", -12, -11.5, 1.2),
            s("wrecks_machines", "rogue_android", "rogue_android", "", -17, -16.5, 0),
            s("wrecks_machines", "rogue_android_alpha", "rogue_android", "keen", -22, -21.5, 0),
            s("wrecks_machines", "terraforming_drone", "terraforming_drone", "", -27, -26.5, 1.4),
            s("wrecks_machines", "scrap_hulk", "scrap_hulk", "", -32, -31.5, 0),
            s("wrecks_crews", "space_pirate", "space_pirate", "", 7, 7.5, 0),
            s("wrecks_crews", "space_pirate_alpha", "space_pirate", "regenerating", 12, 12.5, 0),
            s("wrecks_crews", "pirate_captain", "pirate_captain", "", 17, 17.5, 0),
            s("wrecks_crews", "cache_mimic", "cache_mimic", "", 22, 22.5, 0),
            s("wrecks_crews", "cache_mimic_alpha", "cache_mimic", "swift", 27, 27.5, 0),
            s("wrecks_crews", "static_swarm", "static_swarm", "", 32, 32.5, 0.8),
            s("wrecks_crews", "static_swarm_alpha", "static_swarm", "vital", 37, 37.5, 0.8),
            s("deep_space", "void_crawler", "void_crawler", "", -7, -6.5, 0),
            s("deep_space", "void_crawler_alpha", "void_crawler", "brutal", -12, -11.5, 0),
            s("deep_space", "plasma_slime", "plasma_slime", "", -17, -16.5, 0),
            s("deep_space", "plasma_slime_alpha", "plasma_slime", "fireproof", -22, -21.5, 0),
            s("deep_space", "void_manta", "void_manta", "", -30, -29.0, 1.0));

    /** Exhibits shown in their hidden state (the plate reveals them). */
    public static final Set<String> HIDDEN_AT_REST = Set.of("frost_lurker", "cache_mimic");

    /** The sporeling display sharing the Bloom Host slot: offset from the exhibit, depth 5. */
    public static final double SPORELING_DX = 1.2D;
    public static final int SPORELING_DEPTH = 5;

    public static Optional<Slot> slot(String suffix) {
        for (Slot slot : SLOTS) {
            if (slot.suffix().equals(suffix)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    public static List<Slot> baseSlots() {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : SLOTS) {
            if (!slot.isAlpha()) {
                out.add(slot);
            }
        }
        return out;
    }

    public static List<Slot> alphaSlots() {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : SLOTS) {
            if (slot.isAlpha()) {
                out.add(slot);
            }
        }
        return out;
    }

    // --- boss hall -------------------------------------------------------------------------------

    /** First interior column of each alcove, west to east. */
    public static final int[] ALCOVE_S = {-49, -24, 1, 26};
    public static final List<String> BOSSES = List.of("cinder_tyrant", "rime_matriarch", "bloom_sovereign",
            "facet_colossus");
    public static final int BOSS_Z = -44;
    public static final int BOSS_PLATE_Z = -36;

    /** One boss pose; {@code phase} is 1..3. The boss stands on a 5 x 5 dais at {@code F+1}. */
    public record BossPose(String suffix, String boss, int phase, String phaseId, int x) {

        public int z() {
            return BOSS_Z;
        }

        public int plateX() {
            return this.x;
        }

        public int plateZ() {
            return BOSS_PLATE_Z;
        }

        public int signX() {
            return this.x + 1;
        }

        public int alcove() {
            return BOSSES.indexOf(this.boss);
        }
    }

    public static final List<BossPose> BOSS_POSES = List.of(
            new BossPose("cinder_tyrant_p1", "cinder_tyrant", 1, "stomp", -46),
            new BossPose("cinder_tyrant_p2", "cinder_tyrant", 2, "volley", -38),
            new BossPose("cinder_tyrant_p3", "cinder_tyrant", 3, "desperation", -30),
            new BossPose("rime_matriarch_p1", "rime_matriarch", 1, "veiled", -21),
            new BossPose("rime_matriarch_p2", "rime_matriarch", 2, "spreading_frost", -13),
            new BossPose("rime_matriarch_p3", "rime_matriarch", 3, "shattered_crown", -5),
            new BossPose("bloom_sovereign_p1", "bloom_sovereign", 1, "budding", 4),
            new BossPose("bloom_sovereign_p2", "bloom_sovereign", 2, "full_bloom", 12),
            new BossPose("bloom_sovereign_p3", "bloom_sovereign", 3, "wilting_heart", 20),
            new BossPose("facet_colossus_p1", "facet_colossus", 1, "facets", 29),
            new BossPose("facet_colossus_p2", "facet_colossus", 2, "decoys", 37),
            new BossPose("facet_colossus_p3", "facet_colossus", 3, "shatter", 45));

    /** The two Bloom Sovereign pod displays: offsets from the alcove's first column, z. */
    public static final int[][] POD_OFFSETS = {{8, -41}, {14, -41}};
    public static final List<String> POD_SUFFIXES = List.of("bloom_sovereign_pod_a", "bloom_sovereign_pod_b");

    public static Optional<BossPose> bossPose(String suffix) {
        for (BossPose pose : BOSS_POSES) {
            if (pose.suffix().equals(suffix)) {
                return Optional.of(pose);
            }
        }
        return Optional.empty();
    }

    // --- trait wall ------------------------------------------------------------------------------

    public static final int TRAIT_Z = 22;
    public static final int TRAIT_SIGN_Z = 25;

    public record TraitDisplay(String entity, int minX, int maxX, int x, double hover) {

        public String suffix() {
            return this.entity + "_radiant";
        }
    }

    public static final List<TraitDisplay> TRAIT_WALL = List.of(
            new TraitDisplay("lumen_moth", 6, 8, 7, 1.2),
            new TraitDisplay("lode_tortoise", 9, 11, 10, 0),
            new TraitDisplay("dune_loper", 12, 15, 14, 0),
            new TraitDisplay("strata_hound", 16, 18, 17, 0),
            new TraitDisplay("slag_beetle", 19, 21, 20, 0),
            new TraitDisplay("glacite_wisp", 22, 24, 23, 1.2),
            new TraitDisplay("rime_yak", 25, 28, 27, 0),
            new TraitDisplay("xertz_forager", 29, 31, 30, 0),
            new TraitDisplay("spore_drifter", 32, 34, 33, 1.5),
            new TraitDisplay("mossback", 35, 38, 37, 0),
            new TraitDisplay("prism_stag", 39, 41, 40, 0),
            new TraitDisplay("magnet_crab", 42, 44, 43, 0),
            new TraitDisplay("void_manta", 45, 51, 48, 1.0));

    /** Trait glossary order (the trait pool order). */
    public static final List<String> TRAITS = List.of("hardy", "swift", "keen", "bountiful", "calm", "thrifty",
            "stout", "radiant");

    // --- mount paddock ---------------------------------------------------------------------------

    public record PaddockMount(String suffix, String entity, int x, int z, double hover, boolean panniers,
            Box yard) {

        public double entityX() {
            return this.x + 0.5D;
        }

        public double entityZ() {
            return this.z + 0.5D;
        }

        public float yaw() {
            return 180.0F;
        }
    }

    public static final Box LOPER_YARD = new Box(-54, -1, 36, 51, 0, 14);
    public static final Box MANTA_YARD = new Box(1, 54, 36, 51, 0, 14);

    public static final List<PaddockMount> PADDOCK = List.of(
            new PaddockMount("paddock_loper_a", "dune_loper", -34, 44, 0, false, LOPER_YARD),
            new PaddockMount("paddock_loper_b", "dune_loper", -22, 44, 0, true, LOPER_YARD),
            new PaddockMount("paddock_manta_a", "void_manta", 22, 44, 1.0, false, MANTA_YARD),
            new PaddockMount("paddock_manta_b", "void_manta", 34, 44, 1.0, false, MANTA_YARD));

    public static Optional<PaddockMount> paddock(String suffix) {
        for (PaddockMount mount : PADDOCK) {
            if (mount.suffix().equals(suffix)) {
                return Optional.of(mount);
            }
        }
        return Optional.empty();
    }

    // --- working rows ----------------------------------------------------------------------------

    /** A stand showing one item: {@code y} is relative to F (the stand's feet). */
    public record Stand(String item, int x, int z, int y, String variant) {
    }

    /** Gear bay: 14 visible stands. Variant {@code filled} is a Capture Pod holding a Lumen Moth. */
    public static final List<Stand> GEAR = List.of(
            new Stand("mirror_shield", -52, -25, 1, ""),
            new Stand("lurker_cloak", -50, -25, 1, ""),
            new Stand("blink_charm", -48, -25, 1, ""),
            new Stand("rime_flask", -46, -25, 1, ""),
            new Stand("heat_gland_greaves", -44, -25, 1, ""),
            new Stand("graviton_boots", -42, -25, 1, ""),
            new Stand("static_jar", -40, -25, 1, ""),
            new Stand("resonant_whistle", -52, -22, 2, ""),
            new Stand("chitin_bulwark", -50, -22, 2, ""),
            new Stand("bloom_amulet", -48, -22, 2, ""),
            new Stand("creature_saddle", -46, -22, 2, ""),
            new Stand("capture_pod", -44, -22, 2, ""),
            new Stand("capture_pod", -42, -22, 2, "filled"),
            new Stand("field_scanner", -40, -22, 2, ""));

    /** The six egg items, west to east, in the Hatchery bay. */
    public static final List<String> EGGS = List.of("lumen_moth_egg", "lode_tortoise_egg", "slag_beetle_egg",
            "magnet_crab_egg", "spore_drifter_egg", "void_manta_egg");

    /** Habitat Blocks bay: the real blocks, west to east, at depth 4 on a bench. */
    public static final List<String> HABITAT_BLOCKS = List.of("nest", "feeding_trough", "incubator",
            "lure_beacon", "sonic_ward", "terrarium", "trophy_plaque", "apex_altar");
    public static final int BLOCKS_FIRST_X = -52;
    public static final int BLOCKS_Z = 23;
    /** The three crust variants' columns, and the crust plate. */
    public static final int[] CRUST_X = {-37, -36, -35};
    public static final int CRUST_PLATE_X = -36;
    public static final int CRUST_PLATE_Z = 26;

    /** Boss signature drops, Bestiary order. */
    public static final List<String> SIGNATURE_DROPS = List.of("tyrant_ember", "matriarch_frostcore",
            "sovereign_seed", "colossus_facet");

    /**
     * The Drops bay contents: creature drops in Bestiary order (de-duplicated, {@code cooked_mossback}
     * after its raw form), then the four boss signature drops in the back row.
     */
    public static List<String> drops() {
        Set<String> ordered = new LinkedHashSet<>();
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            if (entry.boss()) {
                continue;
            }
            for (String id : entry.drops()) {
                String path = id.substring(id.indexOf(':') + 1);
                ordered.add(path);
                if (path.equals("raw_mossback")) {
                    ordered.add("cooked_mossback");
                }
            }
        }
        List<String> out = new ArrayList<>(ordered);
        out.addAll(SIGNATURE_DROPS);
        return out;
    }

    /** Drop stands: 7 columns by 5 rows; row {@code r} stands {@code r} blocks up. */
    public static List<Stand> dropStands() {
        List<String> drops = drops();
        List<Stand> out = new ArrayList<>();
        int creatureDrops = drops.size() - SIGNATURE_DROPS.size();
        int i = 0;
        for (String id : drops) {
            int index = i < creatureDrops ? i : 35 - SIGNATURE_DROPS.size() + (i - creatureDrops);
            int row = Math.min(4, index / 7);
            int col = index % 7;
            out.add(new Stand(id, 41 + 2 * col, Row.C.z(2 + 2 * row), 1 + row, ""));
            i++;
        }
        return out;
    }

    /** The hatchery: plain eggs at depth 3, Radiant ones at depth 5 on a step. */
    public static List<Stand> eggStands() {
        List<Stand> out = new ArrayList<>();
        for (int i = 0; i < EGGS.size(); i++) {
            out.add(new Stand(EGGS.get(i), 42 + 2 * i, Row.B.z(3), 1, ""));
            out.add(new Stand(EGGS.get(i), 42 + 2 * i, Row.B.z(5), 2, "radiant"));
        }
        return out;
    }

    /** Boss hall stands: spawn egg, sigil, signature drop per alcove, on the floor line z = -37. */
    public static List<Stand> bossStands() {
        List<Stand> out = new ArrayList<>();
        for (int i = 0; i < BOSSES.size(); i++) {
            String boss = BOSSES.get(i);
            int s = ALCOVE_S[i];
            out.add(new Stand(boss + "_spawn_egg", s + 2, -37, 1, ""));
            out.add(new Stand(boss + "_sigil", s + 6, -37, 1, ""));
            out.add(new Stand(SIGNATURE_DROPS.get(i), s + 8, -37, 1, ""));
        }
        return out;
    }

    /** Field Scanner kiosk stand (visible). */
    public static final Stand KIOSK_SCANNER = new Stand("field_scanner", -52, Row.D.z(4), 1, "");

    // --- labels ----------------------------------------------------------------------------------

    /** A floating title: lang key and position (at F+7). */
    public record Label(String key, int x, int z) {
    }

    public static List<Label> labels() {
        List<Label> out = new ArrayList<>();
        for (Bay bay : BAYS) {
            out.add(new Label("gallery.nerocreatures.bay." + bay.id(), bay.midX(), bay.row().z(0)));
        }
        for (int i = 0; i < BOSSES.size(); i++) {
            out.add(new Label("entity.nerocreatures." + BOSSES.get(i), ALCOVE_S[i] + 11, -35));
        }
        out.add(new Label("gallery.nerocreatures.bay.paddock", -28, 35));
        out.add(new Label("gallery.nerocreatures.bay.paddock", 28, 35));
        out.add(new Label("gallery.nerocreatures.bay.menagerie", 0, 0));
        return out;
    }

    // --- plates ----------------------------------------------------------------------------------

    /** The crust demo's id. */
    public static final String CRUST = "crust";

    /**
     * The slot a Menagerie plate at offset ({@code dx}, {@code dy}, {@code dz}) belongs to. Plates sit at
     * {@code F+1}. Empty for any other position.
     */
    public static Optional<String> plateAt(int dx, int dy, int dz) {
        if (dy != 1) {
            return Optional.empty();
        }
        for (Slot slot : SLOTS) {
            if (!slot.isAlpha() && slot.plateX() == dx && slot.plateZ() == dz) {
                return Optional.of(slot.suffix());
            }
        }
        for (BossPose pose : BOSS_POSES) {
            if (pose.plateX() == dx && pose.plateZ() == dz) {
                return Optional.of(pose.suffix());
            }
        }
        if (dx == CRUST_PLATE_X && dz == CRUST_PLATE_Z) {
            return Optional.of(CRUST);
        }
        return Optional.empty();
    }

    /** Every plate position as {dx, dz}. */
    public static List<int[]> plates() {
        List<int[]> out = new ArrayList<>();
        for (Slot slot : baseSlots()) {
            out.add(new int[] {slot.plateX(), slot.plateZ()});
        }
        for (BossPose pose : BOSS_POSES) {
            out.add(new int[] {pose.plateX(), pose.plateZ()});
        }
        out.add(new int[] {CRUST_PLATE_X, CRUST_PLATE_Z});
        return out;
    }

    /**
     * The box (offsets, inclusive) a slot's exhibit and its props live in: {@code cx-2..cx+2}, depth
     * 0..11, {@code F..F+10}. Bosses: the 8-wide pose spot, z -52..-35. The crust: its stand.
     */
    public static Optional<Box> slotBox(String suffix) {
        Optional<Slot> slot = slot(suffix);
        if (slot.isPresent()) {
            Slot sl = slot.get();
            int half = sl.suffix().equals("drift_leviathan") ? 10 : sl.suffix().equals("void_manta") ? 4 : 2;
            int z0 = sl.row().z(0);
            int z1 = sl.row().z(11);
            return Optional.of(new Box(sl.x() - half, sl.x() + half, Math.min(z0, z1), Math.max(z0, z1), 0, 10));
        }
        Optional<BossPose> pose = bossPose(suffix);
        if (pose.isPresent()) {
            int x = pose.get().x();
            return Optional.of(new Box(x - 4, x + 3, -52, -35, 0, 12));
        }
        if (CRUST.equals(suffix)) {
            return Optional.of(new Box(-37, -35, 22, 27, 0, 4));
        }
        return Optional.empty();
    }

    // --- demos -----------------------------------------------------------------------------------

    /**
     * One demo script row.
     *
     * @param slot     slot id
     * @param clip     pose to play: {@code ability}, {@code ability_2}, {@code rest} or {@code ""}
     * @param ticks    clip length
     * @param shoot    tick at which the harness shoots
     * @param particle particle id in the mod namespace, or a {@code minecraft:} id, or {@code ""}
     * @param sound    sound id in the mod namespace, or {@code ""}
     */
    public record Demo(String slot, String clip, int ticks, int shoot, String particle, String sound) {
    }

    public static final List<Demo> DEMOS = List.of(
            new Demo("lunar_stalker", "ability", 30, 12, "", "lunar_stalker.howl"),
            new Demo("lumen_moth", "ability", 100, 40, "lumen_mote", "lumen_moth.pulse"),
            new Demo("echo_shrike", "ability", 40, 14, "echo_ring", "echo_shrike.screech"),
            new Demo("lode_tortoise", "rest", 80, 60, "nodule_glint", "lode_tortoise.graze"),
            new Demo("dune_loper", "ability", 30, 10, "dust_swirl", "dune_loper.leap"),
            new Demo("strata_hound", "ability", 60, 30, "nodule_glint", "strata_hound.point"),
            new Demo("dust_devil", "ability", 60, 30, "dust_swirl", "dust_devil.whirl"),
            new Demo("slag_beetle", "ability", 80, 50, "slag_ember", "slag_beetle.digest"),
            new Demo("magma_wader", "ability", 40, 22, "slag_ember", "magma_wader.slag"),
            new Demo("glacite_wisp", "ability", 40, 16, "rime_mote", "glacite_wisp.cushion"),
            new Demo("rime_yak", "ability", 80, 50, "rime_mote", "rime_yak.shiver"),
            new Demo("frost_lurker", "ability", 50, 14, "rime_mote", "frost_lurker.erupt"),
            new Demo("xertz_forager", "ability", 60, 30, "prism_glint", "xertz_forager.dig"),
            new Demo("spore_drifter", "ability", 60, 30, "spore_mote", "spore_drifter.puff"),
            new Demo("bloom_host", "ability", 50, 30, "spore_puff", "bloom_host.raise"),
            new Demo("mossback", "ability", 50, 30, "minecraft:happy_villager", "mossback.graze"),
            new Demo("drift_leviathan", "ability", 120, 60, "spore_mote", "drift_leviathan.song"),
            new Demo("crystal_golem", "ability", 40, 16, "prism_glint", "crystal_golem.smash"),
            new Demo("prism_stag", "ability", 80, 55, "prism_glint", "prism_stag.shed"),
            new Demo("shardback", "ability", 40, 10, "prism_glint", "shardback.deflect"),
            new Demo("asteroid_worm", "ability", 50, 26, "", "asteroid_worm.emerge"),
            new Demo("magnet_crab", "ability", 50, 24, "magnet_spark", "magnet_crab.magnet"),
            new Demo("gravity_leech", "ability", 50, 24, "gravity_ripple", "gravity_leech.latch"),
            new Demo("rogue_drone", "ability", 30, 12, "", "rogue_drone.leap"),
            new Demo("rogue_android", "ability", 40, 16, "", "rogue_android.shield"),
            new Demo("terraforming_drone", "ability", 60, 30, "minecraft:happy_villager", "terraforming_drone.work"),
            new Demo("scrap_hulk", "ability", 100, 50, "static_spark", "scrap_hulk.shed"),
            new Demo("space_pirate", "ability", 30, 12, "", "space_pirate.volley"),
            new Demo("pirate_captain", "ability", 40, 14, "minecraft:flame", "pirate_captain.rally"),
            new Demo("cache_mimic", "ability", 60, 30, "", "cache_mimic.reveal"),
            new Demo("static_swarm", "ability", 50, 20, "static_spark", "static_swarm.drain"),
            new Demo("void_crawler", "ability", 30, 12, "void_trail", "void_crawler.blink"),
            new Demo("plasma_slime", "ability", 40, 20, "minecraft:flame", "plasma_slime.pulse"),
            new Demo("void_manta", "ability", 40, 12, "void_blink", "void_manta.blink"),
            new Demo("cinder_tyrant_p1", "ability", 50, 24, "", "cinder_tyrant.slam"),
            new Demo("cinder_tyrant_p2", "ability_2", 60, 30, "slag_ember", "cinder_tyrant.summon"),
            new Demo("cinder_tyrant_p3", "ability", 50, 24, "slag_ember", "cinder_tyrant.phase"),
            new Demo("rime_matriarch_p1", "ability", 80, 40, "rime_mote", "rime_matriarch.beam"),
            new Demo("rime_matriarch_p2", "ability_2", 60, 30, "rime_mote", "rime_matriarch.freeze"),
            new Demo("rime_matriarch_p3", "ability", 80, 40, "rime_mote", "rime_matriarch.beam"),
            new Demo("bloom_sovereign_p1", "ability", 60, 30, "spore_puff", "bloom_sovereign.infect"),
            new Demo("bloom_sovereign_p2", "ability_2", 80, 40, "bloom_mote", "bloom_sovereign.heal"),
            new Demo("bloom_sovereign_p3", "ability", 60, 30, "spore_mote", "bloom_sovereign.phase"),
            new Demo("facet_colossus_p1", "ability", 80, 40, "prism_glint", "facet_colossus.rotate"),
            new Demo("facet_colossus_p2", "ability_2", 60, 30, "prism_glint", "facet_colossus.summon"),
            new Demo("facet_colossus_p3", "ability", 80, 40, "prism_glint", "facet_colossus.beam"),
            new Demo(CRUST, "", 100, 30, "", ""));

    public static Optional<Demo> demo(String slot) {
        for (Demo demo : DEMOS) {
            if (demo.slot().equals(slot)) {
                return Optional.of(demo);
            }
        }
        return Optional.empty();
    }

    /** Cooldown after a demo: its clip length plus this. */
    public static final int DEMO_COOLDOWN_EXTRA = 40;

    /**
     * Every prop block a demo places, as {x, d-or-z, y} offsets: x and y absolute offsets, the second
     * value an absolute z offset. Used by the builder (props the build places) and by the prop-box test.
     */
    public static List<int[]> demoProps(String slot) {
        Optional<Slot> base = slot(slot);
        if (CRUST.equals(slot)) {
            return List.of(new int[] {-37, BLOCKS_Z, 2}, new int[] {-36, BLOCKS_Z, 2}, new int[] {-35, BLOCKS_Z, 2});
        }
        if (base.isEmpty()) {
            return List.of();
        }
        Slot s = base.get();
        Row r = s.row();
        return switch (slot) {
            case "lumen_moth" -> List.<int[]>of(new int[] {s.x(), r.z(4), 3});
            case "strata_hound" -> List.<int[]>of(new int[] {s.x() + 1, r.z(8), 1});
            case "magma_wader" -> List.<int[]>of(new int[] {s.x() - 1, r.z(3), 1});
            case "spore_drifter" -> List.<int[]>of(new int[] {s.x() - 1, r.z(6), 1}, new int[] {s.x(), r.z(6), 1},
                    new int[] {s.x() + 1, r.z(6), 1});
            case "terraforming_drone" -> List.<int[]>of(new int[] {s.x(), r.z(6), 1});
            default -> List.of();
        };
    }

    // --- tour ------------------------------------------------------------------------------------

    public record TourStop(int n, String id, int x, int z, float yaw) {
    }

    public static final List<TourStop> TOUR = List.of(
            new TourStop(1, "plaza", 0, 0, 180),
            new TourStop(2, "luna", -40, -1, 180),
            new TourStop(3, "mars", -15, -1, 180),
            new TourStop(4, "cindara", 12, -1, 180),
            new TourStop(5, "glacira", 29, -1, 180),
            new TourStop(6, "drops", 47, -1, 180),
            new TourStop(7, "greenxertz", 17, 1, 0),
            new TourStop(8, "leviathan", 42, 1, 0),
            new TourStop(9, "nyxara", -15, 1, 0),
            new TourStop(10, "asteroid", -37, 1, 0),
            new TourStop(11, "kiosk", -52, 1, 0),
            new TourStop(12, "wrecks_machines", -20, -30, 0),
            new TourStop(13, "gear", -45, -30, 0),
            new TourStop(14, "wrecks_crews", 22, -30, 0),
            new TourStop(15, "hatchery", 47, -30, 0),
            new TourStop(16, "cinder_tyrant", -37, -32, 180),
            new TourStop(17, "rime_matriarch", -12, -32, 180),
            new TourStop(18, "bloom_sovereign", 13, -32, 180),
            new TourStop(19, "facet_colossus", 38, -32, 180),
            new TourStop(20, "deep_space", -20, 30, 180),
            new TourStop(21, "blocks", -45, 30, 180),
            new TourStop(22, "traits", 30, 30, 180),
            new TourStop(23, "paddock_loper", -28, 32, 0),
            new TourStop(24, "paddock_manta", 28, 32, 0),
            new TourStop(25, "end", 0, 0, 0));

    /** Whether an offset is street, concourse or plaza floor. */
    public static boolean isStreet(int x, int z) {
        boolean north = z >= -34 && z <= -28;
        boolean central = z >= -3 && z <= 3;
        boolean south = z >= 28 && z <= 34;
        boolean concourse = x >= -4 && x <= 4 && z >= -34 && z <= 34;
        return (north || central || south || concourse) && x >= -55 && x <= 55;
    }

    // --- grid ------------------------------------------------------------------------------------

    /** One grid exhibit: entity, x, z, hover, alpha modifier or "", radiant, boss phase (0 none). */
    public record GridExhibit(String entity, int x, int z, double hover, String alpha, boolean radiant) {
    }

    /** The compact layout: creatures in Bestiary order, then large, Leviathan, Radiant and alphas. */
    public static List<GridExhibit> gridExhibits() {
        List<GridExhibit> out = new ArrayList<>();
        int[] rows = {-30, -24, -18, -12};
        int i = 0;
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            String id = entry.id();
            if (entry.boss() || id.equals("void_manta") || id.equals("drift_leviathan")) {
                continue;
            }
            out.add(new GridExhibit(id, 14 + 4 * (i % 10), rows[i / 10], hover(id), "", false));
            i++;
        }
        out.add(new GridExhibit("void_manta", 16, -4, 1.0, "", false));
        for (int b = 0; b < BOSSES.size(); b++) {
            out.add(new GridExhibit(BOSSES.get(b), 24 + 8 * b, -4, 0, "", false));
        }
        out.add(new GridExhibit("drift_leviathan", 20, 8, 1.0, "", false));
        for (int t = 0; t < TRAIT_WALL.size(); t++) {
            String id = TRAIT_WALL.get(t).entity();
            out.add(new GridExhibit(id, 14 + 4 * (t % 10), t < 10 ? 18 : 24, hover(id), "", true));
        }
        List<Slot> alphas = alphaSlots();
        for (int a = 0; a < alphas.size(); a++) {
            Slot s = alphas.get(a);
            out.add(new GridExhibit(s.entity(), 14 + 4 * (a % 10), a < 10 ? 30 : 36, s.hover(), s.alpha(), false));
        }
        return out;
    }

    /** The grid's habitat blocks, empty, at z = 8. */
    public static List<int[]> gridBlocks() {
        List<int[]> out = new ArrayList<>();
        for (int b = 0; b < HABITAT_BLOCKS.size(); b++) {
            out.add(new int[] {34 + 2 * b, 8});
        }
        return out;
    }

    private static double hover(String id) {
        for (Slot slot : SLOTS) {
            if (slot.entity().equals(id) && !slot.isAlpha()) {
                return slot.hover();
            }
        }
        return 0;
    }

    // --- sizes (registered hitbox, for camera framing) -------------------------------------------

    /** Registered width and height per entity path (mirrors the type registrations). */
    public static double[] size(String entity) {
        return switch (entity) {
            case "void_crawler" -> new double[] {0.9, 0.8};
            case "lunar_stalker" -> new double[] {0.8, 1.4};
            case "asteroid_worm" -> new double[] {2.0, 1.1};
            case "plasma_slime" -> new double[] {1.0, 1.0};
            case "crystal_golem" -> new double[] {1.4, 2.2};
            case "space_pirate" -> new double[] {0.6, 1.95};
            case "rogue_drone" -> new double[] {0.7, 0.7};
            case "rogue_android" -> new double[] {1.2, 2.4};
            case "glacite_wisp" -> new double[] {0.6, 0.7};
            case "xertz_forager" -> new double[] {0.8, 0.8};
            case "cinder_tyrant" -> new double[] {2.2, 3.4};
            case "terraforming_drone" -> new double[] {0.8, 0.9};
            case "lumen_moth" -> new double[] {0.6, 0.5};
            case "echo_shrike" -> new double[] {0.7, 0.9};
            case "lode_tortoise" -> new double[] {1.1, 0.9};
            case "dune_loper" -> new double[] {1.3, 2.0};
            case "strata_hound" -> new double[] {0.8, 0.9};
            case "dust_devil" -> new double[] {1.1, 2.2};
            case "slag_beetle" -> new double[] {0.9, 0.8};
            case "magma_wader" -> new double[] {1.1, 2.6};
            case "rime_yak" -> new double[] {1.3, 1.5};
            case "frost_lurker" -> new double[] {1.4, 0.6};
            case "spore_drifter" -> new double[] {0.8, 1.1};
            case "bloom_host" -> new double[] {0.9, 2.2};
            case "mossback" -> new double[] {1.3, 1.2};
            case "drift_leviathan" -> new double[] {8.75, 3.0};
            case "prism_stag" -> new double[] {0.9, 1.6};
            case "shardback" -> new double[] {1.2, 1.1};
            case "magnet_crab" -> new double[] {0.9, 0.55};
            case "gravity_leech" -> new double[] {0.8, 0.5};
            case "cache_mimic" -> new double[] {0.98, 0.98};
            case "static_swarm" -> new double[] {0.8, 0.8};
            case "scrap_hulk" -> new double[] {1.6, 2.2};
            case "pirate_captain" -> new double[] {0.7, 2.25};
            case "void_manta" -> new double[] {2.5, 0.9};
            case "rime_matriarch" -> new double[] {1.8, 4.2};
            case "bloom_sovereign" -> new double[] {3.0, 4.5};
            case "facet_colossus" -> new double[] {2.8, 4.4};
            default -> new double[] {1.0, 1.0};
        };
    }

    // --- counts ----------------------------------------------------------------------------------

    /** Mob displays the Menagerie places. */
    public static int menagerieMobs() {
        return SLOTS.size() + 1 /* sporeling */ + TRAIT_WALL.size() + BOSS_POSES.size() + POD_SUFFIXES.size()
                + PADDOCK.size();
    }

    /** Item stands the Menagerie places (gear and the kiosk scanner included). */
    public static int menagerieItemStands() {
        return baseSlots().size() /* spawn eggs */ + alphaSlots().size() /* alpha drops */ + dropStands().size()
                + GEAR.size() + eggStands().size() + bossStands().size() + 1 /* kiosk */;
    }

    public static int menagerieEntities() {
        return menagerieMobs() + menagerieItemStands() + labels().size() + 1 /* anchor */;
    }

    public static int gridMobs() {
        return gridExhibits().size();
    }

    /** Every item path the Menagerie shows (stands, plaques, terrarium/pod contents, placed blocks). */
    public static Set<String> itemsShown() {
        Set<String> out = new LinkedHashSet<>();
        for (Slot slot : baseSlots()) {
            out.add(standItem(slot.entity()));
        }
        for (Stand st : dropStands()) {
            out.add(st.item());
        }
        for (Stand st : GEAR) {
            out.add(st.item());
        }
        for (Stand st : eggStands()) {
            out.add(st.item());
        }
        for (Stand st : bossStands()) {
            out.add(st.item());
        }
        out.add(KIOSK_SCANNER.item());
        out.add("apex_trophy");
        out.addAll(HABITAT_BLOCKS);
        return Collections.unmodifiableSet(out);
    }

    /** Every block path the Menagerie places from this mod. */
    public static Set<String> blocksShown() {
        Set<String> out = new LinkedHashSet<>(HABITAT_BLOCKS);
        out.add("ephemeral_crust");
        return Collections.unmodifiableSet(out);
    }

    /**
     * What {@code clear} removes from its box: anything carrying the gallery tag, plus item and XP litter.
     * Never anything else: a player's own pet or mount that walked in, a villager, an untagged mob.
     */
    public static boolean clearRemoves(java.util.Collection<String> tags, boolean litter) {
        return litter || tags.contains(TAG);
    }

    /**
     * The item on a base slot's stand: the species' spawn egg, or for the Terraforming Drone (which has
     * no spawn egg) the Drone Shell it is deployed from.
     */
    public static String standItem(String entity) {
        return entity.equals("terraforming_drone") ? "drone_shell" : entity + "_spawn_egg";
    }

    /** Every entity path on display. */
    public static Set<String> entitiesShown() {
        Set<String> out = new LinkedHashSet<>();
        for (Slot slot : SLOTS) {
            out.add(slot.entity());
        }
        out.addAll(BOSSES);
        return Collections.unmodifiableSet(out);
    }
}
