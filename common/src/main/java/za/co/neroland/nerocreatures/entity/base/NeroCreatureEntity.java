package za.co.neroland.nerocreatures.entity.base;

import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * Shared base for every NeroCreatures mob. It exists so that a concrete creature only has to
 * describe what makes it different — its goals, its model, its loot — and inherits the four things
 * every creature in this mod must get right:
 *
 * <ol>
 *   <li><b>Tier-driven attributes.</b> {@link #createCreatureAttributes(CreatureTier)} turns a
 *       {@link CreatureTier} into the standard attribute block, so power levels stay comparable
 *       across the roster.</li>
 *   <li><b>Config scaling.</b> {@code hostileAggressionMultiplier} is applied to attack damage and
 *       follow range once, at spawn, rather than being read every tick.</li>
 *   <li><b>Despawn rules.</b> Naturally spawned creatures are ordinary despawn candidates; anything
 *       summoned, tamed or placed by a structure is persistent. This is what keeps the population
 *       caps from silently filling up with mobs nobody can find.</li>
 *   <li><b>Cap accounting.</b> Being a {@code NeroCreatureEntity} is what makes a mob visible to
 *       {@link CreatureCensus}, which the spawn engine consults before placing anything — so a
 *       creature that skips this base silently escapes the population caps.</li>
 * </ol>
 *
 * <p>The base extends {@link PathfinderMob} rather than {@code Monster} on purpose: the roster
 * contains hostiles, a neutral golem, humanoids and tameables. Hostile subclasses implement
 * {@code Enemy} themselves and pass {@code MobCategory.MONSTER} when building their
 * {@link EntityType}.
 *
 * <p>Server-authoritative: nothing here reads or writes client state, and no player identity is
 * stored on the entity (POPIA/GDPR). Ownership, when it arrives, lives in a saved-data store keyed
 * by game UUID rather than in entity NBT.
 */
public abstract class NeroCreatureEntity extends PathfinderMob {

    private final CreatureTier tier;

    /**
     * The event wave this creature was spawned as part of, or {@code null} for everything that was
     * not. Set only by {@code spawn/InvasionBudget}; see that class for why it exists and why it is
     * persisted rather than kept in memory.
     */
    @Nullable
    private UUID waveId;

    protected NeroCreatureEntity(EntityType<? extends NeroCreatureEntity> type, Level level, CreatureTier tier) {
        super(type, level);
        this.tier = tier;
        this.xpReward = tier.experienceReward();
    }

    /** The power band this creature was built for. */
    public final CreatureTier tier() {
        return this.tier;
    }

    /**
     * The event wave this creature belongs to, or {@code null} if it is an ordinary spawn.
     *
     * <p>This is a <b>wave</b> id, not a player id and not an event-participant id: it identifies a
     * batch of mobs so that batch can be cleaned up again. Nothing about a player is derivable from
     * it (POPIA/GDPR).
     */
    @Nullable
    public final UUID waveId() {
        return this.waveId;
    }

    /** Marks (or unmarks) this creature as part of an event wave. */
    public final void setWaveId(@Nullable UUID waveId) {
        this.waveId = waveId;
    }

    /**
     * The standard attribute block for a tier. Concrete creatures call this and then override the
     * one or two attributes that make them distinct (armour on a golem, speed on an ambusher).
     * Every {@code LivingEntity} type MUST have attributes registered through Core's
     * {@code EntityRegistrationSupport}, or it crashes the first time it spawns.
     */
    public static AttributeSupplier.Builder createCreatureAttributes(CreatureTier tier) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, tier.baseHealth())
                .add(Attributes.ATTACK_DAMAGE, tier.baseAttackDamage())
                .add(Attributes.MOVEMENT_SPEED, tier.baseMovementSpeed())
                .add(Attributes.FOLLOW_RANGE, tier.baseFollowRange());
    }

    /**
     * Applies {@code hostileAggressionMultiplier} to this creature's aggression-facing attributes.
     * Called once from {@link #finalizeSpawn}; deliberately not a per-tick read, so a config reload
     * affects newly spawned creatures rather than retro-fitting every loaded mob.
     */
    protected void applyConfigScaling() {
        double aggression = NeroCreaturesConfig.HOSTILE_AGGRESSION_MULTIPLIER.get();
        if (aggression == 1.0D) {
            return;
        }
        scaleAttribute(Attributes.FOLLOW_RANGE, aggression);
        scaleAttribute(Attributes.ATTACK_DAMAGE, aggression);
    }

    private void scaleAttribute(Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.applyConfigScaling();
        if (spawnReason == EntitySpawnReason.STRUCTURE || spawnReason == EntitySpawnReason.EVENT) {
            // Placed deliberately by a structure or an event: it must still be there when a player
            // arrives, so it is never a despawn candidate.
            this.setPersistenceRequired();
        }
        return result;
    }

    /**
     * Naturally spawned creatures despawn like vanilla monsters. Anything the game or a player put
     * here on purpose ({@code persistenceRequired}) stays. Subclasses that gain an owner (pets,
     * drones) override {@link #requiresCustomPersistence()} instead of this method.
     *
     * <p>This is half of the population-cap story: {@link CreatureCensus} counts what is loaded,
     * and vanilla despawning is what lets that number come back down again.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSqr) {
        return !this.isPersistenceRequired() && !this.requiresCustomPersistence();
    }

    // --- persistence --------------------------------------------------------

    /**
     * The wave marker is persisted deliberately. An invasion that was running when the server went
     * down would otherwise leave its mobs behind with nothing left to identify them by, and
     * {@code spawn/InvasionBudget#sweep} exists precisely to clear that up.
     */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.waveId != null) {
            output.putString("WaveId", this.waveId.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String stored = input.getStringOr("WaveId", "");
        if (stored.isEmpty()) {
            this.waveId = null;
            return;
        }
        try {
            this.waveId = UUID.fromString(stored);
        } catch (IllegalArgumentException ignored) {
            // A malformed marker means this creature simply is not in a wave any more; a bad string
            // must never fail an entity load.
            this.waveId = null;
        }
    }
}
