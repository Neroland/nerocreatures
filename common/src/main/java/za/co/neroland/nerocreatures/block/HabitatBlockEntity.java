package za.co.neroland.nerocreatures.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Base of the habitat block entities. Three things every one of them needs:
 *
 * <ul>
 *   <li><b>Type lookup by id</b>, like the Apex Altar: the block entity never names a registry entry, so
 *       this package compiles without the registries.</li>
 *   <li><b>One removal hook</b>, {@link #onBlockRemoved}, run once when the block is broken or replaced
 *       (never on chunk unload). 1.21.5+ calls it from {@code preRemoveSideEffects} (the only safe place
 *       for world changes on 26.x); older lines from the block's {@code onRemove}.</li>
 *   <li><b>Opt-in client sync</b> for the display blocks ({@link #syncsToClient()}); the inventory blocks
 *       keep their contents server-side.</li>
 * </ul>
 */
public abstract class HabitatBlockEntity extends BlockEntity {

    protected HabitatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The registered type for {@code id}; fails loudly if it has not been registered. */
    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> BlockEntityType<T> lookup(Identifier id) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
        if (type == null) {
            throw new IllegalStateException(id + " block entity type is not registered");
        }
        return (BlockEntityType<T>) type;
    }

    /** Server tick, called by {@link HabitatBlock}'s ticker when the block declares one. */
    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
    }

    /** The block was broken or replaced: drop contents, release rows and fields. Server only, once. */
    protected void onBlockRemoved(ServerLevel level, BlockPos pos) {
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            this.onBlockRemoved(server, pos);
        }
    }
    //?}

    // --- client sync (display blocks only) -------------------------------------------------

    /** Whether clients get this block entity's data (only what {@link #getUpdateTag} returns). */
    protected boolean syncsToClient() {
        return false;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return this.syncsToClient() ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.syncsToClient() ? this.saveCustomOnly(registries) : new CompoundTag();
    }

    // --- gallery hooks (the Menagerie's working rows) ------------------------------------------

    /**
     * Gallery {@code clear}: empty this block so removing it drops nothing and releases nothing a
     * player would miss. Default: nothing to empty.
     */
    public void emptyForGallery() {
    }

    /** Gallery build: show {@code stack} (a trophy, or a filled Capture Pod's creature). Default: no-op. */
    public void showForGallery(net.minecraft.world.item.ItemStack stack) {
    }

    /** Saves and, for a syncing block, pushes the new data to watching clients. */
    protected void changed() {
        this.setChanged();
        if (this.syncsToClient() && this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
