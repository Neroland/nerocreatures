package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.progression.ProgressionState;

import za.co.neroland.nerocreatures.block.NestRules;
import za.co.neroland.nerocreatures.boss.BossContributionState;

/**
 * POPIA/GDPR data access: {@code /nerocreatures export} returns everything this mod holds about one
 * player and nothing about anyone else. Two players share a fight, both have pets, eggs, gates and a
 * loaded creature with a nest; each export is checked for the other player's every identifier.
 */
class CreatureExportTest {

    private static final Identifier WISP = Identifier.fromNamespaceAndPath("nerocreatures", "glacite_wisp");
    private static final Identifier YAK = Identifier.fromNamespaceAndPath("nerocreatures", "rime_yak");
    private static final Identifier BOSS = Identifier.fromNamespaceAndPath("nerocreatures", "bloom_sovereign");
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID alicePet = UUID.randomUUID();
    private final UUID aliceMount = UUID.randomUUID();
    private final UUID bobPet = UUID.randomUUID();
    private final UUID sharedFight = UUID.randomUUID();
    private final UUID bobsFight = UUID.randomUUID();
    private final OwnedCreature aliceEgg = OwnedCreature.egg(WISP, OVERWORLD, 1234L);
    private final OwnedCreature bobEgg = OwnedCreature.egg(YAK, OVERWORLD, 5678L);

    private final CreatureOwnershipState ownership = new CreatureOwnershipState();
    private final BossContributionState contributions = new BossContributionState();
    private final ProgressionState gates = new ProgressionState();
    private final List<CreatureExport.LoadedCreature> loaded;

    CreatureExportTest() {
        this.ownership.add(this.alice, new OwnedCreature(this.alicePet, OwnedCreature.Kind.PET, WISP, OVERWORLD));
        this.ownership.add(this.alice, new OwnedCreature(this.aliceMount, OwnedCreature.Kind.MOUNT, YAK, OVERWORLD)
                .withParked(true, null));
        this.ownership.add(this.alice, this.aliceEgg);
        this.ownership.add(this.bob, new OwnedCreature(this.bobPet, OwnedCreature.Kind.PET, WISP, OVERWORLD));
        this.ownership.add(this.bob, this.bobEgg);
        this.contributions.record(this.sharedFight, BOSS, this.alice, 12.5F);
        this.contributions.record(this.sharedFight, BOSS, this.bob, 77.25F);
        this.contributions.record(this.bobsFight, BOSS, this.bob, 3.0F);
        this.gates.setPlayer(this.alice, id("bestiary/scanned/glacite_wisp"), true);
        this.gates.setPlayer(this.alice, id("first_tame"), true);
        this.gates.setPlayer(this.alice, Identifier.fromNamespaceAndPath("othermod", "gate"), true);
        this.gates.setPlayer(this.bob, id("bestiary/studied/rime_yak"), true);
        this.loaded = List.of(
                new CreatureExport.LoadedCreature(this.alice, this.alicePet, WISP.toString(), "pet",
                        OVERWORLD.toString(), Optional.of(new NestRules.Home(OVERWORLD.toString(), 10, 64, -20))),
                new CreatureExport.LoadedCreature(this.bob, this.bobPet, WISP.toString(), "pet",
                        OVERWORLD.toString(), Optional.of(new NestRules.Home(OVERWORLD.toString(), 999, 70, 888))));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocreatures", path);
    }

    private JsonObject export(UUID player) {
        return CreatureExport.build(player, 42L, this.ownership, this.contributions, this.gates, this.loaded);
    }

    @Test
    void exportHoldsEverythingAboutThePlayer() {
        JsonObject json = export(this.alice);
        String text = json.toString();
        assertEquals(this.alice.toString(), json.get("player").getAsString());
        JsonArray owned = json.getAsJsonObject("ownership").getAsJsonArray("owned");
        assertEquals(3, owned.size(), "pet, parked mount and egg");
        assertTrue(text.contains(this.alicePet.toString()));
        assertTrue(text.contains(this.aliceMount.toString()));
        assertTrue(text.contains(this.aliceEgg.entity().toString()));
        assertTrue(text.contains("\"hatch_at\":1234"));
        assertTrue(text.contains("\"parked\":true"));
        JsonArray fights = json.getAsJsonObject("boss_contribution").getAsJsonArray("boss_contribution");
        assertEquals(1, fights.size());
        assertEquals(12.5F, fights.get(0).getAsJsonObject().get("damage").getAsFloat());
        JsonArray gateList = json.getAsJsonArray("progression_gates");
        assertEquals(2, gateList.size(), "this mod's gates only");
        JsonArray creatures = json.getAsJsonArray("loaded_creatures");
        assertEquals(1, creatures.size());
        assertEquals(10, creatures.get(0).getAsJsonObject().getAsJsonObject("nest").get("x").getAsInt());
        assertFalse(json.get("erasure_marker").getAsBoolean());
    }

    @Test
    void exportHoldsNothingAboutAnyoneElse() {
        String aliceText = export(this.alice).toString();
        for (String other : List.of(this.bob.toString(), this.bobPet.toString(), this.bobEgg.entity().toString(),
                this.bobsFight.toString(), "77.25", "bestiary/studied/rime_yak", "\"x\":999", "\"z\":888",
                "\"hatch_at\":5678")) {
            assertFalse(aliceText.contains(other), "Alice's export leaks " + other);
        }
        String bobText = export(this.bob).toString();
        for (String other : List.of(this.alice.toString(), this.alicePet.toString(), this.aliceMount.toString(),
                this.aliceEgg.entity().toString(), "12.5", "first_tame", "bestiary/scanned", "\"x\":10")) {
            assertFalse(bobText.contains(other), "Bob's export leaks " + other);
        }
        assertEquals(2, export(this.bob).getAsJsonObject("boss_contribution")
                .getAsJsonArray("boss_contribution").size(), "Bob's own two fights");
    }

    @Test
    void erasedPlayerExportsOnlyTheMarkerFlag() {
        this.ownership.forgetPlayer(this.alice);
        this.contributions.forgetPlayer(this.alice);
        this.gates.forgetPlayer(this.alice);
        JsonObject json = CreatureExport.build(this.alice, 42L, this.ownership, this.contributions, this.gates,
                List.of());
        assertEquals(0, json.getAsJsonObject("ownership").getAsJsonArray("owned").size());
        assertEquals(0, json.getAsJsonObject("boss_contribution").getAsJsonArray("boss_contribution").size());
        assertEquals(0, json.getAsJsonArray("progression_gates").size());
        assertTrue(json.get("erasure_marker").getAsBoolean(), "the export says an erasure marker exists");
        assertFalse(export(this.bob).get("erasure_marker").getAsBoolean());
    }
}
