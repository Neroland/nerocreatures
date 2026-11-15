package za.co.neroland.nerocreatures.entity.alpha;

import java.util.List;

/**
 * One data-driven alpha modifier, read from
 * {@code data/<namespace>/nerocreatures/alpha/modifiers/<name>.json}.
 *
 * <pre>{@code
 * {
 *   "weight": 10,
 *   "attributes": [ { "attribute": "minecraft:movement_speed", "amount": 0.25,
 *                     "operation": "add_multiplied_base" } ],
 *   "effects": [ { "effect": "minecraft:regeneration", "amplifier": 0 } ]
 * }
 * }</pre>
 *
 * <p>An alpha rolls exactly one modifier, weighted. Attribute bonuses become permanent attribute
 * modifiers (saved by vanilla with the entity); effects are infinite and hidden.
 *
 * @param id         resource id, e.g. {@code nerocreatures:swift}
 * @param weight     roll weight, at least 1
 * @param attributes attribute bonuses
 * @param effects    permanent effects
 */
public record AlphaModifier(String id, int weight, List<AttributeBonus> attributes, List<EffectBonus> effects) {

    /**
     * @param attribute attribute id (1.21.2+ form; {@code generic.} is tried on older lines)
     * @param amount    modifier amount
     * @param operation {@code add_value}, {@code add_multiplied_base} or {@code add_multiplied_total}
     */
    public record AttributeBonus(String attribute, double amount, String operation) {
    }

    /**
     * @param effect    mob effect id
     * @param amplifier 0-based amplifier
     */
    public record EffectBonus(String effect, int amplifier) {
    }
}
