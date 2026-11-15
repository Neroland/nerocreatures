package za.co.neroland.nerocreatures.breeding;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One species' row of {@code species.json} (decision 003): how it breeds, when its Bestiary entry
 * becomes Studied, and its product timer. Every field is optional; a species with no row gets
 * {@link #DEFAULT} (live birth, threshold 1, no product).
 *
 * @param birth                egg or live birth
 * @param foodTag              breeding/taming food item tag id; empty means the convention
 *                             {@code nerocreatures:food/<species>}
 * @param studiedThreshold     Bestiary Studied threshold (decision 002)
 * @param productIntervalTicks ticks between products; 0 = this species has no product timer
 */
public record SpeciesProfile(BirthMode birth, Optional<String> foodTag, int studiedThreshold,
        int productIntervalTicks) {

    public static final SpeciesProfile DEFAULT = new SpeciesProfile(BirthMode.LIVE, Optional.empty(), 1, 0);

    public static final Codec<SpeciesProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BirthMode.CODEC.optionalFieldOf("birth", BirthMode.LIVE).forGetter(SpeciesProfile::birth),
            Codec.STRING.optionalFieldOf("food_tag").forGetter(SpeciesProfile::foodTag),
            Codec.intRange(1, 1000).optionalFieldOf("studied_threshold", 1).forGetter(SpeciesProfile::studiedThreshold),
            Codec.intRange(0, 24_000 * 30).optionalFieldOf("product_interval_ticks", 0)
                    .forGetter(SpeciesProfile::productIntervalTicks)
    ).apply(instance, SpeciesProfile::new));

    /** The food tag id for a species path, applying the convention when the row names none. */
    public String foodTagId(String speciesPath) {
        return this.foodTag.orElse("nerocreatures:food/" + speciesPath);
    }
}
