package za.co.neroland.nerocreatures.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//?}

final class CreatureStackData {
    private CreatureStackData() {
    }

    static @Nullable CompoundTag copy(ItemStack stack) {
        //? if >=1.20.5 {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.copy();
        *///?}
    }

    static void write(ItemStack stack, CompoundTag tag) {
        //? if >=1.20.5 {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        //?} else {
        /*stack.setTag(tag.isEmpty() ? null : tag);
        *///?}
    }
}
