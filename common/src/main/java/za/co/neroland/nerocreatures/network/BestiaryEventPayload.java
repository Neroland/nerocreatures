package za.co.neroland.nerocreatures.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Server &rarr; client, to the player it is about only: a scan result or
 * a newly opened tier. Carries a species registry path and nothing about any player.
 */
public record BestiaryEventPayload(int schema, Kind kind, String species) implements CustomPacketPayload {

    public static final int SCHEMA = 1;

    /** Longest species path on the wire, in UTF-8 bytes. */
    public static final int MAX_SPECIES_BYTES = 32;

    /** Event kinds; the ordinal is the wire byte. */
    public enum Kind {
        SCANNED, STUDIED, NOTHING_NEW, REFUSED_GALLERY, REFUSED_NOT_LISTED, LOST;

        static Kind byWire(int value) {
            Kind[] all = values();
            if (value < 0 || value >= all.length) {
                throw new IllegalArgumentException("Unknown bestiary event kind " + value);
            }
            return all[value];
        }
    }

    public static final CustomPacketPayload.Type<BestiaryEventPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bestiary_event"));

    public static final StreamCodec<ByteBuf, BestiaryEventPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BestiaryWire.writeVarInt(buf, payload.schema());
                buf.writeByte(payload.kind().ordinal());
                BestiaryWire.writeSpecies(buf, payload.species());
            },
            buf -> {
                int schema = BestiaryWire.readVarInt(buf);
                Kind kind = Kind.byWire(buf.readByte());
                return new BestiaryEventPayload(schema, kind, BestiaryWire.readSpecies(buf));
            });

    public static BestiaryEventPayload of(Kind kind, String species) {
        return new BestiaryEventPayload(SCHEMA, kind, species == null ? "" : species);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
