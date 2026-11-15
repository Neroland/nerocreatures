package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Hide in suitable ground, wait, erupt on whoever steps over.
 *
 * <ul>
 *   <li><b>Hide:</b> with no target and the cooldown spent, if {@code hideIn} accepts the block at
 *       the creature's feet or the block below, it stops and sets {@link Ambusher#setLurking}.</li>
 *   <li><b>Wait:</b> every {@code scanInterval} ticks it checks a box of {@code triggerRadius}
 *       around itself (a bounded query, at most a few entities).</li>
 *   <li><b>Erupt:</b> the first victim that {@code victimFilter} accepts and that stands over it
 *       becomes the target; {@code onErupt} applies the payload (freezing, damage). Being hurt or
 *       waiting {@code maxLurkTicks} also ends the lurk.</li>
 * </ul>
 *
 * <p>No block is ever changed: hiding is a flag, so no griefing gate is needed. {@link #stop()}
 * always clears the flag.
 *
 * @param <T> the creature
 */
public class AmbushGoal<T extends PathfinderMob & Ambusher> extends Goal {

    /** What the ambusher does to its victim when it erupts. */
    @FunctionalInterface
    public interface Eruption {
        void erupt(ServerLevel level, PathfinderMob ambusher, LivingEntity victim);
    }

    private static final int MAX_VICTIMS_SCANNED = 4;

    private final T mob;
    private final Predicate<BlockState> hideIn;
    private final Predicate<LivingEntity> victimFilter;
    private final double triggerRadius;
    private final int cooldownTicks;
    private final int maxLurkTicks;
    private final Eruption onErupt;
    private final CadenceTimer hideCadence = new CadenceTimer(20);
    private final CadenceTimer scanCadence;

    private long lastErupt = Long.MIN_VALUE;
    private long lurkStart;
    private boolean done;

    /**
     * @param mob           the ambusher
     * @param hideIn        blocks it can hide in (e.g. snow, powder snow, ice)
     * @param victimFilter  who triggers it (players not in creative, prey)
     * @param triggerRadius horizontal radius that counts as "walked over"
     * @param scanInterval  ticks between trigger checks while hidden (4 is a good default)
     * @param cooldownTicks ticks after an eruption before it may hide again
     * @param maxLurkTicks  longest single wait; 0 = unlimited
     * @param onErupt       payload
     */
    public AmbushGoal(T mob, Predicate<BlockState> hideIn, Predicate<LivingEntity> victimFilter,
            double triggerRadius, int scanInterval, int cooldownTicks, int maxLurkTicks, Eruption onErupt) {
        this.mob = mob;
        this.hideIn = hideIn;
        this.victimFilter = victimFilter;
        this.triggerRadius = triggerRadius;
        this.scanCadence = new CadenceTimer(scanInterval);
        this.cooldownTicks = cooldownTicks;
        this.maxLurkTicks = maxLurkTicks;
        this.onErupt = onErupt;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    /** Standard victim filter: survival/adventure players. */
    public static boolean survivalPlayer(LivingEntity entity) {
        return entity instanceof Player player && !player.isCreative() && !player.isSpectator();
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level) || this.mob.getTarget() != null
                || !this.mob.onGround()) {
            return false;
        }
        long now = level.getGameTime();
        if (!AmbushRules.cooldownReady(now, this.lastErupt, this.cooldownTicks)
                || !this.hideCadence.ready(now, this.mob.getId())) {
            return false;
        }
        BlockPos feet = this.mob.blockPosition();
        return this.hideIn.test(level.getBlockState(feet)) || this.hideIn.test(level.getBlockState(feet.below()));
    }

    @Override
    public void start() {
        this.done = false;
        this.lurkStart = this.mob.level().getGameTime();
        this.mob.getNavigation().stop();
        this.mob.setLurking(true);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.done && this.mob.hurtTime == 0 && this.mob.getTarget() == null
                && !AmbushRules.lurkExpired(this.mob.level().getGameTime(), this.lurkStart, this.maxLurkTicks);
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (!this.scanCadence.ready(now, this.mob.getId())) {
            return;
        }
        AABB box = this.mob.getBoundingBox().inflate(this.triggerRadius, 2.0D, this.triggerRadius);
        List<LivingEntity> found = new ArrayList<>(MAX_VICTIMS_SCANNED);
        level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class), box,
                e -> e != this.mob && e.isAlive() && this.victimFilter.test(e), found, MAX_VICTIMS_SCANNED);
        for (LivingEntity victim : found) {
            double dx = victim.getX() - this.mob.getX();
            double dz = victim.getZ() - this.mob.getZ();
            if (AmbushRules.shouldErupt(dx * dx + dz * dz, victim.getY() - this.mob.getY(), this.triggerRadius)) {
                this.done = true;
                this.lastErupt = now;
                this.mob.setLurking(false);
                this.mob.setTarget(victim);
                this.onErupt.erupt(level, this.mob, victim);
                return;
            }
        }
    }

    @Override
    public void stop() {
        this.mob.setLurking(false);
        if (this.mob.hurtTime > 0) {
            this.lastErupt = this.mob.level().getGameTime();
        }
    }
}
