package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.platform.NetworkPlatform;

/**
 * Fabric side of the networking seam. {@link #registerCommon()} (mod init, both sides) registers
 * the payload types; {@link #registerClient()} (client init) registers the receivers, keeping
 * {@code ClientPlayNetworking} off the dedicated server. Registered as the
 * {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>Receivers hop to the client thread via {@code context.client().execute}, which is what makes
 * plain-data client mirror caches safe without any locking.
 */
public final class FabricCreatureNetwork implements NetworkPlatform {

    /** Mod-init (both sides): payload types only — NeroCreatures has no serverbound payload yet. */
    public static void registerCommon() {
        for (CreatureNetwork.Clientbound<?> cb : CreatureNetwork.clientbound()) {
            registerClientboundType(cb);
        }
    }

    /** Client-init: clientbound receivers (client-only API). */
    public static void registerClient() {
        for (CreatureNetwork.Clientbound<?> cb : CreatureNetwork.clientbound()) {
            registerClientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            CreatureNetwork.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            CreatureNetwork.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
