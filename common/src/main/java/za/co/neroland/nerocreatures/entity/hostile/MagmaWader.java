package za.co.neroland.nerocreatures.entity.hostile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.ai.SlagLobGoal;
import za.co.neroland.nerocreatures.entity.ai.SlagLobRules;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * <b>Magma Wader</b> (Cindara, hostile): a small body on four very long stilt legs with a hanging
 * slag-sack throat.
 *
 * <h2>Ability: slag lob</h2>
 *
 * <ul>
 *   <li><b>Walks through lava.</b> Fire-proof (registered {@code fireImmune}), stands on a lava
 *       surface like a wading bird ({@link #canStandOnFluid}) and paths across lava and fire at no
 *       extra cost. While wading it holds the {@code rest} pose (the lava-wading stance).</li>
 *   <li><b>Lobs cooling slag</b> ({@link SlagLobGoal}): a 14-tick rear-back telegraph, then a slow
 *       arcing lob (lava-and-smoke trail) that lands where the target stood at launch. A landing
 *       burns and hurts whatever is within {@value SlagLobRules#SPLASH_RADIUS} blocks.</li>
 *   <li><b>The slag hardens into temporary basalt:</b> up to {@value SlagLobRules#MAX_CRUST_PER_LOB}
 *       {@code ephemeral_crust[variant=slag]} blocks per landing, into air over sturdy ground
 *       ({@code restore=air}, {@code Change.PLACE}) or over a lava source ({@code restore=lava},
 *       {@code Change.REPLACE}), never into a cell an entity is in. Each one passes
 *       {@code CreatureGriefing.mayAlter(.., magmaWaderSlag, ..)} first and decays back
 *       after 30-60 s. At most {@value SlagLobRules#MAX_LIVE_CRUST} live crust blocks per wader and
 *       {@value SlagLobRules#MAX_IN_FLIGHT} lobs in the air. Denied: the fire damage alone.</li>
 * </ul>
 *
 * <p>Player use: the crust bridges lava pools for a while, and the wader drops {@code heat_gland}
 * (fire-proof gear).
 *
 * <p>Lobs in flight and the live-crust count are in memory only: an unloaded wader's crust still
 * decays through the block's own scheduled tick, so nothing needs saving.
 */
public class MagmaWader extends NeroCreatureEntity implements Enemy {

    /** One slag lob in the air. */
    private static final class Lob {
        final Vec3 from;
        final Vec3 to;
        final double distance;
        final int flightTicks;
        int age;

        Lob(Vec3 from, Vec3 to) {
            this.from = from;
            this.to = to;
            this.distance = from.distanceTo(to);
            this.flightTicks = SlagLobRules.flightTicks(this.distance);
        }
    }

    private static final double LANDING_SPREAD = 0.6D;
    private static final int STANCE_CHECK_TICKS = 5;
    private static final int TRAIL_EVERY_TICKS = 2;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final List<Lob> lobs = new ArrayList<>(SlagLobRules.MAX_IN_FLIGHT);
    /** Game times at which this wader's live crust blocks decay. */
    private final Deque<Long> crustExpiry = new ArrayDeque<>(SlagLobRules.MAX_LIVE_CRUST);
    private int stanceCooldown;

    public MagmaWader(EntityType<? extends MagmaWader> type, Level level) {
        super(type, level, CreatureTier.COMMON);
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
        //? if >=26.1 {
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        //?} else {
        /*this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        *///?}
    }

    /** Slow stilt walker, long reach of step, middling health. */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SlagLobGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false));
        // A plain stroll, not the water-avoiding one: wading into lava is the point.
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Wades on the lava surface. */
    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    /** Lobs currently in the air (the goal asks before winding up). */
    public int lobsInFlight() {
        return this.lobs.size();
    }

    /** Throws one lob at where {@code target} stands now. Called by {@link SlagLobGoal}. */
    public void launchLob(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel) || !SlagLobRules.mayLaunch(this.lobs.size())) {
            return;
        }
        Vec3 from = this.getEyePosition();
        Vec3 to = target.position().add(
                (this.random.nextDouble() - 0.5D) * LANDING_SPREAD, 0.0D,
                (this.random.nextDouble() - 0.5D) * LANDING_SPREAD);
        this.lobs.add(new Lob(from, to));
        this.playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
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
        this.updateWadingStance(level);
        if (this.lobs.isEmpty()) {
            return;
        }
        Iterator<Lob> it = this.lobs.iterator();
        while (it.hasNext()) {
            Lob lob = it.next();
            lob.age++;
            if (lob.age >= lob.flightTicks) {
                it.remove();
                this.land(level, lob.to);
            } else if (lob.age % TRAIL_EVERY_TICKS == 0) {
                double f = (double) lob.age / lob.flightTicks;
                Vec3 at = lob.from.lerp(lob.to, f).add(0.0D, SlagLobRules.arcHeight(lob.distance, f), 0.0D);
                level.sendParticles(ModParticles.SLAG_EMBER.get(), at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
    }

    /**
     * The lava-wading stance: a held {@code rest} pose while standing in or on lava, back to
     * {@code idle} on land. Checked every {@value #STANCE_CHECK_TICKS} ticks; never interrupts the
     * timed {@code ability} (lob) pose.
     */
    private void updateWadingStance(ServerLevel level) {
        if (--this.stanceCooldown > 0) {
            return;
        }
        this.stanceCooldown = STANCE_CHECK_TICKS;
        VisualPose pose = this.visuals().pose();
        if (pose == VisualPose.ABILITY || pose == VisualPose.ABILITY_2) {
            return;
        }
        BlockPos feet = this.blockPosition();
        boolean wading = level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(feet.below()).is(FluidTags.LAVA);
        if (wading && pose != VisualPose.REST) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!wading && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    /** The landing: splash damage, then (gated, capped) crust. */
    private void land(ServerLevel level, Vec3 at) {
        level.sendParticles(ModParticles.SLAG_EMBER.get(), at.x, at.y + 0.2D, at.z, 6, 0.4D, 0.2D, 0.4D, 0.0D);
        ModSounds.MAGMA_WADER_SLAG_LAND.play(level, at.x, at.y, at.z, this.getSoundSource());
        AABB splash = new AABB(at, at).inflate(SlagLobRules.SPLASH_RADIUS);
        DamageSource source = this.damageSources().mobAttack(this);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, splash,
                e -> e.isAlive() && e != this && !(e instanceof MagmaWader))) {
            if (victim.hurtServer(level, source, SlagLobRules.SPLASH_DAMAGE)) {
                victim.igniteForSeconds(SlagLobRules.IGNITE_SECONDS);
            }
        }
        this.placeCrust(level, at);
    }

    /** Up to three crust blocks around the landing, each through {@code CreatureGriefing}. */
    private void placeCrust(ServerLevel level, Vec3 at) {
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        if (crust == null) {
            return;
        }
        long now = level.getGameTime();
        while (!this.crustExpiry.isEmpty() && this.crustExpiry.peekFirst() <= now) {
            this.crustExpiry.pollFirst();
        }
        if (SlagLobRules.crustBudget(true, this.crustExpiry.size()) <= 0) {
            return;
        }
        BlockPos center = impactCell(level, BlockPos.containing(at));
        List<BlockPos> columns = new ArrayList<>(5);
        columns.add(center);
        int rotate = this.random.nextInt(SIDES.length);
        for (int i = 0; i < SIDES.length; i++) {
            columns.add(center.relative(SIDES[(i + rotate) % SIDES.length]));
        }
        int placed = 0;
        for (BlockPos cell : columns) {
            if (placed >= SlagLobRules.MAX_CRUST_PER_LOB) {
                break;
            }
            BlockPos below = cell.below();
            BlockState belowState = level.getBlockState(below);
            FluidState belowFluid = belowState.getFluidState();
            boolean lava = belowState.is(Blocks.LAVA) && belowFluid.isSource() && belowFluid.is(FluidTags.LAVA);
            SlagLobRules.Target target = SlagLobRules.classify(lava, lava && occupied(level, below),
                    level.getBlockState(cell).isAir(), belowState.isFaceSturdy(level, below, Direction.UP),
                    !lava && occupied(level, cell));
            if (target == SlagLobRules.Target.NONE) {
                continue;
            }
            boolean onLava = target == SlagLobRules.Target.LAVA;
            BlockPos pos = onLava ? below : cell;
            BlockState state = crust.stateFor(EphemeralCrustBlock.Variant.SLAG,
                    onLava ? EphemeralCrustBlock.Restore.LAVA : EphemeralCrustBlock.Restore.AIR);
            boolean allowed = CreatureGriefing.mayAlter(level, this, pos, NeroCreaturesConfig.MAGMA_WADER_SLAG,
                    onLava ? CreatureGriefing.Change.REPLACE : CreatureGriefing.Change.PLACE, state);
            if (SlagLobRules.crustBudget(allowed, this.crustExpiry.size()) <= 0) {
                continue;
            }
            int decay = SlagLobRules.decayTicks(this.random.nextDouble());
            if (crust.place(level, pos, state, decay)) {
                this.crustExpiry.addLast(now + decay);
                placed++;
            }
        }
    }

    /** Settles the landing cell: up out of a solid block, down through open air (at most 3). */
    private static BlockPos impactCell(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        for (int i = 0; i < 2 && !level.getBlockState(pos).isAir(); i++) {
            pos = pos.above();
        }
        for (int i = 0; i < 3 && level.getBlockState(pos.below()).isAir(); i++) {
            pos = pos.below();
        }
        return pos;
    }

    private static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(Entity.class, new AABB(pos), Entity::isAlive).isEmpty();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.lobs.clear();
        super.remove(reason);
    }

    // Mapped vanilla events: the lava-strider set fits a lava wader.

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MAGMA_WADER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.MAGMA_WADER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MAGMA_WADER_DEATH.get();
    }
}
