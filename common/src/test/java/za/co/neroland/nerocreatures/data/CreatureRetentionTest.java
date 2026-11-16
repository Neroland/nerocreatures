package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.data.CreatureOwnershipState.LoadVerdict;

/**
 * POPIA/GDPR retention for the ownership store. Inactivity is Core's last-seen time (Core's
 * purge-inactive sweep reaches this store through the eraser), so this store never releases an active
 * player's creatures on its own; it only expires owner erasure markers, never creature markers. Boss
 * contribution retention is in {@code BossContributionPrivacyTest}; Bestiary and milestone gates are
 * Core's store.
 */
class CreatureRetentionTest {

    private static final long DAY = 86_400_000L;
    private static final Identifier WISP = Identifier.fromNamespaceAndPath("nerocreatures", "glacite_wisp");
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private final long[] now = {1_800_000_000_000L};

    @AfterEach
    void restoreClocks() {
        CreatureOwnershipState.clock = System::currentTimeMillis;
    }

    private CreatureOwnershipState store() {
        CreatureOwnershipState.clock = () -> this.now[0];
        return new CreatureOwnershipState();
    }

    private static OwnedCreature pet(UUID entity) {
        return new OwnedCreature(entity, OwnedCreature.Kind.PET, WISP, OVERWORLD);
    }

    private static CreatureOwnershipState roundTrip(CreatureOwnershipState state) {
        Tag tag = CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        return CreatureOwnershipState.storeCodec().parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }

    @Test
    void anActivePlayerWhoseRowsNeverChangeKeepsEverything() {
        CreatureOwnershipState state = store();
        UUID player = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        state.add(player, pet(pet));
        state.add(player, OwnedCreature.egg(WISP, OVERWORLD, 10L));
        this.now[0] += 3000 * DAY; // tamed nothing new for years, but may log in daily

        assertEquals(0, CreatureData.applyRetention(state, 30));
        assertTrue(state.knowsPlayer(player));
        assertEquals(2, state.owned(player).size());
        assertEquals(LoadVerdict.INDEXED, state.loadVerdict(player, pet));
    }

    @Test
    void ownerMarkerLifetimeIsNeverShorterThanAYear() {
        assertEquals(365, CreatureOwnershipState.markerLifetimeDays(0));
        assertEquals(365, CreatureOwnershipState.markerLifetimeDays(30));
        assertEquals(400, CreatureOwnershipState.markerLifetimeDays(400));
    }

    @Test
    void ownerHashExpiresButTheCreatureMarkerSurvivesAndStillReleases() {
        CreatureOwnershipState state = store();
        UUID player = UUID.randomUUID();
        UUID creature = UUID.randomUUID();
        state.add(player, pet(creature));
        state.forgetPlayer(player);
        assertTrue(state.hasErasureMarker(player));
        assertEquals(2, state.erasureMarkerCount(), "owner hash + creature id");

        this.now[0] += 364 * DAY;
        assertEquals(0, CreatureData.applyRetention(state, 30), "a 30-day server still keeps the owner hash a year");
        CreatureOwnershipState loaded = roundTrip(state);
        this.now[0] += 2 * DAY;
        assertEquals(1, CreatureData.applyRetention(loaded, 30), "only the owner hash expires");
        assertFalse(loaded.hasErasureMarker(player));
        assertEquals(1, loaded.erasureMarkerCount(), "the creature marker stays until the creature is freed");
        Tag after = CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, loaded).getOrThrow();
        assertFalse(after.toString().contains("erasure_salt"), "the salt goes with the last owner marker");

        this.now[0] += 5000 * DAY;
        CreatureOwnershipState years = roundTrip(loaded);
        assertEquals(0, CreatureData.applyRetention(years, 30));
        assertEquals(LoadVerdict.RELEASE, years.loadVerdict(player, creature),
                "the pet is released, never re-indexed under the erased UUID");
    }

    @Test
    void aLongRetentionPeriodStretchesTheOwnerHash() {
        CreatureOwnershipState state = store();
        UUID player = UUID.randomUUID();
        state.forgetPlayer(player);
        this.now[0] += 399 * DAY;
        assertEquals(0, CreatureData.applyRetention(state, 400));
        assertTrue(state.hasErasureMarker(player));
        this.now[0] += 2 * DAY;
        assertEquals(1, CreatureData.applyRetention(state, 400));
        assertFalse(state.hasErasureMarker(player));
    }

    @Test
    void freedCreatureMarkerIsDeletedAtOnce() {
        CreatureOwnershipState state = store();
        UUID player = UUID.randomUUID();
        UUID creature = UUID.randomUUID();
        state.add(player, pet(creature));
        state.forgetPlayer(player);
        state.creatureFreed(creature);
        assertEquals(1, state.erasureMarkerCount(), "only the owner marker remains");
    }

    @Test
    void markersDroppedAtTheCapAreCountedNotSilent() {
        Map<Integer, Long> markers = new LinkedHashMap<>();
        int dropped = 0;
        for (int i = 0; i < CreatureOwnershipState.MAX_ERASURE_MARKERS; i++) {
            dropped += CreatureOwnershipState.addBounded(markers, i, 1L);
        }
        assertEquals(0, dropped);
        assertEquals(2, CreatureOwnershipState.addBounded(markers, -1, 1L)
                + CreatureOwnershipState.addBounded(markers, -2, 1L));
        assertEquals(CreatureOwnershipState.MAX_ERASURE_MARKERS, markers.size());
        assertFalse(markers.containsKey(0), "the oldest went first");

        CreatureOwnershipState state = store();
        state.forgetPlayer(UUID.randomUUID());
        assertEquals(0, state.markersDroppedAtCap(), "an ordinary erasure drops nothing");
    }

    @Test
    void anUnstampedMarkerIsStampedOnceAndSaved() {
        store();
        CompoundTag root = new CompoundTag();
        ListTag legacy = new ListTag();
        legacy.add(StringTag.valueOf(UUID.randomUUID().toString()));
        root.put("erased_creatures", legacy);
        CreatureOwnershipState loaded = CreatureOwnershipState.storeCodec().parse(NbtOps.INSTANCE, root)
                .result().orElseThrow();
        assertEquals(1, loaded.erasureMarkerCount());
        assertTrue(loaded.isDirty(), "the new stamp must be written, or it is re-stamped every session");

        CreatureOwnershipState again = roundTrip(loaded);
        assertFalse(again.isDirty(), "a stamped marker loads clean");
    }

    @Test
    void erasureClearsTheLoveCauseOnlyForThatPlayer() {
        UUID player = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        saved.putIntArray(CreatureData.LOVE_CAUSE, UUIDUtil.uuidToIntArray(UUID.randomUUID()));
        assertFalse(CreatureData.withoutLoveCause(saved, player), "someone else's breeding cause stays");
        assertTrue(saved.contains(CreatureData.LOVE_CAUSE));

        saved.putIntArray(CreatureData.LOVE_CAUSE, UUIDUtil.uuidToIntArray(player));
        saved.putFloat("Health", 8.0F);
        assertTrue(CreatureData.withoutLoveCause(saved, player));
        assertFalse(saved.contains(CreatureData.LOVE_CAUSE));
        assertTrue(saved.contains("Health"), "nothing else is touched");
        assertFalse(CreatureData.withoutLoveCause(new CompoundTag(), player));
    }
}
