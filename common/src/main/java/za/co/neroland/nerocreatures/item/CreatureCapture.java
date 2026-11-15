package za.co.neroland.nerocreatures.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Saving a creature to a compound and loading it back, the same way on every MC line (1.21.6 moved
 * entity save/load onto {@code ValueOutput}/{@code ValueInput}). The pod passes the saved compound
 * through {@code CapturedCreature.strip} before it is stored; loading leaves the fresh entity's own
 * UUID in place because the stripped data carries none.
 */
final class CreatureCapture {

    private CreatureCapture() {
    }

    /** The entity's own save data (no type id). */
    static CompoundTag save(Entity entity, ServerLevel level) {
        //? if >=1.21.6 {
        net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput
                .createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
        //?} else {
        /*return entity.saveWithoutId(new CompoundTag());
        *///?}
    }

    /** Loads save data into a freshly created entity. */
    static void load(Entity entity, CompoundTag data, ServerLevel level) {
        //? if >=1.21.6 {
        entity.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), data));
        //?} else {
        /*entity.load(data);
        *///?}
    }
}
