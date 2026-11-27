package za.co.neroland.nerocreatures.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.link.CreatureLinkJson;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * {@code my_creatures} shows the viewer's own loaded creatures and nothing else: Core's conformance fixture with
 * two viewers, a third owner, an absent dimension and far bounds; and the map-versus-link parity test against the link
 * module's {@code pets} section for owner, stranger, third-party and erased-owner fixtures. A mount ridden by
 * another player is hidden. NeroCreatures has no
 * member concept (ownership is one player), so there is no member fixture.
 */
class CreatureMapLayerProviderTest {
    //? if <1.20.3 {
    /*static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    *///?}

    private static final Identifier OVERWORLD_ID = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier OTHER_ID = Identifier.fromNamespaceAndPath("maptest", "other");
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, OVERWORLD_ID);
    private static final ResourceKey<Level> MOON =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("maptest", "moon"));
    private static final Identifier WISP = Identifier.fromNamespaceAndPath("nerocreatures", "glacite_wisp");
    private static final Identifier DRONE = Identifier.fromNamespaceAndPath("nerocreatures", "terraforming_drone");

    private static final UUID ENTITLED = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID THIRD = UUID.randomUUID();

    /** The real ownership index plus a fake entity index: which creatures are loaded, and where. */
    private static final class World implements CreatureMapSource {
        final CreatureOwnershipState state = new CreatureOwnershipState();
        final Map<UUID, BlockPos> loaded = new HashMap<>();
        final Map<UUID, List<UUID>> riders = new HashMap<>();

        OwnedCreature own(UUID player, OwnedCreature.Kind kind, Identifier type, Identifier dim,
                @Nullable BlockPos at) {
            OwnedCreature row = new OwnedCreature(UUID.randomUUID(), kind, type, dim);
            state.add(player, row);
            if (at != null) loaded.put(row.entity(), at);
            return row;
        }

        @Override
        public List<OwnedCreature> owned(MapViewer viewer) {
            return state.owned(viewer.id());
        }

        @Override
        public @Nullable BlockPos locate(MapViewer viewer, OwnedCreature row) {
            return loaded.get(row.entity());
        }

        @Override
        public List<UUID> riders(MapViewer viewer, OwnedCreature row) {
            return riders.getOrDefault(row.entity(), List.of());
        }

        CreatureLinkJson.Live live(OwnedCreature row) {
            boolean in = row.kind() != OwnedCreature.Kind.EGG && !row.parked() && loaded.containsKey(row.entity());
            return in ? new CreatureLinkJson.Live(true, 10F, 20F, null, List.of(), false, -1.0D, false, -1L, -1L)
                    : CreatureLinkJson.Live.UNLOADED;
        }
    }

    private static World seeded() {
        World world = new World();
        world.own(ENTITLED, OwnedCreature.Kind.PET, WISP, OVERWORLD_ID, new BlockPos(12, 64, 3));
        world.own(ENTITLED, OwnedCreature.Kind.DRONE, DRONE, OVERWORLD_ID, new BlockPos(-40, 70, 25));
        world.own(ENTITLED, OwnedCreature.Kind.MOUNT, WISP, OVERWORLD_ID, null); // unloaded
        world.own(ENTITLED, OwnedCreature.Kind.PET, WISP, OTHER_ID, new BlockPos(0, 64, 0)); // another world
        OwnedCreature parked = world.own(ENTITLED, OwnedCreature.Kind.PET, WISP, OVERWORLD_ID, new BlockPos(5, 64, 5));
        world.state.setParked(ENTITLED, parked.entity(), true, null);
        world.state.add(ENTITLED, OwnedCreature.egg(WISP, OVERWORLD_ID, 100L));
        world.own(THIRD, OwnedCreature.Kind.PET, WISP, OVERWORLD_ID, new BlockPos(8, 64, 8));
        return world;
    }

    private static MapViewer online(UUID id, int x, int z) {
        return MapViewer.forTest(id, null, OVERWORLD, new BlockPos(x, 64, z));
    }

    private static MapQueryResult query(World world, MapViewer viewer, ResourceKey<Level> dim, MapBounds bounds) {
        MapLayerRegistry.register(new CreatureMapLayerProvider(world));
        return MapLayerRegistry.query("nerocreatures", viewer, dim, bounds);
    }

    @Test
    void passesCoreConformance() {
        MapLayerConformance.create(new CreatureMapLayerProvider(seeded()))
                .viewers(online(ENTITLED, 0, 0), online(STRANGER, 3, 0))
                .dimensions(OVERWORLD, MOON)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenIds(THIRD)
                .forbiddenText("Trudy")
                .expectEntitledSees(CreatureMapLayers.MY_CREATURES, 2)
                .verify();
    }

    @Test
    void onlyLoadedPetsMountsAndDronesInThisWorldNearestFirst() {
        MapQueryResult result = query(seeded(), online(ENTITLED, 0, 0), OVERWORLD, MapBounds.around(0, 0, 512));
        List<MapMarker> markers = result.markers();
        assertEquals(2, markers.size(), "unloaded, parked, egg and other-world rows are skipped");
        assertEquals(12, markers.get(0).x());
        assertEquals("entity.nerocreatures.glacite_wisp", markers.get(0).label().text());
        assertEquals("map.nerocreatures.kind.drone", markers.get(1).details().get(0).text());
        assertTrue(query(seeded(), online(ENTITLED, 0, 0), OVERWORLD, MapBounds.around(20_000, 20_000, 256))
                .markers().isEmpty(), "bounds only narrow");
    }

    @Test
    void aMountAnotherPlayerRidesIsNotShownButOneTheOwnerRidesIs() {
        World world = new World();
        OwnedCreature mount = world.own(ENTITLED, OwnedCreature.Kind.MOUNT, WISP, OVERWORLD_ID,
                new BlockPos(30, 64, 30));
        MapViewer owner = online(ENTITLED, 0, 0);
        MapBounds near = MapBounds.around(0, 0, 512);

        world.riders.put(mount.entity(), List.of(THIRD));
        assertTrue(query(world, owner, OVERWORLD, near).markers().isEmpty(),
                "another player's position must never reach the owner's map");
        world.riders.put(mount.entity(), List.of(ENTITLED, THIRD));
        assertTrue(query(world, owner, OVERWORLD, near).markers().isEmpty(), "riding together still hides it");

        world.riders.put(mount.entity(), List.of(ENTITLED));
        assertEquals(1, query(world, owner, OVERWORLD, near).markers().size(), "the owner riding it sees it");
        world.riders.remove(mount.entity());
        assertEquals(1, query(world, owner, OVERWORLD, near).markers().size(), "riderless mount is shown");
    }

    // --- Parity: map versus link --------------------------------------------------------------

    private static Set<String> linkSet(World world, UUID player, Identifier dim) {
        JsonObject pets = CreatureLinkJson.pets(world.state, player, world::live, true, kind -> 10);
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement element : pets.getAsJsonArray("pets")) {
            JsonObject row = element.getAsJsonObject();
            if ("loaded".equals(row.get("status").getAsString()) && !"egg".equals(row.get("kind").getAsString())
                    && dim.toString().equals(row.get("dimension").getAsString())) {
                out.add(row.get("entity").getAsString());
            }
        }
        return out;
    }

    private static Set<String> mapSet(World world, UUID player) {
        return query(world, MapViewer.forTest(player, null, OVERWORLD, new BlockPos(0, 64, 0)), OVERWORLD,
                MapBounds.around(0, 0, 4096)).markers().stream()
                .map(m -> m.id().substring("creature/".length())).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    void mapAndLinkAgreeForOwnerStrangerThirdAndErasedOwner() {
        World world = seeded();
        for (UUID player : List.of(ENTITLED, STRANGER, THIRD)) {
            assertEquals(linkSet(world, player, OVERWORLD_ID), mapSet(world, player));
        }
        assertEquals(2, mapSet(world, ENTITLED).size());
        assertTrue(mapSet(world, STRANGER).isEmpty());
        world.state.forgetPlayer(ENTITLED);
        assertEquals(linkSet(world, ENTITLED, OVERWORLD_ID), mapSet(world, ENTITLED));
        assertTrue(mapSet(world, ENTITLED).isEmpty(), "an erased owner sees nothing on either surface");
    }
}
