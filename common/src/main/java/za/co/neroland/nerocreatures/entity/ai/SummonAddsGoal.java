package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * Calls in reinforcements — a wave of smaller creatures around the summoner, on a cooldown.
 *
 * <h2>The budget is the whole point</h2>
 *
 * <p>Exactly as with {@link SplitOnDamageBehaviour}, an unbounded summon is a server-killer: a boss
 * that adds three mobs every fifteen seconds and is fought for five minutes has added sixty. Every
 * wave here is bounded four times over:
 *
 * <ol>
 *   <li><b>Per wave.</b> At most {@code perWave} creatures, itself clamped to
 *       {@value #MAX_PER_WAVE}.</li>
 *   <li><b>Concurrency.</b> The wave is clipped so that no more than {@code maxConcurrent} of the
 *       summoned type are alive nearby — so killing the adds is what lets the boss make more, and
 *       ignoring them is what stops it.</li>
 *   <li><b>Population caps.</b> {@code maxCreaturesPerChunk} and {@code maxCreaturesPerDimension}
 *       are checked per creature, at placement time, exactly like the spawn engine does.</li>
 *   <li><b>Cooldown.</b> One wave per {@code cooldownTicks}, and the goal does no work at all in
 *       between.</li>
 * </ol>
 *
 * <h2>Cost profile</h2>
 *
 * <ul>
 *   <li>Between waves, {@link #canUse()} is a counter decrement and a cached target read.</li>
 *   <li>A wave is one bounded AABB count of the existing adds plus at most {@code perWave} entity
 *       creations. It happens once per cooldown, never per tick — {@link #tick()} runs once and the
 *       goal stops.</li>
 *   <li>No pathfinding, no line-of-sight tests, no per-tick scanning. The goal takes no control
 *       flags at all, so summoning never interrupts the summoner's own movement or attacks.</li>
 * </ul>
 *
 * <p>Adds are spawned with {@code EntitySpawnReason.EVENT}, which the shared creature base treats as
 * "placed on purpose" — so a wave does not quietly despawn mid-fight.
 */
public class SummonAddsGoal extends Goal {

    /** Hard ceiling on one wave, whatever a caller asks for. */
    private static final int MAX_PER_WAVE = 6;

    /** How far around the summoner adds appear, in blocks. */
    private static final int SCATTER_RADIUS = 3;

    /** Radius within which existing adds count towards {@code maxConcurrent}, in blocks. */
    private static final double CONCURRENCY_RADIUS = 24.0D;

    protected final Mob mob;

    private final Supplier<? extends EntityType<? extends Mob>> type;
    private final int perWave;
    private final int maxConcurrent;
    private final int cooldownTicks;

    @Nullable
    private final Consumer<Mob> initialiser;

    private int ticksToReady;
    private boolean summonedThisRun;

    /**
     * @param mob           the summoner
     * @param type          the creature to summon, as a supplier because the type may not exist yet
     *                      when the goal is declared
     * @param perWave       how many to try per wave, clamped to {@value #MAX_PER_WAVE}
     * @param maxConcurrent how many of this type may be alive within
     *                      {@value #CONCURRENCY_RADIUS} blocks before the goal stops summoning
     * @param cooldownTicks ticks between waves
     * @param initialiser   applied to each add before it joins the level — set a size, a kit, a
     *                      target; may be {@code null}
     */
    public SummonAddsGoal(Mob mob, Supplier<? extends EntityType<? extends Mob>> type, int perWave,
            int maxConcurrent, int cooldownTicks, @Nullable Consumer<Mob> initialiser) {
        this.mob = mob;
        this.type = type;
        this.perWave = Math.clamp(perWave, 1, MAX_PER_WAVE);
        this.maxConcurrent = Math.max(0, maxConcurrent);
        this.cooldownTicks = Math.max(40, cooldownTicks);
        this.initialiser = initialiser;
        this.ticksToReady = this.cooldownTicks / 2;
        // No control flags: calling for help must never compete with moving or attacking.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.ticksToReady > 0) {
            this.ticksToReady--;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.mob.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.summonedThisRun;
    }

    @Override
    public void start() {
        this.summonedThisRun = false;
    }

    @Override
    public void stop() {
        this.ticksToReady = this.cooldownTicks;
        this.summonedThisRun = false;
    }

    @Override
    public void tick() {
        this.summonedThisRun = true;
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        EntityType<? extends Mob> addType = this.type.get();
        if (addType == null) {
            return;
        }
        int budget = this.waveBudget(level, addType);
        int summoned = 0;
        for (int i = 0; i < budget; i++) {
            if (!CreatureCensus.dimensionHasRoom(level)) {
                break;
            }
            BlockPos pos = this.scatter();
            if (!CreatureCensus.chunkHasRoom(level, pos)) {
                continue;
            }
            Mob add = addType.create(level, EntitySpawnReason.EVENT);
            if (add == null) {
                break;
            }
            add.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            add.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
            if (this.initialiser != null) {
                this.initialiser.accept(add);
            }
            add.setTarget(this.mob.getTarget());
            if (level.addFreshEntity(add)) {
                summoned++;
            }
        }
        if (summoned > 0) {
            // A wave is a deliberate batch; the cached per-dimension count is now stale by it.
            CreatureCensus.invalidate(level);
            this.onSummon(level, summoned);
        }
    }

    /** How many may actually be summoned: the wave size, clipped by concurrency and by the caps. */
    private int waveBudget(ServerLevel level, EntityType<? extends Mob> addType) {
        AABB box = this.mob.getBoundingBox().inflate(CONCURRENCY_RADIUS);
        int alive = level.getEntitiesOfClass(Mob.class, box,
                add -> add.isAlive() && add.getType() == addType).size();
        int budget = Math.min(this.perWave, this.maxConcurrent - alive);
        int dimensionCap = NeroCreaturesConfig.MAX_CREATURES_PER_DIMENSION.get();
        budget = Math.min(budget, dimensionCap - CreatureCensus.inDimension(level));
        return Math.max(0, budget);
    }

    private BlockPos scatter() {
        int span = SCATTER_RADIUS * 2 + 1;
        return this.mob.blockPosition().offset(
                this.mob.getRandom().nextInt(span) - SCATTER_RADIUS, 0,
                this.mob.getRandom().nextInt(span) - SCATTER_RADIUS);
    }

    /** Feedback for a wave that actually arrived. Default: a puff of smoke and a call. */
    protected void onSummon(ServerLevel level, int summoned) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.mob.getX(),
                this.mob.getY() + this.mob.getBbHeight() * 0.5D, this.mob.getZ(),
                20, 1.5D, 0.5D, 1.5D, 0.02D);
        this.mob.playSound(SoundEvents.RAVAGER_ROAR, 1.2F, 1.2F);
    }
}
