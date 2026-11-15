package za.co.neroland.nerocreatures.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;

/**
 * {@code drifter_spores}: a bottle of Spore Drifter spores. Used on a crop, sapling or anything else
 * vanilla bone meal grows, it applies up to {@value #STRENGTH} bone-meal growths (twice bone meal,
 * because a drifter refills only every five minutes), then the empty glass bottle comes back.
 *
 * <p>Player use: the player's own permission at the clicked block is checked first
 * ({@code mayUseItemAt}), exactly like vanilla bone meal. Server-authoritative; nothing is logged.
 */
public class DrifterSporesItem extends Item {

    /** Bone-meal growths per bottle. */
    public static final int STRENGTH = 2;

    public DrifterSporesItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof BonemealableBlock)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        int grown = 0;
        for (int i = 0; i < STRENGTH; i++) {
            if (!BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), server, pos)) {
                break;
            }
            grown++;
        }
        if (grown == 0) {
            return InteractionResult.PASS;
        }
        server.levelEvent(1505, pos, 15); // vanilla bone-meal particles
        server.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (player == null || !player.getInventory().add(bottle)) {
                server.addFreshEntity(new ItemEntity(server, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, bottle));
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
