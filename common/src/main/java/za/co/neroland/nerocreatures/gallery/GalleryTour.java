package za.co.neroland.nerocreatures.gallery;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Menagerie tour. Stateless: the server keeps no record of where a player is in the tour; each
 * {@code tour <n>} teleports the invoker to stop {@code n} and sends one chat line with Back and Next
 * buttons. Nothing is logged.
 */
public final class GalleryTour {

    private GalleryTour() {
    }

    /** Teleports {@code player} (the command's source) to stop {@code n} of the gallery at {@code origin}. */
    public static void go(CommandSourceStack source, ServerPlayer player, BlockPos origin, int n) {
        MenagerieLayout.TourStop stop = MenagerieLayout.TOUR.get(n - 1);
        String tp = String.format(Locale.ROOT, "tp @s %.1f %d %.1f %.1f 0", origin.getX() + stop.x() + 0.5D,
                origin.getY() + 1, origin.getZ() + stop.z() + 0.5D, stop.yaw());
        source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), tp);
        Component caption = Component.translatable("gallery.nerocreatures.tour." + stop.id());
        int total = MenagerieLayout.TOUR.size();
        MutableComponent line = Component.literal("[" + n + "/" + total + "] ").append(caption);
        if (n > 1) {
            line.append(" ").append(button("command.nerocreatures.gallery.tour.back", n - 1));
        }
        if (n < total) {
            line.append(" ").append(button("command.nerocreatures.gallery.tour.next", n + 1));
        }
        line.append(" ").append(Component.translatable("command.nerocreatures.gallery.tour.leave")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(line);
        player.sendSystemMessage(caption, true);
    }

    private static Component button(String key, int stop) {
        String command = "/nerocreatures gallery tour " + stop;
        //? if >=1.21.5 {
        ClickEvent click = new ClickEvent.RunCommand(command);
        //?} else {
        /*ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .withStyle(style -> style.withClickEvent(click).withColor(ChatFormatting.AQUA));
    }
}
