package za.co.neroland.nerocreatures.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.nerocreatures.command.CreatureCommands;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;

/**
 * Fabric side of the server-side hooks NeroCreatures needs.
 *
 * <p>Two hooks. The spawn engine's placement sweep is self-throttled to once every two seconds
 * inside {@link CreatureSpawns#serverTick}, so subscribing to the raw server tick costs one integer
 * compare per tick; the engine also notices a change of server instance itself, so no "server
 * stopped" hook is needed on any loader. The {@code /nerocreatures} tree is loader-agnostic and is
 * built in common — neither the build context nor the dedicated/integrated selection changes it.
 */
public final class FabricCreatureEvents {

    private FabricCreatureEvents() {
    }

    /** Called once from the Fabric entry point. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CreatureSpawns::serverTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CreatureCommands.register(dispatcher));
    }
}
