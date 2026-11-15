package za.co.neroland.nerocreatures.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The in-game evaluator ({@link CreatureRig#evaluate}) must give the same pose as the reference in
 * {@code tools/art/spec.py} {@code evaluate()}. The expected numbers below were produced by that
 * reference for the Lunar Stalker spec (regenerate them if that spec's clips change) and for the
 * small synthetic rig in {@link #synthetic()}, whose spec twin is spelled out beside it.
 */
class CreatureRigTest {

    private static final double EPS = 1e-9;

    private static AnimInput in(double age, double walkPos, double walkSpeed, int pose, double poseTicks,
            double hurtTicks) {
        AnimInput in = new AnimInput();
        in.age = age;
        in.walkPos = walkPos;
        in.walkSpeed = walkSpeed;
        in.pose = pose;
        in.poseTicks = poseTicks;
        in.hurtTicks = hurtTicks;
        return in;
    }

    private static void check(AnimInput in, double glow, double[] rot, double[] pos) {
        CreatureRig rig = LunarStalkerModel.RIG;
        double[] r = new double[rig.partCount() * 3];
        double[] p = new double[rig.partCount() * 3];
        assertEquals(glow, rig.evaluate(in, r, p), EPS, "glow");
        assertEquals(glow, rig.evaluate(in, null, null), EPS, "glow only");
        for (int i = 0; i < r.length; i++) {
            assertEquals(rot[i], r[i], EPS, "rot " + rig.partName(i / 3) + "[" + (i % 3) + "]");
            assertEquals(pos[i], p[i], EPS, "pos " + rig.partName(i / 3) + "[" + (i % 3) + "]");
        }
    }

    @Test
    void matchesReferenceEvaluator() {
        check(in(37.3, 0.0, 0.0, 0, 0.0, -1.0), 0.35,
                new double[] {0.0, 0.0, 0.0, 0.0, 12.0, 0.0, -1.8999999999999995, 16.0, 0.0, 0.0, -2.564944540995922, 0.0, 0.0, 6.109478207815078, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        check(in(12.5, 7.1, 0.6, 0, 0.0, -1.0), 0.35,
                new double[] {0.02020281180398129, 0.0, 0.0, 5.115152108852985, 3.28125, 0.0, 0.0, 3.3998842592592595, 0.0, -0.02020281180398129, 4.330884451675657, 0.0, 0.0, 0.9986642216462688, 0.0, 0.3808102936474766, 0.0, 0.0, -0.38081029364747404, 0.0, 0.0, -0.38081029364747404, 0.0, 0.0, 16.793697923582336, 0.0, 0.0, 0.3808102936474766, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[] {0.0, -0.5997749258422262, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        check(in(50.0, 3.0, 0.3, 1, 2.5, -1.0), 0.4515625,
                new double[] {0.49018905874785845, 0.0, 0.0, 2.918435251092144, 2.5493999999999994, 0.0, 0.14440758404948184, 3.578433925130181, 0.0, -0.49629257437285845, -8.211252988386814, 0.0, 0.5741119384765625, -3.2680570786064456, 0.0, -4.480632857609337, 0.0, 0.0, 4.480632857609332, 0.0, 0.0, 4.480632857609332, 0.0, 0.0, 0.0, 0.0, 0.0, -4.480632857609337, 0.0, 0.0, 6.870203286036328, 0.0, 0.0},
                new double[] {0.0, 0.05294684494147775, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        check(in(81.25, 0.0, 0.0, 1, 22.0, 3.2), 1.0,
                new double[] {-10.0, 0.0, 7.055999999999999, -46.704, -14.111999999999998, 0.0, -23.0, 0.0, 0.0, 26.0, 27.531351746225102, 0.0, 14.0, 1.5601017343237513, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[] {1.176, -1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        check(in(5.0, -5.3, 1.0, 0, 0.0, 9.0), 0.35,
                new double[] {-1.830981682993548, 0.0, 0.0, 7.126763737754839, 0.0, 0.0, 0.0, 0.0, 0.0, 1.830981682993548, 0.0, 0.0, 0.0, 0.0, 0.0, -33.30674488809383, 0.0, 0.0, 33.306744888093824, 0.0, 0.0, 33.306744888093824, 0.0, 0.0, 0.0, 0.0, 0.0, -33.30674488809383, 0.0, 0.0, 4.306382666314384, 0.0, 0.0},
                new double[] {0.0, -0.1537993809397995, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        check(in(200.0, 0.0, 0.0, 1, 55.0, -1.0), 0.35,
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -9.011846961260112, 0.0, 0.0, -9.885572423064342, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
    }

    @Test
    void pythonModulo() {
        assertEquals(1.0, CreatureRig.pyMod(-3.0, 4.0), EPS);
        assertEquals(3.0, CreatureRig.pyMod(3.0, 4.0), EPS);
        assertEquals(0.0, CreatureRig.pyMod(8.0, 4.0), EPS);
    }

    @Test
    void lookIsClamped() {
        CreatureRig rig = LunarStalkerModel.RIG;
        int n = rig.partCount() * 3;
        double[] base = new double[n];
        rig.evaluate(in(0.0, 0.0, 0.0, 0, 0.0, -1.0), base, new double[n]);
        AnimInput looking = in(0.0, 0.0, 0.0, 0, 0.0, -1.0);
        looking.headYaw = 200.0;
        double[] r = new double[n];
        rig.evaluate(looking, r, new double[n]);
        int head = rig.indexOf("head");
        assertEquals(45.0, r[head * 3 + 1] - base[head * 3 + 1], EPS);
    }

    /**
     * The spec twin of this rig, evaluated by {@code tools/art/spec.py} for the expected values:
     * parts body, arm (child of body), ring; spin ring y 3/tick phase 10 when pose idle; glow rules
     * form [1] max 0.2, stage [1] min 1.0 flicker 4; idle (20, loop) arm rot 0 -> 10 at 10 (smooth)
     * -> 0; phase_1 (20, loop) body pos y -16, arm rot x -20; ability (20) glow 0.35 -> 1.0 at 10,
     * arm rot 0 -> (-90, 0, 40) at 10, body pos 0 -> y -2 at 10 with "blend": "add"; rest (12) body
     * pos 0 -> y 5 at 12 (smooth).
     */
    private static CreatureRig synthetic() {
        return CreatureRig.builder()
                .part("body", null)
                .part("arm", "body")
                .part("ring", null)
                .spin("ring", 'y', 3, 10, CreatureRig.when(0, 0, 0, 0, 0, CreatureRig.POSE_IDLE))
                .clip("idle", 20, true)
                    .rot("arm", 0, 0, 0, 0, 0,  10, 10, 0, 0, 1,  20, 0, 0, 0, 0)
                .clip("phase_1", 20, true)
                    .pos("body", 0, 0, -16, 0, 0,  20, 0, -16, 0, 0)
                    .rot("arm", 0, -20, 0, 0, 0,  20, -20, 0, 0, 0)
                .clip("ability", 20, false)
                    .glow(0, 0.35, 0,  10, 1.0, 0)
                    .rot("arm", 0, 0, 0, 0, 0,  10, -90, 0, 40, 0)
                    .posAdd("body", 0, 0, 0, 0, 0,  10, 0, -2, 0, 0)
                .clip("rest", 12, false)
                    .pos("body", 0, 0, 0, 0, 0,  12, 0, 5, 0, 1)
                .glowRule(CreatureRig.when(0, 0, 0b10, 0, 0, 0), Double.NaN, 0.2, 0, CreatureRig.GLOW_DIM)
                .glowRule(CreatureRig.when(0b10, 0, 0, 0, 0, 0), 1.0, Double.NaN, 4, CreatureRig.GLOW_DIM)
                .build();
    }

    private static void checkSynthetic(AnimInput in, double glow, double[] rot, double[] pos) {
        CreatureRig rig = synthetic();
        double[] r = new double[9];
        double[] p = new double[9];
        assertEquals(glow, rig.evaluate(in, r, p), EPS, "glow");
        assertEquals(glow, rig.evaluate(in, null, null), EPS, "glow only");
        for (int i = 0; i < 9; i++) {
            assertEquals(rot[i], r[i], EPS, "rot " + rig.partName(i / 3) + "[" + (i % 3) + "]");
            assertEquals(pos[i], p[i], EPS, "pos " + rig.partName(i / 3) + "[" + (i % 3) + "]");
        }
    }

    /** An action replaces only the channels it keys, so the boss phase stance (body at -16) survives. */
    @Test
    void phaseStanceSurvivesAnAction() {
        AnimInput in = in(5.0, 0.0, 0.0, 1, 10.0, -1.0);
        in.phase = 1;
        checkSynthetic(in, 1.0, new double[] {0, 0, 0, -90, 0, 40, 0, 0, 0}, new double[] {0, -18, 0, 0, 0, 0, 0, 0, 0});
        in.poseTicks = 1.0;
        checkSynthetic(in, 0.36624999999999996, new double[] {0, 0, 0, -17.25, 0, 1, 0, 18.75, 0},
                new double[] {0, -16.05, 0, 0, 0, 0, 0, 0, 0});
    }

    /** An action that starts from a held rest blends from the rest pose, not from idle. */
    @Test
    void actionBlendsFromTheHeldPose() {
        AnimInput in = in(7.0, 0.0, 0.0, 1, 2.0, -1.0);
        in.fromPose = 3;
        in.fromTicks = 40.0;
        checkSynthetic(in, 0.415, new double[] {0, 0, 0, -5.080000000000001, 0, 4, 0, 0, 0},
                new double[] {0, 2.3, 0, 0, 0, 0, 0, 0, 0});
    }

    /** Back to idle blends from the action frozen where it stopped; the gated spin fades back in. */
    @Test
    void idleBlendsFromTheLastAction() {
        AnimInput in = in(100.0, 0.0, 0.0, 0, 0.0, -1.0);
        in.fromPose = 1;
        in.fromTicks = 20.0;
        in.since = 1.0;
        checkSynthetic(in, 0.8375, new double[] {0, 0, 0, -67.5, 0, 30, 0, 77.5, 0},
                new double[] {0, -1.5, 0, 0, 0, 0, 0, 0, 0});
        checkSynthetic(in(100.0, 0.0, 0.0, 1, 2.0, -1.0), 0.415, new double[] {0, 0, 0, -9, 0, 4, 0, 155, 0},
                new double[] {0, -0.2, 0, 0, 0, 0, 0, 0, 0});
    }

    /** Glow rules: a stage-keyed flicker every 4 ticks, a form-keyed cap. */
    @Test
    void glowRules() {
        AnimInput in = in(5.0, 0.0, 0.0, 0, 0.0, -1.0);
        in.stage = 1;
        checkSynthetic(in, 0.35, new double[] {0, 0, 0, 5, 0, 0, 0, 25, 0}, new double[9]);
        in.age = 9.0;
        checkSynthetic(in, 1.0, new double[] {0, 0, 0, 9.719999999999999, 0, 0, 0, 37, 0}, new double[9]);
        AnimInput capped = in(9.0, 0.0, 0.0, 0, 0.0, -1.0);
        capped.form = 1;
        assertEquals(0.2, synthetic().evaluate(capped, null, null), EPS);
    }

    @Test
    void visibilityMasks() {
        CreatureRig rig = CreatureRig.builder()
                .part("body", null)
                .part("saddle", "body")
                .part("nodule", "body")
                .visibleWhen("saddle", 0, 0, 0, AnimInput.FLAG_SADDLED)
                .visibleWhen("nodule", 0b1100, 0, 0, 0)
                .build();
        AnimInput in = new AnimInput();
        assertTrue(rig.visible(0, in));
        assertFalse(rig.visible(1, in));
        assertFalse(rig.visible(2, in));
        in.flags = AnimInput.FLAG_SADDLED;
        in.stage = 3;
        assertTrue(rig.visible(1, in));
        assertTrue(rig.visible(2, in));
    }

    @Test
    void phaseAndPoseConditions() {
        CreatureRig rig = CreatureRig.builder()
                .part("body", null)
                .part("beam", "body")
                .visibleWhen("beam", CreatureRig.when(0, 0, 0, 0, 0b1000, CreatureRig.POSE_ABILITY))
                .build();
        AnimInput in = new AnimInput();
        in.phase = 3;
        assertFalse(rig.visible(1, in));
        in.pose = 1;
        assertTrue(rig.visible(1, in));
        in.phase = 2;
        assertFalse(rig.visible(1, in));
    }

    @Test
    void argbMultiply() {
        assertEquals(0xFFD9D9D9, CreatureArt.multiplyArgb(-1, 0xFFD9D9D9));
        assertEquals(0xFF000000, CreatureArt.multiplyArgb(0xFF00FF00, 0xFFFF00FF));
        assertEquals(0x80FF0000, CreatureArt.multiplyArgb(0x80FFFFFF, 0xFFFF0000));
    }
}
