package za.co.neroland.nerocreatures.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.platform.NetworkPlatform;

/**
 * Forge side of the cross-loader packet seam. Registered as the {@link NetworkPlatform}
 * implementation via {@code META-INF/services}.
 *
 * <p>Forge seals a channel at {@code build()}, so every payload must be declared before then —
 * which is exactly why NeroCreatures owns this channel rather than adding to Neroland Core's
 * (Core builds its own inside its constructor, long before NeroCreatures is constructed).
 * {@code optional()} keeps a NeroCreatures-less client connectable.
 */
public final class ForgeCreatureNetwork implements NetworkPlatform {

    private static Channel<CustomPacketPayload> channel;

    public static void register() {
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "main"))
                        .optional()
                        .payloadChannel()
                        .play()
                        .bidirectional();
        for (CreatureNetwork.Clientbound<?> cb : CreatureNetwork.clientbound()) {
            registerClientbound(play, cb);
        }
        channel = play.build();
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            CreatureNetwork.Clientbound<T> cb) {
        play.addMain(cb.type(), registryCodec(cb.codec()),
                (payload, context) -> cb.handler().accept(payload));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> registryCodec(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.SERVER.noArg());
        }
    }
}
