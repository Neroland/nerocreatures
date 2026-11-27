package za.co.neroland.nerocreatures.block;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
//?}

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
 *   entity: { ...entity save data... },     // only the ALLOWED_KEYS of what the entity saves
 *   traits: { v: 1, a: "hardy", b: "swift" },  // optional copy for tooltips and the Terrarium
 *   baby: false                             // optional, display only
 * }
 * </pre>
 *
 * <ul>
 *   <li><b>No owner identity, ever</b>. {@link #strip} removes the owner reference, the
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
     * The vanilla entity save keys a pod keeps: what a released creature needs to be the same animal
     * (health, age, name, attributes, effects, equipment, variant, tags). Everything else is dropped,
     * together with every {@code nerocreatures:} key except mount state. An allow-list, not a
     * deny-list, because the save data is open-ended: vanilla adds keys that hold a player UUID
     * ({@code last_hurt_by_player}, {@code AngryAt}, {@code Brain} memories) and loaders and other mods
     * add attachment blobs ({@code neoforge:attachments}, {@code fabric:attachments}, {@code ForgeCaps},
     * {@code ForgeData}) that may keep identity. Each name is listed in both its pre-1.21.5 and later
     * spelling where they differ.
     */
    public static final Set<String> ALLOWED_KEYS = Set.of(
            "Health", "Age", "ForcedAge", "AbsorptionAmount", "CustomName", "CustomNameVisible",
            "Attributes", "attributes", "active_effects", "ActiveEffects", "PersistenceRequired", "Silent",
            "NoGravity", "Glowing", "LeftHanded", "CanPickUpLoot", "equipment", "ArmorItems", "HandItems",
            "ArmorDropChances", "HandDropChances", "drop_chances", "body_armor_item", "body_armor_drop_chance",
            "Variant", "variant", "Tags");

    /** Mount state is never carried (mounts are refused anyway; a saddle is owner-bound gear). */
    private static final String MOUNT_PREFIX = "nerocreatures:mount";

    /** This mod's own keys (traits, digestion, visuals, products) are kept unless listed above. */
    private static final String OWN_PREFIX = "nerocreatures:";

    /** An entity tag that binds a creature to a Nest (a coordinate): never carried. */
    private static final String NEST_TAG_PREFIX = "nerocreatures.nest:";

    /**
     * Entity save keys known to carry identity, attachments or placement: the UUID, owner and love
     * cause, leash, passengers and mount ownership, position, motion and rotation. None is in
     * {@link #ALLOWED_KEYS}; the list exists so tests can assert each is gone.
     */
    public static final List<String> STRIPPED_KEYS = List.of(
            "UUID", "Owner", "owner", "LoveCause", "InLove", "Leash", "leash", "Passengers",
            "nerocreatures:mount", "Pos", "Motion", "Rotation", "FallDistance", "fall_distance",
            "FallFlying", "OnGround", "PortalCooldown", "Fire", "Air", "Sitting", "OrderedToSit",
            "last_hurt_by_player", "last_hurt_by_mob", "AngryAt", "angry_at", "Brain", "brain",
            "neoforge:attachments", "fabric:attachments", "ForgeCaps", "ForgeData", "home_pos", "HomePos");

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

    /**
     * A copy of the entity data holding only {@link #ALLOWED_KEYS} and this mod's own keys (mount state
     * excluded), with any Nest-binding entity tag removed from {@code Tags}.
     */
    public static CompoundTag strip(CompoundTag entityData) {
        CompoundTag copy = new CompoundTag();
        if (entityData == null) {
            return copy;
        }
        //? if >=1.21.5 {
        Set<String> keys = entityData.keySet();
        //?} else {
        /*Set<String> keys = entityData.getAllKeys();
        *///?}
        for (String key : List.copyOf(keys)) {
            if (keeps(key)) {
                Tag value = entityData.get(key);
                if (value != null) {
                    copy.put(key, value.copy());
                }
            }
        }
        if (copy.get("Tags") instanceof ListTag tags) {
            for (int i = tags.size() - 1; i >= 0; i--) {
                //? if >=1.21.5 {
                String tag = tags.getStringOr(i, "");
                //?} else {
                /*String tag = tags.getString(i);
                *///?}
                if (tag.startsWith(NEST_TAG_PREFIX)) {
                    tags.remove(i);
                }
            }
        }
        return copy;
    }

    /** Whether one entity save key may travel in a pod. */
    static boolean keeps(String key) {
        if (key.startsWith(MOUNT_PREFIX)) {
            return false;
        }
        return ALLOWED_KEYS.contains(key) || key.startsWith(OWN_PREFIX);
    }

    // --- compound -----------------------------------------------------------

    /** Decodes the compound stored under {@link #KEY}; empty when absent or malformed. */
    public static Optional<CapturedCreature> fromTag(Tag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        // Stripped on read as well: a pod written by an earlier build or by another mod must not
        // carry an owner into the world. A newer schema is kept intact (it is never released).
        return CODEC.parse(NbtOps.INSTANCE, tag).result().map(capture -> capture.understood()
                ? new CapturedCreature(capture.version, capture.species, strip(capture.entity), capture.traits,
                        capture.baby)
                : capture);
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
        CompoundTag data = CreatureStackData.copy(stack);
        if (data == null) {
            return Optional.empty();
        }
        return fromTag(data.get(KEY));
    }

    /** Whether a pod carries anything under {@link #KEY} (even a capture this reader cannot parse). */
    public static boolean isFilled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag data = CreatureStackData.copy(stack);
        return data != null && data.contains(KEY);
    }

    /** Writes this capture into a pod, keeping any other custom-data keys. */
    public void write(ItemStack stack) {
        writeTag(stack, this.toTag());
    }

    /**
     * Writes an already-encoded capture compound into a pod verbatim (a capture of a newer schema too, so
     * it is carried, never lost), keeping any other custom-data keys.
     */
    public static void writeTag(ItemStack stack, CompoundTag capture) {
        CreatureStackData.write(stack, podData(CreatureStackData.copy(stack), capture));
        //? if >=1.20.5 {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, FILLED_MODEL);
        //?} else {
        /*stack.getOrCreateTag().putInt("CustomModelData", 1);
        *///?}
    }

    /** A pod's custom data with {@code capture} (copied) under {@link #KEY}; other keys are kept. */
    public static CompoundTag podData(@org.jetbrains.annotations.Nullable CompoundTag existing, CompoundTag capture) {
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copy();
        tag.put(KEY, capture.copy());
        return tag;
    }

    /**
     * The filled-pod model switch: {@code custom_model_data} flag 0 = true on 1.21.4+ (item model
     * {@code minecraft:condition}), value 1 on 1.21.1 (model {@code overrides} predicate). Visual only;
     * {@link #isFilled} still reads {@link #KEY}.
     */
    //? if >=1.21.4 {
    private static final CustomModelData FILLED_MODEL = new CustomModelData(List.of(), List.of(true), List.of(), List.of());
    //?} else if >=1.20.5 {
    /*private static final CustomModelData FILLED_MODEL = new CustomModelData(1);
    *///?}

    /** Empties a pod: removes {@link #KEY}, and the component when nothing else is left. */
    public static void clear(ItemStack stack) {
        //? if >=1.20.5 {
        stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        //?} else {
        /*stack.removeTagKey("CustomModelData");
        *///?}
        CompoundTag data = CreatureStackData.copy(stack);
        if (data == null) {
            return;
        }
        data.remove(KEY);
        CreatureStackData.write(stack, data);
    }
}
