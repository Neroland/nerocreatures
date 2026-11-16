package za.co.neroland.nerocreatures.block;

import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.breeding.BreedingHooks;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.trait.Inheritance;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.trait.TraitSet;

/**
 * The egg layer: an egg-birth species' breeding drops one
 * {@code <species>_egg} (one item per species, same namespace) carrying the inherited traits in
 * {@code minecraft:custom_data}. No owner, no player, no position is written: an egg is creature data.
 * The egg hatches only in an Incubator, which creates and removes the player's {@code egg} row.
 *
 * <p>Bind once at common init with {@link #init()}. If the species' egg item is not registered the
 * layer answers {@code false} and the pair gives a live birth instead, so nothing is lost.
 */
public final class EggLaying {

    private static final String EGG_SUFFIX = "_egg";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private EggLaying() {
    }

    /** Binds the egg layer into the breeding path. */
    public static void init() {
        BreedingHooks.bindEggLayer(EggLaying::layEgg);
    }

    // --- pure id rules (tested) ------------------------------------------------------------------

    /** The egg item path for a species path: {@code lumen_moth} -> {@code lumen_moth_egg}. */
    public static String eggPath(String speciesPath) {
        return speciesPath + EGG_SUFFIX;
    }

    /**
     * The species path an egg item path names, or {@code null} for anything that is not a creature egg
     * (spawn eggs included).
     */
    @Nullable
    public static String speciesPathOf(String eggItemPath) {
        if (eggItemPath == null || !eggItemPath.endsWith(EGG_SUFFIX) || eggItemPath.endsWith(SPAWN_EGG_SUFFIX)
                || eggItemPath.length() <= EGG_SUFFIX.length()) {
            return null;
        }
        return eggItemPath.substring(0, eggItemPath.length() - EGG_SUFFIX.length());
    }

    // --- world side ------------------------------------------------------------------------------

    /** The breeding seam: drops the egg between the parents. */
    static boolean layEgg(ServerLevel level, TameableCreature mother, TameableCreature father, TraitSet traits) {
        Identifier species = EntityType.getKey(mother.getType());
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(
                Identifier.fromNamespaceAndPath(species.getNamespace(), eggPath(species.getPath())));
        if (item.isEmpty() || item.get() == Items.AIR) {
            return false;
        }
        ItemStack egg = new ItemStack(item.get());
        new EggData(species.toString(), traits).write(egg);
        double x = (mother.getX() + father.getX()) * 0.5D;
        double y = Math.max(mother.getY(), father.getY()) + 0.25D;
        double z = (mother.getZ() + father.getZ()) * 0.5D;
        ItemEntity drop = new ItemEntity(level, x, y, z, egg);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        level.playSound(null, mother.blockPosition(), SoundEvents.TURTLE_LAY_EGG, SoundSource.NEUTRAL, 0.6F, 1.2F);
        return true;
    }

    /**
     * An egg stack's contents. A creature egg with no custom data (creative menu, a command, another
     * mod) gets its species from its item id and two fresh traits, so it still hatches.
     */
    static Optional<EggData> resolve(ItemStack stack, RandomSource random) {
        Optional<EggData> data = EggData.read(stack);
        if (data.isPresent() || stack.isEmpty()) {
            return data;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String species = speciesPathOf(id.getPath());
        if (species == null) {
            return Optional.empty();
        }
        return Optional.of(new EggData(id.getNamespace() + ":" + species, Inheritance.fresh(TraitPool.get(), random)));
    }
}
