package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/**
 * Decision 004: a 0.4.x ownership save (no {@code schema}, no {@code kind} on pet rows) loads
 * unchanged; mount and egg rows round-trip; pet and drone rows keep their 0.4.x shape on disk; caps
 * are per kind; erasure and export cover the new kinds.
 */
class OwnershipMigrationTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID PET = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID DRONE = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID FUTURE = UUID.fromString("00000000-0000-4000-8000-0000000000a3");

    private static final Identifier WISP = Identifier.fromNamespaceAndPath("nerocreatures", "glacite_wisp");
    private static final Identifier LOPER = Identifier.fromNamespaceAndPath("nerocreatures", "dune_loper");
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    /** Exactly what 0.4.x wrote: no schema, pet rows without a kind. */
    private static CompoundTag legacyFixture() {
        CompoundTag pet = new CompoundTag();
        pet.putString("entity", PET.toString());
        pet.putString("type", WISP.toString());
        pet.putString("dimension", OVERWORLD.toString());
        CompoundTag drone = new CompoundTag();
        drone.putString("entity", DRONE.toString());
        drone.putString("kind", "drone");
        drone.putString("type", "nerocreatures:terraforming_drone");
        drone.putString("dimension", OVERWORLD.toString());
        CompoundTag future = new CompoundTag();
        future.putString("entity", FUTURE.toString());
        future.putString("kind", "something_newer");
        future.putString("type", WISP.toString());
        future.putString("dimension", OVERWORLD.toString());
        ListTag owned = new ListTag();
        owned.add(pet);
        owned.add(drone);
        owned.add(future);
        CompoundTag row = new CompoundTag();
        row.putString("player", PLAYER.toString());
        row.putLong("updated_at", 1_700_000_000_000L);
        row.put("owned", owned);
        ListTag players = new ListTag();
        players.add(row);
        CompoundTag root = new CompoundTag();
        root.put("players", players);
        return root;
    }

    private static CreatureOwnershipState decode(Tag tag) {
        return CreatureOwnershipState.storeCodec().parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }

    private static Tag encode(CreatureOwnershipState state) {
        return CreatureOwnershipState.storeCodec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
    }

    @Test
    void legacySaveLoadsUnchanged() {
        CreatureOwnershipState state = decode(legacyFixture());
        assertEquals(2, state.count(PLAYER, OwnedCreature.Kind.PET), "pet + unknown kind read as pet");
        assertEquals(1, state.count(PLAYER, OwnedCreature.Kind.DRONE));
        assertEquals(0, state.count(PLAYER, OwnedCreature.Kind.MOUNT));
        assertTrue(state.contains(PLAYER, PET));
        assertTrue(state.knowsPlayer(PLAYER));
        assertFalse(state.knowsPlayer(OTHER));
        assertEquals(0L, state.owned(PLAYER).get(0).hatchAt());
    }

    @Test
    void writesSchemaTwoAndKeepsLegacyRowShape() {
        Tag out = encode(decode(legacyFixture()));
        int schema = Codec.INT.fieldOf("schema").codec().parse(NbtOps.INSTANCE, out).result().orElseThrow();
        assertEquals(CreatureOwnershipState.SCHEMA, schema);

        CompoundTag petRow = (CompoundTag) OwnedCreature.CODEC.encodeStart(NbtOps.INSTANCE,
                new OwnedCreature(PET, OwnedCreature.Kind.PET, WISP, OVERWORLD)).getOrThrow();
        assertEquals(3, petRow.size(), "entity, type, dimension: no kind, no hatch_at");
        assertFalse(petRow.contains("hatch_at"));

        // And the re-encoded store decodes to the same rows.
        CreatureOwnershipState again = decode(out);
        assertEquals(decode(legacyFixture()).owned(PLAYER), again.owned(PLAYER));
    }

    @Test
    void mountAndEggRowsRoundTrip() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        UUID mount = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
        state.add(PLAYER, new OwnedCreature(mount, OwnedCreature.Kind.MOUNT, LOPER, OVERWORLD));
        OwnedCreature egg = OwnedCreature.egg(LOPER, OVERWORLD, 123_456L);
        state.add(PLAYER, egg);

        CreatureOwnershipState back = decode(encode(state));
        assertEquals(state.owned(PLAYER), back.owned(PLAYER));
        assertEquals(1, back.count(PLAYER, OwnedCreature.Kind.MOUNT));
        assertEquals(1, back.count(PLAYER, OwnedCreature.Kind.EGG));
        assertEquals(123_456L, back.owned(PLAYER).get(1).hatchAt());
    }

    @Test
    void capsArePerKindAndMountsDoNotUsePetSlots() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(PLAYER, new OwnedCreature(PET, OwnedCreature.Kind.PET, WISP, OVERWORLD));
        int mountCap = OwnershipCaps.DEFAULT_MAX_MOUNTS;
        for (int i = 0; i < mountCap; i++) {
            assertTrue(state.tryAdd(PLAYER, new OwnedCreature(UUID.randomUUID(), OwnedCreature.Kind.MOUNT,
                    LOPER, OVERWORLD), mountCap));
        }
        assertFalse(state.tryAdd(PLAYER, new OwnedCreature(UUID.randomUUID(), OwnedCreature.Kind.MOUNT,
                LOPER, OVERWORLD), mountCap), "third mount refused at the default cap of 2");
        assertEquals(1, state.count(PLAYER, OwnedCreature.Kind.PET));
        assertTrue(state.tryAdd(PLAYER, new OwnedCreature(UUID.randomUUID(), OwnedCreature.Kind.PET,
                WISP, OVERWORLD), 3), "pet cap untouched by mounts");
        assertFalse(state.tryAdd(OTHER, OwnedCreature.egg(LOPER, OVERWORLD, 1L), 0), "cap 0 refuses");
        // Re-registering an indexed entity never counts twice.
        OwnedCreature first = state.owned(PLAYER).get(1);
        assertTrue(state.tryAdd(PLAYER, first, mountCap));
        assertEquals(mountCap, state.count(PLAYER, OwnedCreature.Kind.MOUNT));
    }

    @Test
    void erasureAndExportCoverMountsAndEggs() {
        CreatureOwnershipState state = new CreatureOwnershipState();
        state.add(PLAYER, new OwnedCreature(PET, OwnedCreature.Kind.MOUNT, LOPER, OVERWORLD));
        state.add(PLAYER, OwnedCreature.egg(LOPER, OVERWORLD, 99L));
        state.add(OTHER, new OwnedCreature(DRONE, OwnedCreature.Kind.PET, WISP, OVERWORLD));

        JsonObject export = state.export(PLAYER);
        JsonArray rows = export.getAsJsonArray("owned");
        assertEquals(2, rows.size());
        assertEquals("mount", rows.get(0).getAsJsonObject().get("kind").getAsString());
        assertFalse(rows.get(0).getAsJsonObject().has("hatch_at"));
        assertEquals("egg", rows.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals(99L, rows.get(1).getAsJsonObject().get("hatch_at").getAsLong());
        assertFalse(export.toString().contains(OTHER.toString()), "only the requester's rows");

        List<OwnedCreature> removed = state.forgetPlayer(PLAYER);
        assertEquals(2, removed.size());
        assertFalse(state.knowsPlayer(PLAYER));
        assertEquals(0, state.export(PLAYER).getAsJsonArray("owned").size());
        assertTrue(state.knowsPlayer(OTHER));
    }
}
