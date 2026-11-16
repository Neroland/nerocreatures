package za.co.neroland.nerocreatures.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.menu.MenuOpener;

/**
 * A habitat block with a five-slot stock (Feeding Trough food, Lure Beacon bait, Sonic Ward fuel), opened
 * in vanilla's {@code HopperMenu} through {@link MenuOpener} (no menu type of our own for
 * these). Hoppers can feed it too. The stock is items only, never player data; it drops when the block is
 * removed.
 */
public abstract class InventoryHabitatBlockEntity extends HabitatBlockEntity {

    public static final int SLOTS = 5;

    private static final String STOCK_KEY = "Stock";

    private static final double REACH_SQR = 8.0D * 8.0D;

    /** One non-empty stock slot as saved. */
    private record StockSlot(int slot, ItemStack item) {
        static final Codec<StockSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, SLOTS - 1).fieldOf("slot").forGetter(StockSlot::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(StockSlot::item)
        ).apply(instance, StockSlot::new));
    }

    protected final SimpleContainer stock = new SimpleContainer(SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            InventoryHabitatBlockEntity self = InventoryHabitatBlockEntity.this;
            return !self.isRemoved() && self.level != null
                    && self.level.getBlockEntity(self.worldPosition) == self
                    && player.distanceToSqr(self.worldPosition.getX() + 0.5D, self.worldPosition.getY() + 0.5D,
                            self.worldPosition.getZ() + 0.5D) <= REACH_SQR;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            InventoryHabitatBlockEntity.this.setChanged();
        }
    };

    protected InventoryHabitatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The menu title's translation key, e.g. {@code container.nerocreatures.feeding_trough}. */
    protected abstract String titleKey();

    /** Opens the stock for a player (server; a no-op on the client). */
    public void openStock(Player player) {
        MenuOpener.open(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new HopperMenu(id, inventory, this.stock),
                Component.translatable(this.titleKey())));
    }

    /** How many items in the stock match. */
    protected int count(Predicate<ItemStack> filter) {
        int total = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.stock.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes one matching item out of the stock; returns a one-item copy, or empty when none matched. */
    protected ItemStack consumeOne(Predicate<ItemStack> filter) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.stock.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                ItemStack taken = stack.copyWithCount(1);
                this.stock.removeItem(i, 1);
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The first stock item that matches, without taking it. */
    protected ItemStack peek(Predicate<ItemStack> filter) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.stock.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void onBlockRemoved(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, this.stock);
    }

    // --- persistence ------------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        List<StockSlot> slots = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.stock.getItem(i);
            if (!stack.isEmpty()) {
                slots.add(new StockSlot(i, stack.copy()));
            }
        }
        output.store(STOCK_KEY, StockSlot.CODEC.listOf(), slots);
        this.saveExtra(output);
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.stock.clearContent();
        for (StockSlot slot : input.read(STOCK_KEY, StockSlot.CODEC.listOf()).orElse(List.of())) {
            this.stock.setItem(slot.slot(), slot.item());
        }
        this.loadExtra(input);
    }

    /** Subclass state beside the stock. */
    protected void saveExtra(ValueOutput output) {
    }

    protected void loadExtra(ValueInput input) {
    }
}
