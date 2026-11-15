package za.co.neroland.nerocreatures.entity.ai.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DigestStateTest {

    @Test
    void capacityCapsEating() {
        DigestState s = new DigestState(2, 100);
        assertTrue(s.eat("minecraft:raw_iron", 0));
        assertTrue(s.eat("minecraft:raw_gold", 0));
        assertFalse(s.hasRoom());
        assertFalse(s.eat("minecraft:raw_copper", 0));
        assertFalse(s.eat("", 0));
    }

    @Test
    void digestsSequentiallyOnTimer() {
        DigestState s = new DigestState(3, 100);
        s.eat("a:x", 0);
        s.eat("a:y", 10);
        assertEquals(List.of(), s.pollReady(99));
        assertEquals(List.of("a:x"), s.pollReady(100));
        assertEquals(List.of(), s.pollReady(199), "second starts after the first finishes");
        assertEquals(List.of("a:y"), s.pollReady(200));
        assertTrue(s.isEmpty());
    }

    @Test
    void stageRunsOneToThree() {
        DigestState s = new DigestState(1, 90);
        assertEquals(0, s.stage(0));
        s.eat("a:x", 0);
        assertEquals(1, s.stage(0));
        assertEquals(2, s.stage(30));
        assertEquals(3, s.stage(89));
    }

    @Test
    void encodeDecodeRoundTripAndTolerateJunk() {
        DigestState s = new DigestState(3, 100);
        s.eat("minecraft:raw_iron", 5);
        s.eat("minecraft:raw_gold", 5);
        DigestState back = new DigestState(3, 100);
        back.decode(s.encode());
        assertEquals(s.encode(), back.encode());
        back.decode("bad;also@bad;minecraft:x@12");
        assertEquals("minecraft:x@12", back.encode());
        DigestState small = new DigestState(1, 100);
        small.decode(s.encode());
        assertEquals(1, small.size(), "decode never exceeds capacity");
    }

    @Test
    void drainEmpties() {
        DigestState s = new DigestState(2, 100);
        s.eat("a:x", 0);
        assertEquals(List.of("a:x"), s.drain());
        assertTrue(s.isEmpty());
    }
}
