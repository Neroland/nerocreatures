package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.nerocreatures.spawn.CreatureSpawns;

/**
 * Fabric side of the server-side hooks NeroCreatures needs.
 *
 * <p>Only the spawn engine needs a loader event so far: the placement sweep is self-throttled to
 * once every two seconds inside {@link CreatureSpawns#serverTick}, so subscribing to the raw server
 * tick costs one integer compare per tick. The engine also notices a change of server instance
 * itself, so no "server stopped" hook is needed on any loader.
 */
public final class FabricCreatureEvents {

    private FabricCreatureEvents() {
    }

    /** Called once from the Fabric entry point. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CreatureSpawns::serverTick);
    }
}
