package za.co.neroland.nerocreatures.block;

import java.util.List;

/**
 * Pure rules for the two area blocks that shape NeroCreatures' own spawn table: the <b>Lure Beacon</b>
 * and the <b>Sonic Ward</b>. No world types, so every bound is unit-tested.
 *
 * <ul>
 *   <li><b>Bounded radius.</b> A ward covers {@value #WARD_RADIUS} blocks and a lure
 *       {@value #LURE_RADIUS} (horizontal distance, plus {@value #VERTICAL_REACH} up and down), fixed by
 *       the griefing rules and not configurable.</li>
 *   <li><b>Bounded strength.</b> A lure multiplies one species' rule weight by its multiplier, clamped
 *       to {@code [1, }{@value #MAX_LURE_MULTIPLIER}{@code ]}. Overlapping lures do not stack: the
 *       strongest one applies.</li>
 *   <li><b>Bounded duration.</b> A field is only live until its {@code expiresAt} game time; the block
 *       entity refreshes it while its bait or fuel burns, so a removed, unloaded or starved block stops
 *       counting within {@value #FIELD_TTL_TICKS} ticks without any cleanup hook.</li>
 *   <li><b>Never exceeds caps.</b> A factor only re-weights the pick between rules; the spawner still
 *       checks every census cap per member, so a lure can make a species likelier, never more numerous
 *       than the caps allow.</li>
 *   <li><b>Ward first.</b> Inside any live ward a hostile rule weighs 0, whatever lure covers it.
 *       Wards touch only hostile NeroCreatures; they never touch another mod's mobs or a tamed pet.</li>
 * </ul>
 */
public final class FieldRules {

    /** Sonic Ward radius in blocks. */
    public static final int WARD_RADIUS = 16;

    /** Lure Beacon radius in blocks. */
    public static final int LURE_RADIUS = 24;

    /** Blocks above and below the field's block that still count as inside. */
    public static final int VERTICAL_REACH = 12;

    /** Hard ceiling on a lure's weight multiplier, whatever the config says. */
    public static final double MAX_LURE_MULTIPLIER = 8.0D;

    /** How long one refresh keeps a field live; the block entities refresh every 20 ticks. */
    public static final long FIELD_TTL_TICKS = 60L;

    /** At most this many live fields per level are consulted (the rest are ignored, never an error). */
    public static final int MAX_FIELDS_PER_LEVEL = 64;

    /** Extra margin past the ward edge a repelled creature is walked to. */
    public static final double WARD_PUSH_MARGIN = 3.0D;

    private FieldRules() {
    }

    /** Which block a field comes from. */
    public enum Kind {
        LURE, WARD
    }

    /**
     * One live field. {@code species} is the lured creature id path (e.g. {@code lumen_moth}) for a lure
     * and empty for a ward; {@code multiplier} is ignored for a ward.
     */
    public record Field(Kind kind, double x, double y, double z, String species, double multiplier,
            long expiresAt) {

        public int radius() {
            return this.kind == Kind.WARD ? WARD_RADIUS : LURE_RADIUS;
        }

        public boolean live(long now) {
            return now < this.expiresAt;
        }

        /** Inside this field's cylinder: horizontal radius plus {@link #VERTICAL_REACH}. */
        public boolean covers(double px, double py, double pz) {
            double dx = px - this.x;
            double dz = pz - this.z;
            double r = this.radius();
            return dx * dx + dz * dz <= r * r && Math.abs(py - this.y) <= VERTICAL_REACH;
        }
    }

    /** A config multiplier made safe: below 1 means "no effect", above the ceiling is clamped. */
    public static double clampMultiplier(double configured) {
        if (Double.isNaN(configured) || configured < 1.0D) {
            return 1.0D;
        }
        return Math.min(configured, MAX_LURE_MULTIPLIER);
    }

    /**
     * The factor a spawn rule's weight is multiplied by at one candidate position.
     *
     * @param fields  the level's fields (only the first {@link #MAX_FIELDS_PER_LEVEL} are read)
     * @param species the rule's creature id path
     * @param hostile whether the rule spawns a hostile creature ({@code MONSTER} category)
     * @return 0 for a hostile inside a live ward; otherwise the strongest matching lure's clamped
     *         multiplier; otherwise 1
     */
    public static double weightFactor(List<Field> fields, double px, double py, double pz, String species,
            boolean hostile, long now) {
        double lure = 1.0D;
        int seen = 0;
        for (Field field : fields) {
            if (++seen > MAX_FIELDS_PER_LEVEL) {
                break;
            }
            if (!field.live(now) || !field.covers(px, py, pz)) {
                continue;
            }
            if (field.kind() == Kind.WARD) {
                if (hostile) {
                    return 0.0D;
                }
            } else if (field.species().equals(species)) {
                lure = Math.max(lure, clampMultiplier(field.multiplier()));
            }
        }
        return lure;
    }

    /** Whether any live ward covers the position (the per-member placement check). */
    public static boolean warded(List<Field> fields, double px, double py, double pz, long now) {
        int seen = 0;
        for (Field field : fields) {
            if (++seen > MAX_FIELDS_PER_LEVEL) {
                break;
            }
            if (field.kind() == Kind.WARD && field.live(now) && field.covers(px, py, pz)) {
                return true;
            }
        }
        return false;
    }

    /** Which creatures a ward repels: hostile NeroCreatures only, never a boss, never a tamed one. */
    public static boolean wardRepels(boolean hostile, boolean boss, boolean tame) {
        return hostile && !boss && !tame;
    }

    /**
     * Where a repelled creature at {@code (px, pz)} is walked to: straight away from the ward's centre to
     * {@link #WARD_PUSH_MARGIN} blocks past its edge. A creature standing on the centre goes east.
     *
     * @return {@code {x, z}}
     */
    public static double[] pushTarget(double cx, double cz, double px, double pz) {
        double dx = px - cx;
        double dz = pz - cz;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        double reach = WARD_RADIUS + WARD_PUSH_MARGIN;
        return new double[] {cx + dx / length * reach, cz + dz / length * reach};
    }

    /** Burn bookkeeping shared by the lure and the ward: ticks left after one pulse, never negative. */
    public static int burnAfterPulse(int burnLeft, int pulseTicks) {
        return Math.max(0, burnLeft - Math.max(0, pulseTicks));
    }
}
