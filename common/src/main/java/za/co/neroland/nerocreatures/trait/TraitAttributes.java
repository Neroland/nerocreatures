package za.co.neroland.nerocreatures.trait;

import java.util.Set;

/**
 * Which attribute ids and operations an {@code attribute} trait may name. Kept as plain strings so
 * the pool validates without touching the game registries (unit tests run without a bootstrap); the
 * id-to-holder mapping lives in {@link TraitEffects}. Every id here exists on every line from
 * 1.21.1 to 26.3, so a pool that validates applies on all 27 cells.
 */
public final class TraitAttributes {

    static final Set<String> ATTRIBUTES = Set.of(
            "minecraft:max_health", "minecraft:movement_speed", "minecraft:flying_speed",
            "minecraft:attack_damage", "minecraft:armor", "minecraft:knockback_resistance",
            "minecraft:follow_range", "minecraft:gravity", "minecraft:scale");

    static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

    private TraitAttributes() {
    }

    public static boolean isSupported(String attribute, String operation) {
        return ATTRIBUTES.contains(attribute) && OPERATIONS.contains(operation);
    }
}
