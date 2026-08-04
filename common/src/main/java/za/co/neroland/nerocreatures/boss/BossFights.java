package za.co.neroland.nerocreatures.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live index of <b>contribution-tracked</b> boss fights: {@code fight id → controller}.
 *
 * <p>It exists so that "which summoned fights are happening right now, and how far along are they"
 * can be answered in one map read instead of a sweep over every loaded entity in every dimension.
 * The link module's {@code bosses} section asks that question on every snapshot, and a companion
 * client may ask often, so a sweep would be the wrong shape.
 *
 * <h2>What is in it, and for exactly how long</h2>
 *
 * <ul>
 *   <li>A fight is added when {@link BossSummons} binds it, and again when a saved boss is loaded
 *       back in carrying a fight id — both go through {@code BossController}'s single bind path, so
 *       a reloaded fight reappears here without any extra bookkeeping.</li>
 *   <li>A fight is dropped the moment its boss leaves the world for <em>any</em> reason: killed,
 *       unloaded, {@code /kill}ed. An unloaded boss's fight still exists in
 *       {@link BossContributionState} — it is still out there — but it has no live state to report,
 *       so reporting it as "in progress" would be a lie. It comes back when its chunk does.</li>
 *   <li>Everything here is in-memory and per server session. {@link #reset()} is called from the
 *       spawn engine's "new server" path, alongside {@code BossSpawns.reset()}.</li>
 * </ul>
 *
 * <p><b>Privacy (POPIA/GDPR):</b> this index is keyed by a fight id — a random UUID minted per
 * summon — and holds no player identity of any kind. Who fought a boss lives in
 * {@link BossContributionState} and nowhere else.
 *
 * <p>Server thread in practice; the backing map is concurrent so a stray read from the link
 * bridge's thread cannot corrupt it.
 */
public final class BossFights {

    private static final Map<UUID, BossController> ACTIVE = new ConcurrentHashMap<>();

    private BossFights() {
    }

    /** Registers a live fight. Called from {@code BossController}'s bind path only. */
    static void track(UUID fight, BossController controller) {
        ACTIVE.put(fight, controller);
    }

    /** Drops a fight from the live index. Idempotent. */
    static void forget(UUID fight) {
        ACTIVE.remove(fight);
    }

    /**
     * Every contribution-tracked fight currently loaded, as a defensive copy. Controllers whose
     * boss has already been defeated are filtered out — a fight is "in progress" or it is over.
     */
    public static List<BossController> active() {
        List<BossController> out = new ArrayList<>(ACTIVE.size());
        for (BossController controller : ACTIVE.values()) {
            if (!controller.isDefeated()) {
                out.add(controller);
            }
        }
        return List.copyOf(out);
    }

    /** How many contribution-tracked fights are loaded right now. */
    public static int activeCount() {
        return active().size();
    }

    /** Drops every tracked fight — called when the spawn engine sees a new server. */
    public static void reset() {
        ACTIVE.clear();
    }
}
