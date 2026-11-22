package za.co.neroland.nerocreatures.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.LevelResource;

import za.co.neroland.nerolandcore.config.CoreConfig;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.data.PlayerDataExport;

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
 * <p><b>Two stores of its own are reachable from here:</b> {@link CreatureOwnershipState} (who owns which pet
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
 *       (owner cleared, order reset, despawnable again), <b>mounts are unsaddled</b> (saddle,
 *       panniers and their contents dropped where the mount stands) and returned to the wild,
 *       <b>drones are recalled</b>, dropping their shell where they stood, and <b>egg rows are
 *       simply dropped</b> (the egg hatches unattributed);</li>
 *   <li>sweeps every <em>loaded</em> level for any pet or drone still carrying that UUID and frees
 *       those too. The index is only an index; if it and the world ever disagree, this is the step
 *       that makes sure erasure is complete anyway.</li>
 * </ol>
 *
 * <p>Creatures in unloaded chunks are released when their chunk next loads: erasure leaves a marker
 * for each removed row and a salted one-way hash of the owner in the store, and the load-time check
 * ({@link CreatureOwnershipState#loadVerdict}) releases a creature only on such a positive record.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> nothing on this path logs the player's identity — the summary logs
 * counts only. See {@code PRIVACY.md} for the full statement of what is stored and why.
 */
public final class CreatureData {

    private CreatureData() {
    }

    /**
     * Registers the eraser and the exporter with Core. Idempotent per launch; called once from common
     * init. The exporter hands Core's {@code /neroland data exportme} the same document as
     * {@code /nerocreatures data export} ({@link CreatureExport#export}), built in memory: Core's path
     * writes no file and logs nothing.
     */
    public static void init() {
        PlayerDataErasure.register(CreatureData::erasePlayer);
        PlayerDataExport.register(NeroCreaturesCommon.MOD_ID, CreatureExport::export);
    }

    /**
     * Frees creatures for the erasure path. Production reaches into the server's levels; the erasure
     * conformance test passes a recording fake, so the same ordering and marker handling is tested.
     */
    interface CreatureFreer {

        /** Frees the creature one removed row points at; {@code true} if it was loaded and freed. */
        boolean free(OwnedCreature row);

        /** Frees any loaded creature still bound to {@code player}; returns how many. */
        int sweep(UUID player);
    }

    /**
     * Core's {@code PlayerDataEraser} body: purge one player from both stores, free every creature
     * that was bound to them, and refresh both last-known-good backups.
     */
    public static void erasePlayer(MinecraftServer server, UUID player) {
        CreatureOwnershipState ownership = CreatureOwnershipState.get(server);
        BossContributionState contributions = BossContributionState.get(server);
        int freed = eraseStores(ownership, contributions, player, serverFreer(server));
        logReleased(freed);
        // A data-access export about this player is a copy of the same record: it goes too.
        eraseExports(server.getWorldPath(LevelResource.ROOT), player);
        // Core's last-known-good backups are a second copy of the same rows: erasure must reach them
        // in the same request, not at the next periodic backup pass (POPIA/GDPR).
        ownership.backupNow(server);
        contributions.backupNow(server);
    }

    /**
     * The whole store-side erasure for one player, against already-loaded stores: ownership rows of
     * every kind (pet, mount, drone, egg, parked) with their creatures freed or marked for release on
     * load, then the player's boss contribution in every fight (a fight nobody is left in is dropped).
     * Nest bindings and owner ids on the creatures go with {@code releaseToWild}.
     *
     * @return how many loaded creatures were freed
     */
    static int eraseStores(CreatureOwnershipState ownership, BossContributionState contributions, UUID player,
            CreatureFreer freer) {
        // Nothing on the erasure path may publish a companion-app event or raise an alert for the
        // player being erased: Core's own alert eraser has already run, so anything sent now would
        // re-create data about them. The release paths below already pass notify=false; this scope is
        // the second lock for anything they reach indirectly (a mount dropping gear, a drone's death).
        return za.co.neroland.nerocreatures.link.CreatureLinkEvents.whileErasing(player,
                () -> eraseStoresQuietly(ownership, contributions, player, freer));
    }

    private static int eraseStoresQuietly(CreatureOwnershipState ownership, BossContributionState contributions,
            UUID player, CreatureFreer freer) {
        int freed = eraseOwnership(ownership, player, freer);
        // The second player-keyed store: damage totals for in-progress summoned boss fights. It is
        // normally empty (a fight's row is deleted the moment the fight ends), but "normally" is not
        // a data-protection control, so erasure reaches it unconditionally.
        contributions.forgetPlayer(player);
        // In-memory link state: the action rate limiter and the altars the player lit (never saved).
        za.co.neroland.nerocreatures.link.CreatureLinkModule.forgetPlayer(player);
        return freed;
    }

    /**
     * The ownership half of erasure. Rows go first (leaving the load-time markers), then each row's
     * creature is freed if loaded (and its marker dropped, since it is no longer needed), then the
     * loaded levels are swept for creatures the index never knew about.
     */
    static int eraseOwnership(CreatureOwnershipState state, UUID player, CreatureFreer freer) {
        int freed = 0;
        for (OwnedCreature row : state.forgetPlayer(player)) {
            if (row.kind() == OwnedCreature.Kind.EGG) {
                // An egg row points at an incubation id, not an entity. Dropping the row is the whole
                // erasure: the egg still hatches, unattributed.
                continue;
            }
            if (freer.free(row)) {
                freed++;
                // Freed now, so the load-time marker for this creature is not needed.
                state.creatureFreed(row.entity());
            }
        }
        return freed + freer.sweep(player);
    }

    /** Deletes the player's export files; logs a count only, and only the exception class on failure. */
    static int eraseExports(Path worldRoot, UUID player) {
        try {
            int deleted = CreatureExportFile.deleteFor(worldRoot, player);
            if (deleted > 0) {
                NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Erasure: deleted {} data export file(s).", deleted);
            }
            return deleted;
        } catch (IOException e) {
            // Exception class only: the message holds a file path (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Erasure: deleting data export files failed: {}",
                    e.getClass().getSimpleName());
            return 0;
        }
    }

    /**
     * Retention for data-access export files, run with the daily ownership sweep and on every new
     * export: deletes files older than {@link CreatureExportFile#lifetimeDays}.
     */
    public static int applyExportRetention(Path worldRoot, long now) {
        try {
            int deleted = CreatureExportFile.deleteExpired(worldRoot, now,
                    CreatureExportFile.lifetimeDays(CoreConfig.DATA_RETENTION_DAYS.get()));
            if (deleted > 0) {
                NeroCreaturesCommon.LOGGER.info("[NeroCreatures] Retention: deleted {} expired data export file(s).",
                        deleted);
            }
            return deleted;
        } catch (IOException e) {
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Retention: deleting data export files failed: {}",
                    e.getClass().getSimpleName());
            return 0;
        }
    }

    private static void logReleased(int freed) {
        if (freed > 0) {
            // Count only — never who was erased, and never which creatures (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Erasure: released {} owned creature(s) back to the world.", freed);
        }
    }

    private static CreatureFreer serverFreer(MinecraftServer server) {
        return new CreatureFreer() {
            @Override
            public boolean free(OwnedCreature row) {
                ServerLevel level = server.getLevel(row.dimensionKey());
                // A parked row's creature is out of the world (companion storage): it is released by
                // the load-time check when it is re-created.
                return level != null && !row.parked() && CreatureData.free(level, level.getEntity(row.entity()));
            }

            @Override
            public int sweep(UUID player) {
                return sweepLoadedLevels(server, player);
            }
        };
    }

    /**
     * Belt and braces: walk the loaded levels for pets and drones still carrying this UUID, and clear
     * vanilla's {@code LoveCause} (the UUID of whoever fed an animal into love mode, kept on every bred
     * or hand-fed animal, wild ones included) where it names the player. Erasure is a rare, explicit
     * operation (a command or Core's inactivity purge), so a full pass over loaded entities is an
     * acceptable cost for being certain — this is deliberately not something any tick path calls.
     *
     * @return how many creatures this pass freed
     */
    private static int sweepLoadedLevels(MinecraftServer server, UUID player) {
        int freed = 0;
        int loveCleared = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Animal> animals = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(Animal.class), animal -> true, animals);
            for (Animal animal : animals) {
                if (clearLoveCause(animal, level, player)) {
                    loveCleared++;
                }
            }
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
        if (loveCleared > 0) {
            // Count only (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Erasure: cleared the breeding cause on {} loaded animal(s).", loveCleared);
        }
        return freed;
    }

    /**
     * Clears vanilla's {@code LoveCause} on one loaded animal when it names {@code player}. Vanilla has
     * no setter that clears it, but its loader sets the field from the save data and to nothing when
     * the key is absent, so the animal is saved, the key removed and the rest loaded back, which is
     * what {@code /data merge entity} does.
     */
    private static boolean clearLoveCause(Animal animal, ServerLevel level, UUID player) {
        CompoundTag saved = EntitySaves.save(animal, level);
        if (!withoutLoveCause(saved, player)) {
            return false;
        }
        EntitySaves.load(animal, saved, level);
        return true;
    }

    /**
     * Removes {@code LoveCause} from an entity's save data if it is {@code player}'s UUID (stored as an
     * NBT int array on every supported line).
     *
     * @return {@code true} if it was there and has been removed
     */
    static boolean withoutLoveCause(CompoundTag saved, UUID player) {
        Tag cause = saved.get(LOVE_CAUSE);
        if (cause == null || !cause.equals(new IntArrayTag(UUIDUtil.uuidToIntArray(player)))) {
            return false;
        }
        saved.remove(LOVE_CAUSE);
        return true;
    }

    /** Vanilla's save key for the UUID of the player that put an animal in love mode. */
    static final String LOVE_CAUSE = "LoveCause";

    /**
     * Frees one creature from its owner: a pet or mount goes wild (nest binding cleared, a mount
     * unsaddled), a drone is recalled into its shell.
     *
     * @return {@code true} if the entity was something this mod owns and was actually freed
     */
    private static boolean free(ServerLevel level, Entity entity) {
        // The erasure variants publish nothing to the former owner (they are being erased).
        if (entity instanceof TameableCreature pet) {
            pet.releaseForErasure();
            return true;
        }
        if (entity instanceof TerraformingDrone drone) {
            drone.recallForErasure(level);
            return true;
        }
        return false;
    }

    // --- retention ------------------------------------------------------------

    /**
     * POPIA/GDPR retention for the boss contribution store, run lazily (first access per server
     * session, then daily) from {@link BossContributionState#get}. A fight record lives only for one
     * fight, so its own clock is the right one here.
     */
    public static void applyContributionRetention(BossContributionState state) {
        int days = CoreConfig.DATA_RETENTION_DAYS.get();
        int purged = applyContributionRetention(state, days);
        if (purged > 0) {
            // Count only — never which players (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Retention: purged boss contribution for {} player record(s) "
                            + "inactive for more than {} day(s).", purged, days);
        }
    }

    /** The pure body of {@link #applyContributionRetention(BossContributionState)}. */
    public static int applyContributionRetention(BossContributionState state, int days) {
        List<UUID> stale = state.stalerThan(days);
        for (UUID player : stale) {
            state.forgetPlayer(player);
        }
        return stale.size();
    }

    /**
     * Retention for the ownership store, run lazily (first access per server session, then daily)
     * from {@link CreatureOwnershipState#get}: expires owner erasure markers older than
     * {@link CreatureOwnershipState#markerLifetimeDays}.
     *
     * <p>It deliberately does <b>not</b> decide who is inactive. Ownership rows change only when a
     * creature is tamed, released, deployed or parked, so "rows unchanged for N days" would release
     * every pet of a player who logs in daily but tamed nothing new. Inactivity is Core's last-seen
     * time ({@code PlayerActivity}); Core's purge-inactive sweep erases an inactive player through
     * the eraser registered in {@link #init()}, which runs the full erasure above.
     *
     * @return how many markers were dropped
     */
    static int applyRetention(CreatureOwnershipState state) {
        int markers = applyRetention(state, CoreConfig.DATA_RETENTION_DAYS.get());
        if (markers > 0) {
            // Count only (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Retention: dropped {} expired erasure marker(s).", markers);
        }
        return markers;
    }

    /** The pure body of {@link #applyRetention(CreatureOwnershipState)}. */
    static int applyRetention(CreatureOwnershipState state, int retentionDays) {
        return state.pruneErasureMarkers(CreatureOwnershipState.markerLifetimeDays(retentionDays));
    }
}
