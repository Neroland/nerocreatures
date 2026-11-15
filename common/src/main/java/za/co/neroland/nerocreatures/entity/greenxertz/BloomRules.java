package za.co.neroland.nerocreatures.entity.greenxertz;

/**
 * The Bloom Host's sporeling rules as pure logic (decision 006, key {@code bloomHostSporelings};
 * per the entity contract the key is read directly, not through {@code mayAlter}, and the caps
 * always apply).
 *
 * <ul>
 *   <li>Only the {@link #FORM_HOST host} form raises sporelings, and only from a mob it killed
 *       itself.</li>
 *   <li>Never from players, owned (tamed) mobs, named mobs, persistent or leashed mobs, bosses or
 *       villager-type mobs; only from {@code #nerocreatures:sporeling_hosts}. Bred livestock
 *       (babies, animals in love or that bred recently) are spared too unless
 *       {@code bloomHostInfectsLivestock} is on.</li>
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
    public static final int RAISE_POSE_TICKS = 50;
    /**
     * {@code minecraft:scale} of a sporeling. 1.0: the art pass authored the sporeling form (the cap
     * alone on stub legs) already small, so the model is not shrunk twice.
     */
    public static final double SPORELING_SCALE = 1.0D;
    /** Particles in the spore strand from corpse to host. */
    public static final int STRAND_POINTS = 8;

    /** Hitbox of a sporeling (the cap on stub legs, per the art spec), in blocks. */
    public static final float SPORELING_WIDTH = 0.5F;
    public static final float SPORELING_HEIGHT = 0.6F;
    /** Hitbox of a spore pod (a closed bulb on a stalk), in blocks. */
    public static final float POD_WIDTH = 0.6F;
    public static final float POD_HEIGHT = 1.0F;

    /**
     * What is known about a slain mob, server side. {@code bred}: a baby, in love, or on its
     * breeding cooldown, i.e. most likely someone's livestock.
     */
    public record Victim(boolean player, boolean owned, boolean named, boolean persistent,
            boolean boss, boolean villager, boolean tagged, boolean bred) {

        /** A victim that is not bred livestock. */
        public Victim(boolean player, boolean owned, boolean named, boolean persistent,
                boolean boss, boolean villager, boolean tagged) {
            this(player, owned, named, persistent, boss, villager, tagged, false);
        }
    }

    private BloomRules() {
    }

    /** Whether the slain mob may rise at all, with bred livestock spared (the default). */
    public static boolean eligible(Victim v) {
        return eligible(v, false);
    }

    /**
     * Whether the slain mob may rise at all. Tamed, named, leashed and persistent mobs are always
     * spared; bred livestock only while {@code infectsLivestock} ({@code bloomHostInfectsLivestock})
     * is off.
     */
    public static boolean eligible(Victim v, boolean infectsLivestock) {
        return v != null && v.tagged() && !v.player() && !v.owned() && !v.named() && !v.persistent()
                && !v.boss() && !v.villager() && (infectsLivestock || !v.bred());
    }

    /** Whether a sporeling rises, given the key, the killer's form, the victim and both caps. */
    public static boolean mayRaise(boolean keyOn, int killerForm, Victim victim, int hostLive, int dimensionLive) {
        return mayRaise(keyOn, false, killerForm, victim, hostLive, dimensionLive);
    }

    /** As {@link #mayRaise(boolean, int, Victim, int, int)}, with the livestock key. */
    public static boolean mayRaise(boolean keyOn, boolean infectsLivestock, int killerForm, Victim victim,
            int hostLive, int dimensionLive) {
        return keyOn && killerForm == FORM_HOST && eligible(victim, infectsLivestock)
                && hostLive < PER_HOST && dimensionLive < PER_DIMENSION;
    }

    /** {@code {width, height}} scale factors from the host's registered size to {@code form}'s hitbox. */
    public static float[] dimensionScale(int form, float hostWidth, float hostHeight) {
        return switch (form(form)) {
            case FORM_SPORELING -> new float[] {SPORELING_WIDTH / hostWidth, SPORELING_HEIGHT / hostHeight};
            case FORM_POD -> new float[] {POD_WIDTH / hostWidth, POD_HEIGHT / hostHeight};
            default -> new float[] {1.0F, 1.0F};
        };
    }

    /** A sporeling of this age has run out of life. */
    public static boolean expired(int ageTicks) {
        return ageTicks >= LIFE_TICKS;
    }

    /**
     * Whether a dead Bloom Host of this form drops its loot table and experience. Only the host
     * does: sporelings are free raises, and pods are the Bloom Sovereign's regrowing spawns, so
     * loot from a killed pod (a {@code bloom_heart}) would be a farm.
     */
    public static boolean dropsLoot(int form) {
        return form(form) == FORM_HOST;
    }

    /** Clamps a saved or synced form to the three known forms (unknown values become the host). */
    public static int form(int raw) {
        return raw == FORM_SPORELING || raw == FORM_POD ? raw : FORM_HOST;
    }
}
