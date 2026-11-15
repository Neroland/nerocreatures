package za.co.neroland.nerocreatures.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.world.CreatureGriefing.Change;

/**
 * The griefing decision order with a fake platform. Stage 2 ability tests reuse {@link FakeChecks}
 * to prove each ability's denied case.
 */
public class CreatureGriefingTest {

    /** A fake world: each check's answer, plus the order in which they were consulted. */
    public static final class FakeChecks implements CreatureGriefing.Checks {
        public boolean key = true;
        public boolean mobGrief = true;
        public boolean loaded = true;
        public boolean place = true;
        public boolean owner = true;
        public final List<String> calls = new ArrayList<>();

        @Override
        public boolean keyEnabled() {
            calls.add("key");
            return key;
        }

        @Override
        public boolean canMobGrief() {
            calls.add("mobGrief");
            return mobGrief;
        }

        @Override
        public boolean loadedAndInBorder() {
            calls.add("loaded");
            return loaded;
        }

        @Override
        public boolean mayPlace() {
            calls.add("place");
            return place;
        }

        @Override
        public boolean ownerPermits() {
            calls.add("owner");
            return owner;
        }
    }

    @Test
    void allowsWhenEveryCheckPasses() {
        for (Change change : Change.values()) {
            assertTrue(CreatureGriefing.decide(change, new FakeChecks()), change.name());
        }
    }

    @Test
    void configKeyOffDeniesBeforeAnythingElse() {
        FakeChecks c = new FakeChecks();
        c.key = false;
        assertFalse(CreatureGriefing.decide(Change.PLACE, c));
        assertEquals(List.of("key"), c.calls);
    }

    @Test
    void mobGriefingDeniedStopsBeforeTheWorldIsTouched() {
        FakeChecks c = new FakeChecks();
        c.mobGrief = false;
        assertFalse(CreatureGriefing.decide(Change.REPLACE, c));
        assertEquals(List.of("key", "mobGrief"), c.calls);
    }

    @Test
    void unloadedOrOutsideBorderNeverFiresThePlaceEvent() {
        FakeChecks c = new FakeChecks();
        c.loaded = false;
        assertFalse(CreatureGriefing.decide(Change.PLACE, c));
        assertEquals(List.of("key", "mobGrief", "loaded"), c.calls);
    }

    @Test
    void cancelledPlaceEventDeniesBlockChangesOnly() {
        FakeChecks place = new FakeChecks();
        place.place = false;
        assertFalse(CreatureGriefing.decide(Change.PLACE, place));
        assertEquals(List.of("key", "mobGrief", "loaded", "place"), place.calls);

        FakeChecks replace = new FakeChecks();
        replace.place = false;
        assertFalse(CreatureGriefing.decide(Change.REPLACE, replace));

        FakeChecks item = new FakeChecks();
        item.place = false;
        assertTrue(CreatureGriefing.decide(Change.ITEM, item));
        assertEquals(List.of("key", "mobGrief", "loaded", "owner"), item.calls);

        FakeChecks entity = new FakeChecks();
        entity.place = false;
        assertTrue(CreatureGriefing.decide(Change.ENTITY, entity));
    }

    @Test
    void ownedActorWithoutOwnerPermissionIsDeniedLast() {
        for (Change change : Change.values()) {
            FakeChecks c = new FakeChecks();
            c.owner = false;
            assertFalse(CreatureGriefing.decide(change, c), change.name());
            assertEquals("owner", c.calls.get(c.calls.size() - 1));
        }
    }

    @Test
    void placesBlockMatchesTheChangeTable() {
        assertTrue(Change.PLACE.placesBlock());
        assertTrue(Change.REPLACE.placesBlock());
        assertFalse(Change.ITEM.placesBlock());
        assertFalse(Change.ENTITY.placesBlock());
    }
}
