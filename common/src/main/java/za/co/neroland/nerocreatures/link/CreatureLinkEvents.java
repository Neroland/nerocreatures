package za.co.neroland.nerocreatures.link;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * The live half of the link module: the few things worth telling a player's phone about, plus the
 * boss broadcasts and the two alerts.
 *
 * <h2>Player events — each one a push when the player has turned on the "Creatures" category</h2>
 *
 * <p>The bridge pushes <em>every</em> player-scoped event of a module to a player who has that
 * module's category on and no live app connection, and it has no per-topic opt-out. So this class
 * publishes a player-scoped event <b>only</b> for the four things that should push, each with the
 * bridge's {@code _notify} copy whose body names the species and nothing else:
 *
 * <ul>
 *   <li><b>{@code pet_state_changed}</b> with {@code state: "died"} — an owned pet, mount or drone
 *       died ("Your Dune Loper died.");</li>
 *   <li><b>{@code creature_hurt}</b> — an owned creature dropped below a quarter health while not
 *       being hurt by its owner, at most once per creature per five minutes ("Your Dune Loper is badly
 *       hurt.");</li>
 *   <li><b>{@code egg_hatched}</b> — an egg the player was incubating hatched ("A Lumen Moth egg has
 *       hatched.");</li>
 *   <li><b>{@code fight_ended}</b> — a summoned boss fight the player joined was won or ended ("The
 *       Rime Matriarch fight has ended."), one event per participant with their own share only.</li>
 * </ul>
 *
 * <p>Routine changes — tamed, released, deployed, recalled, orders, traits, timers — are <b>not</b>
 * published any more (schema 2): the app polls the {@code pets} section for them. The topic and
 * payload shape of {@code pet_state_changed} are unchanged for schema-1 clients.
 *
 * <h2>Broadcasts</h2>
 *
 * <p>{@code boss_phase} and {@code boss_defeated} go to every session and therefore carry a boss, a
 * phase, a phase count and a dimension id — nothing about any player. A broadcast never pushes.
 *
 * <h2>Alerts</h2>
 *
 * <p>"Your pet died" for the owner and "a boss you fought was defeated" for each participant of a
 * summoned fight, through Core's alert store. Each is raised <b>after</b> the matching player event,
 * so the bridge's rich copy shadows the alert's generic push.
 *
 * <p><b>Nothing here may throw at its caller.</b> Every publisher is wrapped: a link failure must
 * never disturb a pet's death, an egg's hatching or a boss's phase transition. Server thread only.
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

    /** Per-creature "badly hurt" debounce, in memory. */
    private static final CreatureLinkRules.HurtDebounce HURT = new CreatureLinkRules.HurtDebounce();

    /** Players whose erasure is running right now; nothing may be published or raised for them. */
    private static final Set<UUID> ERASING = new HashSet<>();

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

    // --- erasure guard ----------------------------------------------------------

    /**
     * Runs one erasure with every event and alert for {@code player} suppressed. An erasure must not
     * re-create data about the person it erases: Core's alert eraser has already run by the time this
     * mod's eraser frees their creatures, so a "released" or "died" event published now would sit in
     * the bridge, a relay queue or an app cache. Server thread only; nests safely.
     */
    public static <T> T whileErasing(UUID player, Supplier<T> body) {
        boolean added = ERASING.add(player);
        try {
            return body.get();
        } finally {
            if (added) {
                ERASING.remove(player);
            }
        }
    }

    /** Whether anything may be published to (or raised for) this player now. */
    public static boolean mayPublishTo(@Nullable UUID player) {
        return player == null || !ERASING.contains(player);
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
        if (owner == null || !publishesState(state)) {
            return; // routine state is polled, never pushed (schema 2)
        }
        try {
            Identifier type = EntityType.getKey(creature.getType());
            JsonObject payload = petStatePayload(creature.getUUID(), kind, type,
                    level.dimension().identifier().toString(), state, System.currentTimeMillis());
            publish(LinkEvent.forPlayer(CreatureLinkModule.MODULE_ID,
                    CreatureLinkModule.TOPIC_PET_STATE_CHANGED, owner, payload));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_PET_STATE_CHANGED, e);
        }
    }

    /** Only a death is published as a {@code pet_state_changed} event (it is the one that pushes). */
    static boolean publishesState(String state) {
        return STATE_DIED.equals(state);
    }

    /**
     * The schema-1 {@code pet_state_changed} payload, plus the {@code _notify} copy for a death. The
     * species name in {@code name} and the dimension id are for the player's own app; the push body
     * uses the species only.
     */
    static JsonObject petStatePayload(UUID entity, OwnedCreature.Kind kind, Identifier type, String dimension,
            String state, long timestamp) {
        String species = CreatureLinkJson.speciesName(type);
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        payload.addProperty("entity", entity.toString());
        payload.addProperty("kind", kind.key());
        payload.addProperty("type", type.toString());
        payload.addProperty("name", species);
        payload.addProperty("dimension", dimension);
        payload.addProperty("state", state);
        payload.addProperty("timestamp", timestamp);
        if (STATE_DIED.equals(state)) {
            payload.add("_notify", CreatureLinkRules.notify(CreatureLinkRules.diedBody(species)));
        }
        return payload;
    }

    // --- creature_hurt ----------------------------------------------------------

    /**
     * An owned creature just crossed below a quarter health. Pushed to its owner at most once per
     * creature per five minutes, and never while its own owner is the one hurting it.
     */
    public static void creatureHurt(ServerLevel level, @Nullable UUID owner, Entity creature,
            OwnedCreature.Kind kind, boolean byOwner) {
        if (owner == null) {
            return;
        }
        try {
            if (!HURT.shouldAlert(creature.getUUID(), byOwner, System.currentTimeMillis())) {
                return;
            }
            publish(LinkEvent.forPlayer(CreatureLinkModule.MODULE_ID, CreatureLinkModule.TOPIC_CREATURE_HURT,
                    owner, hurtPayload(creature.getUUID(), kind, EntityType.getKey(creature.getType()),
                            System.currentTimeMillis())));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_CREATURE_HURT, e);
        }
    }

    static JsonObject hurtPayload(UUID entity, OwnedCreature.Kind kind, Identifier type, long timestamp) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        payload.addProperty("entity", entity.toString());
        payload.addProperty("kind", kind.key());
        payload.addProperty("type", type.toString());
        payload.addProperty("timestamp", timestamp);
        payload.add("_notify", CreatureLinkRules.notify(CreatureLinkRules.hurtBody(CreatureLinkJson.speciesName(type))));
        return payload;
    }

    // --- egg_hatched ------------------------------------------------------------

    /** An egg the player was incubating hatched. Called before the egg row is removed. */
    public static void eggHatched(ServerLevel level, @Nullable UUID owner, EntityType<?> species) {
        if (owner == null) {
            return; // an unattributed egg (erased owner) tells nobody
        }
        try {
            publish(LinkEvent.forPlayer(CreatureLinkModule.MODULE_ID, CreatureLinkModule.TOPIC_EGG_HATCHED,
                    owner, hatchedPayload(EntityType.getKey(species), System.currentTimeMillis())));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_EGG_HATCHED, e);
        }
    }

    static JsonObject hatchedPayload(Identifier type, long timestamp) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        payload.addProperty("type", type.toString());
        payload.addProperty("kind", OwnedCreature.Kind.EGG.key());
        payload.addProperty("timestamp", timestamp);
        payload.add("_notify", CreatureLinkRules.notify(CreatureLinkRules.hatchedBody(CreatureLinkJson.speciesName(type))));
        return payload;
    }

    // --- fight_ended ------------------------------------------------------------

    /**
     * A summoned fight ended without a win (the boss left the world): each player who joined is told on
     * their own. {@code players} comes straight from the discarded contribution row.
     */
    public static void fightEnded(ServerLevel level, Identifier bossId, List<UUID> players) {
        for (UUID player : players) {
            publishFightEnded(player, bossId, false, 0.0F);
        }
    }

    private static void publishFightEnded(UUID player, Identifier bossId, boolean defeated, float share) {
        try {
            publish(LinkEvent.forPlayer(CreatureLinkModule.MODULE_ID, CreatureLinkModule.TOPIC_FIGHT_ENDED,
                    player, fightEndedPayload(bossId, defeated, share, System.currentTimeMillis())));
        } catch (RuntimeException e) {
            warn(CreatureLinkModule.TOPIC_FIGHT_ENDED, e);
        }
    }

    /** Boss, outcome and the player's <b>own</b> share; never anybody else's. */
    static JsonObject fightEndedPayload(Identifier bossId, boolean defeated, float share, long timestamp) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        payload.addProperty("type", bossId.toString());
        payload.addProperty("kind", "boss");
        payload.addProperty("outcome", defeated ? "defeated" : "ended");
        payload.addProperty("share", Math.max(0.0F, Math.min(1.0F, share)));
        payload.addProperty("timestamp", timestamp);
        payload.add("_notify", CreatureLinkRules.notify(
                CreatureLinkRules.fightEndedBody(CreatureLinkJson.speciesName(bossId))));
        return payload;
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
            String name = CreatureLinkJson.speciesName(EntityType.getKey(creature.getType()));
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
            String name = CreatureLinkJson.speciesName(bossId);
            String text = "The " + name + " you fought has been defeated.";
            for (BossContributionState.Share share : shares) {
                // The rich, species-only push first, so it shadows the alert's generic one.
                publishFightEnded(share.player(), bossId, true, share.fraction());
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
        if (server == null || !mayPublishTo(player)) {
            return;
        }
        LinkAlerts.get(server).raise(server, player,
                LinkAlert.raise(alertId, CreatureLinkModule.MODULE_ID, severity, text));
    }

    /** Publish to Core's shared bus; a failure there is logged, never thrown at the caller. */
    private static void publish(LinkEvent event) {
        if (!event.isBroadcast() && !mayPublishTo(event.playerId())) {
            return; // the player is being erased
        }
        try {
            NeroLinkRegistry.eventBus().publish(event);
        } catch (RuntimeException e) {
            warn(event.topic(), e);
        }
    }

    /** Topic only — never who the event was for (POPIA/GDPR). */
    private static void warn(String topic, RuntimeException e) {
        NeroCreaturesCommon.LOGGER.warn(
                "[NeroCreatures] Publishing the NeroLink '{}' event failed: {}", topic, e.getClass().getSimpleName());
        NeroCreaturesTelemetry.captureHandledException(e, "link-event", topic);
    }
}
