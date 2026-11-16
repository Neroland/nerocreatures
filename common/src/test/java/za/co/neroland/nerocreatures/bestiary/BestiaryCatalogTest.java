package za.co.neroland.nerocreatures.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BestiaryCatalogTest {

    private static JsonObject lang() throws Exception {
        try (InputStream in = BestiaryCatalogTest.class.getResourceAsStream("/assets/nerocreatures/lang/en_us.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void holdsThe38RosterSpeciesOnce() {
        Set<String> ids = new HashSet<>();
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            assertTrue(ids.add(entry.id()), entry.id());
        }
        assertEquals(38, ids.size());
        assertEquals(13, BestiaryCatalog.entries().stream().filter(BestiaryCatalog.Entry::radiant).count());
        assertEquals(4, BestiaryCatalog.entries().stream().filter(BestiaryCatalog.Entry::boss).count());
        assertTrue(BestiaryCatalog.entries().stream().filter(BestiaryCatalog.Entry::boss).allMatch(e -> e.sigil().isPresent()));
    }

    @Test
    void everySpeciesHasItsTextAndBothPictures() throws Exception {
        JsonObject lang = lang();
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            for (String part : new String[] {".ability", ".weakness"}) {
                String key = "bestiary.nerocreatures." + entry.id() + part;
                assertTrue(lang.has(key), key);
                assertTrue(lang.get(key).getAsString().length() <= 220, key);
            }
            if (entry.overworld()) {
                assertTrue(lang.has("bestiary.nerocreatures." + entry.id() + ".also"), entry.id());
            }
            assertTrue(lang.has("habitat.nerocreatures." + entry.habitat().id()));
            for (String drop : entry.drops()) {
                String key = "bestiary.nerocreatures.drop." + drop.substring(drop.indexOf(':') + 1) + ".use";
                assertTrue(lang.has(key), key);
            }
            for (String kind : new String[] {"portrait", "silhouette"}) {
                String path = "/assets/nerocreatures/textures/gui/bestiary/" + kind + "/" + entry.id() + ".png";
                try (InputStream in = BestiaryCatalogTest.class.getResourceAsStream(path)) {
                    assertNotNull(in, path);
                }
            }
            for (String tier : new String[] {"scanned", "studied"}) {
                String path = "/data/nerocreatures/neroland_gates/bestiary/" + tier + "/" + entry.id() + ".json";
                try (InputStream in = BestiaryCatalogTest.class.getResourceAsStream(path)) {
                    assertNotNull(in, path);
                }
            }
        }
    }

    @Test
    void gateIdsAreOnTheBestiaryPaths() {
        assertEquals("nerocreatures:bestiary/scanned/lumen_moth", BestiaryProgress.scannedGate("lumen_moth").toString());
        assertEquals("nerocreatures:bestiary/studied/lumen_moth", BestiaryProgress.studiedGate("lumen_moth").toString());
    }
}
