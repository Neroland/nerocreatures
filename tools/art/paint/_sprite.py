#!/usr/bin/env python3
"""Shared sprite kit for the item and block painters (``items.py``, ``blocks.py``).

Standard library only. A sprite is a 16x16 grid of characters plus a legend that maps each character
to a palette ramp step, so a texture cannot hold an off-palette colour:

    legend "a=slate2 b=umber3 G=accent3! w=wrecks.white2"
    token  [habitat.]ramp<step>[!]

A token without a habitat uses the sprite's own habitat. ``.`` is transparent. Tokens are
edge-shaded: a texel on the lower or right rim of the shape drops two steps, one on the upper or
left rim gains one, which is what gives a flat grid its volume. ``!`` keeps the step as written
(glow texels, highlights, hard detail). Nothing here imports ``random``.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import CLEAR, REPO_ROOT, Image, Palette, write_or_check  # noqa: E402

ASSETS = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets", "nerocreatures")
SIZE = 16
_PALETTE = None


def palette() -> Palette:
    global _PALETTE
    if _PALETTE is None:
        _PALETTE = Palette()
    return _PALETTE


def colour(habitat: str, ramp: str, step: int) -> tuple:
    """One palette texel. ``ramp`` may carry its own habitat as ``habitat.ramp``."""
    if "." in ramp:
        habitat, ramp = ramp.split(".", 1)
    steps = palette().ramp(habitat, ramp)
    return steps[max(0, min(len(steps) - 1, step))] + (255,)


def parse_legend(habitat: str, legend: str) -> dict:
    """``"a=slate2 G=accent3!"`` to ``{"a": (habitat, ramp, step, shaded)}``."""
    table = {}
    for entry in legend.split():
        key, token = entry.split("=", 1)
        shaded = not token.endswith("!")
        token = token.rstrip("!")
        ramp, step = token[:-1], int(token[-1])
        hab = habitat
        if "." in ramp:
            hab, ramp = ramp.split(".", 1)
        palette().ramp(hab, ramp)  # raises on an unknown ramp
        table[key] = (hab, ramp, step, shaded)
    return table


def sprite(habitat: str, legend: str, rows, shade: bool = True) -> Image:
    """Builds one 16x16 image from a character grid."""
    rows = [row for row in rows]
    if len(rows) != SIZE or any(len(row) != SIZE for row in rows):
        raise ValueError("a sprite is 16 rows of 16 characters: " + repr(rows))
    table = parse_legend(habitat, legend)
    image = Image(SIZE, SIZE)

    def solid(x: int, y: int) -> bool:
        return 0 <= x < SIZE and 0 <= y < SIZE and rows[y][x] != "."

    for y in range(SIZE):
        for x in range(SIZE):
            ch = rows[y][x]
            if ch == ".":
                continue
            if ch not in table:
                raise KeyError("character %r is not in the legend %r" % (ch, legend))
            hab, ramp, step, shaded = table[ch]
            if shade and shaded:
                lit = not solid(x, y - 1) or not solid(x - 1, y)
                dark = not solid(x, y + 1) or not solid(x + 1, y)
                step += -1 if (lit and dark) else 1 if lit else -2 if dark else 0
            image.set(x, y, colour(hab, ramp, step))
    return image


class Tile:
    """A 16x16 opaque block face drawn with rectangles; every call names a ramp and a step."""

    def __init__(self, habitat: str, ramp: str = None, step: int = 2) -> None:
        self.habitat = habitat
        self.image = Image(SIZE, SIZE)
        if ramp:
            self.rect(0, 0, SIZE, SIZE, ramp, step)

    def px(self, x: int, y: int, ramp: str, step: int) -> None:
        self.image.set(x, y, colour(self.habitat, ramp, step))

    def rect(self, x: int, y: int, w: int, h: int, ramp: str, step: int) -> None:
        self.image.rect(x, y, w, h, colour(self.habitat, ramp, step))

    def row(self, y: int, ramp: str, step: int, x: int = 0, w: int = SIZE) -> None:
        self.rect(x, y % SIZE, w, 1, ramp, step)

    def col(self, x: int, ramp: str, step: int, y: int = 0, h: int = SIZE) -> None:
        self.rect(x % SIZE, y, 1, h, ramp, step)

    def frame(self, x: int, y: int, w: int, h: int, ramp: str, step: int) -> None:
        self.rect(x, y, w, 1, ramp, step)
        self.rect(x, y + h - 1, w, 1, ramp, step)
        self.rect(x, y, 1, h, ramp, step)
        self.rect(x + w - 1, y, 1, h, ramp, step)

    def bevel(self, x: int, y: int, w: int, h: int, ramp: str, step: int) -> None:
        """A pressed plate: fill, lit top and left edge, shadowed bottom and right edge."""
        self.rect(x, y, w, h, ramp, step)
        self.rect(x, y, w, 1, ramp, step + 1)
        self.rect(x, y, 1, h, ramp, step + 1)
        self.rect(x, y + h - 1, w, 1, ramp, step - 1)
        self.rect(x + w - 1, y, 1, h, ramp, step - 1)

    def stripes(self, x: int, y: int, w: int, h: int, a, b, band: int = 2) -> None:
        """Diagonal warning stripes ``band`` texels wide; ``a`` and ``b`` are ``(ramp, step)``."""
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                ramp, step = a if ((xx + yy) // band) % 2 == 0 else b
                self.px(xx, yy, ramp, step)

    def grid(self, rows, legend: str) -> None:
        """Overlays a character grid (``.`` leaves the tile as it is). No edge shading."""
        table = parse_legend(self.habitat, legend)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    hab, ramp, step, _ = table[ch]
                    self.image.set(x, y, colour(hab, ramp, step))


def write_text(path: str, text: str, check: bool) -> bool:
    """Writes a text file with LF endings, or with ``check`` reports whether it already matches."""
    data = text.encode("utf-8")
    if check:
        try:
            with open(path, "rb") as handle:
                same = handle.read() == data
        except OSError:
            same = False
        if not same:
            print("DRIFT " + os.path.relpath(path, REPO_ROOT))
        return same
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(data)
    print("wrote " + os.path.relpath(path, REPO_ROOT))
    return True


def write_images(directory: str, images: dict, check: bool) -> int:
    """Writes or checks ``{name: Image}`` as ``<directory>/<name>.png``. Returns the failure count."""
    failed = 0
    for name in sorted(images):
        failed += not write_or_check(os.path.join(directory, name + ".png"), images[name], check)
    return failed


def contact_sheet(path: str, images: dict, columns: int = 10, scale: int = 5, title: str = "") -> None:
    """A labelled review sheet: every sprite on a dark, a mid and a pale ground."""
    from preview import INK, PAPER, text

    names = list(images)
    grounds = ((40, 40, 48, 255), (124, 128, 136, 255), (198, 198, 198, 255))
    cell_w = max(SIZE * scale + 10, 4 * 2 * 9)
    cell_h = (SIZE * scale + 4) * len(grounds) + 30
    rows = (len(names) + columns - 1) // columns
    sheet = Image(columns * cell_w + 10, rows * cell_h + 30, PAPER)
    text(sheet, 8, 8, title, INK, 2)
    for index, name in enumerate(names):
        x0 = 8 + (index % columns) * cell_w
        y0 = 28 + (index // columns) * cell_h
        for g, ground in enumerate(grounds):
            gy = y0 + g * (SIZE * scale + 4)
            sheet.rect(x0, gy, SIZE * scale, SIZE * scale, ground)
            sheet.blit(images[name], x0, gy, scale)
        label_y = y0 + len(grounds) * (SIZE * scale + 4)
        short = name.replace("_spawn_egg", "").replace("_", " ")
        for line in range(2):
            text(sheet, x0, label_y + line * 8, short[line * 20:(line + 1) * 20], INK, 1)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(sheet.to_png())
    print("sheet " + path)
