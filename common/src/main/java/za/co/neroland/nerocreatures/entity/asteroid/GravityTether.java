package za.co.neroland.nerocreatures.entity.asteroid;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.ai.shared.TetherGoal;
import za.co.neroland.nerocreatures.entity.ai.shared.TetherState;

/**
 * The Gravity Leech tether effect: a transient {@code minecraft:gravity} modifier on the target,
 * server-authoritative (attribute changes sync to the client through vanilla's attribute packet).
 *
 * <p>How it always ends:
 * <ul>
 *   <li>{@link #release} on every tether end (break, timeout, target loss, death, removal), using a weak
 *       reference to the latched entity, so a target that changed dimension is still cleared;</li>
 *   <li>a lease renewed every hold pulse; {@link #sweep} removes any modifier whose lease ran out (the
 *       leech stopped ticking in a frozen chunk). Every leech calls {@link #sweepIfDue}; the integrator may
 *       also call it from the server tick;</li>
 *   <li>a transient modifier is never saved, so a logout, a death or a restart drops it.</li>
 * </ul>
 * No identity is stored: leases are keyed by the live entity object (weakly) and die with it.
 *
 * <p>Server thread only.
 */
public final class GravityTether implements TetherGoal.TetherEffect {

    /** The one modifier id; re-applied (remove, then add) each pulse. */
    public static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "gravity_leech_tether");

    private static final int SWEEP_INTERVAL_TICKS = 20;

    /** Target -> server tick its lease ends. Weak keys: an unloaded entity simply drops out. */
    private static final Map<LivingEntity, Long> LEASES = new WeakHashMap<>();
    private static long nextSweep = Long.MIN_VALUE;

    private GravityTetherRules.Mode mode = GravityTetherRules.Mode.LIGHTEN;
    private long latchedAt;
    @Nullable
    private WeakReference<LivingEntity> held;

    @Override
    public void latch(ServerLevel level, PathfinderMob tetherer, LivingEntity target) {
        this.mode = GravityTetherRules.chooseMode(tetherer.getRandom().nextDouble());
        this.latchedAt = level.getGameTime();
        this.held = new WeakReference<>(target);
        apply(level, target, GravityTetherRules.amount(this.mode, 0L));
        level.playSound(null, tetherer.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.6F, 1.8F);
        if (tetherer instanceof GravityLeech leech) {
            leech.onLatched();
        }
    }

    @Override
    public void hold(ServerLevel level, PathfinderMob tetherer, LivingEntity target) {
        apply(level, target, GravityTetherRules.amount(this.mode, level.getGameTime() - this.latchedAt));
        trail(level, tetherer, target);
    }

    @Override
    public void release(ServerLevel level, PathfinderMob tetherer, @Nullable LivingEntity target,
            TetherState.Status why) {
        LivingEntity latched = target != null ? target : (this.held == null ? null : this.held.get());
        if (latched != null) {
            clear(latched);
        }
        this.held = null;
        if (tetherer instanceof GravityLeech leech) {
            leech.onReleased();
        }
    }

    /** The mode of the current latch (for the visuals and tests). */
    public GravityTetherRules.Mode mode() {
        return this.mode;
    }

    /** Puts (or refreshes) the modifier and renews the lease. */
    static void apply(ServerLevel level, LivingEntity target, double amount) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        MinecraftServer server = level.getServer();
        if (gravity == null || server == null) {
            return;
        }
        if (!LEASES.containsKey(target) && !GravityTetherRules.hasLeaseRoom(LEASES.size())) {
            return; // table full: no new effect (old ones still expire)
        }
        gravity.removeModifier(MODIFIER_ID);
        gravity.addTransientModifier(new AttributeModifier(MODIFIER_ID, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        LEASES.put(target, GravityTetherRules.leaseUntil(server.getTickCount()));
    }

    /** Removes the modifier and forgets the lease. Safe to call repeatedly. */
    static void clear(LivingEntity target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(MODIFIER_ID);
        }
        LEASES.remove(target);
    }

    /** {@link #sweep} at most once per {@value #SWEEP_INTERVAL_TICKS} server ticks. */
    public static void sweepIfDue(MinecraftServer server) {
        long now = server.getTickCount();
        if (now < nextSweep && now >= nextSweep - SWEEP_INTERVAL_TICKS) {
            return;
        }
        nextSweep = now + SWEEP_INTERVAL_TICKS;
        sweep(server);
    }

    /** Clears every modifier whose lease ran out or whose target is gone. */
    public static void sweep(MinecraftServer server) {
        if (LEASES.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Map.Entry<LivingEntity, Long>> it = LEASES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = it.next();
            LivingEntity target = entry.getKey();
            if (target == null) {
                it.remove();
                continue;
            }
            if (target.isRemoved() || GravityTetherRules.leaseExpired(now, entry.getValue())) {
                AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
                if (gravity != null) {
                    gravity.removeModifier(MODIFIER_ID);
                }
                it.remove();
            }
        }
    }

    /** A chain of particles from leech to target (decision 007: the tether must be seen). */
    private static void trail(ServerLevel level, PathfinderMob tetherer, LivingEntity target) {
        Vec3 from = tetherer.position().add(0.0D, tetherer.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        int points = GravityTetherRules.trailPoints(from.distanceTo(to));
        for (int i = 1; i <= points; i++) {
            Vec3 at = from.lerp(to, i / (double) (points + 1));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }
}
