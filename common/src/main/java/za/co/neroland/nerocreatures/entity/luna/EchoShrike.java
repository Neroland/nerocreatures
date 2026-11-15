package za.co.neroland.nerocreatures.entity.luna;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.ai.shared.EcosystemTags;
import za.co.neroland.nerocreatures.entity.ai.shared.PredatorHuntGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.SoundHearing;
import za.co.neroland.nerocreatures.entity.ai.shared.SoundHuntGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * <b>Echo Shrike</b> (Luna, hostile) — blind, and hunts by sound.
 *
 * <ul>
 *   <li>A {@link SoundHearing} game-event listener (radius {@value ShrikeRules#HEARING_RADIUS},
 *       memory {@value ShrikeRules#MEMORY_TICKS} ticks) and the shared {@link SoundHuntGoal}: sprinting
 *       steps, mining and landing draw it; walking is quiet; <b>sneaking players are silent</b>, and
 *       a target that stays quiet past the memory window is dropped. There is no sight-based target
 *       goal and no look-at-player goal.</li>
 *   <li>Its attack is a telegraphed lunge ({@link ShrikeLungeGoal}: {@code ability} pose 26 ticks,
 *       hit at tick 12). While it has lost a target it holds the {@code ability_2} "searching" pose
 *       until the memory window runs out or it hears something.</li>
 *   <li>Hunts wild Lumen Moths ({@code #nerocreatures:prey/moths}) with the shared predator caps.</li>
 *   <li>Drops {@code resonant_membrane}.</li>
 * </ul>
 *
 * <p>The listener keeps only a session entity id (never saved, never logged).
 */
public class EchoShrike extends NeroCreatureEntity implements Enemy {

    /** Assigned in {@link #registerGoals()}, which the Mob constructor runs before field initialisers. */
    @Nullable
    private SoundHearing shrikeHearing;

    /** Game time the last target was lost, or {@link Long#MIN_VALUE}. Not saved. */
    private long lostTargetAt = Long.MIN_VALUE;

    public EchoShrike(EntityType<? extends EchoShrike> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, ShrikeRules.HEARING_RADIUS);
    }

    @Override
    protected void registerGoals() {
        this.shrikeHearing = new SoundHearing(this, ShrikeRules.HEARING_RADIUS, ShrikeRules.MEMORY_TICKS, true);
        this.installHearing(this.shrikeHearing);

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ShrikeLungeGoal(this));
        this.goalSelector.addGoal(3, new SoundHuntGoal(this, this.shrikeHearing, EchoShrike::huntable, 1.0D,
                ShrikeRules.DECISION_INTERVAL));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new PredatorHuntGoal(this, EcosystemTags.PREY_MOTHS,
                ShrikeRules.MOTH_HUNT_RADIUS, ShrikeRules.MOTH_SCAN_INTERVAL, ShrikeRules.MOTH_SATIATION_TICKS,
                ShrikeRules.MOTH_MAX_CHASE_TICKS));
    }

    /** Players (not creative/spectator) and wild moths. */
    static boolean huntable(LivingEntity entity) {
        boolean player = entity instanceof Player;
        boolean exempt = entity instanceof Player p && (p.isCreative() || p.isSpectator());
        boolean moth = entity.getType().builtInRegistryHolder().is(EcosystemTags.PREY_MOTHS);
        return ShrikeRules.huntable(player, exempt, moth, !player && PredatorHuntGoal.isWild(entity));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = this.getTarget();
        super.setTarget(target);
        if (this.level().isClientSide()) {
            return;
        }
        if (target != null) {
            this.lostTargetAt = Long.MIN_VALUE;
            if (this.visuals().pose() == VisualPose.ABILITY_2) {
                this.visuals().holdPose(VisualPose.IDLE);
            }
        } else if (previous != null && this.isAlive()) {
            // Lost it (it went quiet): listen with the searching head tilt.
            this.lostTargetAt = this.level().getGameTime();
            this.visuals().holdPose(VisualPose.ABILITY_2);
        }
    }

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
        if (this.lostTargetAt != Long.MIN_VALUE
                && !ShrikeRules.stillSearching(this.lostTargetAt, level.getGameTime(), this.getTarget() != null)) {
            this.lostTargetAt = Long.MIN_VALUE;
            if (this.visuals().pose() == VisualPose.ABILITY_2) {
                this.visuals().holdPose(VisualPose.IDLE);
            }
        }
    }

    // Mapped vanilla sounds until Stage 5 ships the family set: a small, chittering flyer's register.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }
}
