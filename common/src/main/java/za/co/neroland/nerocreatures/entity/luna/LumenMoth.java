package za.co.neroland.nerocreatures.entity.luna;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.Burrower;
import za.co.neroland.nerocreatures.entity.ai.shared.Ambusher;
import za.co.neroland.nerocreatures.entity.ai.shared.EcosystemTags;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdMember;
import za.co.neroland.nerocreatures.entity.ai.shared.PreyFleeGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.world.CreatureGriefing;

/**
 * <b>Lumen Moth</b> (Luna, tameable) — a living lantern.
 *
 * <h2>Ability: living lantern (decision 006, key {@code lumenMothLight})</h2>
 *
 * <p>A tamed moth following its owner keeps <b>one</b> {@code minecraft:light} block (level 12) lit
 * in the air at its owner's head (else feet, else the moth's own block) while it is dark there. The
 * light moves with the owner, is placed in <b>air only</b> through {@link CreatureGriefing#mayAlter}
 * ({@code PLACE}), and is removed when it moves, when the moth sits, is released, dies, is discarded
 * or changes dimension. Its position is saved in the moth's NBT so a light left in an unloaded chunk
 * is cleaned up when the moth loads again. Denied: glow particles and Night Vision for the owner
 * within 8 blocks instead. Removing its own light is a restore, not a world change, and is not gated.
 *
 * <h2>Use: reveal</h2>
 *
 * <p>Every perk tick (40 ticks, owner within 8 blocks) a tamed moth outlines hidden creatures within
 * 10 blocks (Keen: 12.5) with Glowing: burrowed, lurking, invisible, or in
 * {@code #nerocreatures:lumen_reveals}. Never players; at most 8 per pulse. The {@code ability} pose
 * (30 ticks) plays and the glow is applied at its tick 10.
 *
 * <h2>Wild</h2>
 *
 * <p>Wild moths gather at light after dark ({@link MothGatherGoal}), flee predators as prey
 * ({@code #nerocreatures:prey/moths}; Echo Shrikes hunt them), and in the overworld despawn in
 * daylight with no player within 32 blocks (decision 005). Perch / sit is the held {@code rest}
 * pose. Breeds from glow berries into an egg (decision 003).
 *
 * <p>No player identity is stored or logged; ownership is vanilla's owner field.
 */
public class LumenMoth extends TameableCreature implements HerdMember
        //? if <26.2 {
        /*, net.minecraft.world.entity.animal.FlyingAnimal
        *///?}
{

    /** Entity-type tag of creatures a moth reveals beyond the hidden/invisible ones. */
    public static final TagKey<EntityType<?>> REVEALS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "lumen_reveals"));

    private static final String LANTERN_KEY = "nerocreatures:lantern";
    private static final int PULSE_POSE_TICKS = 30;
    private static final int PULSE_AT_TICK = 10;
    private static final int NIGHT_VISION_TICKS = 300;
    private static final int DAY_CHECK_TICKS = 200;

    /** The light this moth placed, or null. Saved, so an unloaded light is cleaned up on load. */
    @Nullable
    private BlockPos lanternPos;
    /** The last placement was refused by the gate: the owner gets the fallback. Not saved. */
    private boolean lanternDenied;
    /** Game time of a scheduled reveal (the pose's tick 10), or {@link Long#MIN_VALUE}. */
    private long pulseAt = Long.MIN_VALUE;

    private long alarmUntil = Long.MIN_VALUE;
    private Vec3 alarmThreat = Vec3.ZERO;

    public LumenMoth(EntityType<? extends LumenMoth> type, Level level) {
        super(type, level, CreatureTier.LESSER);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.GRAVITY, 0.02D)
                .add(Attributes.SAFE_FALL_DISTANCE, 256.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(4, new PreyFleeGoal(this, EcosystemTags.PREDATORS, 8.0D, 12.0D, 30, 80, 1.5D));
        this.goalSelector.addGoal(6, new MothGatherGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
    }

    /**
     * Flight feeds the vanilla walk animation (including vertical motion), so the flutter clip plays:
     * 26.2+ asks {@code omnidirectionalAirMover()}, older lines check {@code instanceof FlyingAnimal}.
     */
    //? if >=26.2 {
    @Override
    protected boolean omnidirectionalAirMover() {
        return !this.onGround();
    }
    //?} else {
    /*@Override
    public boolean isFlying() {
        return !this.onGround();
    }
    *///?}

    @Override
    public boolean breedable() {
        return true;
    }

    @Override
    public Item tamingFood() {
        return Items.GLOW_BERRIES;
    }

    // --- lantern ----------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) {
            return;
        }
        if ((this.tickCount + this.getId()) % LanternRules.UPDATE_INTERVAL == 0) {
            this.updateLantern(level);
        }
        if (this.pulseAt != Long.MIN_VALUE && level.getGameTime() >= this.pulseAt) {
            this.pulseAt = Long.MIN_VALUE;
            this.reveal(level, true);
        }
        // Perch / sit is the held rest pose (Art-Luna).
        if (this.isOrderedToSit()) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (this.visuals().pose() == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
        if (this.tickCount % DAY_CHECK_TICKS == 0 && !this.isNoAi() && MothRules.despawnAtDay(this.isTame(),
                this.isLeashed(), this.isPersistenceRequired(), level.dimension() == Level.OVERWORLD,
                !level.isDarkOutside(), level.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(),
                        MothRules.DAY_DESPAWN_PLAYER_RADIUS))) {
            this.discard();
        }
    }

    @Nullable
    private Player lanternOwner() {
        if (!this.isTame() || this.isOrderedToSit() || this.isNoAi()) {
            return null;
        }
        LivingEntity owner = this.getOwner();
        if (owner instanceof Player player && player.isAlive() && !player.isSpectator()
                && player.level() == this.level()
                && this.distanceToSqr(player) <= LanternRules.OWNER_RANGE * LanternRules.OWNER_RANGE) {
            return player;
        }
        return null;
    }

    private void updateLantern(ServerLevel level) {
        if (this.lanternPos != null && !isMothLight(level, this.lanternPos)) {
            this.lanternPos = null; // gone (replaced, or never ours after a load): forget it
        }
        Player owner = this.lanternOwner();
        BlockPos target = null;
        if (owner != null) {
            BlockPos feet = owner.blockPosition();
            BlockPos head = feet.above();
            BlockPos self = this.blockPosition();
            int pick = LanternRules.chooseTarget(this.free(level, head), this.free(level, feet), this.free(level, self));
            target = pick == 0 ? head : pick == 1 ? feet : pick == 2 ? self : null;
            if (target != null && !level.isDarkOutside() && level.canSeeSky(target)) {
                target = null; // daylight in the open: no lantern needed
            }
        }
        LanternRules.Action action = LanternRules.decide(target != null, this.lanternPos != null,
                !Objects.equals(target, this.lanternPos));
        switch (action) {
            case NONE, KEEP -> {
                if (owner == null) {
                    this.lanternDenied = false;
                }
            }
            case CLEAR -> {
                this.clearLantern(level);
                this.lanternDenied = false;
            }
            case MOVE -> {
                BlockState light = lightState();
                boolean allowed = CreatureGriefing.mayAlter(level, this, target,
                        NeroCreaturesConfig.LUMEN_MOTH_LIGHT, CreatureGriefing.Change.PLACE, light);
                this.clearLantern(level);
                if (target != null && LanternRules.outcome(allowed) == LanternRules.Outcome.PLACE) {
                    level.setBlockAndUpdate(target, light);
                    this.lanternPos = target.immutable();
                    this.lanternDenied = false;
                } else {
                    this.lanternDenied = true;
                }
            }
        }
    }

    /** Air, or already this moth's light. Never water or replaceables. */
    private boolean free(ServerLevel level, BlockPos pos) {
        return pos.equals(this.lanternPos) || (level.isLoaded(pos) && level.getBlockState(pos).isAir());
    }

    private static BlockState lightState() {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LanternRules.LIGHT_LEVEL);
    }

    private static boolean isMothLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return LanternRules.isMothLight(state.is(Blocks.LIGHT), state.is(Blocks.LIGHT) ? state.getValue(LightBlock.LEVEL) : -1);
    }

    /** Removes this moth's light (a restore; keeps water if the light was waterlogged). */
    private void clearLantern(ServerLevel level) {
        BlockPos pos = this.lanternPos;
        this.lanternPos = null;
        if (pos == null || !isMothLight(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        boolean wet = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
        level.setBlockAndUpdate(pos, wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            this.clearLantern(level);
        }
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Unloading keeps the light and its saved position; the next load cleans it up.
        if (this.level() instanceof ServerLevel level && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            this.clearLantern(level);
        }
        super.remove(reason);
    }

    @Override
    protected void releaseToWild(boolean notifyOwner) {
        super.releaseToWild(notifyOwner);
        if (this.level() instanceof ServerLevel level) {
            this.clearLantern(level);
        }
    }

    // --- reveal and fallback (perk: every 40 ticks, owner within 8 blocks) -------

    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
        if (this.pulseAt == Long.MIN_VALUE && this.reveal(level, false) > 0) {
            this.visuals().playPose(VisualPose.ABILITY, PULSE_POSE_TICKS);
            this.pulseAt = level.getGameTime() + PULSE_AT_TICK;
        }
        if (this.lanternDenied) {
            owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, true));
            level.sendParticles(ParticleTypes.GLOW, this.getX(), this.getY() + 0.3D, this.getZ(),
                    6, 0.4D, 0.3D, 0.4D, 0.0D);
        }
    }

    /** Counts (and with {@code apply}, outlines) up to 8 hidden creatures in range. */
    private int reveal(ServerLevel level, boolean apply) {
        double radius = RevealRules.radius(TraitEffects.abilityRangeMultiplier(this.traits(), TraitPool.get()));
        AABB box = this.getBoundingBox().inflate(radius);
        List<LivingEntity> found = new ArrayList<>(RevealRules.MAX_REVEALED_PER_PULSE);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), box, e -> e != this && e.isAlive()
                && RevealRules.shouldReveal(e instanceof Player, e.isInvisible(),
                        e instanceof Burrower burrower && burrower.isBurrowed(),
                        e instanceof Ambusher ambusher && ambusher.isLurking(),
                        e.getType().builtInRegistryHolder().is(REVEALS)),
                found, RevealRules.MAX_REVEALED_PER_PULSE);
        int count = RevealRules.take(found.size());
        if (apply) {
            for (int i = 0; i < count; i++) {
                found.get(i).addEffect(new MobEffectInstance(MobEffects.GLOWING, RevealRules.GLOW_TICKS, 0, true, false));
            }
        }
        return count;
    }

    // --- herd alarm (prey) --------------------------------------------------------

    @Override
    public void raiseAlarm(Vec3 threat, long untilGameTime) {
        this.alarmThreat = threat;
        this.alarmUntil = untilGameTime;
    }

    @Override
    public long alarmUntil() {
        return this.alarmUntil;
    }

    @Override
    public Vec3 alarmThreat() {
        return this.alarmThreat;
    }

    // --- sounds (mapped vanilla until Stage 5 ships the family set) ---------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    // --- persistence ----------------------------------------------------------------

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
        if (this.lanternPos != null) {
            output.putLong(LANTERN_KEY, this.lanternPos.asLong());
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
        long stored = input.getLongOr(LANTERN_KEY, Long.MIN_VALUE);
        this.lanternPos = stored == Long.MIN_VALUE ? null : BlockPos.of(stored);
    }
}
