package za.co.neroland.nerocreatures.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

/** S3: abandoned summoned fights are pruned when the store is full, not only at server start. */
class BossFightStoreTest {

    private static final Identifier BOSS = Identifier.fromNamespaceAndPath("nerocreatures", "facet_colossus");

    @AfterEach
    void restoreClock() {
        BossContributionState.clock = System::currentTimeMillis;
    }

    @Test
    void fullStoreDropsAbandonedFightsToMakeRoom() {
        long[] now = {1_000_000_000L};
        BossContributionState.clock = () -> now[0];
        BossContributionState store = new BossContributionState();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 64; i++) {
            store.join(UUID.randomUUID(), BOSS, player);
        }
        now[0] += 7L * 3_600_000L; // every one of them abandoned for seven hours
        UUID fresh = UUID.randomUUID();
        store.record(fresh, BOSS, player, 5.0F);
        assertEquals(1, store.participantCount(fresh), "the new fight is recorded");
    }

    @Test
    void fullStoreOfLiveFightsStillRefuses() {
        long[] now = {1_000_000_000L};
        BossContributionState.clock = () -> now[0];
        BossContributionState store = new BossContributionState();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 64; i++) {
            store.join(UUID.randomUUID(), BOSS, player);
        }
        UUID fresh = UUID.randomUUID();
        store.record(fresh, BOSS, player, 5.0F);
        assertEquals(0, store.participantCount(fresh), "live fights are never dropped");
    }
}
