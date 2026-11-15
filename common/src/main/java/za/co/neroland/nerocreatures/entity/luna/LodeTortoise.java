package za.co.neroland.nerocreatures.entity.luna;

import java.util.function.IntSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;
import za.co.neroland.nerocreatures.entity.ai.shared.EcosystemTags;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdMember;
import za.co.neroland.nerocreatures.entity.ai.shared.PreyFleeGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;

/**
 * <b>Lode Tortoise</b> (Luna, livestock) — grows ore nodules on its shell.
 *
 * <ul>
 *   <li><b>Grazing (read-only).</b> Every {@value NoduleRules#GRAZE_INTERVAL} ticks an adult reads
 *       the nearest block within 4 blocks (2 vertically) that is in a
 *       {@code nerocreatures:lode_tortoise/<family>} block tag. It never breaks or changes it
 *       (decision 006: no world change; {@code lodeTortoiseNodules} is a balance switch). The
 *       {@code ability} pose (48 ticks) plays.</li>
 *   <li><b>Nodules</b> grow on a {@link ProductTimer} only while it grazes ore; switching family
 *       restarts growth; rare families grow four times slower. Synced as visuals {@code stage}
 *       (0 bare, 1-3 growth; 3 = ready) and {@code variant} (ore family).</li>
 *   <li><b>Harvest.</b> Use a pickaxe on a tortoise with ready nodules: vanilla nuggets or raw ore
 *       drop, the tortoise is unharmed, growth restarts. Yield is {@code baseYield x rolls}
 *       (Bountiful) capped per harvest; 20% chance to shed a {@code lode_scute}.</li>
 *   <li>Prey of grazer hunters; sleeps (held {@code rest} pose) when idle after dark.</li>
 * </ul>
 */
public class LodeTortoise extends LivestockCreature implements HerdMember {

    private static final String PROGRESS_KEY = "nerocreatures:nodules";
    private static final String FAMILY_KEY = "nerocreatures:nodule_family";
    private static final int GRAZE_POSE_TICKS = 48;
    private static final int SLEEP_CHECK_TICKS = 40;
    private static final int MAX_GRAZE_READS = 405;
    private static final Identifier LODE_SCUTE = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "lode_scute");

    private static final TagKey<Block>[] FAMILY_TAGS = familyTags();

    /** Config hooks, wired at common init by the integrator (defaults until then). */
    private static volatile IntSupplier regrowTicks = () -> NoduleRules.DEFAULT_REGROW_TICKS;
    private static volatile IntSupplier yieldCap = () -> NoduleRules.DEFAULT_YIELD_CAP;

    private final ProductTimer nodules;
    private final CadenceTimer grazeCadence = new CadenceTimer(NoduleRules.GRAZE_INTERVAL);
    @Nullable
    private NoduleFamily family;

    private long alarmUntil = Long.MIN_VALUE;
    private Vec3 alarmThreat = Vec3.ZERO;

    public LodeTortoise(EntityType<? extends LodeTortoise> type, Level level) {
        super(type, level, CreatureTier.COMMON);
        this.nodules = new ProductTimer(Math.max(1200, regrowTicks.getAsInt()), NoduleRules.MAX_STAGE, 64);
    }

    /** Binds the regrow and per-harvest cap config keys (integration: common init). */
    public static void configure(IntSupplier regrow, IntSupplier cap) {
        if (regrow != null) {
            regrowTicks = regrow;
        }
        if (cap != null) {
            yieldCap = cap;
        }
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Block>[] familyTags() {
        NoduleFamily[] families = NoduleFamily.values();
        TagKey<Block>[] tags = new TagKey[families.length];
        for (NoduleFamily f : families) {
            tags[f.ordinal()] = TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, f.tagPath()));
        }
        return tags;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivestockCreature.createPetAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.12D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PreyFleeGoal(this, EcosystemTags.PREDATORS, 10.0D, 12.0D, 40, 100, 1.4D));
    }

    // --- grazing and growth -------------------------------------------------------

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
        if (!NoduleRules.mayGrow(NeroCreaturesConfig.LODE_TORTOISE_NODULES.get(), this.isBaby())) {
            return;
        }
        if (this.farTick().skip(this) || !this.grazeCadence.ready(level.getGameTime(), this.getId())) {
            return;
        }
        NoduleRules.GrazeResult result = NoduleRules.onGraze(this.family, this.nodules.isReady(), this.graze(level));
        if (result.resetProgress()) {
            this.nodules.setProgress(0);
        }
        this.family = result.family();
        if (result.growing()) {
            this.nodules.advance(NoduleRules.growthTicks(NoduleRules.GRAZE_INTERVAL, this.family));
            this.visuals().playPose(VisualPose.ABILITY, GRAZE_POSE_TICKS);
        }
        this.syncVisuals();
    }

    /** The nearest ore family within reach, or null. Reads blocks only; never loads a chunk. */
    @Nullable
    private NoduleFamily graze(ServerLevel level) {
        int reads = 0;
        //? if >=26.3 {
        Iterable<BlockPos> nearestFirst = BlockPos.withinBoxByManhattanDistance(this.blockPosition(),
                NoduleRules.GRAZE_RADIUS, NoduleRules.GRAZE_VERTICAL, NoduleRules.GRAZE_RADIUS);
        //?} else {
        /*Iterable<BlockPos> nearestFirst = BlockPos.withinManhattan(this.blockPosition(),
                NoduleRules.GRAZE_RADIUS, NoduleRules.GRAZE_VERTICAL, NoduleRules.GRAZE_RADIUS);
        *///?}
        for (BlockPos pos : nearestFirst) {
            if (++reads > MAX_GRAZE_READS) {
                break;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            for (NoduleFamily candidate : NoduleFamily.values()) {
                if (state.is(FAMILY_TAGS[candidate.ordinal()])) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private void updateSleep(ServerLevel level) {
        boolean sleep = level.isDarkOutside() && this.getNavigation().isDone() && this.getTarget() == null
                && this.getLastHurtByMob() == null && !this.isInLove();
        VisualPose pose = this.visuals().pose();
        if (sleep && pose == VisualPose.IDLE) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!sleep && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    private void syncVisuals() {
        this.visuals().setStage(this.family == null ? 0 : this.nodules.stage());
        this.visuals().setVariant(this.family == null ? 0 : this.family.variant());
    }

    /** Client-safe: the synced stage says the nodules are ready. */
    public boolean noduleReady() {
        return this.visuals().stage() >= NoduleRules.MAX_STAGE;
    }

    // --- harvest ---------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !held.typeHolder().is(ItemTags.PICKAXES) || this.isBaby()) {
            return super.mobInteract(player, hand);
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return this.noduleReady() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!NeroCreaturesConfig.LODE_TORTOISE_NODULES.get() || this.family == null || !this.nodules.isReady()) {
            return InteractionResult.PASS;
        }
        NoduleFamily harvested = this.family;
        int rolls = TraitEffects.productRolls(this.traits(), TraitPool.get(), this.random);
        int cap = yieldCap.getAsInt();
        int count = this.nodules.harvest(harvested.baseYield(), base -> NoduleRules.harvestYield(base, rolls, cap));
        if (count > 0) {
            this.spawnAtLocation(level, new ItemStack(yieldItem(harvested), count));
        }
        if (NoduleRules.shedsScute(this.random.nextFloat())) {
            Item scute = BuiltInRegistries.ITEM.getValue(LODE_SCUTE);
            if (scute != Items.AIR) {
                this.spawnAtLocation(level, new ItemStack(scute));
            }
        }
        level.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
        this.syncVisuals();
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The vanilla item each family yields (nuggets or raw ore, never a new item). */
    static Item yieldItem(NoduleFamily family) {
        return switch (family) {
            case COAL -> Items.COAL;
            case COPPER -> Items.RAW_COPPER;
            case IRON -> Items.IRON_NUGGET;
            case GOLD -> Items.GOLD_NUGGET;
            case REDSTONE -> Items.REDSTONE;
            case LAPIS -> Items.LAPIS_LAZULI;
            case DIAMOND -> Items.DIAMOND;
            case EMERALD -> Items.EMERALD;
        };
    }

    // --- herd alarm -------------------------------------------------------------------

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

    // --- sounds (mapped vanilla until Stage 5) -------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.TURTLE_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.TURTLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_DEATH;
    }

    // --- persistence ----------------------------------------------------------------------

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
        output.putInt(PROGRESS_KEY, this.nodules.progress());
        output.putInt(FAMILY_KEY, this.family == null ? -1 : this.family.variant());
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
        this.nodules.setProgress(input.getIntOr(PROGRESS_KEY, 0));
        this.family = NoduleFamily.byVariant(input.getIntOr(FAMILY_KEY, -1));
        this.syncVisuals();
    }
}
