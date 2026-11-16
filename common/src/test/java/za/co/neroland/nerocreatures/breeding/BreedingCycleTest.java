package za.co.neroland.nerocreatures.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import za.co.neroland.nerocreatures.block.EggData;
import za.co.neroland.nerocreatures.block.EggLaying;
import za.co.neroland.nerocreatures.block.HatchRules;
import za.co.neroland.nerocreatures.block.IncubatorRules;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.data.OwnershipCaps;
import za.co.neroland.nerocreatures.entity.tame.HusbandryRules;
import za.co.neroland.nerocreatures.trait.Inheritance;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.trait.TraitSet;

/**
 * Breeding at the pure-logic level: breed (inherit) -> egg item custom data ->
 * Incubator save -> hatch, egg rows and their cap, hatchling marking, and every L/T species' data.
 */
class BreedingCycleTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final Identifier MOTH = Identifier.fromNamespaceAndPath("nerocreatures", "lumen_moth");
    private static final Identifier LUNA = Identifier.fromNamespaceAndPath("nerospace", "luna");

    /** Every L and T species (the 0.5.0 roster + the two existing pets) and its birth mode. */
    private static final List<String> EGG_SPECIES = List.of("lumen_moth", "slag_beetle", "spore_drifter",
            "magnet_crab", "void_manta", "lode_tortoise");
    private static final List<String> LIVE_SPECIES = List.of("dune_loper", "strata_hound", "rime_yak",
            "mossback", "prism_stag", "glacite_wisp", "xertz_forager");

    /** Breed -> lay (custom data) -> pick up and insert (item copy) -> incubator save/load -> hatch. */
    private static TraitSet throughTheEgg(TraitSet bred) {
        CompoundTag onTheItem = new EggData(MOTH.toString(), bred).toTag();
        CompoundTag inTheIncubator = onTheItem.copy();
        EggData hatched = EggData.fromTag(inTheIncubator).orElseThrow();
        assertEquals(MOTH.toString(), hatched.speciesId());
        return hatched.traits();
    }

    @Test
    void traitsSurviveTheFullBreedEggIncubateHatchCycle() {
        TraitPool noMutation = new TraitPool(1, 0.0D, TraitPool.defaults().traits());
        RandomSource random = RandomSource.create(11L);
        TraitSet mother = TraitSet.of("hardy", "swift");
        TraitSet father = TraitSet.of("keen", "thrifty");
        for (int i = 0; i < 2_000; i++) {
            TraitSet bred = Inheritance.inherit(noMutation, mother, father, random);
            TraitSet hatched = throughTheEgg(bred);
            assertEquals(bred, hatched, "the hatchling carries exactly the bred traits");
            assertTrue(hatched.a().equals("hardy") || hatched.a().equals("swift"), "one slot from the mother");
            assertTrue(hatched.b().equals("keen") || hatched.b().equals("thrifty"), "one slot from the father");
        }
    }

    @Test
    void mutationStillAppliesAndRadiantBreedsTrueThroughTheEgg() {
        TraitPool pool = TraitPool.defaults();
        RandomSource random = RandomSource.create(5L);
        TraitSet radiant = TraitSet.of(TraitPool.RADIANT, TraitPool.RADIANT);
        int trials = 20_000;
        int radiantHatchlings = 0;
        int mutatedSlots = 0;
        for (int i = 0; i < trials; i++) {
            TraitSet hatched = throughTheEgg(Inheritance.inherit(pool, radiant, radiant, random));
            radiantHatchlings += hatched.isRadiant() ? 1 : 0;
            mutatedSlots += hatched.a().equals(TraitPool.RADIANT) ? 0 : 1;
            mutatedSlots += hatched.b().equals(TraitPool.RADIANT) ? 0 : 1;
        }
        // Radiant unless both slots mutate away (and a mutation can roll Radiant again): about 99.75%.
        assertTrue(radiantHatchlings / (double) trials > 0.99D, "two Radiant parents give Radiant eggs");
        // About 5% of slots mutate; a mutation re-rolls Radiant about 1% of the time.
        assertEquals(0.05D * (1.0D - 7.0D / 707.0D), mutatedSlots / (2.0D * trials), 0.006D);
    }

    @Test
    void oneEggItemPerSpecies() {
        assertEquals("lumen_moth_egg", EggLaying.eggPath("lumen_moth"));
        assertEquals("lode_tortoise", EggLaying.speciesPathOf("lode_tortoise_egg"));
        assertNull(EggLaying.speciesPathOf("lumen_moth_spawn_egg"), "spawn eggs are not creature eggs");
        assertNull(EggLaying.speciesPathOf("_egg"));
        assertNull(EggLaying.speciesPathOf("stalker_hide"));
        for (String species : EGG_SPECIES) {
            assertEquals(species, EggLaying.speciesPathOf(EggLaying.eggPath(species)));
        }
    }

    @Test
    void eggRowsAreCappedPerPlayerAndFreedWhenTheEggLeaves() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        int cap = OwnershipCaps.DEFAULT_MAX_EGGS;
        List<OwnedCreature> rows = new java.util.ArrayList<>();
        for (int i = 0; i < cap; i++) {
            OwnedCreature row = OwnedCreature.egg(MOTH, LUNA, 24_000L);
            assertTrue(state.tryAdd(ALICE, row, cap));
            rows.add(row);
        }
        OwnedCreature extra = OwnedCreature.egg(MOTH, LUNA, 24_000L);
        assertFalse(state.tryAdd(ALICE, extra, cap), "the ninth egg is refused");
        assertEquals(IncubatorRules.Insert.CAP, IncubatorRules.decideInsert(false, true, false));
        assertEquals(0, state.count(ALICE, OwnedCreature.Kind.PET), "eggs never count as pets");
        assertTrue(state.tryAdd(BOB, extra, cap), "another player's cap is their own");

        // Hatch / take out / break: the incubator removes the row by its incubation id alone.
        assertTrue(state.removeRow(rows.get(0).entity()));
        assertFalse(state.removeRow(rows.get(0).entity()), "removed once");
        assertEquals(cap - 1, state.count(ALICE, OwnedCreature.Kind.EGG));
        assertTrue(state.tryAdd(ALICE, OwnedCreature.egg(MOTH, LUNA, 24_000L), cap), "a freed slot is reusable");
        assertEquals(1, state.count(BOB, OwnedCreature.Kind.EGG));

        // A cap of 0 refuses every egg.
        assertFalse(new CreatureOwnershipState().tryAdd(ALICE, OwnedCreature.egg(MOTH, LUNA, 1L), 0));
    }

    @Test
    void erasureDropsEggRowsWithoutReleaseMarkers() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        OwnedCreature egg = OwnedCreature.egg(MOTH, LUNA, 24_000L);
        state.add(ALICE, egg);
        assertEquals(List.of(egg), state.forgetPlayer(ALICE));
        assertEquals(0, state.count(ALICE, OwnedCreature.Kind.EGG));
        assertFalse(state.removeRow(egg.entity()), "the incubator's later remove is a harmless no-op");
    }

    /** Egg-baby marking: the egg path spawns no baby at breeding, so hatch marks it. */
    @Test
    void hatchedLivestockIsHusbandedAndHatchedPetsTameOnFirstFeed() {
        HatchRules livestock = HatchRules.of(false, true);
        assertTrue(livestock.baby());
        assertTrue(livestock.husbanded());
        assertFalse(livestock.hatchling());
        assertFalse(HusbandryRules.mayDespawn(false, false, livestock.husbanded()), "a hatched tortoise stays");
        assertFalse(HusbandryRules.wildPrey(false, false, false, livestock.husbanded()), "and is nobody's prey");

        HatchRules pet = HatchRules.of(true, false);
        assertTrue(pet.baby());
        assertTrue(pet.hatchling(), "first feed tames at 100%");
        assertFalse(pet.husbanded());
    }

    @Test
    void everyLivestockAndTameableSpeciesHasItsBreedingData() throws Exception {
        JsonObject species = read("/data/nerocreatures/creature_data/species.json").getAsJsonObject()
                .getAsJsonObject("species");
        SpeciesProfiles table = SpeciesProfiles.parse(read("/data/nerocreatures/creature_data/species.json"))
                .result().orElseThrow();
        Set<String> eggItems = new HashSet<>();
        read("/data/nerocreatures/tags/item/creature_eggs.json").getAsJsonObject().getAsJsonArray("values")
                .forEach(v -> eggItems.add(v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString()
                        : v.getAsString()));
        for (String path : EGG_SPECIES) {
            assertTrue(species.has(path), path + " has a species row");
            assertEquals(BirthMode.EGG, table.species().get(path).birth(), path + " lays eggs");
            assertTrue(eggItems.contains("nerocreatures:" + EggLaying.eggPath(path)), path + " egg is tagged");
            assertNotNull(read("/data/nerocreatures/tags/item/food/" + path + ".json"), path + " food tag");
        }
        for (String path : LIVE_SPECIES) {
            assertTrue(species.has(path), path + " has a species row");
            assertEquals(BirthMode.LIVE, table.species().get(path).birth(), path + " gives live birth");
            assertNotNull(read("/data/nerocreatures/tags/item/food/" + path + ".json"), path + " food tag");
        }
    }

    private static JsonElement read(String resource) throws Exception {
        try (InputStream in = BreedingCycleTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
