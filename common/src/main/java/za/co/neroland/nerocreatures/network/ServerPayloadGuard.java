package za.co.neroland.nerocreatures.network;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocreatures.world.CreatureOwners;

/**
 * The one server-side validation every client &rarr; server payload must pass before it touches a
 * creature. Call it first thing in a serverbound handler; {@link CreatureActions} already does.
 *
 * <p>{@link #ownedEntity} returns the target only when, in this order:
 * <ol>
 *   <li>the sender is usable: alive, not removed, not a spectator;</li>
 *   <li>the sender is within its rate limit ({@value #MAX_PER_WINDOW} payloads per
 *       {@value #WINDOW_TICKS} ticks);</li>
 *   <li>an entity with that network id exists <b>in the sender's own level</b>, is alive and is an
 *       instance of the expected class;</li>
 *   <li>its owner (vanilla {@code OwnableEntity} owner, see {@link CreatureOwners}) is the sender
 *       — compared by UUID on the server, never taken from the payload;</li>
 *   <li>it is within {@code reach} blocks of the sender.</li>
 * </ol>
 * Any failure returns {@code null} and logs nothing (a refused payload is normal from a lagging
 * client, and a log line would name a player).
 *
 * <p>The rate-limit table is in memory only, weakly keyed by the player object, so it is never
 * persisted and disappears with the player's session (POPIA/GDPR).
 *
 * <p>Server thread only.
 */
public final class ServerPayloadGuard {

    /** Default reach for an action on an owned creature, in blocks. */
    public static final double DEFAULT_REACH = 8.0D;

    /** Rate-limit window in server ticks. */
    public static final int WINDOW_TICKS = 20;

    /** Serverbound payloads one player may send per window. */
    public static final int MAX_PER_WINDOW = 20;

    /** Why a payload was refused (tests and debugging only; never logged with a player). */
    public enum Verdict {
        OK, SENDER_UNUSABLE, RATE_LIMITED, NO_ENTITY, NOT_OWNER, OUT_OF_REACH
    }

    private static final Map<ServerPlayer, RateWindow> RATE = new WeakHashMap<>();

    private ServerPayloadGuard() {
    }

    /**
     * The validated target, or {@code null}.
     *
     * @param player   the sender as identified by the network layer
     * @param entityId the network id the payload names
     * @param reach    the furthest the sender may be from the target, in blocks
     * @param type     the class the target must be
     */
    @Nullable
    public static <E extends Entity> E ownedEntity(ServerPlayer player, int entityId, double reach,
            Class<E> type) {
        boolean usable = senderUsable(player);
        boolean rateOk = usable && acquire(player);
        Entity found = rateOk ? player.level().getEntity(entityId) : null;
        boolean present = found != null && found.isAlive() && type.isInstance(found);
        UUID owner = present ? CreatureOwners.ownerUuid(found) : null;
        boolean owned = owner != null && owner.equals(player.getUUID());
        double distSqr = owned ? player.distanceToSqr(found) : Double.MAX_VALUE;
        Verdict verdict = decide(usable, rateOk, present, owned, distSqr, reach);
        return verdict == Verdict.OK ? type.cast(found) : null;
    }

    /** {@link #ownedEntity(ServerPlayer, int, double, Class)} for any entity class. */
    @Nullable
    public static Entity ownedEntity(ServerPlayer player, int entityId, double reach) {
        return ownedEntity(player, entityId, reach, Entity.class);
    }

    /**
     * For a serverbound payload that does not target a creature (a screen button, a bestiary
     * request): the sender checks and the rate limit only.
     */
    public static boolean senderMayAct(ServerPlayer player) {
        return senderUsable(player) && acquire(player);
    }

    /** The pure decision, in check order. Package-private for tests. */
    static Verdict decide(boolean senderUsable, boolean rateOk, boolean entityPresent, boolean ownedBySender,
            double distanceSqr, double reach) {
        if (!senderUsable) {
            return Verdict.SENDER_UNUSABLE;
        }
        if (!rateOk) {
            return Verdict.RATE_LIMITED;
        }
        if (!entityPresent) {
            return Verdict.NO_ENTITY;
        }
        if (!ownedBySender) {
            return Verdict.NOT_OWNER;
        }
        if (!(reach > 0.0D) || distanceSqr > reach * reach) {
            return Verdict.OUT_OF_REACH;
        }
        return Verdict.OK;
    }

    private static boolean senderUsable(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }

    private static boolean acquire(ServerPlayer player) {
        RateWindow window = RATE.computeIfAbsent(player, p -> new RateWindow(WINDOW_TICKS, MAX_PER_WINDOW));
        return window.tryAcquire(player.level().getGameTime());
    }

    /** A fixed-window counter. Package-private for tests. */
    static final class RateWindow {
        private final int windowTicks;
        private final int max;
        private long windowStart = Long.MIN_VALUE;
        private int used;

        RateWindow(int windowTicks, int max) {
            this.windowTicks = windowTicks;
            this.max = max;
        }

        boolean tryAcquire(long now) {
            if (windowStart == Long.MIN_VALUE || now - windowStart >= windowTicks || now < windowStart) {
                windowStart = now;
                used = 0;
            }
            if (used >= max) {
                return false;
            }
            used++;
            return true;
        }
    }
}
