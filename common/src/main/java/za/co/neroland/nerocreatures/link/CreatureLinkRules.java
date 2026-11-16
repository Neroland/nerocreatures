package za.co.neroland.nerocreatures.link;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

/**
 * The pure rules of the link module (schema 2): push copy, the "badly hurt" crossing and its debounce,
 * and the per-player action rate limit. No world, no server: everything here is tested directly.
 */
public final class CreatureLinkRules {

    /** Push title for every NeroCreatures push. */
    public static final String NOTIFY_TITLE = "NeroCreatures";

    /** Deep link with no entity segment: a push opens the Creatures module, never one creature. */
    public static final String NOTIFY_DEEP_LINK = "nerolink://server/{server}/nerocreatures";

    /** Below this fraction of its maximum health a creature counts as badly hurt. */
    public static final float BADLY_HURT_FRACTION = 0.25F;

    /** One "badly hurt" push per creature per this many milliseconds (five minutes). */
    public static final long HURT_DEBOUNCE_MS = 5L * 60L * 1000L;

    private CreatureLinkRules() {
    }

    // --- push copy ----------------------------------------------------------------

    /** "Your Dune Loper died." */
    public static String diedBody(String species) {
        return "Your " + species + " died.";
    }

    /** "Your Dune Loper is badly hurt." */
    public static String hurtBody(String species) {
        return "Your " + species + " is badly hurt.";
    }

    /** "A Lumen Moth egg has hatched." */
    public static String hatchedBody(String species) {
        return "A " + species + " egg has hatched.";
    }

    /** "The Rime Matriarch fight has ended." */
    public static String fightEndedBody(String boss) {
        return "The " + boss + " fight has ended.";
    }

    /**
     * The bridge's reserved {@code _notify} member: title, a body that names a species and nothing else,
     * and a deep link to the module root. Never a custom name, a dimension, a position or a player.
     */
    public static JsonObject notify(String body) {
        JsonObject notify = new JsonObject();
        notify.addProperty("title", NOTIFY_TITLE);
        notify.addProperty("body", body);
        notify.addProperty("deepLink", NOTIFY_DEEP_LINK);
        return notify;
    }

    // --- badly hurt -----------------------------------------------------------------

    /** Whether a health fraction is "badly hurt". */
    public static boolean isBadlyHurt(float healthFraction) {
        return healthFraction > 0.0F && healthFraction < BADLY_HURT_FRACTION;
    }

    /**
     * Per-creature debounce for "badly hurt", keyed by the creature's own id (not a player), in memory,
     * pruned as it goes.
     */
    public static final class HurtDebounce {
        private final Map<UUID, Long> lastAlert = new HashMap<>();

        /**
         * Whether a fresh crossing should push now, and if so remembers it.
         *
         * @param byOwner the creature's own owner is the one hurting it (sparring is not news)
         */
        public boolean shouldAlert(UUID creature, boolean byOwner, long nowMs) {
            if (byOwner) {
                return false;
            }
            if (this.lastAlert.size() > 256) {
                for (Iterator<Map.Entry<UUID, Long>> it = this.lastAlert.entrySet().iterator(); it.hasNext();) {
                    if (nowMs - it.next().getValue() >= HURT_DEBOUNCE_MS) {
                        it.remove();
                    }
                }
            }
            Long last = this.lastAlert.get(creature);
            if (last != null && nowMs - last < HURT_DEBOUNCE_MS) {
                return false;
            }
            this.lastAlert.put(creature, nowMs);
            return true;
        }

        int size() {
            return this.lastAlert.size();
        }
    }

    // --- rate limit -------------------------------------------------------------------

    /**
     * Per-player, per-action rate limit for link actions, on top of the bridge's per-device limit. In
     * memory only, keyed by the player's game UUID, pruned as it goes and never saved, so it is not a
     * store: an entry is useless (and dropped) once its interval has passed.
     */
    public static final class RateLimiter {
        private final Map<UUID, Map<String, Long>> last = new HashMap<>();

        /**
         * Takes one call if the player's previous call of this action is at least {@code intervalMs}
         * old. A refused call does not restart the wait.
         */
        public boolean tryAcquire(UUID player, String action, long nowMs, long intervalMs) {
            if (intervalMs <= 0L) {
                return true;
            }
            if (this.last.size() > 512) {
                this.prune(nowMs);
            }
            Map<String, Long> actions = this.last.computeIfAbsent(player, p -> new HashMap<>());
            Long previous = actions.get(action);
            if (previous != null && nowMs - previous < intervalMs) {
                return false;
            }
            actions.put(action, nowMs);
            return true;
        }

        /** Drops entries older than ten minutes, longer than any configurable interval. */
        void prune(long nowMs) {
            for (Iterator<Map<String, Long>> players = this.last.values().iterator(); players.hasNext();) {
                Map<String, Long> actions = players.next();
                actions.values().removeIf(at -> nowMs - at > 600_000L);
                if (actions.isEmpty()) {
                    players.remove();
                }
            }
        }

        /** Erasure: forgets the player at once. */
        public void forget(UUID player) {
            this.last.remove(player);
        }

        int players() {
            return this.last.size();
        }
    }
}
