package za.co.neroland.nerocreatures.command;

import com.mojang.brigadier.Command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.gallery.GalleryDemos;
import za.co.neroland.nerocreatures.gallery.GalleryDisplays;
import za.co.neroland.nerocreatures.gallery.GalleryTour;
import za.co.neroland.nerocreatures.gallery.MenagerieBuilder;
import za.co.neroland.nerocreatures.gallery.MenagerieLayout;

/**
 * The {@code /nerocreatures gallery} commands: the walkable <b>Menagerie</b> (pavilions per habitat
 * with signs and demo plates, a mount paddock, a trait wall, a boss hall, working rows of every block
 * and gear item), the compact {@code grid}, the {@code tour}, single {@code demo}s and {@code clear}.
 *
 * <p>Player-only and creative-only: the {@code /nerocreatures} tree's permission level, a
 * {@code requires} clause that rejects anything but a player, and a creative check in the body (a
 * {@code requires} predicate is evaluated when the tree is sent to the client, not when it runs).
 *
 * <p><b>Writes no player data.</b> Every display is created without {@code finalizeSpawn} (no boss
 * fight, bar, contribution row, cooldown or push), is never tamed or owned, and is tagged
 * {@code nerocreatures_gallery} so census, caps, statistics, scans, ownership and the link module
 * skip it. {@code clear} removes what the gallery placed <b>by tag</b> and leaves every other entity
 * (a player's own pet that walked in) alone. Chat lines count things and name nobody; nothing is
 * logged.
 *
 * <p>Server thread only.
 */
public final class CreatureGallery {

    private CreatureGallery() {
    }

    /** {@code gallery}: builds the Menagerie at the invoker's feet. */
    public static int build(CommandSourceStack source) {
        return buildLayout(source, false);
    }

    /** {@code gallery grid}: the compact flat layout. */
    public static int grid(CommandSourceStack source) {
        return buildLayout(source, true);
    }

    private static int buildLayout(CommandSourceStack source, boolean grid) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        if (MenagerieBuilder.busy()
                || GalleryDisplays.nearestAnchor(level, player.position(), MenagerieLayout.NO_STACK_RADIUS) != null) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.exists"));
            return 0;
        }
        BlockPos origin = player.blockPosition();
        // The work is spread over a few server ticks; the summary arrives when the last step has run.
        java.util.function.Consumer<MenagerieBuilder.Stats> done = stats -> report(source, origin, grid, stats);
        if (grid) {
            MenagerieBuilder.startGrid(level, origin, done);
        } else {
            MenagerieBuilder.startMenagerie(level, origin, done);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void report(CommandSourceStack source, BlockPos origin, boolean grid, MenagerieBuilder.Stats stats) {
        if (grid) {
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.gallery.grid_built",
                    stats.mobs(), stats.items()), false);
        } else {
            source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(),
                    String.format(java.util.Locale.ROOT, "tp @s %d.5 %d %d.5 180 0", origin.getX(),
                            origin.getY() + 1, origin.getZ()));
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.gallery.built",
                    stats.creatures(), stats.bossPoses(), stats.items(), stats.blocks()), false);
        }
        if (!stats.missing().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.gallery.missing",
                    String.join(", ", stats.missing())).withStyle(ChatFormatting.RED), false);
        }
        if (stats.overBudget()) {
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.gallery.over_budget",
                    stats.mobs(), stats.entities(), stats.blocks()).withStyle(ChatFormatting.RED), false);
        }
    }

    /** {@code gallery tour [n]}: goes to stop {@code n} (1..25) of the nearest Menagerie. */
    public static int tour(CommandSourceStack source, int n) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        GalleryDisplays.Anchor anchor = GalleryDisplays.nearestAnchor(level, player.position(),
                MenagerieLayout.ANCHOR_RADIUS);
        if (anchor == null || anchor.grid()) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.no_tour"));
            return 0;
        }
        if (n < 1 || n > MenagerieLayout.TOUR.size()) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.bad_stop",
                    MenagerieLayout.TOUR.size()));
            return 0;
        }
        GalleryTour.go(source, player, anchor.origin(), n);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code gallery demo <slot>}: plays one slot's demo, as its plate would. */
    public static int demo(CommandSourceStack source, String slot) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        GalleryDisplays.Anchor anchor = GalleryDisplays.nearestAnchor(level, player.position(),
                MenagerieLayout.ANCHOR_RADIUS);
        if (anchor == null || anchor.grid()) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.none"));
            return 0;
        }
        GalleryDemos.Result result = GalleryDemos.trigger(level, anchor.origin(), slot);
        if (result == GalleryDemos.Result.NO_SLOT || result == GalleryDemos.Result.MISSING) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.bad_slot", slot));
            return 0;
        }
        return result == GalleryDemos.Result.STARTED ? Command.SINGLE_SUCCESS : 0;
    }

    /**
     * {@code gallery clear}: removes the nearest gallery within 96 blocks: running demos stop, paddock
     * riders are put off, containers are emptied, blocks in the layout's clear box go to air, and every
     * <b>tagged</b> entity is discarded (never killed: no loot, no XP, no death event). Untagged
     * entities in the box stay. With no anchor, the 0.4.x box around the invoker is cleared the same way.
     */
    public static int clear(CommandSourceStack source) {
        ServerPlayer player = requireCreativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        GalleryDisplays.Anchor anchor = GalleryDisplays.nearestAnchor(level, player.position(),
                MenagerieLayout.ANCHOR_RADIUS);
        if (MenagerieBuilder.busy()) {
            source.sendFailure(Component.translatable("command.nerocreatures.gallery.busy"));
            return 0;
        }
        java.util.function.Consumer<int[]> done = result -> source.sendSuccess(() -> Component.translatable(
                "command.nerocreatures.gallery.cleared", result[0], result[1]), false);
        if (anchor != null) {
            GalleryDemos.stopAt(level, anchor.origin());
            MenagerieBuilder.startClear(level, anchor.origin(),
                    anchor.grid() ? MenagerieLayout.GRID_CLEAR_BOX : MenagerieLayout.CLEAR_BOX, done);
        } else {
            MenagerieBuilder.startClear(level, player.blockPosition(), MenagerieLayout.LEGACY_CLEAR_BOX, done);
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- helpers -------------------------------------------------------------------------------

    private static ServerLevel levelOf(ServerPlayer player) {
        //? if >=1.21.6 {
        return player.level();
        //?} else {
        /*return player.serverLevel();
        *///?}
    }

    /** The invoker, if a player in creative mode; otherwise {@code null} after saying why. */
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
}
