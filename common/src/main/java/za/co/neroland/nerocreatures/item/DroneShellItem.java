package za.co.neroland.nerocreatures.item;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.mechanical.TerraformingDrone;
import za.co.neroland.nerocreatures.link.CreatureLinkEvents;
import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * The packed-up form of a {@link TerraformingDrone}: right-click a block face to unfold one, bound
 * to you, working the area around where it landed.
 *
 * <p>It is deliberately <b>not</b> a spawn egg. A spawn egg makes an anonymous mob; this makes an
 * <em>owned</em> one, which means three things have to happen in the right order and on the server:
 *
 * <ol>
 *   <li>the {@code maxDronesPerPlayer} cap is checked against {@link CreatureOwnershipState}
 *       <b>before</b> anything is created — refusing costs the player nothing and says so on the
 *       action bar;</li>
 *   <li>the drone is bound to the placer and anchored to the clicked position before it enters the
 *       level, so it never exists for even a tick as an ownerless machine;</li>
 *   <li>the ownership index is updated, so the cap the next deploy sees is correct.</li>
 * </ol>
 *
 * <p>The item comes back when the drone does — shift-interact a drone with an empty hand and it
 * hands the shell over and folds itself away — so deploying is never a one-way spend.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the only thing recorded is "this game UUID owns this entity UUID,
 * of this type, in this dimension". No coordinates are stored, here or anywhere else, and nothing
 * on this path logs player identity.
 */
public class DroneShellItem extends Item {

    public DroneShellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // Client: report success so the arm swings; the server decides what actually happens.
            return InteractionResult.SUCCESS;
        }
        BlockPos anchor = context.getClickedPos().relative(context.getClickedFace());
        UUID owner = player.getUUID();
        CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
        int cap = NeroCreaturesConfig.MAX_DRONES_PER_PLAYER.get();
        if (cap <= 0 || state.count(owner, OwnedCreature.Kind.DRONE) >= cap) {
            notify(player, Component.translatable("message.nerocreatures.drone_cap", cap));
            return InteractionResult.SUCCESS_SERVER;
        }

        TerraformingDrone drone = ModEntities.TERRAFORMING_DRONE.get()
                .create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        drone.snapTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, player.getYRot(), 0.0F);
        drone.deployBy(player, anchor);
        drone.finalizeSpawn(level, level.getCurrentDifficultyAt(anchor),
                EntitySpawnReason.SPAWN_ITEM_USE, null);
        if (!level.addFreshEntity(drone)) {
            return InteractionResult.FAIL;
        }

        state.add(owner, OwnedCreature.of(drone, OwnedCreature.Kind.DRONE));
        CreatureLinkEvents.petStateChanged(level, owner, drone, OwnedCreature.Kind.DRONE,
                CreatureLinkEvents.STATE_DEPLOYED);
        level.playSound(null, anchor, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.4F);
        ItemStack stack = context.getItemInHand();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Action-bar feedback to the placing player only. Never chat, never broadcast. */
    private static void notify(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }
}
