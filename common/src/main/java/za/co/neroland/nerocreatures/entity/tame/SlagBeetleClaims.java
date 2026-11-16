package za.co.neroland.nerocreatures.entity.tame;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;

/**
 * The Slag Beetle feed claim (a tamed Magnet Crab must not vacuum the ore a player
 * throws to a beetle). <b>The Magnet Crab calls {@link #isReservedForBeetle} for every candidate item
 * and skips it when true.</b>
 *
 * <p>An item is reserved when it is younger than {@value SlagBeetleRules#FRESH_ITEM_TICKS} ticks
 * (any item), or when it is beetle food ({@code nerocreatures:slag_beetle_ores}) within
 * {@value SlagBeetleRules#FEED_RADIUS} blocks of a living tamed Slag Beetle while
 * {@code slagBeetleSmelting} is on. No marker is written to the item: the rule is a pure function of
 * the item's age, its tag and one bounded entity query (at most one result).
 *
 * <p>Server thread only.
 */
public final class SlagBeetleClaims {

    private SlagBeetleClaims() {
    }

    /** Whether {@code item} is claimed for a beetle and must be left alone by item magnets. */
    public static boolean isReservedForBeetle(ServerLevel level, ItemEntity item) {
        if (item == null || !item.isAlive()) {
            return false;
        }
        int age = item.getAge();
        if (age < SlagBeetleRules.FRESH_ITEM_TICKS) {
            return true;
        }
        boolean food = item.getItem().is(SlagBeetle.FOOD_ORES);
        if (!food || !Boolean.TRUE.equals(NeroCreaturesConfig.SLAG_BEETLE_SMELTING.get())) {
            return false;
        }
        AABB box = item.getBoundingBox().inflate(SlagBeetleRules.FEED_RADIUS);
        List<SlagBeetle> found = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(SlagBeetle.class), box,
                beetle -> beetle.isAlive() && beetle.isTame(), found, 1);
        return SlagBeetleRules.reserved(age, true, !found.isEmpty());
    }
}
