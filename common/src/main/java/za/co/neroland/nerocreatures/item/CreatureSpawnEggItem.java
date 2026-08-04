package za.co.neroland.nerocreatures.item;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A spawn egg for a NeroCreature.
 *
 * <p>It is deliberately <b>not</b> a vanilla {@code SpawnEggItem}: that class binds its
 * {@link EntityType} in its constructor, and on the deferred-register loaders items are registered
 * before entity types — so the binding would resolve to nothing. This resolves the type
 * <em>lazily</em>, through a {@link Supplier}, at the moment the egg is used, which is correct on
 * all three loaders. (The same reason Nerospace carries its own egg item.)
 *
 * <p>Behaviour mirrors the vanilla egg: right-click a block face, the mob appears on the far side
 * of it, and the stack shrinks unless the player is in creative.
 *
 * <p>Server-authoritative: the spawn happens only on a {@link ServerLevel}; the client just reports
 * success so the swing animation plays.
 */
public class CreatureSpawnEggItem extends Item {

    private final Supplier<? extends EntityType<? extends Mob>> type;

    public CreatureSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        // Spawn inside the clicked block when it has no collision (grass, air pockets), otherwise on
        // the face that was clicked — exactly what a player expects from a vanilla egg.
        BlockPos spawnPos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(face);
        Player player = context.getPlayer();

        Mob mob = this.type.get().spawn(level, stack, player, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE,
                true, !clicked.equals(spawnPos) && face == Direction.UP);
        if (mob != null && (player == null || !player.getAbilities().instabuild)) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
