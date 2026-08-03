package za.co.neroland.nerocreatures.entity.ai;

import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pack behaviour without a pack goal: three small static helpers that a hunter's existing goals
 * call at the moments that matter, rather than a per-tick "flocking" system.
 *
 * <ul>
 *   <li>{@link #broadcastTarget} — one hunter finding prey pulls its neighbours in. Call it
 *       <b>only on target acquisition</b> (from {@code setTarget} / a target goal's
 *       {@code start()}), never from {@code tick()}.</li>
 *   <li>{@link #flankOffset} — a deterministic per-member offset around the target, so the pack
 *       surrounds instead of queueing up in one line. Pure arithmetic, safe to call every re-path.</li>
 *   <li>{@link #nightBoldness} — the "they get braver after dark" multiplier for detection range
 *       and approach speed.</li>
 * </ul>
 *
 * <h2>Cost profile</h2>
 *
 * <p>{@link #broadcastTarget} is the only expensive call: one AABB entity scan bounded by
 * {@code radius}, capped at {@value #MAX_PACK_SIZE} recruits. It is event-driven (a target was just
 * acquired), so a pack of eight hunting one player costs at most eight scans for the whole
 * engagement, not eight per tick. {@link #flankOffset} and {@link #nightBoldness} allocate nothing
 * beyond one {@link Vec3} and touch no world state.
 */
public final class PackCoordination {

    /** Upper bound on how many neighbours a single call may recruit. */
    private static final int MAX_PACK_SIZE = 8;

    /** Detection/approach multiplier applied when it is dark outside. */
    private static final double NIGHT_BOLDNESS = 1.35D;

    private PackCoordination() {
    }

    /**
     * Tells nearby pack-mates of the same class about a target they have not noticed yet.
     *
     * <p>Only mobs with <em>no</em> current target are recruited, so an engaged pack-mate is never
     * pulled off its own fight, and the caller itself is skipped.
     *
     * @param caller     the mob that found the target
     * @param packClass  the class that counts as "same pack" (usually the caller's own class)
     * @param radius     recruiting radius in blocks
     * @param target     the target to share
     * @return how many pack-mates were recruited
     */
    public static <T extends Mob> int broadcastTarget(Mob caller, Class<T> packClass, double radius,
            LivingEntity target) {
        if (target == null || !target.isAlive() || caller.level().isClientSide()) {
            return 0;
        }
        AABB box = caller.getBoundingBox().inflate(radius);
        List<T> packMates = caller.level().getEntitiesOfClass(packClass, box,
                mate -> mate != caller && mate.isAlive() && mate.getTarget() == null);
        int recruited = 0;
        for (T mate : packMates) {
            if (recruited >= MAX_PACK_SIZE) {
                break;
            }
            mate.setTarget(target);
            recruited++;
        }
        return recruited;
    }

    /**
     * A stable position offset around {@code target} for this pack member, so members approach from
     * different sides. The angle is derived from the member's entity id, which means it does not
     * change from tick to tick (no jitter) and needs no shared state.
     *
     * @param member   the pack member asking where to stand
     * @param target   what the pack is converging on
     * @param distance how far from the target to stand, in blocks
     */
    public static Vec3 flankOffset(Mob member, LivingEntity target, double distance) {
        // Golden-angle spread keyed off the entity id: neighbouring ids land far apart on the circle.
        float angle = (member.getId() * 137.508F) % 360.0F;
        double radians = angle * Mth.DEG_TO_RAD;
        return new Vec3(
                target.getX() + Math.cos(radians) * distance,
                target.getY(),
                target.getZ() + Math.sin(radians) * distance);
    }

    /**
     * The night-boldness multiplier: {@value #NIGHT_BOLDNESS} while it is dark outside, 1.0
     * otherwise. Dimensions with a fixed time of day (most space dimensions) are never "dark
     * outside" by vanilla's definition, so a permanently dim planet does not permanently buff its
     * hunters — that is intentional, and per-creature tuning belongs on the creature.
     */
    public static double nightBoldness(Level level) {
        return level.isDarkOutside() ? NIGHT_BOLDNESS : 1.0D;
    }
}
