package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * Builds a {@link ScrapHulk} from mechanical wreckage.
 *
 * <p>The shared creature base calls {@link #onCreatureDeath} when a creature dies (integration hook).
 * Only {@code #nerocreatures:mechanical} deaths that are not hulks count. Each dimension keeps a small
 * in-memory buffer of recent deaths (game time and block position only, at most
 * {@value ScrapHulkRules#BUFFER_CAP}, nothing saved, nothing about a player). When the latest death has
 * {@value ScrapHulkRules#MIN_DEATHS} or more fresh deaths within {@value ScrapHulkRules#CLUSTER_RADIUS}
 * blocks of it (window {@value ScrapHulkRules#WINDOW_TICKS} ticks), and at most one attempt per
 * {@value ScrapHulkRules#ATTEMPT_COOLDOWN_TICKS} ticks per dimension:
 *
 * <ol>
 *   <li>the caps: no living hulk within {@value ScrapHulkRules#AREA_RADIUS} blocks (one bounded box
 *       query), fewer than {@value ScrapHulkRules#MAX_PER_DIMENSION} in the dimension, and the census
 *       caps;</li>
 *   <li>{@link CreatureGriefing#mayAlter} with {@code scrapHulkAssembly}, {@code Change.ENTITY}, the dying
 *       creature as the actor; denied, the fallback is no hulk;</li>
 *   <li>the hulk is created by id ({@code nerocreatures:scrap_hulk}; nothing happens until it is
 *       registered) with reason {@code MOB_SUMMONED} (not persistent), at the cluster's centre, and
 *       starts its assembly pose. The used deaths leave the buffer.</li>
 * </ol>
 *
 * <p>Server thread only.
 */
public final class ScrapHulkAssembly {

    private static final Map<ServerLevel, Tracker> TRACKERS = new WeakHashMap<>();

    private ScrapHulkAssembly() {
    }

    private static final class Tracker {
        List<ScrapHulkRules.Death> deaths = new ArrayList<>();
        long nextAttempt = Long.MIN_VALUE;
    }

    /** Integration hook: call from the creature base's {@code die} on the server. */
    public static void onCreatureDeath(ServerLevel level, Mob dead) {
        if (!ScrapHulkRules.feedsAssembly(WrecksLookups.mechanical(dead), dead instanceof ScrapHulk,
                za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(dead))
                || !NeroCreaturesConfig.SCRAP_HULK_ASSEMBLY.get()) {
            return;
        }
        Tracker tracker = TRACKERS.computeIfAbsent(level, l -> new Tracker());
        long now = level.getGameTime();
        BlockPos pos = dead.blockPosition();
        ScrapHulkRules.Death latest = new ScrapHulkRules.Death(now, pos.getX(), pos.getY(), pos.getZ());
        tracker.deaths.add(latest);
        tracker.deaths = ScrapHulkRules.prune(tracker.deaths, now);
        List<ScrapHulkRules.Death> cluster = ScrapHulkRules.cluster(tracker.deaths, latest, now);
        if (cluster.size() < ScrapHulkRules.MIN_DEATHS || now < tracker.nextAttempt) {
            return;
        }
        tracker.nextAttempt = now + ScrapHulkRules.ATTEMPT_COOLDOWN_TICKS;
        int[] c = ScrapHulkRules.centroid(cluster);
        BlockPos at = new BlockPos(c[0], c[1], c[2]);
        if (!level.isLoaded(at) || !CreatureCensus.dimensionHasRoom(level)) {
            return;
        }
        if (!ScrapHulkRules.shouldAssemble(cluster.size(), hulksNear(level, at), hulksInDimension(level))) {
            return;
        }
        boolean allowed = CreatureGriefing.mayAlter(level, dead, at, NeroCreaturesConfig.SCRAP_HULK_ASSEMBLY,
                CreatureGriefing.Change.ENTITY);
        if (!ScrapHulkRules.assembles(true, allowed)) {
            return; // griefing denied: no hulk
        }
        if (spawn(level, at)) {
            tracker.deaths.removeAll(cluster);
        }
    }

    private static int hulksNear(ServerLevel level, BlockPos at) {
        double r = ScrapHulkRules.AREA_RADIUS;
        AABB box = new AABB(at.getX() - r, at.getY() - r, at.getZ() - r, at.getX() + r, at.getY() + r, at.getZ() + r);
        List<ScrapHulk> found = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(ScrapHulk.class), box, e -> e.isAlive() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(e), found, 1);
        return found.size();
    }

    private static int hulksInDimension(ServerLevel level) {
        return level.getEntities(EntityTypeTest.forClass(ScrapHulk.class), e -> e.isAlive() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(e)).size();
    }

    private static boolean spawn(ServerLevel level, BlockPos at) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(WrecksLookups.SCRAP_HULK);
        if (type.isEmpty()) {
            return false;
        }
        Entity created = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(created instanceof ScrapHulk hulk)) {
            return false;
        }
        hulk.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        hulk.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
        if (!level.addFreshEntity(hulk)) {
            return false;
        }
        hulk.beginAssembly();
        level.sendParticles(ModParticles.STATIC_SPARK.get(), at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D,
                30, 1.0D, 1.0D, 1.0D, 0.15D);
        CreatureCensus.invalidate(level);
        return true;
    }

    /** Drops every buffer (server stop; tests). */
    public static void reset() {
        TRACKERS.clear();
    }
}
