package za.co.neroland.nerocreatures.entity.alpha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class AlphaJsonTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesModifierAndSkipsBadEntries() {
        AlphaModifier m = AlphaJson.modifier("nerocreatures:swift", json("""
                {"weight": 7,
                 "attributes": [
                   {"attribute": "minecraft:movement_speed", "amount": 0.3, "operation": "add_multiplied_base"},
                   {"attribute": "minecraft:armor", "amount": 2, "operation": "nonsense"},
                   {"attribute": "", "amount": 1},
                   {"attribute": "minecraft:armor", "amount": 0}
                 ],
                 "effects": [{"effect": "minecraft:regeneration", "amplifier": 99}, {"amplifier": 1}]}
                """));
        assertNotNull(m);
        assertEquals(7, m.weight());
        assertEquals(1, m.attributes().size());
        assertEquals("add_multiplied_base", m.attributes().get(0).operation());
        assertEquals(1, m.effects().size());
        assertEquals(4, m.effects().get(0).amplifier(), "amplifier clamps to 4");
    }

    @Test
    void emptyModifierIsDropped() {
        assertNull(AlphaJson.modifier("x:y", json("{\"weight\": 3}")));
    }

    @Test
    void settingsClampAndDefault() {
        AlphaSettings s = AlphaJson.settings(json("{\"chance\": 7, \"max_per_dimension\": 999}"));
        assertEquals(1.0, s.chance());
        assertEquals(AlphaSettings.ABSOLUTE_MAX_PER_DIMENSION, s.maxPerDimension());
        assertEquals(AlphaSettings.DEFAULTS.scaleBonus(), s.scaleBonus());
        assertEquals(AlphaSettings.DEFAULTS.base(), s.base());
        AlphaSettings bad = AlphaJson.settings(json("{\"chance\": \"lots\"}"));
        assertEquals(AlphaSettings.DEFAULTS.chance(), bad.chance());
    }

    @Test
    void parsesDrops() {
        List<AlphaDrop> drops = AlphaJson.drops(json("""
                {"drops": [{"item": "nerocreatures:void_essence", "count": 300},
                           {"count": 1},
                           {"item": "minecraft:diamond", "weight": 0}]}
                """));
        assertEquals(2, drops.size());
        assertEquals(64, drops.get(0).count());
        assertEquals(1, drops.get(1).weight());
        assertEquals(0, AlphaJson.drops(json("{}")).size());
    }
}
