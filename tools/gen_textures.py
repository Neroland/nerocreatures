#!/usr/bin/env python3
"""Generate NeroCreatures' programmer-art item textures.

Purely additive: a texture that already exists on disk is never overwritten, so hand-drawn
replacements survive every rerun and this script only fills gaps. Run it directly or through the
Gradle `genAssets` task.

    python tools/gen_textures.py                # write missing textures
    python tools/gen_textures.py --multiloader  # same (flag kept for the shared gradle task)
    python tools/gen_textures.py --force        # rewrite every generated texture
    python tools/gen_textures.py --list         # print what would be written, write nothing

No third-party dependency: PNGs are encoded here with `zlib` + `struct`, so the script runs on a
bare Python 3 install (Pillow is deliberately not required).

Textures are 16x16 RGBA, deterministic (each motif seeds its own PRNG), and intentionally simple —
this is placeholder art whose only job is to make each drop instantly distinguishable in an
inventory. The real art pass replaces these files wholesale.
"""

from __future__ import annotations

import argparse
import os
import random
import struct
import sys
import zlib

SIZE = 16

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURE_DIR = os.path.join(
    REPO_ROOT, "common", "src", "main", "resources", "assets", "nerocreatures", "textures", "item"
)

Colour = tuple  # (r, g, b, a)

TRANSPARENT: Colour = (0, 0, 0, 0)


class Canvas:
    """A tiny 16x16 RGBA raster with just enough primitives for pixel motifs."""

    def __init__(self, size: int = SIZE) -> None:
        self.size = size
        self.px = [[TRANSPARENT for _ in range(size)] for _ in range(size)]

    def set(self, x: int, y: int, colour: Colour) -> None:
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = colour

    def rect(self, x0: int, y0: int, x1: int, y1: int, colour: Colour) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def outline(self, x0: int, y0: int, x1: int, y1: int, colour: Colour) -> None:
        for x in range(x0, x1 + 1):
            self.set(x, y0, colour)
            self.set(x, y1, colour)
        for y in range(y0, y1 + 1):
            self.set(x0, y, colour)
            self.set(x1, y, colour)

    def disc(self, cx: float, cy: float, radius: float, colour: Colour) -> None:
        for y in range(self.size):
            for x in range(self.size):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    self.set(x, y, colour)

    def diamond(self, cx: int, cy: int, radius: int, colour: Colour) -> None:
        for y in range(self.size):
            for x in range(self.size):
                if abs(x - cx) + abs(y - cy) <= radius:
                    self.set(x, y, colour)

    def speckle(self, rng: random.Random, count: int, colour: Colour, region) -> None:
        x0, y0, x1, y1 = region
        for _ in range(count):
            self.set(rng.randint(x0, x1), rng.randint(y0, y1), colour)

    def to_png(self) -> bytes:
        raw = bytearray()
        for row in self.px:
            raw.append(0)  # filter type 0 (None)
            for (r, g, b, a) in row:
                raw += bytes((r, g, b, a))

        def chunk(tag: bytes, data: bytes) -> bytes:
            return (
                struct.pack(">I", len(data))
                + tag
                + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
            )

        header = struct.pack(">2I5B", self.size, self.size, 8, 6, 0, 0, 0)
        return (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b"")
        )


# --- motifs ---------------------------------------------------------------
# One function per drop. Each is deliberately a different silhouette AND a different hue, so the
# ten drops are told apart at inventory size without reading a tooltip.


def void_essence(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.0, 5.6, (36, 14, 60, 255))
    c.disc(7.5, 8.0, 4.0, (74, 30, 118, 255))
    c.disc(7.0, 7.0, 2.2, (150, 90, 220, 255))
    c.speckle(rng, 10, (206, 170, 255, 255), (3, 4, 12, 12))


def stalker_hide(c: Canvas, rng: random.Random) -> None:
    c.rect(2, 3, 13, 12, (110, 82, 56, 255))
    c.outline(2, 3, 13, 12, (70, 50, 32, 255))
    c.rect(4, 5, 11, 6, (140, 108, 74, 255))
    c.speckle(rng, 14, (84, 62, 40, 255), (3, 4, 12, 11))


def stalker_sinew(c: Canvas, rng: random.Random) -> None:
    for i in range(3, 13):
        c.set(i, 13 - i + 2, (214, 200, 168, 255))
        c.set(i, 13 - i + 3, (176, 160, 128, 255))
    c.rect(2, 2, 4, 4, (214, 200, 168, 255))
    c.rect(11, 11, 13, 13, (214, 200, 168, 255))
    c.speckle(rng, 6, (238, 230, 208, 255), (3, 3, 12, 12))


def refined_crystal(c: Canvas, rng: random.Random) -> None:
    c.diamond(8, 8, 6, (28, 116, 130, 255))
    c.diamond(8, 8, 5, (56, 186, 204, 255))
    c.diamond(7, 7, 2, (172, 244, 252, 255))
    c.speckle(rng, 5, (226, 252, 255, 255), (5, 5, 10, 10))


def worm_chitin(c: Canvas, rng: random.Random) -> None:
    c.rect(3, 2, 12, 13, (58, 74, 52, 255))
    c.outline(3, 2, 12, 13, (34, 44, 30, 255))
    for y in (4, 7, 10):
        c.rect(4, y, 11, y, (92, 112, 80, 255))
    c.speckle(rng, 8, (30, 38, 26, 255), (4, 3, 11, 12))


def ore_slurry(c: Canvas, rng: random.Random) -> None:
    for i, y in enumerate(range(13, 7, -1)):
        c.rect(2 + i, y, 13 - i, y, (104, 88, 70, 255))
    c.rect(5, 9, 10, 11, (132, 114, 90, 255))
    c.speckle(rng, 12, (176, 158, 120, 255), (3, 9, 12, 13))
    c.speckle(rng, 6, (72, 60, 46, 255), (3, 10, 12, 13))


def plasma_cell(c: Canvas, rng: random.Random) -> None:
    c.rect(5, 2, 10, 13, (58, 46, 66, 255))
    c.outline(5, 2, 10, 13, (34, 26, 40, 255))
    c.rect(6, 5, 9, 10, (222, 62, 176, 255))
    c.rect(6, 6, 9, 6, (255, 176, 232, 255))
    c.rect(6, 2, 9, 2, (150, 150, 158, 255))
    c.speckle(rng, 5, (255, 220, 248, 255), (6, 5, 9, 10))


def contraband(c: Canvas, rng: random.Random) -> None:
    c.disc(8.0, 10.0, 5.4, (72, 62, 84, 255))
    c.rect(6, 2, 9, 5, (94, 82, 108, 255))
    c.rect(5, 5, 10, 6, (198, 168, 82, 255))
    c.speckle(rng, 8, (48, 40, 58, 255), (4, 7, 12, 13))


def salvaged_circuitry(c: Canvas, rng: random.Random) -> None:
    c.rect(2, 3, 13, 12, (28, 84, 52, 255))
    c.outline(2, 3, 13, 12, (16, 52, 32, 255))
    c.rect(4, 5, 11, 5, (196, 176, 84, 255))
    c.rect(4, 9, 11, 9, (196, 176, 84, 255))
    c.rect(7, 5, 7, 9, (196, 176, 84, 255))
    for x, y in ((4, 7), (10, 7), (5, 11), (11, 11)):
        c.rect(x, y, x + 1, y, (140, 148, 154, 255))
    c.speckle(rng, 5, (60, 128, 84, 255), (3, 4, 12, 11))


def android_core(c: Canvas, rng: random.Random) -> None:
    c.rect(3, 3, 12, 12, (128, 134, 142, 255))
    c.outline(3, 3, 12, 12, (74, 78, 84, 255))
    c.rect(5, 5, 10, 10, (48, 52, 58, 255))
    c.disc(7.5, 7.5, 2.4, (244, 138, 44, 255))
    c.disc(7.5, 7.5, 1.2, (255, 224, 160, 255))
    c.speckle(rng, 4, (176, 182, 190, 255), (4, 4, 11, 11))


MOTIFS = {
    "void_essence": void_essence,
    "stalker_hide": stalker_hide,
    "stalker_sinew": stalker_sinew,
    "refined_crystal": refined_crystal,
    "worm_chitin": worm_chitin,
    "ore_slurry": ore_slurry,
    "plasma_cell": plasma_cell,
    "contraband": contraband,
    "salvaged_circuitry": salvaged_circuitry,
    "android_core": android_core,
}


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--multiloader", action="store_true",
                        help="accepted for parity with the shared gradle task; has no effect")
    parser.add_argument("--force", action="store_true", help="overwrite existing textures")
    parser.add_argument("--list", action="store_true", help="report only, write nothing")
    args = parser.parse_args(argv)

    os.makedirs(TEXTURE_DIR, exist_ok=True)
    written = 0
    skipped = 0
    for name, motif in sorted(MOTIFS.items()):
        path = os.path.join(TEXTURE_DIR, name + ".png")
        if os.path.exists(path) and not args.force:
            skipped += 1
            continue
        if args.list:
            print("would write " + path)
            written += 1
            continue
        canvas = Canvas()
        motif(canvas, random.Random(name))
        with open(path, "wb") as handle:
            handle.write(canvas.to_png())
        print("wrote " + os.path.relpath(path, REPO_ROOT))
        written += 1
    print("gen_textures: {} written, {} already present".format(written, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
