package za.co.neroland.nerocreatures.block;

import java.util.Optional;

/**
 * Pure rules for the Nest: a home point for one tamed pet or mount (the Nest has no block
 * entity; the creature stores its own nest).
 *
 * <ul>
 *   <li><b>Owner-bound.</b> Only the creature's owner binds it, and only when the ownership store
 *       indexes that creature under that player. The owner is compared by UUID on the server; nothing
 *       here ever picks "the nearest player".</li>
 *   <li><b>What it does.</b> A homed creature that is not following its owner (told to stay or guard,
 *       or the owner is away) walks back when it strays more than {@value #STRAY_DISTANCE} blocks,
 *       but never treks more than {@value #MAX_RETURN_DISTANCE}; resting within {@value #REST_DISTANCE}
 *       blocks it slowly heals.</li>
 *   <li><b>Self-clearing.</b> A creature that is no longer tame, whose owner row is gone (erasure), or
 *       whose nest block is gone forgets its nest.</li>
 * </ul>
 *
 * <p>The home is saved as one entity tag {@code nerocreatures.nest:<dimension>@<x>,<y>,<z>}: creature
 * data, never in a player store, cleared with the creature's release.
 */
public final class NestRules {

    public static final String TAG_PREFIX = "nerocreatures.nest:";

    public static final double STRAY_DISTANCE = 10.0D;

    public static final double REST_DISTANCE = 3.0D;

    public static final double MAX_RETURN_DISTANCE = 64.0D;

    /** Binding reach: the owner's creature must be this close to the nest. */
    public static final double BIND_REACH = 8.0D;

    /** The entity-side helper checks on this cadence. */
    public static final int CHECK_TICKS = 40;

    /** Default ticks between 1 HP heals while resting. */
    public static final int DEFAULT_HEAL_INTERVAL_TICKS = 200;

    private NestRules() {
    }

    /** What a homed creature does this check. */
    public enum Action {
        NONE, RETURN, REST, FORGET
    }

    /** Binding needs both: the entity says this player owns it, and the store indexes it for them. */
    public static boolean mayBind(boolean entityOwnedByPlayer, boolean indexedForPlayer) {
        return entityOwnedByPlayer && indexedForPlayer;
    }

    /**
     * One check for a homed creature.
     *
     * @param stillValid  tame, owner row present, nest block present in the same dimension
     * @param following   told to follow and its owner is online in the same level
     * @param ridden      a mount with a rider never wanders off home
     * @param sitting     told to sit: it may rest and heal at the nest but is never walked anywhere
     * @param distance    distance to the nest
     */
    public static Action decide(boolean stillValid, boolean following, boolean ridden, boolean sitting,
            double distance) {
        if (!stillValid) {
            return Action.FORGET;
        }
        if (following || ridden) {
            return Action.NONE;
        }
        if (distance <= REST_DISTANCE) {
            return Action.REST;
        }
        if (!sitting && distance > STRAY_DISTANCE && distance <= MAX_RETURN_DISTANCE) {
            return Action.RETURN;
        }
        return Action.NONE;
    }

    /** The saved home. */
    public record Home(String dimension, int x, int y, int z) {

        public String encode() {
            return TAG_PREFIX + this.dimension + "@" + this.x + "," + this.y + "," + this.z;
        }

        /** Parses a tag written by {@link #encode()}; empty for anything else. */
        public static Optional<Home> decode(String tag) {
            if (tag == null || !tag.startsWith(TAG_PREFIX)) {
                return Optional.empty();
            }
            String body = tag.substring(TAG_PREFIX.length());
            int at = body.lastIndexOf('@');
            if (at <= 0) {
                return Optional.empty();
            }
            String[] xyz = body.substring(at + 1).split(",");
            if (xyz.length != 3) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Home(body.substring(0, at), Integer.parseInt(xyz[0]),
                        Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
