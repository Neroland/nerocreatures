package za.co.neroland.nerocreatures.block;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * Nest: a home point for a pet or mount (no block entity; the creature keeps its own
 * nest, see {@link NestHome}). Use with an empty hand to home your nearest own creature within
 * {@value NestRules#BIND_REACH} blocks of the nest; sneak-use to un-home all of yours homed here.
 * Only the user's own creatures are ever considered, checked against the ownership store.
 */
public class NestBlock extends Block {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "nest");

    public static final MapCodec<NestBlock> CODEC = BlockCodecs.simple(NestBlock::new);

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    /** How far the un-home sweep looks. */
    private static final double UNBIND_REACH = 32.0D;

    public NestBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Registration properties without the id (registration adds {@code .setId(key)}). */
    public static BlockBehaviour.Properties nestProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.6F).sound(SoundType.GRASS)
                .noOcclusion();
    }

    // No @Override: 26.3 removed Block.codec(); older lines still call it.
    protected MapCodec<NestBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                unbind(server, pos, player);
            } else {
                bind(server, pos, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void bind(ServerLevel level, BlockPos pos, Player player) {
        UUID user = player.getUUID();
        CreatureOwnershipState store = CreatureOwnershipState.get(level.getServer());
        NestRules.Home home = NestHome.homeAt(level, pos);
        List<TameableCreature> mine = level.getEntitiesOfClass(TameableCreature.class,
                new AABB(pos).inflate(NestRules.BIND_REACH),
                creature -> creature.isAlive() && NestRules.mayBind(user.equals(creature.ownerId()),
                        store.contains(user, creature.getUUID()))
                        && !home.equals(NestHome.home(creature).orElse(null)));
        if (mine.isEmpty()) {
            HabitatBlock.tell(player, Component.translatable("message.nerocreatures.nest.none"));
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        TameableCreature chosen = mine.stream()
                .min(Comparator.comparingDouble(creature -> creature.distanceToSqr(cx, cy, cz)))
                .orElseThrow();
        NestHome.bind(chosen, home);
        HabitatBlock.tell(player, Component.translatable("message.nerocreatures.nest.bound", chosen.getDisplayName()));
    }

    private static void unbind(ServerLevel level, BlockPos pos, Player player) {
        UUID user = player.getUUID();
        NestRules.Home home = NestHome.homeAt(level, pos);
        List<TameableCreature> mine = level.getEntitiesOfClass(TameableCreature.class,
                new AABB(pos).inflate(UNBIND_REACH),
                creature -> user.equals(creature.ownerId()) && home.equals(NestHome.home(creature).orElse(null)));
        for (TameableCreature creature : mine) {
            NestHome.clear(creature);
        }
        HabitatBlock.tell(player, Component.translatable("message.nerocreatures.nest.cleared", mine.size()));
    }
}
