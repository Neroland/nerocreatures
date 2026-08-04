package za.co.neroland.nerocreatures.link;

import java.util.List;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.platform.Services;

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
 *   <li><b>Write</b> — {@link CreatureLinkActions}, accepting the single {@code pet_recall}
 *       action;</li>
 *   <li><b>Live</b> — {@link CreatureLinkEvents}, publishing {@code pet_state_changed},
 *       {@code boss_phase} and {@code boss_defeated} onto Core's shared event bus, and raising the
 *       two per-player alerts this mod has any business raising.</li>
 * </ul>
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p><b>Own data only.</b> Every snapshot section is scoped to the requesting {@code playerId}
 * before it leaves this mod: their own kill statistics, their own pets and drones, and their own
 * contribution to a boss fight. No other player's rows, shares or identity is ever put in a
 * snapshot, an action result, an event payload or an alert text. Boss events are broadcast — and
 * therefore reach every session — so their payloads carry a boss, a phase and a dimension and
 * nothing player-shaped at all, exactly as the {@code nerocreatures:boss_pressure} threshold
 * channel does.
 *
 * <p><b>Erasure needs no separate wiring.</b> Every read here goes to the live stores
 * ({@code data/CreatureOwnershipState}, {@code boss/BossContributionState}), so a player erased
 * through Core's {@code PlayerDataErasure} hook immediately reads as owning nothing and having
 * contributed nothing. See {@code PRIVACY.md} and {@code wiki/Link-Module.md}.
 *
 * <p><b>Schema version 1.</b> Bump {@link #SCHEMA_VERSION} whenever the shape of a snapshot section
 * changes, so a companion client can tell what it is parsing.
 */
public final class CreatureLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroCreaturesCommon.MOD_ID;

    /** The snapshot schema revision. Bump on any change to a section's shape. */
    public static final int SCHEMA_VERSION = 1;

    /** Section: the roster, with the requesting player's own kill counts. */
    public static final String SECTION_BESTIARY = "bestiary";

    /** Section: the requesting player's own tamed pets and deployed drones, plus their caps. */
    public static final String SECTION_PETS = "pets";

    /** Section: summoned boss fights in progress, with the requesting player's own share. */
    public static final String SECTION_BOSSES = "bosses";

    /** Action: bring your own pets to you (see {@link CreatureLinkActions}). */
    public static final String ACTION_PET_RECALL = "pet_recall";

    /** Topic: one of your pets or drones was tamed, released, deployed, recalled or died. */
    public static final String TOPIC_PET_STATE_CHANGED = "pet_state_changed";

    /** Topic: a boss fight entered a new phase. Broadcast; carries no player data. */
    public static final String TOPIC_BOSS_PHASE = "boss_phase";

    /** Topic: a boss fight ended in the boss's defeat. Broadcast; carries no player data. */
    public static final String TOPIC_BOSS_DEFEATED = "boss_defeated";

    private CreatureLinkModule() {
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
                    List.of(ACTION_PET_RECALL));
            // One provider and one handler cover the whole module; Core keys both on the module id.
            NeroLinkRegistry.registerSnapshotProvider(new CreatureLinkSnapshots(), info);
            NeroLinkRegistry.registerActionHandler(new CreatureLinkActions(), info);
            CreatureLinkEvents.init();
        } catch (RuntimeException e) {
            NeroCreaturesCommon.LOGGER.warn(
                    "[NeroCreatures] Could not register the NeroLink module; companion clients will "
                            + "not see NeroCreatures data. Creatures themselves are unaffected.", e);
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
