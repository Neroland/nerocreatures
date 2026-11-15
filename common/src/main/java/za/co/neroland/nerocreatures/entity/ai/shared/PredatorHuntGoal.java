package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.entity.tame.HusbandryRules;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;

/**
 * Target goal: a predator picks a prey from an entity-type tag, on a slow cadence with a cached
 * target, and respects the {@link PredatorPreyRules} caps (satiation, chase timeout, at most
 * {@link PredatorPreyRules#MAX_HUNTERS_PER_PREY} hunters per prey).
 *
 * <p>Never targets tamed, owned or name-tagged prey: a player's pet or named animal is not part of
 * the wild food chain. Add to the creature's {@code targetSelector} below its player targeting so a
 * player still takes priority. The creature's melee goal does the chasing.
 */
public class PredatorHuntGoal extends Goal {

    private static final int MAX_CANDIDATES = 8;

    private final PathfinderMob mob;
    private final TagKey<EntityType<?>> preyTag;
    private final double radius;
    private final int satiationTicks;
    private final int maxChaseTicks;
    private final CadenceTimer cadence;

    private LivingEntity prey;
    private long chaseStart;
    private long lastMeal = Long.MIN_VALUE;

    /**
     * @param mob            the predator
     * @param preyTag        e.g. {@link EcosystemTags#PREY_GRAZERS}
     * @param radius         search radius in blocks (16 is plenty)
     * @param scanInterval   ticks between searches (40-100)
     * @param satiationTicks rest after a kill (e.g. 2400 = two minutes)
     * @param maxChaseTicks  give up after this long (e.g. 400)
     */
    public PredatorHuntGoal(PathfinderMob mob, TagKey<EntityType<?>> preyTag, double radius, int scanInterval,
            int satiationTicks, int maxChaseTicks) {
        this.mob = mob;
        this.preyTag = preyTag;
        this.radius = radius;
        this.satiationTicks = satiationTicks;
        this.maxChaseTicks = maxChaseTicks;
        this.cadence = new CadenceTimer(scanInterval);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /**
     * Wild-prey filter shared with other goals: not tamed, not owned, not named, and not livestock a
     * player keeps or bred (Stage 2 review M1, {@link HusbandryRules#wildPrey}).
     */
    public static boolean isWild(LivingEntity entity) {
        boolean husbanded = entity instanceof LivestockCreature livestock && livestock.isHusbanded();
        boolean tame = entity instanceof TamableAnimal tamable && tamable.isTame();
        // Owner checked last: it is the only lookup that may touch the level.
        return HusbandryRules.wildPrey(entity.hasCustomName(), tame, false, husbanded)
                && (!(entity instanceof OwnableEntity ownable) || ownable.getOwner() == null);
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null || !(this.mob.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        if (!PredatorPreyRules.hungry(now, this.lastMeal, this.satiationTicks)
                || !this.cadence.ready(now, this.mob.getId())) {
            return false;
        }
        AABB box = this.mob.getBoundingBox().inflate(this.radius, 4.0D, this.radius);
        List<LivingEntity> found = new ArrayList<>(MAX_CANDIDATES);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), box,
                e -> e != this.mob && e.isAlive() && e.getType().builtInRegistryHolder().is(this.preyTag) && isWild(e), found, MAX_CANDIDATES);
        if (found.isEmpty()) {
            return false;
        }
        double[] dist = new double[found.size()];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = this.mob.distanceToSqr(found.get(i));
        }
        int best = PredatorPreyRules.nearestWithin(dist, this.radius);
        if (best < 0) {
            return false;
        }
        LivingEntity candidate = found.get(best);
        if (!PredatorPreyRules.pressureAllows(huntersOf(level, candidate, box))) {
            return false;
        }
        this.prey = candidate;
        return true;
    }

    private int huntersOf(ServerLevel level, LivingEntity candidate, AABB box) {
        List<Mob> hunters = new ArrayList<>(PredatorPreyRules.MAX_HUNTERS_PER_PREY);
        level.getEntities(EntityTypeTest.forClass(Mob.class), box,
                m -> m != this.mob && m.getTarget() == candidate, hunters, PredatorPreyRules.MAX_HUNTERS_PER_PREY);
        return hunters.size();
    }

    @Override
    public void start() {
        this.chaseStart = this.mob.level().getGameTime();
        this.mob.setTarget(this.prey);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || target != this.prey || !target.isAlive()) {
            return false;
        }
        double leash = this.radius * 1.5D;
        return this.mob.distanceToSqr(target) <= leash * leash
                && !PredatorPreyRules.chaseTimedOut(this.mob.level().getGameTime(), this.chaseStart, this.maxChaseTicks);
    }

    @Override
    public void stop() {
        long now = this.mob.level().getGameTime();
        if (this.prey != null && !this.prey.isAlive()) {
            this.lastMeal = now;
        } else {
            // Gave up or lost it: a shorter rest so it does not immediately re-chase the same herd.
            this.lastMeal = now - this.satiationTicks / 2L;
        }
        if (this.mob.getTarget() == this.prey) {
            this.mob.setTarget(null);
        }
        this.prey = null;
    }
}
