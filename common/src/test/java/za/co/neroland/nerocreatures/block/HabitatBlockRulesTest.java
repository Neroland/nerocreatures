package za.co.neroland.nerocreatures.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import za.co.neroland.nerocreatures.block.FieldRules.Field;
import za.co.neroland.nerocreatures.block.FieldRules.Kind;
import za.co.neroland.nerocreatures.trait.TraitSet;

/** Habitat blocks: the pure rule of every block, its bounds and its caps. */
class HabitatBlockRulesTest {

    // --- Incubator ----------------------------------------------------------------------------

    /** Runs pulses until the egg is ready; returns the ticks it took. */
    private static long ticksToHatch(long extractedPerPulse, int energyPerTick) {
        int pulse = IncubatorRules.PULSE_TICKS;
        long wanted = IncubatorRules.energyWanted(pulse, energyPerTick);
        long progress = 0L;
        long ticks = 0L;
        while (!IncubatorRules.ready(progress, IncubatorRules.DEFAULT_HATCH_TICKS)) {
            long got = Math.min(extractedPerPulse, wanted);
            progress += IncubatorRules.progressFor(pulse, got, wanted, IncubatorRules.DEFAULT_HATCH_TICKS,
                    IncubatorRules.DEFAULT_POWERED_HATCH_TICKS);
            ticks += pulse;
            if (ticks > 1_000_000L) {
                throw new AssertionError("never hatched");
            }
        }
        return ticks;
    }

    @Test
    void incubatorUnpoweredHatchesInTwentyMinutes() {
        assertEquals(24_000L, ticksToHatch(0L, IncubatorRules.DEFAULT_ENERGY_PER_TICK));
    }

    @Test
    void incubatorWithNoPowerSurfaceIsNeverStalled() {
        // energyPerTick 0 = power switched off: nothing is asked for, the unpowered rate applies.
        assertEquals(0L, IncubatorRules.energyWanted(IncubatorRules.PULSE_TICKS, 0));
        assertEquals(24_000L, ticksToHatch(Long.MAX_VALUE, 0));
        assertEquals(IncubatorRules.PULSE_TICKS, IncubatorRules.progressFor(IncubatorRules.PULSE_TICKS, 0L, 400L,
                24_000, 6_000));
    }

    @Test
    void incubatorFullyPoweredHatchesInFiveMinutes() {
        assertEquals(6_000L, ticksToHatch(Long.MAX_VALUE, IncubatorRules.DEFAULT_ENERGY_PER_TICK));
    }

    @Test
    void incubatorPartialPowerIsBetweenAndPowerNeverSlowsIt() {
        long half = ticksToHatch(IncubatorRules.energyWanted(IncubatorRules.PULSE_TICKS, 20) / 2, 20);
        assertTrue(half > 6_000L && half < 24_000L, "half power: " + half);
        // A powered time not shorter than the unpowered one gives no speed-up (and no slow-down).
        assertEquals(20L, IncubatorRules.progressFor(20, 400L, 400L, 24_000, 30_000));
    }

    @Test
    void incubatorInsertChecksOccupiedThenEggThenCap() {
        assertEquals(IncubatorRules.Insert.OCCUPIED, IncubatorRules.decideInsert(true, true, true));
        assertEquals(IncubatorRules.Insert.NOT_AN_EGG, IncubatorRules.decideInsert(false, false, true));
        assertEquals(IncubatorRules.Insert.CAP, IncubatorRules.decideInsert(false, true, false));
        assertEquals(IncubatorRules.Insert.ACCEPTED, IncubatorRules.decideInsert(false, true, true));
        assertEquals(1_200L, IncubatorRules.secondsLeft(0L, 24_000));
        assertEquals(1_024_000L, IncubatorRules.hatchAtEstimate(1_000_000L, 0L, 24_000));
    }

    @Test
    void eggDataRoundTripsSpeciesAndTraits() {
        EggData egg = new EggData("lumen_moth", TraitSet.of("hardy", "radiant"));
        assertEquals("nerocreatures:lumen_moth", egg.speciesId());
        Optional<EggData> back = EggData.fromTag(egg.toTag());
        assertTrue(back.isPresent());
        assertEquals("nerocreatures:lumen_moth", back.get().speciesId());
        assertEquals(TraitSet.of("hardy", "radiant"), back.get().traits());
        assertTrue(EggData.fromTag(new CompoundTag()).isEmpty(), "no species, no egg");
    }

    // --- Lure Beacon and Sonic Ward -------------------------------------------------------------

    private static final long NOW = 1_000L;

    private static Field lure(double x, String species, double multiplier) {
        return new Field(Kind.LURE, x, 64.0D, 0.0D, species, multiplier, NOW + FieldRules.FIELD_TTL_TICKS);
    }

    private static Field ward(double x) {
        return new Field(Kind.WARD, x, 64.0D, 0.0D, "", 1.0D, NOW + FieldRules.FIELD_TTL_TICKS);
    }

    @Test
    void radiiAreDecisionSixValues() {
        assertEquals(16, FieldRules.WARD_RADIUS);
        assertEquals(24, FieldRules.LURE_RADIUS);
    }

    @Test
    void lureRaisesOnlyItsSpeciesOnlyInsideItsRadius() {
        List<Field> fields = List.of(lure(0.0D, "lumen_moth", 4.0D));
        assertEquals(4.0D, FieldRules.weightFactor(fields, 23.0D, 64.0D, 0.0D, "lumen_moth", false, NOW));
        assertEquals(1.0D, FieldRules.weightFactor(fields, 25.0D, 64.0D, 0.0D, "lumen_moth", false, NOW));
        assertEquals(1.0D, FieldRules.weightFactor(fields, 5.0D, 64.0D, 0.0D, "rime_yak", false, NOW));
        assertEquals(1.0D, FieldRules.weightFactor(fields, 5.0D, 64.0D + FieldRules.VERTICAL_REACH + 1, 0.0D,
                "lumen_moth", false, NOW), "vertical bound");
    }

    @Test
    void lureStrengthIsCappedAndOverlapsDoNotStack() {
        assertEquals(FieldRules.MAX_LURE_MULTIPLIER, FieldRules.clampMultiplier(1_000.0D));
        assertEquals(1.0D, FieldRules.clampMultiplier(0.2D));
        assertEquals(1.0D, FieldRules.clampMultiplier(Double.NaN));
        List<Field> stacked = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            stacked.add(lure(i, "lumen_moth", 4.0D));
        }
        stacked.add(lure(0.0D, "lumen_moth", 50.0D));
        double factor = FieldRules.weightFactor(stacked, 1.0D, 64.0D, 0.0D, "lumen_moth", false, NOW);
        assertEquals(FieldRules.MAX_LURE_MULTIPLIER, factor, "strongest lure only, clamped");
    }

    @Test
    void fieldsExpireWithoutARefresh() {
        List<Field> fields = List.of(lure(0.0D, "lumen_moth", 4.0D), ward(0.0D));
        long later = NOW + FieldRules.FIELD_TTL_TICKS;
        assertEquals(1.0D, FieldRules.weightFactor(fields, 1.0D, 64.0D, 0.0D, "lumen_moth", false, later));
        assertEquals(1.0D, FieldRules.weightFactor(fields, 1.0D, 64.0D, 0.0D, "echo_shrike", true, later));
        assertFalse(FieldRules.warded(fields, 1.0D, 64.0D, 0.0D, later));
    }

    @Test
    void wardZeroesHostilesOnlyAndBeatsALure() {
        List<Field> fields = List.of(ward(0.0D), lure(0.0D, "echo_shrike", 8.0D), lure(0.0D, "lumen_moth", 3.0D));
        assertEquals(0.0D, FieldRules.weightFactor(fields, 15.0D, 64.0D, 0.0D, "echo_shrike", true, NOW));
        assertEquals(3.0D, FieldRules.weightFactor(fields, 15.0D, 64.0D, 0.0D, "lumen_moth", false, NOW));
        // Past the ward's 16 blocks, the hostile lure applies again.
        assertEquals(8.0D, FieldRules.weightFactor(fields, 17.0D, 64.0D, 0.0D, "echo_shrike", true, NOW));
        assertTrue(FieldRules.warded(fields, 16.0D, 64.0D, 0.0D, NOW));
        assertFalse(FieldRules.warded(fields, 16.5D, 64.0D, 0.0D, NOW));
    }

    @Test
    void fieldCountPerLevelIsBounded() {
        List<Field> many = new ArrayList<>();
        for (int i = 0; i < FieldRules.MAX_FIELDS_PER_LEVEL; i++) {
            many.add(lure(1_000.0D + i, "other", 2.0D));
        }
        many.add(ward(0.0D)); // the 65th field is never read
        assertFalse(FieldRules.warded(many, 0.0D, 64.0D, 0.0D, NOW));
        assertEquals(1.0D, FieldRules.weightFactor(many, 0.0D, 64.0D, 0.0D, "echo_shrike", true, NOW));
    }

    @Test
    void wardRepelsHostilesOnlyAndWalksThemPastTheEdge() {
        assertTrue(FieldRules.wardRepels(true, false, false));
        assertFalse(FieldRules.wardRepels(true, true, false), "never a boss");
        assertFalse(FieldRules.wardRepels(true, false, true), "never a tamed creature");
        assertFalse(FieldRules.wardRepels(false, false, false), "never a passive one");
        double[] out = FieldRules.pushTarget(0.0D, 0.0D, 3.0D, 4.0D);
        double reach = FieldRules.WARD_RADIUS + FieldRules.WARD_PUSH_MARGIN;
        assertEquals(reach, Math.hypot(out[0], out[1]), 1.0E-9);
        assertEquals(0.6D, out[0] / reach, 1.0E-9, "straight away from the centre");
        assertArrayEquals(new double[] {reach, 0.0D}, FieldRules.pushTarget(0.0D, 0.0D, 0.0D, 0.0D), 1.0E-9);
    }

    @Test
    void burnRunsOutAndNeverGoesNegative() {
        assertEquals(5_980, FieldRules.burnAfterPulse(6_000, 20));
        assertEquals(0, FieldRules.burnAfterPulse(10, 20));
        assertEquals(0, FieldRules.burnAfterPulse(0, 20));
    }

    // --- Feeding Trough -------------------------------------------------------------------------

    private static TroughRules.Candidate<String> animal(String name, String species, boolean baby, boolean kept,
            boolean ready) {
        return new TroughRules.Candidate<>(name, species, baby, kept, ready);
    }

    @Test
    void troughFeedsOnlyHusbandedAdultsReadyForLove() {
        assertTrue(TroughRules.eligible(false, true, true));
        assertFalse(TroughRules.eligible(true, true, true), "never a baby");
        assertFalse(TroughRules.eligible(false, false, true), "never wild livestock");
        assertFalse(TroughRules.eligible(false, true, false), "never one already in love or on cooldown");
    }

    @Test
    void troughPicksOnePairOfOneSpeciesWithFood() {
        List<TroughRules.Candidate<String>> herd = List.of(
                animal("wildYak", "rime_yak", false, false, true),
                animal("yakCalf", "rime_yak", true, true, true),
                animal("yakA", "rime_yak", false, true, true),
                animal("moss", "mossback", false, true, true),
                animal("yakB", "rime_yak", false, true, true),
                animal("yakC", "rime_yak", false, true, true));
        assertEquals(List.of("yakA", "yakB"), TroughRules.pickPair(herd, species -> true));
        assertEquals(List.of(), TroughRules.pickPair(herd, species -> false), "no food, no feeding");
        assertEquals(List.of(), TroughRules.pickPair(List.of(animal("moss", "mossback", false, true, true)),
                species -> true), "a single animal is never fed");
    }

    @Test
    void troughScanAndCadenceAreBounded() {
        List<TroughRules.Candidate<String>> crowd = new ArrayList<>();
        for (int i = 0; i < TroughRules.MAX_SCANNED; i++) {
            crowd.add(animal("baby" + i, "rime_yak", true, true, true));
        }
        crowd.add(animal("late1", "rime_yak", false, true, true));
        crowd.add(animal("late2", "rime_yak", false, true, true));
        assertEquals(List.of(), TroughRules.pickPair(crowd, species -> true), "past the scan cap");
        assertTrue(TroughRules.pulseDue(400L, 200, 0L));
        assertFalse(TroughRules.pulseDue(401L, 200, 0L));
        assertFalse(TroughRules.pulseDue(0L, 0, 0L), "interval 0 switches troughs off");
    }

    // --- Nest -----------------------------------------------------------------------------------

    @Test
    void nestBindsOnlyAnOwnersIndexedCreature() {
        assertTrue(NestRules.mayBind(true, true));
        assertFalse(NestRules.mayBind(false, true), "someone else's creature");
        assertFalse(NestRules.mayBind(true, false), "owner says so but the store does not");
    }

    @Test
    void nestHomeBehaviour() {
        assertEquals(NestRules.Action.FORGET, NestRules.decide(false, false, false, false, 1.0D));
        assertEquals(NestRules.Action.NONE, NestRules.decide(true, true, false, false, 40.0D), "following owner");
        assertEquals(NestRules.Action.NONE, NestRules.decide(true, false, true, false, 40.0D), "ridden");
        assertEquals(NestRules.Action.REST, NestRules.decide(true, false, false, false, 2.0D));
        assertEquals(NestRules.Action.REST, NestRules.decide(true, false, false, true, 2.0D), "sitting rests");
        assertEquals(NestRules.Action.RETURN, NestRules.decide(true, false, false, false, 20.0D));
        assertEquals(NestRules.Action.NONE, NestRules.decide(true, false, false, true, 20.0D), "sitting stays put");
        assertEquals(NestRules.Action.NONE, NestRules.decide(true, false, false, false, 6.0D), "within stray range");
        assertEquals(NestRules.Action.NONE, NestRules.decide(true, false, false, false, 65.0D), "no long treks");
    }

    @Test
    void nestHomeTagRoundTrips() {
        NestRules.Home home = new NestRules.Home("minecraft:overworld", -12, 64, 300);
        assertEquals(Optional.of(home), NestRules.Home.decode(home.encode()));
        assertTrue(NestRules.Home.decode("nerocreatures.nest:bad").isEmpty());
        assertTrue(NestRules.Home.decode("nerocreatures.nest:minecraft:overworld@1,x,3").isEmpty());
        assertTrue(NestRules.Home.decode("some_other_tag").isEmpty());
    }

    // --- Trophy Plaque and Terrarium: one item ------------------------------------------------

    @Test
    void displayHoldsExactlyOneItem() {
        assertEquals(DisplayRules.Action.INSERT, DisplayRules.decide(false, false, true));
        assertEquals(1, DisplayRules.insertCount(64), "one from a full stack");
        assertEquals(DisplayRules.Action.REFUSE_FULL, DisplayRules.decide(true, false, true), "no second item");
        assertEquals(DisplayRules.Action.REFUSE_ITEM, DisplayRules.decide(false, false, false));
        assertEquals(DisplayRules.Action.TAKE, DisplayRules.decide(true, true, false));
        assertEquals(DisplayRules.Action.PASS, DisplayRules.decide(false, true, false));
    }

    @Test
    void capturedCreatureCarriesNoIdentityAndRoundTrips() {
        CompoundTag entity = new CompoundTag();
        entity.putString("Owner", "owner-id");
        entity.putString("UUID", "entity-id");
        entity.putString("LoveCause", "player-id");
        entity.putString("Pos", "here");
        entity.putString("nerocreatures:mount", "saddled");
        entity.putString("nerocreatures:custom_kept", "kept");
        entity.putString("neoforge:attachments", "another mod's data");
        entity.putString("last_hurt_by_player", "player-id");
        entity.putString("Health", "10");
        CapturedCreature capture = CapturedCreature.of("nerocreatures:lumen_moth", entity,
                Optional.of(TraitSet.of("keen", "calm")), true);
        for (String key : CapturedCreature.STRIPPED_KEYS) {
            assertFalse(capture.entity().contains(key), key);
        }
        assertTrue(capture.entity().contains("nerocreatures:custom_kept"), "this mod's own keys travel");
        assertTrue(capture.entity().contains("Health"), "allowed vanilla keys travel");
        assertFalse(capture.entity().contains("neoforge:attachments"), "anything not allowed is dropped");
        assertTrue(entity.contains("Owner"), "the caller's tag is not modified");

        Optional<CapturedCreature> back = CapturedCreature.fromTag(capture.toTag());
        assertTrue(back.isPresent());
        assertEquals(capture, back.get());
        assertTrue(back.get().understood());

        CapturedCreature newer = new CapturedCreature(CapturedCreature.CURRENT_VERSION + 1, "nerocreatures:lumen_moth",
                new CompoundTag(), Optional.empty(), false);
        assertFalse(newer.understood(), "a newer capture is kept, never released");
    }
}
