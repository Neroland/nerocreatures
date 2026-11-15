package za.co.neroland.nerocreatures.network;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

/**
 * The small wire helpers the Bestiary payloads share: var-ints and a byte-bounded species path.
 * Written here rather than through {@code ByteBufCodecs} so the 32-byte limit is enforced in
 * <b>bytes</b> on both sides, exactly as decision 010 section 9 states.
 */
final class BestiaryWire {

    private BestiaryWire() {
    }

    static void writeVarInt(ByteBuf buf, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            buf.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        buf.writeByte(v);
    }

    static int readVarInt(ByteBuf buf) {
        int value = 0;
        int shift = 0;
        while (true) {
            if (shift >= 35) {
                throw new DecoderException("VarInt too long");
            }
            byte b = buf.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
    }

    static void writeSpecies(ByteBuf buf, String species) {
        byte[] bytes = species.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > BestiaryEventPayload.MAX_SPECIES_BYTES) {
            throw new EncoderException("Species id too long");
        }
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    static String readSpecies(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length < 0 || length > BestiaryEventPayload.MAX_SPECIES_BYTES || length > buf.readableBytes()) {
            throw new DecoderException("Species id too long");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
