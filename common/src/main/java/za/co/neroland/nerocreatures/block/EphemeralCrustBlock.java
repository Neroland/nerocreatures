package za.co.neroland.nerocreatures.block;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.item.ItemStack;

/**
 * {@code nerocreatures:ephemeral_crust}: the one temporary block creatures and boss
 * arenas place. It heals the world by itself, so nothing has to track it.
 *
 * <ul>
 *   <li>{@link #VARIANT} {@code slag|rime|prism}: Magma Wader slag, Rime Matriarch ice pillars,
 *       Facet Colossus refracting crystals. Look only.</li>
 *   <li>{@link #RESTORE} {@code air|lava|water}: what it was before. A scheduled tick turns it
 *       back. {@link EphemeralCrustRules} lists the safety nets (random-tick fallback, fluid
 *       refill after a break or an explosion).</li>
 *   <li>No item, no loot ({@link #crustProperties()} sets {@code noLootTable}), no block entity, never
 *       pushed by pistons, never pick-blockable (it has no item).</li>
 * </ul>
 *
 * <p><b>Gating.</b> {@link #place} does not check anything: the caller must already have passed
 * {@code CreatureGriefing.mayAlter(..)} with its own key, as the Magma Wader does with
 * {@code magmaWaderSlag}. Boss arenas use {@code bossArenaBlocks}.
 *
 * <p>Registered in {@code ModBlocks}; this class owns the logic.
 */
public class EphemeralCrustBlock extends Block {

    /** The look. */
    public enum Variant implements StringRepresentable {
        SLAG("slag"),
        RIME("rime"),
        PRISM("prism");

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /** What the block turns back into. */
    public enum Restore implements StringRepresentable {
        AIR("air"),
        LAVA("lava"),
        WATER("water");

        private final String name;

        Restore(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** Whether this restore target is a fluid source that must never be lost. */
        public boolean isFluid() {
            return this != AIR;
        }

        /** The block state this restore target places. */
        public BlockState state() {
            return switch (this) {
                case AIR -> Blocks.AIR.defaultBlockState();
                case LAVA -> Blocks.LAVA.defaultBlockState();
                case WATER -> Blocks.WATER.defaultBlockState();
            };
        }
    }

    /** The registry id this block is registered under. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("nerocreatures", "ephemeral_crust");

    @Nullable
    private static volatile EphemeralCrustBlock registered;

    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);
    public static final EnumProperty<Restore> RESTORE = EnumProperty.create("restore", Restore.class);

    public EphemeralCrustBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(VARIANT, Variant.SLAG)
                .setValue(RESTORE, Restore.AIR));
    }

    /**
     * The registration properties, without the registry id (registration adds {@code .setId(key)}
     * on 1.21.2+ through its usual helper). Basalt-like, dim glow for slag, no loot, immovable,
     * random ticks on for the lost-tick safety net.
     */
    public static BlockBehaviour.Properties crustProperties() {
        //? if >=26.3 {
        PushReaction immovable = PushReaction.IMMOVEABLE;
        //?} else {
        /*PushReaction immovable = PushReaction.BLOCK;
        *///?}
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(1.25F, 4.2F)
                .sound(SoundType.BASALT)
                .lightLevel(state -> state.getValue(VARIANT) == Variant.SLAG ? 3 : 0)
                .pushReaction(immovable)
                .randomTicks()
                .noLootTable();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, RESTORE);
    }

    /**
     * The registered block, looked up by {@link #ID} once and cached; {@code null} before the block is
     * registered, in which case callers take their no-block fallback. Keeps creature code free of a
     * registry reference.
     */
    @Nullable
    public static EphemeralCrustBlock registered() {
        EphemeralCrustBlock cached = registered;
        if (cached != null) {
            return cached;
        }
        if (BuiltInRegistries.BLOCK.containsKey(ID)
                && BuiltInRegistries.BLOCK.getValue(ID) instanceof EphemeralCrustBlock block) {
            registered = block;
            return block;
        }
        return null;
    }

    /** The state for a variant and restore target. */
    public BlockState stateFor(Variant variant, Restore restore) {
        return this.defaultBlockState().setValue(VARIANT, variant).setValue(RESTORE, restore);
    }

    /**
     * Places a crust and schedules its decay. The caller has already passed
     * {@code CreatureGriefing.mayAlter} for {@code pos} and {@code state}.
     *
     * @return whether the block was set
     */
    public boolean place(ServerLevel level, BlockPos pos, BlockState state, int decayTicks) {
        if (!state.is(this) || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        level.scheduleTick(pos, this, EphemeralCrustRules.clampDecay(decayTicks));
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.revert(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (EphemeralCrustRules.revertOnRandomTick(level.getBlockTicks().hasScheduledTick(pos, this))) {
            this.revert(state, level, pos);
        }
    }

    /** Turns the crust back into what it was, with a hiss and a puff. */
    private void revert(BlockState state, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        level.setBlock(pos, state.getValue(RESTORE).state(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.8F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                2, 0.25D, 0.1D, 0.25D, 0.0D);
    }

    /** Broken by a player: a crust that stood in a fluid source puts the source back. */
    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        super.destroy(level, pos, state);
        refill(level, pos, state);
    }

    /** Blown up: same as a break. The state is captured before vanilla clears the cell. */
    //? if >=1.21.2 {
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> dropConsumer) {
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
        refill(level, pos, state);
    }
    //?} else {
    /*@Override
    protected void onExplosionHit(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
        refill(level, pos, state);
    }
    *///?}

    private static void refill(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !state.hasProperty(RESTORE)) {
            return;
        }
        Restore restore = state.getValue(RESTORE);
        if (EphemeralCrustRules.refillAfterBreak(restore.isFluid(), level.getBlockState(pos).isAir())) {
            level.setBlock(pos, restore.state(), Block.UPDATE_ALL);
        }
    }
}
