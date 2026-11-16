package za.co.neroland.nerocreatures.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Sonic Ward: burns one fuel item at a time to keep hostile NeroCreatures from spawning in, or walking into, a {@value FieldRules#WARD_RADIUS}-block radius; rules in {@link FieldRules}.
 */
public class SonicWardBlock extends FieldBlock {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "sonic_ward");

    public static final MapCodec<SonicWardBlock> CODEC = BlockCodecs.simple(SonicWardBlock::new);

    public SonicWardBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Registration properties without the id (registration adds {@code .setId(key)}). */
    public static BlockBehaviour.Properties fieldProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.0F, 6.0F).sound(SoundType.AMETHYST)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> {
                    boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
                    return lit ? 6 : 0;
                });
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<SonicWardBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SonicWardBlockEntity(pos, state);
    }
}
