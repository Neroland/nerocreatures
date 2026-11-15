package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.data.CreatureOwnershipState.LoadVerdict;

/**
 * The load-time ownership check (decision 004). No world loses a pet: a tamed creature is released
 * only when erasure or retention positively recorded it or its owner, never because the index merely
 * does not know the owner.
 */
class OwnershipLoadVerdictTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID PET = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID UNINDEXED = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID BOBS_PET = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    private static final Identifier WISP = Identifier.fromNamespaceAndPath("nerocreatures", "glacite_wisp");
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private static OwnedCreature pet(UUID entity) {
        return new OwnedCreature(entity, OwnedCreature.Kind.PET, WISP, OVERWORLD);
    }

    private static CreatureOwnershipState roundTrip(CreatureOwnershipState state) {
        Tag tag = CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        return CreatureOwnershipState.storeCodec().parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }

    /** A 0.4.x file: one player, one indexed pet, no schema, no markers. */
    private static CreatureOwnershipState legacy() {
        CompoundTag row = new CompoundTag();
        row.putString("entity", PET.toString());
        row.putString("type", WISP.toString());
        row.putString("dimension", OVERWORLD.toString());
        ListTag owned = new ListTag();
        owned.add(row);
        CompoundTag player = new CompoundTag();
        player.putString("player", ALICE.toString());
        player.putLong("updated_at", 1_700_000_000_000L);
        player.put("owned", owned);
        ListTag players = new ListTag();
        players.add(player);
        CompoundTag root = new CompoundTag();
        root.put("players", players);
        return CreatureOwnershipState.storeCodec().parse(NbtOps.INSTANCE, root).result().orElseThrow();
    }

    @Test
    void legacySaveKeepsEveryPet() {
        CreatureOwnershipState state = legacy();
        assertEquals(LoadVerdict.INDEXED, state.loadVerdict(ALICE, PET));
        assertEquals(LoadVerdict.REINDEX, state.loadVerdict(ALICE, UNINDEXED), "known owner, missing row");
        assertEquals(LoadVerdict.REINDEX, state.loadVerdict(BOB, BOBS_PET),
                "owner unknown to a 0.4.x index (tamed before its row existed) is not released");
    }

    @Test
    void recoveredEmptyStoreReleasesNothing() {
        CreatureOwnershipState empty = new CreatureOwnershipState();
        assertEquals(LoadVerdict.REINDEX, empty.loadVerdict(ALICE, PET));
        assertEquals(LoadVerdict.REINDEX, roundTrip(empty).loadVerdict(BOB, BOBS_PET));
    }

    @Test
    void ownerWhoseLastRowWasRemovedKeepsOtherPets() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(ALICE, pet(PET));
        state.remove(ALICE, PET); // that pet died; Alice now has no rows at all
        assertFalse(state.knowsPlayer(ALICE));
        assertEquals(LoadVerdict.REINDEX, state.loadVerdict(ALICE, UNINDEXED));
    }

    @Test
    void erasureReleasesIndexedAndUnindexedCreaturesOfThatOwnerOnly() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(ALICE, pet(PET));
        state.add(BOB, pet(BOBS_PET));
        state.forgetPlayer(ALICE);

        CreatureOwnershipState loaded = roundTrip(state); // markers survive a save
        assertEquals(LoadVerdict.RELEASE, loaded.loadVerdict(ALICE, PET), "row removed by erasure");
        assertEquals(LoadVerdict.RELEASE, loaded.loadVerdict(ALICE, UNINDEXED), "owner erased");
        assertEquals(LoadVerdict.INDEXED, loaded.loadVerdict(BOB, BOBS_PET), "other players untouched");
        assertEquals(LoadVerdict.REINDEX, loaded.loadVerdict(BOB, UNINDEXED));

        // Freed creature: its own marker goes, the owner marker still covers the rest.
        loaded.creatureFreed(PET);
        assertEquals(LoadVerdict.RELEASE, loaded.loadVerdict(ALICE, UNINDEXED));
    }

    @Test
    void erasureMarkersNeverHoldThePlayerUuid() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(ALICE, pet(PET));
        state.forgetPlayer(ALICE);
        Tag tag = CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        assertFalse(tag.toString().contains(ALICE.toString()), "only a salted hash of the owner is kept");
        assertFalse(state.export(ALICE).toString().contains(PET.toString()), "nothing exported after erasure");
    }

    @Test
    void retamingAfterErasureClearsTheMarkers() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(ALICE, pet(PET));
        state.forgetPlayer(ALICE);
        state.add(ALICE, pet(UNINDEXED)); // the player came back and tamed again
        assertEquals(LoadVerdict.INDEXED, state.loadVerdict(ALICE, UNINDEXED));
        assertEquals(LoadVerdict.RELEASE, state.loadVerdict(ALICE, PET), "the erased row stays erased");
        // Someone else re-tames the released creature: no stale marker can release it again.
        state.add(BOB, pet(PET));
        assertEquals(LoadVerdict.INDEXED, state.loadVerdict(BOB, PET));
    }
}
