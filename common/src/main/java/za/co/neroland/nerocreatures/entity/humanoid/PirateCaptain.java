package za.co.neroland.nerocreatures.entity.humanoid;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.entity.wrecks.CaptainRules;
import za.co.neroland.nerocreatures.entity.wrecks.WrecksLookups;
import za.co.neroland.nerocreatures.item.CaptainsLedgerItem;
import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Pirate Captain</b> (pirates; hostile elite) — a Space Pirate 15% taller, long split coat, twin jet
 * nacelles above the shoulders, wide-brimmed hat. Same kit system as the crew ({@link SpacePirate}),
 * always from the raider tier.
 *
 * <p><b>Ability: jet-dash.</b> Toward a target {@value CaptainRules#DASH_MIN}–{@value CaptainRules#DASH_MAX}
 * blocks away it plants its feet, the nacelles flare ({@code ability}, {@value CaptainRules#DASH_POSE_TICKS}
 * ticks, launch at tick {@value CaptainRules#DASH_TELEGRAPH_TICKS}), then launches level at the target.
 * Every {@value CaptainRules#DASH_COOLDOWN_TICKS} ticks at most.
 *
 * <p><b>Rally:</b> with a target, every {@value CaptainRules#RALLY_INTERVAL_TICKS} ticks it raises an arm
 * ({@code ability_2}) and up to {@value CaptainRules#MAX_RALLIED} Space Pirates within
 * {@value CaptainRules#RALLY_RADIUS} blocks take its target (if they have none) and speed I plus
 * strength I for {@value CaptainRules#RALLY_BUFF_TICKS} ticks. A naturally spawned captain brings
 * {@value CaptainRules#ESCORT_COUNT} crew (reinforcements: not persistent, census-capped).
 *
 * <p><b>Use: the {@code captains_ledger}.</b> On death the ledger goes to the player credited with the
 * kill by the damage source only, alive and in the same level, straight into that player's inventory
 * (dropped at their feet if full). Never a nearest player; otherwise no ledger. The ledger marks one
 * pirate stash ({@link CaptainsLedgerItem}). Nothing is logged; the mark lives only on the item.
 * Elite: in {@code alpha_excluded}.
 */
public class PirateCaptain extends SpacePirate {

    private int ticksSinceDash = CaptainRules.DASH_COOLDOWN_TICKS;
    private int ticksSinceRally = CaptainRules.RALLY_INTERVAL_TICKS;
    /** Escorts still to place on the first server tick (natural spawns only). Not saved. */
    private int escortsPending;

    public PirateCaptain(EntityType<? extends PirateCaptain> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return SpacePirate.createAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new JetDashGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.setLoadout(PirateLoadout.roll(this.random, PirateLoadout.RAIDER_TIER));
        if (spawnReason == EntitySpawnReason.NATURAL) {
            this.escortsPending = CaptainRules.ESCORT_COUNT;
        }
        return result;
    }

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        if (this.escortsPending > 0) {
            spawnCrew(level, this.blockPosition(), this.escortsPending, PirateLoadout.RECRUIT_TIER, null);
            this.escortsPending = 0;
        }
    }

    /**
     * Places up to {@code count} Space Pirates around {@code origin} as reinforcements (not persistent),
     * respecting the census caps, each only where it fits.
     *
     * @param target an entity every placed pirate should attack (the subject of the action, never a
     *               looked-up nearest player), or null
     * @return how many were placed
     */
    public static int spawnCrew(ServerLevel level, BlockPos origin, int count, int tier, @Nullable LivingEntity target) {
        RandomSource random = level.getRandom();
        int placed = 0;
        for (int i = 0; i < count; i++) {
            if (!CreatureCensus.dimensionHasRoom(level)) {
                break;
            }
            BlockPos pos = origin.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            if (!level.isLoaded(pos) || !CreatureCensus.chunkHasRoom(level, pos)) {
                continue;
            }
            SpacePirate pirate = ModEntities.SPACE_PIRATE.get().create(level, EntitySpawnReason.REINFORCEMENT);
            if (pirate == null) {
                break;
            }
            pirate.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(pirate)) {
                pirate.discard();
                continue;
            }
            pirate.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.REINFORCEMENT, null);
            pirate.setLoadout(PirateLoadout.roll(random, tier));
            if (target != null) {
                pirate.setTarget(target);
            }
            if (level.addFreshEntity(pirate)) {
                placed++;
            }
        }
        if (placed > 0) {
            CreatureCensus.invalidate(level);
        }
        return placed;
    }

    // --- rally ----------------------------------------------------------------------------------

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
        this.ticksSinceDash++;
        this.ticksSinceRally++;
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        if (CaptainRules.rallyDue(hasTarget, this.ticksSinceRally) && !this.farTick().skip(this)) {
            this.ticksSinceRally = 0;
            this.rally(level, target);
        }
    }

    private void rally(ServerLevel level, LivingEntity target) {
        AABB box = this.getBoundingBox().inflate(CaptainRules.RALLY_RADIUS);
        List<SpacePirate> crew = new ArrayList<>(CaptainRules.MAX_RALLIED);
        level.getEntities(EntityTypeTest.forClass(SpacePirate.class), box,
                p -> p != this && p.isAlive() && !(p instanceof PirateCaptain) && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(p), crew,
                CaptainRules.MAX_RALLIED);
        this.visuals().playPose(VisualPose.ABILITY_2, CaptainRules.RALLY_POSE_TICKS);
        ModSounds.PIRATE_CAPTAIN_RALLY.play(this);
        Holder<MobEffect> speed = WrecksLookups.effect("speed");
        Holder<MobEffect> strength = WrecksLookups.effect("strength");
        for (SpacePirate pirate : crew) {
            if (pirate.getTarget() == null) {
                pirate.setTarget(target);
            }
            if (speed != null) {
                pirate.addEffect(new MobEffectInstance(speed, CaptainRules.RALLY_BUFF_TICKS, 0), this);
            }
            if (strength != null) {
                pirate.addEffect(new MobEffectInstance(strength, CaptainRules.RALLY_BUFF_TICKS, 0), this);
            }
        }
    }

    // --- the ledger -----------------------------------------------------------------------------

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        Entity killer = source.getEntity();
        boolean isPlayer = killer instanceof ServerPlayer;
        if (!CaptainRules.ledgerGoesToKiller(isPlayer, isPlayer && killer.level() == level,
                isPlayer && killer.isAlive())) {
            return;
        }
        ServerPlayer player = (ServerPlayer) killer;
        ItemStack ledger = CaptainsLedgerItem.create(level, this.blockPosition(), this.random);
        if (ledger.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(ledger)) {
            player.spawnAtLocation(level, ledger);
        }
        player.sendSystemMessage(Component.translatable("message.nerocreatures.captains_ledger.received"), true);
    }

    /** Plants, flares the nacelles, then launches at the target. */
    private static final class JetDashGoal extends Goal {

        private final PirateCaptain captain;
        private int ticks;

        JetDashGoal(PirateCaptain captain) {
            this.captain = captain;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.captain.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return CaptainRules.mayDash(this.captain.distanceTo(target), this.captain.ticksSinceDash,
                    this.captain.onGround()) && this.captain.getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return this.ticks < CaptainRules.DASH_POSE_TICKS && this.captain.isAlive();
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.captain.ticksSinceDash = 0;
            this.captain.getNavigation().stop();
            this.captain.visuals().playPose(VisualPose.ABILITY, CaptainRules.DASH_POSE_TICKS);
            this.captain.playSound(SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.6F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.ticks++;
            LivingEntity target = this.captain.getTarget();
            if (target != null) {
                this.captain.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            if (this.ticks != CaptainRules.DASH_TELEGRAPH_TICKS || target == null
                    || !(this.captain.level() instanceof ServerLevel level)) {
                return;
            }
            double[] v = CaptainRules.dashVelocity(target.getX() - this.captain.getX(), target.getZ() - this.captain.getZ());
            this.captain.setDeltaMovement(v[0], v[1], v[2]);
            ModSounds.PIRATE_CAPTAIN_DASH.play(this.captain);
            level.sendParticles(ParticleTypes.FLAME, this.captain.getX(), this.captain.getY() + 1.6D, this.captain.getZ(),
                    12, 0.3D, 0.2D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.CLOUD, this.captain.getX(), this.captain.getY() + 0.2D, this.captain.getZ(),
                    8, 0.4D, 0.1D, 0.4D, 0.02D);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.PIRATE_CAPTAIN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.PIRATE_CAPTAIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PIRATE_CAPTAIN_DEATH.get();
    }
}
