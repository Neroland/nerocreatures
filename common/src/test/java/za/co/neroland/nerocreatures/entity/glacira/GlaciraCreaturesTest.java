package za.co.neroland.nerocreatures.entity.glacira;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.entity.ai.shared.AmbushRules;
import za.co.neroland.nerocreatures.entity.ai.shared.ProductTimer;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureGriefing.Change;
import za.co.neroland.nerocreatures.world.CreatureGriefingTest.FakeChecks;

/** Glacira creatures: Rime Yak and Frost Lurker ability rules and caps. */
public class GlaciraCreaturesTest {

    // --- Rime Yak: cold aura ---------------------------------------------------------------

    @Test
    void auraIsBoundedAndOnACadence() {
        assertTrue(RimeYakRules.inAura(0.0D));
        assertTrue(RimeYakRules.inAura(RimeYakRules.AURA_RADIUS * RimeYakRules.AURA_RADIUS));
        assertFalse(RimeYakRules.inAura(RimeYakRules.AURA_RADIUS * RimeYakRules.AURA_RADIUS + 0.01D));
        assertTrue(RimeYakRules.AURA_INTERVAL >= 40, "at most one frozen block per 2 s");
        assertTrue(RimeYakRules.MAX_AURA_TARGETS > 0 && RimeYakRules.MAX_AURA_TARGETS <= 16);
    }

    @Test
    void freezingIsCappedPerPassAndLive() {
        assertEquals(1, RimeYakRules.freezeBudget(true, 0, 0));
        assertEquals(0, RimeYakRules.freezeBudget(true, RimeYakRules.MAX_FREEZE_PER_PASS, 0), "one per pass");
        assertEquals(1, RimeYakRules.freezeBudget(true, 0, RimeYakRules.MAX_LIVE_FROSTED - 1));
        assertEquals(0, RimeYakRules.freezeBudget(true, 0, RimeYakRules.MAX_LIVE_FROSTED), "live cap");
        assertEquals(0, RimeYakRules.freezeBudget(true, 0, 999));
        // A long simulated stay never exceeds the live cap.
        int live = 0;
        for (int pass = 0; pass < 100; pass++) {
            live += RimeYakRules.freezeBudget(true, 0, live);
        }
        assertEquals(RimeYakRules.MAX_LIVE_FROSTED, live);
    }

    @Test
    void onlyStillOpenUnoccupiedWaterFreezes() {
        assertTrue(RimeYakRules.freezable(true, true, false));
        assertFalse(RimeYakRules.freezable(false, true, false), "not a still source");
        assertFalse(RimeYakRules.freezable(true, false, false), "not the surface");
        assertFalse(RimeYakRules.freezable(true, true, true), "something swims there");
    }

    @Test
    void deniedGriefingFreezesNothing() {
        FakeChecks noGrief = new FakeChecks();
        noGrief.mobGrief = false;
        assertEquals(0, RimeYakRules.freezeBudget(CreatureGriefing.decide(Change.REPLACE, noGrief), 0, 0));
        FakeChecks keyOff = new FakeChecks();
        keyOff.key = false; // rimeYakFreezesWater = false
        assertEquals(0, RimeYakRules.freezeBudget(CreatureGriefing.decide(Change.REPLACE, keyOff), 0, 0));
        FakeChecks claimed = new FakeChecks();
        claimed.place = false; // a claim mod cancelled the place event
        assertEquals(0, RimeYakRules.freezeBudget(CreatureGriefing.decide(Change.REPLACE, claimed), 0, 0));
        FakeChecks ownerAway = new FakeChecks();
        ownerAway.owner = false; // a leash holder who may not build here
        assertEquals(0, RimeYakRules.freezeBudget(CreatureGriefing.decide(Change.REPLACE, ownerAway), 0, 0));
        assertEquals(1, RimeYakRules.freezeBudget(CreatureGriefing.decide(Change.REPLACE, new FakeChecks()), 0, 0));
    }

    @Test
    void mobChillSlowsButNeverFullyFreezes() {
        int full = 140;
        assertEquals(RimeYakRules.MOB_CHILL_TICKS, RimeYakRules.chillTicks(0, full));
        assertTrue(RimeYakRules.chillTicks(0, full) < full);
        assertEquals(139, RimeYakRules.chillTicks(139, full), "keeps a higher value");
        assertEquals(200, RimeYakRules.chillTicks(200, full), "never thaws someone already frozen");
        assertEquals(49, RimeYakRules.chillTicks(0, 50), "always one short of a full freeze");
        // Repeated passes never cross the threshold.
        int t = 0;
        for (int i = 0; i < 50; i++) {
            t = RimeYakRules.chillTicks(t, full);
        }
        assertTrue(t < full);
    }

    // --- Rime Yak: wool and milk -------------------------------------------------------------

    @Test
    void woolRegrowsInStagesAndYieldIsCapped() {
        ProductTimer wool = new ProductTimer(RimeYakRules.woolRegrowTicks(0), RimeYakRules.WOOL_STAGES,
                RimeYakRules.WOOL_YIELD_CAP);
        assertEquals(RimeYakRules.MIN_WOOL_REGROW_TICKS, wool.regrowTicks(), "regrow floor");
        assertEquals(0, wool.stage(), "shorn");
        wool.advance(wool.regrowTicks());
        assertEquals(RimeYakRules.WOOL_STAGES, wool.stage());
        int got = wool.harvest(RimeYakRules.WOOL_BASE_YIELD,
                base -> RimeYakRules.woolYield(base, 99, RimeYakRules.WOOL_YIELD_CAP));
        assertEquals(RimeYakRules.WOOL_YIELD_CAP, got, "Bountiful never exceeds the cap");
        assertEquals(0, wool.stage(), "shorn again");
        assertEquals(0, wool.harvest(RimeYakRules.WOOL_BASE_YIELD, null), "nothing to shear");
        assertEquals(2, RimeYakRules.woolYield(2, 1, 6));
        assertEquals(0, RimeYakRules.woolYield(2, 1, 0));
    }

    @Test
    void milkHasACooldownAndBabiesAreNeverMilked() {
        assertTrue(RimeYakRules.mayMilk(false, 0));
        assertFalse(RimeYakRules.mayMilk(true, 0));
        int cd = RimeYakRules.milkCooldownTicks(1);
        assertEquals(RimeYakRules.MIN_MILK_COOLDOWN_TICKS, cd, "cooldown floor");
        assertFalse(RimeYakRules.mayMilk(false, cd));
        int steps = 0;
        while (!RimeYakRules.mayMilk(false, cd)) {
            cd = RimeYakRules.coolDown(cd, RimeYakRules.GROWTH_STEP);
            steps++;
        }
        assertEquals(RimeYakRules.MIN_MILK_COOLDOWN_TICKS / RimeYakRules.GROWTH_STEP, steps);
        assertEquals(0, RimeYakRules.coolDown(5, 20));
    }

    // --- Frost Lurker: ambush -------------------------------------------------------------------

    @Test
    void eruptsOnlyWhenWalkedOver() {
        double r = FrostLurkerRules.TRIGGER_RADIUS;
        assertTrue(AmbushRules.shouldErupt(0.0D, 0.0D, r));
        assertTrue(AmbushRules.shouldErupt(r * r, 1.0D, r));
        assertFalse(AmbushRules.shouldErupt(r * r + 0.1D, 0.0D, r), "beside it, not over it");
        assertFalse(AmbushRules.shouldErupt(0.0D, 3.0D, r), "flying over");
        assertFalse(AmbushRules.cooldownReady(100L, 0L, FrostLurkerRules.REBURY_COOLDOWN_TICKS));
        assertTrue(AmbushRules.cooldownReady(FrostLurkerRules.REBURY_COOLDOWN_TICKS, 0L,
                FrostLurkerRules.REBURY_COOLDOWN_TICKS));
    }

    @Test
    void eruptionIsTelegraphedAndCanBeDodged() {
        assertTrue(FrostLurkerRules.TELEGRAPH_TICKS >= 10, "contract: telegraph of at least 10 ticks");
        assertTrue(FrostLurkerRules.TELEGRAPH_TICKS < FrostLurkerRules.ERUPT_POSE_TICKS, "bite inside the pose");
        assertTrue(FrostLurkerRules.ERUPT_POSE_TICKS < 60, "contract: timed poses under 3 s");
        assertTrue(FrostLurkerRules.strikeLands(1.0D));
        assertFalse(FrostLurkerRules.strikeLands(FrostLurkerRules.STRIKE_REACH * FrostLurkerRules.STRIKE_REACH + 0.1D),
                "a player who stepped off in time is missed");
    }

    @Test
    void freezeIsAppliedAndCapped() {
        int full = 140;
        assertEquals(full + FrostLurkerRules.FREEZE_EXTRA_TICKS, FrostLurkerRules.freezeTicks(0, full));
        int t = 0;
        for (int i = 0; i < 20; i++) {
            t = FrostLurkerRules.freezeTicks(t + FrostLurkerRules.FREEZE_EXTRA_TICKS, full);
        }
        assertEquals(full + FrostLurkerRules.MAX_FREEZE_OVER_TICKS, t, "repeated bites never stack past the cap");
        assertEquals(0, FrostLurkerRules.freezeTicks(-5, -5) - FrostLurkerRules.FREEZE_EXTRA_TICKS);
    }

    @Test
    void chaseIsLeashedAndTimed() {
        assertFalse(FrostLurkerRules.dropTarget(4.0D, 10L));
        assertTrue(FrostLurkerRules.dropTarget(FrostLurkerRules.LEASH * FrostLurkerRules.LEASH + 1.0D, 10L));
        assertTrue(FrostLurkerRules.dropTarget(4.0D, FrostLurkerRules.MAX_CHASE_TICKS + 1L));
    }
}
