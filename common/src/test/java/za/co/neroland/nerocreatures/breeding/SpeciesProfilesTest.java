package za.co.neroland.nerocreatures.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** Decision 003: the bundled species table parses; missing rows and unknown birth modes are lenient. */
class SpeciesProfilesTest {

    @Test
    void bundledTableParses() throws Exception {
        try (InputStream in = SpeciesProfiles.class.getResourceAsStream(SpeciesProfiles.RESOURCE)) {
            assertNotNull(in, "species.json must be bundled");
            SpeciesProfiles table = SpeciesProfiles.parse(JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))).result().orElseThrow();
            assertEquals(BirthMode.LIVE, table.species().get("glacite_wisp").birth());
        }
    }

    @Test
    void rowsDefaultAndFoodTagFollowsConvention() {
        SpeciesProfiles table = SpeciesProfiles.parse(JsonParser.parseString(
                "{\"species\":{\"lumen_moth\":{\"birth\":\"egg\"},\"odd\":{\"birth\":\"budding\"}}}"))
                .result().orElseThrow();
        SpeciesProfile moth = table.species().get("lumen_moth");
        assertEquals(BirthMode.EGG, moth.birth());
        assertEquals(1, moth.studiedThreshold());
        assertEquals(0, moth.productIntervalTicks());
        assertEquals("nerocreatures:food/lumen_moth", moth.foodTagId("lumen_moth"));
        assertEquals(BirthMode.LIVE, table.species().get("odd").birth());
    }
}
