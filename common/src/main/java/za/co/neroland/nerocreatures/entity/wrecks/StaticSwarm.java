package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.spawn.HabitatTags;
import za.co.neroland.nerolandcore.worldgen.SpaceTags;

/**
 * <b>Static Swarm</b> (wrecks and stations; hostile) — one entity drawn as a cloud of about fourteen
 * motes on three rings around a denser core (the core is the hitbox).
 *
 * <p><b>Ability: drain.</b> Every {@value SwarmRules#PULSE_TICKS} ticks it pulls charge from items
 * in {@code #nerocreatures:chargeable} carried by up to {@value SwarmRules#MAX_PLAYERS} players within
 * {@value SwarmRules#DRAIN_RADIUS} blocks, at most {@value SwarmRules#MAX_UNITS_PER_PLAYER} unit per
 * player per pulse, through {@link ChargeDrain} (Core has no item-energy API: plasma cells are eaten,
 * registered handlers drain their own items, anything else is untouched). Each unit heals it. Gated by
 * {@code staticSwarmDrain} (decision 006: items only, no world change, so the key alone); off, it only
 * shocks on contact.
 *
 * <p><b>Use: bottling.</b> At or below half health a glass bottle captures it as a
 * {@code static_jar} (anyone may; it is wild). The jar is a throwable that briefly disables
 * mechanical creatures ({@link StaticJarBurst}).
 *
 * <p><b>Overworld</b> (decision 005): spawned only in thunderstorms; on its next storm check after the
 * storm ends it discards itself unless it is in a wreck habitat or name-tagged; and it never follows a
 * target into block light 10 or more, drifting away instead.
 *
 * <p>Visuals: {@code ability} = drain (rings contract to the core, then burst). Sparks
 * ({@code electric_spark} until the {@code static_spark} particle lands) jump to each drained player.
 * Nothing is saved beyond the base; no player identity is stored or logged.
 */
public class StaticSwarm extends NeroCreatureEntity implements Enemy
        //? if <26.2 {
        /*, net.minecraft.world.entity.animal.FlyingAnimal
        *///?}
{

    private int nextPulse = SwarmRules.PULSE_TICKS;

    public StaticSwarm(EntityType<? extends StaticSwarm> type, Level level) {
        super(type, level, CreatureTier.COMMON);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
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
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SwarmChaseGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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

    /** Whether the swarm may go to {@code pos} (decision 005: never into overworld block light 10+). */
    boolean mayEnter(BlockPos pos) {
        Level level = this.level();
        return !SwarmRules.tooBright(level.dimension() == Level.OVERWORLD,
                level.getBrightness(LightLayer.BLOCK, pos));
    }

    // --- storm, light and drain -------------------------------------------------------------

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
        if (this.tickCount % SwarmRules.STORM_CHECK_TICKS == 0 && this.overworldCheck(level)) {
            return;
        }
        if (--this.nextPulse > 0 || this.farTick().skip(this)) {
            return;
        }
        this.nextPulse = SwarmRules.PULSE_TICKS;
        this.pulse(level);
    }

    /** @return true if the swarm discarded itself */
    private boolean overworldCheck(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos pos = this.blockPosition();
        if (!this.isPersistenceRequired() && SwarmRules.vanishes(true, level.isThundering(),
                SpaceTags.biomeIn(level, pos, HabitatTags.WRECKS))) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.5D, this.getZ(),
                    10, 0.4D, 0.4D, 0.4D, 0.05D);
            this.discard();
            return true;
        }
        if (!this.mayEnter(pos)) {
            // Too bright: let go of the target and drift up and away from the light.
            this.setTarget(null);
            this.getNavigation().moveTo(this.getX() + this.random.nextInt(17) - 8, this.getY() + 4.0D,
                    this.getZ() + this.random.nextInt(17) - 8, 1.0D);
        }
        return false;
    }

    private void pulse(ServerLevel level) {
        if (!SwarmRules.drainEnabled(NeroCreaturesConfig.STATIC_SWARM_DRAIN.get())) {
            return;
        }
        double r2 = SwarmRules.DRAIN_RADIUS * SwarmRules.DRAIN_RADIUS;
        AABB box = this.getBoundingBox().inflate(SwarmRules.DRAIN_RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && this.distanceToSqr(p) <= r2);
        int n = SwarmRules.take(players.size(), SwarmRules.MAX_PLAYERS);
        int total = 0;
        for (int i = 0; i < n; i++) {
            Player player = players.get(i);
            int drained = drainPlayer(player);
            if (drained > 0) {
                total += drained;
                this.arcTo(level, player);
            }
        }
        if (total > 0) {
            this.heal(total * SwarmRules.HEAL_PER_UNIT);
            this.visuals().playPose(VisualPose.ABILITY, SwarmRules.DRAIN_POSE_TICKS);
            this.playSound(SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.8F);
        }
    }

    /** Drains up to the per-player cap from the first chargeable stacks in the inventory. */
    static int drainPlayer(Player player) {
        Inventory inventory = player.getInventory();
        int wanted = SwarmRules.MAX_UNITS_PER_PLAYER;
        int got = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && got < wanted; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(WrecksLookups.CHARGEABLE)) {
                continue;
            }
            got += ChargeDrain.drain(stack, WrecksLookups.isItem(stack, WrecksLookups.PLASMA_CELL), wanted - got);
        }
        if (got > 0) {
            inventory.setChanged();
        }
        return got;
    }

    /** A short line of sparks from the core to the drained player (one packet). */
    private void arcTo(ServerLevel level, Player player) {
        double mx = (this.getX() + player.getX()) * 0.5D;
        double my = (this.getY() + 0.4D + player.getY() + 1.0D) * 0.5D;
        double mz = (this.getZ() + player.getZ()) * 0.5D;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mx, my, mz, 6,
                Math.abs(player.getX() - this.getX()) * 0.25D, 0.2D, Math.abs(player.getZ() - this.getZ()) * 0.25D, 0.02D);
    }

    // --- bottling ---------------------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GLASS_BOTTLE)) {
            return super.mobInteract(player, hand);
        }
        if (!SwarmRules.mayBottle(this.getHealth(), this.getMaxHealth())) {
            return InteractionResult.PASS;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Item jar = WrecksLookups.item(WrecksLookups.STATIC_JAR);
        if (jar == Items.AIR) {
            return InteractionResult.PASS;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(jar)));
        level.playSound(null, this.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.HOSTILE, 1.0F, 1.6F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.4D, this.getZ(),
                12, 0.3D, 0.3D, 0.3D, 0.05D);
        // Captured, not killed: no loot, no death.
        this.discard();
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- sounds (mapped vanilla until the sound pass) ------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    /**
     * Flies at its target and shocks on contact every {@value SwarmRules#SHOCK_INTERVAL_TICKS} ticks.
     * Re-paths every 10 ticks (cached target), never toward a spot it may not enter.
     */
    static final class SwarmChaseGoal extends Goal {

        private final StaticSwarm swarm;
        private int repath;
        private int shockCooldown;

        SwarmChaseGoal(StaticSwarm swarm) {
            this.swarm = swarm;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.swarm.getTarget();
            return target != null && target.isAlive() && this.swarm.mayEnter(target.blockPosition());
        }

        @Override
        public void start() {
            this.repath = 0;
        }

        @Override
        public void stop() {
            this.swarm.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.swarm.getTarget();
            if (target == null) {
                return;
            }
            this.swarm.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--this.repath <= 0) {
                this.repath = 10;
                this.swarm.getNavigation().moveTo(target.getX(), target.getY() + 1.0D, target.getZ(), 1.0D);
            }
            if (this.shockCooldown > 0) {
                this.shockCooldown--;
                return;
            }
            double reach = SwarmRules.SHOCK_RANGE + target.getBbWidth() * 0.5D;
            if (this.swarm.distanceToSqr(target) <= reach * reach
                    && this.swarm.level() instanceof ServerLevel level) {
                this.shockCooldown = SwarmRules.SHOCK_INTERVAL_TICKS;
                this.swarm.doHurtTarget(level, target);
            }
        }
    }
}
