package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * Surfaces from a burrow with a telegraphed strike — the payoff half of {@link BurrowGoal}.
 *
 * <p>The strike is deliberately readable: for {@code telegraphTicks} the creature stays under while
 * dust erupts at its position (so the player can see where it is about to come up and step away),
 * and only then does it surface and swing. A hidden instant hit would be unfair; a telegraph makes
 * it a mechanic.
 *
 * <p>With a clip ({@link #EmergeAttackGoal(PathfinderMob, double, int, int, int)}), the creature
 * plays its {@code ability} pose from the first telegraph tick, surfaces at {@code telegraphTicks}
 * and lands the bite later, at {@code biteTick}, so the hit matches the clip's key frame. The
 * surfacing ring of dust is drawn at the strike range: standing outside it is safe.
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>The telegraph is a particle packet every {@value #TELEGRAPH_PARTICLE_INTERVAL} ticks, not
 *       every tick.</li>
 *   <li>No pathfinding at all: the goal holds position and then does a single
 *       {@code doHurtTarget} on the surfacing tick.</li>
 *   <li>{@link #stop()} always clears the burrowed flag, so an interrupted emerge can never leave
 *       the creature invulnerable.</li>
 * </ul>
 *
 * @param <T> the creature type — a pathfinding mob that implements {@link Burrower}
 */
public class EmergeAttackGoal<T extends PathfinderMob & Burrower> extends Goal {

    /** Ticks between telegraph particle bursts. */
    private static final int TELEGRAPH_PARTICLE_INTERVAL = 4;

    /** Particles in the surfacing ring (one packet each, once per emerge). */
    private static final int EMERGE_RING_POINTS = 16;

    protected final T mob;

    private final double strikeRange;
    private final double strikeRangeSqr;
    private final int telegraphTicks;
    private final int biteTick;
    private final int clipTicks;

    private int ticksRunning;
    private boolean surfaced;
    private boolean struck;

    /**
     * @param mob            the burrower
     * @param strikeRange    distance (blocks) the target must be within for the strike to land
     * @param telegraphTicks how long the "it is about to come up" warning lasts
     */
    @org.jetbrains.annotations.Nullable
    private za.co.neroland.nerocreatures.sound.CreatureSound sound;

    /** The ability's own sound, played at the strike (optional; none by default). */
    public EmergeAttackGoal<T> withSound(za.co.neroland.nerocreatures.sound.CreatureSound abilitySound) {
        this.sound = abilitySound;
        return this;
    }

    public EmergeAttackGoal(T mob, double strikeRange, int telegraphTicks) {
        this(mob, strikeRange, telegraphTicks, telegraphTicks, 0);
    }

    /**
     * @param mob            the burrower
     * @param strikeRange    distance (blocks) the target must be within for the strike to land
     * @param telegraphTicks ticks under ground before surfacing
     * @param biteTick       tick the bite lands (at or after {@code telegraphTicks})
     * @param clipTicks      length of the {@code ability} clip played from the start; 0 = no pose
     */
    public EmergeAttackGoal(T mob, double strikeRange, int telegraphTicks, int biteTick, int clipTicks) {
        this.mob = mob;
        this.strikeRange = strikeRange;
        this.strikeRangeSqr = strikeRange * strikeRange;
        this.telegraphTicks = Math.max(1, telegraphTicks);
        this.biteTick = Math.max(this.telegraphTicks, biteTick);
        this.clipTicks = Math.max(0, clipTicks);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isBurrowed()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.mob.distanceToSqr(target) <= this.strikeRangeSqr;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.struck && this.mob.getTarget() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticksRunning = 0;
        this.surfaced = false;
        this.struck = false;
        this.mob.getNavigation().stop();
        if (this.clipTicks > 0 && this.mob instanceof CreatureVisualsHolder holder) {
            holder.visuals().playPose(VisualPose.ABILITY, this.clipTicks);
        }
    }

    @Override
    public void stop() {
        // Whatever happened — struck, target lost, or interrupted — the creature ends up above
        // ground. This is the single place the invulnerable state is guaranteed to be released.
        this.mob.setBurrowed(false);
        this.surfaced = false;
        this.struck = false;
    }

    @Override
    public void tick() {
        this.ticksRunning++;
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.ticksRunning < this.telegraphTicks) {
            if (this.ticksRunning % TELEGRAPH_PARTICLE_INTERVAL == 0) {
                this.onTelegraph(level);
            }
            return;
        }
        if (!this.surfaced) {
            this.surfaced = true;
            this.mob.setBurrowed(false);
            this.onEmerge(level);
        }
        if (!this.struck && this.ticksRunning >= this.biteTick) {
            this.struck = true;
            if (this.mob.distanceToSqr(target) <= this.strikeRangeSqr) {
                this.mob.doHurtTarget(level, target);
            }
        }
    }

    /** Warning feedback while still under. Default: dust puffing out of the ground. */
    protected void onTelegraph(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                8, 0.4D, 0.0D, 0.4D, 0.05D);
    }

    /** Feedback for the surfacing itself. Default: the vanilla warden emerge sound plus dust. */
    protected void onEmerge(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                30, 0.8D, 0.3D, 0.8D, 0.1D);
        // The danger ring: dust at exactly the strike range, so "outside the ring" means "safe".
        for (int i = 0; i < EMERGE_RING_POINTS; i++) {
            double angle = (Math.PI * 2.0D * i) / EMERGE_RING_POINTS;
            level.sendParticles(ParticleTypes.POOF, this.mob.getX() + Math.cos(angle) * this.strikeRange,
                    this.mob.getY() + 0.1D, this.mob.getZ() + Math.sin(angle) * this.strikeRange,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (this.sound != null) {
            this.sound.play(this.mob);
        } else {
            this.mob.playSound(SoundEvents.WARDEN_EMERGE, 1.0F, 1.0F);
        }
    }
}
