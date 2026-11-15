package za.co.neroland.nerocreatures.bestiary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The static Bestiary table (decision 010 sections 4.2, 4.3 and 8): for each of the 38 roster
 * species its habitat, kind chip, Radiant-carrier and alpha flags, the boss sigil and the drops the
 * detail page lists. Pure data with no Minecraft types, so the server ({@link BestiaryProgress}),
 * the client screen and the tests share one copy. It is not player data.
 *
 * <p>Order is the screen's list order: habitats in section 4.2 order, creatures in roster order
 * inside each, the boss last.
 */
public final class BestiaryCatalog {

    /** The nine habitat sections, in list order; {@link #id()} is the lang suffix and art habitat. */
    public enum Habitat {
        LUNA("luna", 0xFFFFE08A),
        MARS("mars", 0xFF3DFFC0),
        CINDARA("cindara", 0xFFFF6A1F),
        GLACIRA("glacira", 0xFF9FD8FF),
        GREENXERTZ("greenxertz", 0xFFD9FF4A),
        NYXARA("nyxara", 0xFFFF5AD9),
        ASTEROID("asteroid", 0xFF4F7BFF),
        WRECKS("wrecks", 0xFFFF4A2E),
        DEEP_SPACE("deep_space", 0xFFB06BFF);

        private final String id;
        private final int accent;

        Habitat(String id, int accent) {
            this.id = id;
            this.accent = accent;
        }

        public String id() {
            return this.id;
        }

        /** The habitat accent seed (ARGB), used for the viewport gradient. */
        public int accent() {
            return this.accent;
        }
    }

    /** The kind chip (section 4.3). Decides the study rule and the reward size. */
    public enum Kind {
        TAMEABLE, DRONE, MOUNT, LIVESTOCK, AMBIENT, NEUTRAL, HOSTILE, ELITE, BOSS;

        public String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One species row.
     *
     * @param id      registry path, for example {@code lumen_moth}
     * @param habitat list section
     * @param kind    kind chip
     * @param radiant one of the 13 Radiant carriers (decision 007 section 2.6)
     * @param alpha   may spawn as an alpha (every plain hostile: bosses and elites are excluded)
     * @param sigil   the boss's sigil item path, or empty
     * @param drops   item ids ({@code ns:path}) the detail page lists, in display order
     */
    public record Entry(String id, Habitat habitat, Kind kind, boolean radiant, boolean alpha,
            Optional<String> sigil, List<String> drops) {

        public boolean boss() {
            return this.kind == Kind.BOSS;
        }

        public boolean elite() {
            return this.kind == Kind.ELITE;
        }

        /** Tameable, drone and mount entries can be owned (the "Owned now" row, payload bit 2). */
        public boolean ownable() {
            return this.kind == Kind.TAMEABLE || this.kind == Kind.DRONE || this.kind == Kind.MOUNT;
        }

        /** Species whose overworld spawn rule exists (decision 005), for the Habitat "also" line. */
        public boolean overworld() {
            return OVERWORLD.contains(this.id);
        }
    }

    /** Decision 005's three species with a rare overworld rule. */
    public static final List<String> OVERWORLD = List.of("lumen_moth", "lode_tortoise", "static_swarm");

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final List<Entry> ENTRIES;

    static {
        List<Entry> all = new ArrayList<>();
        Habitat h = Habitat.LUNA;
        all.add(e("lunar_stalker", h, Kind.HOSTILE, false, "stalker_hide", "stalker_sinew"));
        all.add(e("lumen_moth", h, Kind.TAMEABLE, true, "lumen_dust"));
        all.add(e("echo_shrike", h, Kind.HOSTILE, false, "resonant_membrane"));
        all.add(e("lode_tortoise", h, Kind.LIVESTOCK, true, "lode_scute"));
        h = Habitat.MARS;
        all.add(e("dune_loper", h, Kind.MOUNT, true));
        all.add(e("strata_hound", h, Kind.TAMEABLE, true));
        all.add(e("dust_devil", h, Kind.HOSTILE, false, "charged_sand"));
        h = Habitat.CINDARA;
        all.add(e("slag_beetle", h, Kind.TAMEABLE, true, "slag_carapace"));
        all.add(e("magma_wader", h, Kind.HOSTILE, false, "heat_gland"));
        all.add(boss("cinder_tyrant", h, "cinder_tyrant_sigil", "apex_trophy", "tyrant_ember"));
        h = Habitat.GLACIRA;
        all.add(e("glacite_wisp", h, Kind.TAMEABLE, true));
        all.add(e("rime_yak", h, Kind.LIVESTOCK, true, "rime_wool", "coolant_milk"));
        all.add(e("frost_lurker", h, Kind.HOSTILE, false, "cryo_fang"));
        all.add(boss("rime_matriarch", h, "rime_matriarch_sigil", "apex_trophy", "matriarch_frostcore"));
        h = Habitat.GREENXERTZ;
        all.add(e("xertz_forager", h, Kind.TAMEABLE, true));
        all.add(e("spore_drifter", h, Kind.TAMEABLE, true, "drifter_spores"));
        all.add(e("bloom_host", h, Kind.HOSTILE, false, "bloom_heart"));
        all.add(e("mossback", h, Kind.LIVESTOCK, true, "mossback_hide", "raw_mossback"));
        all.add(e("drift_leviathan", h, Kind.AMBIENT, false, "leviathan_barnacle"));
        all.add(boss("bloom_sovereign", h, "bloom_sovereign_sigil", "apex_trophy", "sovereign_seed"));
        h = Habitat.NYXARA;
        all.add(e("crystal_golem", h, Kind.NEUTRAL, false, "refined_crystal"));
        all.add(e("prism_stag", h, Kind.LIVESTOCK, true, "prism_antler"));
        all.add(e("shardback", h, Kind.HOSTILE, false, "mirror_scale"));
        all.add(boss("facet_colossus", h, "facet_colossus_sigil", "apex_trophy", "colossus_facet"));
        h = Habitat.ASTEROID;
        all.add(e("asteroid_worm", h, Kind.HOSTILE, false, "worm_chitin", "ore_slurry"));
        all.add(e("magnet_crab", h, Kind.TAMEABLE, true));
        all.add(e("gravity_leech", h, Kind.HOSTILE, false, "graviton_node"));
        h = Habitat.WRECKS;
        all.add(e("rogue_drone", h, Kind.HOSTILE, false, "salvaged_circuitry"));
        all.add(e("rogue_android", h, Kind.HOSTILE, false, "salvaged_circuitry", "android_core"));
        all.add(e("terraforming_drone", h, Kind.DRONE, false, "drone_shell"));
        all.add(e("space_pirate", h, Kind.HOSTILE, false, "contraband"));
        all.add(e("cache_mimic", h, Kind.HOSTILE, false, "salvaged_circuitry", "android_core"));
        all.add(e("static_swarm", h, Kind.HOSTILE, false, "static_jar"));
        all.add(e("scrap_hulk", h, Kind.ELITE, false, "salvaged_circuitry", "android_core"));
        all.add(e("pirate_captain", h, Kind.ELITE, false, "captains_ledger", "contraband"));
        h = Habitat.DEEP_SPACE;
        all.add(e("void_crawler", h, Kind.HOSTILE, false, "void_essence"));
        all.add(e("plasma_slime", h, Kind.HOSTILE, false, "plasma_cell"));
        all.add(e("void_manta", h, Kind.MOUNT, true, "void_essence"));
        for (Entry entry : all) {
            BY_ID.put(entry.id(), entry);
        }
        ENTRIES = Collections.unmodifiableList(all);
    }

    private BestiaryCatalog() {
    }

    private static Entry e(String id, Habitat habitat, Kind kind, boolean radiant, String... drops) {
        List<String> ids = new ArrayList<>();
        for (String drop : drops) {
            ids.add("nerocreatures:" + drop);
        }
        return new Entry(id, habitat, kind, radiant, kind == Kind.HOSTILE, Optional.empty(), List.copyOf(ids));
    }

    private static Entry boss(String id, Habitat habitat, String sigil, String... drops) {
        Entry plain = e(id, habitat, Kind.BOSS, false, drops);
        return new Entry(id, habitat, Kind.BOSS, false, false, Optional.of(sigil), plain.drops());
    }

    /** All 38 entries in list order. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** The entry for a registry path, if it is a Bestiary species. */
    public static Optional<Entry> get(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** The entries of one habitat section, in order. */
    public static List<Entry> of(Habitat habitat) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.habitat() == habitat) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Index of an id in list order, or -1. */
    public static int indexOf(String id) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
