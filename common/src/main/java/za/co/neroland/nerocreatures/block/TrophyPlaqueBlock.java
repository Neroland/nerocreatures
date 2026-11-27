package za.co.neroland.nerocreatures.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Trophy Plaque: a wall board that shows one creature or boss trophy ({@code #nerocreatures:trophies}).
 * Use a trophy on an empty plaque to hang one; use it with an empty hand to take it down
 * ({@link DisplayRules}: exactly one item, never a stack, never a swap).
 */
public class TrophyPlaqueBlock extends HabitatBlock {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "trophy_plaque");

    public static final MapCodec<TrophyPlaqueBlock> CODEC = BlockCodecs.simple(TrophyPlaqueBlock::new);

    /** Faces out of the wall; the board sits against the opposite side of the block. */
    private static final VoxelShape NORTH = Block.box(1.0D, 1.0D, 15.0D, 15.0D, 15.0D, 16.0D);
    private static final VoxelShape SOUTH = Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 1.0D);
    private static final VoxelShape WEST = Block.box(15.0D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D);
    private static final VoxelShape EAST = Block.box(0.0D, 1.0D, 1.0D, 1.0D, 15.0D, 15.0D);

    public TrophyPlaqueBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,
                Direction.NORTH));
    }

    /** Registration properties without the id (registration adds {@code .setId(key)}). */
    public static BlockBehaviour.Properties plaqueProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F, 3.0F).sound(SoundType.WOOD)
                .noOcclusion();
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<TrophyPlaqueBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrophyPlaqueBlockEntity(pos, state);
    }

    //? if <1.20.5 {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = useItemOnLegacy(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        return result == InteractionResult.PASS ? useWithoutItem(state, level, pos, player, hit) : result;
    }
    *///?}

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
    //?} else if >=1.20.5 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    *///?} else {
    /*private InteractionResult useItemOnLegacy(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    *///?}
        if (!stack.is(TrophyPlaqueBlockEntity.TROPHIES)) {
            //? if >=1.20.5 {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
            //?} else {
            /*return InteractionResult.PASS;
            *///?}
        }
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TrophyPlaqueBlockEntity plaque) {
            plaque.use(player, stack);
        }
        //? if >=1.20.5 && <1.21.2 {
        /*return net.minecraft.world.ItemInteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.SUCCESS;
        //?}
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
    //?} else {
    /*private InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
    *///?}
        // Reached with a non-trophy item in hand too (vanilla falls through): only a truly empty hand takes
        // the trophy down, so a held block or tool never strips the plaque.
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TrophyPlaqueBlockEntity plaque) {
            plaque.use(player, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }
}
