package za.co.neroland.nerocreatures.client.bestiary;

/**
 * Every rectangle of the Bestiary screen for a scaled window. Pure integer
 * arithmetic, identical to {@code layout(width, height)} in {@code tools/art/paint/gui.py}; if the
 * two ever disagree, that function wins. Nothing is scaled, cropped or hidden to fit: a smaller
 * window gets fewer columns, a smaller viewport and more scrolling.
 */
public record BestiaryLayout(int width, int height, Rect panel, Rect header, Rect close, Rect footer, Rect body,
        boolean twoPane, int columns, Rect list, Rect detail, Mode mode, Rect viewport, Rect records,
        int nameX, int nameY, Rect text, Rect back) {

    /** How the detail pane is arranged. */
    public enum Mode {
        SIDE, STACKED, SINGLE
    }

    /** A rectangle in GUI pixels. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return this.x + this.w;
        }

        public int bottom() {
            return this.y + this.h;
        }

        public boolean contains(double px, double py) {
            return px >= this.x && py >= this.y && px < this.x + this.w && py < this.y + this.h;
        }

        public boolean overlaps(Rect o) {
            return this.x < o.right() && o.x < this.right() && this.y < o.bottom() && o.y < this.bottom();
        }

        public static final Rect NONE = new Rect(0, 0, 0, 0);
    }

    public static BestiaryLayout of(int width, int height) {
        int pw = Math.min(720, width - 16) & ~1;
        int ph = Math.min(400, height - 16) & ~1;
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        int bx = px + 6;
        int by = py + 30;
        int bw = pw - 12;
        int bh = ph - 52;
        boolean twoPane = pw >= 440;
        int cols = twoPane ? (pw >= 600 ? 6 : 4) : (bw - 18) / 30;
        int lw = twoPane ? cols * 30 + 14 : bw;
        Rect panel = new Rect(px, py, pw, ph);
        Rect header = new Rect(px + 4, py + 4, pw - 8, 22);
        Rect close = new Rect(px + pw - 22, py + 8, 14, 14);
        Rect footer = new Rect(px + 4, py + ph - 18, pw - 8, 14);
        Rect body = new Rect(bx, by, bw, bh);
        Rect list = new Rect(bx, by, lw, bh);
        if (twoPane) {
            int dx = bx + lw + 6;
            int dw = bw - lw - 6;
            boolean side = dw >= 400;
            int view = side ? (dw >= 480 ? 176 : 144) : 96;
            Rect detail = new Rect(dx, by, dw, bh);
            Rect viewport = new Rect(dx, by, view, view);
            if (side) {
                return new BestiaryLayout(width, height, panel, header, close, footer, body, true, cols, list, detail,
                        Mode.SIDE, viewport, new Rect(dx, by + view + 4, view, bh - view - 4), dx + view + 8, by + 2,
                        new Rect(dx + view + 8, by + 30, dw - view - 8, bh - 30), Rect.NONE);
            }
            return new BestiaryLayout(width, height, panel, header, close, footer, body, true, cols, list, detail,
                    Mode.STACKED, viewport, new Rect(dx + view + 8, by + 30, dw - view - 8, view - 30), dx + view + 8,
                    by + 2, new Rect(dx, by + view + 4, dw, bh - view - 4), Rect.NONE);
        }
        int view = 72;
        return new BestiaryLayout(width, height, panel, header, close, footer, body, false, cols, list, body,
                Mode.SINGLE, new Rect(bx, by + 18, view, view), new Rect(bx + view + 8, by + 30, bw - view - 8, view - 12),
                bx + 18, by + 3, new Rect(bx, by + 18 + view + 4, bw, bh - view - 22), new Rect(bx, by, 14, 14));
    }

    /** Viewport edge length. */
    public int view() {
        return this.viewport.w();
    }

    /** Width of one tile row inside the list. */
    public int rowWidth() {
        return this.columns * 30 - 2;
    }
}
