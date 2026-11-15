package za.co.neroland.nerocreatures.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/** The Trophy Plaque's one shown item. Items only; dropped when the plaque is broken. */
public class TrophyPlaqueBlockEntity extends HabitatBlockEntity implements HabitatDisplay {

    public static final Identifier ID = TrophyPlaqueBlock.ID;

    /** What a plaque shows: {@code #nerocreatures:trophies}. */
    public static final TagKey<Item> TROPHIES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "trophies"));

    private ItemStack trophy = ItemStack.EMPTY;

    public TrophyPlaqueBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);
    }

    public static BlockEntityType<TrophyPlaqueBlockEntity> type() {
        return lookup(ID);
    }

    /** One use, server thread only: hang one trophy, or take it down with an empty hand. */
    void use(Player player, ItemStack held) {
        DisplayRules.Action action = DisplayRules.decide(!this.trophy.isEmpty(), held.isEmpty(),
                !held.isEmpty() && held.is(TROPHIES));
        switch (action) {
            case INSERT -> {
                this.trophy = held.copyWithCount(DisplayRules.insertCount(held.getCount()));
                held.consume(1, player);
                this.playSound(true);
                this.changed();
            }
            case TAKE -> {
                ItemStack out = this.trophy;
                this.trophy = ItemStack.EMPTY;
                HabitatBlock.give(player, out);
                this.playSound(false);
                this.changed();
            }
            case REFUSE_FULL -> HabitatBlock.tell(player,
                    Component.translatable("message.nerocreatures.trophy_plaque.full"));
            default -> {
            }
        }
    }

    private void playSound(boolean added) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition,
                    added ? SoundEvents.ITEM_FRAME_ADD_ITEM : SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS,
                    1.0F, 1.0F);
        }
    }

    @Override
    protected void onBlockRemoved(ServerLevel level, BlockPos pos) {
        if (!this.trophy.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, this.trophy);
            this.trophy = ItemStack.EMPTY;
        }
    }

    // --- display ---------------------------------------------------------------------------

    @Override
    protected boolean syncsToClient() {
        return true;
    }

    @Override
    public ItemStack displayStack() {
        return this.trophy;
    }

    @Override
    public boolean displaySpins() {
        return false;
    }

    @Override
    public float displayYaw() {
        BlockState state = this.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        return 180.0F - facing.toYRot();
    }

    @Override
    public float displayDepth() {
        return 0.4F;
    }

    @Override
    public float displayScale() {
        return 0.6F;
    }

    // --- persistence -----------------------------------------------------------------------

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
        if (!this.trophy.isEmpty()) {
            output.store("Trophy", ItemStack.CODEC, this.trophy);
        }
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
        this.trophy = input.read("Trophy", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
