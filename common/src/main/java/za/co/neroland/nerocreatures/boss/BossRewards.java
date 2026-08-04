package za.co.neroland.nerocreatures.boss;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.registry.ModItems;

/**
 * The enhanced reward a <b>summoned</b> boss pays out on top of its loot table, split by damage
 * share. This class is the single place the rule lives, and the rule is written out for players in
 * {@code wiki/Bosses.md} — keep the two in step.
 *
 * <h2>The rule</h2>
 *
 * <p>Let {@code share} be a participant's damage divided by all damage recorded for that fight.
 *
 * <ol>
 *   <li><b>The loot table drops as normal</b>, where the boss fell, for everybody. A summon never
 *       replaces the base drop; it only adds.</li>
 *   <li><b>Base share</b> — every recorded participant, including the summoner even at zero damage,
 *       gets {@value #BASE_TROPHIES} Apex Trophy, {@value #BASE_CRYSTAL} Refined Crystal and
 *       {@value #BASE_PLASMA} Plasma Cell. Turning up and being part of the fight is enough.</li>
 *   <li><b>Participation floor</b> — a participant whose {@code share} is below
 *       {@value #PARTICIPATION_FLOOR} (5%) gets the base share <b>and nothing more</b>. This is the
 *       anti-leech rule: standing at the back and landing one arrow does not earn a boss's worth of
 *       materials.</li>
 *   <li><b>Scaled share</b> — a participant at or above the floor also gets
 *       {@code round(share × pool)} of each bonus material, where the pool is
 *       {@value #POOL_CRYSTAL} Refined Crystal and {@value #POOL_PLASMA} Plasma Cell. Because the
 *       shares of everyone above the floor sum to at most 1, the pool is what the fight is worth,
 *       divided by who did the work.</li>
 *   <li><b>Major share</b> — at or above {@value #MAJOR_SHARE} (25%) a participant gets one extra
 *       Apex Trophy. A trophy is the boss-kill token, so the people who actually killed it get a
 *       second one.</li>
 *   <li>Every count above is a <b>bonus roll</b> in the {@code wiki/Drop-Map.md} sense and is scaled
 *       by {@code dropRateMultiplier}. Setting that to {@code 0} switches the whole enhanced reward
 *       off and leaves the loot table alone.</li>
 * </ol>
 *
 * <h2>Delivery</h2>
 *
 * <p>A participant who is online in the boss's dimension gets their share dropped at their feet. A
 * participant who has logged out or left the dimension gets it dropped where the boss fell — leave
 * the fight before it ends and your share is left on the floor with everyone else's. That is
 * deliberate: the alternative is a mailbox, which would mean storing a player's unclaimed items
 * indefinitely, and this mod does not keep a player-keyed store one moment longer than it must.
 *
 * <p>Server-side only; no player identity is logged.
 */
public final class BossRewards {

    /** Minimum damage share for anything above the base reward. */
    public static final float PARTICIPATION_FLOOR = 0.05F;

    /** Damage share at or above which a participant earns a second trophy. */
    public static final float MAJOR_SHARE = 0.25F;

    /** Apex Trophies every participant receives. */
    private static final int BASE_TROPHIES = 1;

    /** Refined Crystal every participant receives. */
    private static final int BASE_CRYSTAL = 2;

    /** Plasma Cells every participant receives. */
    private static final int BASE_PLASMA = 2;

    /** Refined Crystal divided among the participants above the floor, by share. */
    private static final int POOL_CRYSTAL = 12;

    /** Plasma Cells divided among the participants above the floor, by share. */
    private static final int POOL_PLASMA = 8;

    private BossRewards() {
    }

    /**
     * Pays every share out. A no-op for an empty list, which is the normal case for a fight whose
     * contribution row was never created.
     *
     * @param level  the level the boss died in
     * @param where  where the boss fell — the fallback drop point
     * @param shares the resolved shares from {@link BossContributionState#resolve}
     */
    public static void payOut(ServerLevel level, Vec3 where, List<BossContributionState.Share> shares) {
        if (shares.isEmpty()) {
            return;
        }
        double multiplier = NeroCreaturesConfig.DROP_RATE_MULTIPLIER.get();
        if (multiplier <= 0.0D) {
            return;
        }
        for (BossContributionState.Share share : shares) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(share.player());
            Vec3 target = player != null && player.level() == level ? player.position() : where;

            int trophies = BASE_TROPHIES + (share.fraction() >= MAJOR_SHARE ? 1 : 0);
            int crystal = BASE_CRYSTAL;
            int plasma = BASE_PLASMA;
            if (share.fraction() >= PARTICIPATION_FLOOR) {
                crystal += Math.round(share.fraction() * POOL_CRYSTAL);
                plasma += Math.round(share.fraction() * POOL_PLASMA);
            }
            drop(level, target, ModItems.APEX_TROPHY.get(), scale(trophies, multiplier));
            drop(level, target, ModItems.REFINED_CRYSTAL.get(), scale(crystal, multiplier));
            drop(level, target, ModItems.PLASMA_CELL.get(), scale(plasma, multiplier));
        }
    }

    private static int scale(int count, double multiplier) {
        return (int) Math.floor(count * multiplier);
    }

    private static void drop(ServerLevel level, Vec3 where, Item item, int count) {
        if (count <= 0) {
            return;
        }
        ItemEntity entity = new ItemEntity(level, where.x(), where.y() + 0.5D, where.z(),
                new ItemStack(item, count));
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
