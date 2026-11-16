package za.co.neroland.nerocreatures.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Feeding Trough: holds food in a five-slot stock and, on a cadence, feeds two husbanded adults of one
 * species nearby so they breed (rules: {@link TroughRules}).
 */
public class FeedingTroughBlock extends HabitatBlock {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "feeding_trough");

    public static final MapCodec<FeedingTroughBlock> CODEC = BlockCodecs.simple(FeedingTroughBlock::new);

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 2.0D, 16.0D, 8.0D, 14.0D);

    public FeedingTroughBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Registration properties without the id (registration adds {@code .setId(key)}). */
    public static BlockBehaviour.Properties troughProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD)
                .noOcclusion();
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<FeedingTroughBlock> codec() {
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
        return new FeedingTroughBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FeedingTroughBlockEntity trough) {
            trough.openStock(player);
        }
        return InteractionResult.SUCCESS;
    }
}
