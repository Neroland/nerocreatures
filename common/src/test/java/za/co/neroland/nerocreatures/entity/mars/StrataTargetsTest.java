package za.co.neroland.nerocreatures.entity.mars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrataTargetsTest {

    @Test
    void rawOresAndProductsMapToVanillaOreTags() {
        assertEquals("#minecraft:iron_ores", StrataTargets.specForItem("minecraft", "raw_iron"));
        assertEquals("#minecraft:copper_ores", StrataTargets.specForItem("minecraft", "raw_copper"));
        assertEquals("#minecraft:gold_ores", StrataTargets.specForItem("minecraft", "raw_gold"));
        assertEquals("#minecraft:coal_ores", StrataTargets.specForItem("minecraft", "coal"));
        assertEquals("#minecraft:diamond_ores", StrataTargets.specForItem("minecraft", "diamond"));
        assertEquals("#minecraft:lapis_ores", StrataTargets.specForItem("minecraft", "lapis_lazuli"));
        assertEquals("#minecraft:redstone_ores", StrataTargets.specForItem("minecraft", "redstone"));
    }

    @Test
    void otherRawMaterialsUseTheConventionTag() {
        assertEquals("#c:ores/tin", StrataTargets.specForItem("othermod", "raw_tin"));
        assertNull(StrataTargets.specForItem("othermod", "raw_"), "no material");
        assertNull(StrataTargets.specForItem("minecraft", "stick"), "not a prospecting item");
        assertNull(StrataTargets.specForItem("othermod", "diamond"), "only vanilla's own diamond is shorthand");
    }

    @Test
    void oreBlocksWidenToTheirTagOtherBlocksStayExact() {
        assertEquals("#minecraft:iron_ores", StrataTargets.specForBlock("minecraft", "deepslate_iron_ore"));
        assertEquals("#minecraft:gold_ores", StrataTargets.specForBlock("minecraft", "nether_gold_ore"));
        assertEquals("minecraft:amethyst_block", StrataTargets.specForBlock("minecraft", "amethyst_block"));
        assertEquals("othermod:tin_ore", StrataTargets.specForBlock("othermod", "tin_ore"));
        assertNull(StrataTargets.specForBlock("minecraft", "air"));
    }

    @Test
    void specsParseAndColour() {
        assertTrue(StrataTargets.isTag("#minecraft:iron_ores"));
        assertFalse(StrataTargets.isTag("minecraft:stone"));
        assertEquals("minecraft:iron_ores", StrataTargets.idOf("#minecraft:iron_ores"));
        assertEquals(StrataTargets.FAMILY_IRON, StrataTargets.colourFamily("#minecraft:iron_ores"));
        assertEquals(StrataTargets.FAMILY_REDSTONE, StrataTargets.colourFamily("#minecraft:redstone_ores"));
        assertEquals(StrataTargets.FAMILY_BLUE, StrataTargets.colourFamily("#minecraft:diamond_ores"));
        assertEquals(StrataTargets.FAMILY_NONE, StrataTargets.colourFamily("minecraft:stone"));
        assertEquals(StrataTargets.FAMILY_NONE, StrataTargets.colourFamily(null));
        for (String s : new String[] {"#minecraft:coal_ores", "#minecraft:emerald_ores", "#c:ores/tin"}) {
            int f = StrataTargets.colourFamily(s);
            assertTrue(f >= 0 && f <= 7, "variant range 0-7");
        }
    }

    @Test
    void chestsAndOtherBlockEntitiesAreRefused() {
        // M5: showing the hound a chest must never lead anyone to another player's chest.
        assertEquals("minecraft:chest", StrataTargets.specForBlock("minecraft", "chest"), "the spec is exact...");
        assertFalse(StrataTargets.trackable(false, true), "...but a chest is refused: block entity, not allowed");
        assertFalse(StrataTargets.trackable(true, true), "a block entity is refused even if a pack tags it");
        assertFalse(StrataTargets.trackable(false, false), "anything outside the allow tag is refused");
        assertTrue(StrataTargets.trackable(true, false), "ore or natural stone");
    }

    @Test
    void allowTagHoldsOresAndStoneOnly() throws java.io.IOException {
        try (java.io.InputStream in = StrataTargetsTest.class.getResourceAsStream(
                "/data/nerocreatures/tags/block/strata_trackable.json")) {
            assertTrue(in != null, "allow tag on the classpath");
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(json.contains("#minecraft:iron_ores"));
            assertTrue(json.contains("#c:ores"));
            for (String banned : new String[] {"chest", "barrel", "shulker", "spawner", "furnace", "hopper",
                    "#minecraft:logs", "planks"}) {
                assertFalse(json.contains(banned), "never tracks " + banned);
            }
        }
        assertEquals("nerocreatures:strata_trackable", StrataTargets.TRACKABLE_TAG);
    }
}
