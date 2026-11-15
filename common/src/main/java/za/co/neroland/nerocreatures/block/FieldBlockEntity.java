package za.co.neroland.nerocreatures.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared engine of the Lure Beacon and the Sonic Ward: burn one stock item for a configured number of
 * ticks, keep a {@link HabitatFields} field live while burning, show it with the block's {@code lit}
 * state. Every pulse is {@value #PULSE_TICKS} ticks; a field outlives a pulse by a few ticks only, so a
 * removed or unloaded block stops counting almost at once.
 */
public abstract class FieldBlockEntity extends InventoryHabitatBlockEntity {

    public static final int PULSE_TICKS = 20;

    private int burnLeft;
    private String species = "";
    private int pulse;

    protected FieldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract FieldRules.Kind kind();

    /** Ticks one item burns for; 0 or less switches this block type off. */
    protected abstract int ticksPerItem();

    /**
     * The species the item lures (a creature id path), {@code ""} for a ward fuel, or {@code null} when
     * the item is not this block's fuel.
     */
    protected abstract String fuelFor(ItemStack stack);

    /** The field's weight multiplier (lure only). */
    protected double multiplier() {
        return 1.0D;
    }

    /** Extra work on a burning pulse (the ward's repel sweep). */
    protected void onBurningPulse(ServerLevel level, BlockPos pos) {
    }

    public boolean burning() {
        return this.burnLeft > 0;
    }

    public String species() {
        return this.species;
    }

    @Override
    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (++this.pulse < PULSE_TICKS) {
            return;
        }
        this.pulse = 0;
        int perItem = this.ticksPerItem();
        if (perItem <= 0) {
            this.burnLeft = 0;
        } else {
            this.burnLeft = FieldRules.burnAfterPulse(this.burnLeft, PULSE_TICKS);
            if (this.burnLeft <= 0) {
                this.refuel(perItem);
            }
        }
        if (this.burnLeft > 0) {
            HabitatFields.refresh(level, pos, this.kind(), this.species, this.multiplier());
            this.onBurningPulse(level, pos);
        } else {
            HabitatFields.clear(level, pos);
        }
        setLit(level, pos, state, this.burnLeft > 0);
        this.setChanged();
    }

    private void refuel(int perItem) {
        ItemStack fuel = this.peek(stack -> this.fuelFor(stack) != null);
        if (fuel.isEmpty()) {
            return;
        }
        String lured = this.fuelFor(fuel);
        if (!this.consumeOne(stack -> this.fuelFor(stack) != null).isEmpty()) {
            this.species = lured == null ? "" : lured;
            this.burnLeft = perItem;
        }
    }

    private static void setLit(ServerLevel level, BlockPos pos, BlockState state, boolean lit) {
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void onBlockRemoved(ServerLevel level, BlockPos pos) {
        HabitatFields.clear(level, pos);
        super.onBlockRemoved(level, pos);
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        output.putInt("Burn", this.burnLeft);
        output.putString("Species", this.species);
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.burnLeft = Math.max(0, input.getIntOr("Burn", 0));
        this.species = input.getStringOr("Species", "");
    }
}
