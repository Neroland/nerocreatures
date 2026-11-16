package za.co.neroland.nerocreatures.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Kind;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.ScanProgress;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.ScanProgress.Step;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.StudyKind;
import za.co.neroland.nerocreatures.breeding.SpeciesProfile;
import za.co.neroland.nerocreatures.breeding.SpeciesProfiles;

class BestiaryRulesTest {

    @Test
    void studyKindFollowsTheStudyTable() {
        assertEquals(StudyKind.KILLS, BestiaryRules.studyKind(Kind.HOSTILE));
        assertEquals(StudyKind.KILLS, BestiaryRules.studyKind(Kind.ELITE));
        assertEquals(StudyKind.KILLS, BestiaryRules.studyKind(Kind.NEUTRAL));
        assertEquals(StudyKind.TAME_OR_BREED, BestiaryRules.studyKind(Kind.TAMEABLE));
        assertEquals(StudyKind.TAME_OR_BREED, BestiaryRules.studyKind(Kind.MOUNT));
        assertEquals(StudyKind.TAME_OR_BREED, BestiaryRules.studyKind(Kind.DRONE));
        assertEquals(StudyKind.HARVEST_OR_BREED, BestiaryRules.studyKind(Kind.LIVESTOCK));
        assertEquals(StudyKind.HARVEST_OR_BREED, BestiaryRules.studyKind(Kind.AMBIENT));
        assertEquals(StudyKind.BOSS_FIGHT, BestiaryRules.studyKind(Kind.BOSS));
    }

    @Test
    void killGoalsAreFiveCommonThreeEliteAndOneOtherwise() {
        assertEquals(5, BestiaryRules.goal(Kind.HOSTILE, 0));
        assertEquals(3, BestiaryRules.goal(Kind.ELITE, 0));
        assertEquals(8, BestiaryRules.goal(Kind.HOSTILE, 8));
        assertEquals(1, BestiaryRules.goal(Kind.TAMEABLE, 3));
        assertEquals(1, BestiaryRules.goal(Kind.LIVESTOCK, 3));
        assertEquals(1, BestiaryRules.goal(Kind.BOSS, 0));
        assertEquals(1000, BestiaryRules.goal(Kind.HOSTILE, 5000));
    }

    @Test
    void thresholdMetOnlyAtTheGoal() {
        assertFalse(BestiaryRules.killsMeetGoal(4, 5));
        assertTrue(BestiaryRules.killsMeetGoal(5, 5));
        assertEquals(4, BestiaryRules.progress(StudyKind.KILLS, 5, 4, false));
        assertEquals(5, BestiaryRules.progress(StudyKind.KILLS, 5, 400, false));
        assertEquals(0, BestiaryRules.progress(StudyKind.TAME_OR_BREED, 1, 99, false));
        assertEquals(1, BestiaryRules.progress(StudyKind.TAME_OR_BREED, 1, 0, true));
    }

    @Test
    void nonKillSpeciesThresholdsAreOneInSpeciesJson() {
        SpeciesProfiles.override(null);
        for (BestiaryCatalog.Entry entry : BestiaryCatalog.entries()) {
            SpeciesProfile profile = SpeciesProfiles.of(entry.id());
            if (BestiaryRules.studyKind(entry.kind()) != StudyKind.KILLS) {
                assertEquals(1, profile.studiedThreshold(), entry.id());
                assertEquals(1, BestiaryProgress.goal(entry), entry.id());
            }
        }
        assertEquals(5, BestiaryProgress.goal(BestiaryCatalog.get("echo_shrike").orElseThrow()));
        assertEquals(3, BestiaryProgress.goal(BestiaryCatalog.get("scrap_hulk").orElseThrow()));
    }

    @Test
    void rewardIsExperienceOnlyAndOnlyWhenTheGateChanged() {
        assertEquals(25, BestiaryRules.rewardFor(true, Kind.HOSTILE));
        assertEquals(25, BestiaryRules.rewardFor(true, Kind.TAMEABLE));
        assertEquals(60, BestiaryRules.rewardFor(true, Kind.ELITE));
        assertEquals(150, BestiaryRules.rewardFor(true, Kind.BOSS));
        assertEquals(0, BestiaryRules.rewardFor(false, Kind.BOSS), "a second open grants nothing");
    }

    @Test
    void fortyOnTargetTicksComplete() {
        ScanProgress p = new ScanProgress();
        for (int t = 1; t < BestiaryRules.SCAN_TICKS; t++) {
            assertEquals(Step.PROGRESS, p.tick(t, 7));
        }
        assertEquals(Step.COMPLETE, p.tick(BestiaryRules.SCAN_TICKS, 7));
        assertEquals(0, p.ticks());
    }

    @Test
    void fiveOffTargetTicksAreForgivenTheSixthLoses() {
        ScanProgress p = new ScanProgress();
        long t = 0;
        for (int i = 0; i < 10; i++) {
            p.tick(++t, 7);
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(Step.PROGRESS, p.tick(++t, -1));
        }
        assertEquals(10, p.ticks());
        assertEquals(Step.PROGRESS, p.tick(++t, 7));
        for (int i = 0; i < 5; i++) {
            p.tick(++t, -1);
        }
        assertEquals(Step.LOST, p.tick(++t, -1));
        assertEquals(0, p.ticks());
        assertEquals(Step.NO_TARGET, p.tick(++t, -1));
    }

    @Test
    void aTargetSwapOrAGapRestarts() {
        ScanProgress p = new ScanProgress();
        for (int t = 1; t <= 30; t++) {
            p.tick(t, 7);
        }
        p.tick(31, 8);
        assertEquals(1, p.ticks());
        p.tick(40, 8); // the use was interrupted for 8 ticks
        assertEquals(1, p.ticks());
    }

    @Test
    void requestAnswersAtMostOncePer20Ticks() {
        assertTrue(BestiaryProgress.mayAnswer(null, 100L));
        assertFalse(BestiaryProgress.mayAnswer(100L, 119L));
        assertTrue(BestiaryProgress.mayAnswer(100L, 120L));
        assertTrue(BestiaryProgress.mayAnswer(100L, 50L), "a clock reset never locks a player out");
    }
}
