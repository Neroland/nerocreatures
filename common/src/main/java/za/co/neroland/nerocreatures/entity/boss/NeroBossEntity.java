package za.co.neroland.nerocreatures.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossGoals;
import za.co.neroland.nerocreatures.boss.BossSpawns;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * Shared base for every NeroCreatures boss. It owns the plumbing between a
 * {@link BossController} and the vanilla entity lifecycle, so a concrete boss only has to describe
 * its phases, its goals and its numbers.
 *
 * <p>What a boss inherits from here:
 *
 * <ul>
 *   <li><b>The controller is wired to the lifecycle.</b> {@code finalizeSpawn} starts the fight and
 *       captures the arena anchor, {@code customServerAiStep} ticks the phase machine and the leash,
 *       {@code hurtServer} feeds contribution, {@code die} pays it out and publishes the defeat, and
 *       {@code remove} takes the boss bar off everyone's screen.</li>
 *   <li><b>A boss never despawns.</b> {@link #requiresCustomPersistence()} is always {@code true}.
 *       A boss that quietly vanished while the player went for better gear would be worse than no
 *       boss at all. The counterweight is that natural boss spawns are gated hard — see
 *       {@link BossSpawns}.</li>
 *   <li><b>Contribution is attributed honestly.</b> Damage is recorded only for what actually landed
 *       (clamped to the health the boss had), so overkill on the last hit cannot buy a share.</li>
 * </ul>
 *
 * <p>Everything is server-authoritative. The only thing a client is told about a boss beyond
 * ordinary entity sync is the boss bar, which vanilla owns. No player identity is stored on the
 * entity (POPIA/GDPR) — the fight id saved in entity data is a random per-fight id, not a person.
 */
public abstract class NeroBossEntity extends NeroCreatureEntity implements Enemy, BossGoals {

    /** The phase machine, bar, arena and contribution wiring for this boss. */
    protected final BossController controller;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring; see createController
    protected NeroBossEntity(EntityType<? extends NeroBossEntity> type, Level level, CreatureTier tier) {
        super(type, level, tier);
        this.controller = this.createController();
    }

    /**
     * Builds this boss's controller. Called from the constructor, so an implementation must only use
     * {@code this} as an identity — the subclass's own fields are not initialised yet. In practice
     * that is not a constraint: a {@link za.co.neroland.nerocreatures.boss.BossPhase} builds its
     * goals through a factory that runs later, when the phase is entered.
     */
    protected abstract BossController createController();

    /** This boss's controller — the read surface for commands, the link module and rewards. */
    public BossController bossController() {
        return this.controller;
    }

    // --- BossGoals: the controller's only reach into this entity's goal selector ---

    @Override
    public void addPhaseGoal(int priority, Goal goal) {
        this.goalSelector.addGoal(priority, goal);
    }

    @Override
    public void removePhaseGoal(Goal goal) {
        this.goalSelector.removeGoal(goal);
    }

    // --- lifecycle ----------------------------------------------------------

    /**
     * Starts the fight. The arena is anchored wherever the boss arrived — the sweep's chosen
     * position for a natural spawn, the summon position for a summoned one.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        ServerLevel serverLevel = level.getLevel();
        this.controller.onSpawn(serverLevel, this.blockPosition());
        BossSpawns.noteBossPresent(serverLevel);
        return result;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.controller.tick(level);
    }

    /**
     * Records what actually landed. The clamp matters: without it the killing blow would be recorded
     * at its raw value, so one big hit on a boss already at 1 health would outweigh the whole fight.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = this.getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            this.controller.recordDamage(level, source, Math.min(before, before - this.getHealth()));
        }
        return hurt;
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level) {
            this.controller.onDeath(level);
            BossSpawns.noteBossGone(level);
        }
    }

    /**
     * Takes the bar away whenever the boss leaves the world — killed, unloaded, {@code /kill}ed.
     * An unloaded boss keeps its fight (it is still out there and its chunk will come back); a
     * discarded one does not.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        boolean keepContribution = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION;
        this.controller.onRemoved(keepContribution);
        super.remove(reason);
    }

    /** A boss is never a despawn candidate. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** A boss is not something a crowd of mobs (or a boat) can shove out of its own arena. */
    @Override
    public boolean isPushable() {
        return false;
    }

    // --- persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.controller.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.controller.load(input);
    }

    /** The arena centre this boss belongs to, or {@code null} before its fight starts. */
    public BlockPos arenaAnchor() {
        return this.controller.anchor();
    }
}
