package za.co.neroland.nerocreatures.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * Where the provider reads the viewer's own rows and finds a loaded creature. Only ever asked about the viewer's
 * own UUID. The server implementation reads the ownership index and the level's entity index, the same two
 * lookups the link module's {@code pets} section makes; it never loads a chunk.
 */
public interface CreatureMapSource {

    /** The viewer's own ownership rows. */
    List<OwnedCreature> owned(MapViewer viewer);

    /** Where this row's creature is, if it is loaded and alive in its row's dimension; otherwise {@code null}. */
    @Nullable
    BlockPos locate(MapViewer viewer, OwnedCreature row);

    /**
     * The ids of every player riding with this row's creature: anyone on the creature, on something it carries, or
     * on the vehicle it is itself riding. Empty when it is not loaded or nobody rides with it. The provider hides
     * the creature whenever one of these is not the viewer, because its position is then that player's position.
     */
    List<UUID> riders(MapViewer viewer, OwnedCreature row);

    CreatureMapSource SERVER = new CreatureMapSource() {
        @Override
        public List<OwnedCreature> owned(MapViewer viewer) {
            MinecraftServer server = viewer.server();
            return server == null ? List.of() : CreatureOwnershipState.get(server).owned(viewer.id());
        }

        @Override
        public @Nullable BlockPos locate(MapViewer viewer, OwnedCreature row) {
            MinecraftServer server = viewer.server();
            if (server == null) return null;
            ServerLevel level = server.getLevel(row.dimensionKey());
            Entity entity = level == null ? null : level.getEntity(row.entity());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) return null;
            return living.blockPosition();
        }

        @Override
        public List<UUID> riders(MapViewer viewer, OwnedCreature row) {
            MinecraftServer server = viewer.server();
            if (server == null) return List.of();
            ServerLevel level = server.getLevel(row.dimensionKey());
            Entity entity = level == null ? null : level.getEntity(row.entity());
            if (entity == null) return List.of();
            Entity root = entity.getRootVehicle();
            List<UUID> out = new ArrayList<>();
            if (root instanceof Player player) out.add(player.getUUID());
            for (Entity passenger : root.getIndirectPassengers()) {
                if (passenger instanceof Player player) out.add(player.getUUID());
            }
            return out;
        }
    };
}
