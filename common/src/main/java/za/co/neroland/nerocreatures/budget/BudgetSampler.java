package za.co.neroland.nerocreatures.budget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The accumulator behind {@code /nerocreatures budget}: nanoseconds and call counts per key, turned
 * into "milliseconds per server tick" over a window. Plain Java, no game types, so it is unit
 * tested. Not thread-safe; used from the server thread only.
 *
 * @param <K> the key (an {@code EntityType} in game, anything in tests); compared by identity
 */
public final class BudgetSampler<K> {

    /** One line of the report. {@code msPerTick} is the share of one server tick this key used. */
    public record Row(String name, double msPerTick, double callsPerTick, long calls) {
    }

    /** The whole report: rows (most expensive first) and the total. */
    public record Report(long ticks, List<Row> rows, double totalMsPerTick, long totalCalls) {
    }

    private final Map<K, long[]> totals = new IdentityHashMap<>();

    /** Adds one measured call. */
    public void record(K key, long nanos) {
        long[] t = totals.computeIfAbsent(key, k -> new long[2]);
        t[0] += Math.max(0L, nanos);
        t[1]++;
    }

    public void clear() {
        totals.clear();
    }

    public boolean isEmpty() {
        return totals.isEmpty();
    }

    /**
     * The report over {@code ticks} server ticks (at least 1).
     *
     * @param namer turns a key into its printed name (a registry id; never anything about a player)
     */
    public Report report(long ticks, Function<K, String> namer) {
        long t = Math.max(1L, ticks);
        List<Row> rows = new ArrayList<>(totals.size());
        long totalNanos = 0L;
        long totalCalls = 0L;
        for (Map.Entry<K, long[]> e : totals.entrySet()) {
            long nanos = e.getValue()[0];
            long calls = e.getValue()[1];
            totalNanos += nanos;
            totalCalls += calls;
            rows.add(new Row(namer.apply(e.getKey()), nanos / 1.0e6D / t, calls / (double) t, calls));
        }
        rows.sort(Comparator.comparingDouble(Row::msPerTick).reversed().thenComparing(Row::name));
        return new Report(t, List.copyOf(rows), totalNanos / 1.0e6D / t, totalCalls);
    }
}
