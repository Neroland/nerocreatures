package za.co.neroland.nerocreatures.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;

/** The Menagerie layout tables: coverage, fit, plates, tour, budget, sign widths, demo props, shots. */
class MenagerieLayoutTest {

    private static JsonObject lang() throws IOException {
        try (InputStream in = MenagerieLayoutTest.class.getResourceAsStream("/assets/nerocreatures/lang/en_us.json")) {
            assertTrue(in != null, "en_us.json on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    // --- 1. coverage -------------------------------------------------------------------------------

    @Test
    void everyRosterIdHasOneBaseSlotOrBossAlcove() {
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            long base = MenagerieLayout.baseSlots().stream().filter(s -> s.entity().equals(entry.id())).count();
            long poses = MenagerieLayout.BOSS_POSES.stream().filter(p -> p.boss().equals(entry.id())).count();
            if (entry.boss()) {
                assertEquals(0, base, entry.id());
                assertEquals(3, poses, entry.id() + " is posed once per phase");
            } else {
                assertEquals(1, base, entry.id() + " has exactly one base slot");
            }
        }
        assertEquals(34, MenagerieLayout.baseSlots().size());
        assertEquals(4, MenagerieLayout.BOSSES.size());
    }

    @Test
    void everyAlphaEligibleSpeciesHasOneAlphaSlotAndEveryRadiantCarrierIsOnTheWall() {
        Set<String> wall = new HashSet<>();
        MenagerieLayout.TRAIT_WALL.forEach(t -> wall.add(t.entity()));
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            long alphas = MenagerieLayout.alphaSlots().stream().filter(s -> s.entity().equals(entry.id())).count();
            assertEquals(entry.alpha() ? 1 : 0, alphas, entry.id());
            assertEquals(entry.radiant(), wall.contains(entry.id()), entry.id());
        }
        assertEquals(16, MenagerieLayout.alphaSlots().size());
        assertEquals(13, MenagerieLayout.TRAIT_WALL.size());
        Map<String, Integer> modifiers = new HashMap<>();
        MenagerieLayout.alphaSlots().forEach(s -> modifiers.merge(s.alpha(), 1, Integer::sum));
        assertEquals(7, modifiers.size(), "all seven modifiers shown");
        modifiers.values().forEach(n -> assertTrue(n >= 2, "each modifier shows at least twice"));
    }

    @Test
    void everyModItemAndBlockIsPlaced() throws IOException {
        JsonObject lang = lang();
        Set<String> items = MenagerieLayout.itemsShown();
        Set<String> blocks = MenagerieLayout.blocksShown();
        for (String key : lang.keySet()) {
            String[] parts = key.split("\\.");
            if (parts.length != 3 || !parts[1].equals("nerocreatures")) {
                continue;
            }
            if (parts[0].equals("item")) {
                assertTrue(items.contains(parts[2]), "item placed: " + parts[2]);
            } else if (parts[0].equals("block")) {
                assertTrue(blocks.contains(parts[2]), "block placed: " + parts[2]);
            }
        }
        assertEquals(35, MenagerieLayout.dropStands().size(), "35 drops on the stepped stand");
        assertEquals(14, MenagerieLayout.GEAR.size());
        assertEquals(12, MenagerieLayout.eggStands().size());
    }

    // --- 2. fit ------------------------------------------------------------------------------------

    @Test
    void layoutFitsTheBuildBoxAndTheBuildBoxFitsTheClearBox() {
        MenagerieLayout.Box build = MenagerieLayout.BUILD_BOX;
        assertTrue(MenagerieLayout.CLEAR_BOX.containsBox(build, 3), "Menagerie clear box has a margin of 3");
        assertTrue(MenagerieLayout.GRID_CLEAR_BOX.containsBox(MenagerieLayout.GRID_BUILD_BOX, 3), "grid margin 3");
        Map<String, String> used = new HashMap<>();
        for (MenagerieLayout.Slot s : MenagerieLayout.SLOTS) {
            put(used, s.x(), 6, s.z(), "exhibit " + s.suffix());
            put(used, s.signX(), 1, s.signZ(), "sign " + s.suffix());
            put(used, s.standX(), 1, s.signZ(), "stand " + s.suffix());
            if (!s.isAlpha()) {
                put(used, s.plateX(), 1, s.plateZ(), "plate " + s.suffix());
            }
            assertTrue(build.contains(s.x() - 2, 0, s.z()) && build.contains(s.x() + 2, 10, s.z()), s.suffix());
            assertTrue(build.contains((int) Math.floor(s.entityX()), (int) (1 + s.hover() + 3), s.z()),
                    s.suffix() + " stands inside the box");
        }
        for (MenagerieLayout.BossPose p : MenagerieLayout.BOSS_POSES) {
            put(used, p.x(), 2, p.z(), "boss " + p.suffix());
            put(used, p.plateX(), 1, p.plateZ(), "plate " + p.suffix());
            put(used, p.signX(), 1, p.plateZ(), "sign " + p.suffix());
            assertTrue(build.contains(p.x() - 2, 9, -46) && build.contains(p.x() + 2, 1, -42), p.suffix());
        }
        for (MenagerieLayout.TraitDisplay t : MenagerieLayout.TRAIT_WALL) {
            put(used, t.x(), 2, MenagerieLayout.TRAIT_Z, "radiant " + t.entity());
            put(used, t.x(), 1, MenagerieLayout.TRAIT_SIGN_Z, "sign " + t.entity());
        }
        List<MenagerieLayout.Stand> stands = new ArrayList<>();
        stands.addAll(MenagerieLayout.dropStands());
        stands.addAll(MenagerieLayout.GEAR);
        stands.addAll(MenagerieLayout.eggStands());
        stands.addAll(MenagerieLayout.bossStands());
        stands.add(MenagerieLayout.KIOSK_SCANNER);
        for (MenagerieLayout.Stand st : stands) {
            put(used, st.x(), st.y(), st.z(), "stand " + st.item());
            assertTrue(build.contains(st.x(), st.y(), st.z()), st.item());
        }
        for (MenagerieLayout.PaddockMount m : MenagerieLayout.PADDOCK) {
            put(used, m.x(), 1, m.z(), "mount " + m.suffix());
            assertTrue(m.yard().containsXZ(m.entityX(), m.entityZ()), m.suffix() + " stands in its yard");
        }
        for (MenagerieLayout.Label label : MenagerieLayout.labels()) {
            assertTrue(build.contains(label.x(), 7, label.z()), label.key());
        }
        put(used, MenagerieLayout.CRUST_PLATE_X, 1, MenagerieLayout.CRUST_PLATE_Z, "crust plate");
    }

    private static void put(Map<String, String> used, int x, int y, int z, String what) {
        String key = x + "," + y + "," + z;
        String before = used.put(key, what);
        assertEquals(null, before, what + " shares " + key + " with " + before);
    }

    // --- 3. plates ---------------------------------------------------------------------------------

    @Test
    void everyPlateMapsBackToItsSlotAndNothingElseDoes() {
        Set<String> plates = new HashSet<>();
        for (int[] p : MenagerieLayout.plates()) {
            plates.add(p[0] + "," + p[1]);
        }
        assertEquals(34 + 12 + 1, plates.size(), "47 plates");
        MenagerieLayout.Box box = MenagerieLayout.BUILD_BOX;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                Optional<String> slot = MenagerieLayout.plateAt(x, 1, z);
                assertEquals(plates.contains(x + "," + z), slot.isPresent(), x + "," + z);
                assertFalse(MenagerieLayout.plateAt(x, 0, z).isPresent());
                assertFalse(MenagerieLayout.plateAt(x, 2, z).isPresent());
            }
        }
        for (MenagerieLayout.Slot s : MenagerieLayout.baseSlots()) {
            assertEquals(Optional.of(s.suffix()), MenagerieLayout.plateAt(s.plateX(), 1, s.plateZ()));
        }
        for (MenagerieLayout.BossPose p : MenagerieLayout.BOSS_POSES) {
            assertEquals(Optional.of(p.suffix()), MenagerieLayout.plateAt(p.plateX(), 1, p.plateZ()));
        }
    }

    // --- 4. tour -----------------------------------------------------------------------------------

    @Test
    void everyTourStopIsOnStreetOrPlazaFloorInsideTheBox() {
        assertEquals(25, MenagerieLayout.TOUR.size());
        for (int i = 0; i < MenagerieLayout.TOUR.size(); i++) {
            MenagerieLayout.TourStop stop = MenagerieLayout.TOUR.get(i);
            assertEquals(i + 1, stop.n());
            assertTrue(MenagerieLayout.isStreet(stop.x(), stop.z()), stop.id());
            assertTrue(MenagerieLayout.BUILD_BOX.contains(stop.x(), 1, stop.z()), stop.id());
        }
    }

    // --- 5. budget ---------------------------------------------------------------------------------

    @Test
    void budgetWithinCaps() {
        assertEquals(82, MenagerieLayout.menagerieMobs());
        assertEquals(124, MenagerieLayout.menagerieItemStands());
        assertEquals(23, MenagerieLayout.labels().size());
        assertEquals(230, MenagerieLayout.menagerieEntities());
        assertTrue(MenagerieLayout.menagerieMobs() <= MenagerieLayout.MAX_MOBS);
        assertTrue(MenagerieLayout.menagerieEntities() <= MenagerieLayout.MAX_ENTITIES);
        assertEquals(67, MenagerieLayout.gridMobs());
        assertTrue(MenagerieLayout.gridMobs() <= MenagerieLayout.MAX_MOBS);
        assertEquals(4, MenagerieLayout.MAX_RUNNING_DEMOS);
    }

    // --- 6. sign widths ----------------------------------------------------------------------------

    /** Vanilla default-font advance widths (glyph plus one pixel), for the characters that differ from 6. */
    static int pixels(String text) {
        int sum = 0;
        for (char c : text.toCharArray()) {
            sum += switch (c) {
                case 'i', '.', ',', ':', ';', '\'', '!', '|' -> 2;
                case 'l', '`' -> 3;
                case 't', 'I', ' ', '(', ')', '[', ']', '{', '}', '*', '"' -> 4;
                case 'f', 'k', '<', '>' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
        }
        return sum;
    }

    @Test
    void everySignLineFitsAt88Pixels() throws IOException {
        JsonObject lang = lang();
        int checked = 0;
        for (String key : lang.keySet()) {
            if (key.startsWith("gallery.nerocreatures.sign.") || key.startsWith("gallery.nerocreatures.alpha.")) {
                String value = lang.get(key).getAsString().replace("%s", "3");
                assertTrue(pixels(value) <= 88, key + " is " + pixels(value) + " px: " + value);
                checked++;
            }
        }
        assertTrue(checked > 150, "sign lines found: " + checked);
        for (MenagerieLayout.Slot s : MenagerieLayout.baseSlots()) {
            assertTrue(lang.has("gallery.nerocreatures.sign." + s.entity() + ".a"), s.entity());
            assertTrue(lang.has("gallery.nerocreatures.sign." + s.entity() + ".b"), s.entity());
        }
        for (MenagerieLayout.BossPose p : MenagerieLayout.BOSS_POSES) {
            assertTrue(lang.has("gallery.nerocreatures.sign." + p.boss() + ".p" + p.phase()), p.suffix());
            assertTrue(lang.has("boss.nerocreatures." + p.boss() + "." + p.phaseId()), p.suffix());
        }
        for (MenagerieLayout.TourStop stop : MenagerieLayout.TOUR) {
            assertTrue(lang.has("gallery.nerocreatures.tour." + stop.id()), stop.id());
        }
        for (MenagerieLayout.Label label : MenagerieLayout.labels()) {
            assertTrue(lang.has(label.key()), label.key());
        }
    }

    // --- 7. demos ----------------------------------------------------------------------------------

    @Test
    void everyExhibitHasADemoAndEveryPropIsInsideItsSlotBox() {
        for (MenagerieLayout.Slot s : MenagerieLayout.baseSlots()) {
            assertTrue(MenagerieLayout.demo(s.suffix()).isPresent(), s.suffix());
        }
        for (MenagerieLayout.BossPose p : MenagerieLayout.BOSS_POSES) {
            assertTrue(MenagerieLayout.demo(p.suffix()).isPresent(), p.suffix());
        }
        assertEquals(47, MenagerieLayout.DEMOS.size());
        for (MenagerieLayout.Demo demo : MenagerieLayout.DEMOS) {
            assertTrue(demo.shoot() < demo.ticks(), demo.slot());
            MenagerieLayout.Box box = MenagerieLayout.slotBox(demo.slot()).orElseThrow();
            for (int[] prop : MenagerieLayout.demoProps(demo.slot())) {
                assertTrue(box.contains(prop[0], prop[2], prop[1]), demo.slot() + " prop inside its box");
            }
        }
    }

    // --- 8. clear ----------------------------------------------------------------------------------

    @Test
    void clearRemovesOnlyTaggedEntitiesAndLitter() {
        assertTrue(MenagerieLayout.clearRemoves(Set.of(MenagerieLayout.TAG), false));
        assertTrue(MenagerieLayout.clearRemoves(Set.of(MenagerieLayout.TAG, MenagerieLayout.ANCHOR_TAG), false));
        assertTrue(MenagerieLayout.clearRemoves(Set.of(), true), "dropped items and XP orbs");
        assertFalse(MenagerieLayout.clearRemoves(Set.of(), false), "a player's pet stays");
        assertFalse(MenagerieLayout.clearRemoves(Set.of("nerocreatures:nest_home"), false), "untagged stays");
        assertFalse(MenagerieLayout.clearRemoves(Set.of("my_gallery"), false));
        assertTrue(MenagerieLayout.clearKeepsBlock(true, false, false), "a player's chest is neither emptied nor removed");
        assertFalse(MenagerieLayout.clearKeepsBlock(true, true, false), "the gallery's own habitat block goes");
        assertFalse(MenagerieLayout.clearKeepsBlock(true, false, true), "the builder's lectern goes");
        assertFalse(MenagerieLayout.clearKeepsBlock(false, false, false), "plain blocks go");
    }

    // --- shots -------------------------------------------------------------------------------------

    @Test
    void harnessShotListIs160NamedShots() {
        List<MenagerieShots.Shot> shots = MenagerieShots.menagerie();
        assertEquals(160, shots.size());
        Set<String> names = new HashSet<>();
        shots.forEach(s -> assertTrue(names.add(s.name()), "unique " + s.name()));
        Map<String, Integer> perSet = new HashMap<>();
        shots.forEach(s -> perSet.merge(s.set(), 1, Integer::sum));
        assertEquals(Map.of("overview", 3, "pavilions", 11, "creatures", 38, "abilities", 46, "alphas", 16,
                "radiant", 14, "bosses", 16, "rows", 16), perSet);
        assertTrue(names.contains("gallery_overview"), "the published lead image keeps its name");
        for (MenagerieShots.Shot s : shots) {
            if (s.set().equals("abilities")) {
                assertEquals(1, s.pre().size(), s.name());
                assertTrue(s.pre().get(0).startsWith("nerocreatures gallery demo "), s.name());
            }
            assertTrue(MenagerieLayout.CLEAR_BOX.containsXZ(s.cam()[0], s.cam()[2])
                    || s.set().equals("overview"), s.name() + " camera inside the gallery");
        }
        assertEquals(9, MenagerieShots.grid().size());
    }
}
