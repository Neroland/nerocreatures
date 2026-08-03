package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * A slow, low-damage energy field around the creature — pressure that makes standing next to a
 * plasma-bodied mob a bad idea, without turning it into a damage-per-tick blender.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>The goal runs continuously but does work only once every {@code intervalTicks}: outside
 *       that beat, {@link #tick()} is an integer decrement.</li>
 *   <li>On the beat it does <b>one</b> AABB entity scan bounded by {@code radius} and damages at
 *       most {@value #MAX_VICTIMS_PER_PULSE} entities. Both bounds exist so the goal's worst case
 *       is independent of how crowded the area is.</li>
 *   <li>The scan filters to living, non-{@code Mob} targets by default (i.e. players and passive
 *       livestock), which keeps a group of aura creatures from burning each other down and from
 *       scanning-and-damaging in an N² loop.</li>
 *   <li>No pathfinding, no line-of-sight ray casts.</li>
 * </ul>
 */
public class EnergyAuraGoal extends Goal {

    /** Never damage more than this many entities in a single pulse. */
    private static final int MAX_VICTIMS_PER_PULSE = 8;

    protected final Mob mob;

    private final double radius;
    private final float damage;
    private final int intervalTicks;

    private int ticksToPulse;

    /**
     * @param mob           the aura source
     * @param radius        aura radius in blocks
     * @param damage        damage dealt per pulse to each victim
     * @param intervalTicks ticks between pulses (20 = once a second)
     */
    public EnergyAuraGoal(Mob mob, double radius, float damage, int intervalTicks) {
        this.mob = mob;
        this.radius = radius;
        this.damage = damage;
        this.intervalTicks = Math.max(1, intervalTicks);
        // No control flags: the aura is passive and must never compete with movement or targeting.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return this.mob.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticksToPulse = this.intervalTicks;
    }

    @Override
    public void tick() {
        if (--this.ticksToPulse > 0) {
            return;
        }
        this.ticksToPulse = this.intervalTicks;
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        AABB box = this.mob.getBoundingBox().inflate(this.radius);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box, this::isVictim);
        DamageSource source = this.damageSource();
        int hit = 0;
        for (LivingEntity victim : victims) {
            if (hit >= MAX_VICTIMS_PER_PULSE) {
                break;
            }
            victim.hurtServer(level, source, this.damage);
            hit++;
        }
        if (hit > 0) {
            this.onPulse(level);
        }
    }

    /**
     * Who the aura burns. Default: any living entity that is not itself a mob — players and the
     * like — which deliberately excludes other creatures so packs do not melt one another.
     * Override for a creature whose aura should be indiscriminate.
     */
    protected boolean isVictim(LivingEntity candidate) {
        return candidate != this.mob && candidate.isAlive() && !(candidate instanceof Mob);
    }

    /**
     * The damage type of the aura. Default: generic magic damage attributed to this mob, so death
     * messages and mob-griefing rules behave sensibly. Override to use a mod- or datapack-defined
     * damage type once one exists.
     */
    protected DamageSource damageSource() {
        return this.mob.damageSources().mobAttack(this.mob);
    }

    /** Feedback for a pulse that actually hit something. Default: a ring of enchant-hit sparks. */
    protected void onPulse(ServerLevel level) {
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, this.mob.getX(),
                this.mob.getY() + this.mob.getBbHeight() * 0.5D, this.mob.getZ(),
                10, this.radius * 0.4D, 0.3D, this.radius * 0.4D, 0.0D);
    }
}
