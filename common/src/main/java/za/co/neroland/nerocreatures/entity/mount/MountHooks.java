package za.co.neroland.nerocreatures.entity.mount;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;

/**
 * Config reads and the one seam {@link MountCreature} needs from outside its package: the
 * {@code creature_saddle} item, which is registered elsewhere. Until it is bound, no item saddles a
 * mount (and nothing is dropped as a saddle), so the base is inert rather than broken:
 *
 * <pre>{@code
 * MountHooks.bindSaddle(stack -> stack.is(ModItems.CREATURE_SADDLE.get()),
 *         () -> new ItemStack(ModItems.CREATURE_SADDLE.get()));
 * }</pre>
 *
 * <p>All config values are read on the server only; who may ride reaches the client through a synced
 * flag on the mount, never through client config.
 */
public final class MountHooks {

    private static volatile Predicate<ItemStack> isSaddle = stack -> false;
    private static volatile Supplier<ItemStack> saddleStack = () -> ItemStack.EMPTY;

    private MountHooks() {
    }

    public static void bindSaddle(Predicate<ItemStack> saddlePredicate, Supplier<ItemStack> saddleStackSupplier) {
        isSaddle = Objects.requireNonNull(saddlePredicate);
        saddleStack = Objects.requireNonNull(saddleStackSupplier);
    }

    /** {@code mountOwnerOnly} (default true). */
    public static boolean ownerOnly() {
        return NeroCreaturesConfig.MOUNT_OWNER_ONLY.get();
    }

    /** {@code mountStaminaRegenTicks} (default 10): ticks per stamina point regained. */
    public static int staminaRegenTicks() {
        return Math.max(1, NeroCreaturesConfig.MOUNT_STAMINA_REGEN_TICKS.get());
    }

    /** {@code mountAbilityCost} (default 35) before Thrifty. */
    public static int abilityCost() {
        return Math.max(0, Math.min(MountCreature.MAX_STAMINA, NeroCreaturesConfig.MOUNT_ABILITY_COST.get()));
    }

    public static boolean isSaddle(ItemStack stack) {
        return !stack.isEmpty() && isSaddle.test(stack);
    }

    /** A fresh saddle stack to drop on unsaddle, death or release; empty until the item is bound. */
    public static ItemStack saddleStack() {
        ItemStack stack = saddleStack.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
