package za.co.neroland.nerocreatures.entity.nyxara;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;
import za.co.neroland.nerocreatures.entity.ai.shared.HerdCohesionGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * <b>Prism Stag</b> (Nyxara; neutral livestock) — a slender deer with two tall crystal antlers.
 *
 * <ul>
 *   <li><b>Antler shedding</b> ({@link PrismStagRules}). Adults grow antlers on a
 *       {@link ProductTimer} (regrow time = the species' {@code product_interval_ticks}) and shed
 *       them as {@code nerocreatures:prism_antler} items: renewable crystal without killing golems.
 *       Dropping an item on its own position changes no block, so no griefing gate.
 *       {@code ability} pose = head toss; {@code stage} 0 bare, 1 half, 2 full.</li>
 *   <li><b>Herd aura.</b> At night, three or more adults within {@value PrismStagRules#HERD_RADIUS}
 *       blocks light their antlers ({@code form} 1) and the herd leader gives nearby players a short
 *       Regeneration I ({@code ability_2} pose: head lowered, antlers lit). Cadence-checked, one
 *       bounded box query per stag per check, capped players. Config hook {@link #configure}.</li>
 *   <li><b>Neutral.</b> It never starts a fight; an adult hurt by something fights back and alerts the
 *       herd. Babies panic instead. Bred with its food tag ({@code nerocreatures:food/prism_stag}),
 *       live birth. Sleeps ({@code rest}) when idle after dark.</li>
 * </ul>
 *
 * <p>Saved: antler progress only (an int). Nothing identifies a player; the aura picks players by
 * area, never by identity.
 */
public class PrismStag extends LivestockCreature {

    private static final String ANTLER_KEY = "nerocreatures:antlers";
    private static final int SLEEP_CHECK_TICKS = 40;
    private static final Identifier PRISM_ANTLER =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "prism_antler");

    /** Config hook ({@code prismStagHerdAura}), wired at common init; on until then. */
    private static volatile BooleanSupplier auraEnabled = () -> true;

    private final CadenceTimer growthCadence = new CadenceTimer(PrismStagRules.GROWTH_INTERVAL);
    private final CadenceTimer auraCadence = new CadenceTimer(PrismStagRules.AURA_INTERVAL);
    /** Built on first use, so the data-driven regrow time is read after the species data loaded. */
    @Nullable
    private ProductTimer antlers;
    private int savedProgress;

    public PrismStag(EntityType<? extends PrismStag> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    /** Binds the aura on/off key (integration: common init). */
    public static void configure(BooleanSupplier aura) {
        if (aura != null) {
            auraEnabled = aura;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivestockCreature.createPetAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    /**
     * Livestock goals, except that an adult answers an attack instead of panicking (neutral). Babies
     * keep the panic. No goal targets anything on its own.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.3D) {
            @Override
            public boolean canUse() {
                return PrismStag.this.isBaby() && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true) {
            @Override
            public boolean canUse() {
                return !PrismStag.this.isBaby() && super.canUse();
            }
        });
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1D, this::isSpeciesFood, false));
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(6, new HerdCohesionGoal(this, 16.0D, 7.0D, 60, 1.0D));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    private ProductTimer antlers() {
        if (this.antlers == null) {
            this.antlers = new ProductTimer(PrismStagRules.shedTicks(this.productIntervalTicks()),
                    PrismStagRules.ANTLER_STAGES, PrismStagRules.SHED_CAP);
            this.antlers.setProgress(this.savedProgress);
        }
        return this.antlers;
    }

    /** Antlers grow even without a {@code species.json} timer; the default regrow time applies. */
    @Override
    public boolean canProduce() {
        return this.isAlive() && !this.isBaby();
    }

    // --- antlers and aura ------------------------------------------------------------

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
        if (this.farTick().skip(this)) {
            return;
        }
        long now = level.getGameTime();
        if (this.growthCadence.ready(now, this.getId())) {
            this.tickAntlers(level);
        }
        if (this.auraCadence.ready(now, this.getId())) {
            this.tickAura(level);
        }
    }

    private void tickAntlers(ServerLevel level) {
        if (!this.canProduce()) {
            this.visuals().setStage(0);
            return;
        }
        ProductTimer timer = this.antlers();
        timer.advance(PrismStagRules.GROWTH_INTERVAL);
        if (PrismStagRules.mayShed(timer.isReady(), !this.isBaby())) {
            this.shed(level, timer);
        }
        this.visuals().setStage(PrismStagRules.visualStage(timer.stage()));
    }

    /** Drops the antlers where the stag stands and restarts growth. Never touches a block. */
    private void shed(ServerLevel level, ProductTimer timer) {
        int rolls = TraitEffects.productRolls(this.traits(), TraitPool.get(), this.random);
        int count = timer.harvest(PrismStagRules.SHED_BASE,
                base -> PrismStagRules.shedYield(base, rolls, PrismStagRules.SHED_CAP));
        Item antler = BuiltInRegistries.ITEM.getValue(PRISM_ANTLER);
        if (count > 0 && antler != Items.AIR) {
            this.spawnAtLocation(level, new ItemStack(antler, count));
        }
        if (this.visuals().pose() != VisualPose.REST) {
            this.visuals().playPose(VisualPose.ABILITY, PrismStagRules.SHED_POSE_TICKS);
        }
        ModSounds.PRISM_STAG_SHED.play(this);
    }

    private void tickAura(ServerLevel level) {
        boolean enabled = auraEnabled.getAsBoolean();
        boolean night = level.isDarkOutside();
        if (this.isBaby() || !enabled || !night) {
            this.setAuraLive(false);
            return;
        }
        double h = PrismStagRules.HERD_RADIUS;
        List<PrismStag> herd = level.getEntitiesOfClass(PrismStag.class, this.getBoundingBox().inflate(h, 4.0D, h),
                stag -> stag.isAlive() && !stag.isBaby() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(stag));
        boolean live = PrismStagRules.auraLive(enabled, night, herd.size());
        this.setAuraLive(live);
        if (!live) {
            return;
        }
        int lowest = Integer.MAX_VALUE;
        for (PrismStag stag : herd) {
            lowest = Math.min(lowest, stag.getId());
        }
        if (!PrismStagRules.isLeader(this.getId(), lowest)) {
            return;
        }
        double r = PrismStagRules.auraRadius(TraitEffects.abilityRangeMultiplier(this.traits(), TraitPool.get()));
        List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r, 4.0D, r),
                player -> player.isAlive() && !player.isSpectator());
        int n = PrismStagRules.take(players.size(), PrismStagRules.AURA_MAX_PLAYERS);
        for (int i = 0; i < n; i++) {
            players.get(i).addEffect(new MobEffectInstance(MobEffects.REGENERATION, PrismStagRules.AURA_EFFECT_TICKS,
                    PrismStagRules.AURA_AMPLIFIER, true, true), this);
        }
        if (this.visuals().pose() == VisualPose.IDLE) {
            this.visuals().playPose(VisualPose.ABILITY_2, PrismStagRules.AURA_POSE_TICKS);
        }
        level.sendParticles(ModParticles.PRISM_GLINT.get(), this.getX(), this.getY() + this.getBbHeight() + 0.4D, this.getZ(),
                6, 0.6D, 0.4D, 0.6D, 0.01D);
        if (n > 0) {
            ModSounds.PRISM_STAG_AURA.play(this);
        }
    }

    private void setAuraLive(boolean live) {
        this.visuals().setForm(live ? PrismStagRules.FORM_AURA : 0);
    }

    /** Client-safe: the synced form says the herd aura is live (antlers lit). */
    public boolean auraLive() {
        return this.visuals().form() == PrismStagRules.FORM_AURA;
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

    // --- sounds (mapped vanilla until the sound pass) -------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.PRISM_STAG_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.PRISM_STAG_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PRISM_STAG_DEATH.get();
    }

    // --- persistence -----------------------------------------------------------------

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
        output.putInt(ANTLER_KEY, this.antlers == null ? this.savedProgress : this.antlers.progress());
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
        this.savedProgress = Math.max(0, input.getIntOr(ANTLER_KEY, 0));
        if (this.antlers != null) {
            this.antlers.setProgress(this.savedProgress);
        }
        this.visuals().setStage(this.isBaby() ? 0 : PrismStagRules.visualStage(this.antlers().stage()));
    }
}
