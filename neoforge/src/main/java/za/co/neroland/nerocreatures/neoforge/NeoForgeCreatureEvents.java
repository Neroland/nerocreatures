package za.co.neroland.nerocreatures.neoforge;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.nerocreatures.command.CreatureCommands;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;

/**
 * NeoForge side of the server-side hooks NeroCreatures needs.
 *
 * <p>Two hooks. The spawn engine's placement sweep is self-throttled to once every two seconds
 * inside {@link CreatureSpawns#serverTick}, so subscribing to the raw server tick costs one integer
 * compare per tick; the engine also notices a change of server instance itself, so no "server
 * stopped" hook is needed on any loader. The {@code /nerocreatures} tree is loader-agnostic and is
 * built in common.
 */
public final class NeoForgeCreatureEvents {

    private NeoForgeCreatureEvents() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                CreatureSpawns.serverTick(event.getServer()));

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                CreatureCommands.register(event.getDispatcher()));
    }
}
