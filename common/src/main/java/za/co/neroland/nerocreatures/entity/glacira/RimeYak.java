package za.co.neroland.nerocreatures.entity.glacira;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;
import za.co.neroland.nerocreatures.entity.ai.shared.EcosystemTags;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdCohesionGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdMember;
import za.co.neroland.nerocreatures.entity.ai.shared.PreyFleeGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.world.CreatureGriefing;

/**
 * <b>Rime Yak</b> (Glacira, livestock): a haystack of frost-cream wool under wide sweeping horns.
 *
 * <ul>
 *   <li><b>Wool.</b> Shears on an adult with full wool (visuals {@code stage} 3) drop
 *       {@code rime_wool} ({@code 2 x} Bountiful rolls, capped at {@value RimeYakRules#WOOL_YIELD_CAP});
 *       the wool regrows through stages 0 (shorn, legs show) to 3 on a {@link ProductTimer}.</li>
 *   <li><b>Milk.</b> A bucket on an adult returns {@code coolant_milk}; then a cooldown.</li>
 *   <li><b>Cold aura</b> (cadence {@value RimeYakRules#AURA_INTERVAL} ticks, radius
 *       {@value RimeYakRules#AURA_RADIUS}; nothing in vanilla spoils, so there is no food-preservation
 *       part, decision 006): players inside are cooled (fire put out, and the
 *       {@link GlaciraHooks.Cooling} hook is called for them); hostile mobs are chilled, which slows
 *       them and never reaches a damaging full freeze; still water sources at the surface within
 *       {@value RimeYakRules#FREEZE_RADIUS} blocks slowly turn to {@code minecraft:frosted_ice}, which
 *       melts back by itself. The freezing goes through
 *       {@code CreatureGriefing.mayAlter(.., rimeYakFreezesWater, REPLACE, ..)}: at most
 *       {@value RimeYakRules#MAX_FREEZE_PER_PASS} block per pass and {@value RimeYakRules#MAX_LIVE_FROSTED}
 *       recent blocks per yak. Denied: the aura still cools and chills.</li>
 *   <li>Herd grazer and prey (flees predators, alarms the herd, keeps together); breeds on
 *       {@code nerocreatures:food/rime_yak} with a live birth; rests (held {@code rest}) at night.</li>
 * </ul>
 */
public class RimeYak extends LivestockCreature implements HerdMember {

    private static final String WOOL_KEY = "nerocreatures:wool";
    private static final String MILK_KEY = "nerocreatures:milk_cooldown";
    private static final int SLEEP_CHECK_TICKS = 40;
    private static final Identifier RIME_WOOL = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "rime_wool");
    private static final Identifier COOLANT_MILK = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "coolant_milk");

    /** Config hooks, bound at common init by the integrator (defaults until then). */
    private static volatile IntSupplier woolRegrowTicks = () -> RimeYakRules.DEFAULT_WOOL_REGROW_TICKS;
    private static volatile IntSupplier milkCooldownTicks = () -> RimeYakRules.DEFAULT_MILK_COOLDOWN_TICKS;

    private final ProductTimer wool;
    private final CadenceTimer auraCadence = new CadenceTimer(RimeYakRules.AURA_INTERVAL);
    /** Game times at which this yak's recent frosted-ice blocks stop counting against the cap. */
    private final Deque<Long> frosted = new ArrayDeque<>(RimeYakRules.MAX_LIVE_FROSTED);
    private int milkCooldown;
    private int growthClock;

    private long alarmUntil = Long.MIN_VALUE;
    private Vec3 alarmThreat = Vec3.ZERO;

    public RimeYak(EntityType<? extends RimeYak> type, Level level) {
        super(type, level, CreatureTier.COMMON);
        this.wool = new ProductTimer(RimeYakRules.woolRegrowTicks(woolRegrowTicks.getAsInt()),
                RimeYakRules.WOOL_STAGES, RimeYakRules.WOOL_YIELD_CAP);
        // A fresh yak (spawned or born) arrives in full wool; a loaded one restores its saved growth.
        this.wool.setProgress(this.wool.regrowTicks());
        this.syncVisuals();
    }

    /** Binds the wool-regrow and milk-cooldown config keys (integration: common init). */
    public static void configure(IntSupplier regrow, IntSupplier milkCooldown) {
        if (regrow != null) {
            woolRegrowTicks = regrow;
        }
        if (milkCooldown != null) {
            milkCooldownTicks = milkCooldown;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivestockCreature.createPetAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PreyFleeGoal(this, EcosystemTags.PREDATORS, 10.0D, 14.0D, 30, 100, 1.5D));
        this.goalSelector.addGoal(5, new HerdCohesionGoal(this, 18.0D, 7.0D, 60, 1.0D));
    }

    // --- products and aura -------------------------------------------------------------------

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
        if (this.tickCount % SLEEP_CHECK_TICKS == 0) {
            this.updateSleep(level);
        }
        if (++this.growthClock >= RimeYakRules.GROWTH_STEP) {
            this.growthClock = 0;
            if (!this.isBaby()) {
                this.wool.advance(RimeYakRules.GROWTH_STEP);
            }
            this.milkCooldown = RimeYakRules.coolDown(this.milkCooldown, RimeYakRules.GROWTH_STEP);
            this.syncVisuals();
        }
        if (this.farTick().skip(this) || !this.auraCadence.ready(level.getGameTime(), this.getId())) {
            return;
        }
        this.auraPass(level);
    }

    /** One aura pass: cool players, chill hostiles, then (gated, capped) freeze one water source. */
    private void auraPass(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(RimeYakRules.AURA_RADIUS, 2.0D, RimeYakRules.AURA_RADIUS);
        List<LivingEntity> found = new ArrayList<>(RimeYakRules.MAX_AURA_TARGETS);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), box,
                e -> e != this && e.isAlive() && !(e instanceof RimeYak) && !(e instanceof FrostLurker),
                found, RimeYakRules.MAX_AURA_TARGETS);
        for (LivingEntity entity : found) {
            if (!RimeYakRules.inAura(this.distanceToSqr(entity))) {
                continue;
            }
            if (entity.isOnFire()) {
                entity.clearFire();
            }
            if (entity instanceof ServerPlayer player) {
                if (!player.isSpectator()) {
                    GlaciraHooks.cooling().cool(player, RimeYakRules.AURA_INTERVAL);
                }
            } else if (entity instanceof Enemy && entity.canFreeze()) {
                entity.setTicksFrozen(RimeYakRules.chillTicks(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze()));
            }
        }
        this.freezeWater(level);
        long now = level.getGameTime();
        if (now % RimeYakRules.RING_EVERY_TICKS < RimeYakRules.AURA_INTERVAL) {
            this.auraRing(level);
        }
        if (this.random.nextInt(RimeYakRules.BREATH_EVERY_TICKS / RimeYakRules.AURA_INTERVAL) == 0) {
            Vec3 look = this.getLookAngle();
            level.sendParticles(ParticleTypes.CLOUD, this.getX() + look.x * 0.9D, this.getEyeY() - 0.1D,
                    this.getZ() + look.z * 0.9D, 2, 0.05D, 0.02D, 0.05D, 0.01D);
        }
    }

    /** A faint ring of frost motes at the aura edge (vanilla snowflakes until {@code rime_mote} lands). */
    private void auraRing(ServerLevel level) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0D * i / points;
            level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX() + Math.cos(a) * RimeYakRules.AURA_RADIUS,
                    this.getY() + 0.3D, this.getZ() + Math.sin(a) * RimeYakRules.AURA_RADIUS, 1, 0.0D, 0.05D, 0.0D, 0.0D);
        }
    }

    /** Freezes at most one still surface water source near the yak, through the griefing gate. */
    private void freezeWater(ServerLevel level) {
        if (!NeroCreaturesConfig.RIME_YAK_FREEZES_WATER.get()) {
            return;
        }
        long now = level.getGameTime();
        while (!this.frosted.isEmpty() && this.frosted.peekFirst() <= now) {
            this.frosted.pollFirst();
        }
        if (RimeYakRules.freezeBudget(true, 0, this.frosted.size()) <= 0) {
            return;
        }
        BlockPos feet = this.blockPosition();
        int r = RimeYakRules.FREEZE_RADIUS;
        int side = r * 2 + 1;
        int start = this.random.nextInt(side * side);
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        for (int dy = -1; dy <= 0; dy++) {
            for (int i = 0; i < side * side; i++) {
                int cell = (start + i) % (side * side);
                BlockPos pos = feet.offset(cell % side - r, dy, cell / side - r);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                boolean source = state.is(Blocks.WATER) && state.getFluidState().isSource();
                if (!source || !RimeYakRules.freezable(true, level.getBlockState(pos.above()).isAir(), occupied(level, pos))) {
                    continue;
                }
                boolean allowed = CreatureGriefing.mayAlter(level, this, pos, NeroCreaturesConfig.RIME_YAK_FREEZES_WATER,
                        CreatureGriefing.Change.REPLACE, ice);
                if (RimeYakRules.freezeBudget(allowed, 0, this.frosted.size()) <= 0) {
                    return; // denied: the aura still cooled and chilled; no block changes this pass
                }
                level.setBlockAndUpdate(pos, ice);
                level.scheduleTick(pos, Blocks.FROSTED_ICE, RimeYakRules.FROSTED_MELT_MIN_TICKS
                        + this.random.nextInt(RimeYakRules.FROSTED_MELT_MAX_TICKS - RimeYakRules.FROSTED_MELT_MIN_TICKS + 1));
                this.frosted.addLast(now + RimeYakRules.FROSTED_TRACK_TICKS);
                return; // MAX_FREEZE_PER_PASS = 1
            }
        }
    }

    private static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(Entity.class, new AABB(pos), Entity::isAlive).isEmpty();
    }

    private void updateSleep(ServerLevel level) {
        boolean sleep = level.isDarkOutside() && this.getNavigation().isDone() && this.getTarget() == null
                && this.getLastHurtByMob() == null && !this.isInLove() && this.alarmUntil < level.getGameTime();
        VisualPose pose = this.visuals().pose();
        if (sleep && pose == VisualPose.IDLE) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!sleep && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    private void syncVisuals() {
        // stage 0 = shorn (legs show), 3 = full wool. Babies show their growing wool too.
        this.visuals().setStage(this.wool.stage());
    }

    /** Client-safe: the synced stage says the wool is ready to shear. */
    public boolean woolReady() {
        return this.visuals().stage() >= RimeYakRules.WOOL_STAGES;
    }

    // --- shearing and milking -----------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && held.getItem() instanceof ShearsItem && !this.isBaby()) {
            return this.shear(player, hand, held);
        }
        if (!held.isEmpty() && held.is(Items.BUCKET) && !this.isBaby()) {
            return this.milk(player, hand, held);
        }
        return super.mobInteract(player, hand);
    }

    private InteractionResult shear(Player player, InteractionHand hand, ItemStack shears) {
        if (!(this.level() instanceof ServerLevel level)) {
            return this.woolReady() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Item woolItem = BuiltInRegistries.ITEM.getValue(RIME_WOOL);
        if (woolItem == Items.AIR || !this.wool.isReady()) {
            return InteractionResult.PASS;
        }
        int rolls = TraitEffects.productRolls(this.traits(), TraitPool.get(), this.random);
        int count = this.wool.harvest(RimeYakRules.WOOL_BASE_YIELD,
                base -> RimeYakRules.woolYield(base, rolls, RimeYakRules.WOOL_YIELD_CAP));
        if (count > 0) {
            this.spawnAtLocation(level, new ItemStack(woolItem, count));
        }
        shears.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        this.markHusbanded();
        level.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.8F);
        this.visuals().playPose(VisualPose.ABILITY, RimeYakRules.SHIVER_TICKS);
        this.syncVisuals();
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult milk(Player player, InteractionHand hand, ItemStack bucket) {
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Item milkItem = BuiltInRegistries.ITEM.getValue(COOLANT_MILK);
        if (milkItem == Items.AIR || !RimeYakRules.mayMilk(this.isBaby(), this.milkCooldown)) {
            return InteractionResult.PASS;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(bucket, player, new ItemStack(milkItem)));
        this.milkCooldown = RimeYakRules.milkCooldownTicks(milkCooldownTicks.getAsInt());
        this.markHusbanded();
        level.playSound(null, this.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F, 0.9F);
        this.visuals().playPose(VisualPose.ABILITY, RimeYakRules.SHIVER_TICKS);
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- herd alarm ---------------------------------------------------------------------------

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

    // --- sounds (mapped vanilla until the sound pass) -------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    // --- persistence ---------------------------------------------------------------------------

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
        output.putInt(WOOL_KEY, this.wool.progress());
        output.putInt(MILK_KEY, this.milkCooldown);
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
        this.wool.setProgress(input.getIntOr(WOOL_KEY, this.wool.regrowTicks()));
        this.milkCooldown = Math.max(0, input.getIntOr(MILK_KEY, 0));
        this.syncVisuals();
    }
}
