package za.co.neroland.nerocreatures.trait;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.RandomSource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Where trait effects meet the game. The effect types are a closed set (decision 003):
 *
 * <ul>
 *   <li>{@code attribute}: a permanent modifier with id {@code nerocreatures:trait/<id>}, applied by
 *       {@link #applyAttributes}. Permanent so it is saved with the entity's attributes and a
 *       reload never clamps health below a Hardy creature's real maximum.</li>
 *   <li>{@code ability_range}, {@code cooldown}, {@code product_yield}, {@code temperament},
 *       {@code carry_slots}: read through the query methods below by the systems that own them.</li>
 *   <li>{@code cosmetic}: Radiant. The renderer reads the synced flag; nothing here.</li>
 * </ul>
 *
 * <p>Distinct ids apply once each; when two distinct traits share a type (not the case in the
 * default pool) their multipliers multiply and their slot grants take the larger.
 */
public final class TraitEffects {

    /** Pannier slots without Stout (decision 004). */
    public static final int BASE_CARRY_SLOTS = 9;

    private TraitEffects() {
    }

    // --- attribute effects --------------------------------------------------

    /**
     * Adds or replaces the modifier of every attribute trait the individual has and removes those of
     * every attribute trait it does not have. Idempotent. A creature that was at full health stays at
     * full health when Hardy raises its maximum.
     */
    public static void applyAttributes(LivingEntity entity, @Nullable TraitSet traits, TraitPool pool) {
        boolean wasFull = entity.getHealth() >= entity.getMaxHealth();
        for (TraitDefinition def : pool.traits()) {
            TraitEffect effect = def.effect();
            if (effect.type() != TraitEffect.Type.ATTRIBUTE) {
                continue;
            }
            Holder<Attribute> attribute = resolve(effect.attribute());
            AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            Identifier id = modifierId(def.id());
            if (traits != null && traits.has(def.id())) {
                instance.addOrReplacePermanentModifier(new AttributeModifier(id, effect.amount(), operation(effect.operation())));
            } else {
                instance.removeModifier(id);
            }
        }
        if (wasFull && entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        } else if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /** {@code nerocreatures:trait/<id>}. */
    public static Identifier modifierId(String traitId) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "trait/" + traitId);
    }

    @Nullable
    static Holder<Attribute> resolve(String id) {
        return switch (id) {
            case "minecraft:max_health" -> Attributes.MAX_HEALTH;
            case "minecraft:movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "minecraft:flying_speed" -> Attributes.FLYING_SPEED;
            case "minecraft:attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "minecraft:armor" -> Attributes.ARMOR;
            case "minecraft:knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "minecraft:follow_range" -> Attributes.FOLLOW_RANGE;
            case "minecraft:gravity" -> Attributes.GRAVITY;
            case "minecraft:scale" -> Attributes.SCALE;
            default -> null;
        };
    }

    private static AttributeModifier.Operation operation(String key) {
        return switch (key) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    // --- queries (pure; no game state) --------------------------------------

    /** Keen: multiplier on an ability's range (1 when absent). */
    public static double abilityRangeMultiplier(@Nullable TraitSet traits, TraitPool pool) {
        return product(traits, pool, TraitEffect.Type.ABILITY_RANGE);
    }

    /** Thrifty: multiplier on ability cooldowns and mount ability stamina cost (1 when absent). */
    public static double cooldownMultiplier(@Nullable TraitSet traits, TraitPool pool) {
        return product(traits, pool, TraitEffect.Type.COOLDOWN);
    }

    /** Calm: multiplier on panic duration (1 when absent). */
    public static double panicMultiplier(@Nullable TraitSet traits, TraitPool pool) {
        double result = 1.0D;
        for (TraitDefinition def : applied(traits, pool, TraitEffect.Type.TEMPERAMENT)) {
            result *= def.effect().panicMultiplier();
        }
        return result;
    }

    /** Calm: a mount that never throws its rider. */
    public static boolean neverBuck(@Nullable TraitSet traits, TraitPool pool) {
        for (TraitDefinition def : applied(traits, pool, TraitEffect.Type.TEMPERAMENT)) {
            if (def.effect().neverBuck()) {
                return true;
            }
        }
        return false;
    }

    /** Bountiful: chance of one extra product roll (0 when absent; capped at 1). */
    public static double extraProductChance(@Nullable TraitSet traits, TraitPool pool) {
        double chance = 0.0D;
        for (TraitDefinition def : applied(traits, pool, TraitEffect.Type.PRODUCT_YIELD)) {
            chance = Math.max(chance, def.effect().extraRollChance());
        }
        return Math.min(1.0D, chance);
    }

    /** How many product rolls one harvest gets: 1, plus 1 on a Bountiful extra roll. */
    public static int productRolls(@Nullable TraitSet traits, TraitPool pool, RandomSource random) {
        double extra = extraProductChance(traits, pool);
        return extra > 0.0D && random.nextDouble() < extra ? 2 : 1;
    }

    /** Stout: pannier slots ({@value #BASE_CARRY_SLOTS} without it), rounded to whole rows, max 54. */
    public static int carrySlots(@Nullable TraitSet traits, TraitPool pool) {
        int slots = BASE_CARRY_SLOTS;
        for (TraitDefinition def : applied(traits, pool, TraitEffect.Type.CARRY_SLOTS)) {
            slots = Math.max(slots, def.effect().slots());
        }
        int rows = Math.max(1, Math.min(6, (slots + 8) / 9));
        return rows * 9;
    }

    private static double product(@Nullable TraitSet traits, TraitPool pool, TraitEffect.Type type) {
        double result = 1.0D;
        for (TraitDefinition def : applied(traits, pool, type)) {
            result *= def.effect().multiplier();
        }
        return result;
    }

    private static java.util.List<TraitDefinition> applied(@Nullable TraitSet traits, TraitPool pool,
            TraitEffect.Type type) {
        if (traits == null) {
            return java.util.List.of();
        }
        java.util.List<TraitDefinition> out = new java.util.ArrayList<>(2);
        for (String id : traits.effectiveIds()) {
            TraitDefinition def = pool.byId(id);
            if (def != null && def.effect().type() == type) {
                out.add(def);
            }
        }
        return out;
    }
}
