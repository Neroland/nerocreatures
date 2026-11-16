package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.progression.ProgressionState;

import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState.LoadVerdict;
import za.co.neroland.nerocreatures.link.CreatureLinkEvents;

/**
 * POPIA/GDPR: NeroCreatures' erasure run through Neroland Core's reusable {@link ErasureConformance}
 * harness (the same pattern as the other Nero mods: plain JVM, no game bootstrap, {@code null} server).
 *
 * <p>The production eraser ({@code CreatureData.erasePlayer}) fetches the SavedData stores from a live
 * server, frees the creatures it can reach and refreshes Core's backups. The eraser registered here
 * runs the same store-side body, {@link CreatureData#eraseStores}, against bare store instances with a
 * recording creature freer standing in for the levels; the backup refresh is a
 * {@code SavedDataRecovery.backupNow} call with no logic of its own. The Bestiary and milestone gates
 * live in Core's progression store and are erased by Core's own registered eraser, mirrored here as
 * Core's own conformance test does. Random UUIDs only; nothing is logged.
 */
class CreatureErasureConformanceTest {

    private static final Identifier WISP = Identifier.fromNamespaceAndPath("nerocreatures", "glacite_wisp");
    private static final Identifier LOPER = Identifier.fromNamespaceAndPath("nerocreatures", "dune_loper");
    private static final Identifier DRONE = Identifier.fromNamespaceAndPath("nerocreatures", "terraforming_drone");
    private static final Identifier BOSS = Identifier.fromNamespaceAndPath("nerocreatures", "rime_matriarch");
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier SCANNED = Identifier.fromNamespaceAndPath("nerocreatures", "bestiary/scanned/glacite_wisp");
    private static final Identifier STUDIED = Identifier.fromNamespaceAndPath("nerocreatures", "bestiary/studied/glacite_wisp");
    private static final Identifier FIRST_TAME = Identifier.fromNamespaceAndPath("nerocreatures", "first_tame");

    private final List<PlayerDataEraser> registered = new ArrayList<>();

    private void register(PlayerDataEraser eraser) {
        this.registered.add(eraser);
        PlayerDataErasure.register(eraser);
    }

    @AfterEach
    void unregisterErasers() {
        this.registered.forEach(PlayerDataErasure::unregister);
        this.registered.clear();
    }

    /** Stands in for the server's levels: frees only the creatures listed as loaded, and records it. */
    private static final class RecordingFreer implements CreatureData.CreatureFreer {
        final Set<UUID> loaded = new HashSet<>();
        final List<UUID> freed = new ArrayList<>();
        final List<UUID> swept = new ArrayList<>();
        /** Whether a link event could have been published to the player while their erasure ran. */
        final List<Boolean> publishAllowedDuringErasure = new ArrayList<>();
        int unindexedLoaded;

        @Override
        public boolean free(OwnedCreature row) {
            if (!row.parked() && this.loaded.contains(row.entity())) {
                this.freed.add(row.entity());
                return true;
            }
            return false;
        }

        @Override
        public int sweep(UUID player) {
            this.swept.add(player);
            this.publishAllowedDuringErasure.add(CreatureLinkEvents.mayPublishTo(player));
            int count = this.unindexedLoaded;
            this.unindexedLoaded = 0;
            return count;
        }
    }

    @Test
    void erasurePurgesEveryStoreThisModWritesAndOnlyThatPlayer() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        UUID mount = UUID.randomUUID();
        UUID drone = UUID.randomUUID();
        UUID parked = UUID.randomUUID();
        UUID bobsPet = UUID.randomUUID();
        UUID soloFight = UUID.randomUUID();
        UUID sharedFight = UUID.randomUUID();

        CreatureOwnershipState ownership = new CreatureOwnershipState();
        BossContributionState contributions = new BossContributionState();
        ProgressionState gates = new ProgressionState();

        // Seed every kind of row, so no probe is vacuous: pet (loaded), mount and drone (unloaded),
        // a parked pet (in companion storage) and an incubating egg.
        ownership.add(alice, new OwnedCreature(pet, OwnedCreature.Kind.PET, WISP, OVERWORLD));
        ownership.add(alice, new OwnedCreature(mount, OwnedCreature.Kind.MOUNT, LOPER, OVERWORLD));
        ownership.add(alice, new OwnedCreature(drone, OwnedCreature.Kind.DRONE, DRONE, OVERWORLD));
        ownership.add(alice, new OwnedCreature(parked, OwnedCreature.Kind.PET, WISP, OVERWORLD).withParked(true, null));
        OwnedCreature egg = OwnedCreature.egg(WISP, OVERWORLD, 24_000L);
        ownership.add(alice, egg);
        ownership.add(bob, new OwnedCreature(bobsPet, OwnedCreature.Kind.PET, WISP, OVERWORLD));
        contributions.record(soloFight, BOSS, alice, 40.0F);
        contributions.record(sharedFight, BOSS, alice, 10.0F);
        contributions.record(sharedFight, BOSS, bob, 30.0F);
        for (Identifier gate : List.of(SCANNED, STUDIED, FIRST_TAME)) {
            gates.setPlayer(alice, gate, true);
            gates.setPlayer(bob, gate, true);
        }

        RecordingFreer freer = new RecordingFreer();
        freer.loaded.add(pet);
        freer.unindexedLoaded = 1; // a loaded 0.4.x pet the index never knew about

        // NeroCreatures' eraser (the store-side body of CreatureData.erasePlayer) ...
        register((server, uuid) -> CreatureData.eraseStores(ownership, contributions, uuid, freer));
        // ... and Core's own progression eraser (ProgressionState::eraseFor), which owns the gate rows.
        register((server, uuid) -> gates.forgetPlayer(uuid));

        List<UUID> creatures = List.of(pet, mount, drone, parked);
        ErasureConformance.create()
                .probe("nerocreatures:ownership_rows", uuid -> !ownership.owned(uuid).isEmpty())
                .probe("nerocreatures:ownership_retention_stamp", ownership::knowsPlayer)
                .probe("nerocreatures:egg_rows", uuid -> ownership.count(uuid, OwnedCreature.Kind.EGG) > 0)
                .probe("nerocreatures:parked_rows", uuid -> ownership.isParked(uuid, parked))
                // A pet, mount or drone in an unloaded chunk (or companion storage) still bound to the
                // player: anything short of RELEASE on its next load is retained data.
                .probe("nerocreatures:creatures_bound_on_load", uuid -> creatures.stream()
                        .anyMatch(entity -> ownership.loadVerdict(uuid, entity) != LoadVerdict.RELEASE))
                .probe("nerocreatures:boss_contribution", contributions::knowsPlayer)
                .probe("core:progression_gates(nerocreatures bestiary + milestones)",
                        uuid -> !gates.playerGates(uuid).isEmpty())
                .verify(null, alice);

        // Loaded creature freed (its marker dropped); the sweep ran for the erased player.
        assertEquals(List.of(pet), freer.freed);
        assertTrue(freer.swept.contains(alice));
        // Nothing may be published or raised for the erased player while their creatures are freed
        // (the release paths pass notify=false, and the guard drops anything reached indirectly).
        assertFalse(freer.publishAllowedDuringErasure.isEmpty());
        assertFalse(freer.publishAllowedDuringErasure.contains(true));
        assertTrue(CreatureLinkEvents.mayPublishTo(alice), "the guard is lifted when erasure ends");
        assertTrue(CreatureLinkEvents.mayPublishTo(bob));
        // Unloaded and parked creatures release on load; the egg left no marker (it hatches unowned).
        assertEquals(LoadVerdict.RELEASE, ownership.loadVerdict(alice, mount));
        assertEquals(LoadVerdict.RELEASE, ownership.loadVerdict(UUID.randomUUID(), parked),
                "a parked pet is released by its own marker, whoever it names");
        assertFalse(ownership.ownerOfRow(egg.entity()) != null, "egg row gone");
        // The solo fight is gone; the shared fight keeps Bob's share only.
        assertEquals(0, contributions.participantCount(soloFight));
        assertEquals(1, contributions.participantCount(sharedFight));
        assertEquals(30.0F, contributions.damageOf(sharedFight, bob));
        // Bob is untouched everywhere.
        assertEquals(LoadVerdict.INDEXED, ownership.loadVerdict(bob, bobsPet));
        assertEquals(3, gates.playerGates(bob).size());
        // Nothing left on disk names Alice: markers are a salted hash and creature ids only.
        String ownershipDisk = CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, ownership)
                .getOrThrow().toString();
        assertFalse(ownershipDisk.contains(alice.toString()));
        assertTrue(ownershipDisk.contains(bob.toString()));
        assertFalse(contributions.export(alice).toString().contains(soloFight.toString()));
    }

    @Test
    void erasingAPlayerNobodyHasHeardOfIsHarmless() {
        CreatureOwnershipState ownership = new CreatureOwnershipState();
        BossContributionState contributions = new BossContributionState();
        UUID bob = UUID.randomUUID();
        UUID bobsPet = UUID.randomUUID();
        ownership.add(bob, new OwnedCreature(bobsPet, OwnedCreature.Kind.PET, WISP, OVERWORLD));
        RecordingFreer freer = new RecordingFreer();
        assertEquals(0, CreatureData.eraseStores(ownership, contributions, UUID.randomUUID(), freer));
        assertEquals(LoadVerdict.INDEXED, ownership.loadVerdict(bob, bobsPet));
        assertTrue(freer.freed.isEmpty());
    }
}
