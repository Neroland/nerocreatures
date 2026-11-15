package za.co.neroland.nerocreatures.entity.mars;

import java.util.List;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.mars.DustDevilRules.Phase;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Dust Devil</b> (Mars; hostile) — a headless whirlwind of three sand rings around a charged
 * core. An arena hazard that changes fights; drops {@code charged_sand}.
 *
 * <p><b>Ability</b> (cycle in {@link DustDevilRules}): with a target within
 * {@value DustDevilRules#ENGAGE_RANGE} blocks it <b>spins up</b> for
 * {@value DustDevilRules#SPIN_UP_TICKS} ticks, during which it cannot be hit (the telegraph:
 * {@code ability} pose, a tight pale column). Then it <b>whirls</b> for
 * {@value DustDevilRules#WHIRL_TICKS} ticks: players (and, when allowed, item entities) within
 * {@value DustDevilRules#LIFT_RADIUS} blocks are lifted up to {@value DustDevilRules#MAX_LIFT_HEIGHT}
 * blocks and swirled, and players within {@value DustDevilRules#VISIBILITY_RADIUS} blocks are blinded
 * briefly. Finally it <b>throws</b> them outward ({@code ability_2} pose) and rests
 * {@value DustDevilRules#COOLDOWN_TICKS} ticks, exposed.
 *
 * <p><b>Griefing.</b> Moving ground items can fling them into lava or the void, so it goes through
 * {@link CreatureGriefing#mayAlter} ({@code ITEM}, key {@code dustDevilThrowsItems}), decided once
 * per whirl. Denied, the devil throws players only. Players are combat, never gated.
 *
 * <p>Caps: {@value DustDevilRules#MAX_PLAYERS} players and {@value DustDevilRules#MAX_ITEMS} items
 * per whirl tick; one bounded box query each, every {@value #LIFT_INTERVAL_TICKS} ticks. Far devils
 * hold still in their cycle through the far-tick gate. Nothing is saved (a reloaded devil is calm).
 *
 * <p>Visuals: {@code ability} = spin-up, {@code ability_2} = throw; {@code form} 1 while spinning up
 * (invulnerable: closed pale column), 0 otherwise; {@code stage} 1 while whirling (arc cage, core
 * exposed), 0 otherwise.
 */
public class DustDevil extends NeroCreatureEntity implements Enemy {

    static final int LIFT_INTERVAL_TICKS = 2;
    /** {@code form} while spinning up and invulnerable: the model's closed pale column. */
    static final int FORM_COLUMN = 1;
    private static final int BLIND_INTERVAL_TICKS = 20;
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    private Phase phase = Phase.CALM;
    private int phaseTicks = DustDevilRules.COOLDOWN_TICKS; // ready on spawn
    private boolean itemsAllowed;

    public DustDevil(EntityType<? extends DustDevil> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.SAFE_FALL_DISTANCE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new DriftAttackGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** The current phase (server). */
    public Phase phase() {
        return this.phase;
    }

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        this.visuals().setStage(0);
        this.visuals().setForm(0);
    }

    // --- invulnerable while spinning up ---------------------------------------------------

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
        if (DustDevilRules.invulnerable(this.phase) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    // --- the cycle --------------------------------------------------------------------

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
        if (this.phase == Phase.CALM && this.farTick().skip(this)) {
            return;
        }
        this.phaseTicks++;
        LivingEntity target = this.getTarget();
        boolean inRange = target != null && target.isAlive()
                && this.distanceToSqr(target) <= DustDevilRules.ENGAGE_RANGE * DustDevilRules.ENGAGE_RANGE;
        Phase next = DustDevilRules.next(this.phase, this.phaseTicks, inRange);
        if (next != this.phase) {
            this.enter(level, next);
        }
        switch (this.phase) {
            case SPIN_UP -> this.getNavigation().stop();
            case WHIRL -> this.tickWhirl(level);
            default -> {
            }
        }
    }

    private void enter(ServerLevel level, Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
        switch (next) {
            case SPIN_UP -> {
                this.visuals().playPose(VisualPose.ABILITY, DustDevilRules.SPIN_UP_TICKS);
                this.visuals().setForm(FORM_COLUMN);
                ModSounds.DUST_DEVIL_WHIRL.play(this);
            }
            case WHIRL -> {
                this.visuals().setForm(0);
                this.visuals().setStage(1);
                this.itemsAllowed = CreatureGriefing.mayAlter(level, this, this.blockPosition(),
                        NeroCreaturesConfig.DUST_DEVIL_THROWS_ITEMS, CreatureGriefing.Change.ITEM);
            }
            case THROW -> {
                this.visuals().playPose(VisualPose.ABILITY_2, DustDevilRules.THROW_TICKS);
                ModSounds.DUST_DEVIL_THROW.play(this);
                this.throwAll(level);
                this.visuals().setStage(0);
            }
            case CALM -> this.itemsAllowed = false;
        }
    }

    private AABB liftBox() {
        double r = DustDevilRules.LIFT_RADIUS;
        return new AABB(this.getX() - r, this.getY() - 1.0D, this.getZ() - r,
                this.getX() + r, this.getY() + DustDevilRules.MAX_LIFT_HEIGHT + 2.0D, this.getZ() + r);
    }

    private List<Player> playersInReach(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private void tickWhirl(ServerLevel level) {
        if (this.phaseTicks % PARTICLE_INTERVAL_TICKS == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_SAND.defaultBlockState()),
                    this.getX(), this.getY() + 1.0D, this.getZ(), 8, 0.8D, 1.0D, 0.8D, 0.1D);
        }
        if (this.phaseTicks % BLIND_INTERVAL_TICKS == 1) {
            double v = DustDevilRules.VISIBILITY_RADIUS;
            for (Player player : playersInReach(level, this.getBoundingBox().inflate(v, v * 0.5D, v))) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DustDevilRules.BLIND_TICKS, 0,
                        false, false, true), this);
            }
        }
        if (this.phaseTicks % LIFT_INTERVAL_TICKS != 0) {
            return;
        }
        AABB box = this.liftBox();
        List<Player> players = playersInReach(level, box);
        int n = DustDevilRules.take(players.size(), DustDevilRules.MAX_PLAYERS);
        for (int i = 0; i < n; i++) {
            this.lift(players.get(i));
        }
        int itemCap = DustDevilRules.itemCap(this.itemsAllowed);
        if (itemCap > 0) {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
            int m = DustDevilRules.take(items.size(), itemCap);
            for (int i = 0; i < m; i++) {
                this.lift(items.get(i));
            }
        }
    }

    private void lift(Entity entity) {
        double dx = entity.getX() - this.getX();
        double dz = entity.getZ() - this.getZ();
        if (dx * dx + dz * dz > DustDevilRules.LIFT_RADIUS * DustDevilRules.LIFT_RADIUS) {
            return;
        }
        double[] swirl = DustDevilRules.swirl(dx, dz);
        Vec3 motion = entity.getDeltaMovement();
        double vy = DustDevilRules.liftVelocity(motion.y, entity.getY() - this.getY());
        entity.setDeltaMovement(swirl[0], vy, swirl[1]);
        entity.resetFallDistance();
        markMoved(entity);
    }

    private void throwAll(ServerLevel level) {
        AABB box = this.liftBox();
        List<Player> players = playersInReach(level, box);
        int n = DustDevilRules.take(players.size(), DustDevilRules.MAX_PLAYERS);
        for (int i = 0; i < n; i++) {
            this.fling(players.get(i));
        }
        int itemCap = DustDevilRules.itemCap(this.itemsAllowed);
        if (itemCap > 0) {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
            int m = DustDevilRules.take(items.size(), itemCap);
            for (int i = 0; i < m; i++) {
                this.fling(items.get(i));
            }
        }
    }

    private void fling(Entity entity) {
        double[] v = DustDevilRules.throwVelocity(entity.getX() - this.getX(), entity.getZ() - this.getZ());
        entity.setDeltaMovement(v[0], v[1], v[2]);
        markMoved(entity);
    }

    /** Without this a player's client never sees a server-side velocity change. */
    private static void markMoved(Entity entity) {
        //? if >=26.3 {
        entity.syncVelocity = true;
        //?} else {
        /*entity.hurtMarked = true;
        *///?}
    }

    // --- sounds (mapped vanilla events) ---------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.DUST_DEVIL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.DUST_DEVIL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DUST_DEVIL_DEATH.get();
    }

    /** Vanilla melee chase, idle while spinning up (it is rooted then). */
    private static final class DriftAttackGoal extends MeleeAttackGoal {

        private final DustDevil devil;

        DriftAttackGoal(DustDevil devil) {
            super(devil, 1.0D, true);
            this.devil = devil;
        }

        @Override
        public boolean canUse() {
            return this.devil.phase != Phase.SPIN_UP && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.devil.phase != Phase.SPIN_UP && super.canContinueToUse();
        }
    }
}
