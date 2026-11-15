package za.co.neroland.nerocreatures.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The in-game evaluator ({@link CreatureRig#evaluate}) must give the same pose as the reference in
 * {@code tools/art/spec.py} {@code evaluate()}. The expected numbers below were produced by that
 * reference for the Lunar Stalker spec; regenerate them if that spec's clips change.
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
                new double[] {0.49018905874785856, 0.0, 0.0, 2.918435251092144, 2.5493999999999994, 0.0, 0.14440758404948195, 3.5784339251301813, 0.0, -0.49629257437285856, -8.211252988386814, 0.0, 0.5741119384765625, -3.2680570786064456, 0.0, -4.480632857609337, 0.0, 0.0, 4.480632857609332, 0.0, 0.0, 4.480632857609332, 0.0, 0.0, 0.0, 0.0, 0.0, -4.480632857609337, 0.0, 0.0, 2.576326232263623, 0.0, 0.0},
                new double[] {0.0, 0.052946844941477764, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
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
}
