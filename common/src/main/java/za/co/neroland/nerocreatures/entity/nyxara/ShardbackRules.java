package za.co.neroland.nerocreatures.entity.nyxara;

/**
 * Pure rules for the Shardback (Nyxara hostile): the front mirror arc, the charge cycle and the
 * reflection cap. No game types, so each rule is unit-tested.
 *
 * <h2>Front arc</h2>
 * The mirror fan covers {@code 2 x }{@value #FRONT_HALF_ANGLE} degrees around the body's facing.
 * Damage whose source position lies in that arc is ignored and projectiles arriving from it are
 * reflected back the way they came, unless the plates are folded ({@link #exposed}: wind-up, run
 * and recovery of a charge). From behind, or with no source position (falls, lava, the void), it is
 * hurt normally.
 *
 * <h2>Charge</h2>
 * {@link Phase#ROAM} -> {@link Phase#WINDUP} ({@value #WINDUP_TICKS} ticks: plates fold, it paws
 * the ground; the telegraph) -> {@link Phase#RUN} (straight line toward where the target stood, at
 * most {@value #RUN_MAX_TICKS} ticks, ends on the first hit or a wall) -> {@link Phase#RECOVER}
 * ({@value #RECOVER_TICKS} ticks, still exposed) -> ROAM with a cooldown. At most
 * {@value #MAX_HITS_PER_CHARGE} hit per charge.
 *
 * <h2>Reflection cap</h2>
 * At most {@value #MAX_REFLECTS_PER_WINDOW} reflections per {@value #REFLECT_WINDOW_TICKS} ticks
 * ({@link ReflectBudget}); past it, projectiles from the front are still blocked but drop instead of
 * flying back, so a dispenser loop cannot keep projectiles alive forever.
 */
public final class ShardbackRules {

    /** Half the front arc, in degrees (a 140-degree mirror fan). */
    public static final double FRONT_HALF_ANGLE = 70.0D;

    public static final int WINDUP_TICKS = 20;
    public static final int RUN_MAX_TICKS = 30;
    public static final int RECOVER_TICKS = 40;
    public static final int COOLDOWN_MIN_TICKS = 100;
    public static final int COOLDOWN_MAX_TICKS = 160;
    /** The {@code ability} pose covers the wind-up and the run (under 3 s). */
    public static final int CHARGE_POSE_TICKS = WINDUP_TICKS + RUN_MAX_TICKS;

    public static final double CHARGE_MIN_RANGE = 5.0D;
    public static final double CHARGE_MAX_RANGE = 16.0D;
    /** Run speed, blocks per tick. */
    public static final double RUN_SPEED = 0.55D;
    public static final float CHARGE_DAMAGE = 8.0F;
    public static final double CHARGE_KNOCKBACK = 1.2D;
    public static final int MAX_HITS_PER_CHARGE = 1;

    public static final int REFLECT_WINDOW_TICKS = 20;
    public static final int MAX_REFLECTS_PER_WINDOW = 6;

    /** {@code form} while the plates are folded flat (exposed): the model shows the weak point. */
    public static final int FORM_FOLDED = 1;

    /** The charge cycle. */
    public enum Phase { ROAM, WINDUP, RUN, RECOVER }

    private ShardbackRules() {
    }

    /**
     * Whether a point lies in the front arc.
     *
     * @param bodyYawDeg the body yaw in degrees (Minecraft convention: facing {@code (-sin, cos)})
     * @param dx         source x minus creature x
     * @param dz         source z minus creature z
     * @param halfAngle  half the arc, degrees
     * @return true in the arc; a source exactly on the creature counts as in front (point blank
     *         hits the fan)
     */
    public static boolean inFrontArc(double bodyYawDeg, double dx, double dz, double halfAngle) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) {
            return true;
        }
        double rad = Math.toRadians(bodyYawDeg);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double cos = (fx * dx + fz * dz) / len;
        return cos >= Math.cos(Math.toRadians(halfAngle)) - 1.0E-9;
    }

    /** Plates are folded (back and front both exposed) for the whole charge. */
    public static boolean exposed(Phase phase) {
        return phase != Phase.ROAM;
    }

    /**
     * Whether a hit lands.
     *
     * @param exposed      plates folded (charging)
     * @param hasSource    the damage has a source position
     * @param fromFront    that position is in the front arc
     */
    public static boolean vulnerable(boolean exposed, boolean hasSource, boolean fromFront) {
        return exposed || !hasSource || !fromFront;
    }

    /** A projectile is reflected when it arrives from the front while the fan is up. */
    public static boolean reflects(boolean exposed, boolean fromFront) {
        return !exposed && fromFront;
    }

    /** A charge may start at this distance. */
    public static boolean inChargeRange(double distance) {
        return distance >= CHARGE_MIN_RANGE && distance <= CHARGE_MAX_RANGE;
    }

    /**
     * The next phase.
     *
     * @param phase    current phase
     * @param ticks    ticks spent in it
     * @param ready    ROAM only: cooldown over and a target in charge range with line of sight
     * @param stopped  RUN only: it hit something, ran into a wall, or the hit cap was reached
     */
    public static Phase next(Phase phase, int ticks, boolean ready, boolean stopped) {
        return switch (phase) {
            case ROAM -> ready ? Phase.WINDUP : Phase.ROAM;
            case WINDUP -> ticks >= WINDUP_TICKS ? Phase.RUN : Phase.WINDUP;
            case RUN -> stopped || ticks >= RUN_MAX_TICKS ? Phase.RECOVER : Phase.RUN;
            case RECOVER -> ticks >= RECOVER_TICKS ? Phase.ROAM : Phase.RECOVER;
        };
    }

    /** True while another hit is allowed this charge. */
    public static boolean mayHit(int hitsThisCharge) {
        return hitsThisCharge < MAX_HITS_PER_CHARGE;
    }

    /** Cooldown for a roll in {@code [0, 1)}. */
    public static int cooldown(float roll) {
        float r = Math.max(0.0F, Math.min(1.0F, roll));
        return COOLDOWN_MIN_TICKS + (int) ((COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS) * r);
    }

    /** Reflection rate limit: {@value #MAX_REFLECTS_PER_WINDOW} per {@value #REFLECT_WINDOW_TICKS} ticks. */
    public static final class ReflectBudget {

        private long windowStart = Long.MIN_VALUE;
        private int used;

        /** Uses one reflection if the window has room. */
        public boolean tryUse(long gameTime) {
            if (this.windowStart == Long.MIN_VALUE || gameTime - this.windowStart >= REFLECT_WINDOW_TICKS
                    || gameTime < this.windowStart) {
                this.windowStart = gameTime;
                this.used = 0;
            }
            if (this.used >= MAX_REFLECTS_PER_WINDOW) {
                return false;
            }
            this.used++;
            return true;
        }
    }
}
