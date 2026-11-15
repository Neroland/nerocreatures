package za.co.neroland.nerocreatures.entity.greenxertz;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;
import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Drift Leviathan</b> (Greenxertz skies and gas giants, ambient) — a very long flattened whale-ray
 * with slow side fins and a barnacled deck for a back. Enormous, slow, peaceful.
 *
 * <h2>Standing on its back</h2>
 *
 * <p>The approach (see {@link LeviathanRules} for the numbers): <b>no passengers, no carrying.</b> The
 * back is the top face of its collision box: the art's deck footprint, 3.5 x 8.75 blocks with its top
 * 3.0 above the origin, snapped to whichever axis the body lies along (it drifts mostly along an axis
 * and turns the box only when the turned box is free; synced). On 1.21.6+ {@code canBeCollidedWith(other)} is true only
 * for an entity whose feet are at or above the deck, on client and server alike, so a player who lands
 * on it stands on it and nobody is blocked from the side or below; before 1.21.6 the no-argument form
 * makes the whole box solid (entities overlapping it can still walk out; entity collision never
 * suffocates). Clients own player movement, so instead of dragging players along the leviathan
 * <b>holds still while any player is on its deck</b> (checked every tick over the level's player list,
 * held {@value LeviathanRules#STILL_TICKS} ticks after the last one leaves), as vanilla does for its
 * own floating platform. The deck therefore moves with it and never moves under a standing player.
 * It is not pushable and ignores knockback.
 *
 * <h2>Use: barnacles</h2>
 *
 * <p>{@code leviathan_barnacle} regrows on a {@link ProductTimer} (one day, visuals {@code stage} 0-3).
 * Shears on a leviathan with ready barnacles (from its back, or within reach) put 2-3 barnacles in the
 * player's inventory, wear the shears by one, and restart growth. Saved. A natural leviathan starts
 * with barnacles half to fully grown.
 *
 * <h2>Population</h2>
 *
 * <p>At most {@value LeviathanRules#MAX_PER_DIMENSION} loaded per dimension ({@link #maySpawnNaturally}
 * is the spawn gate). It is saved with its chunk (barnacles and axis included); it skips vanilla's
 * random despawn and leaves only past the hard 128-block range. A named one stays. It lifts itself to
 * cruising altitude when it spawns.
 */
public class DriftLeviathan extends NeroCreatureEntity {

    private static final Identifier BARNACLE = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "leviathan_barnacle");
    private static final String BARNACLE_KEY = "nerocreatures:barnacles";
    private static final String AXIS_KEY = "nerocreatures:along_x";
    /** Which axis the deck footprint lies along (synced so client and server collide alike). */
    private static final EntityDataAccessor<Boolean> DATA_ALONG_X =
            SynchedEntityData.defineId(DriftLeviathan.class, EntityDataSerializers.BOOLEAN);
    private static final int SONG_CHANCE = 30;
    private static final int LIFT_TRIES = 6;

    static final DimensionTally LOADED = new DimensionTally();

    private final ProductTimer barnacles = new ProductTimer(LeviathanRules.BARNACLE_REGROW_TICKS,
            LeviathanRules.BARNACLE_STAGES, LeviathanRules.BARNACLE_MAX_YIELD);
    private final CadenceTimer songCadence = new CadenceTimer(600);
    /** Ticks left holding still (a player is or was just on the deck). Server only, not saved. */
    private int stillTicks;
    /** Mirror of {@link #DATA_ALONG_X}, read by the bounding box (never reads synced data itself). */
    private boolean alongX;

    public DriftLeviathan(EntityType<? extends DriftLeviathan> type, Level level) {
        super(type, level, CreatureTier.GREATER);
        this.moveControl = new DriftControl(this);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.GREATER)
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.05D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new DriftGoal(this));
    }

    /** Spawn gate for the integrator's spawn rule: below the per-dimension cap. */
    public static boolean maySpawnNaturally(ServerLevel level) {
        return LeviathanRules.maySpawnNaturally(LOADED.count(dimensionKey(level), id -> isLoadedLeviathan(level, id)));
    }

    public boolean isStill() {
        return this.stillTicks > 0;
    }

    // --- the deck ----------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return this.isAlive() && other != null && other != this
                && LeviathanRules.collidesFromAbove(other.getY(), this.getBoundingBox().maxY);
    }
    //?} else {
    /*@Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }
    *///?}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ALONG_X, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ALONG_X.equals(key)) {
            this.alongX = this.entityData.get(DATA_ALONG_X);
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    /** The deck footprint (3.5 x 8.75, 3.0 high) snapped to the axis the body lies along. */
    //? if >=1.21.4 {
    @Override
    protected AABB makeBoundingBox(Vec3 at) {
    //?} else {
    /*@Override
    protected AABB makeBoundingBox() {
        Vec3 at = this.position();
    *///?}
        double[] half = LeviathanRules.halfExtents(this.alongX);
        double height = this.getBbHeight();
        return new AABB(at.x - half[0], at.y, at.z - half[1], at.x + half[0], at.y + height, at.z + half[1]);
    }

    /** Turns the footprint to the body's nearer axis when the turned box is free. Never while still. */
    private void realign(ServerLevel level) {
        boolean wanted = LeviathanRules.alongX(this.getYRot());
        if (wanted == this.alongX) {
            return;
        }
        double[] half = LeviathanRules.halfExtents(wanted);
        AABB turned = new AABB(this.getX() - half[0], this.getY(), this.getZ() - half[1],
                this.getX() + half[0], this.getY() + this.getBbHeight(), this.getZ() + half[1]);
        if (level.noCollision(this, turned)) {
            this.entityData.set(DATA_ALONG_X, wanted);
            this.alongX = wanted;
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            this.stillTicks = LeviathanRules.stillTimer(this.stillTicks, this.playerOnDeck(level));
            if (this.isStill()) {
                this.setDeltaMovement(Vec3.ZERO);
            } else if (this.tickCount % 10 == 0) {
                this.realign(level);
            }
        }
    }

    /** Any non-spectator player standing on (or just above) the deck. Iterates the level's players only. */
    private boolean playerOnDeck(ServerLevel level) {
        AABB deck = this.getBoundingBox();
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.isAlive() && LeviathanRules.onDeck(player.getY(), player.getX(),
                    player.getZ(), deck.maxY, deck.minX, deck.maxX, deck.minZ, deck.maxZ)) {
                return true;
            }
        }
        return false;
    }

    // --- barnacles and song ------------------------------------------------------------

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
        if ((this.tickCount + this.getId()) % LeviathanRules.GROWTH_STEP == 0) {
            this.barnacles.advance(LeviathanRules.GROWTH_STEP);
            this.visuals().setStage(this.barnacles.stage());
        }
        if (this.farTick().skip(this) || !this.songCadence.ready(level.getGameTime(), this.getId())) {
            return;
        }
        if (this.visuals().pose() == VisualPose.IDLE && this.random.nextInt(SONG_CHANCE) < 10) {
            this.visuals().playPose(VisualPose.ABILITY, LeviathanRules.SONG_POSE_TICKS);
            ModSounds.DRIFT_LEVIATHAN_SONG.play(this);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof ShearsItem)) {
            return super.mobInteract(player, hand);
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return this.visuals().stage() >= LeviathanRules.BARNACLE_STAGES ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Item barnacle = BuiltInRegistries.ITEM.getValue(BARNACLE);
        if (barnacle == Items.AIR || !this.barnacles.isReady()) {
            return InteractionResult.PASS;
        }
        int bonus = this.random.nextInt(2);
        int count = this.barnacles.harvest(LeviathanRules.BARNACLE_BASE_YIELD, base -> LeviathanRules.barnacleYield(base, bonus));
        this.visuals().setStage(this.barnacles.stage());
        if (count > 0) {
            ItemStack stack = new ItemStack(barnacle, count);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5D, player.getZ(), stack));
            }
        }
        held.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        level.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY(), player.getZ(), 6, 0.4D, 0.1D, 0.4D, 0.0D);
        return za.co.neroland.nerocreatures.bestiary.BestiaryProgress.noteHarvest(player, this, InteractionResult.SUCCESS_SERVER);
    }

    // --- population -----------------------------------------------------------------------

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason spawnReason, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        if (spawnReason == EntitySpawnReason.NATURAL || spawnReason == EntitySpawnReason.CHUNK_GENERATION) {
            this.liftToCruise(level);
            // M3: barnacles start partly grown, so one found in the wild is worth landing on.
            this.barnacles.setProgress(LeviathanRules.seedBarnacles(this.barnacles.regrowTicks(),
                    this.random.nextDouble()));
            this.visuals().setStage(this.barnacles.stage());
        }
        LOADED.force(dimensionKey(level.getLevel()), this.getId());
        return result;
    }

    /** Rises to cruising altitude where the air is free; stays put otherwise. Never loads a chunk. */
    private void liftToCruise(ServerLevelAccessor level) {
        BlockPos at = this.blockPosition();
        int top = level.getMinY() + level.getHeight();
        double target = LeviathanRules.clampAltitude(this.getY() + LeviathanRules.MIN_ALTITUDE + 8, at.getY(), top);
        for (int i = 0; i < LIFT_TRIES; i++) {
            double dy = target - this.getY() - i * 2.0D;
            if (dy <= 0.0D) {
                return;
            }
            if (level.noCollision(this, this.getBoundingBox().move(0.0D, dy, 0.0D))) {
                this.setPos(this.getX(), this.getY() + dy, this.getZ());
                return;
            }
        }
    }

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        LOADED.force(dimensionKey(level), this.getId());
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            LOADED.remove(dimensionKey(level), this.getId());
        }
        super.remove(reason);
    }

    /**
     * Saved like any mob (M3; the old "never saved" rule wiped barnacle progress on every unload).
     * Vanilla's random despawn between 32 and 128 blocks is skipped; the hard range still applies.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSqr) {
        return super.removeWhenFarAway(distanceToClosestPlayerSqr)
                && LeviathanRules.mayDespawnAt(distanceToClosestPlayerSqr);
    }

    private static boolean isLoadedLeviathan(ServerLevel level, int id) {
        return level.getEntity(id) instanceof DriftLeviathan leviathan && leviathan.isAlive();
    }

    private static String dimensionKey(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    // --- sounds (mapped vanilla until the sound pass) --------------------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return ModSounds.DRIFT_LEVIATHAN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.DRIFT_LEVIATHAN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DRIFT_LEVIATHAN_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    // --- persistence -------------------------------------------------------------------------

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
        output.putInt(BARNACLE_KEY, this.barnacles.progress());
        output.putBoolean(AXIS_KEY, this.alongX);
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
        this.barnacles.setProgress(input.getIntOr(BARNACLE_KEY, 0));
        boolean axis = input.getBooleanOr(AXIS_KEY, false);
        this.entityData.set(DATA_ALONG_X, axis);
        this.alongX = axis;
        this.setBoundingBox(this.makeBoundingBox());
        this.visuals().setStage(this.barnacles.stage());
    }

    // --- movement ------------------------------------------------------------------------------

    /** Slow, collision-checked drift toward the wanted position; nothing at all while still. */
    static final class DriftControl extends MoveControl {

        private int recheck;

        DriftControl(DriftLeviathan leviathan) {
            super(leviathan);
        }

        @Override
        public void tick() {
            DriftLeviathan leviathan = (DriftLeviathan) this.mob;
            if (leviathan.isStill()) {
                this.operation = MoveControl.Operation.WAIT;
                return;
            }
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                return;
            }
            Vec3 to = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
            double distance = to.length();
            if (distance < 2.0D) {
                this.operation = MoveControl.Operation.WAIT;
                return;
            }
            Vec3 dir = to.scale(1.0D / distance);
            if (--this.recheck <= 0) {
                this.recheck = 20 + this.mob.getRandom().nextInt(10);
                if (!this.canReach(dir, (int) Math.ceil(Math.min(distance, 8.0D)))) {
                    this.operation = MoveControl.Operation.WAIT;
                    return;
                }
            }
            Vec3 v = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(LeviathanRules.accelerate(v.x, dir.x),
                    LeviathanRules.accelerate(v.y, dir.y * 0.5D), LeviathanRules.accelerate(v.z, dir.z));
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 2.0F));
            this.mob.yBodyRot = this.mob.getYRot();
        }

        private boolean canReach(Vec3 dir, int steps) {
            AABB box = this.mob.getBoundingBox();
            for (int i = 1; i <= steps; i++) {
                box = box.move(dir);
                if (!this.mob.level().noCollision(this.mob, box)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Picks a new cruising target on a slow cadence; idle while a player stands on the deck. */
    static final class DriftGoal extends Goal {

        private final DriftLeviathan leviathan;
        private final CadenceTimer cadence = new CadenceTimer(LeviathanRules.DRIFT_INTERVAL);

        DriftGoal(DriftLeviathan leviathan) {
            this.leviathan = leviathan;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.leviathan.isStill() || !(this.leviathan.level() instanceof ServerLevel level)) {
                return false;
            }
            MoveControl control = this.leviathan.getMoveControl();
            return (!control.hasWanted() || this.leviathan.getRandom().nextInt(4) == 0)
                    && this.cadence.ready(level.getGameTime(), this.leviathan.getId());
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Level level = this.leviathan.level();
            int range = LeviathanRules.DRIFT_RANGE;
            // Mostly along one axis, so the body (and its snapped deck footprint) lies along it.
            double along = (this.leviathan.getRandom().nextBoolean() ? 1.0D : -1.0D)
                    * (range / 2.0D + this.leviathan.getRandom().nextDouble() * range / 2.0D);
            double across = (this.leviathan.getRandom().nextDouble() * 2.0D - 1.0D) * 4.0D;
            boolean xAxis = this.leviathan.getRandom().nextBoolean();
            double x = this.leviathan.getX() + (xAxis ? along : across);
            double z = this.leviathan.getZ() + (xAxis ? across : along);
            BlockPos column = BlockPos.containing(x, this.leviathan.getY(), z);
            if (!level.isLoaded(column)) {
                return;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            int top = level.getMinY() + level.getHeight();
            double y = LeviathanRules.clampAltitude(this.leviathan.getY() + this.leviathan.getRandom().nextInt(9) - 4,
                    ground, top);
            this.leviathan.getMoveControl().setWantedPosition(x, y, z, 1.0D);
        }
    }
}
