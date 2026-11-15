package za.co.neroland.nerocreatures.item.gear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One gear item ({@link GearKind}). Passive kinds do their work in {@link CreatureGear#serverTick};
 * active kinds (Blink Charm, Rime Flask, Resonant Whistle) work on use, server-side, with a vanilla
 * item cooldown. None is consumed.
 */
public class CreatureGearItem extends Item {

    private final GearKind kind;

    public CreatureGearItem(Item.Properties properties, GearKind kind) {
        super(properties);
        this.kind = kind;
    }

    /** Which gear this is. */
    public GearKind kind() {
        return this.kind;
    }

    /** Whether {@code stack} is gear of {@code kind}. */
    public static boolean is(ItemStack stack, GearKind kind) {
        return !stack.isEmpty() && stack.getItem() instanceof CreatureGearItem gear && gear.kind == kind;
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    *///?}
        ItemStack stack = player.getItemInHand(hand);
        boolean handled = this.kind.active();
        if (handled && level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
                && !CreatureGear.onCooldown(player, stack)) {
            switch (this.kind) {
                case BLINK_CHARM -> CreatureGear.blink(server, serverPlayer, stack);
                case RIME_FLASK -> CreatureGear.drinkRimeFlask(server, serverPlayer, stack);
                case RESONANT_WHISTLE -> CreatureGear.whistle(server, serverPlayer, stack);
                default -> {
                }
            }
        }
        //? if >=1.21.2 {
        return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        //?} else {
        /*return handled ? net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide())
                : net.minecraft.world.InteractionResultHolder.pass(stack);
        *///?}
    }
}
