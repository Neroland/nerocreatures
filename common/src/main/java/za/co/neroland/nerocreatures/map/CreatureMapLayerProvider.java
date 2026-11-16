package za.co.neroland.nerocreatures.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * {@code nerocreatures:my_creatures} ({@link MapScope#OWNER}): one marker per pet, mount or drone in the viewer's
 * own ownership rows that is loaded and alive in the queried dimension. Eggs and parked creatures have no place in
 * the world and are skipped. This is the same row set and the same loaded test as the link module's {@code pets}
 * section, so the map never shows a creature the link would not (see the parity test). A creature another player
 * is riding with is not shown at all: its position is that player's.
 *
 * <p>Labels are the species' lang key and the kind, never a player name. Positions are read live on each query and
 * never kept; the marker id is the creature's own entity id, which names no player.
 */
public final class CreatureMapLayerProvider implements MapLayerProvider {

    private static final List<MapLayerInfo> LAYERS = List.of(MapLayerInfo.of(CreatureMapLayers.MY_CREATURES,
            "map.layer.nerocreatures.my_creatures", CreatureMapLayers.ICON, MapScope.OWNER));

    private final CreatureMapSource source;

    public CreatureMapLayerProvider(CreatureMapSource source) {
        this.source = source;
    }

    @Override
    public String modId() {
        return NeroCreaturesCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    private record Found(OwnedCreature row, BlockPos pos) { }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        if (viewer == null || dimension == null || bounds == null || sink.isFull(CreatureMapLayers.MY_CREATURES)) {
            return;
        }
        Identifier dim = dimension.identifier();
        List<Found> found = new ArrayList<>();
        for (OwnedCreature row : source.owned(viewer)) {
            if (!shown(row) || !row.dimension().equals(dim)) continue;
            if (ridingWithAnother(viewer.id(), source.riders(viewer, row))) continue;
            BlockPos pos = source.locate(viewer, row);
            if (pos != null && bounds.contains(pos.getX(), pos.getZ())) found.add(new Found(row, pos));
        }
        if (found.isEmpty()) return;
        int originX = viewer.position().map(BlockPos::getX).orElse(bounds.centreX());
        int originZ = viewer.position().map(BlockPos::getZ).orElse(bounds.centreZ());
        found.sort(Comparator.comparingLong(f -> distanceSq(f.pos(), originX, originZ)));
        for (Found f : found) {
            if (sink.isFull(CreatureMapLayers.MY_CREATURES)) return;
            Identifier type = f.row().type();
            sink.marker(MapMarker.builder(markerId(f.row()), CreatureMapLayers.MY_CREATURES, dimension, f.pos())
                    .label(MapLabel.translatable("entity." + type.getNamespace() + "." + type.getPath().replace('/', '.')))
                    .detail(MapLabel.translatable("map.nerocreatures.kind." + f.row().kind().key()))
                    .build());
        }
    }

    /** Pets, mounts and drones are creatures in the world; an egg row is an incubation id and a parked pet is stored. */
    static boolean shown(OwnedCreature row) {
        return row.kind() != OwnedCreature.Kind.EGG && !row.parked();
    }

    /**
     * True when a player other than the viewer rides with the creature (for example a mount on a server that lets
     * anyone ride). Its position is then that player's live position, so the creature is not shown.
     */
    static boolean ridingWithAnother(UUID viewer, List<UUID> riders) {
        for (UUID rider : riders) {
            if (!rider.equals(viewer)) return true;
        }
        return false;
    }

    /** The creature's own entity id: stable, and not a player's id. */
    static String markerId(OwnedCreature row) {
        return "creature/" + row.entity();
    }

    private static long distanceSq(BlockPos pos, int x, int z) {
        long dx = (long) pos.getX() - x;
        long dz = (long) pos.getZ() - z;
        return dx * dx + dz * dz;
    }
}
