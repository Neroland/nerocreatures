package za.co.neroland.nerocreatures.entity.greenxertz;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureGriefing;

/**
 * <b>Spore Drifter</b> (Greenxertz, ambient, tameable) — a translucent umbrella canopy with six
 * hanging tendrils that floats over crops.
 *
 * <h2>Ability: growth ticks (decision 006, key {@code sporeDrifterGrowth})</h2>
 *
 * <p>Every {@value DrifterRules#GROWTH_INTERVAL} ticks (stretched by {@link DrifterRules#interval}
 * when other drifters share the area) an adult picks one column of the 3 x 3 below it and looks down
 * at most {@value DrifterRules#MAX_DEPTH} blocks for the first non-air block. If that block is in
 * {@code #minecraft:crops} and {@link CreatureGriefing#mayAlter} allows a {@code REPLACE}, it gets one
 * extra vanilla random tick (a falling spore strand, and a brighter puff where it lands; the
 * {@code ability} pose plays). Denied: the strand alone. Wild drifters drift to crops on their own
 * ({@link DriftOverCropsGoal}); a tamed or wild one on a lead (fence knot) is a stationary farm booster.
 *
 * <h2>Use: bottling</h2>
 *
 * <p>A glass bottle on an adult gives one {@code drifter_spores} (bone-meal-like fertiliser), then it
 * refills for 5 minutes (saved). Wild: anyone; tamed: its owner only.
 *
 * <p>Tamed with bone meal, breeds from bone meal into a spore-pod egg (decision 003). Perch on a
 * lead or sitting is the held {@code rest} pose. No player identity is stored or logged.
 */
public class SporeDrifter extends TameableCreature
        //? if <26.2 {
        /*implements net.minecraft.world.entity.animal.FlyingAnimal
        *///?}
{

    private static final Identifier DRIFTER_SPORES = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "drifter_spores");
    private static final String BOTTLE_KEY = "nerocreatures:bottle_ready";
    private static final int POSE_CHECK_TICKS = 10;

    /** Game time it may be bottled again. Saved. */
    private long bottleReadyAt = Long.MIN_VALUE;
    /** Game time of the next growth pulse. Not saved (re-phased on load). */
    private long nextPulse = Long.MIN_VALUE;

    public SporeDrifter(EntityType<? extends SporeDrifter> type, Level level) {
        super(type, level, CreatureTier.LESSER);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FLYING_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.SAFE_FALL_DISTANCE, 256.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new DriftOverCropsGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 0.8D));
    }

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
        return Items.BONE_MEAL;
    }

    /** The drifter's use is its growth ticks and its spores, not a perk on the owner. */
    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
    }

    // --- growth ticks ------------------------------------------------------------

    //? if >=1.21.2 {
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) this.level();
    *///?}
        if (this.tickCount % POSE_CHECK_TICKS == 0) {
            this.updateRestPose();
        }
        long now = level.getGameTime();
        if (this.nextPulse == Long.MIN_VALUE) {
            this.nextPulse = now + Math.floorMod(this.getId(), DrifterRules.GROWTH_INTERVAL);
        }
        if (now < this.nextPulse || this.isBaby() || this.farTick().skip(this)) {
            return;
        }
        this.nextPulse = now + DrifterRules.interval(this.neighbours(level));
        this.pulse(level);
    }

    /** Other drifters sharing this one's area (bounded query). */
    private int neighbours(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(DrifterRules.AREA_RADIUS);
        List<SporeDrifter> found = new ArrayList<>(DrifterRules.MAX_COUNTED);
        level.getEntities(EntityTypeTest.forClass(SporeDrifter.class), box,
                e -> e != this && e.isAlive() && !e.isBaby(), found, DrifterRules.MAX_COUNTED);
        return found.size();
    }

    private void pulse(ServerLevel level) {
        BlockPos crop = this.findCrop(level);
        boolean allowed = crop != null && CreatureGriefing.mayAlter(level, this, crop,
                NeroCreaturesConfig.SPORE_DRIFTER_GROWTH, CreatureGriefing.Change.REPLACE);
        DrifterRules.Growth growth = DrifterRules.growth(crop != null, allowed);
        if (growth == DrifterRules.Growth.NONE) {
            return;
        }
        this.visuals().playPose(VisualPose.ABILITY, DrifterRules.GROWTH_POSE_TICKS);
        this.strand(level, crop);
        if (growth == DrifterRules.Growth.TICK) {
            BlockState state = level.getBlockState(crop);
            if (state.is(BlockTags.CROPS)) {
                state.randomTick(level, crop, this.random);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, crop.getX() + 0.5D, crop.getY() + 0.5D,
                        crop.getZ() + 0.5D, 4, 0.3D, 0.2D, 0.3D, 0.0D);
            }
        }
    }

    /** One column of the footprint, first non-air block down; a crop or null. Never loads a chunk. */
    @Nullable
    private BlockPos findCrop(ServerLevel level) {
        int dx = this.random.nextInt(DrifterRules.FOOTPRINT * 2 + 1) - DrifterRules.FOOTPRINT;
        int dz = this.random.nextInt(DrifterRules.FOOTPRINT * 2 + 1) - DrifterRules.FOOTPRINT;
        BlockPos.MutableBlockPos pos = this.blockPosition().offset(dx, 0, dz).mutable();
        for (int i = 0; i <= DrifterRules.MAX_DEPTH; i++) {
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                return state.is(BlockTags.CROPS) ? pos.immutable() : null;
            }
            pos.move(0, -1, 0);
        }
        return null;
    }

    /** The falling spore strand from canopy to crop (decision 007 readability fix). */
    private void strand(ServerLevel level, BlockPos crop) {
        double top = this.getY();
        double bottom = crop.getY() + 1.0D;
        int points = (int) Math.max(1, Math.min(8, top - bottom));
        for (int i = 0; i < points; i++) {
            double y = top - (top - bottom) * i / points;
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, crop.getX() + 0.5D, y, crop.getZ() + 0.5D,
                    1, 0.05D, 0.0D, 0.05D, 0.0D);
        }
    }

    /** Perch on a lead or sitting: the held {@code rest} pose; never over a running timed pose. */
    private void updateRestPose() {
        boolean perch = this.isLeashed() || this.isOrderedToSit();
        VisualPose pose = this.visuals().pose();
        if (perch && pose == VisualPose.IDLE) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!perch && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    // --- bottling ------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GLASS_BOTTLE)) {
            return super.mobInteract(player, hand);
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return this.isBaby() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        Item spores = BuiltInRegistries.ITEM.getValue(DRIFTER_SPORES);
        if (spores == Items.AIR || !DrifterRules.mayBottle(this.isBaby(), this.isTame(), this.isOwnedBy(player),
                level.getGameTime(), this.bottleReadyAt)) {
            return InteractionResult.PASS;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(spores)));
        this.bottleReadyAt = DrifterRules.nextBottle(level.getGameTime());
        level.playSound(null, this.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, this.getX(), this.getY() + 0.4D, this.getZ(),
                8, 0.3D, 0.2D, 0.3D, 0.0D);
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- sounds (mapped vanilla until the sound pass) -----------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }

    // --- persistence ------------------------------------------------------------------

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
        if (this.bottleReadyAt != Long.MIN_VALUE) {
            output.putLong(BOTTLE_KEY, this.bottleReadyAt);
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
        this.bottleReadyAt = input.getLongOr(BOTTLE_KEY, Long.MIN_VALUE);
    }
}
