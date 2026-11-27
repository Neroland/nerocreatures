package za.co.neroland.nerocreatures.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Terrarium: a glass case that keeps and shows one small captured creature. A filled Capture Pod used on
 * an empty Terrarium moves its creature in; an empty pod used on a full one takes it back out
 * ({@link CapturedCreature} is the shared format). A full Terrarium cannot be mined in survival, so a
 * creature is never lost to a broken case: empty it with a pod first.
 */
public class TerrariumBlock extends HabitatBlock {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "terrarium");

    public static final MapCodec<TerrariumBlock> CODEC = BlockCodecs.simple(TerrariumBlock::new);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public TerrariumBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Registration properties without the id: reinforced glass, blast-proof so a capture is never lost. */
    public static BlockBehaviour.Properties terrariumProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5F, 1200.0F).sound(SoundType.GLASS)
                .noOcclusion();
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<TerrariumBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerrariumBlockEntity(pos, state);
    }

    /** An occupied Terrarium cannot be mined in survival (creative still can). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium
                && terrarium.occupied()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
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
        if (!stack.is(TerrariumBlockEntity.CAPTURE_PODS)) {
            //? if >=1.20.5 {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
            //?} else {
            /*return InteractionResult.PASS;
            *///?}
        }
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium) {
            terrarium.usePod(player, stack);
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
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TerrariumBlockEntity terrarium) {
            terrarium.describe(player);
        }
        return InteractionResult.SUCCESS;
    }
}
