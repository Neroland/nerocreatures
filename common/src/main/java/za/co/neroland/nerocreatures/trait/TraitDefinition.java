package za.co.neroland.nerocreatures.trait;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One entry of the trait pool: an id (stored on the entity), a roll weight and the effect.
 *
 * @param id     lower-case id, e.g. {@code hardy}; this exact string is what entity NBT stores
 * @param weight relative weight of a fresh roll ({@code radiant} is deliberately tiny)
 * @param effect what the trait does
 */
public record TraitDefinition(String id, int weight, TraitEffect effect) {

    public static final Codec<TraitDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TraitDefinition::id),
            Codec.INT.fieldOf("weight").forGetter(TraitDefinition::weight),
            TraitEffect.CODEC.fieldOf("effect").forGetter(TraitDefinition::effect)
    ).apply(instance, TraitDefinition::new));
}
