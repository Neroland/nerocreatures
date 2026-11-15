package za.co.neroland.nerocreatures.bestiary;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.entity.glacira.FrostLurker;
import za.co.neroland.nerocreatures.entity.hostile.AsteroidWorm;
import za.co.neroland.nerocreatures.entity.mars.DustDevil;
import za.co.neroland.nerocreatures.entity.wrecks.CacheMimic;

/**
 * The Field Scanner's ray (decision 010 section 1): from the eye along the look vector, 8 blocks,
 * entity boxes inflated by 0.3, blocks stop it. The server runs it to decide; the client runs the
 * same code only to draw the HUD. A hidden creature (burrowed, lurking, posing as a crate, spinning
 * up, invisible) is skipped as if nothing were there, so the scanner never works as a detector.
 */
public final class ScanTargets {

    /** What the ray found. */
    public enum Kind {
        NONE, VALID, GALLERY, NOT_LISTED
    }

    public record Target(Kind kind, @Nullable Entity entity, @Nullable Entry entry) {
        static final Target NONE = new Target(Kind.NONE, null, null);

        public int entityId() {
            return this.kind == Kind.VALID && this.entity != null ? this.entity.getId() : -1;
        }
    }

    private ScanTargets() {
    }

    public static Target find(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(BestiaryRules.SCAN_RANGE));
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB sweep = new AABB(eye, end).inflate(1.0D + BestiaryRules.SCAN_INFLATE);
        List<Entity> candidates = level.getEntities(player, sweep,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !hidden(e));
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            AABB box = candidate.getBoundingBox().inflate(BestiaryRules.SCAN_INFLATE);
            double dist;
            if (box.contains(eye)) {
                dist = 0.0D;
            } else {
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                dist = eye.distanceToSqr(hit.get());
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return classify(best);
    }

    static Target classify(@Nullable Entity entity) {
        if (entity == null) {
            return Target.NONE;
        }
        Optional<Entry> entry = BestiaryProgress.entryOf(entity.getType());
        if (entry.isEmpty()) {
            return new Target(Kind.NOT_LISTED, entity, null);
        }
        if (BestiaryProgress.isGallery(entity)) {
            return new Target(Kind.GALLERY, entity, entry.get());
        }
        return new Target(Kind.VALID, entity, entry.get());
    }

    /** Creatures in a hidden state are not there as far as the scanner is concerned. */
    public static boolean hidden(Entity entity) {
        if (entity.isInvisible()) {
            return true;
        }
        if (entity instanceof CacheMimic mimic && mimic.isDormant()) {
            return true;
        }
        if (entity instanceof FrostLurker lurker && lurker.isLurking()) {
            return true;
        }
        if (entity instanceof AsteroidWorm worm && worm.isBurrowed()) {
            return true;
        }
        return entity instanceof DustDevil devil && devil.isForming();
    }
}
