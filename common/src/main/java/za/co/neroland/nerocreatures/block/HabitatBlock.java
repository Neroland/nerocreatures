package za.co.neroland.nerocreatures.block;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Base of the habitat blocks that have a block entity: model rendering, a server-only ticker for the
 * blocks that work on a cadence, and the pre-1.21.5 removal hook. Placed by players, so nothing here goes
 * through {@code CreatureGriefing} (decision 006).
 */
public abstract class HabitatBlock extends BaseEntityBlock {

    protected HabitatBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Whether this block's entity runs {@link HabitatBlockEntity#serverTick}. */
    protected boolean ticks() {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || !this.ticks()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof HabitatBlockEntity habitat && tickLevel instanceof ServerLevel server) {
                habitat.serverTick(server, pos, tickState);
            }
        };
    }

    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState state, Level level, net.minecraft.core.BlockPos pos, BlockState newState,
            boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof HabitatBlockEntity habitat) {
            habitat.onBlockRemoved(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
    *///?}

    /** Action-bar feedback to the one player who used the block. Never chat, never broadcast. */
    static void tell(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    /** Hands a stack to a player, dropping it at their feet when the inventory is full. */
    static void give(Player player, net.minecraft.world.item.ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            net.minecraft.world.Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        }
    }
}
