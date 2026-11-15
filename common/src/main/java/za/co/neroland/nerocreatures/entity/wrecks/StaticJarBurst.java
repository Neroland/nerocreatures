package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;

/**
 * The thrown {@code static_jar}: its arc and its burst. Server side only.
 *
 * <p>No projectile entity: the arc is simulated in one call ({@link SwarmRules#step}, the vanilla
 * thrown-item physics, at most {@value SwarmRules#JAR_MAX_STEPS} ticks), clipped against blocks and
 * living entities, drawn as a spark trail, and bursts where it lands. That keeps the jar free of the
 * projectile class moves between Minecraft lines, and decision 001's {@code creature_projectile} id
 * stays unregistered (it was reserved only for this).
 *
 * <p>The burst disables up to {@value SwarmRules#MAX_JAR_TARGETS} creatures in
 * {@code #nerocreatures:mechanical} within {@value SwarmRules#JAR_RADIUS} blocks for
 * {@value SwarmRules#JAR_DISABLE_TICKS} ticks: target dropped, path stopped, and vanilla slowness X
 * plus weakness V (ordinary saved effects, so nothing to clean up). Entities only, no world change:
 * gated by {@code staticSwarmDrain} alone (decision 006); off, the jar just shatters.
 */
public final class StaticJarBurst {

    private static final double HIT_INFLATE = 0.3D;

    private StaticJarBurst() {
    }

    /**
     * Throws a jar from {@code thrower}'s eyes along its look and bursts it where it lands.
     *
     * @return how many mechanical creatures were disabled
     */
    public static int throwFrom(ServerLevel level, LivingEntity thrower) {
        Vec3 eye = thrower.getEyePosition();
        Vec3 look = thrower.getLookAngle();
        double[] pos = {eye.x, eye.y - 0.1D, eye.z};
        double[] vel = SwarmRules.launch(look.x, look.y, look.z);
        Vec3 landed = null;
        for (int step = 0; step < SwarmRules.JAR_MAX_STEPS && landed == null; step++) {
            Vec3 from = new Vec3(pos[0], pos[1], pos[2]);
            SwarmRules.step(pos, vel);
            Vec3 to = new Vec3(pos[0], pos[1], pos[2]);
            if (!level.isLoaded(BlockPos.containing(to))) {
                landed = from;
                break;
            }
            BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, thrower));
            boolean hit = blockHit.getType() != HitResult.Type.MISS;
            Vec3 end = hit ? blockHit.getLocation() : to;
            Vec3 entityHit = firstEntityHit(level, thrower, from, end);
            if (entityHit != null) {
                end = entityHit;
                hit = true;
            }
            if (step % 2 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            if (hit) {
                landed = end;
            }
        }
        if (landed == null) {
            landed = new Vec3(pos[0], pos[1], pos[2]);
        }
        return burst(level, landed);
    }

    @Nullable
    private static Vec3 firstEntityHit(ServerLevel level, Entity thrower, Vec3 from, Vec3 to) {
        AABB sweep = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(0.5D);
        List<Entity> found = level.getEntities(thrower, sweep,
                e -> e instanceof LivingEntity && e.isAlive() && e.isPickable() && !e.isSpectator());
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : found) {
            Optional<Vec3> clip = e.getBoundingBox().inflate(HIT_INFLATE).clip(from, to);
            if (clip.isPresent()) {
                double d = from.distanceToSqr(clip.get());
                if (d < bestD) {
                    bestD = d;
                    best = clip.get();
                }
            }
        }
        return best;
    }

    /** Shatters a jar at {@code at}. @return how many mechanical creatures were disabled */
    public static int burst(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 24, 1.2D, 0.8D, 1.2D, 0.15D);
        if (!SwarmRules.drainEnabled(NeroCreaturesConfig.STATIC_SWARM_DRAIN.get())) {
            return 0;
        }
        double r = SwarmRules.JAR_RADIUS;
        AABB box = new AABB(at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r);
        List<Mob> found = level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && WrecksLookups.mechanical(m) && m.distanceToSqr(at) <= r * r);
        int n = SwarmRules.take(found.size(), SwarmRules.MAX_JAR_TARGETS);
        Holder<MobEffect> slowness = WrecksLookups.effect("slowness");
        Holder<MobEffect> weakness = WrecksLookups.effect("weakness");
        for (int i = 0; i < n; i++) {
            disable(level, found.get(i), slowness, weakness);
        }
        return n;
    }

    private static void disable(ServerLevel level, Mob mob, @Nullable Holder<MobEffect> slowness,
            @Nullable Holder<MobEffect> weakness) {
        mob.setTarget(null);
        mob.getNavigation().stop();
        if (slowness != null) {
            mob.addEffect(new MobEffectInstance(slowness, SwarmRules.JAR_DISABLE_TICKS, 9, false, true));
        }
        if (weakness != null) {
            mob.addEffect(new MobEffectInstance(weakness, SwarmRules.JAR_DISABLE_TICKS, 4, false, true));
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6D,
                mob.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.05D);
    }
}
