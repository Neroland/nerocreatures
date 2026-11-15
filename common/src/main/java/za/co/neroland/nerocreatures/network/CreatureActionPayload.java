package za.co.neroland.nerocreatures.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Client &rarr; server: "I, the owner, want action {@code action} done to my creature
 * {@code entityId}". The one serverbound payload on {@code nerocreatures:main}; features register
 * an action id in {@link CreatureActions} instead of adding payloads.
 *
 * <p>Carries two small integers and nothing else: the entity's session-local network id and an
 * action id. No UUID, name, position or free text (POPIA/GDPR). The server resolves and validates
 * the entity itself through {@link ServerPayloadGuard}; nothing here is trusted.
 *
 * @param entityId the target's {@code Entity#getId()} (network id, not persistent)
 * @param action   an id registered in {@link CreatureActions}
 */
public record CreatureActionPayload(int entityId, int action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreatureActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "creature_action"));

    /** Registry-free codec: two var-ints. Round-trip tested on a plain JVM. */
    public static final StreamCodec<ByteBuf, CreatureActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreatureActionPayload::entityId,
            ByteBufCodecs.VAR_INT, CreatureActionPayload::action,
            CreatureActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client side: asks the server to run {@code action} on the creature with this network id. */
    public static void send(int entityId, int action) {
        CreatureNetwork.sendToServer(new CreatureActionPayload(entityId, action));
    }
}
