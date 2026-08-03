package za.co.neroland.nerocreatures.neoforge;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.nerocreatures.spawn.CreatureSpawns;

/**
 * NeoForge side of the server-side hooks NeroCreatures needs.
 *
 * <p>Only the spawn engine needs a loader event so far: the placement sweep is self-throttled to
 * once every two seconds inside {@link CreatureSpawns#serverTick}, so subscribing to the raw server
 * tick costs one integer compare per tick. The engine also notices a change of server instance
 * itself, so no "server stopped" hook is needed on any loader.
 */
public final class NeoForgeCreatureEvents {

    private NeoForgeCreatureEvents() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                CreatureSpawns.serverTick(event.getServer()));
    }
}
