package za.co.neroland.nerocreatures.entity.base;

import java.util.UUID;
import java.util.function.BiConsumer;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.budget.CreatureBudget;
import za.co.neroland.nerocreatures.budget.FarTickGate;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.shared.SoundHearing;
import za.co.neroland.nerocreatures.entity.alpha.AlphaSystem;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisuals;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.entity.wrecks.ScrapHulkAssembly;
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
public abstract class NeroCreatureEntity extends PathfinderMob implements CreatureVisualsHolder {

    /** The root's visuals ids are this class's first {@code defineId}. */
    // defineId must be called from the owning class itself: NeoForge 26.x rejects ids allocated
    // through a helper ("synced data to a foreign entity").  Keep these two direct calls.
    private static final CreatureVisuals.Ids VISUALS_IDS = new CreatureVisuals.Ids(
            SynchedEntityData.defineId(NeroCreatureEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(NeroCreatureEntity.class, EntityDataSerializers.INT));

    /**
     * Synced alpha flag: the renderer reads it for the crimson accent and darker
     * body; size comes from the vanilla {@code minecraft:scale} attribute, which syncs itself.
     */
    private static final EntityDataAccessor<Boolean> DATA_ALPHA =
            SynchedEntityData.defineId(NeroCreatureEntity.class, EntityDataSerializers.BOOLEAN);

    private final CreatureTier tier;

    /** Pose, phase, stage/variant/form and the four flags (radiant, alpha, saddled, panniers), synced. */
    private final CreatureVisuals visuals = new CreatureVisuals(this, VISUALS_IDS);

    /** "Far creatures tick slowly" gate for species' own per-tick work. */
    private final FarTickGate farTick = new FarTickGate();

    /** One-shot first-server-tick work ({@link #onFirstServerTick}) has run. Not saved. */
    private boolean firstTickDone;

    /** Which alpha modifier was rolled ({@code ""} when not an alpha or none). Server-side only. */
    private String alphaModifier = "";

    /** Optional game-event hearing for sound hunters; null for everything else. */
    @Nullable
    private SoundHearing hearing;

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        CreatureVisuals.define(builder, VISUALS_IDS);
        builder.define(DATA_ALPHA, false);
    }

    // --- visuals, budget, far-tick ----------

    @Override
    public CreatureVisuals visuals() {
        return this.visuals;
    }

    /** The far-tick gate; expensive goals and timers ask {@code farTick().skip(this)}. */
    public FarTickGate farTick() {
        return this.farTick;
    }

    /**
     * Times every creature tick for {@code /nerocreatures budget} (one boolean read outside a sample
     * window) and advances the visuals timer on the server. Species never redefine this wrapping; a
     * species that overrides {@code tick()} calls {@code super.tick()}.
     */
    @Override
    public void tick() {
        long budgetStart = CreatureBudget.begin();
        super.tick();
        if (!this.level().isClientSide()) {
            this.visuals.tick();
        }
        if (!this.firstTickDone && this.level() instanceof ServerLevel level) {
            this.firstTickDone = true;
            this.onFirstServerTick(level);
        }
        CreatureBudget.end(this, budgetStart);
    }

    /**
     * Runs once, on the first server tick after this creature was created or loaded (NoAI creatures
     * included). Default: nothing. The Terraforming Drone runs its load-time ownership check here.
     */
    protected void onFirstServerTick(ServerLevel level) {
    }

    // --- alphas (entity/alpha/AlphaSystem) -----------------------------------

    /** True if this creature spawned as an alpha. Synced; safe to read on the client. */
    public final boolean isAlpha() {
        return this.entityData.get(DATA_ALPHA);
    }

    /** The rolled alpha modifier id, or {@code ""}. Server-side only. */
    public final String alphaModifierId() {
        return this.alphaModifier;
    }

    /**
     * Sets the alpha state. Only {@link AlphaSystem} calls this; it also applies the attribute
     * modifiers, which vanilla saves with the entity.
     */
    public final void setAlphaState(boolean alpha, String modifierId) {
        this.entityData.set(DATA_ALPHA, alpha);
        // The visuals bit is the synced mirror the models read; the alpha system stays the source.
        this.visuals.setAlpha(alpha);
        this.alphaModifier = alpha && modifierId != null ? modifierId : "";
    }

    // --- sound hunting hook (entity/ai/shared/SoundHearing) -----------------

    /**
     * Installs a game-event listener for a sound-hunting creature. Call once from the constructor.
     * Vanilla then registers it with the level through {@link #updateDynamicGameEventListener}.
     */
    protected final void installHearing(SoundHearing soundHearing) {
        this.hearing = soundHearing;
    }

    /** The installed hearing, or null. */
    @Nullable
    public final SoundHearing hearing() {
        return this.hearing;
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> action) {
        if (this.hearing != null && this.level() instanceof ServerLevel serverLevel) {
            action.accept(this.hearing.dynamicListener(), serverLevel);
        }
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
        AlphaSystem.onFinalizeSpawn(this, level.getLevel(), spawnReason);
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

    /** A gallery display drops nothing (no loot, no equipment) and gives no experience. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (!za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this)) {
            super.dropAllDeathLoot(level, source);
        }
    }

    @Override
    public boolean shouldDropExperience() {
        return !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this) && super.shouldDropExperience();
    }

    /** Alphas add one guaranteed rare drop when a player kills them (see {@link AlphaSystem}). */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (killedByPlayer && this.isAlpha()) {
            AlphaSystem.dropReward(this, level);
        }
    }

    /**
     * Scrap Hulk assembly hook: a mechanical creature's death is offered to
     * {@link ScrapHulkAssembly}, which keeps a short per-dimension buffer of machine deaths and builds
     * a hulk from a cluster of them. Cheap for everything else (one tag test; it returns at once for
     * anything outside {@code #nerocreatures:mechanical}, for hulks, and with {@code scrapHulkAssembly}
     * off). Species that override this call {@code super.die}.
     */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel serverLevel) {
            // Bestiary: the kill statistic was just awarded by super.die; a met kill goal studies it.
            za.co.neroland.nerocreatures.bestiary.BestiaryProgress.noteKill(this, source.getEntity());
            ScrapHulkAssembly.onCreatureDeath(serverLevel, this);
        }
    }

    // --- persistence --------------------------------------------------------

    /**
     * The wave marker is persisted deliberately. An invasion that was running when the server went
     * down would otherwise leave its mobs behind with nothing left to identify them by, and
     * {@code spawn/InvasionBudget#sweep} exists precisely to clear that up.
     */
    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ValueOutput output = ValueOutput.of(nbt, this.registryAccess());
    *///?}
        output.putInt(CreatureVisuals.SAVE_KEY, this.visuals.persistentBits());
        if (this.waveId != null) {
            output.putString("WaveId", this.waveId.toString());
        }
        if (this.isAlpha()) {
            output.putBoolean("Alpha", true);
            output.putString("AlphaModifier", this.alphaModifier);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    //?} else {
    /*@Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ValueInput input = ValueInput.of(nbt, this.registryAccess());
    *///?}
        this.visuals.restorePersistentBits(input.getIntOr(CreatureVisuals.SAVE_KEY, 0));
        // After the visuals bits, so the alpha system re-writes the flag it owns.
        this.setAlphaState(input.getBooleanOr("Alpha", false), input.getStringOr("AlphaModifier", ""));
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
