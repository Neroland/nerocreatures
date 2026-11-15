package za.co.neroland.nerocreatures.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocreatures.platform.Services;

/**
 * The cross-loader payload registry: NeroCreatures declares its payloads here once (type + stream
 * codec + handler), and each loader module iterates the lists and wires them to its own networking
 * API — NeoForge's {@code PayloadRegistrar}, Forge's {@code ChannelBuilder}, Fabric's
 * {@code PayloadTypeRegistry} + {@code Client/ServerPlayNetworking}. Sending goes through the
 * {@link Services#NETWORK} seam. The channel is {@code nerocreatures:main}.
 *
 * <p>This is Neroland Core's {@code CoreNetwork} architecture reproduced on NeroCreatures' own
 * channel. It cannot reuse Core's instance: Core drains its payload lists during Core's own
 * bootstrap (on Forge the channel is {@code build()}-sealed inside Core's constructor), so a
 * downstream registration would be silently dropped — see
 * {@link za.co.neroland.nerocreatures.platform.NetworkPlatform} for the full reasoning.
 *
 * <h2>Rules for a new payload</h2>
 *
 * <ul>
 *   <li>Declare it in {@link #init()} only, never from a loader module. A payload record lives in
 *       this package, owns a {@code TYPE} and a {@code STREAM_CODEC}, and gets a round-trip codec
 *       test (see {@code CreatureActionPayloadCodecTest}).</li>
 *   <li>Prefer a {@code StreamCodec<ByteBuf, T>} built from {@code ByteBufCodecs}: it needs no
 *       registry access, so the codec test runs on a plain JVM.</li>
 *   <li><b>Client &rarr; server:</b> the server trusts nothing in the payload. A handler that acts
 *       on a creature must resolve it through {@link ServerPayloadGuard} (owner, reach, liveness,
 *       rate limit) before touching it. The simplest way is not to add a payload at all and to
 *       register an action on {@link CreatureActionPayload} via {@link CreatureActions}, which does
 *       the guard call for you.</li>
 *   <li>No payload carries personal data it does not need: never a player name, UUID, coordinates
 *       or free text (POPIA/GDPR). Entity network ids are session-local integers and are fine.</li>
 * </ul>
 *
 * <p>Handlers run on the receiving side's main thread on every loader (NeoForge
 * {@code enqueueWork}, Forge {@code addMain}, Fabric {@code execute}).
 */
public final class CreatureNetwork {

    /** A server &rarr; client payload plus the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /**
     * A client &rarr; server payload plus the server-side handler. The handler gets the sending
     * player as the network layer identified it, never as the payload claims it.
     */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private static boolean declared;

    private CreatureNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    /**
     * Declares a client &rarr; server payload. The handler runs on the server thread; it must
     * validate everything it reads (see {@link ServerPayloadGuard}).
     */
    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    /** Every declared server &rarr; client payload, for each loader's registration pass. */
    public static List<Clientbound<?>> clientbound() {
        return Collections.unmodifiableList(CLIENTBOUND);
    }

    /** Every declared client &rarr; server payload, for each loader's registration pass. */
    public static List<Serverbound<?>> serverbound() {
        return Collections.unmodifiableList(SERVERBOUND);
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
        // Client -> server: one generic "owner acts on their creature" payload. Features add an
        // action id in CreatureActions rather than a new payload; the dispatcher runs the
        // ServerPayloadGuard before any action handler sees the entity.
        serverbound(CreatureActionPayload.TYPE, CreatureActionPayload.STREAM_CODEC,
                CreatureActions::handle);
        // The Bestiary (decision 010 section 9): one request answered with the sender's own counts,
        // and scan/tier events sent only to the player they are about. Client handlers go through
        // BestiaryClientHooks so this common class never names a client class.
        serverbound(BestiaryRequestPayload.TYPE, BestiaryRequestPayload.STREAM_CODEC,
                za.co.neroland.nerocreatures.bestiary.BestiaryProgress::handleRequest);
        clientbound(BestiaryCountsPayload.TYPE, BestiaryCountsPayload.STREAM_CODEC,
                payload -> za.co.neroland.nerocreatures.bestiary.BestiaryClientHooks.counts.accept(payload));
        clientbound(BestiaryEventPayload.TYPE, BestiaryEventPayload.STREAM_CODEC,
                payload -> za.co.neroland.nerocreatures.bestiary.BestiaryClientHooks.event.accept(payload));
    }

    /** Client side: sends a payload to the server (no-op on a server without the channel). */
    public static void sendToServer(CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }

    /**
     * Drops every client-side mirror. Each loader calls this when the client leaves a world or
     * server, so one session's state can never bleed into the next — or appear at all on a
     * server that does not run NeroCreatures. No-op while there are no client caches.
     */
    public static void clearClientCaches() {
        // The Bestiary's client state (counts, scan HUD, screen memory) is the only mirror.
        za.co.neroland.nerocreatures.bestiary.BestiaryClientHooks.clear.run();
    }
}
