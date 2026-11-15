package za.co.neroland.nerocreatures.menu;

import java.util.OptionalInt;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * The one place NeroCreatures opens a container screen. Never call {@code player.openMenu(...)}
 * anywhere else; a reviewer greps for it.
 *
 * <ul>
 *   <li><b>Server only.</b> On the client (or for a non-server player) it does nothing and returns
 *       empty, so a caller in shared {@code use}/{@code interact} code needs no side check.</li>
 *   <li><b>Guarded.</b> {@code openMenu} runs third-party code (container listeners, and on hybrid
 *       servers a plugin bridge). A {@code RuntimeException} or {@code LinkageError} from it closes
 *       the half-open container, tells the player with a translated message, and is reported to
 *       telemetry with the menu's class name only. Never the player (POPIA/GDPR). Hybrid servers
 *       are not a supported platform; this guard only stops them crashing the caller.</li>
 * </ul>
 *
 * <p>Owner checks are the caller's job (an owner-only menu checks ownership before calling this, and
 * its menu's {@code stillValid} re-checks distance and ownership every tick).
 */
public final class MenuOpener {

    private MenuOpener() {
    }

    /**
     * Opens {@code provider} for {@code player} on the server.
     *
     * @return the container id, or empty when on the client, when the player could not open it, or
     *         when opening failed
     */
    public static OptionalInt open(Player player, MenuProvider provider) {
        if (!(player instanceof ServerPlayer serverPlayer) || provider == null) {
            return OptionalInt.empty();
        }
        try {
            return serverPlayer.openMenu(provider);
        } catch (RuntimeException | LinkageError e) {
            String menu = provider.getClass().getSimpleName();
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Opening menu {} failed: {}", menu,
                    e.getClass().getSimpleName());
            try {
                serverPlayer.closeContainer();
            } catch (RuntimeException | LinkageError ignored) {
                // Already broken; the message below is all we can still do.
            }
            serverPlayer.sendSystemMessage(Component.translatable("gui.nerocreatures.menu_open_failed"));
            NeroCreaturesTelemetry.captureHandledException(e, "menu", "open " + menu);
            return OptionalInt.empty();
        }
    }
}
