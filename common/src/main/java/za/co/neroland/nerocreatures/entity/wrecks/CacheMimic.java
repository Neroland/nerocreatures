package za.co.neroland.nerocreatures.entity.wrecks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;

/**
 * <b>Cache Mimic</b> (wrecks and stations; hostile) — an entity drawn as a loot crate. It places no
 * block (decision 001).
 *
 * <p><b>What it copies</b> (decision 007's open item, answered here): an <b>original salvage crate</b>
 * in the family style, not a vanilla chest or barrel. A squat, square plated cargo crate: ribbed grey
 * side panels, hazard-yellow corner bands, two side latch clamps, a flat hinged lid with a stencilled
 * chevron, and one sensor-red status pip. This mod has no wreck structure with vanilla containers, so
 * nothing else is matched. {@code variant} picks the dormant skin: 0 grey salvage, 1 worn white.
 *
 * <p><b>Dormant</b> ({@code form} 0, the model's rest): exactly one block, centred on its block,
 * yaw snapped to 90 degrees and re-asserted every {@value MimicRules#SETTLE_INTERVAL_TICKS} ticks; no
 * goals run, it cannot be pushed, and it makes no ambient sound. The fair tell is the model's (lid
 * lifts a texel every 8–12 s, glow seam within 3 blocks), and Lumen Moths reveal it
 * ({@code #nerocreatures:lumen_reveals}).
 *
 * <p><b>Reveal:</b> any interaction (a player trying to open it) or any damage wakes it: the
 * {@code ability} pose plays for {@value MimicRules#REVEAL_TICKS} ticks (lid flies open, legs unfold)
 * and it cannot bite until that telegraph ends. The interacting player becomes its target; damage
 * targets the attacker through the vanilla hurt-by goal. After {@value MimicRules#SLEEP_AFTER_TICKS}
 * ticks awake with no target it settles back into a crate.
 *
 * <p><b>Use:</b> the real loot is inside it: its loot table is a guaranteed good roll (no kill
 * condition). Nothing about a player is stored; the dormant flag is the persisted {@code form} bit.
 */
public class CacheMimic extends NeroCreatureEntity implements Enemy {

    private int ticksSinceReveal = MimicRules.REVEAL_TICKS;
    private int ticksWithoutTarget;

    public CacheMimic(EntityType<? extends CacheMimic> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BiteGoal(this));
        this.goalSelector.addGoal(7, new AwakeStrollGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new AwakeTargetGoal(this));
    }

    /** Whether it is a crate right now (synced through {@code form}). */
    public boolean isDormant() {
        return this.visuals().form() == MimicRules.FORM_DORMANT;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.visuals().setVariant(MimicRules.skin(this.random.nextInt(MimicRules.SKINS)));
        this.visuals().setForm(MimicRules.FORM_DORMANT);
        return result;
    }

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        if (this.isDormant()) {
            this.settle();
        }
    }

    /** Centres it on its block and snaps its yaw (decision 007). */
    private void settle() {
        float yaw = MimicRules.snapYaw(this.getYRot());
        this.snapTo(MimicRules.blockCentre(this.getX()), this.getY(), MimicRules.blockCentre(this.getZ()), yaw, 0.0F);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
    }

    private void wake(@Nullable LivingEntity cause) {
        if (!MimicRules.wakes(this.isDormant(), true)) {
            return;
        }
        this.visuals().setForm(MimicRules.FORM_AWAKE);
        this.visuals().playPose(VisualPose.ABILITY, MimicRules.REVEAL_TICKS);
        this.ticksSinceReveal = 0;
        this.ticksWithoutTarget = 0;
        this.playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.0F, 0.6F);
        if (cause != null && cause.isAlive() && !(cause instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            this.setTarget(cause);
        }
    }

    private void sleep() {
        this.visuals().setForm(MimicRules.FORM_DORMANT);
        this.visuals().playPose(VisualPose.REST, 10);
        this.setTarget(null);
        this.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.0F, 0.6F);
        this.settle();
    }

    // --- reveal on interaction or damage ----------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.isDormant()) {
            return super.mobInteract(player, hand);
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        this.wake(player);
        return InteractionResult.SUCCESS_SERVER;
    }

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //?} else {
    /*@Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(this.level() instanceof ServerLevel level)) {
            return super.hurt(source, amount);
        }
    *///?}
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && this.isDormant()) {
            this.wake(source.getEntity() instanceof LivingEntity attacker ? attacker : null);
        }
        return hurt;
    }

    //? if >=1.21.2 {
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
    *///?}
        if (this.isDormant()) {
            if (this.tickCount % MimicRules.SETTLE_INTERVAL_TICKS == 0) {
                this.settle();
            }
            return;
        }
        if (this.ticksSinceReveal < MimicRules.REVEAL_TICKS) {
            this.ticksSinceReveal++;
            this.getNavigation().stop();
        }
        LivingEntity target = this.getTarget();
        this.ticksWithoutTarget = target != null && target.isAlive() ? 0 : this.ticksWithoutTarget + 1;
        if (MimicRules.sleeps(false, this.ticksWithoutTarget)) {
            this.sleep();
        }
    }

    @Override
    public boolean isPushable() {
        return !this.isDormant() && super.isPushable();
    }

    // --- sounds (mapped vanilla until the sound pass) -----------------------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.isDormant() ? null : SoundEvents.IRON_TRAPDOOR_CLOSE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ANVIL_LAND;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ANVIL_BREAK;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    /** Vanilla melee, only once awake and after the reveal telegraph. */
    private static final class BiteGoal extends MeleeAttackGoal {

        private final CacheMimic mimic;

        BiteGoal(CacheMimic mimic) {
            super(mimic, 1.2D, true);
            this.mimic = mimic;
        }

        @Override
        public boolean canUse() {
            return MimicRules.mayAttack(this.mimic.isDormant(), this.mimic.ticksSinceReveal) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mimic.isDormant() && super.canContinueToUse();
        }
    }

    /** Wanders only while awake. */
    private static final class AwakeStrollGoal extends WaterAvoidingRandomStrollGoal {

        private final CacheMimic mimic;

        AwakeStrollGoal(CacheMimic mimic) {
            super(mimic, 0.8D);
            this.mimic = mimic;
        }

        @Override
        public boolean canUse() {
            return !this.mimic.isDormant() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mimic.isDormant() && super.canContinueToUse();
        }
    }

    /** Looks for players only while awake: a dormant crate never picks a target on its own. */
    private static final class AwakeTargetGoal extends NearestAttackableTargetGoal<Player> {

        private final CacheMimic mimic;

        AwakeTargetGoal(CacheMimic mimic) {
            super(mimic, Player.class, true);
            this.mimic = mimic;
        }

        @Override
        public boolean canUse() {
            return !this.mimic.isDormant() && super.canUse();
        }
    }
}
