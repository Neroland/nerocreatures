package za.co.neroland.nerocreatures.entity.hostile;

import java.util.EnumSet;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.entity.ai.PackCoordination;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * <b>Lunar Stalker</b> — the pack hunter of the regolith moons.
 *
 * <p>Three things make a stalker a stalker:
 *
 * <ol>
 *   <li><b>It hunts in a pack.</b> The first stalker to acquire a target broadcasts it to its
 *       neighbours through {@link PackCoordination#broadcastTarget}. That happens once, on
 *       acquisition, never per tick.</li>
 *   <li><b>It flanks.</b> Beyond melee range each member paths to its own
 *       {@linkplain PackCoordination#flankOffset flank offset} around the target instead of queueing
 *       up behind the leader, so a pack surrounds you.</li>
 *   <li><b>It gets bolder after dark.</b> {@link PackCoordination#nightBoldness} scales both
 *       detection range and approach speed while it is dark outside.</li>
 * </ol>
 *
 * <p><b>Low gravity is a tag lookup, never a mod reference.</b> If the biome it spawns in carries
 * {@code nerospace:gravity_low}, the stalker's own gravity is reduced and its safe-fall distance
 * raised, so it lopes across a low-gravity moon the way the terrain implies. The tag id is a plain
 * {@link Identifier} — NeroCreatures declares no dependency on Nerospace and behaves identically
 * (just heavier-footed) when that tag does not exist.
 *
 * <p>Spawns in Core's {@code neroland:space/moon_biomes}. Drops {@code stalker_hide} and
 * {@code stalker_sinew}.
 *
 * <p>Server-authoritative: the pack broadcast is guarded to the server and no player identity is
 * retained anywhere (POPIA/GDPR).
 */
public class LunarStalker extends NeroCreatureEntity implements Enemy {

    /**
     * Nerospace's low-gravity biome tag, referenced by <b>id only</b>. Resolves to an empty tag —
     * and therefore to "normal gravity" — on any install without Nerospace.
     */
    private static final TagKey<Biome> GRAVITY_LOW = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("nerospace", "gravity_low"));

    /** Gravity multiplier applied in a low-gravity biome. */
    private static final double LOW_GRAVITY_FACTOR = 0.55D;

    /** Extra blocks of safe fall granted in a low-gravity biome — long lopes, no self-damage. */
    private static final double LOW_GRAVITY_SAFE_FALL_BONUS = 6.0D;

    /** How far a target acquisition is shouted to pack-mates, in blocks. */
    private static final double PACK_RADIUS = 16.0D;

    /** Re-entrancy guard: a recruited pack-mate must not start its own recruiting cascade. */
    private boolean broadcasting;

    public LunarStalker(EntityType<? extends LunarStalker> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    /**
     * {@link CreatureTier#COMMON} tuned lean: a little quicker and a little squishier than the band
     * default, because the threat is the number of them, not any one of them.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Flanking outranks melee: while the target is further than the flank hand-over distance the
        // stalker circles to its own side, and only then closes.
        this.goalSelector.addGoal(2, new FlankApproachGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // setAlertOthers() with no arguments = "alert my own kind"; the varargs are classes to
        // exclude, not classes to alert.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NightBoldTargetGoal(this));
    }

    /**
     * Pack broadcast, fired exactly once per acquisition. The guard matters: without it a recruited
     * mate would immediately recruit again and one sighting would ripple across the whole moon.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = this.getTarget();
        super.setTarget(target);
        if (this.broadcasting || target == null || previous != null || this.level().isClientSide()) {
            return;
        }
        this.broadcasting = true;
        try {
            PackCoordination.broadcastTarget(this, LunarStalker.class, PACK_RADIUS, target);
        } finally {
            this.broadcasting = false;
        }
    }

    /**
     * Applies the low-gravity tuning once, at spawn, from the biome the stalker actually appeared
     * in. Deliberately not a per-tick biome lookup: a stalker that walks off a low-gravity plateau
     * keeps its stride, which is both cheaper and less surprising than gravity flickering mid-leap.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        if (level.getBiome(this.blockPosition()).is(GRAVITY_LOW)) {
            applyLowGravity();
        }
        return result;
    }

    private void applyLowGravity() {
        AttributeInstance gravity = this.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.setBaseValue(gravity.getBaseValue() * LOW_GRAVITY_FACTOR);
        }
        AttributeInstance safeFall = this.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFall != null) {
            safeFall.setBaseValue(safeFall.getBaseValue() + LOW_GRAVITY_SAFE_FALL_BONUS);
        }
    }

    // Vanilla sound events, mapped rather than shipped: the polar bear's set is the right weight for
    // a big quadruped predator. (The wolf set is deliberately not used — 26.x moved it behind the
    // per-variant wolf sound registry, so it is no longer a plain SoundEvents constant.)

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    /**
     * Target acquisition with the night-boldness multiplier folded into the detection distance —
     * the one place the multiplier belongs, since everything else about targeting is vanilla's.
     */
    private static final class NightBoldTargetGoal extends NearestAttackableTargetGoal<Player> {

        NightBoldTargetGoal(LunarStalker stalker) {
            super(stalker, Player.class, true);
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * PackCoordination.nightBoldness(this.mob.level());
        }
    }

    /**
     * Circles to this member's own side of the target instead of walking straight at it.
     *
     * <h2>Cost profile</h2>
     *
     * <ul>
     *   <li>One navigation call every {@value #REPATH_INTERVAL_TICKS} ticks — the same order as
     *       vanilla {@code MeleeAttackGoal}, and the only expensive operation in the goal.</li>
     *   <li>{@link #canUse()} is a null check and one squared distance. No entity scans, no ray
     *       casts; the flank offset is pure arithmetic keyed off the entity id.</li>
     *   <li>Hands over to melee inside {@value #HANDOVER_DISTANCE} blocks, so the two goals never
     *       fight over the MOVE flag for the same stretch of ground.</li>
     * </ul>
     */
    private static final class FlankApproachGoal extends Goal {

        /** Ticks between navigation updates while circling. */
        private static final int REPATH_INTERVAL_TICKS = 10;

        /** Inside this distance (blocks) melee takes over. */
        private static final double HANDOVER_DISTANCE = 6.0D;

        /** How far off the target the flank position sits, in blocks. */
        private static final double FLANK_DISTANCE = 4.0D;

        private final LunarStalker stalker;
        private int repathCooldown;

        FlankApproachGoal(LunarStalker stalker) {
            this.stalker = stalker;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.stalker.getTarget();
            return target != null && target.isAlive()
                    && this.stalker.distanceToSqr(target) > HANDOVER_DISTANCE * HANDOVER_DISTANCE;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
        }

        @Override
        public void stop() {
            this.stalker.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--this.repathCooldown > 0) {
                return;
            }
            this.repathCooldown = REPATH_INTERVAL_TICKS;
            LivingEntity target = this.stalker.getTarget();
            if (target == null) {
                return;
            }
            Vec3 flank = PackCoordination.flankOffset(this.stalker, target, FLANK_DISTANCE);
            double speed = PackCoordination.nightBoldness(this.stalker.level());
            this.stalker.getNavigation().moveTo(flank.x(), flank.y(), flank.z(), speed);
        }
    }
}
