package za.co.neroland.nerocreatures.link;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import org.jetbrains.annotations.Nullable;

/**
 * The three questions the link surfaces have to answer before they may say anything: <em>which
 * server is running</em>, <em>is this player online</em>, and <em>what is this creature called</em>.
 *
 * <p>Core's snapshot/action API hands over a {@link UUID} and nothing else — no server, no player —
 * so the running server is taken from {@code spawn/CreatureSpawns}, which already tracks it for the
 * placement sweep's clock. Before the first server tick there is no server and every section
 * answers empty, which is the honest result rather than a guess.
 *
 * <p><b>Names.</b> A companion client wants something to print, and a dedicated server has no
 * client lang file to resolve {@code entity.nerocreatures.void_crawler} with — so
 * {@link #readableName(EntityType)} title-cases the registry path instead
 * ({@code void_crawler} &rarr; {@code Void Crawler}). Snapshots additionally carry the raw
 * translation key so an app that has the lang file can do better; alert texts, which are plain
 * strings by Core's contract, use this.
 *
 * <p>Server thread only. Nothing here reads or stores player data beyond the UUID it is handed.
 */
final class CreatureLinkAccess {

    private CreatureLinkAccess() {
    }

    /** The running server, or {@code null} before the first server tick / after shutdown. */
    @Nullable
    static MinecraftServer server() {
        return za.co.neroland.nerocreatures.spawn.CreatureSpawns.currentServer();
    }

    /** The online player with this UUID, or {@code null} if they are not connected. */
    @Nullable
    static ServerPlayer online(MinecraftServer server, UUID playerId) {
        return server.getPlayerList().getPlayer(playerId);
    }

    /** Whether this player is online right now. */
    static boolean isOnline(MinecraftServer server, UUID playerId) {
        return online(server, playerId) != null;
    }

    /**
     * A printable English name for a creature, derived from its registry path — never from a lang
     * file, which a dedicated server does not have for a mod's assets.
     */
    static String readableName(EntityType<?> type) {
        return readablePath(EntityType.getKey(type).getPath());
    }

    /** {@code cinder_tyrant} &rarr; {@code Cinder Tyrant}. */
    static String readablePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        boolean capitalise = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '/') {
                out.append(' ');
                capitalise = true;
            } else if (capitalise) {
                out.append(Character.toUpperCase(c));
                capitalise = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim().isEmpty() ? path.toLowerCase(Locale.ROOT) : out.toString();
    }
}
