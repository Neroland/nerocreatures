package za.co.neroland.nerocreatures.entity.visual;

/**
 * The synced pose a creature model plays. Matches the fixed clip vocabulary: {@code idle},
 * {@code ability}, {@code ability_2}, {@code rest}. {@code move} is procedural and {@code hurt}
 * uses vanilla {@code hurtTime}, so neither is a pose. Boss phase stances are {@code phase}, not a
 * pose. The ordinal is the wire value: append only, never reorder.
 */
public enum VisualPose {
    IDLE("idle"),
    ABILITY("ability"),
    ABILITY_2("ability_2"),
    REST("rest");

    private final String clip;

    VisualPose(String clip) {
        this.clip = clip;
    }

    /** The clip name in the model spec. */
    public String clip() {
        return clip;
    }

    /** The pose for a wire value; out-of-range values read as {@link #IDLE}. */
    public static VisualPose byId(int id) {
        VisualPose[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }
}
