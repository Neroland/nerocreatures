package za.co.neroland.nerocreatures.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocreatures.data.EntitySaves;

/**
 * Saving a creature to a compound and loading it back, the same way on every MC line (see
 * {@link EntitySaves}). The pod passes the saved compound
 * through {@code CapturedCreature.strip} before it is stored; loading leaves the fresh entity's own
 * UUID in place because the stripped data carries none.
 */
final class CreatureCapture {

    private CreatureCapture() {
    }

    /** The entity's own save data (no type id). */
    static CompoundTag save(Entity entity, ServerLevel level) {
        return EntitySaves.save(entity, level);
    }

    /** Loads save data into a freshly created entity. */
    static void load(Entity entity, CompoundTag data, ServerLevel level) {
        EntitySaves.load(entity, data, level);
    }
}
