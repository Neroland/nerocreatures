package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.function.IntUnaryOperator;

/**
 * A regrowing product on a creature: Lode Tortoise nodules, Prism Stag antlers, Rime Yak wool,
 * Drift Leviathan barnacles. Pure and unit-tested.
 *
 * <p>Growth is counted in elapsed ticks the creature reports with {@link #advance(int)} (call it
 * from the server AI step on a cadence, e.g. {@code advance(20)} every 20 ticks), so an unloaded
 * creature does not grow, like vanilla wool. {@link #stage()} runs {@code 0..maxStage}; the product
 * is harvestable at {@code maxStage}. A harvest resets growth and returns the yield after the
 * {@link YieldModifier} (the Bountiful trait hook).
 *
 * <p>Saved by the creature as one int ({@link #progress()} / {@link #setProgress(int)}).
 */
public final class ProductTimer {

    /** Hook for traits that change yield (Bountiful). Must return a value of at least 0. */
    @FunctionalInterface
    public interface YieldModifier extends IntUnaryOperator {
        /** No change. */
        YieldModifier NONE = base -> base;
    }

    private final int regrowTicks;
    private final int maxStage;
    private final int maxYield;
    private int progress;

    /**
     * @param regrowTicks ticks from empty to harvestable
     * @param maxStage    visual stages; the last one is "ready" (1-3 fits {@code CreatureVisuals})
     * @param maxYield    hard cap on one harvest, after modifiers
     */
    public ProductTimer(int regrowTicks, int maxStage, int maxYield) {
        this.regrowTicks = Math.max(1, regrowTicks);
        this.maxStage = Math.max(1, maxStage);
        this.maxYield = Math.max(1, maxYield);
    }

    /** Adds elapsed ticks; growth stops at ready. */
    public void advance(int ticks) {
        if (ticks > 0) {
            this.progress = (int) Math.min(this.regrowTicks, (long) this.progress + ticks);
        }
    }

    /** {@code 0..maxStage}; {@code maxStage} means ready. */
    public int stage() {
        if (this.progress >= this.regrowTicks) {
            return this.maxStage;
        }
        return (int) ((long) this.progress * this.maxStage / this.regrowTicks);
    }

    public boolean isReady() {
        return this.progress >= this.regrowTicks;
    }

    /**
     * Harvests if ready: resets growth and returns {@code modifier(baseYield)} clamped to
     * {@code 0..maxYield}; returns 0 and changes nothing if not ready.
     */
    public int harvest(int baseYield, YieldModifier modifier) {
        if (!this.isReady()) {
            return 0;
        }
        this.progress = 0;
        int yield = (modifier == null ? YieldModifier.NONE : modifier).applyAsInt(Math.max(0, baseYield));
        return Math.max(0, Math.min(this.maxYield, yield));
    }

    /** Saved form. */
    public int progress() {
        return this.progress;
    }

    /** Restores the saved form; out-of-range values are clamped, never thrown. */
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(this.regrowTicks, progress));
    }

    public int regrowTicks() {
        return this.regrowTicks;
    }

    public int maxStage() {
        return this.maxStage;
    }
}
