package za.co.neroland.nerocreatures.entity.visual;

/**
 * An immutable copy of a creature's visual state for one frame: what a renderer copies into its
 * render state (1.21.2+) or reads directly (1.21.1). Small ints and flags only; nothing in it
 * identifies a player.
 *
 * @param pose          the pose to play
 * @param abilityTicks  ticks since the pose was entered, including the partial tick
 * @param duration      the pose's length in ticks, or 0 when it is held (untimed)
 * @param phase         boss phase 0-3 (0 = not a boss or no phase)
 * @param stage         part-visibility stage 0-3
 * @param variant       colour or skin variant 0-7
 * @param form          form 0-7 (bloom host: host, sporeling, pod)
 */
public record VisualsSnapshot(VisualPose pose, float abilityTicks, int duration, int phase, int stage,
        int variant, int form, boolean radiant, boolean alpha, boolean saddled, boolean panniers) {

    /** The default for a renderer that has no creature (or before the first update). */
    public static final VisualsSnapshot EMPTY =
            new VisualsSnapshot(VisualPose.IDLE, 0.0F, 0, 0, 0, 0, 0, false, false, false, false);

    /** 0..1 through a timed pose; 0 for a held pose. */
    public float progress() {
        return duration <= 0 ? 0.0F : Math.min(1.0F, abilityTicks / duration);
    }

    /** Builds a snapshot from the two synced ints. Pure; used by {@link CreatureVisuals} and tests. */
    public static VisualsSnapshot of(int state, int timer, float partialTick) {
        int duration = VisualsBits.duration(timer);
        float ticks = VisualsBits.elapsed(timer)
                + (VisualsBits.pose(state) == 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, partialTick)));
        if (duration > 0) {
            ticks = Math.min(ticks, duration);
        }
        return new VisualsSnapshot(VisualPose.byId(VisualsBits.pose(state)), ticks, duration,
                VisualsBits.phase(state), VisualsBits.stage(state), VisualsBits.variant(state),
                VisualsBits.form(state), VisualsBits.flag(state, VisualsBits.FLAG_RADIANT),
                VisualsBits.flag(state, VisualsBits.FLAG_ALPHA), VisualsBits.flag(state, VisualsBits.FLAG_SADDLED),
                VisualsBits.flag(state, VisualsBits.FLAG_PANNIERS));
    }
}
