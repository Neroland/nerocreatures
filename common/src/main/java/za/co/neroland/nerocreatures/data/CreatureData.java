package za.co.neroland.nerocreatures.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;

import za.co.neroland.nerolandcore.config.CoreConfig;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.entity.mechanical.TerraformingDrone;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * Registers NeroCreatures' player-keyed storage with Neroland Core's shared
 * {@link PlayerDataErasure} hook, so one erase request ({@code /neroland data eraseme} or an admin
 * erase) purges a player's NeroCreatures data together with every other Nero mod's. Called once from
 * {@link NeroCreaturesCommon#init()}.
 *
 * <p>The hook is registered <b>at construction</b>, ahead of the stores it purges, on purpose:
 * registering late is the classic way an erasure request silently misses a store.
 *
 * <p><b>Two stores are reachable from here:</b> {@link CreatureOwnershipState} (who owns which pet
 * or drone) and {@link BossContributionState} (damage totals for in-progress summoned boss fights).
 * The second is normally empty, because a fight's row is deleted the moment the fight resolves —
 * but "normally empty" is not a data-protection control, so erasure and retention reach it
 * unconditionally.
 *
 * <h2>What erasure actually does</h2>
 *
 * <p>Purging the index alone would not be enough — a pet keeps its owner's UUID in its own entity
 * data (vanilla's design for every tameable animal), so the creatures themselves have to be dealt
 * with too. {@link #erasePlayer} therefore does three things, in this order (and then clears the
 * player out of every boss fight they were contributing to):
 *
 * <ol>
 *   <li>takes the player's rows out of {@link CreatureOwnershipState} and keeps them;</li>
 *   <li>follows each row to its creature — in the dimension the row names, which is the only reason
 *       the dimension id is stored at all — and frees it: <b>pets are returned to the wild</b>
 *       (owner cleared, order reset, despawnable again) and <b>drones are recalled</b>, dropping
 *       their shell where they stood;</li>
 *   <li>sweeps every <em>loaded</em> level for any pet or drone still carrying that UUID and frees
 *       those too. The index is only an index; if it and the world ever disagree, this is the step
 *       that makes sure erasure is complete anyway.</li>
 * </ol>
 *
 * <p>Creatures in unloaded chunks that the index did not know about are the one gap, and it closes
 * itself: such a creature is unreachable until its chunk loads, and its owner row is already gone,
 * so it can never be counted, recalled or attributed to anyone again.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> nothing on this path logs the player's identity — the summary logs
 * counts only. See {@code PRIVACY.md} for the full statement of what is stored and why.
 */
public final class CreatureData {

    private CreatureData() {
    }

    /** Registers the eraser with Core. Idempotent per launch; called once from common init. */
    public static void init() {
        PlayerDataErasure.register(CreatureData::erasePlayer);
    }

    /**
     * Core's {@code PlayerDataEraser} body: purge one player from the ownership store and free every
     * creature that was bound to them.
     */
    public static void erasePlayer(MinecraftServer server, UUID player) {
        erasePlayer(server, player, CreatureOwnershipState.get(server));
        // The second player-keyed store: damage totals for in-progress summoned boss fights. It is
        // normally empty (a fight's row is deleted the moment the fight ends), but "normally" is not
        // a data-protection control, so erasure reaches it unconditionally.
        BossContributionState.get(server).forgetPlayer(player);
    }

    /**
     * The body of {@link #erasePlayer(MinecraftServer, UUID)} against an already-loaded store. Split
     * out so the retention sweep can reuse it without re-entering {@link CreatureOwnershipState#get}.
     */
    static void erasePlayer(MinecraftServer server, UUID player, CreatureOwnershipState state) {
        int freed = 0;
        for (OwnedCreature row : state.forgetPlayer(player)) {
            ServerLevel level = server.getLevel(row.dimensionKey());
            if (level == null) {
                continue;
            }
            if (free(level, level.getEntity(row.entity()))) {
                freed++;
            }
        }
        freed += sweepLoadedLevels(server, player);
        if (freed > 0) {
            // Count only — never who was erased, and never which creatures (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Erasure: released {} owned creature(s) back to the world.", freed);
        }
    }

    /**
     * Belt and braces: walk the loaded levels for pets and drones still carrying this UUID. Erasure
     * is a rare, explicit operation (a command or a retention sweep), so a full pass over loaded
     * entities is an acceptable cost for being certain — this is deliberately not something any tick
     * path calls.
     *
     * @return how many creatures this pass freed
     */
    private static int sweepLoadedLevels(MinecraftServer server, UUID player) {
        int freed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<TameableCreature> pets = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(TameableCreature.class),
                    pet -> player.equals(pet.ownerId()), pets);
            List<TerraformingDrone> drones = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(TerraformingDrone.class),
                    drone -> player.equals(drone.ownerId()), drones);
            for (TameableCreature pet : pets) {
                if (free(level, pet)) {
                    freed++;
                }
            }
            for (TerraformingDrone drone : drones) {
                if (free(level, drone)) {
                    freed++;
                }
            }
        }
        return freed;
    }

    /**
     * Frees one creature from its owner: a pet goes wild, a drone is recalled into its shell.
     *
     * @return {@code true} if the entity was something this mod owns and was actually freed
     */
    private static boolean free(ServerLevel level, Entity entity) {
        if (entity instanceof TameableCreature pet) {
            pet.releaseToWild();
            return true;
        }
        if (entity instanceof TerraformingDrone drone) {
            drone.recall(level);
            return true;
        }
        return false;
    }

    /**
     * POPIA/GDPR retention, run lazily on the first ownership-store access per server session (from
     * {@link CreatureOwnershipState#get}). Every player whose ownership has not changed in Core's
     * {@code dataRetentionDays} days is erased in full — index row <em>and</em> creatures — so a
     * pruned record leaves nothing behind, not even an owner UUID sitting in a pet's entity data.
     *
     * <p>A retention period of {@code 0} (Core's default) disables the sweep and leaves retention
     * entirely to Core's own purge-inactive flow, which reaches this store through the registered
     * eraser above.
     */
    public static void applyContributionRetention(BossContributionState state) {
        int days = CoreConfig.DATA_RETENTION_DAYS.get();
        List<UUID> stale = state.stalerThan(days);
        if (stale.isEmpty()) {
            return;
        }
        for (UUID player : stale) {
            state.forgetPlayer(player);
        }
        // Count only — never which players (POPIA/GDPR).
        NeroCreaturesCommon.LOGGER.info(
                "[NeroCreatures] Retention: purged boss contribution for {} player record(s) "
                        + "inactive for more than {} day(s).", stale.size(), days);
    }

    static void applyRetention(MinecraftServer server, CreatureOwnershipState state) {
        int days = CoreConfig.DATA_RETENTION_DAYS.get();
        List<UUID> stale = state.stalerThan(days);
        if (stale.isEmpty()) {
            return;
        }
        for (UUID player : stale) {
            erasePlayer(server, player, state);
        }
        // Count only — never which players (POPIA/GDPR).
        NeroCreaturesCommon.LOGGER.info(
                "[NeroCreatures] Retention: purged creature ownership for {} player record(s) "
                        + "inactive for more than {} day(s).", stale.size(), days);
    }
}
