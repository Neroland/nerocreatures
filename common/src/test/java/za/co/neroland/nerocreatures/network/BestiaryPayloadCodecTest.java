package za.co.neroland.nerocreatures.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class BestiaryPayloadCodecTest {

    private static BestiaryCountsPayload.Entry entry(String species) {
        return new BestiaryCountsPayload.Entry(species, 2, 0, 5, 5, 3, 9_999_999, 9999, 0, 0);
    }

    @Test
    void requestRoundTrips() {
        BestiaryRequestPayload in = new BestiaryRequestPayload(BestiaryRequestPayload.SCHEMA);
        assertEquals(in, PayloadCodecs.roundTrip(BestiaryRequestPayload.STREAM_CODEC, in));
        assertEquals("nerocreatures:bestiary_request", BestiaryRequestPayload.TYPE.id().toString());
    }

    @Test
    void eventRoundTripsEveryKind() {
        for (BestiaryEventPayload.Kind kind : BestiaryEventPayload.Kind.values()) {
            BestiaryEventPayload in = BestiaryEventPayload.of(kind, kind.ordinal() < 3 ? "lumen_moth" : "");
            assertEquals(in, PayloadCodecs.roundTrip(BestiaryEventPayload.STREAM_CODEC, in));
        }
        assertEquals("nerocreatures:bestiary_event", BestiaryEventPayload.TYPE.id().toString());
    }

    @Test
    void countsRoundTripAtTheEntryCap() {
        List<BestiaryCountsPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < BestiaryCountsPayload.MAX_ENTRIES; i++) {
            entries.add(entry("species_" + i));
        }
        BestiaryCountsPayload in = new BestiaryCountsPayload(BestiaryCountsPayload.SCHEMA, entries);
        assertEquals(in, PayloadCodecs.roundTrip(BestiaryCountsPayload.STREAM_CODEC, in));
        BestiaryCountsPayload empty = new BestiaryCountsPayload(BestiaryCountsPayload.SCHEMA, List.of());
        assertEquals(empty, PayloadCodecs.roundTrip(BestiaryCountsPayload.STREAM_CODEC, empty));
    }

    @Test
    void decoderRejects65Entries() {
        ByteBuf buf = Unpooled.buffer();
        try {
            BestiaryWire.writeVarInt(buf, 1);
            BestiaryWire.writeVarInt(buf, 65);
            assertThrows(RuntimeException.class, () -> BestiaryCountsPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void decoderRejectsA33ByteSpeciesId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            BestiaryWire.writeVarInt(buf, 1);
            buf.writeByte(0);
            BestiaryWire.writeVarInt(buf, 33);
            buf.writeBytes(new byte[33]);
            assertThrows(RuntimeException.class, () -> BestiaryEventPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
        String longId = "x".repeat(33);
        ByteBuf out = Unpooled.buffer();
        try {
            assertThrows(RuntimeException.class,
                    () -> BestiaryEventPayload.STREAM_CODEC.encode(out, BestiaryEventPayload.of(BestiaryEventPayload.Kind.SCANNED, longId)));
        } finally {
            out.release();
        }
    }

    @Test
    void worstCaseCountsPayloadStaysUnder2Kb() {
        List<BestiaryCountsPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 38; i++) {
            entries.add(entry("terraforming_drone"));
        }
        ByteBuf buf = Unpooled.buffer();
        try {
            BestiaryCountsPayload.STREAM_CODEC.encode(buf, new BestiaryCountsPayload(1, entries));
            assertTrue(buf.readableBytes() < 2048, "size " + buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void initDeclaresTheBestiaryPayloadsOnce() {
        CreatureNetwork.init();
        CreatureNetwork.init();
        assertEquals(1L, CreatureNetwork.serverbound().stream().filter(s -> s.type().equals(BestiaryRequestPayload.TYPE)).count());
        assertEquals(1L, CreatureNetwork.clientbound().stream().filter(c -> c.type().equals(BestiaryCountsPayload.TYPE)).count());
        assertEquals(1L, CreatureNetwork.clientbound().stream().filter(c -> c.type().equals(BestiaryEventPayload.TYPE)).count());
    }
}
