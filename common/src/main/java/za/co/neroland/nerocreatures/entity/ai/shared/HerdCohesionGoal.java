package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * Keeps a herd together: every {@code scanInterval} ticks a member finds up to
 * {@value #MAX_MATES} same-type mates within {@code radius} and walks back toward their centroid
 * when it has strayed past {@code maxGap} ({@link HerdRules}). Low priority: any flee, feed or
 * follow goal wins over it.
 */
public class HerdCohesionGoal extends Goal {

    private static final int MAX_MATES = 12;

    private final PathfinderMob mob;
    private final double radius;
    private final double maxGap;
    private final double speed;
    private final CadenceTimer cadence;

    private double targetX;
    private double targetY;
    private double targetZ;

    /**
     * @param mob          herd member
     * @param radius       how far it looks for mates (16-24)
     * @param maxGap       distance from the centroid that triggers regrouping (6-8)
     * @param scanInterval ticks between checks (40-80)
     * @param speed        walk speed modifier
     */
    public HerdCohesionGoal(PathfinderMob mob, double radius, double maxGap, int scanInterval, double speed) {
        this.mob = mob;
        this.radius = radius;
        this.maxGap = maxGap;
        this.speed = speed;
        this.cadence = new CadenceTimer(scanInterval);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level) || this.mob.isLeashed()
                || !this.cadence.ready(level.getGameTime(), this.mob.getId())) {
            return false;
        }
        AABB box = this.mob.getBoundingBox().inflate(this.radius, 4.0D, this.radius);
        List<PathfinderMob> mates = new ArrayList<>(MAX_MATES);
        level.getEntities(EntityTypeTest.forClass(PathfinderMob.class), box,
                e -> e != this.mob && e.isAlive() && e.getType() == this.mob.getType() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(e), mates, MAX_MATES);
        if (mates.isEmpty()) {
            return false;
        }
        double[] xs = new double[mates.size()];
        double[] zs = new double[mates.size()];
        double sy = 0.0D;
        for (int i = 0; i < xs.length; i++) {
            xs[i] = mates.get(i).getX();
            zs[i] = mates.get(i).getZ();
            sy += mates.get(i).getY();
        }
        double[] c = HerdRules.centroid(xs, zs);
        double dx = c[0] - this.mob.getX();
        double dz = c[1] - this.mob.getZ();
        if (!HerdRules.shouldRegroup(mates.size(), dx * dx + dz * dz, this.maxGap)) {
            return false;
        }
        this.targetX = c[0];
        this.targetY = sy / xs.length;
        this.targetZ = c[1];
        return true;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        double dx = this.targetX - this.mob.getX();
        double dz = this.targetZ - this.mob.getZ();
        return !this.mob.getNavigation().isDone() && !HerdRules.regrouped(dx * dx + dz * dz, this.maxGap);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }
}
