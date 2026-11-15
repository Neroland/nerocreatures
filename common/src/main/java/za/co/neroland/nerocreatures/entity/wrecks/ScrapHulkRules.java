package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules for the {@link ScrapHulk}: decision 006's assembly (three or more mechanical deaths
 * within 8 blocks in 10 s; at most one hulk per 64 blocks and four per dimension), the shed-parts
 * stages, and the sweep. No Minecraft types; unit-tested in {@code ScrapHulkRulesTest}.
 */
public final class ScrapHulkRules {

    /** Deaths older than this (10 s) no longer count. */
    public static final long WINDOW_TICKS = 200L;
    /** Deaths must lie within this many blocks of the latest one. */
    public static final double CLUSTER_RADIUS = 8.0D;
    /** How many mechanical deaths make a hulk. */
    public static final int MIN_DEATHS = 3;
    /** No new hulk within this many blocks of a living one. */
    public static final double AREA_RADIUS = 64.0D;
    /** At most this many hulks alive per dimension. */
    public static final int MAX_PER_DIMENSION = 4;
    /** The per-dimension death buffer never holds more than this. */
    public static final int BUFFER_CAP = 32;
    /** At most one assembly attempt per dimension per this many ticks. */
    public static final long ATTEMPT_COOLDOWN_TICKS = 20L;

    /** The assembly pose: parts fly in. It cannot be hurt or act meanwhile. */
    public static final int ASSEMBLE_TICKS = 30;

    /** Highest shed stage (health quarters 0..3). */
    public static final int MAX_STAGE = 3;
    /** Armour lost per shed part. */
    public static final double ARMOUR_PER_STAGE = 2.0D;

    /** Big-arm sweep: telegraph before the hit (inside the 20-tick {@code ability_2} pose). */
    public static final int SWEEP_TELEGRAPH_TICKS = 14;
    /** The {@code ability_2} pose length. */
    public static final int SWEEP_POSE_TICKS = 20;
    /** Ticks between sweeps. */
    public static final int SWEEP_COOLDOWN_TICKS = 80;
    /** Sweep reach, in blocks. */
    public static final double SWEEP_RANGE = 3.5D;
    /** At most this many players hit per sweep. */
    public static final int MAX_SWEEP_TARGETS = 4;
    /** Sweep damage as a multiple of the attack attribute. */
    public static final float SWEEP_DAMAGE_FACTOR = 1.5F;

    private ScrapHulkRules() {
    }

    /** One recorded mechanical death. Block coordinates and game time; nothing else. */
    public record Death(long time, int x, int y, int z) {
    }

    /** Whether a death is still inside the window at {@code now}. */
    public static boolean fresh(Death death, long now) {
        return now - death.time() <= WINDOW_TICKS && now >= death.time();
    }

    /** Drops stale deaths and keeps the newest {@link #BUFFER_CAP}, in order. */
    public static List<Death> prune(List<Death> deaths, long now) {
        List<Death> kept = new ArrayList<>(Math.min(deaths.size(), BUFFER_CAP));
        for (Death d : deaths) {
            if (fresh(d, now)) {
                kept.add(d);
            }
        }
        int over = kept.size() - BUFFER_CAP;
        return over > 0 ? new ArrayList<>(kept.subList(over, kept.size())) : kept;
    }

    /** The fresh deaths within {@link #CLUSTER_RADIUS} of {@code latest} (including it). */
    public static List<Death> cluster(List<Death> deaths, Death latest, long now) {
        List<Death> out = new ArrayList<>();
        double r2 = CLUSTER_RADIUS * CLUSTER_RADIUS;
        for (Death d : deaths) {
            if (!fresh(d, now)) {
                continue;
            }
            double dx = d.x() - latest.x();
            double dy = d.y() - latest.y();
            double dz = d.z() - latest.z();
            if (dx * dx + dy * dy + dz * dz <= r2) {
                out.add(d);
            }
        }
        return out;
    }

    /** Block coordinates of a cluster's centre; {@code null} for an empty cluster. */
    public static int[] centroid(List<Death> cluster) {
        if (cluster.isEmpty()) {
            return null;
        }
        long sx = 0;
        long sy = 0;
        long sz = 0;
        for (Death d : cluster) {
            sx += d.x();
            sy += d.y();
            sz += d.z();
        }
        int n = cluster.size();
        return new int[] {(int) Math.floorDiv(sx, n), (int) Math.floorDiv(sy, n), (int) Math.floorDiv(sz, n)};
    }

    /**
     * The whole assembly decision except the griefing check: enough deaths, no hulk in the area, room
     * in the dimension.
     */
    public static boolean shouldAssemble(int clusterSize, int hulksInArea, int hulksInDimension) {
        return clusterSize >= MIN_DEATHS && hulksInArea == 0 && hulksInDimension < MAX_PER_DIMENSION;
    }

    /**
     * After {@code CreatureGriefing}: {@code true} spawns the hulk, {@code false} takes the decision 006
     * fallback, which is no hulk (the deaths stay ordinary deaths).
     */
    public static boolean assembles(boolean shouldAssemble, boolean griefingAllowed) {
        return shouldAssemble && griefingAllowed;
    }

    /** Shed stage by health quarter: 0 above 75 %, 1 above 50 %, 2 above 25 %, 3 at or below. */
    public static int stageFor(float health, float maxHealth) {
        if (maxHealth <= 0.0F) {
            return MAX_STAGE;
        }
        float f = health / maxHealth;
        if (f > 0.75F) {
            return 0;
        }
        if (f > 0.5F) {
            return 1;
        }
        if (f > 0.25F) {
            return 2;
        }
        return MAX_STAGE;
    }

    /** Parts shed going from {@code oldStage} to {@code newStage}: never negative, never past 3 total. */
    public static int partsToShed(int oldStage, int newStage) {
        int from = Math.max(0, Math.min(MAX_STAGE, oldStage));
        int to = Math.max(0, Math.min(MAX_STAGE, newStage));
        return Math.max(0, to - from);
    }

    /** Armour left at a stage, never below zero. */
    public static double armourAt(double baseArmour, int stage) {
        return Math.max(0.0D, baseArmour - ARMOUR_PER_STAGE * Math.max(0, Math.min(MAX_STAGE, stage)));
    }

    /** Whether a point lies in the sweep's front arc (dot of facing and offset positive) and reach. */
    public static boolean inSweep(double facingX, double facingZ, double dx, double dz) {
        double d2 = dx * dx + dz * dz;
        return d2 <= SWEEP_RANGE * SWEEP_RANGE && facingX * dx + facingZ * dz >= 0.0D;
    }
}
