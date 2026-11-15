package za.co.neroland.nerocreatures.item;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import za.co.neroland.nerocreatures.entity.humanoid.PirateCaptain;
import za.co.neroland.nerocreatures.entity.humanoid.PirateLoadout;
import za.co.neroland.nerocreatures.entity.wrecks.CaptainRules;
import za.co.neroland.nerocreatures.entity.wrecks.WrecksLookups;

/**
 * {@code captains_ledger}: a Pirate Captain's log, given only to the player who killed the captain. It
 * marks one pirate stash: a spot {@value CaptainRules#STASH_MIN}–{@value CaptainRules#STASH_MAX} blocks
 * from where the captain fell, in the same dimension. This mod has no stash structure, so the stash is
 * a pirate-band hiding place: when the holder stands within {@value CaptainRules#REDEEM_RADIUS} blocks
 * and uses the ledger, it is consumed, the stash loot ({@code nerocreatures:gameplay/pirate_stash}) goes
 * to the holder, and {@value CaptainRules#STASH_GUARDS} raider-kit pirates come for them. A structure
 * mod can later place a real stash at the mark without changing the item.
 *
 * <p><b>Privacy.</b> The mark is stored only on the item, in the vanilla {@code lodestone_tracker}
 * component (dimension and block position, untracked), so it travels with the item and nowhere else.
 * It is shown only to the holder, as a distance and a compass direction in their own action bar when
 * they use it. Never logged, never broadcast, nothing about a player stored.
 */
public class CaptainsLedgerItem extends Item {

    private static final int PLACEMENT_TRIES = 4;

    public CaptainsLedgerItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * A marked ledger for a captain that fell at {@code origin}; empty if the item is not registered.
     * The mark stays inside the world border (up to {@value #PLACEMENT_TRIES} angles, else the origin).
     */
    public static ItemStack create(ServerLevel level, BlockPos origin, RandomSource random) {
        Item item = WrecksLookups.item(WrecksLookups.CAPTAINS_LEDGER);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        BlockPos stash = origin;
        for (int i = 0; i < PLACEMENT_TRIES; i++) {
            int[] off = CaptainRules.stashOffset(random.nextDouble() * Math.PI * 2.0D, random.nextDouble());
            BlockPos candidate = origin.offset(off[0], 0, off[1]);
            if (level.getWorldBorder().isWithinBounds(candidate)) {
                stash = candidate;
                break;
            }
        }
        ItemStack ledger = new ItemStack(item);
        mark(ledger, level.dimension(), stash);
        return ledger;
    }

    /** Writes the stash mark onto a ledger. */
    public static void mark(ItemStack ledger, ResourceKey<Level> dimension, BlockPos stash) {
        ledger.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(dimension, stash)), false));
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    *///?}
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer holder) {
            read(server, holder, stack);
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        *///?}
    }

    private static void read(ServerLevel level, ServerPlayer holder, ItemStack stack) {
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null || tracker.target().isEmpty()) {
            holder.sendSystemMessage(Component.translatable("message.nerocreatures.captains_ledger.blank"), true);
            return;
        }
        GlobalPos mark = tracker.target().get();
        if (!mark.dimension().equals(level.dimension())) {
            holder.sendSystemMessage(Component.translatable("message.nerocreatures.captains_ledger.other_world"), true);
            return;
        }
        double dx = mark.pos().getX() + 0.5D - holder.getX();
        double dz = mark.pos().getZ() + 0.5D - holder.getZ();
        if (!CaptainRules.atStash(dx, dz)) {
            holder.sendSystemMessage(Component.translatable("message.nerocreatures.captains_ledger.bearing",
                    CaptainRules.roundedDistance(dx, dz),
                    Component.translatable("message.nerocreatures.direction." + CaptainRules.direction(dx, dz))), true);
            return;
        }
        openStash(level, holder, stack);
    }

    private static void openStash(ServerLevel level, ServerPlayer holder, ItemStack stack) {
        stack.consume(1, holder);
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, WrecksLookups.PIRATE_STASH_LOOT);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, holder.position())
                .withParameter(LootContextParams.THIS_ENTITY, holder)
                .create(LootContextParamSets.CHEST);
        for (ItemStack loot : table.getRandomItems(params)) {
            if (!holder.getInventory().add(loot) && !loot.isEmpty()) {
                holder.spawnAtLocation(level, loot);
            }
        }
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        PirateCaptain.spawnCrew(level, holder.blockPosition(), CaptainRules.STASH_GUARDS,
                PirateLoadout.RAIDER_TIER, holder);
        holder.sendSystemMessage(Component.translatable("message.nerocreatures.captains_ledger.opened"), true);
    }
}
