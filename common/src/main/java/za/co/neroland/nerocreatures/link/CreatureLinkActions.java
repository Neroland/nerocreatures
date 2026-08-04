package za.co.neroland.nerocreatures.link;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * The write half of the link module, and a deliberately tiny one: a single action,
 * {@code pet_recall}, which brings your own pets to you.
 *
 * <h2>Why this action and no others</h2>
 *
 * <p>Everything else a companion client might want to do to a creature — taming it, commanding it,
 * deploying a drone, summoning a boss — is an <em>in-world</em> act with an in-world cost and an
 * in-world audience. Doing any of those from a phone would let a player affect the world without
 * being in it. Recalling a pet you already own to a place you are already standing changes nothing
 * that was not already yours, which is what makes it the one safe write.
 *
 * <h2>Validation</h2>
 *
 * <p>Server-authoritative, and the incoming {@link UUID} is trusted for <em>nothing beyond scoping
 * the request to that player's own data</em>:
 *
 * <ol>
 *   <li>the player must be online ({@link LinkActionResult.Error#PLAYER_OFFLINE_REQUIRED}) — see
 *       {@link #allowOffline(String)};</li>
 *   <li>an optional {@code pet} parameter naming an entity UUID must be one of <em>this player's
 *       own</em> ownership rows, or the call is refused with
 *       {@link LinkActionResult.Error#NOT_OWNER} — which is also the answer for a pet that belongs
 *       to somebody else, so the action cannot be used to probe for other players' creatures;</li>
 *   <li>a pet must be in the same dimension as its owner and currently loaded, or it is skipped
 *       with a reason ({@link LinkActionResult.Error#VALIDATION} if that leaves nothing to
 *       recall).</li>
 * </ol>
 *
 * <p><b>Drones are never recalled by this action</b>, and that is not an oversight: a drone's whole
 * purpose is the area it was deployed to work, and teleporting it to its owner would quietly break
 * it. Folding a drone away remains a deliberate in-world act (sneak-interact with an empty hand).
 *
 * <h2>What "recall" actually does</h2>
 *
 * <p>The pet is placed on a valid block near the owner — solid ground, open air, room to stand,
 * found by a small bounded search — exactly the checks the spawn engine uses. If no such place
 * exists the pet stays where it is and is reported as skipped; a recall never puts an animal inside
 * a wall or over a drop. A pet's standing order is preserved and re-applied, so a guarding pet
 * guards its new spot rather than walking back to the old one.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> No coordinates are read from or written to any store; the search
 * happens against the live world and is forgotten. A result names only the requesting player's own
 * creatures. Nothing on this path logs player identity.
 *
 * <p>Server thread only.
 */
public final class CreatureLinkActions implements LinkActionHandler {

    private static final List<String> ACTIONS = List.of(CreatureLinkModule.ACTION_PET_RECALL);

    /** Candidate positions tried around the owner before giving up on one pet. */
    private static final int PLACEMENT_ATTEMPTS = 12;

    /** Half-width of the box around the owner a pet may be placed in, in blocks. */
    private static final int PLACEMENT_SPREAD = 3;

    /** Reported for a pet that is in another dimension from its owner. */
    private static final String SKIP_OTHER_DIMENSION = "other_dimension";

    /** Reported for a pet whose chunk is not loaded, so there is nothing to move. */
    private static final String SKIP_NOT_LOADED = "not_loaded";

    /** Reported when no safe standing position could be found near the owner. */
    private static final String SKIP_NO_ROOM = "no_room";

    @Override
    public String moduleId() {
        return CreatureLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    /**
     * {@code false}, and permanently so. A recall moves an animal to where its owner is standing;
     * an offline player is not standing anywhere. There is no sensible offline semantics for this
     * action, so refusing it is not a limitation to be lifted later.
     */
    @Override
    public boolean allowOffline(String actionId) {
        return false;
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (!CreatureLinkModule.ACTION_PET_RECALL.equals(actionId)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "NeroCreatures does not know the action '" + actionId + "'.");
        }
        if (playerId == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "No player was supplied.");
        }
        MinecraftServer server = CreatureLinkAccess.server();
        if (server == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "The server is not running a world yet.");
        }
        try {
            return recall(server, playerId, params);
        } catch (RuntimeException e) {
            // Action id only — never who asked (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] NeroLink action '{}' failed.", actionId, e);
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "The recall could not be processed.");
        }
    }

    // --- pet_recall -----------------------------------------------------------

    private static LinkActionResult recall(MinecraftServer server, UUID playerId, JsonObject params) {
        ServerPlayer owner = CreatureLinkAccess.online(server, playerId);
        if (owner == null) {
            return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                    "Pets can only be recalled while you are online.");
        }
        if (!(owner.level() instanceof ServerLevel ownerLevel)) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "You are not in a server level.");
        }

        UUID requested = entityId(params);
        if (requested == null && params != null && params.has("pet")) {
            // Present but unusable. Falling through to "recall everything" would turn a typo into a
            // much larger action than the caller asked for.
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "The 'pet' parameter must be a creature UUID.");
        }
        List<OwnedCreature> rows = CreatureOwnershipState.get(server).owned(playerId);
        if (requested != null && rows.stream().noneMatch(row -> row.entity().equals(requested))) {
            // Indistinguishable from "no such creature", so the action cannot be used to find out
            // whether somebody else's pet exists.
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER,
                    "You do not own a creature with that id.");
        }

        JsonArray recalled = new JsonArray();
        JsonArray skipped = new JsonArray();
        int candidates = 0;

        for (OwnedCreature row : rows) {
            if (row.kind() != OwnedCreature.Kind.PET) {
                continue; // a drone belongs to its work area, not to its owner's feet
            }
            if (requested != null && !row.entity().equals(requested)) {
                continue;
            }
            candidates++;
            if (!row.dimension().equals(ownerLevel.dimension().identifier())) {
                skipped.add(skip(row, SKIP_OTHER_DIMENSION));
                continue;
            }
            Entity entity = ownerLevel.getEntity(row.entity());
            if (!(entity instanceof TameableCreature pet) || !pet.isAlive()) {
                skipped.add(skip(row, SKIP_NOT_LOADED));
                continue;
            }
            if (!teleportNear(ownerLevel, pet, owner)) {
                skipped.add(skip(row, SKIP_NO_ROOM));
                continue;
            }
            JsonObject moved = new JsonObject();
            moved.addProperty("entity", row.entity().toString());
            moved.addProperty("type", row.type().toString());
            moved.addProperty("name", CreatureLinkAccess.readablePath(row.type().getPath()));
            moved.addProperty("command", pet.command().key());
            recalled.add(moved);
        }

        if (candidates == 0) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "You have no tamed pets to recall.");
        }
        if (recalled.isEmpty()) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "None of your pets could be recalled right now.");
        }

        JsonObject result = new JsonObject();
        result.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        result.addProperty("recalled", recalled.size());
        result.add("pets", recalled);
        result.add("skipped", skipped);
        return LinkActionResult.ok(result);
    }

    /**
     * Puts one pet down somewhere it can actually stand near its owner. Bounded to
     * {@value #PLACEMENT_ATTEMPTS} candidates; returns {@code false} without moving anything if
     * none of them work.
     */
    private static boolean teleportNear(ServerLevel level, TameableCreature pet, ServerPlayer owner) {
        RandomSource random = level.getRandom();
        BlockPos anchor = owner.blockPosition();
        int span = PLACEMENT_SPREAD * 2 + 1;
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            BlockPos pos = anchor.offset(
                    random.nextInt(span) - PLACEMENT_SPREAD,
                    random.nextInt(3) - 1,
                    random.nextInt(span) - PLACEMENT_SPREAD);
            if (level.getBlockState(pos.below()).isAir() || !level.getBlockState(pos).isAir()) {
                continue;
            }
            if (!level.noCollision(pet.getType().getSpawnAABB(
                    pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))) {
                continue;
            }
            pet.getNavigation().stop();
            pet.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    pet.getYRot(), pet.getXRot());
            // Re-apply the standing order so a guarding pet holds its NEW spot rather than the one
            // it was just taken from.
            pet.setCommand(pet.command());
            return true;
        }
        return false;
    }

    private static JsonObject skip(OwnedCreature row, String reason) {
        JsonObject entry = new JsonObject();
        entry.addProperty("entity", row.entity().toString());
        entry.addProperty("type", row.type().toString());
        entry.addProperty("reason", reason);
        return entry;
    }

    /** The {@code pet} parameter as an entity UUID, or {@code null} if absent or malformed. */
    @Nullable
    private static UUID entityId(JsonObject params) {
        if (params == null || !params.has("pet")) {
            return null;
        }
        JsonElement element = params.get("pet");
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String raw = element.getAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
