package za.co.neroland.nerocreatures.forge;

import net.minecraftforge.event.TickEvent;

import za.co.neroland.nerocreatures.spawn.CreatureSpawns;

/**
 * Forge side of the server-side hooks NeroCreatures needs. Forge 26.x has no single global event
 * bus — each event class owns a static {@code BUS} — so listeners are attached per event type.
 *
 * <p>Only the spawn engine needs a loader event so far: the placement sweep is self-throttled to
 * once every two seconds inside {@link CreatureSpawns#serverTick}, so subscribing to the raw server
 * tick costs one integer compare per tick. The engine also notices a change of server instance
 * itself, so no "server stopped" hook is needed on any loader.
 */
public final class ForgeCreatureEvents {

    private ForgeCreatureEvents() {
    }

    /** Called once from the Forge entry point. */
    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event ->
                CreatureSpawns.serverTick(event.server()));
    }
}
