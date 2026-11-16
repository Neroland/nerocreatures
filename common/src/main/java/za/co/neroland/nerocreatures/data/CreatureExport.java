package za.co.neroland.nerocreatures.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;

import za.co.neroland.nerolandcore.progression.ProgressionState;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.NestHome;
import za.co.neroland.nerocreatures.block.NestRules;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.entity.mechanical.TerraformingDrone;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * POPIA/GDPR data access: everything NeroCreatures holds about <b>one</b> player, and nothing about
 * anybody else. Backs {@code /nerocreatures export <player>}.
 *
 * <p>Sections, all scoped to the one player:
 * <ul>
 *   <li>{@code ownership} — their ownership-index rows (pets, mounts, drones, eggs, parked pets) and
 *       their retention stamp ({@link CreatureOwnershipState#export});</li>
 *   <li>{@code boss_contribution} — their own damage in any summoned fight still running, never another
 *       participant's ({@link BossContributionState#export});</li>
 *   <li>{@code progression_gates} — this mod's gates open for them in Core's progression store
 *       (Bestiary Scanned/Studied, first tame/deploy/breed). Core keeps and erases them; listed here
 *       because this mod writes them;</li>
 *   <li>{@code loaded_creatures} — creatures loaded right now whose own entity data names them as owner,
 *       with the creature's Nest home if it has one (the one coordinate this mod stores);</li>
 *   <li>{@code erasure_marker} — whether an earlier erasure left a salted one-way marker for them (yes
 *       or no; the marker itself cannot be read back into anything).</li>
 * </ul>
 */
public final class CreatureExport {

    /** Bumped when a section is added or changes shape. */
    public static final int FORMAT = 3;

    private CreatureExport() {
    }

    /** One loaded creature and the owner its entity data names. */
    public record LoadedCreature(UUID owner, UUID entity, String type, String kind, String dimension,
            Optional<NestRules.Home> nest) {
    }

    /** Builds the export from the live server. */
    public static JsonObject export(MinecraftServer server, UUID player) {
        JsonObject json = build(player, System.currentTimeMillis(), CreatureOwnershipState.get(server),
                BossContributionState.get(server), ProgressionState.get(server), loadedCreatures(server, player));
        // In memory only (never saved): the Apex Altars this player lit, for the app's cooldowns.
        json.add("altars_lit", za.co.neroland.nerocreatures.boss.AltarUses.export(server, player));
        return json;
    }

    /**
     * The pure export body. Every section reads by {@code player} alone (loaded creatures are filtered
     * by owner again here), so another player's data cannot enter it.
     */
    public static JsonObject build(UUID player, long now, CreatureOwnershipState ownership,
            BossContributionState contributions, ProgressionState progression, List<LoadedCreature> loaded) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("player", player.toString());
        json.addProperty("exported_at", now);
        json.add("ownership", ownership.export(player));
        json.add("boss_contribution", contributions.export(player));
        JsonArray gates = new JsonArray();
        progression.playerGates(player).stream()
                .filter(gate -> gate.startsWith(NeroCreaturesCommon.MOD_ID + ":"))
                .sorted()
                .forEach(gates::add);
        json.add("progression_gates", gates);
        JsonArray creatures = new JsonArray();
        for (LoadedCreature creature : loaded) {
            if (!player.equals(creature.owner())) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("entity", creature.entity().toString());
            row.addProperty("type", creature.type());
            row.addProperty("kind", creature.kind());
            row.addProperty("dimension", creature.dimension());
            creature.nest().ifPresent(home -> {
                JsonObject nest = new JsonObject();
                nest.addProperty("dimension", home.dimension());
                nest.addProperty("x", home.x());
                nest.addProperty("y", home.y());
                nest.addProperty("z", home.z());
                row.add("nest", nest);
            });
            creatures.add(row);
        }
        json.add("loaded_creatures", creatures);
        json.addProperty("erasure_marker", ownership.hasErasureMarker(player));
        return json;
    }

    /** Loaded pets, mounts and drones whose entity data names {@code player} as owner. */
    private static List<LoadedCreature> loadedCreatures(MinecraftServer server, UUID player) {
        List<LoadedCreature> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().identifier().toString();
            List<TameableCreature> pets = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(TameableCreature.class),
                    pet -> player.equals(pet.ownerId()), pets);
            for (TameableCreature pet : pets) {
                out.add(new LoadedCreature(player, pet.getUUID(), typeId(pet), pet.ownershipKind().key(), dimension,
                        NestHome.home(pet)));
            }
            List<TerraformingDrone> drones = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(TerraformingDrone.class),
                    drone -> player.equals(drone.ownerId()), drones);
            for (TerraformingDrone drone : drones) {
                out.add(new LoadedCreature(player, drone.getUUID(), typeId(drone), OwnedCreature.Kind.DRONE.key(),
                        dimension, Optional.empty()));
            }
        }
        return out;
    }

    private static String typeId(Entity entity) {
        return EntityType.getKey(entity.getType()).toString();
    }
}
