package za.co.neroland.nerocreatures.budget;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Low-overhead tick timing for {@code /nerocreatures budget}: milliseconds per server tick spent in
 * NeroCreatures entity ticks, per species and in total.
 *
 * <p><b>Cost.</b> Outside a sampling window, {@link #begin()} is one static boolean read and
 * {@link #end(Entity, long)} returns at once. Inside a window (an operator started one, default 10 s,
 * at most 60 s) each creature tick costs two {@code System.nanoTime()} calls and a map update.
 *
 * <p><b>Wiring</b> (done once in each class root: {@code NeroCreatureEntity}, {@code TameableCreature}
 * and anything else that ticks creatures):
 * <pre>{@code
 * @Override
 * public void tick() {
 *     long budgetStart = CreatureBudget.begin();
 *     super.tick();
 *     CreatureBudget.end(this, budgetStart);
 * }
 * }</pre>
 * The measurement includes AI, navigation and movement: everything the creature costs the server.
 *
 * <p>Reports name species by registry id only. Nothing about players is recorded (POPIA/GDPR).
 * Server thread only.
 */
public final class CreatureBudget {

    public static final int DEFAULT_SECONDS = 10;

    /** The budget key gallery displays are recorded under (never an entity type). */
    private static final Object GALLERY_KEY = "gallery";
    public static final int MAX_SECONDS = 60;

    private static final BudgetSampler<Object> SAMPLER = new BudgetSampler<>();

    private static boolean active;
    private static long startTick;
    private static long endTick;
    private static long stoppedAtTick = -1L;

    private CreatureBudget() {
    }

    /** Call first in a creature's {@code tick()}. Returns 0 when no window is open. */
    public static long begin() {
        return active ? System.nanoTime() : 0L;
    }

    /** Call last in a creature's {@code tick()} with the value {@link #begin()} returned. */
    public static void end(Entity entity, long started) {
        if (started == 0L || !active) {
            return;
        }
        long elapsed = System.nanoTime() - started;
        if (entity.level().isClientSide()) {
            return;
        }
        MinecraftServer server = entity.level().getServer();
        if (server != null && server.getTickCount() >= endTick) {
            stop(server.getTickCount());
            return;
        }
        // Gallery displays get one line of their own, outside every species line and the total.
        SAMPLER.record(za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(entity) ? GALLERY_KEY : entity.getType(), elapsed);
    }

    /** Opens a fresh window of {@code seconds} (clamped to 1..{@value #MAX_SECONDS}). */
    public static void start(MinecraftServer server, int seconds) {
        int s = Math.max(1, Math.min(MAX_SECONDS, seconds));
        SAMPLER.clear();
        startTick = server.getTickCount();
        endTick = startTick + s * 20L;
        stoppedAtTick = -1L;
        active = true;
    }

    /** Closes the window early. */
    public static void stop(MinecraftServer server) {
        if (active) {
            stop(Math.min(server.getTickCount(), endTick));
        }
    }

    private static void stop(long tick) {
        active = false;
        stoppedAtTick = Math.min(tick, endTick);
    }

    /** Whether a window is open (it closes itself at its end tick). */
    public static boolean running(MinecraftServer server) {
        if (active && server.getTickCount() >= endTick) {
            stop(endTick);
        }
        return active;
    }

    /** Whether a finished window has data to report. */
    public static boolean hasResult() {
        return !active && stoppedAtTick >= 0L;
    }

    /** Ticks still left in the open window. */
    public static long ticksLeft(MinecraftServer server) {
        return Math.max(0L, endTick - server.getTickCount());
    }

    /** The report for the current or last window, over the ticks it has covered so far. */
    public static BudgetSampler.Report report(MinecraftServer server) {
        long until = active ? Math.min(server.getTickCount(), endTick) : stoppedAtTick;
        return SAMPLER.report(until - startTick, CreatureBudget::name, type -> type == GALLERY_KEY);
    }

    private static String name(Object key) {
        if (!(key instanceof EntityType<?> type)) {
            return String.valueOf(key);
        }
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /** Forgets everything (server stop). */
    public static void reset() {
        active = false;
        stoppedAtTick = -1L;
        SAMPLER.clear();
    }
}
