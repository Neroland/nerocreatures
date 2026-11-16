package za.co.neroland.nerocreatures.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.item.SigilItem;

/**
 * The Apex Altar: use a boss sigil on it to summon that boss as a contribution-tracked fight.
 *
 * <ul>
 *   <li>No menu and no inventory: the sigil goes straight from the hand into the summon, so there is
 *       nothing stored in the block to duplicate on break, relog or a double click.</li>
 *   <li>Per-altar cooldown (config {@code apexAltarCooldownTicks}), kept on the block entity, never on a
 *       player. Refuses while the fight it started is live.</li>
 *   <li>The {@link #SIGIL} block-state property is the altar's lock and its glow: anything but
 *       {@code none} means "fight live or cooling down", lights the slot in that boss's habitat accent,
 *       syncs to clients for free, and makes the block unbreakable in survival until it clears, so
 *       breaking and re-placing the altar cannot skip the cooldown.</li>
 * </ul>
 */
public class ApexAltarBlock extends BaseEntityBlock {

    /** The registry id this block is registered under. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "apex_altar");

    /** Which habitat accent the sigil slot glows in; {@code none} = idle and unlocked. */
    public enum Sigil implements StringRepresentable {
        NONE("none"), CINDARA("cindara"), GLACIRA("glacira"), GREENXERTZ("greenxertz"), NYXARA("nyxara");

        private final String name;

        Sigil(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** The value for a saved name; unknown names read as {@link #NONE}. */
        public static Sigil byName(String name) {
            for (Sigil sigil : values()) {
                if (sigil.name.equals(name)) {
                    return sigil;
                }
            }
            return NONE;
        }
    }

    public static final EnumProperty<Sigil> SIGIL = EnumProperty.create("sigil", Sigil.class);

    public static final MapCodec<ApexAltarBlock> CODEC = BlockCodecs.simple(ApexAltarBlock::new);

    public ApexAltarBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SIGIL, Sigil.NONE));
    }

    /**
     * Registration properties without the id (registration adds {@code .setId(key)}): obsidian-hard,
     * blast-proof, needs a pickaxe, glows faintly while locked.
     */
    public static BlockBehaviour.Properties altarProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(25.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> state.getValue(SIGIL) == Sigil.NONE ? 0 : 7);
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<ApexAltarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGIL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ApexAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ApexAltarBlockEntity altar && tickLevel instanceof ServerLevel server) {
                altar.serverTick(server, pos, tickState);
            }
        };
    }

    /** Locked altars cannot be mined in survival (creative still can). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (state.getValue(SIGIL) != Sigil.NONE && !player.getAbilities().instabuild) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
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
        if (!(stack.getItem() instanceof SigilItem sigil)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ApexAltarBlockEntity altar) {
            altar.trySummon(server, pos, serverPlayer, stack, sigil.kind());
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.ItemInteractionResult.SUCCESS;
        *///?}
    }
}
