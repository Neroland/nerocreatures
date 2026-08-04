package za.co.neroland.nerocreatures.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;

import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.boss.BossFights;
import za.co.neroland.nerocreatures.boss.BossSummons;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;
import za.co.neroland.nerocreatures.spawn.SpawnRule;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * The {@code /nerocreatures} operator command tree — the server owner's view of, and lever on, the
 * creature layer. Registered identically from all three loaders (NeoForge/Forge
 * {@code RegisterCommandsEvent}, Fabric {@code CommandRegistrationCallback}), so the tree itself
 * lives here in common.
 *
 * <pre>
 * /nerocreatures list                     the roster, with what is loaded in your dimension
 * /nerocreatures caps                     population and ownership caps, and what is using them
 * /nerocreatures summon-boss &lt;boss&gt;       start a contribution-tracked fight where you stand
 * /nerocreatures export &lt;player&gt;          one player's stored NeroCreatures data, as JSON
 * /nerocreatures telemetry-test           fire one synthetic crash-reporting event
 * /nerocreatures gallery [clear]          build (or wipe) the creative showcase scene
 * </pre>
 *
 * <p>The whole tree is gated at Core's own admin level ({@code Commands.LEVEL_GAMEMASTERS}, i.e.
 * permission level 2), matching {@code /neroland} and {@code /neroquests}. {@code summon-boss} and
 * {@code export} genuinely need it; {@code list} and {@code caps} are harmless, and are held at the
 * same level only so that the tree has one consistent answer to "who may run this".
 * {@code gallery} is gated <em>further</em> — a player, in creative — because it rewrites a
 * hundred-block box; see {@link CreatureGallery}.
 *
 * <p><b>Arguments.</b> A player is a plain string — an online player's name <em>or</em> a raw UUID —
 * rather than an entity selector, because {@code export} must reach players who have <em>left</em>,
 * which a selector cannot. This is the same shape Core's {@code /neroland data erase &lt;uuid&gt;}
 * takes. A boss id without a namespace is read as {@code nerocreatures:}.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <ul>
 *   <li>Every {@code sendSuccess} passes {@code false} for "broadcast to ops", so output goes to the
 *       invoker alone and player-scoped results stay out of {@code latest.log} under the
 *       {@code logAdminCommands} game rule.</li>
 *   <li>Nothing here logs player identity, and no subcommand prints anything about a player other
 *       than the one named — {@code list} and {@code caps} report world state and configuration
 *       only, never who owns what.</li>
 *   <li><b>{@code export} is the documented data-access path.</b> It prints exactly one player's own
 *       ownership rows and their own boss contribution, and nobody else's — a property of the
 *       stores' {@code exportPlayer} methods, not of this presentation. It requires operator
 *       permission because it is somebody's personal record; a player asking for their own data asks
 *       an operator to run it, exactly as {@code PRIVACY.md} says.</li>
 * </ul>
 *
 * <p>Server thread only.
 */
public final class CreatureCommands {

    /** Chat is not a file transfer: an export longer than this is cut off with a note. */
    private static final int EXPORT_CHAR_LIMIT = 32_000;

    /** Upper bound on the entities {@code list} will count in one dimension. */
    private static final int LIST_SCAN_LIMIT = 2_048;

    private CreatureCommands() {
    }

    // --- tree ---------------------------------------------------------------

    /** Builds {@code /nerocreatures …}. Called once per loader from its command-registration hook. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerocreatures")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(ctx -> runSafely(ctx.getSource(), "list",
                                () -> list(ctx.getSource()))))
                .then(Commands.literal("caps")
                        .executes(ctx -> runSafely(ctx.getSource(), "caps",
                                () -> caps(ctx.getSource()))))
                .then(Commands.literal("summon-boss")
                        .then(bossArgument()
                                .executes(ctx -> runSafely(ctx.getSource(), "summon-boss",
                                        () -> summonBoss(ctx)))))
                .then(Commands.literal("export")
                        .then(playerArgument()
                                .executes(ctx -> runSafely(ctx.getSource(), "export",
                                        () -> export(ctx)))))
                .then(Commands.literal("telemetry-test")
                        .executes(ctx -> runSafely(ctx.getSource(), "telemetry-test",
                                () -> telemetryTest(ctx.getSource()))))
                // Creative-only showcase scene. The extra `requires` keeps it off a non-player
                // source's tab-completion entirely; the creative check itself lives in the body,
                // because a `requires` predicate is evaluated when the tree is sent to the client
                // rather than when the command runs.
                .then(Commands.literal("gallery")
                        .requires(src -> src.getPlayer() != null)
                        .executes(ctx -> runSafely(ctx.getSource(), "gallery",
                                () -> CreatureGallery.build(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> runSafely(ctx.getSource(), "gallery clear",
                                        () -> CreatureGallery.clear(ctx.getSource()))))));
    }

    /**
     * {@code <player>} — an online player's name or a raw UUID, suggesting the names of everyone
     * currently online.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    if (server != null) {
                        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                            String name = online.getName().getString();
                            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                                builder.suggest(name);
                            }
                        }
                    }
                    return builder.buildFuture();
                });
    }

    /**
     * {@code <boss>} — a summonable boss id, suggesting the registered set. Greedy because an id
     * contains {@code :}, which Brigadier will not read as a bare word, and because it is always the
     * last argument of its subcommand.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> bossArgument() {
        return Commands.argument("boss", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                    for (Identifier id : BossSummons.bossIds()) {
                        String text = id.toString();
                        if (text.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(text);
                        }
                    }
                    return builder.buildFuture();
                });
    }

    // --- list ---------------------------------------------------------------

    /**
     * The whole registered roster, each line carrying how many of that creature are loaded in the
     * invoker's own dimension and the spawn-table lines that place it.
     *
     * <p>The counts come from <b>two bounded queries</b> over the invoker's level (the mod's two
     * class roots, the same pair {@code spawn/CreatureCensus} counts), tallied by type — not one
     * query per creature, which would be twelve passes over the same entity index.
     */
    private static int list(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level == null) {
            return noServer(source);
        }
        Map<Identifier, Integer> loaded = countByType(level);
        List<SpawnRule> rules = CreatureSpawns.rules();
        String dimension = level.dimension().identifier().toString();

        int roster = ModEntities.roster().size();
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.list.header",
                roster, dimension), false);

        for (RegistryEntry<? extends EntityType<? extends Mob>> holder : ModEntities.roster()) {
            EntityType<? extends Mob> type = resolve(holder);
            if (type == null) {
                continue;
            }
            Identifier id = EntityType.getKey(type);
            int here = loaded.getOrDefault(id, 0);
            String spawns = spawnSummary(rules, id.getPath());
            String line = "  " + id + " §7— " + here + " here§r §8" + spawns + "§r";
            source.sendSuccess(() -> Component.literal(line), false);
        }

        int fights = BossFights.activeCount();
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.list.fights", fights),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code type id → how many are loaded}, over both of the mod's class roots. */
    private static Map<Identifier, Integer> countByType(ServerLevel level) {
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        List<NeroCreatureEntity> creatures = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(NeroCreatureEntity.class), creature -> true,
                creatures, LIST_SCAN_LIMIT);
        for (NeroCreatureEntity creature : creatures) {
            counts.merge(EntityType.getKey(creature.getType()), 1, Integer::sum);
        }
        List<TameableCreature> pets = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(TameableCreature.class), pet -> true, pets,
                LIST_SCAN_LIMIT);
        for (TameableCreature pet : pets) {
            counts.merge(EntityType.getKey(pet.getType()), 1, Integer::sum);
        }
        return counts;
    }

    /** {@code [dark_biomes w30 1-2, moon_biomes w24 2-4]}, or a note that it never spawns. */
    private static String spawnSummary(List<SpawnRule> rules, String creatureId) {
        StringBuilder out = new StringBuilder();
        for (SpawnRule rule : rules) {
            if (!rule.creatureId().equals(creatureId)) {
                continue;
            }
            out.append(out.isEmpty() ? "[" : ", ")
                    .append(rule.biomeTag().location().getPath())
                    .append(" w").append(rule.weight())
                    .append(' ').append(rule.minGroupSize()).append('-').append(rule.maxGroupSize());
        }
        return out.isEmpty() ? "[no natural spawn]" : out.append(']').toString();
    }

    // --- caps ---------------------------------------------------------------

    /** What the caps are set to and how much of them the invoker's dimension is using. */
    private static int caps(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level == null) {
            return noServer(source);
        }
        int chunkCap = NeroCreaturesConfig.MAX_CREATURES_PER_CHUNK.get();
        int dimensionCap = NeroCreaturesConfig.MAX_CREATURES_PER_DIMENSION.get();
        // Bounded and cached, exactly as the spawn sweep asks it — it counts up to the cap and
        // deliberately says nothing beyond it.
        int inDimension = CreatureCensus.inDimension(level);
        String dimension = level.dimension().identifier().toString();
        BlockPos here = BlockPos.containing(source.getPosition());
        int inChunk = CreatureCensus.countInChunk(level, here);

        source.sendSuccess(() -> Component.translatable("command.nerocreatures.caps.header"), false);
        source.sendSuccess(() -> Component.literal(
                "  §7dimension§r " + dimension + " — " + inDimension + " / " + dimensionCap), false);
        source.sendSuccess(() -> Component.literal(
                "  §7this chunk§r — " + inChunk + " / " + chunkCap), false);
        source.sendSuccess(() -> Component.literal(
                "  §7per player§r — pets " + NeroCreaturesConfig.MAX_PETS_PER_PLAYER.get()
                        + ", drones " + NeroCreaturesConfig.MAX_DRONES_PER_PLAYER.get()), false);
        source.sendSuccess(() -> Component.literal(
                "  §7spawns enabled§r — " + NeroCreaturesConfig.SPAWNS_ENABLED.get()
                        + " §7· weight ×§r" + NeroCreaturesConfig.GLOBAL_SPAWN_WEIGHT_MULTIPLIER.get()),
                false);
        return Command.SINGLE_SUCCESS;
    }

    // --- summon-boss ---------------------------------------------------------

    /**
     * Starts a contribution-tracked fight where the invoker is standing, through the same public
     * {@link BossSummons#summon} API a future NeroEvents raid will use — so what an operator tests
     * with this command is exactly what an event will do.
     *
     * <p>The invoker becomes the fight's initiator when they are a player, which registers them as a
     * zero-damage participant and nothing else. A summon from a command block or the console has no
     * initiator, which is a legitimate "nobody in particular started this".
     */
    private static int summonBoss(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (level == null) {
            return noServer(source);
        }
        String raw = StringArgumentType.getString(ctx, "boss").trim();
        Identifier bossId = parseBossId(raw);
        if (bossId == null || !BossSummons.isKnown(bossId)) {
            source.sendFailure(Component.translatable("command.nerocreatures.summon.unknown", raw));
            return 0;
        }
        ServerPlayer initiator = source.getPlayer();
        BlockPos pos = BlockPos.containing(source.getPosition());
        boolean summoned = BossSummons.summon(level, pos, bossId,
                initiator == null ? null : initiator.getUUID()).isPresent();
        if (!summoned) {
            source.sendFailure(Component.translatable("command.nerocreatures.summon.refused"));
            return 0;
        }
        String id = bossId.toString();
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.summon.success", id),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** Accept a bare path ("cinder_tyrant") as nerocreatures-namespaced, or a full "ns:path". */
    private static Identifier parseBossId(String raw) {
        return Identifier.tryParse(raw.indexOf(':') < 0
                ? NeroCreaturesCommon.MOD_ID + ":" + raw
                : raw);
    }

    // --- export --------------------------------------------------------------

    /**
     * POPIA/GDPR data access: prints exactly one player's own NeroCreatures rows as pretty JSON to
     * the invoker — their ownership index rows and their own boss contribution, and nothing that
     * belongs to anybody else.
     *
     * <p>Both halves come straight from the stores' own {@code exportPlayer} methods, so this
     * command cannot accidentally widen what an export contains; widening it would mean changing
     * the stores, which is where the guarantee is documented.
     */
    private static int export(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        UUID player = resolvePlayer(source, server, ctx);
        if (player == null) {
            return 0;
        }
        JsonObject json = new JsonObject();
        json.addProperty("player", player.toString());
        json.addProperty("exported_at", System.currentTimeMillis());
        json.add("ownership", CreatureOwnershipState.exportPlayer(server, player));
        json.add("boss_contribution", BossContributionState.exportPlayer(server, player));

        String subject = player.toString();
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.export.header",
                subject), false);

        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        boolean truncated = pretty.length() > EXPORT_CHAR_LIMIT;
        if (truncated) {
            pretty = pretty.substring(0, EXPORT_CHAR_LIMIT);
        }
        for (String line : pretty.split("\n", -1)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (truncated) {
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.export.truncated",
                    EXPORT_CHAR_LIMIT), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- telemetry-test -------------------------------------------------------

    /**
     * Fires one synthetic crash-reporting event, to confirm end-to-end reporting on a real jar.
     * Answers honestly when nothing was sent — which, in every build shipped so far, is the case:
     * the DSN is still a placeholder and telemetry is a hard no-op.
     */
    private static int telemetryTest(CommandSourceStack source) {
        if (NeroCreaturesTelemetry.sendTestEvent("command")) {
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.telemetry.sent"),
                    false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.translatable("command.nerocreatures.telemetry.inactive"));
        return 0;
    }

    // --- argument resolution --------------------------------------------------

    /**
     * The {@code player} argument as a UUID — an online player's name or a raw UUID — or
     * {@code null} after reporting that it named nobody. Offline players are reachable by UUID on
     * purpose: a data-access request must work for somebody who has left.
     */
    private static UUID resolvePlayer(CommandSourceStack source, MinecraftServer server,
            CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "player").trim();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getName().getString().equalsIgnoreCase(raw)) {
                return online.getUUID();
            }
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.nerocreatures.player.unknown"));
            return null;
        }
    }

    /** A roster entry's type, or {@code null} if it is not resolvable yet. */
    private static EntityType<? extends Mob> resolve(
            RegistryEntry<? extends EntityType<? extends Mob>> holder) {
        try {
            return holder.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int noServer(CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.nerocreatures.no_server"));
        return 0;
    }

    /**
     * Runs one subcommand body, turning an unexpected failure into a polite message plus an
     * anonymous telemetry event instead of a Brigadier stack trace in chat. The captured context is
     * the subcommand name only — never its arguments, which may name a player.
     */
    private static int runSafely(CommandSourceStack source, String subcommand, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException e) {
            NeroCreaturesTelemetry.captureHandledException(e, "command", "/nerocreatures " + subcommand);
            NeroCreaturesCommon.LOGGER.error("[NeroCreatures] /nerocreatures {} failed", subcommand, e);
            source.sendFailure(Component.translatable("command.nerocreatures.failed", subcommand));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandBody {

        int run();
    }
}
