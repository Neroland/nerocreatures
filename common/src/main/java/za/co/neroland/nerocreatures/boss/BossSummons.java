package za.co.neroland.nerocreatures.boss;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * The public way to start a boss fight on purpose — the seam NeroEvents will call when it lands, and
 * what the {@code /nerocreatures summon-boss} command will use.
 *
 * <h2>Why a summoned fight is not the same as a wild one</h2>
 *
 * <p>A boss can arrive two ways, and they pay differently. A boss found in the world drops its
 * loot table and nothing else — it is a place, and whoever kills it takes what falls. A boss
 * <b>summoned</b> here is a scheduled event with participants, so this method binds the fight to a
 * contribution row: damage per player is accumulated in {@link BossContributionState} for as long as
 * the fight lasts and paid out by {@link BossRewards} on death, split by share with a minimum
 * participation floor. The exact rule is documented on {@link BossRewards} and in
 * {@code wiki/Bosses.md}.
 *
 * <p>Contract for callers:
 *
 * <ul>
 *   <li><b>Caps are respected.</b> A summon is refused if the dimension or the chunk is already at
 *       its NeroCreatures population cap — the same rule {@code entity/humanoid/PirateSpawner}
 *       follows. An event that needs the room should raise the cap rather than route around it.</li>
 *   <li><b>Spawn reason is {@code EVENT}</b>, and every boss is persistent regardless, so a summoned
 *       fight does not evaporate.</li>
 *   <li><b>The initiator is optional and is used for one thing only</b> — being registered as a
 *       participant at zero damage, so whoever paid to start the fight is on the reward list even if
 *       they never land a hit. Pass {@code null} for a fight nobody in particular started (a
 *       scheduled world event). No other player identity is recorded, and the initiator is never
 *       written onto the boss entity.</li>
 *   <li><b>The fight id is not a player id.</b> It is a fresh random UUID per summon.</li>
 * </ul>
 *
 * <p>Server-side only.
 */
public final class BossSummons {

    /** Builds one boss, unspawned, at a position. */
    @FunctionalInterface
    public interface BossFactory {
        @Nullable
        NeroBossEntity create(ServerLevel level, BlockPos pos);
    }

    private static final Map<Identifier, BossFactory> BOSSES = new LinkedHashMap<>();

    private BossSummons() {
    }

    /**
     * Registers the bosses this mod ships. Called once from common init, after the entity types
     * exist — the factories resolve their types eagerly on Fabric.
     */
    public static void init() {
        register(id("cinder_tyrant"), (level, pos) -> {
            NeroBossEntity boss = ModEntities.CINDER_TYRANT.get().create(level, EntitySpawnReason.EVENT);
            return boss;
        });
    }

    /** Adds a boss to the summonable set. Idempotent for a given id. */
    public static void register(Identifier bossId, BossFactory factory) {
        BOSSES.put(bossId, factory);
    }

    /** Every summonable boss id, for commands and tab completion. */
    public static Set<Identifier> bossIds() {
        return Set.copyOf(BOSSES.keySet());
    }

    /** Whether {@code bossId} names a boss this mod can summon. */
    public static boolean isKnown(Identifier bossId) {
        return BOSSES.containsKey(bossId);
    }

    /**
     * Summons a boss and starts a contribution-tracked fight.
     *
     * @param level     the level to summon into
     * @param pos       where the boss arrives; also becomes the centre of its arena
     * @param bossId    which boss — one of {@link #bossIds()}
     * @param initiator the player who started the fight, or {@code null}. Recorded as a
     *                  zero-damage participant and nothing else.
     * @return the boss, or empty if the id is unknown, the population caps are full, or the entity
     *         could not be created
     */
    public static Optional<NeroBossEntity> summon(ServerLevel level, BlockPos pos, Identifier bossId,
            @Nullable UUID initiator) {
        BossFactory factory = BOSSES.get(bossId);
        if (factory == null) {
            return Optional.empty();
        }
        if (!CreatureCensus.dimensionHasRoom(level) || !CreatureCensus.chunkHasRoom(level, pos)) {
            NeroCreaturesCommon.LOGGER.info(
                    "[NeroCreatures] Boss summon refused: the population cap for this dimension or "
                            + "chunk is already full.");
            return Optional.empty();
        }
        NeroBossEntity boss = factory.create(level, pos);
        if (boss == null) {
            return Optional.empty();
        }
        boss.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);

        UUID fight = UUID.randomUUID();
        // Bind the fight BEFORE finalizeSpawn/addFreshEntity, so the very first hit is attributed.
        boss.bossController().trackContribution(fight);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        if (!level.addFreshEntity(boss)) {
            return Optional.empty();
        }
        if (initiator != null) {
            BossContributionState.get(level.getServer()).join(fight, bossId, initiator);
        }
        // A boss is a big addition to the population; the cached count is now stale by one.
        CreatureCensus.invalidate(level);
        return Optional.of(boss);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path);
    }
}
