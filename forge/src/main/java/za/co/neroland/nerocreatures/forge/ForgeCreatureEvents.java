package za.co.neroland.nerocreatures.forge;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

import za.co.neroland.nerocreatures.command.CreatureCommands;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;

/**
 * Forge side of the server-side hooks NeroCreatures needs. Forge 26.x has no single global event
 * bus — each event class owns a static {@code BUS} — so listeners are attached per event type.
 *
 * <p>Two hooks. The spawn engine's placement sweep is self-throttled to once every two seconds
 * inside {@link CreatureSpawns#serverTick}, so subscribing to the raw server tick costs one integer
 * compare per tick; the engine also notices a change of server instance itself, so no "server
 * stopped" hook is needed on any loader. The {@code /nerocreatures} tree is loader-agnostic and is
 * built in common.
 */
public final class ForgeCreatureEvents {

    private ForgeCreatureEvents() {
    }

    /** Called once from the Forge entry point. */
    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event ->
                CreatureSpawns.serverTick(event.server()));

        RegisterCommandsEvent.BUS.addListener(event -> CreatureCommands.register(event.getDispatcher()));
    }
}
