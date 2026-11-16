package za.co.neroland.nerocreatures.entity.wrecks;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Id-based lookups the Wrecks creatures share, so their classes compile and run before (and without)
 * the shared registries naming them: drop items by id ({@code Items.AIR} until registered), the
 * {@code mechanical} and {@code chargeable} tags, and vanilla effects by id (their Java field names
 * differ across Minecraft lines; their ids do not).
 */
public final class WrecksLookups {

    /** Entity types a {@code static_jar} disables and whose deaths feed Scrap Hulk assembly. */
    public static final TagKey<EntityType<?>> MECHANICAL = TagKey.create(Registries.ENTITY_TYPE, id("mechanical"));

    /** Items a Static Swarm drains. */
    public static final TagKey<Item> CHARGEABLE = TagKey.create(Registries.ITEM, id("chargeable"));

    public static final Identifier STATIC_JAR = id("static_jar");
    public static final Identifier CAPTAINS_LEDGER = id("captains_ledger");
    public static final Identifier PLASMA_CELL = id("plasma_cell");
    public static final Identifier SALVAGED_CIRCUITRY = id("salvaged_circuitry");
    public static final Identifier SCRAP_HULK = id("scrap_hulk");
    /** Chest-type loot table a ledger's stash rolls. */
    public static final Identifier PIRATE_STASH_LOOT = id("gameplay/pirate_stash");

    private WrecksLookups() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path);
    }

    /** The registered item for an id, or {@code Items.AIR}. */
    public static Item item(Identifier itemId) {
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        return item == null ? Items.AIR : item;
    }

    /** Whether a stack is the item with this id. */
    public static boolean isItem(ItemStack stack, Identifier itemId) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId);
    }

    /** Whether an entity's type is in {@link #MECHANICAL}. */
    public static boolean mechanical(Entity entity) {
        return entity.getType().builtInRegistryHolder().is(MECHANICAL);
    }

    /** A vanilla effect by its {@code minecraft:} path, or {@code null}. */
    @Nullable
    public static Holder<MobEffect> effect(String vanillaPath) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.withDefaultNamespace(vanillaPath));
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }
}
