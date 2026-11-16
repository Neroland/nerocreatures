package za.co.neroland.nerocreatures.link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

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
import za.co.neroland.nerocreatures.compat.CompanionBridge;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.tame.PetCommand;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * The write half of the link module: {@code pet_recall} (bring your own pets and mounts to you) and,
 * from schema 2, {@code pet_command} (tell one of your own pets or mounts to follow, stay or sit).
 *
 * <h2>Why these two and no others</h2>
 *
 * <p>Everything else a companion client might want to do to a creature — taming it, deploying a
 * drone, summoning a boss — is an <em>in-world</em> act with an in-world cost and audience. Recalling a
 * creature you own to where you are standing, or changing its standing order, changes nothing that was
 * not already yours.
 *
 * <h2>Validation (server-authoritative)</h2>
 *
 * <ol>
 *   <li>A per-player, per-action rate limit ({@code linkRecallCooldownSeconds},
 *       {@code linkCommandCooldownMillis}), on top of the bridge's per-device limit. Refused with
 *       {@code VALIDATION} and the token {@code rate_limited}.</li>
 *   <li>Parameters: {@code pet} must be a creature id when present (required for
 *       {@code pet_command}); {@code command} must be exactly {@code follow}, {@code stay} or
 *       {@code sit} — anything else is {@code unknown_command}, never a silent default.</li>
 *   <li>The player must be online ({@code PLAYER_OFFLINE_REQUIRED}).</li>
 *   <li>Ownership through the ownership store: a creature id that is not one of <em>this player's
 *       own</em> rows answers {@code NOT_OWNER} / {@code not_owner}, exactly like one that does not
 *       exist, so the action cannot probe for other players' creatures.</li>
 * </ol>
 *
 * <p>Refusals carry a stable <b>token</b> as the message, never a sentence, so the app can show its own
 * words: {@code rate_limited}, {@code bad_request}, {@code unknown_command}, {@code not_owner},
 * {@code no_pets}, {@code not_loaded}, {@code other_dimension}, {@code no_room}, {@code player_offline},
 * {@code internal}.
 *
 * <p><b>Drones are never recalled or commanded</b>: a drone belongs to the area it works.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> No coordinates are read from or written to any store; a recall's
 * placement search happens against the live world and is forgotten. Results name only the requesting
 * player's own creatures. Nothing on this path logs player identity.
 *
 * <p>Server thread only.
 */
public final class CreatureLinkActions implements LinkActionHandler {

    private static final List<String> ACTIONS = List.of(
            CreatureLinkModule.ACTION_PET_RECALL, CreatureLinkModule.ACTION_PET_COMMAND);

    /** Candidate positions tried around the owner before giving up on one pet. */
    private static final int PLACEMENT_ATTEMPTS = 12;

    /** Half-width of the box around the owner a pet may be placed in, in blocks. */
    private static final int PLACEMENT_SPREAD = 3;

    public static final String RATE_LIMITED = "rate_limited";
    public static final String BAD_REQUEST = "bad_request";
    public static final String UNKNOWN_COMMAND = "unknown_command";
    public static final String NOT_OWNER = "not_owner";
    public static final String NO_PETS = "no_pets";
    public static final String NOT_LOADED = "not_loaded";
    public static final String OTHER_DIMENSION = "other_dimension";
    public static final String NO_ROOM = "no_room";
    public static final String PLAYER_OFFLINE = "player_offline";
    public static final String INTERNAL = "internal";

    private final CreatureLinkRules.RateLimiter limiter = new CreatureLinkRules.RateLimiter();
    private final LongSupplier clockMs;
    private final LongSupplier recallIntervalMs;
    private final LongSupplier commandIntervalMs;

    /** The registered handler: wall clock and the two config keys. */
    public CreatureLinkActions() {
        this(System::currentTimeMillis,
                () -> NeroCreaturesConfig.LINK_RECALL_COOLDOWN_SECONDS.get() * 1000L,
                () -> NeroCreaturesConfig.LINK_COMMAND_COOLDOWN_MILLIS.get().longValue());
    }

    /** For tests: an explicit clock and intervals. */
    public CreatureLinkActions(LongSupplier clockMs, LongSupplier recallIntervalMs, LongSupplier commandIntervalMs) {
        this.clockMs = clockMs;
        this.recallIntervalMs = recallIntervalMs;
        this.commandIntervalMs = commandIntervalMs;
    }

    @Override
    public String moduleId() {
        return CreatureLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    /**
     * {@code false}, and permanently so. A recall moves a creature to where its owner is standing, and
     * a standing order is something a player gives while they are in the world.
     */
    @Override
    public boolean allowOffline(String actionId) {
        return false;
    }

    /** Erasure: the limiter forgets the player (its entries expire within seconds anyway). */
    public void forgetPlayer(UUID player) {
        this.limiter.forget(player);
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        boolean recall = CreatureLinkModule.ACTION_PET_RECALL.equals(actionId);
        boolean command = CreatureLinkModule.ACTION_PET_COMMAND.equals(actionId);
        if (!recall && !command) {
            return refuse(LinkActionResult.Error.VALIDATION, BAD_REQUEST);
        }
        if (playerId == null) {
            return refuse(LinkActionResult.Error.VALIDATION, BAD_REQUEST);
        }
        long interval = recall ? this.recallIntervalMs.getAsLong() : this.commandIntervalMs.getAsLong();
        if (!this.limiter.tryAcquire(playerId, actionId, this.clockMs.getAsLong(), interval)) {
            return refuse(LinkActionResult.Error.VALIDATION, RATE_LIMITED);
        }

        // Parameters are checked before anything in the world is touched.
        boolean hasPet = params != null && params.has("pet");
        UUID requested = entityId(params);
        if (hasPet && requested == null) {
            // Present but unusable: never widen a typo into "recall everything".
            return refuse(LinkActionResult.Error.VALIDATION, BAD_REQUEST);
        }
        PetCommand order = null;
        if (command) {
            if (requested == null) {
                return refuse(LinkActionResult.Error.VALIDATION, BAD_REQUEST);
            }
            Optional<PetCommand> parsed = PetCommand.fromRemoteKey(stringParam(params, "command"));
            if (parsed.isEmpty()) {
                return refuse(LinkActionResult.Error.VALIDATION, UNKNOWN_COMMAND);
            }
            order = parsed.get();
        }

        MinecraftServer server = CreatureLinkAccess.server();
        if (server == null) {
            return refuse(LinkActionResult.Error.INTERNAL, INTERNAL);
        }
        try {
            ServerPlayer owner = CreatureLinkAccess.online(server, playerId);
            if (owner == null) {
                return refuse(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED, PLAYER_OFFLINE);
            }
            List<OwnedCreature> rows = CreatureOwnershipState.get(server).owned(playerId);
            if (requested != null && rows.stream().noneMatch(row -> row.entity().equals(requested))) {
                return refuse(LinkActionResult.Error.NOT_OWNER, NOT_OWNER);
            }
            return recall ? recall(owner, playerId, rows, requested)
                    : command(server, playerId, rows, requested, order);
        } catch (RuntimeException e) {
            // Action id only — never who asked (POPIA/GDPR).
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] NeroLink action '{}' failed: {}", actionId,
                    e.getClass().getSimpleName());
            NeroCreaturesTelemetry.captureHandledException(e, "link-action", actionId);
            return refuse(LinkActionResult.Error.INTERNAL, INTERNAL);
        }
    }

    private static LinkActionResult refuse(LinkActionResult.Error error, String token) {
        return LinkActionResult.error(error, token);
    }

    /** Whether a row may be recalled or commanded: pets and mounts, never drones or eggs. */
    static boolean commandable(OwnedCreature row) {
        return row.kind() == OwnedCreature.Kind.PET || row.kind() == OwnedCreature.Kind.MOUNT;
    }

    // --- pet_command ----------------------------------------------------------------

    private static LinkActionResult command(MinecraftServer server, UUID playerId, List<OwnedCreature> rows,
            UUID requested, PetCommand order) {
        OwnedCreature row = rows.stream().filter(r -> r.entity().equals(requested)).findFirst().orElse(null);
        if (row == null) {
            return refuse(LinkActionResult.Error.NOT_OWNER, NOT_OWNER);
        }
        if (!commandable(row)) {
            return refuse(LinkActionResult.Error.VALIDATION, UNKNOWN_COMMAND);
        }
        if (row.parked()) {
            return refuse(LinkActionResult.Error.VALIDATION, NOT_LOADED);
        }
        ServerLevel level = server.getLevel(row.dimensionKey());
        Entity entity = level == null ? null : level.getEntity(row.entity());
        if (!(entity instanceof TameableCreature pet) || !pet.isAlive() || pet.isPerched()) {
            return refuse(LinkActionResult.Error.VALIDATION, NOT_LOADED);
        }
        if (!playerId.equals(pet.ownerId())) {
            // The store and the creature disagree: answer as though it is not ours.
            return refuse(LinkActionResult.Error.NOT_OWNER, NOT_OWNER);
        }
        pet.setCommand(order);
        CompanionBridge.petCommandChanged(pet, order.key());
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        result.addProperty("entity", row.entity().toString());
        result.addProperty("command", order.key());
        return LinkActionResult.ok(result);
    }

    // --- pet_recall -----------------------------------------------------------------

    /**
     * Why a loaded creature found for a recall row is skipped, or {@code null} to move it. The creature's
     * own owner must be the asker, exactly as for {@code pet_command}: a store row alone never moves a
     * creature (if the two ever disagree, it answers as though it were not theirs).
     *
     * @param movable alive, not perched, not riding or ridden, not leashed
     */
    static @Nullable String recallSkip(UUID asker, @Nullable UUID entityOwner, boolean movable) {
        if (!movable) {
            return NOT_LOADED;
        }
        return asker.equals(entityOwner) ? null : NOT_OWNER;
    }

    private static LinkActionResult recall(ServerPlayer owner, UUID playerId, List<OwnedCreature> rows,
            @Nullable UUID requested) {
        if (!(owner.level() instanceof ServerLevel ownerLevel)) {
            return refuse(LinkActionResult.Error.INTERNAL, INTERNAL);
        }
        JsonArray recalled = new JsonArray();
        JsonArray skipped = new JsonArray();
        String firstSkip = null;
        int candidates = 0;

        for (OwnedCreature row : rows) {
            if (!commandable(row)) {
                continue; // a drone belongs to its work area, an egg to its Incubator
            }
            if (requested != null && !row.entity().equals(requested)) {
                continue;
            }
            candidates++;
            String reason = null;
            TameableCreature pet = null;
            if (row.parked()) {
                reason = NOT_LOADED;
            } else if (!row.dimension().equals(ownerLevel.dimension().identifier())) {
                reason = OTHER_DIMENSION;
            } else {
                Entity entity = ownerLevel.getEntity(row.entity());
                if (!(entity instanceof TameableCreature found)) {
                    reason = NOT_LOADED;
                } else {
                    reason = recallSkip(playerId, found.ownerId(), found.isAlive() && !found.isPerched()
                            && !found.isPassenger() && !found.isVehicle() && !found.isLeashed());
                    if (reason == null) {
                        pet = found;
                        if (!teleportNear(ownerLevel, pet, owner)) {
                            reason = NO_ROOM;
                        }
                    }
                }
            }
            if (reason != null || pet == null) {
                reason = reason == null ? NOT_LOADED : reason;
                skipped.add(skip(row, reason));
                if (firstSkip == null) {
                    firstSkip = reason;
                }
                continue;
            }
            JsonObject moved = new JsonObject();
            moved.addProperty("entity", row.entity().toString());
            moved.addProperty("type", row.type().toString());
            moved.addProperty("name", CreatureLinkJson.speciesName(row.type()));
            moved.addProperty("command", pet.command().key());
            recalled.add(moved);
        }

        if (candidates == 0) {
            return refuse(LinkActionResult.Error.VALIDATION, NO_PETS);
        }
        if (recalled.isEmpty()) {
            if (NOT_OWNER.equals(firstSkip)) {
                return refuse(LinkActionResult.Error.NOT_OWNER, NOT_OWNER);
            }
            return refuse(LinkActionResult.Error.VALIDATION, firstSkip == null ? NOT_LOADED : firstSkip);
        }

        JsonObject result = new JsonObject();
        result.addProperty("schema_version", CreatureLinkModule.SCHEMA_VERSION);
        result.addProperty("recalled", recalled.size());
        result.add("pets", recalled);
        result.add("skipped", skipped);
        return LinkActionResult.ok(result);
    }

    /**
     * Puts one creature down somewhere it can actually stand near its owner. Bounded to
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

    @Nullable
    private static String stringParam(@Nullable JsonObject params, String name) {
        if (params == null || !params.has(name)) {
            return null;
        }
        JsonElement element = params.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /** The {@code pet} parameter as an entity UUID, or {@code null} if absent or malformed. */
    @Nullable
    private static UUID entityId(@Nullable JsonObject params) {
        String raw = stringParam(params, "pet");
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
