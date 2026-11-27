package za.co.neroland.nerocreatures.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;

/** The companion SPI 0.6 additions: rules, park rows, opt-in data. */
class CompanionSpiTest {
    //? if <1.20.3 {
    /*static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    *///?}


    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PET = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final Identifier MOTH = Identifier.fromNamespaceAndPath("nerocreatures", "lumen_moth");
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier LUNA = Identifier.fromNamespaceAndPath("nerospace", "luna");

    @Test
    void everyRequestedMethodExistsWithANoOpOrSafeDefault() throws Exception {
        String[] methods = {"isSmall", "onPetPerched", "canFetch", "addCompanionGoals", "onPetParking",
                "onPetUnparked", "allowsRecovery", "hatFor", "onPetLoaded"};
        for (String name : methods) {
            boolean found = false;
            for (Method method : CompanionPetHooks.class.getMethods()) {
                if (method.getName().equals(name)) {
                    assertTrue(method.isDefault(), name + " is a default method (additive)");
                    found = true;
                }
            }
            assertTrue(found, name + " exists");
        }
    }

    @Test
    void perchAndFetchNeedATamedAdultOfAnOptedInSpecies() {
        assertTrue(CompanionSupport.optedIn(true, true, false, true));
        assertFalse(CompanionSupport.optedIn(true, false, false, true), "wild");
        assertFalse(CompanionSupport.optedIn(true, true, true, true), "baby");
        assertFalse(CompanionSupport.optedIn(true, true, false, false), "species not opted in");
        assertFalse(CompanionSupport.optedIn(false, true, false, true), "not a NeroCreatures pet");
    }

    @Test
    void companionGoalsStayInsideTheBand() {
        assertFalse(CompanionSupport.allowedPriority(0), "float");
        assertFalse(CompanionSupport.allowedPriority(1), "sit");
        assertFalse(CompanionSupport.allowedPriority(2), "attack");
        for (int p = 3; p <= 6; p++) {
            assertTrue(CompanionSupport.allowedPriority(p));
        }
        assertFalse(CompanionSupport.allowedPriority(7), "stroll");
        assertFalse(CompanionSupport.allowedPriority(-1));
    }

    @Test
    void parkingIsRefusedForWildRiddenOrFightingPets() {
        assertTrue(CompanionSupport.mayPark(true, true, false, false));
        assertFalse(CompanionSupport.mayPark(false, true, false, false));
        assertFalse(CompanionSupport.mayPark(true, false, false, false));
        assertFalse(CompanionSupport.mayPark(true, true, true, false));
        assertFalse(CompanionSupport.mayPark(true, true, false, true));
    }

    @Test
    void aParkedPetKeepsItsRowAndCapSlotAcrossASave() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(ALICE, new OwnedCreature(PET, OwnedCreature.Kind.PET, MOTH, OVERWORLD));
        assertTrue(state.setParked(ALICE, PET, true, null));
        assertTrue(state.isParked(ALICE, PET));
        assertEquals(1, state.count(ALICE, OwnedCreature.Kind.PET), "still counts against the pet cap");
        assertTrue(state.export(ALICE).getAsJsonArray("owned").get(0).getAsJsonObject().get("parked").getAsBoolean());

        OwnedCreature row = state.owned(ALICE).get(0);
        OwnedCreature reread = OwnedCreature.CODEC.parse(NbtOps.INSTANCE,
                OwnedCreature.CODEC.encodeStart(NbtOps.INSTANCE, row).getOrThrow()).result().orElseThrow();
        assertTrue(reread.parked(), "the mark survives a save");

        // Unparked somewhere else: the dimension follows, the mark goes, and the key is not written.
        assertTrue(state.setParked(ALICE, PET, false, LUNA));
        OwnedCreature moved = state.owned(ALICE).get(0);
        assertFalse(moved.parked());
        assertEquals(LUNA, moved.dimension());
        CompoundTag encoded = (CompoundTag) OwnedCreature.CODEC.encodeStart(NbtOps.INSTANCE, moved).getOrThrow();
        assertFalse(encoded.contains("parked"), "unparked rows keep the 0.4.x shape");
    }

    @Test
    void aPetErasedWhileParkedIsReleasedNotReIndexed() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(ALICE, new OwnedCreature(PET, OwnedCreature.Kind.PET, MOTH, OVERWORLD));
        state.setParked(ALICE, PET, true, null);
        state.forgetPlayer(ALICE);
        assertFalse(state.setParked(ALICE, PET, false, LUNA), "unpark never re-adds an erased row");
        assertEquals(CreatureOwnershipState.LoadVerdict.RELEASE, state.loadVerdict(ALICE, PET),
                "the re-created pet goes wild on its first tick");
    }

    @Test
    void smallPetsAndFetchersAreOptedIn() throws Exception {
        Set<String> perch = tag("companion_perch");
        assertTrue(perch.contains("nerocreatures:lumen_moth"));
        assertTrue(perch.contains("nerocreatures:magnet_crab"));
        assertTrue(perch.contains("nerocreatures:glacite_wisp"));
        assertFalse(perch.contains("nerocreatures:xertz_forager"), "forager does not perch");
        Set<String> fetch = tag("companion_fetch");
        assertTrue(fetch.contains("nerocreatures:lumen_moth"));
        assertTrue(fetch.contains("nerocreatures:magnet_crab"));
    }

    private static Set<String> tag(String name) throws Exception {
        String resource = "/data/nerocreatures/tags/entity_type/" + name + ".json";
        try (InputStream in = CompanionSpiTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            Set<String> out = new HashSet<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("values").forEach(v -> out.add(v.getAsString()));
            return out;
        }
    }
}
