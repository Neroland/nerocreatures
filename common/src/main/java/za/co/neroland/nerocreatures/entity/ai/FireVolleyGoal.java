package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * A burst of fireballs, fired one at a time over a short window.
 *
 * <p>This is the ranged half of a boss fight: it turns a melee brawl into a fight with cover in it,
 * because a volley is dodgeable by moving and blockable by standing behind something. Each shot is
 * aimed at where the target is <em>now</em> with a little spread, so a moving target is genuinely
 * harder to hit and standing still is genuinely worse.
 *
 * <p>The projectile is vanilla's {@link SmallFireball}. That is a deliberate choice over a custom
 * one: it already has a model, a renderer, deflection behaviour, a damage type and a "punch it back"
 * interaction on every loader, and NeroCreatures ships no entity renderer it does not have to.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>Between volleys the goal is not running: {@link #canUse()} is a counter comparison, a cached
 *       target read, one squared-distance check and one <b>cached</b> line-of-sight test (the mob's
 *       own sensing cache, not a fresh ray cast).</li>
 *   <li>During a volley the goal spawns at most {@code projectiles} entities, one every
 *       {@code shotIntervalTicks} ticks. Every other tick is an integer decrement.</li>
 *   <li>No pathfinding. It takes the LOOK flag so the creature faces what it is shooting at, and
 *       nothing else — the boss keeps walking while it fires.</li>
 * </ul>
 */
public class FireVolleyGoal extends Goal {

    /** Hard ceiling on a volley, whatever a caller asks for. */
    private static final int MAX_PROJECTILES = 8;

    protected final Mob mob;

    private final int projectiles;
    private final int cooldownTicks;
    private final int shotIntervalTicks;
    private final double minRange;
    private final double maxRange;
    private final double spread;

    private int ticksToReady;
    private int shotsLeft;
    private int ticksToShot;

    /**
     * @param mob               the shooter
     * @param projectiles       shots per volley, clamped to {@value #MAX_PROJECTILES}
     * @param cooldownTicks     ticks between volleys
     * @param shotIntervalTicks ticks between the shots of one volley
     * @param minRange          closest the target may be for the goal to fire, in blocks — below it
     *                          the creature should be swinging, not shooting
     * @param maxRange          furthest the target may be, in blocks
     * @param spread            aim scatter added to each shot, as a fraction of the aim vector
     */
    public FireVolleyGoal(Mob mob, int projectiles, int cooldownTicks, int shotIntervalTicks,
            double minRange, double maxRange, double spread) {
        this.mob = mob;
        this.projectiles = Math.clamp(projectiles, 1, MAX_PROJECTILES);
        this.cooldownTicks = Math.max(20, cooldownTicks);
        this.shotIntervalTicks = Math.max(2, shotIntervalTicks);
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.spread = Math.max(0.0D, spread);
        this.ticksToReady = this.cooldownTicks / 2;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.ticksToReady > 0) {
            this.ticksToReady--;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distanceSqr = this.mob.distanceToSqr(target);
        return distanceSqr >= this.minRange * this.minRange
                && distanceSqr <= this.maxRange * this.maxRange
                && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return this.shotsLeft > 0 && this.mob.isAlive() && this.mob.getTarget() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.shotsLeft = this.projectiles;
        this.ticksToShot = 0;
    }

    @Override
    public void stop() {
        this.shotsLeft = 0;
        this.ticksToReady = this.cooldownTicks;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !(this.mob.level() instanceof ServerLevel level)) {
            this.shotsLeft = 0;
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--this.ticksToShot > 0) {
            return;
        }
        this.ticksToShot = this.shotIntervalTicks;
        this.shotsLeft--;
        this.shoot(level, target);
    }

    /** One fireball, aimed at the target's mid-height with a little scatter. */
    private void shoot(ServerLevel level, LivingEntity target) {
        Vec3 from = this.mob.getEyePosition();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 aim = to.subtract(from).normalize();
        if (this.spread > 0.0D) {
            aim = aim.add(
                    (this.mob.getRandom().nextDouble() - 0.5D) * this.spread,
                    (this.mob.getRandom().nextDouble() - 0.5D) * this.spread,
                    (this.mob.getRandom().nextDouble() - 0.5D) * this.spread).normalize();
        }
        SmallFireball fireball = new SmallFireball(level, this.mob, aim);
        // Start it clear of the shooter's own hitbox, or a wide creature shoots itself in the chest.
        fireball.setPos(from.x() + aim.x(), from.y() + aim.y() * 0.5D, from.z() + aim.z());
        level.addFreshEntity(fireball);
        this.mob.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
    }
}
