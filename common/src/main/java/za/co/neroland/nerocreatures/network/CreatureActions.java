package za.co.neroland.nerocreatures.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * The server-side action table behind {@link CreatureActionPayload}. A feature that needs the
 * client to ask for something on an owned creature (open panniers, unsaddle, a pet command button
 * in a screen) registers an action id here once, during common init:
 *
 * <pre>{@code
 * CreatureActions.register(CreatureActions.MOUNT_OPEN_PANNIERS, MountCreature.class, 6.0D,
 *         (player, mount) -> mount.openPanniers(player));
 * }</pre>
 *
 * <p>The dispatcher runs {@link ServerPayloadGuard#ownedEntity} <em>before</em> the handler:
 * the handler only ever sees a live entity of the requested class, owned by the sending player, in
 * the sender's level and within reach, and the sender is rate-limited. A handler therefore cannot
 * forget the ownership check. Anything the action reads beyond the entity (an item in hand, a
 * cooldown) it still validates itself.
 *
 * <p>Unknown ids, failed guards and handler exceptions are dropped silently for the client; a
 * handler exception is reported to telemetry with the action id only — never the player or the
 * entity's position (POPIA/GDPR).
 *
 * <h2>Reserved ids</h2>
 * Ids are part of the wire format; never renumber one. Blocks of 16 per area:
 * 0–15 pets, 16–31 mounts, 32–47 habitat and gear, 48–63 bestiary and scanner.
 */
public final class CreatureActions {

    // --- reserved ids (constants live here so two features can never collide) -------------
    public static final int PET_SIT = 0;
    public static final int PET_FOLLOW = 1;
    public static final int PET_GUARD = 2;
    public static final int MOUNT_OPEN_PANNIERS = 16;
    public static final int MOUNT_UNSADDLE = 17;

    /** The server-side half of one action. */
    @FunctionalInterface
    public interface Handler<E extends Entity> {
        void handle(ServerPlayer player, E owned);
    }

    private record Entry<E extends Entity>(Class<E> type, double reach, Handler<E> handler) {
    }

    private static final Map<Integer, Entry<?>> ACTIONS = new ConcurrentHashMap<>();

    private CreatureActions() {
    }

    /**
     * Registers an action. Call during common init only.
     *
     * @param id      a reserved id (see the class Javadoc), unique
     * @param type    the entity class the action applies to; anything else is refused
     * @param reach   the furthest (in blocks) the sender may be from the entity
     * @param handler runs on the server thread after the guard passed
     * @throws IllegalStateException when the id is already taken
     */
    public static <E extends Entity> void register(int id, Class<E> type, double reach, Handler<E> handler) {
        if (id < 0) {
            throw new IllegalArgumentException("Negative creature action id " + id);
        }
        Entry<?> previous = ACTIONS.putIfAbsent(id, new Entry<>(type, reach, handler));
        if (previous != null) {
            throw new IllegalStateException("Creature action id " + id + " is already registered");
        }
    }

    /** Whether an id has a handler (for tests and the docs check). */
    public static boolean isRegistered(int id) {
        return ACTIONS.containsKey(id);
    }

    /** Server thread: the {@link CreatureActionPayload} handler. */
    static void handle(CreatureActionPayload payload, ServerPlayer player) {
        Entry<?> entry = ACTIONS.get(payload.action());
        if (entry == null) {
            return; // unknown action: an old or modified client; ignore
        }
        dispatch(entry, payload, player);
    }

    private static <E extends Entity> void dispatch(Entry<E> entry, CreatureActionPayload payload,
            ServerPlayer player) {
        E target = ServerPayloadGuard.ownedEntity(player, payload.entityId(), entry.reach(), entry.type());
        if (target == null) {
            return;
        }
        try {
            entry.handler().handle(player, target);
        } catch (RuntimeException e) {
            NeroCreaturesTelemetry.captureHandledException(e, "network", "creature_action " + payload.action());
        }
    }
}
