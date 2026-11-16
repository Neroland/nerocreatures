package za.co.neroland.nerocreatures.block;

/**
 * Pure rules for the Incubator: one egg at a time, 20 minutes unpowered, 5 minutes with
 * full power pulled through Core's {@code EnergyLookup}, linear in between. Progress is counted in
 * "unpowered ticks", so an unpowered egg is done after exactly {@code hatchTicks} ticks and a fully
 * powered one after exactly {@code poweredHatchTicks}.
 *
 * <p><b>Works with no power mod.</b> Power is only ever a speed-up: zero energy found (no storage next
 * to it, no power mod, Core's lookup missing, or {@code energyPerTick = 0}) is the plain unpowered rate,
 * never a stall.
 */
public final class IncubatorRules {

    /** 20 minutes. */
    public static final int DEFAULT_HATCH_TICKS = 24_000;

    /** 5 minutes. */
    public static final int DEFAULT_POWERED_HATCH_TICKS = 6_000;

    /** Nero energy units pulled per powered tick. */
    public static final int DEFAULT_ENERGY_PER_TICK = 20;

    /** The block entity works on this cadence. */
    public static final int PULSE_TICKS = 20;

    private IncubatorRules() {
    }

    /** Why an egg was or was not accepted. */
    public enum Insert {
        ACCEPTED, OCCUPIED, NOT_AN_EGG, CAP
    }

    /** The insert decision, in check order: occupied, then a readable egg, then the owner's egg cap. */
    public static Insert decideInsert(boolean occupied, boolean readableEgg, boolean underCap) {
        if (occupied) {
            return Insert.OCCUPIED;
        }
        if (!readableEgg) {
            return Insert.NOT_AN_EGG;
        }
        return underCap ? Insert.ACCEPTED : Insert.CAP;
    }

    /** Energy one pulse asks for; 0 when power is switched off. */
    public static long energyWanted(int pulseTicks, int energyPerTick) {
        if (pulseTicks <= 0 || energyPerTick <= 0) {
            return 0L;
        }
        return (long) pulseTicks * energyPerTick;
    }

    /**
     * Progress one pulse adds, in unpowered ticks: {@code pulseTicks} unpowered, scaled up to
     * {@code pulseTicks * hatchTicks / poweredHatchTicks} with all the energy it wanted, proportional for
     * a partial pull. A powered time not shorter than the unpowered one gives no speed-up.
     */
    public static long progressFor(int pulseTicks, long extracted, long wanted, int hatchTicks,
            int poweredHatchTicks) {
        if (pulseTicks <= 0) {
            return 0L;
        }
        long base = pulseTicks;
        if (wanted <= 0L || extracted <= 0L || poweredHatchTicks <= 0 || poweredHatchTicks >= hatchTicks) {
            return base;
        }
        double fraction = Math.min(1.0D, (double) extracted / (double) wanted);
        double speed = (double) hatchTicks / (double) poweredHatchTicks; // e.g. 4x
        return base + Math.round(base * (speed - 1.0D) * fraction);
    }

    /** Whether the egg is ready. */
    public static boolean ready(long progress, int hatchTicks) {
        return progress >= Math.max(1, hatchTicks);
    }

    /**
     * The game time the egg row advertises (egg timer): the unpowered worst case from now.
     * Power can only make it earlier.
     */
    public static long hatchAtEstimate(long now, long progress, int hatchTicks) {
        return now + Math.max(0L, hatchTicks - progress);
    }

    /** Seconds left at the unpowered rate, for the status message. */
    public static long secondsLeft(long progress, int hatchTicks) {
        return Math.max(0L, (hatchTicks - progress + 19L) / 20L);
    }
}
