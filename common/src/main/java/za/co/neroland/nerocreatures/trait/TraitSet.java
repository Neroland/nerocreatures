package za.co.neroland.nerocreatures.trait;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * The two traits one individual carries, as stored in its entity NBT under {@value #NBT_KEY}:
 * {@code {v: 1, a: "hardy", b: "radiant"}} (decision 003).
 *
 * <ul>
 *   <li>{@code v} is the schema version. A reader that sees a {@code v} greater than
 *       {@link #CURRENT_VERSION} keeps the raw strings (so a downgrade round-trips them unchanged)
 *       but {@linkplain #effectiveIds() applies no effect}.</li>
 *   <li>Unknown ids are kept as strings and have no effect: forward-compatible.</li>
 *   <li>A duplicate (Swift + Swift) is allowed and applies once.</li>
 * </ul>
 *
 * <p>Egg items carry the same three fields in {@code minecraft:custom_data} beside a {@code species}
 * key ({@link #writeInto} / {@link #readFrom}). Traits are creature data, never player data.
 *
 * @param version schema version ({@code v})
 * @param a       first trait id
 * @param b       second trait id
 */
public record TraitSet(int version, String a, String b) {

    public static final String NBT_KEY = "nerocreatures:traits";

    public static final int CURRENT_VERSION = 1;

    public static final MapCodec<TraitSet> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // Always written (a plain default would be omitted on encode); absent reads as version 1.
            Codec.INT.optionalFieldOf("v").xmap(v -> v.orElse(CURRENT_VERSION), Optional::of)
                    .forGetter(TraitSet::version),
            Codec.STRING.fieldOf("a").forGetter(TraitSet::a),
            Codec.STRING.fieldOf("b").forGetter(TraitSet::b)
    ).apply(instance, TraitSet::new));

    public static final Codec<TraitSet> CODEC = MAP_CODEC.codec();

    public static TraitSet of(String a, String b) {
        return new TraitSet(CURRENT_VERSION, a, b);
    }

    /** Whether this reader understands the schema, i.e. whether effects apply at all. */
    public boolean understood() {
        return this.version <= CURRENT_VERSION;
    }

    /** The distinct ids whose effects apply (empty for a newer schema). Duplicates apply once. */
    public Set<String> effectiveIds() {
        if (!this.understood()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>(2);
        ids.add(this.a);
        ids.add(this.b);
        return ids;
    }

    /** Whether a trait applies to this individual. */
    public boolean has(String id) {
        return this.understood() && (this.a.equals(id) || this.b.equals(id));
    }

    public boolean isRadiant() {
        return this.has(TraitPool.RADIANT);
    }

    // --- NBT ---------------------------------------------------------------

    /** Encodes as a standalone compound {@code {v,a,b}}. */
    public CompoundTag toTag() {
        Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
        return (CompoundTag) tag;
    }

    /** Decodes a compound written by {@link #toTag()}; empty when absent or malformed. */
    public static Optional<TraitSet> fromTag(Tag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result();
    }

    /** Writes {@code v,a,b} into an existing compound (an egg's custom data), keeping other keys. */
    public void writeInto(CompoundTag target) {
        target.merge(this.toTag());
    }

    /** Reads {@code v,a,b} from a compound that may hold other keys (an egg's custom data). */
    public static Optional<TraitSet> readFrom(CompoundTag source) {
        return MAP_CODEC.codec().parse(NbtOps.INSTANCE, source).result();
    }
}
