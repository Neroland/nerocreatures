package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Prey flee predators as a herd.
 *
 * <p>Every {@code scanInterval} ticks the member looks for one entity in {@code predatorTag} within
 * {@code detectRadius} (bounded query, first hit wins). On a sighting it raises the alarm on up to
 * {@value #MAX_MATES_ALARMED} same-type mates within {@code herdRadius} that implement
 * {@link HerdMember}, then runs. A member that is already alarmed by a mate runs without scanning.
 */
public class PreyFleeGoal extends Goal {

    private static final int MAX_MATES_ALARMED = 8;

    private final PathfinderMob mob;
    private final TagKey<EntityType<?>> predatorTag;
    private final double detectRadius;
    private final double herdRadius;
    private final int alarmTicks;
    private final double speed;
    private final CadenceTimer cadence;

    private Vec3 fleeTo;
    private long fleeUntil;

    /**
     * @param mob          the prey
     * @param predatorTag  usually {@link EcosystemTags#PREDATORS}
     * @param detectRadius blocks at which a predator is noticed (10-12)
     * @param herdRadius   blocks within which mates are alarmed (12-16)
     * @param scanInterval ticks between scans (20-40)
     * @param alarmTicks   how long an alarm lasts (100)
     * @param speed        flee speed modifier (1.4-1.6)
     */
    public PreyFleeGoal(PathfinderMob mob, TagKey<EntityType<?>> predatorTag, double detectRadius, double herdRadius,
            int scanInterval, int alarmTicks, double speed) {
        this.mob = mob;
        this.predatorTag = predatorTag;
        this.detectRadius = detectRadius;
        this.herdRadius = herdRadius;
        this.alarmTicks = alarmTicks;
        this.speed = speed;
        this.cadence = new CadenceTimer(scanInterval);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level) || this.mob.isLeashed()) {
            return false;
        }
        long now = level.getGameTime();
        Vec3 threat = null;
        if (this.mob instanceof HerdMember member && PredatorPreyRules.alarmActive(now, member.alarmUntil())) {
            threat = member.alarmThreat();
        } else if (this.cadence.ready(now, this.mob.getId())) {
            LivingEntity predator = this.findPredator(level);
            if (predator != null) {
                threat = predator.position();
                this.alarmHerd(level, threat, now + this.alarmTicks);
            }
        }
        if (threat == null) {
            return false;
        }
        Vec3 away = DefaultRandomPos.getPosAway(this.mob, 16, 7, threat);
        if (away == null) {
            return false;
        }
        this.fleeTo = away;
        this.fleeUntil = now + this.alarmTicks;
        return true;
    }

    private LivingEntity findPredator(ServerLevel level) {
        AABB box = this.mob.getBoundingBox().inflate(this.detectRadius, 4.0D, this.detectRadius);
        List<LivingEntity> found = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), box,
                // canBeSeenAsEnemy: a hidden predator (a buried Frost Lurker, a burrowed worm) is not
                // noticed, so prey can walk into the trap.
                e -> e != this.mob && e.isAlive() && e.canBeSeenAsEnemy() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(e)
                        && e.getType().builtInRegistryHolder().is(this.predatorTag), found, 1);
        return found.isEmpty() ? null : found.get(0);
    }

    private void alarmHerd(ServerLevel level, Vec3 threat, long until) {
        if (this.mob instanceof HerdMember self) {
            self.raiseAlarm(threat, until);
        }
        AABB box = this.mob.getBoundingBox().inflate(this.herdRadius, 4.0D, this.herdRadius);
        List<PathfinderMob> mates = new ArrayList<>(MAX_MATES_ALARMED);
        level.getEntities(EntityTypeTest.forClass(PathfinderMob.class), box,
                e -> e != this.mob && e.getType() == this.mob.getType() && e instanceof HerdMember && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(e), mates,
                MAX_MATES_ALARMED);
        for (PathfinderMob mate : mates) {
            ((HerdMember) mate).raiseAlarm(threat, until);
        }
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.fleeTo.x, this.fleeTo.y, this.fleeTo.z, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && this.mob.level().getGameTime() < this.fleeUntil;
    }

    @Override
    public void stop() {
        this.fleeTo = null;
    }
}
