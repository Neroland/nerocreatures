package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductTimerTest {

    @Test
    void growsThroughStagesAndStopsAtReady() {
        ProductTimer t = new ProductTimer(300, 3, 4);
        assertEquals(0, t.stage());
        t.advance(100);
        assertEquals(1, t.stage());
        t.advance(100);
        assertEquals(2, t.stage());
        assertFalse(t.isReady());
        t.advance(10_000);
        assertTrue(t.isReady());
        assertEquals(3, t.stage());
        assertEquals(300, t.progress(), "growth clamps at ready");
    }

    @Test
    void harvestOnlyWhenReadyAndResets() {
        ProductTimer t = new ProductTimer(100, 3, 4);
        assertEquals(0, t.harvest(2, null));
        t.advance(100);
        assertEquals(2, t.harvest(2, ProductTimer.YieldModifier.NONE));
        assertEquals(0, t.progress());
        assertEquals(0, t.harvest(2, null), "no double harvest");
    }

    @Test
    void bountifulHookIsCapped() {
        ProductTimer t = new ProductTimer(10, 1, 4);
        t.advance(10);
        assertEquals(3, t.harvest(2, base -> base + 1));
        t.advance(10);
        assertEquals(4, t.harvest(2, base -> base * 10), "max yield caps the hook");
        t.advance(10);
        assertEquals(0, t.harvest(2, base -> -5), "never negative");
    }

    @Test
    void restoreClamps() {
        ProductTimer t = new ProductTimer(100, 3, 4);
        t.setProgress(-5);
        assertEquals(0, t.progress());
        t.setProgress(500);
        assertEquals(100, t.progress());
    }
}
