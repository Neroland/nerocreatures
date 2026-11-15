package za.co.neroland.nerocreatures.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * The reward path's duplicate guards, at the store level: a fight's shares can be taken exactly once,
 * whichever of death (resolve), despawn/discard (discard) or an altar break happens first, and a fight
 * that was never summoned has nothing to pay. The altar never touches this store's rows.
 */
class BossRewardRacesTest {

    private static final Identifier BOSS = Identifier.fromNamespaceAndPath("nerocreatures", "facet_colossus");

    @Test
    void sharesResolveExactlyOnce() {
        BossContributionState store = new BossContributionState();
        UUID fight = UUID.randomUUID();
        UUID summoner = UUID.randomUUID();
        UUID fighter = UUID.randomUUID();
        store.join(fight, BOSS, summoner);
        store.record(fight, BOSS, fighter, 40.0F);
        List<BossContributionState.Share> first = store.resolve(fight);
        assertEquals(2, first.size());
        assertTrue(store.resolve(fight).isEmpty(), "a second death pays nothing");
        assertEquals(0, store.participantCount(fight), "the altar's live probe sees the fight is over");
    }

    @Test
    void discardThenDeathPaysNothing() {
        BossContributionState store = new BossContributionState();
        UUID fight = UUID.randomUUID();
        store.record(fight, BOSS, UUID.randomUUID(), 10.0F);
        store.discard(fight);
        assertTrue(store.resolve(fight).isEmpty());
    }

    @Test
    void deathThenRemovalDiscardIsHarmless() {
        BossContributionState store = new BossContributionState();
        UUID fight = UUID.randomUUID();
        store.record(fight, BOSS, UUID.randomUUID(), 10.0F);
        assertEquals(1, store.resolve(fight).size());
        store.discard(fight);
        assertTrue(store.resolve(fight).isEmpty());
    }

    @Test
    void unsummonedFightsRecordNothing() {
        BossContributionState store = new BossContributionState();
        UUID natural = UUID.randomUUID();
        assertTrue(store.resolve(natural).isEmpty(), "no row, no enhanced reward");
        assertEquals(0, store.participantCount(natural));
    }

    @Test
    void bossPressureChannelIsUnchanged() {
        assertEquals("nerocreatures:boss_pressure", BossController.PRESSURE_CHANNEL.toString());
    }
}
