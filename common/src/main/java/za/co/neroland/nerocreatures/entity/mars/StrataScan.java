package za.co.neroland.nerocreatures.entity.mars;

/**
 * The Strata Hound's prospecting scan: a bounded, incremental, read-only search, as pure logic.
 *
 * <p>The search area is a box of horizontal radius {@code radius} and vertical half-height
 * {@code vertical} around the point where the hound was shown the item. It is walked one column at a
 * time in rings of growing horizontal distance (ring 0 is the centre column, ring {@code d} the
 * {@code 8d} columns at Chebyshev distance {@code d}), a few thousand blocks per step, so no single
 * tick reads more than {@link #blocksPerStep()} block states. Once a match is found the scan finishes
 * that ring only and keeps the closest match in it, then stops; nothing is read after that.
 *
 * <p>Caps: {@link #MAX_RADIUS} and {@link #MAX_VERTICAL} bound the area whatever the config and the
 * Keen trait say; {@link #BLOCKS_PER_STEP} bounds one step.
 */
public final class StrataScan {

    /** Hard cap on the horizontal radius, after config and Keen. */
    public static final int MAX_RADIUS = 60;

    /** Hard cap on the vertical half-height. */
    public static final int MAX_VERTICAL = 24;

    /** Smallest radius a scan uses. */
    public static final int MIN_RADIUS = 4;

    /** Most block states one step reads. */
    public static final int BLOCKS_PER_STEP = 2048;

    /** What a step reads: one block offset from the scan centre. */
    @FunctionalInterface
    public interface Probe {
        /** Whether the block at this offset matches. Called at most once per offset. */
        boolean matches(int dx, int dy, int dz);
    }

    /** Whether a whole column can be read (loaded, inside the world). Skipped columns cost nothing. */
    @FunctionalInterface
    public interface ColumnGate {
        boolean readable(int dx, int dz);
    }

    private final int radius;
    private final int vertical;

    private int ring;
    private int index;
    private boolean exhausted;

    private boolean found;
    private int foundRing = -1;
    private int bestDx;
    private int bestDy;
    private int bestDz;
    private long bestDist = Long.MAX_VALUE;

    private long blocksRead;

    public StrataScan(int radius, int vertical) {
        this.radius = clampRadius(radius);
        this.vertical = Math.max(0, Math.min(MAX_VERTICAL, vertical));
    }

    /** The effective radius for a configured range and a Keen multiplier, clamped to the caps. */
    public static int effectiveRadius(int configuredRange, double keenMultiplier) {
        double m = Double.isNaN(keenMultiplier) || keenMultiplier <= 0.0D ? 1.0D : keenMultiplier;
        return clampRadius((int) Math.round(configuredRange * m));
    }

    static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    /** The vertical half-height used for a radius. */
    public static int verticalFor(int radius) {
        return Math.min(MAX_VERTICAL, clampRadius(radius));
    }

    public int radius() {
        return this.radius;
    }

    public int vertical() {
        return this.vertical;
    }

    public static int blocksPerStep() {
        return BLOCKS_PER_STEP;
    }

    /** Whether the scan is over (a match settled, or the area is exhausted). */
    public boolean done() {
        return this.exhausted || (this.found && this.ring > this.foundRing);
    }

    public boolean found() {
        return this.found;
    }

    public int bestDx() {
        return this.bestDx;
    }

    public int bestDy() {
        return this.bestDy;
    }

    public int bestDz() {
        return this.bestDz;
    }

    /** Total block states read so far (for tests and the budget). */
    public long blocksRead() {
        return this.blocksRead;
    }

    /** Columns in ring {@code d}: 1 for the centre, {@code 8d} otherwise. */
    static int columnsInRing(int d) {
        return d == 0 ? 1 : 8 * d;
    }

    /** Column {@code i} of ring {@code d}, as {@code {dx, dz}}. Each perimeter column exactly once. */
    static int[] column(int d, int i) {
        if (d == 0) {
            return new int[] {0, 0};
        }
        int side = i / (2 * d);
        int off = i % (2 * d);
        return switch (side) {
            case 0 -> new int[] {-d + off, -d};
            case 1 -> new int[] {d, -d + off};
            case 2 -> new int[] {d - off, d};
            default -> new int[] {-d, d - off};
        };
    }

    /**
     * Reads up to {@link #BLOCKS_PER_STEP} block states (whole columns only, so a step may stop a
     * little short). Returns {@link #done()}.
     */
    public boolean step(ColumnGate gate, Probe probe) {
        int budget = BLOCKS_PER_STEP;
        int columnCost = 2 * this.vertical + 1;
        while (!this.done() && budget >= columnCost) {
            int[] col = column(this.ring, this.index);
            int dx = col[0];
            int dz = col[1];
            if (gate.readable(dx, dz)) {
                // Nearest heights first, so a match at the hound's own level reads soonest.
                for (int k = 0; k < columnCost; k++) {
                    int dy = (k % 2 == 0) ? k / 2 : -((k + 1) / 2);
                    this.blocksRead++;
                    if (probe.matches(dx, dy, dz)) {
                        this.offer(dx, dy, dz);
                    }
                }
                budget -= columnCost;
            } else {
                budget--; // an unreadable column still costs a little, so a step stays bounded
            }
            this.advance();
        }
        return this.done();
    }

    private void offer(int dx, int dy, int dz) {
        long dist = (long) dx * dx + (long) dy * dy + (long) dz * dz;
        if (!this.found) {
            this.found = true;
            this.foundRing = this.ring;
        }
        if (dist < this.bestDist) {
            this.bestDist = dist;
            this.bestDx = dx;
            this.bestDy = dy;
            this.bestDz = dz;
        }
    }

    private void advance() {
        this.index++;
        if (this.index >= columnsInRing(this.ring)) {
            this.index = 0;
            this.ring++;
            if (this.ring > this.radius) {
                this.exhausted = true;
            }
        }
    }
}
