package za.co.neroland.nerocreatures.link;

import java.util.List;
import java.util.UUID;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.platform.Services;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * NeroCreatures' plug into Neroland Core's link API — the seam a companion client reaches a
 * player's bestiary, pets and boss fights through, without NeroCreatures knowing that any such
 * client exists.
 *
 * <p>The whole module is plain server-side Java against Core's
 * {@link za.co.neroland.nerolandcore.link} package: no loader wiring, no networking of its own, no
 * HTTP. NeroCreatures registers what it can show and what it can do; the separate NeroLink bridge
 * mod reads Core's registry and serves it. With no bridge installed this costs one registry entry.
 *
 * <p>Three surfaces, all registered from {@link NeroCreaturesCommon#init()}:
 *
 * <ul>
 *   <li><b>Read</b> — {@link CreatureLinkSnapshots}, serving the {@code bestiary}, {@code pets} and
 *       {@code bosses} sections;</li>
 *   <li><b>Write</b> — {@link CreatureLinkActions}, accepting {@code pet_recall} and (schema 2)
 *       {@code pet_command}, both owner-checked and rate-limited per player;</li>
 *   <li><b>Live</b> — {@link CreatureLinkEvents}, publishing the four player events that may push
 *       ({@code pet_state_changed} for a death, {@code creature_hurt}, {@code egg_hatched},
 *       {@code fight_ended}) and the participant-only {@code boss_phase} / {@code boss_defeated} events onto Core's
 *       shared event bus, and raising the two per-player alerts this mod has any business raising.</li>
 * </ul>
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p><b>Own data only.</b> Every snapshot section is scoped to the requesting {@code playerId}
 * before it leaves this mod: their own kill statistics, their own pets and drones, and their own
 * contribution to a boss fight. No other player's rows, shares or identity is ever put in a
 * snapshot, an action result, an event payload or an alert text. Boss events and the {@code bosses}
 * section reach only the players recorded in that fight; their payloads carry a boss, a phase and a
 * dimension and nothing player-shaped at all.
 *
 * <p><b>Erasure needs no separate wiring.</b> Every read here goes to the live stores
 * ({@code data/CreatureOwnershipState}, {@code boss/BossContributionState}), so a player erased
 * through Core's {@code PlayerDataErasure} hook immediately reads as owning nothing and having
 * contributed nothing. See {@code PRIVACY.md} and {@code wiki/Link-Module.md}.
 *
 * <p><b>Schema version 2</b> (0.5.0). Every schema-1 member is kept; schema 2 only adds members
 * (see {@code wiki/Link-Module.md}). Bump {@link #SCHEMA_VERSION} whenever the shape of a snapshot
 * section changes, so a companion client can tell what it is parsing.
 */
public final class CreatureLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroCreaturesCommon.MOD_ID;

    /** The snapshot schema revision. Bump on any change to a section's shape. */
    public static final int SCHEMA_VERSION = 2;

    /** Section: the roster, with the requesting player's own kill counts. */
    public static final String SECTION_BESTIARY = "bestiary";

    /** Section: the requesting player's own tamed pets and deployed drones, plus their caps. */
    public static final String SECTION_PETS = "pets";

    /** Section: summoned boss fights in progress, with the requesting player's own share. */
    public static final String SECTION_BOSSES = "bosses";

    /** Action: bring your own pets and mounts to you (see {@link CreatureLinkActions}). */
    public static final String ACTION_PET_RECALL = "pet_recall";

    /** Action (schema 2): tell one of your own pets or mounts to follow, stay or sit. */
    public static final String ACTION_PET_COMMAND = "pet_command";

    /**
     * Topic: one of your owned creatures died. Schema 1 also published tamed, released, deployed and
     * recalled here; schema 2 publishes only the death (the app polls the rest).
     */
    public static final String TOPIC_PET_STATE_CHANGED = "pet_state_changed";

    /** Topic (schema 2): one of your owned creatures is badly hurt. Player-scoped; may push. */
    public static final String TOPIC_CREATURE_HURT = "creature_hurt";

    /** Topic (schema 2): an egg you were incubating hatched. Player-scoped; may push. */
    public static final String TOPIC_EGG_HATCHED = "egg_hatched";

    /** Topic (schema 2): a summoned fight you joined ended. Player-scoped; may push. */
    public static final String TOPIC_FIGHT_ENDED = "fight_ended";

    /** Topic: a boss fight entered a new phase. Sent to the fight's participants only; no player data. */
    public static final String TOPIC_BOSS_PHASE = "boss_phase";

    /** Topic: a boss fight ended in the boss's defeat. Sent to its participants only; no player data. */
    public static final String TOPIC_BOSS_DEFEATED = "boss_defeated";

    /** The registered action handler, kept so erasure can reach its in-memory rate limiter. */
    private static volatile CreatureLinkActions actions;

    private CreatureLinkModule() {
    }

    /**
     * Erasure: forgets the player's in-memory link state (the action rate limiter and the altars they
     * lit). Called from {@code CreatureData} in the same request as the stores.
     */
    public static void forgetPlayer(UUID player) {
        CreatureLinkActions handler = actions;
        if (handler != null) {
            handler.forgetPlayer(player);
        }
        za.co.neroland.nerocreatures.boss.AltarUses.forget(player);
    }

    /**
     * Register the read, write and live surfaces with Core. Called <b>last</b> from
     * {@link NeroCreaturesCommon#init()}, so a companion client is never told about something before
     * the mod itself has finished reacting to it.
     *
     * <p>A failure here must never take the mod down with it: creatures work perfectly well with no
     * link module, so any problem is logged and swallowed.
     */
    public static void init() {
        try {
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION,
                    List.of(SECTION_BESTIARY, SECTION_PETS, SECTION_BOSSES),
                    List.of(ACTION_PET_RECALL, ACTION_PET_COMMAND));
            // One provider and one handler cover the whole module; Core keys both on the module id.
            CreatureLinkActions handler = new CreatureLinkActions();
            actions = handler;
            NeroLinkRegistry.registerSnapshotProvider(new CreatureLinkSnapshots(), info);
            NeroLinkRegistry.registerActionHandler(handler, info);
            CreatureLinkEvents.init();
        } catch (RuntimeException e) {
            NeroCreaturesCommon.LOGGER.warn(
                    "[NeroCreatures] Could not register the NeroLink module; companion clients will "
                            + "not see NeroCreatures data. Creatures themselves are unaffected. ({})",
                    e.getClass().getSimpleName());
            NeroCreaturesTelemetry.captureHandledException(e, "link-module", "register");
        }
    }

    /** This mod's public version string for discovery, or {@code "unknown"} if the seam is unhappy. */
    private static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
