package za.co.neroland.nerocreatures.entity.greenxertz;

/**
 * The Bloom Host's sporeling rules as pure logic (decision 006, key {@code bloomHostSporelings};
 * per the entity contract the key is read directly, not through {@code mayAlter}, and the caps
 * always apply).
 *
 * <ul>
 *   <li>Only the {@link #FORM_HOST host} form raises sporelings, and only from a mob it killed
 *       itself.</li>
 *   <li>Never from players, owned (tamed) mobs, named mobs, persistent mobs, bosses or
 *       villager-type mobs; only from {@code #nerocreatures:sporeling_hosts}.</li>
 *   <li>Hard caps: {@link #PER_HOST} live sporelings per host, {@link #PER_DIMENSION} per
 *       dimension, {@link #LIFE_TICKS} ticks of life, no drops and no experience. Sporelings are
 *       never saved, so an unload removes them.</li>
 * </ul>
 */
public final class BloomRules {

    /** {@code CreatureVisuals.form} values (decision 001, entity contract section 3). */
    public static final int FORM_HOST = 0;
    public static final int FORM_SPORELING = 1;
    public static final int FORM_POD = 2;

    public static final int PER_HOST = 3;
    public static final int PER_DIMENSION = 24;
    /** 30 s. */
    public static final int LIFE_TICKS = 600;
    /** The host's {@code ability} pose ("raise": cap tilts back and opens). */
    public static final int RAISE_POSE_TICKS = 20;
    /** {@code minecraft:scale} of a sporeling (the cap alone on stub legs). */
    public static final double SPORELING_SCALE = 0.5D;
    /** Particles in the spore strand from corpse to host. */
    public static final int STRAND_POINTS = 8;

    /** What is known about a slain mob, server side. */
    public record Victim(boolean player, boolean owned, boolean named, boolean persistent,
            boolean boss, boolean villager, boolean tagged) {
    }

    private BloomRules() {
    }

    /** Whether the slain mob may rise at all. */
    public static boolean eligible(Victim v) {
        return v != null && v.tagged() && !v.player() && !v.owned() && !v.named() && !v.persistent()
                && !v.boss() && !v.villager();
    }

    /** Whether a sporeling rises, given the key, the killer's form, the victim and both caps. */
    public static boolean mayRaise(boolean keyOn, int killerForm, Victim victim, int hostLive, int dimensionLive) {
        return keyOn && killerForm == FORM_HOST && eligible(victim)
                && hostLive < PER_HOST && dimensionLive < PER_DIMENSION;
    }

    /** A sporeling of this age has run out of life. */
    public static boolean expired(int ageTicks) {
        return ageTicks >= LIFE_TICKS;
    }

    /** Clamps a saved or synced form to the three known forms (unknown values become the host). */
    public static int form(int raw) {
        return raw == FORM_SPORELING || raw == FORM_POD ? raw : FORM_HOST;
    }
}
