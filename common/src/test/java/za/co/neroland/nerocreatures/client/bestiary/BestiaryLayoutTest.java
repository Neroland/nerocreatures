package za.co.neroland.nerocreatures.client.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.client.bestiary.BestiaryLayout.Mode;
import za.co.neroland.nerocreatures.client.bestiary.BestiaryLayout.Rect;

/** The resolved layout for a 1920x1080 window, plus the 320x240 floor. */
class BestiaryLayoutTest {

    private static void rect(Rect r, int x, int y, int w, int h) {
        assertEquals(new Rect(x, y, w, h), r);
    }

    @Test
    void scale2() {
        BestiaryLayout l = BestiaryLayout.of(960, 540);
        rect(l.panel(), 120, 70, 720, 400);
        rect(l.header(), 124, 74, 712, 22);
        rect(l.close(), 818, 78, 14, 14);
        rect(l.footer(), 124, 452, 712, 14);
        rect(l.body(), 126, 100, 708, 348);
        rect(l.list(), 126, 100, 194, 348);
        assertEquals(6, l.columns());
        rect(l.detail(), 326, 100, 508, 348);
        assertEquals(Mode.SIDE, l.mode());
        rect(l.viewport(), 326, 100, 176, 176);
        rect(l.records(), 326, 280, 176, 168);
        assertEquals(510, l.nameX());
        assertEquals(102, l.nameY());
        rect(l.text(), 510, 130, 324, 318);
    }

    @Test
    void scale3() {
        BestiaryLayout l = BestiaryLayout.of(640, 360);
        rect(l.panel(), 8, 8, 624, 344);
        rect(l.close(), 610, 16, 14, 14);
        rect(l.list(), 14, 38, 194, 292);
        rect(l.detail(), 214, 38, 412, 292);
        assertEquals(Mode.SIDE, l.mode());
        rect(l.viewport(), 214, 38, 144, 144);
        rect(l.records(), 214, 186, 144, 144);
        rect(l.text(), 366, 68, 260, 262);
    }

    @Test
    void scale4() {
        BestiaryLayout l = BestiaryLayout.of(480, 270);
        rect(l.panel(), 8, 8, 464, 254);
        rect(l.footer(), 12, 244, 456, 14);
        rect(l.list(), 14, 38, 134, 202);
        assertEquals(4, l.columns());
        rect(l.detail(), 154, 38, 312, 202);
        assertEquals(Mode.STACKED, l.mode());
        rect(l.viewport(), 154, 38, 96, 96);
        rect(l.records(), 258, 68, 208, 66);
        assertEquals(258, l.nameX());
        rect(l.text(), 154, 138, 312, 102);
    }

    @Test
    void smallestWindowIsSinglePane() {
        BestiaryLayout l = BestiaryLayout.of(320, 240);
        rect(l.panel(), 8, 8, 304, 224);
        rect(l.body(), 14, 38, 292, 172);
        assertFalse(l.twoPane());
        assertEquals(9, l.columns());
        assertEquals(Mode.SINGLE, l.mode());
        assertEquals(72, l.view());
    }

    @Test
    void nothingLeavesTheWindowAndControlsDoNotOverlap() {
        int[][] sizes = {{960, 540}, {640, 360}, {480, 270}, {320, 240}};
        for (int[] s : sizes) {
            BestiaryLayout l = BestiaryLayout.of(s[0], s[1]);
            Rect window = new Rect(0, 0, s[0], s[1]);
            for (Rect r : new Rect[] {l.panel(), l.header(), l.close(), l.footer(), l.body(), l.list(), l.detail(),
                    l.viewport(), l.records(), l.text()}) {
                assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= window.right() && r.bottom() <= window.bottom(),
                        s[0] + "x" + s[1] + " " + r);
            }
            assertFalse(l.viewport().overlaps(l.records()));
            assertFalse(l.viewport().overlaps(l.text()));
            assertFalse(l.records().overlaps(l.text()), s[0] + "x" + s[1]);
            if (l.twoPane()) {
                assertFalse(l.list().overlaps(l.detail()));
            } else {
                assertFalse(l.back().overlaps(l.viewport()));
            }
            assertFalse(l.close().overlaps(l.body()));
        }
    }
}
