package za.co.neroland.nerocreatures.entity.greenxertz;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bloom Host integration rules: owner-friendly livestock default ({@code bloomHostInfectsLivestock}) and hitbox by form. */
class BloomLivestockRulesTest {

    private static BloomRules.Victim wild() {
        return new BloomRules.Victim(false, false, false, false, false, false, true, false);
    }

    private static BloomRules.Victim bred() {
        return new BloomRules.Victim(false, false, false, false, false, false, true, true);
    }

    @Test
    void bredLivestockSparedByDefault() {
        assertTrue(BloomRules.eligible(wild()), "a wild animal is fair game");
        assertFalse(BloomRules.eligible(bred()), "bred livestock is spared by default");
        assertFalse(BloomRules.eligible(bred(), false));
        assertTrue(BloomRules.eligible(bred(), true), "the key opts bred livestock back in");
    }

    @Test
    void tamedNamedLeashedAlwaysSpared() {
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, true, false, false, false, false, true, false), true),
                "owned/tamed, even with the key on");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, true, false, false, false, true, false), true),
                "named, even with the key on");
        assertFalse(BloomRules.eligible(new BloomRules.Victim(false, false, false, true, false, false, true, false), true),
                "leashed or persistent, even with the key on");
    }

    @Test
    void livestockKeyStillRespectsCaps() {
        assertFalse(BloomRules.mayRaise(true, false, BloomRules.FORM_HOST, bred(), 0, 0));
        assertTrue(BloomRules.mayRaise(true, true, BloomRules.FORM_HOST, bred(), 0, 0));
        assertFalse(BloomRules.mayRaise(true, true, BloomRules.FORM_HOST, bred(), BloomRules.PER_HOST, 0));
        assertFalse(BloomRules.mayRaise(true, true, BloomRules.FORM_HOST, bred(), 0, BloomRules.PER_DIMENSION));
        assertFalse(BloomRules.mayRaise(false, true, BloomRules.FORM_HOST, wild(), 0, 0), "sporelings off");
    }

    @Test
    void hitboxFollowsForm() {
        assertArrayEquals(new float[] {1.0F, 1.0F}, BloomRules.dimensionScale(BloomRules.FORM_HOST, 0.9F, 2.2F));
        float[] sporeling = BloomRules.dimensionScale(BloomRules.FORM_SPORELING, 0.9F, 2.2F);
        assertTrue(sporeling[0] * 0.9F < 0.6F && sporeling[1] * 2.2F < 0.7F, "sporeling is small");
        float[] pod = BloomRules.dimensionScale(BloomRules.FORM_POD, 0.9F, 2.2F);
        assertTrue(pod[1] * 2.2F <= 1.0F + 1.0E-4F, "pod is at most a block tall");
        assertArrayEquals(new float[] {1.0F, 1.0F}, BloomRules.dimensionScale(7, 0.9F, 2.2F), "unknown form = host");
    }
}
