package za.co.neroland.nerocreatures.block;

import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import za.co.neroland.nerocreatures.trait.TraitSet;

/**
 * An egg item's contents: {@code minecraft:custom_data} holds
 * {@code {species: "nerocreatures:lumen_moth", v: 1, a: "hardy", b: "swift"}}. The species is a full
 * entity-type id; a bare path is read in the {@code nerocreatures} namespace. No owner, no player, no
 * position: an egg is creature data only.
 *
 * <p>The egg layer writes eggs with {@link #write}; the Incubator reads them with {@link #read}. Which
 * items count as eggs is the item tag {@code #nerocreatures:creature_eggs}.
 *
 * @param species the entity-type id that hatches
 * @param traits  the rolled traits
 */
public record EggData(String species, TraitSet traits) {

    public static final String SPECIES_KEY = "species";

    private static final Codec<String> SPECIES_CODEC = Codec.STRING.fieldOf(SPECIES_KEY).codec();

    /** The species id with its namespace ({@code nerocreatures} when the egg named a bare path). */
    public String speciesId() {
        return this.species.indexOf(':') < 0 ? "nerocreatures:" + this.species : this.species;
    }

    /** Reads an egg compound; empty when the species or the traits are missing or malformed. */
    public static Optional<EggData> fromTag(CompoundTag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        Optional<String> species = SPECIES_CODEC.parse(NbtOps.INSTANCE, tag).result()
                .filter(s -> !s.isBlank());
        Optional<TraitSet> traits = TraitSet.readFrom(tag);
        if (species.isEmpty() || traits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EggData(species.get(), traits.get()));
    }

    /** Encodes as an egg compound ({@code species, v, a, b}). */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        this.traits.writeInto(tag);
        tag.merge((CompoundTag) SPECIES_CODEC.encodeStart(NbtOps.INSTANCE, this.speciesId()).getOrThrow());
        return tag;
    }

    /** Reads an egg stack's custom data. */
    public static Optional<EggData> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? Optional.empty() : fromTag(data.copyTag());
    }

    /** Writes this egg into a stack's custom data, keeping any other keys. */
    public void write(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.merge(this.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
