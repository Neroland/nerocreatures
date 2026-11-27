package za.co.neroland.nerocreatures.network;

import io.netty.buffer.ByteBuf;

//? if >=1.20.5 {
import net.minecraft.network.codec.ByteBufCodecs;
//?}
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Client &rarr; server: "send me my own Bestiary counts". Carries only a
 * schema number. The server answers for the sender as the network layer identified them, at most
 * once per 20 ticks; anything but schema 1 is ignored.
 */
public record BestiaryRequestPayload(int schema) implements CustomPacketPayload {

    public static final int SCHEMA = 1;

    //? if >=1.20.5 {
    public static final CustomPacketPayload.Type<BestiaryRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bestiary_request"));
    //?} else {
    /*public static final CustomPacketPayload.Type<BestiaryRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bestiary_request"), BestiaryRequestPayload.class);
    *///?}

    //? if >=1.20.5 {
    public static final StreamCodec<ByteBuf, BestiaryRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BestiaryRequestPayload::schema,
            BestiaryRequestPayload::new);
    //?} else {
    /*public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, BestiaryRequestPayload> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.schema());
            }, buffer -> new BestiaryRequestPayload(buffer.readVarInt()));
    *///?}

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
