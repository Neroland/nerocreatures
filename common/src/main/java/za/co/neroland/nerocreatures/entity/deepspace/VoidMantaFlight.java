package za.co.neroland.nerocreatures.entity.deepspace;

import java.util.function.DoublePredicate;

/**
 * The Void Manta's flight and blink rules, as pure numbers so they are testable without a world.
 * {@link VoidManta} feeds them the gravity attribute, the place, a block raycast and a chunk probe.
 *
 * <h2>Where it flies (the gating rule)</h2>
 * <ul>
 *   <li><b>Free flight</b> when the mount's {@code minecraft:gravity} attribute is reduced to
 *       {@value #FREE_FLIGHT_MAX_GRAVITY} or less (three quarters of vanilla's
 *       {@value #VANILLA_GRAVITY}; a planet mod's low- or zero-gravity world), <b>or</b> it is in a
 *       {@code nerocreatures:habitat/deep_space} biome on an off-Earth dimension type (its own
 *       habitat; the dimension guard keeps void superflat and skyblock overworlds out, as the spawn
 *       rule does). Look pitch climbs and dives; there is no gravity while ridden.</li>
 *   <li><b>Glide</b> everywhere else (normal gravity): it cannot climb, and while airborne it sinks
 *       no faster than {@value #GLIDE_SINK} blocks a tick, ridden or not. It never takes fall
 *       damage.</li>
 * </ul>
 *
 * <h2>Blink</h2>
 * The jump bar (stamina-limited by the mount base) arms a blink; it fires
 * {@value #BLINK_DELAY_TICKS} ticks later, at the art's wing-fold. It moves the mount up to
 * {@value #BLINK_DISTANCE} blocks along the rider's look (horizontal only while gliding):
 * <ul>
 *   <li>A block raycast shortens it to stop {@value #BLOCK_CLEARANCE} blocks short of the first
 *       solid block; the move itself is a swept-box move, so it can never pass through a block.</li>
 *   <li>It is shortened again to stay inside loaded chunks (sampled every block, plus the disc's
 *       half-width).</li>
 *   <li>Shorter than {@value #MIN_BLINK} blocks, it fizzles.</li>
 *   <li>Only one blink at a time: another cannot be armed until the {@value #BLINK_POSE_TICKS}-tick
 *       pose has finished.</li>
 * </ul>
 */
public final class VoidMantaFlight {

    /** Vanilla's default {@code minecraft:gravity}. */
    public static final double VANILLA_GRAVITY = 0.08D;

    /** At or below this gravity the manta flies freely anywhere. */
    public static final double FREE_FLIGHT_MAX_GRAVITY = 0.06D;

    /** Fastest airborne sink while gliding (blocks per tick). */
    public static final double GLIDE_SINK = 0.12D;

    /** The longest blink (blocks). */
    public static final double BLINK_DISTANCE = 8.0D;

    /** A blink shorter than this fizzles (blocks). */
    public static final double MIN_BLINK = 1.0D;

    /** How far short of the raycast's block hit a blink stops (the disc's half-width plus a margin). */
    public static final double BLOCK_CLEARANCE = 1.5D;

    /** Ticks from arming to the jump: the art's ability clip has the blink at tick 10. */
    public static final int BLINK_DELAY_TICKS = 10;

    /** The ability pose's length; also the shortest gap between two blinks. */
    public static final int BLINK_POSE_TICKS = 20;

    /** Chunk-probe step along the path (blocks). */
    public static final double LOADED_STEP = 1.0D;

    private VoidMantaFlight() {
    }

    /**
     * Whether the manta flies freely here.
     *
     * @param gravity         the mount's current {@code minecraft:gravity} attribute value
     * @param spaceDimension  whether the level's dimension type is off-Earth
     * @param deepSpaceBiome  whether the biome here is in {@code nerocreatures:habitat/deep_space}
     */
    public static boolean flightAllowed(double gravity, boolean spaceDimension, boolean deepSpaceBiome) {
        if (!Double.isNaN(gravity) && gravity <= FREE_FLIGHT_MAX_GRAVITY) {
            return true;
        }
        return spaceDimension && deepSpaceBiome;
    }

    /** The vertical speed after the glide floor: never sinking faster than {@link #GLIDE_SINK}. */
    public static double glideVertical(double verticalSpeed) {
        if (Double.isNaN(verticalSpeed)) {
            return 0.0D;
        }
        return Math.max(verticalSpeed, -GLIDE_SINK);
    }

    /**
     * The blink's unit direction {x, y, z} from the rider's yaw and pitch (degrees, Minecraft
     * convention). While gliding the pitch is ignored, so a blink never gains height on a
     * normal-gravity world.
     */
    public static double[] direction(float yawDegrees, float pitchDegrees, boolean flight) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = flight ? Math.toRadians(pitchDegrees) : 0.0D;
        double horizontal = Math.cos(pitch);
        return new double[] {-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal};
    }

    /**
     * The furthest distance (a multiple of {@link #LOADED_STEP}, at most {@link #BLINK_DISTANCE})
     * such that every path sample up to it, each probed {@code margin} further on, is in a loaded
     * chunk. Zero when even the start is not.
     *
     * @param margin    extra reach probed past each sample (the disc's half-width)
     * @param loadedAt  whether the point this many blocks along the path is in a loaded chunk
     */
    public static double loadedReach(double margin, DoublePredicate loadedAt) {
        double probe = Double.isNaN(margin) ? 0.0D : Math.max(0.0D, margin);
        double safe = 0.0D;
        int steps = (int) Math.floor(BLINK_DISTANCE / LOADED_STEP);
        for (int i = 0; i <= steps; i++) {
            double along = i * LOADED_STEP;
            if (!loadedAt.test(along + probe)) {
                return safe;
            }
            safe = along;
        }
        return Math.min(BLINK_DISTANCE, safe);
    }

    /**
     * The blink length: {@link #BLINK_DISTANCE}, cut to stop {@link #BLOCK_CLEARANCE} short of the
     * raycast's block hit and to the loaded reach. Zero when the result is under {@link #MIN_BLINK}
     * (the blink fizzles).
     *
     * @param blockHitDistance distance to the first solid block along the path, or
     *                         {@link Double#POSITIVE_INFINITY} for a clear path
     * @param loadedReach      {@link #loadedReach}
     */
    public static double blinkDistance(double blockHitDistance, double loadedReach) {
        double hit = Double.isNaN(blockHitDistance) ? 0.0D : blockHitDistance;
        double reach = Double.isNaN(loadedReach) ? 0.0D : loadedReach;
        double distance = Math.min(BLINK_DISTANCE, Math.min(hit - BLOCK_CLEARANCE, reach));
        return distance >= MIN_BLINK ? distance : 0.0D;
    }

    /** Whether a new blink may be armed: none pending and the previous pose finished. */
    public static boolean mayArm(int pendingCountdown, boolean abilityPoseRunning) {
        return pendingCountdown <= 0 && !abilityPoseRunning;
    }
}
