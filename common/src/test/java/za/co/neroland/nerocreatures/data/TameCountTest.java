package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import za.co.neroland.nerocreatures.progression.CreatureMilestones;

/**
 * The lifetime hand-tame count behind {@code tamed/10} and {@code tamed/25}: saturates at the highest
 * threshold, survives a save with no ownership rows, is erased with the player and appears only in
 * that player's export.
 */
class TameCountTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000012");

    private static CreatureOwnershipState roundTrip(CreatureOwnershipState state) {
        Tag tag = CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        return CreatureOwnershipState.storeCodec().parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }

    @Test
    void countSaturatesAtTheHighestThreshold() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        int last = 0;
        for (int i = 0; i < 40; i++) {
            last = state.noteTame(PLAYER);
        }
        assertEquals(CreatureOwnershipState.TAME_COUNT_CAP, last);
        assertEquals(25, state.tameCount(PLAYER));
        assertEquals(0, state.tameCount(OTHER));
    }

    @Test
    void countOnlyRowSurvivesASaveWithoutIndexingThePlayer() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        for (int i = 0; i < 12; i++) {
            state.noteTame(PLAYER);
        }
        CreatureOwnershipState loaded = roundTrip(state);
        assertEquals(12, loaded.tameCount(PLAYER));
        assertTrue(loaded.owned(PLAYER).isEmpty());
        assertTrue(loaded.knowsPlayer(PLAYER));
    }

    @Test
    void erasureDropsTheCountAndExportShowsOnlyTheOwnersCount() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.noteTame(PLAYER);
        state.noteTame(PLAYER);
        state.noteTame(OTHER);
        assertEquals(2, state.export(PLAYER).get("tames").getAsInt());
        assertFalse(state.export(UUID.randomUUID()).has("tames"));
        state.forgetPlayer(PLAYER);
        assertEquals(0, state.tameCount(PLAYER));
        assertFalse(state.export(PLAYER).has("tames"));
        assertEquals(1, roundTrip(state).tameCount(OTHER));
        assertEquals(0, roundTrip(state).tameCount(PLAYER));
    }

    @Test
    void tameCountGatesOpenAtTenAndTwentyFive() {
        assertEquals(List.of(), CreatureMilestones.tameCountGates(9));
        assertEquals(List.of(CreatureMilestones.TAMED_10), CreatureMilestones.tameCountGates(10));
        assertEquals(List.of(CreatureMilestones.TAMED_10), CreatureMilestones.tameCountGates(24));
        assertEquals(List.of(CreatureMilestones.TAMED_10, CreatureMilestones.TAMED_25),
                CreatureMilestones.tameCountGates(25));
        assertEquals("nerocreatures:tamed/10", CreatureMilestones.TAMED_10.toString());
        assertEquals("nerocreatures:bestiary/complete", CreatureMilestones.BESTIARY_COMPLETE.toString());
    }
}
