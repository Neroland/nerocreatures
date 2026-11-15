package za.co.neroland.nerocreatures.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreatureActionPayloadCodecTest {

    @Test
    void roundTripsOrdinaryAndExtremeValues() {
        int[][] cases = {{0, 0}, {42, CreatureActions.MOUNT_OPEN_PANNIERS}, {-1, 63},
                {Integer.MAX_VALUE, Integer.MAX_VALUE}, {Integer.MIN_VALUE, 0}};
        for (int[] c : cases) {
            CreatureActionPayload in = new CreatureActionPayload(c[0], c[1]);
            assertEquals(in, PayloadCodecs.roundTrip(CreatureActionPayload.STREAM_CODEC, in));
        }
    }

    @Test
    void typeIdIsOnTheModNamespace() {
        assertEquals("nerocreatures:creature_action", CreatureActionPayload.TYPE.id().toString());
        assertEquals(CreatureActionPayload.TYPE, new CreatureActionPayload(1, 2).type());
    }

    @Test
    void initDeclaresTheServerboundPayloadOnce() {
        CreatureNetwork.init();
        CreatureNetwork.init();
        long count = CreatureNetwork.serverbound().stream()
                .filter(sb -> sb.type().equals(CreatureActionPayload.TYPE))
                .count();
        assertEquals(1L, count);
    }

    @Test
    void duplicateActionIdsAreRefused() {
        CreatureActions.register(1000, net.minecraft.world.entity.Entity.class, 4.0D, (p, e) -> { });
        assertTrue(CreatureActions.isRegistered(1000));
        assertThrows(IllegalStateException.class,
                () -> CreatureActions.register(1000, net.minecraft.world.entity.Entity.class, 4.0D, (p, e) -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> CreatureActions.register(-1, net.minecraft.world.entity.Entity.class, 4.0D, (p, e) -> { }));
    }
}
