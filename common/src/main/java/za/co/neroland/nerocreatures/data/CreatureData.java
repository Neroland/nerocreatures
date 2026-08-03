package za.co.neroland.nerocreatures.data;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Registers NeroCreatures' player-keyed storage with Neroland Core's shared
 * {@link PlayerDataErasure} hook, so one erase request ({@code /neroland data eraseme}
 * or an admin erase) purges a player's NeroCreatures data together with every other
 * Nero mod's. Called once from {@link NeroCreaturesCommon#init()}.
 *
 * <p>The hook is registered <b>now</b>, while the stores that will feed it are still to be
 * built, on purpose: registering late is the classic way an erasure request silently misses a
 * store. The lambda below is a no-op today because NeroCreatures currently persists nothing
 * player-keyed — creatures live only as world entities.
 *
 * <p>POPIA/GDPR: the future stores hold only pet ownership (game UUID &rarr; owned entity ids) and
 * boss-contribution counters (game UUID &rarr; damage totals + a timestamp), both world-save
 * scoped — see PRIVACY.md. Erasure must never log player identity.
 */
public final class CreatureData {

    private CreatureData() {
    }

    public static void init() {
        PlayerDataErasure.register((server, uuid) -> {
            // TODO(Stage 5/6): purge this UUID from CreatureOwnershipState (untame pets, deactivate
            // drones) and BossContributionState. Nothing here may log who was erased.
        });
    }
}
