package za.co.neroland.nerocreatures.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.network.codec.StreamCodec;

/**
 * The codec test pattern for every NeroCreatures payload: encode into a plain Netty buffer, decode,
 * and require the buffer to be fully consumed. Works for any {@code StreamCodec<ByteBuf, T>} without
 * a game bootstrap.
 */
final class PayloadCodecs {

    private PayloadCodecs() {
    }

    //? if >=1.20.5 {
    static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
    //?} else {
    /*static <T> T roundTrip(StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec, T value) {
        var buf = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
    *///?}
        try {
            codec.encode(buf, value);
            T decoded = codec.decode(buf);
            if (buf.isReadable()) {
                throw new AssertionError(buf.readableBytes() + " unread bytes after decoding " + value);
            }
            return decoded;
        } finally {
            buf.release();
        }
    }
}
