package za.co.neroland.nerocreatures.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.data.CreatureData;

/**
 * POPIA/GDPR for the boss contribution store (fight records): retention honours Core's
 * {@code dataRetentionDays}, abandoned fights go after six hours regardless, and erasure leaves the
 * other participants' shares intact.
 */
class BossContributionPrivacyTest {

    private static final long DAY = 86_400_000L;
    private static final Identifier BOSS = Identifier.fromNamespaceAndPath("nerocreatures", "cinder_tyrant");

    private final long[] now = {1_800_000_000_000L};

    @AfterEach
    void restoreClock() {
        BossContributionState.clock = System::currentTimeMillis;
    }

    @Test
    void staleContributionIsPurgedByRetention() {
        BossContributionState.clock = () -> this.now[0];
        BossContributionState state = new BossContributionState();
        UUID stale = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        UUID oldFight = UUID.randomUUID();
        state.record(oldFight, BOSS, stale, 5.0F);
        this.now[0] += 20 * DAY;
        state.record(UUID.randomUUID(), BOSS, fresh, 5.0F);
        this.now[0] += 15 * DAY;
        assertEquals(0, CreatureData.applyContributionRetention(state, 0), "retention off: nothing purged");
        assertEquals(1, CreatureData.applyContributionRetention(state, 30));
        assertFalse(state.knowsPlayer(stale));
        assertEquals(0, state.participantCount(oldFight), "a fight nobody is left in is dropped");
        assertTrue(state.knowsPlayer(fresh));
    }

    @Test
    void abandonedFightsGoAfterSixHoursWhateverTheRetentionSetting() {
        BossContributionState.clock = () -> this.now[0];
        BossContributionState state = new BossContributionState();
        UUID player = UUID.randomUUID();
        UUID fight = UUID.randomUUID();
        state.record(fight, BOSS, player, 9.0F);
        this.now[0] += 7L * 3_600_000L;
        assertEquals(1, state.pruneStaleFights());
        assertEquals(0, state.participantCount(fight));
    }

    @Test
    void erasureKeepsTheOtherParticipantsShare() {
        BossContributionState state = new BossContributionState();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID fight = UUID.randomUUID();
        state.record(fight, BOSS, alice, 10.0F);
        state.record(fight, BOSS, bob, 30.0F);
        assertTrue(state.forgetPlayer(alice));
        assertFalse(state.knowsPlayer(alice));
        assertEquals(1.0F, state.shareOf(fight, bob), "Bob now holds the whole remaining fight");
        assertFalse(state.export(bob).toString().contains(alice.toString()));
    }
}
