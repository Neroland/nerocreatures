package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.network.CreatureNetwork;

/** Fabric client entry point for NeroCreatures. */
public final class NeroCreaturesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Fabric client bootstrap");
        // Clientbound receivers (client-only API) — registered here, off the dedicated server.
        FabricCreatureNetwork.registerClient();

        // Drop any synced mirror caches on leaving a world/server, so one session's state can never
        // be shown in the next (or on a server that does not run NeroCreatures).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CreatureNetwork.clearClientCaches());
    }
}
