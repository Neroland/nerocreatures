package za.co.neroland.nerocreatures.trait;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one trait does, as read from the jar-bundled pool. The <b>type is a closed set implemented in
 * code</b>; the JSON only tunes the numbers. Fields that a type does not use are simply
 * left at their neutral default, so one flat record serves every type and the codec stays trivial.
 *
 * @param type            which hook in code consumes this trait
 * @param attribute       {@link Type#ATTRIBUTE} only: the attribute id, e.g. {@code minecraft:max_health}
 * @param operation       {@link Type#ATTRIBUTE} only: {@code add_value}, {@code add_multiplied_base} or
 *                        {@code add_multiplied_total}
 * @param amount          {@link Type#ATTRIBUTE} only: the modifier amount
 * @param multiplier      {@link Type#ABILITY_RANGE} and {@link Type#COOLDOWN}: the multiplier (1 = none)
 * @param extraRollChance {@link Type#PRODUCT_YIELD}: chance of one extra product roll
 * @param panicMultiplier {@link Type#TEMPERAMENT}: panic duration multiplier (1 = none)
 * @param neverBuck       {@link Type#TEMPERAMENT}: a mount with this never throws its rider
 * @param slots           {@link Type#CARRY_SLOTS}: pannier slots granted
 */
public record TraitEffect(Type type, String attribute, String operation, double amount, double multiplier,
        double extraRollChance, double panicMultiplier, boolean neverBuck, int slots) {

    /** The closed set of effect types. A new type is a code change, never a data change. */
    public enum Type {
        ATTRIBUTE, ABILITY_RANGE, PRODUCT_YIELD, TEMPERAMENT, COOLDOWN, CARRY_SLOTS, COSMETIC;

        public static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(Type::parse, Type::key);

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        static DataResult<Type> parse(String key) {
            for (Type type : values()) {
                if (type.key().equals(key)) {
                    return DataResult.success(type);
                }
            }
            return DataResult.error(() -> "Unknown trait effect type: " + key);
        }
    }

    public static final Codec<TraitEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Type.CODEC.fieldOf("type").forGetter(TraitEffect::type),
            Codec.STRING.optionalFieldOf("attribute", "").forGetter(TraitEffect::attribute),
            Codec.STRING.optionalFieldOf("operation", "add_value").forGetter(TraitEffect::operation),
            Codec.DOUBLE.optionalFieldOf("amount", 0.0D).forGetter(TraitEffect::amount),
            Codec.DOUBLE.optionalFieldOf("multiplier", 1.0D).forGetter(TraitEffect::multiplier),
            Codec.DOUBLE.optionalFieldOf("extra_roll_chance", 0.0D).forGetter(TraitEffect::extraRollChance),
            Codec.DOUBLE.optionalFieldOf("panic_multiplier", 1.0D).forGetter(TraitEffect::panicMultiplier),
            Codec.BOOL.optionalFieldOf("never_buck", false).forGetter(TraitEffect::neverBuck),
            Codec.INT.optionalFieldOf("slots", 0).forGetter(TraitEffect::slots)
    ).apply(instance, TraitEffect::new));

    public static TraitEffect attribute(String attribute, String operation, double amount) {
        return new TraitEffect(Type.ATTRIBUTE, attribute, operation, amount, 1.0D, 0.0D, 1.0D, false, 0);
    }

    public static TraitEffect multiplier(Type type, double multiplier) {
        return new TraitEffect(type, "", "add_value", 0.0D, multiplier, 0.0D, 1.0D, false, 0);
    }

    public static TraitEffect productYield(double extraRollChance) {
        return new TraitEffect(Type.PRODUCT_YIELD, "", "add_value", 0.0D, 1.0D, extraRollChance, 1.0D, false, 0);
    }

    public static TraitEffect temperament(double panicMultiplier, boolean neverBuck) {
        return new TraitEffect(Type.TEMPERAMENT, "", "add_value", 0.0D, 1.0D, 0.0D, panicMultiplier, neverBuck, 0);
    }

    public static TraitEffect carrySlots(int slots) {
        return new TraitEffect(Type.CARRY_SLOTS, "", "add_value", 0.0D, 1.0D, 0.0D, 1.0D, false, slots);
    }

    public static TraitEffect cosmetic() {
        return new TraitEffect(Type.COSMETIC, "", "add_value", 0.0D, 1.0D, 0.0D, 1.0D, false, 0);
    }
}
