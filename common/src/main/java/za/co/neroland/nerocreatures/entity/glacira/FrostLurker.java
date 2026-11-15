package za.co.neroland.nerocreatures.entity.glacira;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.ai.shared.AmbushGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.Ambusher;
import za.co.neroland.nerocreatures.entity.ai.shared.EcosystemTags;
import za.co.neroland.nerocreatures.entity.ai.shared.PredatorHuntGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * <b>Frost Lurker</b> (Glacira, hostile): a flat diamond body with a row of ice-shard spines and two
 * eye stalks; buried, only the spines and stalks show.
 *
 * <ul>
 *   <li><b>Lies in wait</b> (shared {@link AmbushGoal}) in any block of
 *       {@code nerocreatures:frost_lurker_hides_in} (snow and ice by default). Burying is a flag and a
 *       held {@code rest} pose: no block is ever changed, so there is no griefing gate (decision 006).
 *       Buried, other mobs cannot see it as an enemy and it makes no ambient sound.</li>
 *   <li><b>Erupts when walked over</b> by a survival player or a wild grazer: a
 *       {@value FrostLurkerRules#TELEGRAPH_TICKS}-tick telegraph (the {@code ability} pose, snow burst,
 *       crunch), then a bite that lands only if the victim is still within
 *       {@value FrostLurkerRules#STRIKE_REACH} blocks: damage plus a freeze past the vanilla full-freeze
 *       threshold (capped; leather and freeze-immune mobs are spared, as in powder snow).</li>
 *   <li>Then it chases, gives up beyond {@value FrostLurkerRules#LEASH} blocks or after
 *       {@value FrostLurkerRules#MAX_CHASE_TICKS} ticks, and buries itself again after a cooldown.</li>
 *   <li><b>Predator of grazers</b> (shared {@link PredatorHuntGoal}, tag {@code prey/grazers}): slow
 *       cadence, cached target, satiation after a kill.</li>
 * </ul>
 *
 * <p>Player use: it drops {@code cryo_fang} (the Lurker Cloak). Nothing is saved; an unloaded lurker
 * simply buries itself again.
 */
public class FrostLurker extends NeroCreatureEntity implements Enemy, Ambusher {

    /** Blocks it can lie hidden in. Data, so packs add their own snow and ice. */
    public static final TagKey<Block> HIDES_IN = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "frost_lurker_hides_in"));

    private boolean lurking;
    private int eruptTicks;
    @Nullable
    private LivingEntity pendingVictim;
    private long chaseStart = Long.MIN_VALUE;

    public FrostLurker(EntityType<? extends FrostLurker> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    /** A low, quick ambusher: modest health, a hard bite. */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AmbushGoal<>(this, state -> state.is(HIDES_IN), FrostLurker::triggers,
                FrostLurkerRules.TRIGGER_RADIUS, FrostLurkerRules.SCAN_INTERVAL, FrostLurkerRules.REBURY_COOLDOWN_TICKS,
                FrostLurkerRules.MAX_LURK_TICKS, this::beginEruption));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true) {
            @Override
            public boolean canUse() {
                return FrostLurker.this.eruptTicks <= 0 && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // No long-range player targeting: it is a fixed trap. Players become targets by stepping on
        // it or by hitting it.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new PredatorHuntGoal(this, EcosystemTags.PREY_GRAZERS,
                FrostLurkerRules.HUNT_RADIUS, FrostLurkerRules.HUNT_SCAN_TICKS,
                FrostLurkerRules.HUNT_SATIATION_TICKS, FrostLurkerRules.HUNT_MAX_CHASE_TICKS));
    }

    /** Who sets it off: survival players and wild grazers. */
    static boolean triggers(LivingEntity entity) {
        return AmbushGoal.survivalPlayer(entity)
                || (entity.getType().builtInRegistryHolder().is(EcosystemTags.PREY_GRAZERS) && PredatorHuntGoal.isWild(entity));
    }

    // --- ambush ---------------------------------------------------------------------------

    @Override
    public boolean isLurking() {
        return this.level().isClientSide() ? this.visuals().pose() == VisualPose.REST : this.lurking;
    }

    @Override
    public void setLurking(boolean lurking) {
        this.lurking = lurking;
        VisualPose pose = this.visuals().pose();
        if (lurking && pose != VisualPose.REST) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!lurking && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    /** The eruption: telegraph now, bite when it ends (see {@link #customServerAiStep}). */
    private void beginEruption(ServerLevel level, PathfinderMob self, LivingEntity victim) {
        this.pendingVictim = victim;
        this.eruptTicks = FrostLurkerRules.TELEGRAPH_TICKS;
        this.setTarget(null); // no melee during the telegraph; the bite decides
        this.visuals().playPose(VisualPose.ABILITY, FrostLurkerRules.ERUPT_POSE_TICKS);
        level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.3D, this.getZ(),
                16, 0.6D, 0.3D, 0.6D, 0.05D);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, this.getX(), this.getY() + 0.2D, this.getZ(),
                10, 0.5D, 0.2D, 0.5D, 0.1D);
        level.playSound(null, this.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    /** The bite at the end of the telegraph: damage and a capped freeze, only if still in reach. */
    private void strike(ServerLevel level, LivingEntity victim) {
        if (!victim.isAlive() || victim.level() != level) {
            return;
        }
        this.setTarget(victim);
        this.chaseStart = level.getGameTime();
        if (!FrostLurkerRules.strikeLands(this.distanceToSqr(victim))) {
            return;
        }
        if (victim.hurtServer(level, this.damageSources().mobAttack(this), FrostLurkerRules.STRIKE_DAMAGE)
                && victim.canFreeze()) {
            victim.setTicksFrozen(FrostLurkerRules.freezeTicks(victim.getTicksFrozen(), victim.getTicksRequiredToFreeze()));
        }
        level.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 1.4F);
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
        if (this.eruptTicks > 0 && --this.eruptTicks == 0) {
            LivingEntity victim = this.pendingVictim;
            this.pendingVictim = null;
            if (victim != null) {
                this.strike(level, victim);
            }
        }
        LivingEntity target = this.getTarget();
        if (target == null) {
            this.chaseStart = Long.MIN_VALUE;
            return;
        }
        long now = level.getGameTime();
        if (this.chaseStart == Long.MIN_VALUE) {
            this.chaseStart = now;
        }
        boolean gone = !target.isAlive() || target.level() != level
                || (target instanceof Player player && (player.isCreative() || player.isSpectator()));
        if (gone || FrostLurkerRules.dropTarget(this.distanceToSqr(target), now - this.chaseStart)) {
            this.setTarget(null);
            this.chaseStart = Long.MIN_VALUE;
        }
    }

    /** Buried, it is no one's enemy: mobs cannot pick it as a target. Players can still hit it. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.lurking && super.canBeSeenAsEnemy();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.pendingVictim = null;
        super.remove(reason);
    }

    // Mapped vanilla events until the sound pass: snow crunch and a cold rattle.

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.lurking ? null : SoundEvents.STRAY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.STRAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STRAY_DEATH;
    }
}
