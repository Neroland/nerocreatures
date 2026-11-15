package za.co.neroland.nerocreatures.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/**
 * The live Lure Beacon and Sonic Ward fields of each loaded level, and the two questions NeroCreatures'
 * spawner asks of them. Server thread only. The rules themselves are {@link FieldRules}.
 *
 * <p>Nothing here is saved: a block entity re-registers its field every pulse while it burns, and every
 * field carries an expiry, so a broken, unloaded or starved block simply stops counting. Keyed weakly by
 * level, so a stopped server's levels are collectable. No player data at all: a field is a block
 * position, a species id and a number.
 */
public final class HabitatFields {

    private static final Map<ServerLevel, Map<BlockPos, FieldRules.Field>> FIELDS = new WeakHashMap<>();

    private HabitatFields() {
    }

    /** A block entity's pulse: (re)register or refresh its field until {@code now + TTL}. */
    public static void refresh(ServerLevel level, BlockPos pos, FieldRules.Kind kind, String species,
            double multiplier) {
        long expires = level.getGameTime() + FieldRules.FIELD_TTL_TICKS;
        FIELDS.computeIfAbsent(level, key -> new LinkedHashMap<>()).put(pos.immutable(),
                new FieldRules.Field(kind, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, species,
                        multiplier, expires));
    }

    /** A block that stopped burning or was removed. */
    public static void clear(ServerLevel level, BlockPos pos) {
        Map<BlockPos, FieldRules.Field> fields = FIELDS.get(level);
        if (fields != null) {
            fields.remove(pos);
            if (fields.isEmpty()) {
                FIELDS.remove(level);
            }
        }
    }

    /** The live fields of a level (expired ones pruned on the way). */
    public static List<FieldRules.Field> live(ServerLevel level) {
        Map<BlockPos, FieldRules.Field> fields = FIELDS.get(level);
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        long now = level.getGameTime();
        fields.values().removeIf(field -> !field.live(now));
        return fields.isEmpty() ? List.of() : new ArrayList<>(fields.values());
    }

    /**
     * Spawner hook: the factor a rule's weight is multiplied by at {@code pos}. 1 when no field is near
     * (the common case costs one map lookup).
     */
    public static double spawnWeightFactor(ServerLevel level, BlockPos pos, String creatureId,
            EntityType<?> type) {
        List<FieldRules.Field> fields = live(level);
        if (fields.isEmpty()) {
            return 1.0D;
        }
        return FieldRules.weightFactor(fields, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, creatureId,
                isHostile(type), level.getGameTime());
    }

    /** Spawner hook, per group member: whether a hostile may not be placed at {@code pos}. */
    public static boolean blocksHostile(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (!isHostile(type)) {
            return false;
        }
        List<FieldRules.Field> fields = live(level);
        return !fields.isEmpty()
                && FieldRules.warded(fields, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getGameTime());
    }

    /** Hostile = vanilla's {@code MONSTER} category, the same line the census and the spawner use. */
    public static boolean isHostile(EntityType<?> type) {
        return type != null && type.getCategory() == MobCategory.MONSTER;
    }

    /** Hostile for a live mob. */
    public static boolean isHostile(Mob mob) {
        return isHostile(mob.getType());
    }
}
