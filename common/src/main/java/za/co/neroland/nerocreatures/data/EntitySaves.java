package za.co.neroland.nerocreatures.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Saving an entity to a compound and loading it back, the same way on every MC line (1.21.6 moved
 * entity save/load onto {@code ValueOutput}/{@code ValueInput}). Used by the Capture Pod and by
 * erasure, which edits one vanilla key out of a live animal's data.
 */
public final class EntitySaves {

    private EntitySaves() {
    }

    /** The entity's own save data (no type id). */
    public static CompoundTag save(Entity entity, ServerLevel level) {
        //? if >=1.21.6 {
        net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput
                .createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
        //?} else {
        /*return entity.saveWithoutId(new CompoundTag());
        *///?}
    }

    /** Loads save data into an entity (a fresh one, or a live one being edited in place). */
    public static void load(Entity entity, CompoundTag data, ServerLevel level) {
        //? if >=1.21.6 {
        entity.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), data));
        //?} else {
        /*entity.load(data);
        *///?}
    }
}
