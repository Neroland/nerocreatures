package za.co.neroland.nerocreatures.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.bestiary.BestiaryClientHooks;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules;
import za.co.neroland.nerocreatures.bestiary.ScanTracker;

/**
 * The Field Scanner. Hold Use on a creature within 8 blocks for two
 * seconds to scan it; Sneak + Use opens the Bestiary on the client. Stack size 1, no durability.
 * Every scan result is decided on the server ({@link ScanTracker}); the client only draws the bar.
 */
public class FieldScannerItem extends Item {

    /** Effectively "as long as the button is held". */
    public static final int USE_DURATION = 72000;

    public FieldScannerItem(Item.Properties properties) {
        super(properties);
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    *///?}
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                BestiaryClientHooks.openScreen.run();
                //? if >=1.21.2 {
                return InteractionResult.SUCCESS;
                //?} else {
                /*return net.minecraft.world.InteractionResultHolder.success(stack);
                *///?}
            }
            //? if >=1.21.2 {
            return InteractionResult.PASS;
            //?} else {
            /*return net.minecraft.world.InteractionResultHolder.pass(stack);
            *///?}
        }
        player.startUsingItem(hand);
        //? if >=1.21.2 {
        return InteractionResult.CONSUME;
        //?} else {
        /*return net.minecraft.world.InteractionResultHolder.consume(stack);
        *///?}
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    //? if >=1.21.2 {
    @Override
    public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.ItemUseAnimation.NONE;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.NONE;
    }
    *///?}

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && ScanTracker.tick(player)) {
            //? if >=1.21.2 {
            player.getCooldowns().addCooldown(stack, BestiaryRules.SCAN_COOLDOWN_TICKS);
            //?} else {
            /*player.getCooldowns().addCooldown(this, BestiaryRules.SCAN_COOLDOWN_TICKS);
            *///?}
            player.stopUsingItem();
        }
    }

    //? if >=1.21.2 {
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player) {
            ScanTracker.reset(player);
        }
        return false;
    }
    //?} else {
    /*@Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player) {
            ScanTracker.reset(player);
        }
    }
    *///?}
}
