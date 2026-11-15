package za.co.neroland.nerocreatures.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** Decision 003: the jar-bundled pool parses, matches the built-in defaults and rejects bad data. */
class TraitPoolTest {

    @Test
    void bundledPoolMatchesDefaults() throws Exception {
        try (InputStream in = TraitPool.class.getResourceAsStream(TraitPool.RESOURCE)) {
            assertNotNull(in, "traits.json must be bundled");
            TraitPool pool = TraitPool.parse(in).result().orElseThrow();
            assertEquals(TraitPool.defaults(), pool);
            assertEquals(List.of("hardy", "swift", "keen", "bountiful", "calm", "thrifty", "stout", "radiant"),
                    List.copyOf(pool.asMap().keySet()));
            assertEquals(707, pool.totalWeight());
        }
    }

    @Test
    void rejectsUnknownEffectType() {
        assertTrue(parse("{\"traits\":[{\"id\":\"x\",\"weight\":1,\"effect\":{\"type\":\"teleport\"}}]}").isEmpty());
    }

    @Test
    void rejectsDuplicateIdsBadWeightsAndUnsupportedAttributes() {
        assertTrue(parse("{\"traits\":[{\"id\":\"x\",\"weight\":1,\"effect\":{\"type\":\"cosmetic\"}},"
                + "{\"id\":\"x\",\"weight\":1,\"effect\":{\"type\":\"cosmetic\"}}]}").isEmpty());
        assertTrue(parse("{\"traits\":[{\"id\":\"x\",\"weight\":0,\"effect\":{\"type\":\"cosmetic\"}}]}").isEmpty());
        assertTrue(parse("{\"traits\":[{\"id\":\"x\",\"weight\":1,\"effect\":{\"type\":\"attribute\","
                + "\"attribute\":\"minecraft:luck\",\"operation\":\"add_value\",\"amount\":1}}]}").isEmpty());
        assertTrue(parse("{\"traits\":[]}").isEmpty());
        assertTrue(parse("{\"mutation_chance\":2,\"traits\":[{\"id\":\"x\",\"weight\":1,"
                + "\"effect\":{\"type\":\"cosmetic\"}}]}").isEmpty());
    }

    @Test
    void mutationOffIsAPureDataChange() {
        TraitPool off = parse("{\"mutation_chance\":0,\"traits\":[{\"id\":\"calm\",\"weight\":1,"
                + "\"effect\":{\"type\":\"cosmetic\"}}]}").orElseThrow();
        assertEquals(0.0D, off.mutationChance());
    }

    private static java.util.Optional<TraitPool> parse(String json) {
        return TraitPool.parse(JsonParser.parseString(json)).result();
    }
}
