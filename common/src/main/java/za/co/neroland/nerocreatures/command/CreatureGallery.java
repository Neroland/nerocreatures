package za.co.neroland.nerocreatures.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * The {@code /nerocreatures gallery} showcase scene — a creative-only, entirely disposable set built
 * at the invoker's feet so every creature, the boss, the owned mobs and every item can be seen (and
 * photographed) in one place.
 *
 * <p>It exists for two audiences: a builder or pack maker who wants to look at the whole roster
 * without hunting for it, and the release process, which drives the client-side
 * {@code client/GalleryCaptureHarness} over this scene to re-render the Modrinth gallery
 * reproducibly after an art change.
 *
 * <h2>The scene</h2>
 *
 * <p>Four spokes around the invoker, each far enough out that a camera aimed at one has none of the
 * others in frame. The bearings and distances here are mirrored by the harness's camera positions —
 * <b>tune the two together</b>.
 *
 * <ul>
 *   <li><b>EAST — the roster.</b> Every entry of {@link ModEntities#roster()} except the boss and the
 *       Terraforming Drone (both of which get their own set piece), twice: a <em>frozen</em> row for
 *       clean shots and a <em>live</em> row behind glass. Each pair carries a floating label.</li>
 *   <li><b>NORTH — the boss arena.</b> An ember-and-ash ring with the Cinder Tyrant frozen on its
 *       dais. It is a <b>display spawn</b> and starts no fight — see below.</li>
 *   <li><b>SOUTH — pets and the drone.</b> Both tameable species, <em>untamed</em>, on their themed
 *       patches, and an <em>unowned</em> Terraforming Drone hovering over a work area.</li>
 *   <li><b>WEST — the items.</b> Every {@code nerocreatures} item — the eleven drops, the drone
 *       shell and every spawn egg — floating against a wall on invisible stands.</li>
 * </ul>
 *
 * <h2>What the gallery deliberately does not touch</h2>
 *
 * <p>The scene is built from vanilla blocks and entity spawns only. <b>It writes no player data of
 * any kind</b> (POPIA/GDPR), and that is a design constraint rather than a happy accident:
 *
 * <ul>
 *   <li><b>No boss contribution.</b> The Cinder Tyrant is created with
 *       {@code EntityType#create} and added to the level directly, <b>without</b> calling
 *       {@code finalizeSpawn}. That is what keeps it a display: {@code finalizeSpawn} is where
 *       {@code NeroBossEntity} starts the fight, and a started fight means a boss bar, a phase
 *       threshold crossing, a companion-app broadcast and a natural-boss-spawn cooldown for the whole
 *       dimension. None of that belongs in a photograph. Contribution itself is only ever turned on
 *       by {@code boss/BossSummons}, so no {@code BossContributionState} row can exist for a gallery
 *       boss even in principle — the gallery never calls it.
 *       <p><b>There is therefore no boss bar in the boss shot.</b> The bar is owned by the fight,
 *       and showing it would mean starting one. A bar was judged not worth polluting a live world's
 *       boss-spawn state for; run {@code /nerocreatures summon-boss cinder_tyrant} if a shot of the
 *       real bar is wanted.</li>
 *   <li><b>No ownership rows.</b> The pets are shown untamed and the drone unowned. Taming a display
 *       pet or deploying a display drone would write a {@code CreatureOwnershipState} row for the
 *       invoker and spend their pet/drone cap — real, erasable, exportable personal data created by
 *       a screenshot tool. The set piece says what it is with a label instead.</li>
 *   <li><b>No feedback about anybody.</b> The summary line counts blocks and entities and names
 *       nobody, and nothing here is logged.</li>
 * </ul>
 *
 * <h2>Safety</h2>
 *
 * <p>The subcommand is gated three ways: the {@code /nerocreatures} tree's own permission level, a
 * {@code requires} clause that rejects anything but a player, and a creative-mode check in the body.
 * A survival player cannot reach it even with operator permission, which is the point — this
 * bulldozes a hundred-block box.
 *
 * <p>{@code gallery clear} wipes that box (blocks to air, every non-player entity discarded) so a
 * rebuild — or a harness rerun — never stacks duplicates. Run it standing where the gallery was
 * built.
 *
 * <p>Server thread only.
 */
public final class CreatureGallery {

    // --- layout, relative to the invoker's block position -------------------
    // (fy = the invoker's Y; every pad is laid at fy and everything stands at fy + 1.)

    /** EAST spoke: the first creature cell, and the step between cells. */
    private static final int CREATURE_X = 14;
    private static final int CREATURE_Z = -6;
    private static final int CREATURE_SPACING = 4;

    /** How far south of the frozen row the live vivarium runs. */
    private static final int LIVE_ROW_OFFSET = 6;

    /** Height of the glass vivarium around the live row, in blocks. */
    private static final int VIVARIUM_HEIGHT = 4;

    /** Every displayed creature faces west, so a camera at the west end of the row sees its front. */
    private static final float DISPLAY_YAW = 90.0F;

    /** NORTH spoke: the boss arena centre and its radius. */
    private static final int BOSS_Z = -30;
    private static final int ARENA_RADIUS = 9;

    /** SOUTH spoke: the pets-and-drone pad. */
    private static final int PET_Z = 26;

    /** WEST spoke: the item wall plane, and the first display column. */
    private static final int ITEM_WALL_X = -34;
    private static final int ITEM_Z = -5;

    /** Items per display row, and the vertical step between rows. */
    private static final int ITEMS_PER_ROW = 6;
    private static final int ITEM_ROW_STEP = 2;
    private static final int ITEM_COLUMN_STEP = 2;

    /**
     * Scoreboard tag on every floating label stand. The capture harness strips the labels with
     * {@code kill @e[tag=…]} before a marketing shot, and it must not catch the item displays — which
     * are also invisible armour stands, so a "kill invisible armour stands" sweep would take them
     * too. Tagging is what tells the two apart.
     */
    public static final String LABEL_TAG = "nerocreatures_gallery_label";

    // --- the clear footprint (must contain every spoke, with margin) --------

    private static final int CLEAR_MIN_X = -40;
    private static final int CLEAR_MAX_X = 58;
    private static final int CLEAR_MIN_Z = -44;
    private static final int CLEAR_MAX_Z = 36;
    private static final int CLEAR_HEIGHT = 16;

    private CreatureGallery() {
    }

    // --- build ---------------------------------------------------------------

    /** Builds the whole scene at the invoker's feet. Creative players only. */
    public static int build(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int ox = origin.getX();
        int oz = origin.getZ();
        int fy = origin.getY();

        int creatures = buildCreatureRows(level, ox + CREATURE_X, fy, oz + CREATURE_Z);
        buildBossArena(level, ox, fy, oz + BOSS_Z);
        buildOwnedDisplays(level, ox, fy, oz + PET_Z);
        int items = buildItemWall(level, ox + ITEM_WALL_X, fy, oz + ITEM_Z);

        final int shownCreatures = creatures;
        final int shownItems = items;
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.gallery.built",
                shownCreatures, shownItems), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The roster, twice. The frozen row is the one the camera shoots: no AI, no gravity drift, no
     * sound and no damage, so the same shot renders identically every run. The live row is the same
     * creatures with their AI running, inside a glass vivarium — <b>the glass is the safety
     * feature</b>: a solid transparent block breaks line of sight, so a live hostile can neither
     * reach nor shoot the photographer, without the gallery having to rely on the world being on
     * Peaceful (which it usually is not, and which would also stop half the roster existing).
     *
     * @return how many creatures were displayed
     */
    private static int buildCreatureRows(ServerLevel level, int cx, int fy, int cz) {
        List<EntityType<? extends Mob>> roster = displayRoster();
        int liveZ = cz + LIVE_ROW_OFFSET;
        int lastX = cx + Math.max(0, roster.size() - 1) * CREATURE_SPACING;

        // One pad under both rows.
        fill(level, cx - 3, fy, cz - 3, lastX + 3, fy, liveZ + 3, Blocks.POLISHED_DEEPSLATE);
        // A tile strip down the middle so the two rows read as separate exhibits.
        fill(level, cx - 3, fy, cz + 3, lastX + 3, fy, cz + 3, Blocks.DEEPSLATE_TILES);

        // The vivarium: a glass corridor around the live row, closed on top so nothing that hops
        // (Rogue Drone) or climbs can get out and wander into another spoke's shot.
        int penMinZ = liveZ - 2;
        int penMaxZ = liveZ + 2;
        fill(level, cx - 2, fy + 1, penMinZ, lastX + 2, fy + VIVARIUM_HEIGHT, penMinZ, Blocks.GLASS);
        fill(level, cx - 2, fy + 1, penMaxZ, lastX + 2, fy + VIVARIUM_HEIGHT, penMaxZ, Blocks.GLASS);
        fill(level, cx - 2, fy + 1, penMinZ, cx - 2, fy + VIVARIUM_HEIGHT, penMaxZ, Blocks.GLASS);
        fill(level, lastX + 2, fy + 1, penMinZ, lastX + 2, fy + VIVARIUM_HEIGHT, penMaxZ, Blocks.GLASS);
        fill(level, cx - 2, fy + VIVARIUM_HEIGHT, penMinZ, lastX + 2, fy + VIVARIUM_HEIGHT, penMaxZ,
                Blocks.GLASS);

        int shown = 0;
        for (int i = 0; i < roster.size(); i++) {
            EntityType<? extends Mob> type = roster.get(i);
            int x = cx + i * CREATURE_SPACING;
            Mob frozen = spawnDisplay(level, type, new BlockPos(x, fy + 1, cz), true);
            spawnDisplay(level, type, new BlockPos(x, fy + 1, liveZ), false);
            if (frozen != null) {
                // Airborne creatures are pinned in the air rather than left resting on the pad: NoAI
                // stops the AI, not gravity.
                if (hovers(type)) {
                    frozen.setNoGravity(true);
                    frozen.snapTo(x + 0.5D, fy + 2.2D, cz + 0.5D, DISPLAY_YAW, 0.0F);
                }
                shown++;
            }
            label(level, new BlockPos(x, fy + 4, cz - 1), Component.translatable(type.getDescriptionId()));
        }
        label(level, new BlockPos(cx - 1, fy + 4, liveZ), Component.literal("Live (vivarium)"));
        return shown;
    }

    /**
     * The boss set piece: a ring of ember-world stone with the Cinder Tyrant frozen on the dais, at
     * the look it has the moment it arrives — which is phase one, because no phase has been entered.
     *
     * <p>The Tyrant here is created and added directly, never {@code finalizeSpawn}ed, so no fight
     * starts. See the class docs for why that matters and what it costs (no boss bar).
     */
    private static void buildBossArena(ServerLevel level, int bx, int fy, int bz) {
        BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState ash = Blocks.BASALT.defaultBlockState();
        BlockState ember = Blocks.NETHERRACK.defaultBlockState();
        BlockState rim = Blocks.BLACKSTONE.defaultBlockState();
        BlockState dais = Blocks.POLISHED_BASALT.defaultBlockState();

        int r2 = ARENA_RADIUS * ARENA_RADIUS;
        int inner = (ARENA_RADIUS - 1) * (ARENA_RADIUS - 1);
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r2) {
                    continue;
                }
                BlockPos pos = new BlockPos(bx + dx, fy, bz + dz);
                if (d2 <= 9) {
                    level.setBlockAndUpdate(pos, dais); // the 3-radius dais the boss stands on
                } else if (Math.floorMod(dx * 7 + dz * 13, 11) == 0) {
                    // A deterministic speckle rather than a random one: the same scene must rebuild
                    // identically, or a re-shot gallery would not match the last one.
                    level.setBlockAndUpdate(pos, ember);
                } else if (Math.floorMod(dx * 5 - dz * 3, 7) == 0) {
                    level.setBlockAndUpdate(pos, ash);
                } else {
                    level.setBlockAndUpdate(pos, floor);
                }
                if (d2 > inner) {
                    level.setBlockAndUpdate(pos.above(), rim); // a one-block lip around the arena
                }
            }
        }
        // Four corner pillars, capped with glowstone for the ember light.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = bx + sx * 6;
                int pz = bz + sz * 6;
                fill(level, px, fy + 1, pz, px, fy + 4, pz, Blocks.CRYING_OBSIDIAN);
                level.setBlockAndUpdate(new BlockPos(px, fy + 5, pz), Blocks.GLOWSTONE.defaultBlockState());
            }
        }

        NeroBossEntity boss = displayBoss(level, new BlockPos(bx, fy + 1, bz));
        if (boss != null) {
            label(level, new BlockPos(bx, fy + 7, bz),
                    Component.translatable(ModEntities.CINDER_TYRANT.get().getDescriptionId()));
        }
    }

    /**
     * The owned-mob spoke: both pet species and the Terraforming Drone.
     *
     * <p>All three are shown <b>unowned</b>. Taming a pet writes a {@code CreatureOwnershipState}
     * row against the invoker and spends one of their {@code maxPetsPerPlayer} slots; deploying a
     * drone does the same against {@code maxDronesPerPlayer}. Neither is something a screenshot
     * command should do to somebody's save file, so the display is the creature and a label saying
     * what it becomes.
     */
    private static void buildOwnedDisplays(ServerLevel level, int ox, int fy, int pz) {
        fill(level, ox - 14, fy, pz - 6, ox + 12, fy, pz + 6, Blocks.POLISHED_DEEPSLATE);

        // Glacite Wisp — a frost patch.
        patch(level, ox - 9, fy, pz, 2, Blocks.PACKED_ICE);
        patch(level, ox - 9, fy, pz, 1, Blocks.BLUE_ICE);
        Mob wisp = spawnDisplay(level, ModEntities.GLACITE_WISP.get(), new BlockPos(ox - 9, fy + 1, pz), true);
        if (wisp != null) {
            wisp.setNoGravity(true);
            wisp.snapTo(ox - 9 + 0.5D, fy + 2.2D, pz + 0.5D, DISPLAY_YAW, 0.0F);
        }
        label(level, new BlockPos(ox - 9, fy + 4, pz),
                Component.translatable(ModEntities.GLACITE_WISP.get().getDescriptionId()));

        // Xertz Forager — a moss patch.
        patch(level, ox - 1, fy, pz, 2, Blocks.MOSS_BLOCK);
        patch(level, ox - 1, fy, pz, 1, Blocks.GRASS_BLOCK);
        spawnDisplay(level, ModEntities.XERTZ_FORAGER.get(), new BlockPos(ox - 1, fy + 1, pz), true);
        label(level, new BlockPos(ox - 1, fy + 4, pz),
                Component.translatable(ModEntities.XERTZ_FORAGER.get().getDescriptionId()));

        // Terraforming Drone — a work area to hover over. The grass goes down before the saplings,
        // or the saplings would pop straight back off an invalid support.
        patch(level, ox + 8, fy, pz, 3, Blocks.GRASS_BLOCK);
        level.setBlockAndUpdate(new BlockPos(ox + 6, fy + 1, pz - 2), Blocks.OAK_SAPLING.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(ox + 10, fy + 1, pz + 2), Blocks.OAK_SAPLING.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(ox + 9, fy + 1, pz + 3), Blocks.OAK_SAPLING.defaultBlockState());
        Mob drone = spawnDisplay(level, ModEntities.TERRAFORMING_DRONE.get(),
                new BlockPos(ox + 8, fy + 1, pz), true);
        if (drone != null) {
            drone.setNoGravity(true);
            drone.snapTo(ox + 8 + 0.5D, fy + 2.4D, pz + 0.5D, DISPLAY_YAW, 0.0F);
        }
        label(level, new BlockPos(ox + 8, fy + 5, pz),
                Component.translatable(ModEntities.TERRAFORMING_DRONE.get().getDescriptionId()));
    }

    /**
     * Every item this mod registers, floating in a grid against a wall: the drops and the drone shell
     * on the lower rows, the spawn eggs above them.
     *
     * <p>The list comes from walking the vanilla item registry filtered to this mod's namespace
     * rather than from {@code ModItems}/{@code ModSpawnEggs}' own lists — Core's cross-loader
     * {@code RegistrationProvider} has no entry iteration, and a registry walk cannot miss an item
     * somebody adds without remembering this class.
     *
     * @return how many items were displayed
     */
    private static int buildItemWall(ServerLevel level, int wx, int fy, int wz) {
        List<Item> drops = new ArrayList<>();
        List<Item> eggs = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!NeroCreaturesCommon.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            (id.getPath().endsWith("_spawn_egg") ? eggs : drops).add(item);
        }
        Comparator<Item> byPath = Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath());
        drops.sort(byPath);
        eggs.sort(byPath);

        int dropRows = rowsFor(drops.size());
        int eggRows = rowsFor(eggs.size());
        int columns = Math.max(1, Math.min(ITEMS_PER_ROW, Math.max(drops.size(), eggs.size())));
        int maxZ = wz + (columns - 1) * ITEM_COLUMN_STEP;
        int topY = fy + 1 + (dropRows + eggRows) * ITEM_ROW_STEP;

        // Backdrop wall + the strip of floor in front of it.
        fill(level, wx, fy, wz - 2, wx, topY, maxZ + 2, Blocks.DEEPSLATE_TILES);
        fill(level, wx + 1, fy, wz - 2, wx + 6, fy, maxZ + 2, Blocks.POLISHED_DEEPSLATE);

        placeItemGrid(level, wx + 1, fy + 1, wz, drops);
        placeItemGrid(level, wx + 1, fy + 1 + dropRows * ITEM_ROW_STEP, wz, eggs);
        label(level, new BlockPos(wx + 1, fy + 1 + dropRows * ITEM_ROW_STEP - 1, wz - 2),
                Component.literal("Drops"));
        label(level, new BlockPos(wx + 1, topY - 1, wz - 2), Component.literal("Spawn eggs"));
        return drops.size() + eggs.size();
    }

    /** Lays one item group out in rows of {@value #ITEMS_PER_ROW}, growing upward from {@code y0}. */
    private static void placeItemGrid(ServerLevel level, int x, int y0, int z0, List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int column = i % ITEMS_PER_ROW;
            itemDisplay(level, new BlockPos(x, y0 + row * ITEM_ROW_STEP, z0 + column * ITEM_COLUMN_STEP),
                    new ItemStack(items.get(i)));
        }
    }

    private static int rowsFor(int count) {
        return Math.max(1, (count + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW);
    }

    // --- clear ---------------------------------------------------------------

    /**
     * Wipes the gallery footprint so a rebuild — or a harness rerun — cannot stack duplicates.
     * Clears from the pad layer ({@code origin.y}) upward, which leaves whatever was at
     * {@code origin.y - 1} intact, and discards every non-player entity in the box.
     */
    public static int clear(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int minX = origin.getX() + CLEAR_MIN_X;
        int maxX = origin.getX() + CLEAR_MAX_X;
        int minZ = origin.getZ() + CLEAR_MIN_Z;
        int maxZ = origin.getZ() + CLEAR_MAX_Z;
        int minY = origin.getY();
        int maxY = origin.getY() + CLEAR_HEIGHT;

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, 2); // flag 2 = tell clients, skip the neighbour cascade
                        cleared++;
                    }
                }
            }
        }

        AABB box = new AABB(minX, minY - 1, minZ, maxX + 1, maxY + 4, maxZ + 1);
        int removed = 0;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
            entity.discard();
            removed++;
        }

        final int clearedBlocks = cleared;
        final int removedEntities = removed;
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.gallery.cleared",
                clearedBlocks, removedEntities), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- helpers -------------------------------------------------------------

    /**
     * The invoker, if they are a player in creative mode; otherwise {@code null} after saying why.
     * Creative is checked here rather than in {@code requires} because a {@code requires} predicate
     * is evaluated when the command tree is sent to the client, not when the command runs — the
     * gamemode at parse time is the one that matters.
     */
    @Nullable
    private static ServerPlayer requireCreativePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.player_only"));
            return null;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.creative_only"));
            return null;
        }
        return player;
    }

    /** The roster minus the two entries that have their own set piece. */
    private static List<EntityType<? extends Mob>> displayRoster() {
        EntityType<?> boss = resolve(ModEntities.CINDER_TYRANT);
        EntityType<?> drone = resolve(ModEntities.TERRAFORMING_DRONE);
        List<EntityType<? extends Mob>> types = new ArrayList<>();
        for (RegistryEntry<? extends EntityType<? extends Mob>> holder : ModEntities.roster()) {
            EntityType<? extends Mob> type = resolve(holder);
            if (type != null && type != boss && type != drone) {
                types.add(type);
            }
        }
        return types;
    }

    @Nullable
    private static <T extends EntityType<?>> T resolve(RegistryEntry<T> holder) {
        try {
            return holder.get();
        } catch (RuntimeException e) {
            return null; // not registered yet on this loader — the gallery simply skips it
        }
    }

    /**
     * Places one creature.
     *
     * <p>A frozen display is inert on purpose: {@code setNoAi} stops the goal selectors (so nothing
     * wanders out of frame, and nothing targets the photographer), {@code setPersistenceRequired}
     * stops it despawning mid-shoot, {@code setInvulnerable} stops fall/fire/cactus damage from
     * quietly killing an exhibit, and {@code setSilent} keeps a hall of hostiles from being
     * unbearable to stand in.
     *
     * <p>A live display keeps its AI but is still invulnerable and persistent — it is contained by
     * the vivarium glass, not by being harmless.
     */
    @Nullable
    private static Mob spawnDisplay(ServerLevel level, @Nullable EntityType<? extends Mob> type,
            BlockPos pos, boolean frozen) {
        if (type == null) {
            return null;
        }
        Mob mob = type.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return null;
        }
        mob.setPersistenceRequired();
        mob.setInvulnerable(true);
        mob.setTarget(null);
        mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, DISPLAY_YAW, 0.0F);
        mob.setYBodyRot(DISPLAY_YAW);
        mob.setYHeadRot(DISPLAY_YAW);
        if (frozen) {
            mob.setNoAi(true);
            mob.setSilent(true);
        }
        return mob;
    }

    /**
     * Places the boss as a <b>display</b>: created and added to the level directly, with no
     * {@code finalizeSpawn} call, so {@code NeroBossEntity} never starts a fight. No bar, no phase
     * crossing, no companion broadcast, no dimension-wide boss cooldown and — by construction — no
     * contribution row.
     */
    @Nullable
    private static NeroBossEntity displayBoss(ServerLevel level, BlockPos pos) {
        EntityType<? extends NeroBossEntity> type = resolve(ModEntities.CINDER_TYRANT);
        if (type == null) {
            return null;
        }
        NeroBossEntity boss = type.create(level, EntitySpawnReason.COMMAND);
        if (boss == null) {
            return null;
        }
        boss.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, DISPLAY_YAW, 0.0F);
        boss.setYBodyRot(DISPLAY_YAW);
        boss.setYHeadRot(DISPLAY_YAW);
        boss.setNoAi(true);
        boss.setSilent(true);
        boss.setInvulnerable(true);
        boss.setPersistenceRequired();
        return level.addFreshEntity(boss) ? boss : null;
    }

    /** A floating cluster label: an invisible, weightless, tagged armour stand with a visible name. */
    private static void label(ServerLevel level, BlockPos pos, Component name) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.addTag(LABEL_TAG);
        level.addFreshEntity(stand);
    }

    /**
     * One floating item: an invisible armour stand wearing the stack on its head. The head slot is
     * used rather than a hand because a stand only renders held items when it has arms, which is
     * off by default, while a "hat" renders on every stand.
     */
    private static void itemDisplay(ServerLevel level, BlockPos pos, ItemStack stack) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        stand.setItemSlot(EquipmentSlot.HEAD, stack);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        level.addFreshEntity(stand);
    }

    /** Whether this creature reads better pinned in the air than resting on the pad. */
    private static boolean hovers(EntityType<? extends Mob> type) {
        return type == resolve(ModEntities.GLACITE_WISP) || type == resolve(ModEntities.ROGUE_DRONE);
    }

    /** A square patch of {@code block}, {@code radius} either side of {@code (x, z)}. */
    private static void patch(ServerLevel level, int x, int y, int z, int radius, Block block) {
        fill(level, x - radius, y, z - radius, x + radius, y, z + radius, block);
    }

    /** An inclusive box of one block type. Coordinates may be given in any order. */
    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2,
            Block block) {
        BlockState state = block.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    cursor.set(x, y, z);
                    level.setBlockAndUpdate(cursor, state);
                }
            }
        }
    }
}
