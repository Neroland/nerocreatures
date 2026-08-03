package za.co.neroland.nerocreatures.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.platform.NetworkPlatform;

/**
 * NeoForge side of the networking seam: registers every {@link CreatureNetwork} payload during
 * {@code RegisterPayloadHandlersEvent} and implements the send methods. Registered as the
 * {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>The registrar is {@code optional()}, so a vanilla (or NeroCreatures-less) client can still
 * connect — it simply never receives a NeroCreatures payload.
 *
 * <p>Handlers run through {@code context.enqueueWork}, i.e. on the client thread, which is what
 * makes plain-data client mirror caches safe without any locking.
 */
public final class NeoForgeCreatureNetwork implements NetworkPlatform {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeCreatureNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (CreatureNetwork.Clientbound<?> cb : CreatureNetwork.clientbound()) {
            registerClientbound(registrar, cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, CreatureNetwork.Clientbound<T> cb) {
        registrar.playToClient(cb.type(), cb.codec(),
                (payload, context) -> context.enqueueWork(() -> cb.handler().accept(payload)));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
