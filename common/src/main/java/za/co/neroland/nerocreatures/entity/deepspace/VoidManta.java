package za.co.neroland.nerocreatures.entity.deepspace;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.spawn.HabitatTags;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.registry.ModParticles;
import za.co.neroland.nerolandcore.worldgen.SpaceTags;

/**
 * <b>Void Manta</b> (deep space and dark biomes; flying mount, tameable, breedable, lays eggs) — a
 * flat diamond disc that rows through low gravity on long slow wing beats.
 *
 * <p>Everything a mount does (taming as {@code MOUNT} against {@code maxMountsPerPlayer}, the
 * {@code creature_saddle}, owner-only riding, stamina, panniers behind Stout, egg breeding from
 * {@code species.json}) is the shared {@link MountCreature} base. The species adds:
 *
 * <p><b>Flight, gated by gravity</b> ({@link VoidMantaFlight#flightAllowed}): free flight where the
 * gravity attribute is reduced or in its own deep-space habitat; a glide (no climbing, slow sink)
 * everywhere else. While a rider flies it, the mount is marked no-gravity on the server, so a server
 * with {@code allow-flight=false} does not treat the ride as hovering. That mark is this class's own
 * and is undone on landing in glide country, on dismount, and on load; a no-gravity mark set by
 * anything else is never touched.
 *
 * <p><b>Blink.</b> The jump bar (stamina-limited by the base; Thrifty makes it cheaper) arms a blink
 * that fires {@value VoidMantaFlight#BLINK_DELAY_TICKS} ticks later on the controlling side, where
 * vanilla simulates the ridden vehicle: a block raycast and a loaded-chunk probe shorten it, then
 * one swept-box {@code move} carries the mount, so it never passes through a block or enters an
 * unloaded chunk. The server, which replays and validates the vehicle move as for a horse, plays the
 * pose, sound and particles. No world change, no payload.
 *
 * <p>Visuals: {@code ability} pose for the blink (20 ticks, the jump at tick 10), {@code rest} while
 * ordered to sit; saddle and panniers are the base's flags.
 */
public class VoidManta extends MountCreature {

    /** How often (ticks) the flight gate and the rest pose are re-checked. */
    private static final int CHECK_TICKS = 10;

    /** Ticks after the blink's origin burst that the arrival burst plays. */
    private static final int ARRIVAL_FX_DELAY = 3;

    /** Probe margin ahead of each blink sample (blocks). */
    private static final double LOADED_LEAD = 1.0D;

    private static final String LIFT_KEY = "nerocreatures:void_manta.lift";

    /** Both sides: whether free flight is allowed here, re-checked every {@value #CHECK_TICKS} ticks. */
    private boolean flight;
    private boolean flightChecked;

    /** Server: whether this class set the entity's no-gravity mark (and so may clear it). */
    private boolean managedLift;

    /** Controlling side: ticks until an armed blink fires, or 0. Not saved. */
    private int blinkCountdown;

    /** Server: ticks until the origin burst, then until the arrival burst. Not saved. */
    private int serverBlinkFx;
    private int serverArrivalFx;

    public VoidManta(EntityType<? extends VoidManta> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    /** A broad, sturdy flyer; never takes fall damage (it glides). */
    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.SAFE_FALL_DISTANCE, 1024.0D);
    }

    @Override
    public Item tamingFood() {
        return Items.CHORUS_FRUIT;
    }

    @Override
    public boolean breedable() {
        return true;
    }

    /** A mount's value is the ride; it lends its owner nothing passive. */
    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
    }

    // --- flight gate -----------------------------------------------------------------

    /** Free flight here (pitch climbs and dives); otherwise the base walks and this class glides. */
    @Override
    public boolean isFlyingMount() {
        if (!this.flightChecked) {
            this.refreshFlight();
        }
        return this.flight;
    }

    /** One attribute read, one dimension-type tag test and one biome lookup. */
    private void refreshFlight() {
        this.flightChecked = true;
        Level level = this.level();
        boolean space = level.dimensionTypeRegistration().is(SpaceTags.SPACE_DIMENSIONS);
        boolean deepSpace = space && level.getBiome(this.blockPosition()).is(HabitatTags.DEEP_SPACE);
        this.flight = VoidMantaFlight.flightAllowed(this.getAttributeValue(Attributes.GRAVITY), space, deepSpace);
    }

    // --- movement --------------------------------------------------------------------

    /**
     * Only runs where the mount is simulated (the rider's client, or the server when riderless). The
     * base applies the armed ability and flies or walks; this then fires a due blink and applies the
     * glide floor whenever the manta is airborne and not in free flight under a rider.
     */
    @Override
    public void travel(Vec3 input) {
        super.travel(input);
        boolean ridden = this.getControllingPassenger() instanceof Player;
        if (!ridden) {
            this.blinkCountdown = 0;
        } else if (this.blinkCountdown > 0 && --this.blinkCountdown == 0 && this.getControllingPassenger() instanceof Player rider) {
            this.blink(rider);
        }
        if (!this.onGround() && !(ridden && this.isFlyingMount())) {
            Vec3 motion = this.getDeltaMovement();
            double vertical = VoidMantaFlight.glideVertical(motion.y);
            if (vertical != motion.y) {
                this.setDeltaMovement(motion.x, vertical, motion.z);
            }
            this.resetFallDistance();
        }
    }

    // --- the blink -------------------------------------------------------------------

    /** Only one blink at a time: none armed, and the previous pose has played out. */
    @Override
    public boolean canJump() {
        return super.canJump()
                && VoidMantaFlight.mayArm(this.blinkCountdown, this.visuals().pose() == VisualPose.ABILITY);
    }

    /** Controlling side: arm the blink; {@link #travel} fires it at the wing-fold. */
    @Override
    protected void performAbilityMotion(Player rider, float strength, Vec3 input) {
        this.blinkCountdown = VoidMantaFlight.BLINK_DELAY_TICKS;
    }

    /** Controlling side: raycast, chunk probe, then one swept-box move. Never through a block. */
    private void blink(Player rider) {
        Level level = this.level();
        double[] d = VoidMantaFlight.direction(rider.getYRot(), rider.getXRot(), this.isFlyingMount());
        Vec3 direction = new Vec3(d[0], d[1], d[2]);
        Vec3 start = this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
        Vec3 end = start.add(direction.scale(VoidMantaFlight.BLINK_DISTANCE + VoidMantaFlight.BLOCK_CLEARANCE));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double hitDistance = hit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY
                : hit.getLocation().distanceTo(start);
        double halfWidth = this.getBbWidth() * 0.5D + 0.5D;
        double reach = VoidMantaFlight.loadedReach(LOADED_LEAD,
                along -> boxLoaded(level, start.add(direction.scale(along)), halfWidth));
        double distance = VoidMantaFlight.blinkDistance(hitDistance, reach);
        if (distance <= 0.0D) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        this.move(MoverType.SELF, direction.scale(distance));
        this.setDeltaMovement(motion);
        this.resetFallDistance();
    }

    /** Whether the centre and the four horizontal corners around {@code centre} are in loaded chunks. */
    private static boolean boxLoaded(Level level, Vec3 centre, double halfWidth) {
        return level.isLoaded(BlockPos.containing(centre))
                && level.isLoaded(BlockPos.containing(centre.x - halfWidth, centre.y, centre.z - halfWidth))
                && level.isLoaded(BlockPos.containing(centre.x + halfWidth, centre.y, centre.z - halfWidth))
                && level.isLoaded(BlockPos.containing(centre.x - halfWidth, centre.y, centre.z + halfWidth))
                && level.isLoaded(BlockPos.containing(centre.x + halfWidth, centre.y, centre.z + halfWidth));
    }

    /** Server half: the pose now, the bursts when the blink lands. Stamina is already spent. */
    @Override
    protected void onAbilityServer(ServerLevel level, ServerPlayer rider, float strength) {
        this.visuals().playPose(VisualPose.ABILITY, VoidMantaFlight.BLINK_POSE_TICKS);
        this.serverBlinkFx = VoidMantaFlight.BLINK_DELAY_TICKS;
        this.serverArrivalFx = 0;
    }

    // --- tick ------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!this.flightChecked || this.tickCount % CHECK_TICKS == 0) {
            this.refreshFlight();
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.tickLift();
        this.tickBlinkFx(level);
        if (this.tickCount % CHECK_TICKS == 0) {
            if (this.isOrderedToSit()) {
                this.visuals().holdPose(VisualPose.REST);
            } else if (this.visuals().pose() == VisualPose.REST) {
                this.visuals().holdPose(VisualPose.IDLE);
            }
        }
    }

    /** Server: the no-gravity mark is on exactly while a rider flies it freely, and only if ours. */
    private void tickLift() {
        boolean wanted = this.flight && this.getControllingPassenger() != null;
        if (wanted && !this.managedLift && !this.isNoGravity()) {
            this.setNoGravity(true);
            this.managedLift = true;
        } else if (!wanted && this.managedLift) {
            this.setNoGravity(false);
            this.managedLift = false;
        }
    }

    // Particles: vanilla reverse_portal until ModParticles has void_trail; one swap each.
    private void tickBlinkFx(ServerLevel level) {
        if (this.serverBlinkFx > 0 && --this.serverBlinkFx == 0) {
            level.sendParticles(ModParticles.VOID_TRAIL.get(), this.getX(), this.getY() + 0.4D, this.getZ(),
                    16, 1.0D, 0.2D, 1.0D, 0.02D);
            ModSounds.VOID_MANTA_BLINK.play(this);
            this.serverArrivalFx = ARRIVAL_FX_DELAY;
        } else if (this.serverArrivalFx > 0 && --this.serverArrivalFx == 0) {
            level.sendParticles(ModParticles.VOID_TRAIL.get(), this.getX(), this.getY() + 0.4D, this.getZ(),
                    16, 1.0D, 0.2D, 1.0D, 0.02D);
        }
    }

    // --- persistence: only the lift mark, so a save taken mid-flight never floats forever ---------

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
        if (this.managedLift) {
            output.store(LIFT_KEY, Codec.BOOL, true);
        }
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
        // Loaded riderless: drop our own no-gravity mark; a mark someone else set is kept.
        if (input.read(LIFT_KEY, Codec.BOOL).orElse(false)) {
            this.setNoGravity(false);
        }
        this.managedLift = false;
    }

    // --- sounds (mapped vanilla events; synthesised ones arrive with the sound pass) -------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.VOID_MANTA_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.VOID_MANTA_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VOID_MANTA_DEATH.get();
    }
}
