package za.co.neroland.nerocreatures.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Incubator: holds one egg and hatches it (decision 003). Use an egg on it to put it in; use it with an
 * empty hand to see how long is left; sneak-use with an empty hand to take the egg back out. No menu: the
 * egg goes straight from the hand into the block, so there is nothing to duplicate. Faster with power
 * from an adjacent Core energy storage; works with none.
 */
public class IncubatorBlock extends HabitatBlock {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "incubator");

    public static final MapCodec<IncubatorBlock> CODEC = BlockCodecs.simple(IncubatorBlock::new);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    /** Core energy reached the egg on the last pulse (lamp green, a little brighter). */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public IncubatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    /** Sets {@link #POWERED} if it changed (client update only, no neighbour churn). */
    static void setPowered(Level level, BlockPos pos, BlockState state, boolean powered) {
        if (state.hasProperty(POWERED) && state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    /** Registration properties without the id (the integrator adds {@code .setId(key)}). */
    public static BlockBehaviour.Properties incubatorProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.COPPER)
                .requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(state -> state.hasProperty(POWERED) && state.getValue(POWERED) ? 7 : 4);
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<IncubatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean ticks() {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IncubatorBlockEntity(pos, state);
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    *///?}
        if (!stack.is(IncubatorBlockEntity.EGGS)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof IncubatorBlockEntity incubator) {
            incubator.insert(server, serverPlayer, stack);
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.ItemInteractionResult.SUCCESS;
        *///?}
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof IncubatorBlockEntity incubator) {
            if (player.isShiftKeyDown()) {
                incubator.takeOut(player);
            } else {
                incubator.status(player);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
