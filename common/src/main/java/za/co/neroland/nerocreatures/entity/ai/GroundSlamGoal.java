package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * A telegraphed area slam: the creature plants itself, the ground shakes for a moment, and then
 * everything standing near it is thrown off its feet.
 *
 * <p>It is the melee half of a boss fight expressed as one goal — a heavy attack that punishes
 * standing next to the boss without punishing being in melee at all, because the telegraph is long
 * enough to walk out of. The slam does not care where the target is once it starts: it hits a ring,
 * not a person, so backing off is always the answer and there is nothing to dodge frame-perfectly.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>Between slams the goal is not running at all: {@link #canUse()} is a counter comparison and
 *       one already-cached target read.</li>
 *   <li>While winding up, {@link #tick()} is an integer decrement plus (every four ticks) one
 *       {@code sendParticles} call.</li>
 *   <li>The slam itself is <b>one</b> AABB scan bounded by {@code radius}, damaging at most
 *       {@value #MAX_VICTIMS} entities. Both bounds are why a crowded arena costs the same as an
 *       empty one.</li>
 *   <li>No pathfinding and no line-of-sight ray casts, ever. The goal takes the MOVE flag while it
 *       winds up purely so the creature stands still — it never asks the navigator for anything.</li>
 * </ul>
 */
public class GroundSlamGoal extends Goal {

    /** Never throw more than this many entities in a single slam. */
    private static final int MAX_VICTIMS = 10;

    /** Upward impulse applied to a victim, in blocks per tick. */
    private static final double LIFT = 0.45D;

    /** Horizontal impulse applied to a victim, in blocks per tick. */
    private static final double SHOVE = 0.7D;

    protected final Mob mob;

    private final double radius;
    private final float damage;
    private final int cooldownTicks;
    private final int telegraphTicks;

    private int ticksToReady;
    private int windUp;

    /**
     * @param mob            the slammer
     * @param radius         how far the shockwave reaches, in blocks
     * @param damage         damage dealt to each victim
     * @param cooldownTicks  ticks between slams
     * @param telegraphTicks how long the wind-up lasts — the player's warning, and their way out
     */
    public GroundSlamGoal(Mob mob, double radius, float damage, int cooldownTicks, int telegraphTicks) {
        this.mob = mob;
        this.radius = radius;
        this.damage = damage;
        this.cooldownTicks = Math.max(20, cooldownTicks);
        this.telegraphTicks = Math.max(5, telegraphTicks);
        this.ticksToReady = this.cooldownTicks / 2;
        // MOVE only: the creature roots itself for the wind-up but keeps looking around, and the
        // slam must never block its melee goal from resuming afterwards.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.ticksToReady > 0) {
            this.ticksToReady--;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) <= (this.radius + 2.0D) * (this.radius + 2.0D);
    }

    @Override
    public boolean canContinueToUse() {
        return this.windUp > 0 && this.mob.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.windUp = this.telegraphTicks;
        this.mob.getNavigation().stop();
        this.mob.playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.6F);
    }

    @Override
    public void stop() {
        this.windUp = 0;
        this.ticksToReady = this.cooldownTicks;
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            this.windUp = 0;
            return;
        }
        if (this.windUp % 4 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, this.mob.getX(), this.mob.getY() + 0.1D,
                    this.mob.getZ(), 8, this.radius * 0.3D, 0.0D, this.radius * 0.3D, 0.01D);
        }
        if (--this.windUp > 0) {
            return;
        }
        this.slam(level);
    }

    /** The moment of impact: one bounded scan, damage and an impulse. */
    private void slam(ServerLevel level) {
        AABB box = this.mob.getBoundingBox().inflate(this.radius, this.radius * 0.5D, this.radius);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box, this::isVictim);
        DamageSource source = this.mob.damageSources().mobAttack(this.mob);
        int hit = 0;
        for (LivingEntity victim : victims) {
            if (hit >= MAX_VICTIMS) {
                break;
            }
            victim.hurtServer(level, source, this.damage);
            double dx = victim.getX() - this.mob.getX();
            double dz = victim.getZ() - this.mob.getZ();
            double length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            victim.push(dx / length * SHOVE, LIFT, dz / length * SHOVE);
            // Without this a player's client never sees the impulse — the server's velocity change
            // is only sent when the entity is marked as having been knocked about.
            victim.hurtMarked = true;
            hit++;
        }
        this.onSlam(level, hit);
    }

    /**
     * Who the shockwave throws. Default: any living entity that is not itself a mob — players and
     * the like — so a boss does not blast its own summoned adds off the arena.
     */
    protected boolean isVictim(LivingEntity candidate) {
        return candidate != this.mob && candidate.isAlive() && !(candidate instanceof Mob);
    }

    /** Impact feedback. Default: a dust ring and a heavy thud, whether or not it connected. */
    protected void onSlam(ServerLevel level, int victims) {
        level.sendParticles(ParticleTypes.EXPLOSION, this.mob.getX(), this.mob.getY() + 0.2D,
                this.mob.getZ(), 3, this.radius * 0.4D, 0.0D, this.radius * 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.mob.getX(), this.mob.getY() + 0.2D,
                this.mob.getZ(), 24, this.radius * 0.5D, 0.1D, this.radius * 0.5D, 0.02D);
        this.mob.playSound(SoundEvents.ANVIL_LAND, 1.4F, 0.5F);
    }
}
