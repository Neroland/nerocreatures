package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * The pure state of one latched tether (Gravity Leech). Unit-tested.
 *
 * <p>A tether is held while the target stays within {@code breakDistance}, the tethering creature
 * has taken less than {@code breakDamage} since it latched, and {@code maxDuration} has not passed.
 * After any break there is a re-latch cooldown. Holds an entity network id only for this session;
 * never saved, so no identity is persisted.
 */
public final class TetherState {

    /** Result of one {@link #check}. */
    public enum Status {
        /** Not latched to anything. */
        IDLE,
        /** Latched and holding. */
        HOLD,
        /** The target got too far away. */
        BROKEN_DISTANCE,
        /** The tetherer took enough damage. */
        BROKEN_DAMAGE,
        /** Held for the maximum time. */
        EXPIRED
    }

    private final double breakDistance;
    private final float breakDamage;
    private final int maxDuration;
    private final int cooldown;

    private int targetId = -1;
    private long latchedAt;
    private long releasedAt = Long.MIN_VALUE;
    private float damageTaken;

    /**
     * @param breakDistance distance in blocks at which the tether snaps
     * @param breakDamage   damage the tetherer must take to break it
     * @param maxDuration   longest hold in ticks (0 = unlimited)
     * @param cooldown      ticks after a break before it may latch again
     */
    public TetherState(double breakDistance, float breakDamage, int maxDuration, int cooldown) {
        this.breakDistance = breakDistance;
        this.breakDamage = breakDamage;
        this.maxDuration = maxDuration;
        this.cooldown = cooldown;
    }

    public boolean isLatched() {
        return this.targetId >= 0;
    }

    public int targetId() {
        return this.targetId;
    }

    /** Whether a new latch is allowed at {@code now}. */
    public boolean canLatch(long now) {
        return !this.isLatched() && (this.releasedAt == Long.MIN_VALUE || now - this.releasedAt >= this.cooldown);
    }

    /** Latches onto {@code entityId}. Returns false if not allowed. */
    public boolean latch(int entityId, long now) {
        if (entityId < 0 || !this.canLatch(now)) {
            return false;
        }
        this.targetId = entityId;
        this.latchedAt = now;
        this.damageTaken = 0.0F;
        return true;
    }

    /** Records damage to the tetherer. Returns true if that breaks the tether. */
    public boolean recordDamage(float amount) {
        if (!this.isLatched() || amount <= 0.0F) {
            return false;
        }
        this.damageTaken += amount;
        return this.damageTaken >= this.breakDamage;
    }

    /** Evaluates the tether at {@code now} with the target at squared distance {@code distSqr}. */
    public Status check(double distSqr, long now) {
        if (!this.isLatched()) {
            return Status.IDLE;
        }
        if (this.damageTaken >= this.breakDamage) {
            return Status.BROKEN_DAMAGE;
        }
        if (distSqr > this.breakDistance * this.breakDistance) {
            return Status.BROKEN_DISTANCE;
        }
        if (this.maxDuration > 0 && now - this.latchedAt >= this.maxDuration) {
            return Status.EXPIRED;
        }
        return Status.HOLD;
    }

    /** Releases the tether and starts the cooldown. Safe to call when not latched. */
    public void release(long now) {
        if (this.isLatched()) {
            this.releasedAt = now;
        }
        this.targetId = -1;
        this.damageTaken = 0.0F;
    }
}
