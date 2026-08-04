package za.co.neroland.nerocreatures.entity.mechanical;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.ai.HoldAreaGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.link.CreatureLinkEvents;
import za.co.neroland.nerocreatures.registry.ModItems;

/**
 * <b>Terraforming Drone</b> — the mod's one utility mob: an owned, deployed machine that tends the
 * patch of ground it was put down on.
 *
 * <p>It is the only creature in NeroCreatures that <b>never spawns naturally</b>. There is no spawn
 * rule, no spawn placement and no spawn egg; the only way one exists is a player placing a
 * {@code drone_shell} ({@code item/DroneShellItem}), which binds the drone to them and counts
 * against {@code maxDronesPerPlayer}. Shift-interacting with an empty hand hands the shell back and
 * removes the drone.
 *
 * <h2>The duty cycle, and why there is no energy buffer</h2>
 *
 * <p>Work happens on a <b>time-based duty cycle</b>: every {@value #DUTY_CYCLE_TICKS} ticks the
 * drone tries at most {@value #ATTEMPTS_PER_CYCLE} positions inside its work area and stops at the
 * first success. That is the whole power model, and it is a deliberate choice over Core's
 * {@code EnergyBuffer}:
 *
 * <ul>
 *   <li>Core's energy framework is <b>block-entity shaped</b> — {@code AbstractMachineBlockEntity},
 *       the {@code nerolandcore:energy} capability and {@code EnergyLookup} all address a block in a
 *       level. There is no cross-loader seam for exposing a capability on a <em>mob</em>, and
 *       inventing one here would put a NeroCreatures-specific loader surface in the one module that
 *       is supposed to stay loader-free.</li>
 *   <li>A charge that has to be topped up would make the drone a chore in a mod whose subject is
 *       creatures, not machines. The pacing that matters — "this is an assist, not a farm" — is
 *       already expressed by the cycle length.</li>
 * </ul>
 *
 * <p>If a future stage does want a real charge, the seam to add is a Core-side mob-capability
 * lookup, and this class's {@link #runDutyCycle} is the one method that would change.
 *
 * <h2>What it actually does</h2>
 *
 * <ul>
 *   <li><b>Planting assist</b> — one bone-meal-equivalent growth tick per cycle, on a random
 *       bonemealable block inside the work area. It consumes nothing: the drone is the reagent.</li>
 *   <li><b>Oxygen support</b> — {@link #runOxygenSupport} is an intentional no-op hook. NeroCreatures
 *       does not reference Nerospace or NeroAgriculture, in a manifest or in code; when the real
 *       integration lands it will arrive as Core threshold/link events, and this is where it will be
 *       answered.</li>
 * </ul>
 *
 * <p>It stays where it was put: a {@link HoldAreaGoal} walks it back whenever wandering takes it
 * outside the work radius, and it is persistent, so a deployed drone survives the chunk unloading.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> The drone stores its owner's UUID in its own entity data — the same
 * thing vanilla does for a tamed animal, declared in {@code PRIVACY.md}. It stores no names and no
 * player position history; the work anchor is the drone's own position, not the player's. Nothing
 * here logs player identity.
 */
public class TerraformingDrone extends NeroCreatureEntity implements OwnableEntity {

    /** Ticks between work attempts. Five seconds: an assist, not a farm. */
    private static final int DUTY_CYCLE_TICKS = 100;

    /** Positions tried per cycle before giving up until the next one. */
    private static final int ATTEMPTS_PER_CYCLE = 6;

    /** Horizontal reach of the work area from the deploy anchor, in blocks. */
    private static final int WORK_RADIUS = 8;

    /** Vertical reach of the work area from the deploy anchor, in blocks. */
    private static final int WORK_HEIGHT = 3;

    /** How far the drone may wander from its anchor before it is walked back, in blocks. */
    private static final double LEASH_RADIUS = 10.0D;

    /** Where the drone was deployed. The centre of its work area, and its own position — not a player's. */
    @Nullable
    private BlockPos workAnchor;

    @Nullable
    private EntityReference<LivingEntity> owner;

    private int dutyTicks;

    public TerraformingDrone(EntityType<? extends TerraformingDrone> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /**
     * Deliberately feeble: a drone is a tool, and a tool that can be broken by the wildlife it is
     * working next to is part of the deal. It has an attack damage value only because every mob does
     * — it has no attack goal and no target goal, so it never uses it.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HoldAreaGoal(this, () -> this.workAnchor, LEASH_RADIUS, 1.0D,
                () -> this.workAnchor != null));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // No target selector at all: a terraforming drone has no quarrel with anything.
    }

    // --- deployment & ownership ---------------------------------------------

    /**
     * Binds a freshly created drone to the player who placed it and fixes its work area. Call before
     * adding the entity to the level; the caller ({@code item/DroneShellItem}) is also responsible
     * for the cap check and for indexing the drone in {@link CreatureOwnershipState}.
     */
    public void deployBy(Player player, BlockPos anchor) {
        // Explicit witness: the field is an owner reference to a LivingEntity, not to a Player.
        this.owner = EntityReference.<LivingEntity>of(player);
        this.workAnchor = anchor.immutable();
        this.setPersistenceRequired();
    }

    @Override
    @Nullable
    public EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    /** The owner's game UUID, or {@code null} for an unbound drone. Never logged. */
    @Nullable
    public UUID ownerId() {
        return this.owner == null ? null : this.owner.getUUID();
    }

    /** Whether this player deployed this drone. */
    public boolean isOwnedBy(Player player) {
        UUID id = this.ownerId();
        return id != null && id.equals(player.getUUID());
    }

    /** The centre of the work area, or {@code null} if the drone was never deployed. */
    @Nullable
    public BlockPos workAnchor() {
        return this.workAnchor;
    }

    /**
     * Drops this drone's row from the ownership index. Called when the drone stops existing, by
     * whichever route — recall, death, or the erasure hook.
     */
    public void unregisterOwnership(ServerLevel level) {
        UUID id = this.ownerId();
        if (id != null) {
            CreatureOwnershipState.get(level.getServer()).remove(id, this.getUUID());
        }
    }

    /** Hands the shell back and removes the drone. */
    public void recall(ServerLevel level) {
        CreatureLinkEvents.petStateChanged(level, this.ownerId(), this, OwnedCreature.Kind.DRONE,
                CreatureLinkEvents.STATE_RECALLED);
        this.unregisterOwnership(level);
        this.spawnAtLocation(level, new ItemStack(ModItems.DRONE_SHELL.get()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.5D, this.getZ(),
                8, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, this.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.NEUTRAL, 0.5F, 1.4F);
        this.discard();
    }

    // --- interaction --------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.isOwnedBy(player)) {
            // Somebody else's machine: let their click fall through to whatever they meant by it.
            return InteractionResult.PASS;
        }
        boolean recalling = player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty();
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (recalling) {
            this.recall(level);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.nerocreatures.drone_status", WORK_RADIUS), true);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- the duty cycle -----------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.workAnchor == null || --this.dutyTicks > 0) {
            return;
        }
        this.dutyTicks = DUTY_CYCLE_TICKS;
        this.runDutyCycle(level);
    }

    /**
     * One work tick. Bounded by construction: at most {@value #ATTEMPTS_PER_CYCLE} block-state reads
     * and one growth call, once every {@value #DUTY_CYCLE_TICKS} ticks. It stops at the first
     * success, so a drone standing in a field of crops costs the same as one standing on bare rock.
     */
    private void runDutyCycle(ServerLevel level) {
        this.runPlantingAssist(level);
        this.runOxygenSupport(level);
    }

    /**
     * Bone-meal-equivalent growth on one random block in the work area. Vanilla's
     * {@link BoneMealItem#growCrop} does all the "is this a valid target" work, including a data
     * pack's own bonemealable blocks, so the drone stays correct for content it has never heard of.
     * The stack it is handed is a throwaway — nothing is consumed from anyone's inventory.
     */
    private void runPlantingAssist(ServerLevel level) {
        BlockPos anchor = this.workAnchor;
        if (anchor == null) {
            return;
        }
        for (int attempt = 0; attempt < ATTEMPTS_PER_CYCLE; attempt++) {
            BlockPos target = anchor.offset(
                    this.random.nextInt(WORK_RADIUS * 2 + 1) - WORK_RADIUS,
                    this.random.nextInt(WORK_HEIGHT * 2 + 1) - WORK_HEIGHT,
                    this.random.nextInt(WORK_RADIUS * 2 + 1) - WORK_RADIUS);
            if (level.getBlockState(target).isAir()) {
                continue;
            }
            if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, target)) {
                level.playSound(null, target, SoundEvents.BONE_MEAL_USE, SoundSource.NEUTRAL, 0.4F, 1.2F);
                return;
            }
        }
    }

    /**
     * Life-support assist — <b>intentionally a no-op today</b>, and the hook point for when it is
     * not.
     *
     * <p>Making a sealed volume breathable is Nerospace's model, and fertility beyond bone meal is
     * NeroAgriculture's; NeroCreatures references neither, in any manifest or in any import. The
     * integration this method will eventually carry arrives the reflection-free way — a Core
     * threshold or link event that a planet mod raises and this drone answers — so the method exists
     * now, with its call site wired, and does nothing until there is something to call.
     */
    @SuppressWarnings("unused") // level is the parameter the real implementation will need
    private void runOxygenSupport(ServerLevel level) {
        // Deliberately empty. See the Javadoc above before filling it in.
    }

    // --- lifecycle ----------------------------------------------------------

    /** A deployed drone belongs to somebody and must still be there when they come back. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.workAnchor != null;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            // Before unregistering: the event needs the owner the row still points at.
            CreatureLinkEvents.petDied(level, this.ownerId(), this, OwnedCreature.Kind.DRONE);
            this.unregisterOwnership(level);
        }
        super.die(source);
    }

    /** A machine: the effects that only make sense against something alive do not apply. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect().is(MobEffects.POISON)
                || effect.getEffect().is(MobEffects.WITHER)
                || effect.getEffect().is(MobEffects.HUNGER)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    // --- persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.owner != null) {
            this.owner.store(output, "Owner");
        }
        if (this.workAnchor != null) {
            output.putInt("AnchorX", this.workAnchor.getX());
            output.putInt("AnchorY", this.workAnchor.getY());
            output.putInt("AnchorZ", this.workAnchor.getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.owner = EntityReference.read(input, "Owner");
        if (input.getInt("AnchorX").isPresent()) {
            this.workAnchor = new BlockPos(input.getIntOr("AnchorX", 0), input.getIntOr("AnchorY", 0),
                    input.getIntOr("AnchorZ", 0));
        }
    }

    // --- sounds -------------------------------------------------------------
    // Mapped vanilla events; NeroCreatures ships no audio of its own.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.COPPER_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COPPER_GOLEM_DEATH;
    }
}
