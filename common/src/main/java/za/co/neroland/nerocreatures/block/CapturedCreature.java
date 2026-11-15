package za.co.neroland.nerocreatures.block;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import za.co.neroland.nerocreatures.trait.TraitSet;

/**
 * <b>The captured-creature data contract</b> shared by the Capture Pod (the item) and the Terrarium
 * (the block). Both read and write only through this class, so the format lives in one place.
 *
 * <p>A filled pod's {@code minecraft:custom_data} holds one compound under {@value #KEY}:
 *
 * <pre>
 * "nerocreatures:captured": {
 *   v: 1,                                   // schema; a reader refuses a newer one (keeps it intact)
 *   species: "nerocreatures:lumen_moth",    // full entity-type id
 *   entity: { ...entity save data... },     // as the entity saves itself, minus STRIPPED_KEYS
 *   traits: { v: 1, a: "hardy", b: "swift" },  // optional copy for tooltips and the Terrarium
 *   baby: false                             // optional, display only
 * }
 * </pre>
 *
 * <ul>
 *   <li><b>No owner identity, ever</b> (decision 004). {@link #strip} removes the owner reference, the
 *       entity UUID, love cause, leash and mount ownership, plus position and motion (a released creature
 *       takes its new place). A released creature is wild.</li>
 *   <li>An empty pod has no {@value #KEY} key. {@link #clear} removes it (and the whole component
 *       when nothing else is left).</li>
 *   <li>Which items are pods: item tag {@code #nerocreatures:capture_pods}. Which species fit a
 *       Terrarium: entity-type tag {@code #nerocreatures:terrarium_small}. Mounts and bosses are never
 *       captured (the pod refuses them).</li>
 * </ul>
 *
 * @param version schema version ({@code v})
 * @param species full entity-type id
 * @param entity  stripped entity save data
 * @param traits  the creature's traits, when it has any
 * @param baby    whether it was a baby (display only)
 */
public record CapturedCreature(int version, String species, CompoundTag entity, Optional<TraitSet> traits,
        boolean baby) {

    /** The custom-data key a filled pod carries. */
    public static final String KEY = "nerocreatures:captured";

    public static final int CURRENT_VERSION = 1;

    /**
     * Entity save keys never carried in a pod: identity (UUID, owner, love cause), attachments (leash,
     * passengers, mount ownership) and placement (position, motion, rotation, fall state).
     */
    public static final List<String> STRIPPED_KEYS = List.of(
            "UUID", "Owner", "owner", "LoveCause", "InLove", "Leash", "leash", "Passengers",
            "nerocreatures:mount", "Pos", "Motion", "Rotation", "FallDistance", "fall_distance",
            "FallFlying", "OnGround", "PortalCooldown", "Fire", "Air", "Sitting", "OrderedToSit");

    public static final Codec<CapturedCreature> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Always written (a plain default would be omitted on encode); absent reads as version 1.
            Codec.INT.optionalFieldOf("v").xmap(v -> v.orElse(CURRENT_VERSION), java.util.Optional::of)
                    .forGetter(CapturedCreature::version),
            Codec.STRING.fieldOf("species").forGetter(CapturedCreature::species),
            CompoundTag.CODEC.optionalFieldOf("entity", new CompoundTag()).forGetter(CapturedCreature::entity),
            TraitSet.CODEC.optionalFieldOf("traits").forGetter(CapturedCreature::traits),
            Codec.BOOL.optionalFieldOf("baby", false).forGetter(CapturedCreature::baby)
    ).apply(instance, CapturedCreature::new));

    /** A capture of the given save data, stripped of identity on the way in. */
    public static CapturedCreature of(String species, CompoundTag entityData, Optional<TraitSet> traits,
            boolean baby) {
        return new CapturedCreature(CURRENT_VERSION, species, strip(entityData), traits, baby);
    }

    /** Whether this reader understands the schema. A newer capture is kept intact, never released. */
    public boolean understood() {
        return this.version <= CURRENT_VERSION && !this.species.isBlank();
    }

    /** A copy of the entity data with every {@link #STRIPPED_KEYS} key removed. */
    public static CompoundTag strip(CompoundTag entityData) {
        CompoundTag copy = entityData == null ? new CompoundTag() : entityData.copy();
        for (String key : STRIPPED_KEYS) {
            copy.remove(key);
        }
        return copy;
    }

    // --- compound -----------------------------------------------------------

    /** Decodes the compound stored under {@link #KEY}; empty when absent or malformed. */
    public static Optional<CapturedCreature> fromTag(Tag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        return CODEC.parse(NbtOps.INSTANCE, tag).result();
    }

    /** Encodes this capture as the compound stored under {@link #KEY}. */
    public CompoundTag toTag() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    // --- item stacks --------------------------------------------------------

    /** The capture a pod carries, if any. */
    public static Optional<CapturedCreature> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        return fromTag(data.copyTag().get(KEY));
    }

    /** Whether a pod carries anything under {@link #KEY} (even a capture this reader cannot parse). */
    public static boolean isFilled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(KEY);
    }

    /** Writes this capture into a pod, keeping any other custom-data keys. */
    public void write(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.put(KEY, this.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, FILLED_MODEL);
    }

    /**
     * The filled-pod model switch: {@code custom_model_data} flag 0 = true on 1.21.4+ (item model
     * {@code minecraft:condition}), value 1 on 1.21.1 (model {@code overrides} predicate). Visual only;
     * {@link #isFilled} still reads {@link #KEY}.
     */
    //? if >=1.21.4 {
    private static final CustomModelData FILLED_MODEL = new CustomModelData(List.of(), List.of(true), List.of(), List.of());
    //?} else {
    /*private static final CustomModelData FILLED_MODEL = new CustomModelData(1);
    *///?}

    /** Empties a pod: removes {@link #KEY}, and the component when nothing else is left. */
    public static void clear(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        tag.remove(KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
