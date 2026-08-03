package za.co.neroland.nerocreatures.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import za.co.neroland.nerocreatures.platform.Services;

/**
 * The cross-loader payload registry: NeroCreatures declares its payloads here once (type + stream
 * codec + client handler), and each loader module iterates the list and wires it to its own
 * networking API — NeoForge's {@code PayloadRegistrar}, Forge's {@code ChannelBuilder}, Fabric's
 * {@code PayloadTypeRegistry} + {@code ClientPlayNetworking}. Sending goes through the
 * {@link Services#NETWORK} seam. The channel is {@code nerocreatures:main}.
 *
 * <p>This is Neroland Core's {@code CoreNetwork} architecture reproduced on NeroCreatures' own
 * channel. It cannot reuse Core's instance: Core drains its payload lists during Core's own
 * bootstrap (on Forge the channel is {@code build()}-sealed inside Core's constructor), so a
 * downstream registration would be silently dropped — see
 * {@link za.co.neroland.nerocreatures.platform.NetworkPlatform} for the full reasoning.
 *
 * <p>NeroCreatures is server-authoritative end to end: spawning, AI, drops, taming and boss
 * contribution are all decided server-side and the client only renders. The list is therefore
 * legitimately <b>empty</b> at this stage — the framework exists so that the first client-facing
 * feature (boss bars beyond vanilla, pet status HUD) is a one-line addition rather than a
 * per-loader change. An empty list is a supported state: every loader's registration pass simply
 * iterates nothing.
 */
public final class CreatureNetwork {

    /** A server &rarr; client payload plus the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();

    private static boolean declared;

    private CreatureNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    /** Every declared server &rarr; client payload, for each loader's registration pass. */
    public static List<Clientbound<?>> clientbound() {
        return CLIENTBOUND;
    }

    /**
     * Declares the payloads. Called once from common init, before any loader registers them
     * (each loader entry point runs common init first, then its own network registration).
     */
    public static void init() {
        if (declared) {
            return; // defensive: a second call must not duplicate registrations
        }
        declared = true;
        // No payloads yet — see the class Javadoc. Declare them here, never from a loader module.
    }

    /**
     * Drops every client-side mirror. Each loader calls this when the client leaves a world or
     * server, so one session's state can never bleed into the next — or appear at all on a
     * server that does not run NeroCreatures. No-op while there are no client caches.
     */
    public static void clearClientCaches() {
        // Nothing mirrored on the client yet.
    }
}
