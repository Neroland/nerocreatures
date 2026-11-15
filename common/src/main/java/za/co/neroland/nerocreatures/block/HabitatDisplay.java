package za.co.neroland.nerocreatures.block;

import net.minecraft.world.item.ItemStack;

/**
 * What a habitat block shows above or on itself (Incubator egg, Terrarium creature, Trophy Plaque
 * trophy), read by the one shared block-entity renderer. Client-side values from synced data only.
 */
public interface HabitatDisplay {

    /** The stack to draw; empty draws nothing (and the renderer culls the block entirely). */
    ItemStack displayStack();

    /** Height of the item's centre above the block's base, in blocks. */
    default float displayHeight() {
        return 0.5F;
    }

    default float displayScale() {
        return 0.5F;
    }

    /** Whether the item turns slowly; otherwise it faces {@link #displayYaw()}. */
    default boolean displaySpins() {
        return true;
    }

    /** Fixed yaw in degrees when not spinning. */
    default float displayYaw() {
        return 0.0F;
    }

    /** Offset along the facing after the yaw (a wall plaque pushes its item against the board). */
    default float displayDepth() {
        return 0.0F;
    }
}
