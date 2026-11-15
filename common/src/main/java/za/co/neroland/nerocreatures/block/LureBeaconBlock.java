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
 * Lure Beacon: burns one bait item (a species' breeding food) to raise that species' spawn weight within {@value FieldRules#LURE_RADIUS} blocks; rules in {@link FieldRules}.
 */
public class LureBeaconBlock extends FieldBlock {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "lure_beacon");

    public static final MapCodec<LureBeaconBlock> CODEC = BlockCodecs.simple(LureBeaconBlock::new);

    public LureBeaconBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Registration properties without the id (the integrator adds {@code .setId(key)}). */
    public static BlockBehaviour.Properties fieldProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).sound(SoundType.LANTERN)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> {
                    boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
                    return lit ? 10 : 2;
                });
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<LureBeaconBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LureBeaconBlockEntity(pos, state);
    }
}
