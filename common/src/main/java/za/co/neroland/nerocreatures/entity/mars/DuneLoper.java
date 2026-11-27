package za.co.neroland.nerocreatures.entity.mars;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * <b>Dune Loper</b> (Mars; mount, tameable, breedable, live birth) — a digitigrade biped that
 * crosses the dunes in long low-gravity bounds.
 *
 * <p>Everything a mount does (taming as {@code MOUNT} against {@code maxMountsPerPlayer}, the
 * {@code creature_saddle}, owner-only riding, stamina, panniers behind Stout) is the shared
 * {@link MountCreature} base. The species adds exactly one thing, its ability:
 *
 * <p><b>Bounding leap.</b> The jump bar (stamina-limited by the base; Thrifty makes it cheaper)
 * launches a forward bound along the mount's facing, and for up to
 * {@value DuneLoperLeap#LOW_GRAVITY_WINDOW_TICKS} ticks the mount falls at a fraction of its normal
 * gravity. The window is a hard cap and ends on landing, so the leap never turns into flight. Fall
 * distance is forgiven for the window plus a short grace on both sides of the connection: the
 * controlling client (which simulates the ridden mount) and the server (which applies fall damage).
 *
 * <p>Movement happens only where vanilla simulates the vehicle, through {@link #performAbilityMotion}
 * and {@link #travel}; stamina is spent on the server by the base. No world change, no payload.
 *
 * <p>Visuals: {@code ability} pose for the leap (crouch telegraph, extension, tucked landing),
 * {@code rest} while ordered to sit; saddle and panniers are the base's flags.
 */
public class DuneLoper extends MountCreature {

    /** How often (ticks) the server re-checks the rest pose. */
    private static final int POSE_CHECK_TICKS = 10;

    /** Controlling side: low-gravity ticks left in the current leap. Not saved. */
    private int leapWindow;

    /** Controlling side: ticks since take-off, or -1 when not leaping. */
    private int leapAge = -1;

    /** Server: ticks since the last leap, or -1. Drives the fall-damage grace. Not saved. */
    private int serverLeapAge = -1;

    public DuneLoper(EntityType<? extends DuneLoper> type, Level level) {
        super(type, level, CreatureTier.COMMON);
        //? if <1.20.5 {
        /*this.setMaxUpStep(1.1F);
        *///?}
    }

    /** Sturdy and quick, with a tall step for dune ridges. */
    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                //? if >=1.20.5 {
                .add(Attributes.STEP_HEIGHT, 1.1D)
                //?}
                .add(Attributes.SAFE_FALL_DISTANCE, 6.0D);
    }

    @Override
    public Item tamingFood() {
        return Items.BEETROOT;
    }

    @Override
    public boolean breedable() {
        return true;
    }

    /** A mount's value is the ride; it lends its owner nothing passive. */
    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
    }

    // --- the leap ------------------------------------------------------------------

    /** Controlling side: the forward bound, then the low-gravity window opens. */
    @Override
    protected void performAbilityMotion(Player rider, float strength, Vec3 input) {
        if (!this.onGround()) {
            return;
        }
        double up = DuneLoperLeap.upwardSpeed(strength);
        double forward = DuneLoperLeap.forwardSpeed(strength, input.z);
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x - Mth.sin(yaw) * forward, up, motion.z + Mth.cos(yaw) * forward);
        this.leapWindow = DuneLoperLeap.LOW_GRAVITY_WINDOW_TICKS;
        this.leapAge = 0;
        this.resetFallDistance();
    }

    /**
     * Only runs where the mount is simulated. After vanilla's step (which applied full gravity) the
     * share of gravity the loper does not feel is handed back as lift, inside the capped window.
     */
    @Override
    public void travel(Vec3 input) {
        super.travel(input);
        if (this.leapAge < 0) {
            return;
        }
        double gravity = this.getAttributeValue(Attributes.GRAVITY);
        double lift = DuneLoperLeap.liftPerTick(gravity, this.leapWindow, this.onGround());
        if (lift > 0.0D) {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x, motion.y + lift, motion.z);
        }
        if (DuneLoperLeap.fallForgiven(this.leapAge)) {
            this.resetFallDistance();
        }
        this.leapWindow = DuneLoperLeap.nextWindow(this.leapWindow, this.onGround(), this.leapAge);
        this.leapAge++;
        if (!DuneLoperLeap.fallForgiven(this.leapAge)) {
            this.leapAge = -1;
        }
    }

    /** Server half: the pose, a sound, dust, and the server-side fall grace. Stamina is already spent. */
    @Override
    protected void onAbilityServer(ServerLevel level, ServerPlayer rider, float strength) {
        this.serverLeapAge = 0;
        this.visuals().playPose(VisualPose.ABILITY, DuneLoperLeap.LEAP_POSE_TICKS);
        ModSounds.DUNE_LOPER_LEAP.play(this);
        level.sendParticles(ModParticles.DUST_SWIRL.get(), this.getX(), this.getY() + 0.1D, this.getZ(),
                6, 0.5D, 0.05D, 0.5D, 0.02D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        if (this.serverLeapAge >= 0) {
            if (DuneLoperLeap.fallForgiven(this.serverLeapAge)) {
                this.resetFallDistance();
                this.serverLeapAge++;
            } else {
                this.serverLeapAge = -1;
            }
        }
        if (this.tickCount % POSE_CHECK_TICKS == 0) {
            if (this.isOrderedToSit()) {
                this.visuals().holdPose(VisualPose.REST);
            } else if (this.visuals().pose() == VisualPose.REST) {
                this.visuals().holdPose(VisualPose.IDLE);
            }
        }
    }

    // --- sounds (mapped vanilla events; synthesised ones arrive with the sound pass) -------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.DUNE_LOPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.DUNE_LOPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DUNE_LOPER_DEATH.get();
    }
}
