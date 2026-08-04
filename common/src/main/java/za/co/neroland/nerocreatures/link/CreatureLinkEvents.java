package za.co.neroland.nerocreatures.link;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.data.OwnedCreature;

/**
 * The live half of the link module: the three things worth waking a companion client for, plus the
 * two things worth interrupting a player for.
 *
 * <h2>Events</h2>
 *
 * <ul>
 *   <li><b>{@code pet_state_changed}</b> — one of <em>your</em> owned creatures changed hands with
 *       the world: {@code tamed}, {@code released}, {@code deployed}, {@code recalled} or
 *       {@code died}. Published with {@link LinkEvent#forPlayer}, so the bridge routes it to that
 *       player's sessions and nobody else's. It mirrors the {@code pets} snapshot section, which is
 *       why a drone's states are on the same topic — the section covers both.</li>
 *   <li><b>{@code boss_phase}</b> — a boss fight escalated. <b>Broadcast</b>, because a boss fight
 *       is a place, not a person.</li>
 *   <li><b>{@code boss_defeated}</b> — a boss fight ended in the boss's defeat. Also broadcast.</li>
 * </ul>
 *
 * <h2>Alerts</h2>
 *
 * <p>Two, and deliberately only two — an alert survives in Core's store until it is acknowledged,
 * so it is reserved for things a player would genuinely want to be told about while the game is
 * closed:
 *
 * <ul>
 *   <li><b>your pet died</b> — raised for the owner alone;</li>
 *   <li><b>a boss you damaged was defeated</b> — raised for each recorded participant of a
 *       <em>summoned</em> fight (a natural fight records nobody, so it alerts nobody).</li>
 * </ul>
 *
 * <h2>Scope and privacy (POPIA/GDPR)</h2>
 *
 * <p>A broadcast reaches every session, so a broadcast payload here carries a boss id, a phase
 * number, a phase count and a dimension id — and nothing else. There is no participant list, no
 * killer, no damage total and no position: the same rule Core's {@code ThresholdEvents} contract
 * imposes on {@code nerocreatures:boss_pressure}, applied to the same information.
 *
 * <p>An alert's {@code text} is a plain string by Core's contract and therefore <b>must never
 * contain another player's data</b>. Both texts here name a creature or a boss and nothing else —
 * "Your Glacite Wisp did not survive", never who killed it. Names come from
 * {@link CreatureLinkAccess#readableName} rather than a lang file, because a dedicated server has
 * no client assets to resolve one with.
 *
 * <p><b>Nothing here may throw at its caller.</b> Every publisher is wrapped: a link failure must
 * never disturb a pet's death, a drone's recall or a boss's phase transition.
 *
 * <p>Server thread only.
 */
public final class CreatureLinkEvents {

    /** A pet was tamed by its owner. */
    public static final String STATE_TAMED = "tamed";

    /** A pet stopped being owned — erasure, retention, or any other release to the wild. */
    public static final String STATE_RELEASED = "released";

    /** A drone was unfolded from its shell and bound to its owner. */
    public static final String STATE_DEPLOYED = "deployed";

    /** A drone was folded back into its shell. */
    public static final String STATE_RECALLED = "recalled";

    /** An owned creature died. */
    public static final String STATE_DIED = "died";

    private CreatureLinkEvents() {
    }

    /**
     * Nothing to subscribe to. NeroCreatures publishes from the gameplay paths themselves rather
     * than from an in-mod bus, because there is no in-mod bus to hang off: taming, recall, death and
     * phase transitions are each exactly one call site. The method exists so
     * {@link CreatureLinkModule#init()} has the same three-surface shape as every other Nero mod's,
     * and so a future in-mod listener has an obvious home.
     */
    static void init() {
        // Intentionally empty — see the javadoc.
    }

    // --- pet_state_changed ----------------------------------------------------

    /**
     * Publishes one owned-creature state change to its owner's sessions.
     *
     * @param level    the level the creature is in
     * @param owner    the owning player's UUID; {@code null} is ignored (a wild creature has no
     *                 owner to tell)
     * @param creature the creature itself
     * @param kind     pet or drone, matching the {@code pets} snapshot section's {@code kind}
     * @param state    one of the {@code STATE_*} constants
     */
    public static void petStateChanged(ServerLevel level, @Nullable UUID owner, Entity creature,
            OwnedCreature.Kind kind, String state) {
        if (owner == null) {
            return;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
            payload.addProperty("entity", creature.getUUID().toString());
            payload.addProperty("kind", kind.key());
            payload.addProperty("type", EntityType.getKey(creature.getType()).toString());
            payload.addProperty("name", CreatureLinkAccess.readableName(creature.getType()));
            payload.addProperty("dimension", level.dimension().identifier().toString());
            payload.addProperty("state", state);
            payload.addProperty("timestamp", System.currentTimeMillis());
            publish(LinkEvent.forPlayer(CreatureLinkModule.MODULE_ID,
                    CreatureLinkModule.TOPIC_PET_STATE_CHANGED, owner, payload));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_PET_STATE_CHANGED, e);
        }
    }

    /**
     * The death case: the same {@code pet_state_changed} event, plus the "your pet died" alert.
     * Split out because the alert is the whole point — a companion app that is closed when a pet
     * dies should still be able to say so afterwards.
     */
    public static void petDied(ServerLevel level, @Nullable UUID owner, Entity creature,
            OwnedCreature.Kind kind) {
        petStateChanged(level, owner, creature, kind, STATE_DIED);
        if (owner == null) {
            return;
        }
        try {
            String name = CreatureLinkAccess.readableName(creature.getType());
            // Game state only: what died, never who or what killed it (POPIA/GDPR).
            raise(level.getServer(), owner, "pet_lost." + creature.getUUID(),
                    LinkAlert.Severity.WARN,
                    kind == OwnedCreature.Kind.DRONE
                            ? "Your " + name + " was destroyed."
                            : "Your " + name + " did not survive.");
        } catch (RuntimeException e) {
            warn("alerts", e);
        }
    }

    // --- boss_phase -----------------------------------------------------------

    /** Broadcasts a phase transition. Carries a boss, a phase and a place — never a person. */
    public static void bossPhase(ServerLevel level, Identifier bossId, int phase, int phaseCount,
            boolean summoned) {
        try {
            JsonObject payload = bossPayload(level, bossId, phaseCount, summoned);
            payload.addProperty("phase", phase);
            publish(LinkEvent.broadcast(CreatureLinkModule.MODULE_ID,
                    CreatureLinkModule.TOPIC_BOSS_PHASE, payload));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_BOSS_PHASE, e);
        }
    }

    // --- boss_defeated --------------------------------------------------------

    /**
     * Broadcasts a defeat, then raises one private alert per recorded participant.
     *
     * <p>The broadcast and the alerts carry deliberately different things. The broadcast says only
     * that a boss fell in a dimension — it reaches everybody, so it may not say who was there. The
     * alerts are private to one player each and say "a boss you fought was defeated", which is a
     * fact about that player's own participation.
     *
     * @param shares the resolved contribution shares, already emptied out of the store by the
     *               caller; empty for a natural fight, which alerts nobody
     */
    public static void bossDefeated(ServerLevel level, Identifier bossId, int phaseCount,
            boolean summoned, List<BossContributionState.Share> shares) {
        try {
            JsonObject payload = bossPayload(level, bossId, phaseCount, summoned);
            payload.addProperty("participants", shares.size());
            publish(LinkEvent.broadcast(CreatureLinkModule.MODULE_ID,
                    CreatureLinkModule.TOPIC_BOSS_DEFEATED, payload));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_BOSS_DEFEATED, e);
        }
        if (shares.isEmpty()) {
            return;
        }
        try {
            String name = CreatureLinkAccess.readablePath(bossId.getPath());
            String text = "The " + name + " you fought has been defeated.";
            for (BossContributionState.Share share : shares) {
                // One id per fight, so a player who fought two bosses gets two alerts and a
                // re-raise for the same fight replaces rather than stacks.
                raise(level.getServer(), share.player(), "boss_defeated." + bossId.getPath() + "."
                        + level.dimension().identifier().getPath(), LinkAlert.Severity.INFO, text);
            }
        } catch (RuntimeException e) {
            warn("alerts", e);
        }
    }

    private static JsonObject bossPayload(ServerLevel level, Identifier bossId, int phaseCount,
            boolean summoned) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        payload.addProperty("boss", bossId.toString());
        payload.addProperty("name", CreatureLinkAccess.readablePath(bossId.getPath()));
        payload.addProperty("dimension", level.dimension().identifier().toString());
        payload.addProperty("phase_count", phaseCount);
        payload.addProperty("summoned", summoned);
        payload.addProperty("timestamp", System.currentTimeMillis());
        return payload;
    }

    // --- plumbing -------------------------------------------------------------

    /** Raises one alert through Core's per-player store. Never logs who it was raised for. */
    private static void raise(MinecraftServer server, UUID player, String alertId,
            LinkAlert.Severity severity, String text) {
        if (server == null) {
            return;
        }
        LinkAlerts.get(server).raise(server, player,
                LinkAlert.raise(alertId, CreatureLinkModule.MODULE_ID, severity, text));
    }

    /** Publish to Core's shared bus; a failure there is logged, never thrown at the caller. */
    private static void publish(LinkEvent event) {
        try {
            NeroLinkRegistry.eventBus().publish(event);
        } catch (RuntimeException e) {
            warn(event.topic(), e);
        }
    }

    /** Topic only — never who the event was for (POPIA/GDPR). */
    private static void warn(String topic, RuntimeException e) {
        NeroCreaturesCommon.LOGGER.warn(
                "[NeroCreatures] Publishing the NeroLink '{}' event failed.", topic, e);
    }
}
