package za.co.neroland.nerocreatures.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Server &rarr; client: the asking player's own Bestiary tiers and counts (section
 * 9.2). One entry per species the player has at least Scanned, in roster order; species not yet
 * scanned are omitted, so the payload says nothing about them. No UUID, name, coordinate, entity id
 * or free text.
 */
public record BestiaryCountsPayload(int schema, List<Entry> entries) implements CustomPacketPayload {

    public static final int SCHEMA = 1;
    /** Most entries the decoder accepts. */
    public static final int MAX_ENTRIES = 64;

    /** Bits of {@link Entry#tracked()}. */
    public static final int TRACK_KILLS = 1;
    public static final int TRACK_OWNED = 2;
    public static final int TRACK_BRED = 4;
    public static final int TRACK_HARVESTED = 8;

    /**
     * One species. {@code tier} 1 Scanned, 2 Studied; {@code studyKind} 0-3 (unknown values draw no
     * goal line); a clear {@code tracked} bit means that number does not exist, not that it is 0.
     */
    public record Entry(String species, int tier, int studyKind, int studyProgress, int studyGoal, int tracked,
            int kills, int owned, int bred, int harvested) {
    }

    public static final CustomPacketPayload.Type<BestiaryCountsPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bestiary_counts"));

    public static final StreamCodec<ByteBuf, BestiaryCountsPayload> STREAM_CODEC = StreamCodec.of(
            BestiaryCountsPayload::write, BestiaryCountsPayload::read);

    public BestiaryCountsPayload {
        entries = List.copyOf(entries);
    }

    private static void write(ByteBuf buf, BestiaryCountsPayload payload) {
        if (payload.entries().size() > MAX_ENTRIES) {
            throw new EncoderException("Too many bestiary entries");
        }
        BestiaryWire.writeVarInt(buf, payload.schema());
        BestiaryWire.writeVarInt(buf, payload.entries().size());
        for (Entry e : payload.entries()) {
            BestiaryWire.writeSpecies(buf, e.species());
            buf.writeByte(e.tier());
            buf.writeByte(e.studyKind());
            BestiaryWire.writeVarInt(buf, e.studyProgress());
            BestiaryWire.writeVarInt(buf, e.studyGoal());
            buf.writeByte(e.tracked());
            BestiaryWire.writeVarInt(buf, e.kills());
            BestiaryWire.writeVarInt(buf, e.owned());
            BestiaryWire.writeVarInt(buf, e.bred());
            BestiaryWire.writeVarInt(buf, e.harvested());
        }
    }

    private static BestiaryCountsPayload read(ByteBuf buf) {
        int schema = BestiaryWire.readVarInt(buf);
        int count = BestiaryWire.readVarInt(buf);
        if (count < 0 || count > MAX_ENTRIES) {
            throw new DecoderException("Too many bestiary entries");
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String species = BestiaryWire.readSpecies(buf);
            int tier = buf.readByte();
            int studyKind = buf.readByte();
            int progress = BestiaryWire.readVarInt(buf);
            int goal = BestiaryWire.readVarInt(buf);
            int tracked = buf.readByte();
            int kills = BestiaryWire.readVarInt(buf);
            int owned = BestiaryWire.readVarInt(buf);
            int bred = BestiaryWire.readVarInt(buf);
            int harvested = BestiaryWire.readVarInt(buf);
            entries.add(new Entry(species, tier, studyKind, progress, goal, tracked, kills, owned, bred, harvested));
        }
        return new BestiaryCountsPayload(schema, entries);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
