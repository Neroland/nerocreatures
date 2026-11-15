package za.co.neroland.nerocreatures.world;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.5 {
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
//?}

/**
 * Who a creature acts for, read the same way on every MC line. Server-authoritative: ownership is
 * the vanilla {@code OwnableEntity} owner stored on the entity ({@code EntityReference} from 1.21.5,
 * a bare UUID before), never a nearest-player guess.
 *
 * <p>The returned UUIDs are for comparisons only. Never log, print or send them (POPIA/GDPR).
 */
public final class CreatureOwners {

    private CreatureOwners() {
    }

    /** The owner's UUID of a tamed pet, mount or drone, or {@code null} for a wild entity. */
    @Nullable
    public static UUID ownerUuid(@Nullable Entity entity) {
        if (!(entity instanceof OwnableEntity ownable)) {
            return null;
        }
        //? if >=1.21.5 {
        EntityReference<LivingEntity> ref = ownable.getOwnerReference();
        return ref == null ? null : ref.getUUID();
        //?} else {
        /*return ownable.getOwnerUUID();
        *///?}
    }

    /**
     * The player a creature is acting for: its owner when it has one, otherwise the player holding
     * its leash, otherwise {@code null} (wild). Returned as a UUID so an offline owner is still
     * "owned" (and therefore paused, see {@link CreatureGriefing}).
     */
    @Nullable
    public static UUID controllerUuid(Mob actor) {
        UUID owner = ownerUuid(actor);
        if (owner != null) {
            return owner;
        }
        Entity holder = actor.getLeashHolder();
        return holder instanceof Player player ? player.getUUID() : null;
    }

    /** The online owner (or leash-holding player) in the same level, or {@code null}. */
    @Nullable
    public static ServerPlayer onlineController(ServerLevel level, Mob actor) {
        UUID id = controllerUuid(actor);
        if (id == null) {
            return null;
        }
        MinecraftServer server = level.getServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
        return player != null && player.level() == level ? player : null;
    }
}
