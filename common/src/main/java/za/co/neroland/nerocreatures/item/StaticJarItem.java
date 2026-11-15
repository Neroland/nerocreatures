package za.co.neroland.nerocreatures.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.wrecks.StaticJarBurst;
import za.co.neroland.nerocreatures.entity.wrecks.SwarmRules;

/**
 * {@code static_jar}: a bottled Static Swarm. Use to throw it; it bursts where it lands and briefly
 * disables mechanical creatures nearby ({@link StaticJarBurst}). Consumed on use (not in creative),
 * with a short cooldown. Server-authoritative: the client only swings.
 */
public class StaticJarItem extends Item {

    public StaticJarItem(Item.Properties properties) {
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
        if (level instanceof ServerLevel server) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW,
                    SoundSource.PLAYERS, 0.5F, 0.8F);
            StaticJarBurst.throwFrom(server, player);
            //? if >=1.21.2 {
            player.getCooldowns().addCooldown(stack, SwarmRules.JAR_COOLDOWN_TICKS);
            //?} else {
            /*player.getCooldowns().addCooldown(this, SwarmRules.JAR_COOLDOWN_TICKS);
            *///?}
            stack.consume(1, player);
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        *///?}
    }
}
