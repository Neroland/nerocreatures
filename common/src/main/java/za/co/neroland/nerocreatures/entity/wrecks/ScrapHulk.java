package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * <b>Scrap Hulk</b> (wrecks and stations; hostile elite) — a lopsided knuckle-walker built from the
 * wreckage of other machines: one enormous arm made of an android torso, a rotor boom for the other,
 * no head. An emergent mini-boss.
 *
 * <p><b>Ability: assembly.</b> It is not spawned by a rule: {@link ScrapHulkAssembly} builds one where
 * three or more {@code #nerocreatures:mechanical} creatures die within 8 blocks in 10 s (* {@code scrapHulkAssembly}, ENTITY, capped one per 64 blocks and four per dimension). It arrives in
 * the {@code ability} pose for {@value ScrapHulkRules#ASSEMBLE_TICKS} ticks (parts fly in), during
 * which it cannot be hurt and does not act.
 *
 * <p><b>Sheds parts:</b> {@code stage} 0–3 by health quarter. Each step drops one
 * {@code salvaged_circuitry}, a clank and a spark burst, and costs
 * {@value ScrapHulkRules#ARMOUR_PER_STAGE} armour (at most three parts in its life).
 *
 * <p><b>Fight:</b> vanilla melee plus a big-arm sweep ({@code ability_2}, 20 ticks, hit at tick
 * {@value ScrapHulkRules#SWEEP_TELEGRAPH_TICKS}) on up to {@value ScrapHulkRules#MAX_SWEEP_TARGETS}
 * players in its front arc. Stacked circuitry and an android core in its loot table. Elite: listed in
 * {@code alpha_excluded}. Not persistent: it despawns like any natural hostile. Saves only whether
 * assembly finished.
 */
public class ScrapHulk extends NeroCreatureEntity implements Enemy {

    private static final double BASE_ARMOUR = 10.0D;
    private static final String ASSEMBLED_KEY = "nerocreatures:assembled";

    private int assembling;
    private int sweepCooldown = ScrapHulkRules.SWEEP_COOLDOWN_TICKS;

    public ScrapHulk(EntityType<? extends ScrapHulk> type, Level level) {
        super(type, level, CreatureTier.GREATER);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.GREATER)
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, BASE_ARMOUR)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SweepGoal(this));
        this.goalSelector.addGoal(3, new HulkMeleeGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Starts the assembly pose. Called once by {@link ScrapHulkAssembly} right after spawning. */
    public void beginAssembly() {
        this.assembling = ScrapHulkRules.ASSEMBLE_TICKS;
        this.visuals().playPose(VisualPose.ABILITY, ScrapHulkRules.ASSEMBLE_TICKS);
        this.visuals().setStage(0);
        ModSounds.SCRAP_HULK_ASSEMBLE.play(this);
    }

    /** Whether it is still assembling (invulnerable, inert). */
    public boolean isAssembling() {
        return this.assembling > 0;
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
        if (this.isAssembling() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive()) {
            this.updateStage(level);
        }
        return hurt;
    }

    private void updateStage(ServerLevel level) {
        int old = this.visuals().stage();
        int now = ScrapHulkRules.stageFor(this.getHealth(), this.getMaxHealth());
        int shed = ScrapHulkRules.partsToShed(old, now);
        if (shed <= 0) {
            return;
        }
        this.visuals().setStage(now);
        AttributeInstance armour = this.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(ScrapHulkRules.armourAt(BASE_ARMOUR, now));
        }
        Item part = WrecksLookups.item(WrecksLookups.SALVAGED_CIRCUITRY);
        for (int i = 0; i < shed; i++) {
            if (part != Items.AIR) {
                this.spawnAtLocation(level, new ItemStack(part));
            }
        }
        ModSounds.SCRAP_HULK_SHED.play(this);
        level.sendParticles(ModParticles.STATIC_SPARK.get(), this.getX(), this.getY() + 1.4D, this.getZ(),
                16, 0.6D, 0.6D, 0.6D, 0.1D);
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
        if (this.assembling > 0) {
            this.assembling--;
            this.getNavigation().stop();
        }
        if (this.sweepCooldown > 0) {
            this.sweepCooldown--;
        }
    }

    // --- persistence --------------------------------------------------------------------------

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
        output.putBoolean(ASSEMBLED_KEY, true);
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
        // A reloaded hulk is always fully assembled (the pose is not saved).
        if (input.getBooleanOr(ASSEMBLED_KEY, true)) {
            this.assembling = 0;
        }
    }

    // --- sounds ---------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SCRAP_HULK_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.SCRAP_HULK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SCRAP_HULK_DEATH.get();
    }

    /** Vanilla melee, idle while assembling or mid-sweep. */
    private static final class HulkMeleeGoal extends MeleeAttackGoal {

        private final ScrapHulk hulk;

        HulkMeleeGoal(ScrapHulk hulk) {
            super(hulk, 1.0D, true);
            this.hulk = hulk;
        }

        @Override
        public boolean canUse() {
            return !this.hulk.isAssembling() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.hulk.isAssembling() && super.canContinueToUse();
        }
    }

    /** The big-arm sweep: telegraphed, front arc, capped targets, on a cooldown. */
    private static final class SweepGoal extends Goal {

        private final ScrapHulk hulk;
        private int ticks;

        SweepGoal(ScrapHulk hulk) {
            this.hulk = hulk;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.hulk.getTarget();
            if (this.hulk.isAssembling() || this.hulk.sweepCooldown > 0 || target == null || !target.isAlive()) {
                return false;
            }
            double reach = ScrapHulkRules.SWEEP_RANGE;
            return this.hulk.distanceToSqr(target) <= reach * reach;
        }

        @Override
        public boolean canContinueToUse() {
            return this.ticks < ScrapHulkRules.SWEEP_POSE_TICKS && this.hulk.isAlive();
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.hulk.getNavigation().stop();
            this.hulk.visuals().playPose(VisualPose.ABILITY_2, ScrapHulkRules.SWEEP_POSE_TICKS);
        }

        @Override
        public void stop() {
            this.hulk.sweepCooldown = ScrapHulkRules.SWEEP_COOLDOWN_TICKS;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.ticks++;
            LivingEntity target = this.hulk.getTarget();
            if (target != null && this.ticks < ScrapHulkRules.SWEEP_TELEGRAPH_TICKS) {
                this.hulk.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            if (this.ticks == ScrapHulkRules.SWEEP_TELEGRAPH_TICKS && this.hulk.level() instanceof ServerLevel level) {
                this.strike(level);
            }
        }

        private void strike(ServerLevel level) {
            float yaw = this.hulk.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            double r = ScrapHulkRules.SWEEP_RANGE;
            AABB box = this.hulk.getBoundingBox().inflate(r, 1.0D, r);
            List<Player> players = level.getEntitiesOfClass(Player.class, box, p -> p.isAlive()
                    && !p.isSpectator() && !p.isCreative()
                    && ScrapHulkRules.inSweep(fx, fz, p.getX() - this.hulk.getX(), p.getZ() - this.hulk.getZ()));
            int n = SwarmRules.take(players.size(), ScrapHulkRules.MAX_SWEEP_TARGETS);
            float damage = (float) this.hulk.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * ScrapHulkRules.SWEEP_DAMAGE_FACTOR;
            for (int i = 0; i < n; i++) {
                Player player = players.get(i);
                if (player.hurtServer(level, this.hulk.damageSources().mobAttack(this.hulk), damage)) {
                    player.setDeltaMovement(player.getDeltaMovement().add(fx * 1.2D, 0.4D, fz * 1.2D));
                    //? if >=26.3 {
                    player.syncVelocity = true;
                    //?} else {
                    /*player.hurtMarked = true;
                    *///?}
                }
            }
            ModSounds.SCRAP_HULK_SWEEP.play(this.hulk);
        }
    }
}
