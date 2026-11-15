#!/usr/bin/env python3
"""Generate NeroCreatures' programmer-art item and entity textures.

Purely additive: a texture that already exists on disk is never overwritten, so hand-drawn
replacements survive every rerun and this script only fills gaps. Run it directly or through the
Gradle `genAssets` task.

    python tools/gen_textures.py                # write missing textures
    python tools/gen_textures.py --multiloader  # same (flag kept for the shared gradle task)
    python tools/gen_textures.py --force        # rewrite generated textures the art kit does not own
    python tools/gen_textures.py --list         # print what would be written, write nothing

`--force` never touches real art: an item texture painted by `tools/art/paint/items.py` and an
entity texture whose species has an art spec (`art/models/<id>.json`) are always kept.

No third-party dependency: PNGs are encoded here with `zlib` + `struct`, so the script runs on a
bare Python 3 install (Pillow is deliberately not required).

Item textures are 16x16 RGBA; entity textures are 64x64 RGBA (matching the model layers, which are
all built with `LayerDefinition.create(mesh, 64, 64)`). Everything is deterministic (each motif seeds
its own PRNG) and intentionally simple — this is placeholder art whose only job is to make each drop
and each creature instantly distinguishable. The real art pass replaces these files wholesale.

Entity sheets are laid out to match the models rather than to be UV-correct: the cube models use
`texOffs(0, 0)` for body parts and `texOffs(0, 32)` for heads and limbs, so each sheet is a body
field in the top half and a limb field in the bottom half. Overlapping UVs are fine for placeholder
art and are what lets the models stay this short.
"""

from __future__ import annotations

import argparse
import os
import random
import struct
import sys
import zlib

SIZE = 16
ENTITY_SIZE = 64

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_ASSETS = os.path.join(
    REPO_ROOT, "common", "src", "main", "resources", "assets", "nerocreatures", "textures"
)
TEXTURE_DIR = os.path.join(_ASSETS, "item")
ENTITY_TEXTURE_DIR = os.path.join(_ASSETS, "entity")

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


def drone_shell(c: Canvas, rng: random.Random) -> None:
    """The packed-up Terraforming Drone: a folded green-grey casing with a seed-hopper window."""
    c.rect(3, 4, 12, 12, (96, 110, 96, 255))
    c.outline(3, 4, 12, 12, (44, 56, 44, 255))
    # Folded legs, tucked under the casing.
    c.rect(4, 13, 5, 13, (72, 78, 84, 255))
    c.rect(10, 13, 11, 13, (72, 78, 84, 255))
    # Carry handle.
    c.rect(6, 2, 9, 3, (72, 78, 84, 255))
    # Hopper window, in the plasma-cell magenta so the recipe reads off the item.
    c.rect(6, 6, 9, 9, (58, 46, 66, 255))
    c.rect(6, 7, 9, 8, (222, 62, 176, 255))
    c.speckle(rng, 6, (140, 156, 140, 255), (4, 5, 11, 11))


def creature_saddle(c: Canvas, rng: random.Random) -> None:
    """The mount saddle: a stalker-hide seat on a sinew girth, iron rings either side."""
    # Seat, pale regolith hide like the Lunar Stalker drop it is made from.
    c.rect(3, 5, 12, 9, (150, 132, 112, 255))
    c.outline(3, 5, 12, 9, (84, 70, 58, 255))
    # Raised pommel and cantle.
    c.rect(3, 3, 4, 5, (120, 104, 88, 255))
    c.rect(11, 3, 12, 5, (120, 104, 88, 255))
    # Sinew girth hanging down.
    c.rect(5, 10, 5, 13, (196, 92, 74, 255))
    c.rect(10, 10, 10, 13, (196, 92, 74, 255))
    # Iron stirrup rings.
    c.rect(4, 13, 6, 14, (176, 182, 190, 255))
    c.rect(9, 13, 11, 14, (176, 182, 190, 255))
    c.speckle(rng, 6, (182, 166, 146, 255), (4, 6, 11, 8))

def apex_trophy(c: Canvas, rng: random.Random) -> None:
    """The planet-boss token: a scorched horn-crown mounted on a slag plinth, ember-lit."""
    # Plinth.
    c.rect(3, 12, 12, 13, (48, 40, 38, 255))
    c.rect(4, 11, 11, 11, (74, 62, 56, 255))
    # Crown of vents, tallest in the middle.
    for x, top in ((4, 7), (6, 5), (9, 5), (11, 7)):
        c.rect(x, top, x + 1, 10, (58, 46, 44, 255))
    c.rect(4, 9, 11, 10, (72, 56, 52, 255))
    # Ember core, still hot.
    c.disc(7.5, 8.5, 1.9, (214, 74, 26, 255))
    c.disc(7.5, 8.5, 1.0, (255, 190, 96, 255))
    c.speckle(rng, 8, (240, 128, 44, 255), (4, 6, 11, 11))
    c.speckle(rng, 4, (30, 24, 22, 255), (3, 11, 12, 13))


def android_core(c: Canvas, rng: random.Random) -> None:
    c.rect(3, 3, 12, 12, (128, 134, 142, 255))
    c.outline(3, 3, 12, 12, (74, 78, 84, 255))
    c.rect(5, 5, 10, 10, (48, 52, 58, 255))
    c.disc(7.5, 7.5, 2.4, (244, 138, 44, 255))
    c.disc(7.5, 7.5, 1.2, (255, 224, 160, 255))
    c.speckle(rng, 4, (176, 182, 190, 255), (4, 4, 11, 11))


# --- spawn eggs -----------------------------------------------------------
# One per creature, in that creature's palette, so a full creative tab reads at a glance.

# Per-row x range of a classic 16x16 spawn-egg silhouette: pointed top, round bottom.
_EGG_ROWS = {
    2: (7, 8), 3: (6, 9), 4: (6, 9), 5: (5, 10), 6: (5, 10), 7: (4, 11), 8: (4, 11),
    9: (4, 11), 10: (4, 11), 11: (4, 11), 12: (5, 10), 13: (5, 10), 14: (6, 9),
}


def _spawn_egg(base: Colour, spot: Colour, outline: Colour):
    """Builds a motif function for a spawn egg in the given palette."""

    def motif(c: Canvas, rng: random.Random) -> None:
        rows = sorted(_EGG_ROWS)
        for y, (x0, x1) in _EGG_ROWS.items():
            for x in range(x0, x1 + 1):
                edge = (x in (x0, x1) or y in (rows[0], rows[-1])
                        or (y - 1) not in _EGG_ROWS or (y + 1) not in _EGG_ROWS)
                c.set(x, y, outline if edge else (spot if rng.random() < 0.32 else base))
        # A two-pixel highlight near the top-left reads as a rounded shell.
        for hx, hy in ((7, 4), (6, 5)):
            c.set(hx, hy, _lighten(base, 46))

    return motif


def _lighten(colour: Colour, amount: int) -> Colour:
    r, g, b, a = colour
    return (min(255, r + amount), min(255, g + amount), min(255, b + amount), a)


def _darken(colour: Colour, amount: int) -> Colour:
    r, g, b, a = colour
    return (max(0, r - amount), max(0, g - amount), max(0, b - amount), a)


# Creature palettes, shared by the spawn eggs and the entity sheets so the two always agree.
PALETTES = {
    # Void Crawler — condensed darkness with a violet sheen.
    "void_crawler": ((58, 34, 86, 255), (146, 92, 214, 255), (26, 14, 40, 255)),
    # Lunar Stalker — pale regolith hide with a bone-white underside.
    "lunar_stalker": ((150, 148, 156, 255), (214, 212, 206, 255), (74, 72, 80, 255)),
    # Asteroid Worm — dull rock-green chitin with darker ring seams.
    "asteroid_worm": ((84, 96, 68, 255), (128, 142, 100, 255), (40, 48, 32, 255)),
    # Plasma Slime — magenta charge inside a dark shell.
    "plasma_slime": ((198, 58, 158, 255), (255, 176, 232, 255), (62, 24, 58, 255)),
    # Crystal Golem — the refined-crystal teal, on a darker mineral body.
    "crystal_golem": ((46, 132, 146, 255), (172, 244, 252, 255), (20, 62, 72, 255)),
    # Space Pirate — void-purple fatigues with the contraband gold trim.
    "space_pirate": ((72, 62, 90, 255), (198, 168, 82, 255), (32, 26, 44, 255)),
    # Rogue Drone — light machine grey with a hot orange lens.
    "rogue_drone": ((142, 148, 156, 255), (244, 138, 44, 255), (58, 62, 68, 255)),
    # Rogue Android — the same machine family, heavier and darker.
    "rogue_android": ((92, 98, 108, 255), (244, 138, 44, 255), (38, 42, 48, 255)),
    # Glacite Wisp — pale glacier blue with a white ice-lattice sheen.
    "glacite_wisp": ((132, 186, 214, 255), (226, 246, 255, 255), (52, 96, 126, 255)),
    # Xertz Forager — Greenxertz moss green with a xertz-quartz crest.
    "xertz_forager": ((78, 132, 74, 255), (176, 232, 214, 255), (32, 62, 34, 255)),
    # Terraforming Drone — the machine family again, but green-tinted: a tool, not a threat.
    "terraforming_drone": ((96, 110, 96, 255), (176, 232, 214, 255), (44, 56, 44, 255)),
    # Cinder Tyrant — cooled slag with the ember glow still in the cracks. Cindara's palette:
    # near-black basalt, hot orange, ash outline.
    "cinder_tyrant": ((62, 44, 40, 255), (240, 128, 44, 255), (24, 16, 14, 255)),
    # 0.5.0 Luna: moonlit moth, ink-dark shrike, ore-flecked tortoise.
    "lumen_moth": ((206, 214, 168, 255), (250, 246, 170, 255), (96, 100, 72, 255)),
    "echo_shrike": ((54, 58, 74, 255), (150, 200, 230, 255), (22, 24, 32, 255)),
    "lode_tortoise": ((112, 108, 100, 255), (210, 160, 70, 255), (56, 52, 48, 255)),
    # 0.5.0 Mars: rust-red dune runners and a pale sand column.
    "dune_loper": ((176, 98, 62, 255), (232, 190, 140, 255), (88, 44, 28, 255)),
    "strata_hound": ((134, 84, 64, 255), (120, 220, 200, 255), (62, 36, 28, 255)),
    "dust_devil": ((206, 150, 110, 255), (250, 226, 190, 255), (128, 80, 56, 255)),
    # 0.5.0 Cindara: basalt shell with a furnace glow, and an ash-grey wader with ember legs.
    "slag_beetle": ((60, 50, 48, 255), (250, 150, 50, 255), (26, 20, 18, 255)),
    "magma_wader": ((96, 82, 78, 255), (255, 96, 32, 255), (40, 30, 28, 255)),
    # 0.5.0 Glacira: frost-white wool over slate horns, and a snow-pale lurker with ice-blue spines.
    "rime_yak": ((214, 226, 234, 255), (120, 180, 220, 255), (70, 78, 92, 255)),
    "frost_lurker": ((196, 214, 226, 255), (110, 200, 240, 255), (60, 80, 100, 255)),
    # 0.5.0 Greenxertz: spore-violet drifter, flower-capped host, moss herd, sky-grey leviathan.
    "spore_drifter": ((150, 112, 176, 255), (214, 240, 150, 255), (64, 44, 80, 255)),
    "bloom_host": ((92, 120, 64, 255), (236, 110, 150, 255), (40, 54, 30, 255)),
    "mossback": ((86, 116, 62, 255), (150, 190, 96, 255), (52, 44, 34, 255)),
    "drift_leviathan": ((120, 140, 156, 255), (200, 230, 220, 255), (54, 64, 76, 255)),
    # 0.5.0 Nyxara: twilight-violet stag with prism antlers, a dark charger with a mirror fan.
    "prism_stag": ((92, 80, 124, 255), (190, 240, 250, 255), (40, 34, 58, 255)),
    "shardback": ((58, 60, 76, 255), (220, 230, 245, 255), (24, 24, 34, 255)),
    # 0.5.0 Asteroid: rust-iron crab with magnet-red claws, a violet leech disc.
    "magnet_crab": ((120, 96, 84, 255), (220, 60, 50, 255), (50, 40, 36, 255)),
    "gravity_leech": ((70, 60, 96, 255), (180, 140, 255, 255), (30, 26, 44, 255)),
    # 0.5.0 Wrecks and Pirates: salvage-crate grey with hazard yellow, a crackling cyan swarm, a
    # rust-and-scrap hulk, the captain's long coat with brass trim.
    "cache_mimic": ((118, 122, 128, 255), (240, 200, 40, 255), (52, 54, 58, 255)),
    "static_swarm": ((40, 60, 90, 255), (140, 230, 255, 255), (18, 24, 40, 255)),
    "scrap_hulk": ((104, 90, 78, 255), (255, 74, 46, 255), (44, 38, 34, 255)),
    "pirate_captain": ((60, 44, 50, 255), (220, 170, 70, 255), (26, 20, 24, 255)),
    # 0.5.0 Deep space: a midnight manta with starlight speckle.
    "void_manta": ((30, 26, 54, 255), (200, 190, 255, 255), (12, 10, 24, 255)),
    # 0.5.0 Apex bosses (eggs only: their entity sheets come from the art specs).
    "rime_matriarch": ((120, 170, 205, 255), (235, 250, 255, 255), (40, 70, 100, 255)),
    "bloom_sovereign": ((70, 120, 60, 255), (240, 130, 200, 255), (30, 50, 26, 255)),
    "facet_colossus": ((80, 64, 120, 255), (210, 190, 255, 255), (30, 24, 50, 255)),
}


def lumen_dust(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 9.0, 4.6, (176, 168, 96, 255))
    c.disc(7.5, 9.0, 3.2, (236, 230, 140, 255))
    c.speckle(rng, 14, (255, 255, 214, 255), (2, 3, 13, 13))


def resonant_membrane(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 7.5, 5.8, (70, 88, 120, 255))
    c.disc(7.5, 7.5, 4.6, (120, 160, 200, 255))
    c.disc(7.5, 7.5, 2.6, (70, 88, 120, 255))
    c.disc(7.5, 7.5, 1.4, (190, 226, 250, 255))


def lode_scute(c: Canvas, rng: random.Random) -> None:
    c.diamond(7, 8, 6, (84, 80, 74, 255))
    c.diamond(7, 8, 4, (126, 120, 110, 255))
    c.speckle(rng, 6, (214, 164, 72, 255), (3, 4, 11, 12))
    c.speckle(rng, 3, (110, 210, 220, 255), (4, 5, 10, 11))


def charged_sand(c: Canvas, rng: random.Random) -> None:
    c.rect(3, 9, 12, 13, (176, 92, 56, 255))
    c.rect(5, 7, 10, 8, (176, 92, 56, 255))
    c.speckle(rng, 12, (222, 140, 92, 255), (3, 7, 12, 13))
    for x, y in ((4, 4), (5, 5), (6, 4), (7, 3), (9, 5), (10, 4), (11, 5)):
        c.set(x, y, (170, 220, 255, 255))


def slag_carapace(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.8, (40, 32, 30, 255))
    c.disc(7.5, 8.5, 4.4, (66, 54, 50, 255))
    c.rect(7, 3, 8, 14, (40, 32, 30, 255))
    c.speckle(rng, 10, (244, 140, 48, 255), (3, 4, 12, 13))


def heat_gland(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.0, (150, 40, 24, 255))
    c.disc(7.0, 8.0, 3.4, (232, 96, 36, 255))
    c.disc(6.5, 7.0, 1.6, (255, 210, 110, 255))
    c.rect(7, 2, 8, 3, (110, 30, 20, 255))


def rime_wool(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.4, (176, 196, 210, 255))
    c.disc(7.0, 8.0, 4.2, (226, 236, 244, 255))
    c.speckle(rng, 10, (150, 200, 230, 255), (3, 4, 12, 13))


def coolant_milk(c: Canvas, rng: random.Random) -> None:
    c.rect(4, 6, 11, 13, (120, 124, 132, 255))
    c.rect(5, 7, 10, 12, (206, 236, 250, 255))
    c.rect(5, 7, 10, 8, (150, 214, 245, 255))
    c.rect(3, 5, 12, 5, (90, 94, 102, 255))


def cryo_fang(c: Canvas, rng: random.Random) -> None:
    for i in range(8):
        c.rect(6 + i // 3, 3 + i, 9 - i // 3, 3 + i, (196, 232, 250, 255))
    c.rect(7, 3, 8, 9, (240, 252, 255, 255))
    c.rect(6, 11, 9, 12, (110, 170, 210, 255))


def drifter_spores(c: Canvas, rng: random.Random) -> None:
    c.rect(5, 6, 10, 13, (170, 190, 200, 255))
    c.rect(6, 7, 9, 12, (190, 150, 214, 255))
    c.rect(6, 3, 9, 5, (150, 110, 80, 255))
    c.speckle(rng, 6, (230, 250, 170, 255), (6, 7, 9, 12))


def bloom_heart(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.0, (180, 60, 110, 255))
    c.disc(7.0, 8.0, 3.4, (240, 120, 160, 255))
    c.disc(6.5, 7.0, 1.4, (255, 220, 120, 255))
    c.rect(7, 2, 8, 3, (70, 110, 50, 255))


def mossback_hide(c: Canvas, rng: random.Random) -> None:
    c.rect(3, 4, 12, 12, (110, 80, 54, 255))
    c.rect(4, 3, 11, 13, (110, 80, 54, 255))
    c.speckle(rng, 16, (110, 150, 70, 255), (3, 3, 12, 13))


def raw_mossback(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.0, (196, 70, 70, 255))
    c.disc(7.5, 8.5, 3.0, (230, 120, 110, 255))
    c.rect(11, 5, 13, 6, (232, 226, 214, 255))


def cooked_mossback(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.0, (130, 70, 40, 255))
    c.disc(7.5, 8.5, 3.0, (176, 110, 60, 255))
    c.rect(11, 5, 13, 6, (232, 226, 214, 255))


def leviathan_barnacle(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 9.0, 5.2, (110, 120, 128, 255))
    c.disc(7.5, 8.0, 3.6, (170, 182, 186, 255))
    c.disc(7.5, 7.5, 1.6, (50, 60, 70, 255))
    c.speckle(rng, 6, (200, 230, 220, 255), (3, 4, 12, 13))


def prism_antler(c: Canvas, rng: random.Random) -> None:
    for x, y in ((4, 13), (5, 12), (6, 11), (7, 10), (8, 9), (9, 8), (10, 7), (11, 6), (12, 5),
                 (8, 8), (8, 7), (8, 6), (7, 5), (11, 4), (12, 3), (6, 4)):
        c.set(x, y, (190, 240, 250, 255))
    for x, y in ((5, 13), (6, 12), (7, 11), (9, 9), (10, 8), (12, 6)):
        c.set(x, y, (140, 120, 200, 255))


def mirror_scale(c: Canvas, rng: random.Random) -> None:
    c.diamond(7, 8, 6, (110, 116, 136, 255))
    c.diamond(7, 8, 4, (220, 230, 245, 255))
    c.rect(5, 6, 6, 7, (255, 255, 255, 255))


def graviton_node(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.0, 5.0, (70, 56, 110, 255))
    c.disc(7.5, 8.0, 3.2, (150, 120, 230, 255))
    c.disc(7.5, 8.0, 1.4, (235, 225, 255, 255))
    c.speckle(rng, 6, (190, 170, 255, 255), (2, 2, 13, 13))


def static_jar(c: Canvas, rng: random.Random) -> None:
    c.rect(5, 5, 10, 13, (170, 200, 214, 255))
    c.rect(6, 6, 9, 12, (40, 70, 110, 255))
    c.rect(6, 3, 9, 4, (120, 90, 60, 255))
    c.speckle(rng, 7, (150, 240, 255, 255), (6, 6, 9, 12))


def field_scanner(c: Canvas, rng: random.Random) -> None:
    # Placeholder until the item art pass paints it: a handheld scanner with a teal screen.
    c.rect(4, 2, 11, 13, (58, 66, 84, 255))
    c.outline(4, 2, 11, 13, (28, 32, 44, 255))
    c.rect(5, 3, 10, 8, (20, 40, 52, 255))
    c.rect(6, 4, 9, 7, (61, 220, 200, 255))
    c.set(7, 5, (220, 255, 250, 255))
    c.rect(6, 10, 7, 11, (255, 183, 77, 255))
    c.rect(9, 10, 9, 11, (82, 217, 242, 255))
    c.rect(7, 14, 8, 15, (40, 46, 60, 255))


def captains_ledger(c: Canvas, rng: random.Random) -> None:
    c.rect(3, 3, 12, 13, (96, 52, 40, 255))
    c.rect(4, 4, 11, 12, (130, 74, 52, 255))
    c.rect(4, 4, 4, 12, (70, 36, 28, 255))
    c.rect(6, 7, 10, 8, (220, 170, 70, 255))
    c.set(8, 10, (220, 60, 50, 255))


# --- 0.5.0 boss signature drops and sigils ----------------------------------


def matriarch_frostcore(c: Canvas, rng: random.Random) -> None:
    c.diamond(7, 8, 6, (60, 110, 160, 255))
    c.diamond(7, 8, 4, (150, 210, 245, 255))
    c.disc(7.5, 8.0, 1.5, (240, 252, 255, 255))
    c.speckle(rng, 6, (220, 246, 255, 255), (2, 2, 13, 13))


def sovereign_seed(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 9.0, 5.0, (70, 110, 50, 255))
    c.disc(7.5, 9.0, 3.4, (150, 200, 90, 255))
    c.rect(7, 2, 8, 4, (90, 150, 70, 255))
    c.disc(7.5, 9.0, 1.4, (240, 120, 190, 255))
    c.speckle(rng, 5, (230, 250, 170, 255), (3, 5, 12, 13))


def colossus_facet(c: Canvas, rng: random.Random) -> None:
    for y in range(3, 14):
        half = (y - 3) // 2 if y < 9 else (13 - y)
        c.rect(7 - half, y, 8 + half, y, (110, 80, 170, 255))
    c.rect(6, 6, 9, 10, (200, 170, 250, 255))
    c.rect(6, 6, 7, 7, (255, 255, 255, 255))


def tyrant_ember(c: Canvas, rng: random.Random) -> None:
    c.disc(7.5, 8.5, 5.2, (60, 40, 36, 255))
    c.disc(7.5, 8.5, 3.6, (214, 74, 26, 255))
    c.disc(7.5, 8.5, 2.0, (255, 190, 96, 255))
    c.speckle(rng, 8, (240, 128, 44, 255), (2, 3, 13, 14))


def _sigil(rim: Colour, glyph: Colour, core: Colour):
    """A round stone token carved with the boss's habitat accent; one per boss."""

    def motif(c: Canvas, rng: random.Random) -> None:
        c.disc(7.5, 7.5, 6.4, (40, 36, 46, 255))
        c.disc(7.5, 7.5, 5.4, rim)
        c.disc(7.5, 7.5, 3.6, (40, 36, 46, 255))
        c.rect(7, 3, 8, 12, glyph)
        c.rect(3, 7, 12, 8, glyph)
        c.disc(7.5, 7.5, 1.4, core)

    return motif


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
    "drone_shell": drone_shell,
    "creature_saddle": creature_saddle,
    "apex_trophy": apex_trophy,
    "lumen_dust": lumen_dust,
    "resonant_membrane": resonant_membrane,
    "lode_scute": lode_scute,
    "charged_sand": charged_sand,
    "slag_carapace": slag_carapace,
    "heat_gland": heat_gland,
    "rime_wool": rime_wool,
    "coolant_milk": coolant_milk,
    "cryo_fang": cryo_fang,
    "drifter_spores": drifter_spores,
    "bloom_heart": bloom_heart,
    "mossback_hide": mossback_hide,
    "raw_mossback": raw_mossback,
    "cooked_mossback": cooked_mossback,
    "leviathan_barnacle": leviathan_barnacle,
    "prism_antler": prism_antler,
    "mirror_scale": mirror_scale,
    "graviton_node": graviton_node,
    "static_jar": static_jar,
    "field_scanner": field_scanner,
    "captains_ledger": captains_ledger,
    "matriarch_frostcore": matriarch_frostcore,
    "sovereign_seed": sovereign_seed,
    "colossus_facet": colossus_facet,
    "tyrant_ember": tyrant_ember,
    "cinder_tyrant_sigil": _sigil((120, 70, 50, 255), (240, 128, 44, 255), (255, 200, 110, 255)),
    "rime_matriarch_sigil": _sigil((80, 120, 150, 255), (170, 225, 250, 255), (245, 252, 255, 255)),
    "bloom_sovereign_sigil": _sigil((70, 110, 60, 255), (160, 220, 100, 255), (240, 130, 200, 255)),
    "facet_colossus_sigil": _sigil((90, 70, 130, 255), (200, 170, 250, 255), (255, 255, 255, 255)),
}

# Creatures that have a palette (and therefore an entity sheet) but deliberately no spawn egg. The
# Terraforming Drone is only ever created by deploying its crafted shell, which is what binds it to
# an owner — see registry/ModSpawnEggs for why an egg would be wrong.
NO_SPAWN_EGG = {"terraforming_drone"}

for _creature, (_base, _spot, _outline) in PALETTES.items():
    if _creature not in NO_SPAWN_EGG:
        MOTIFS[_creature + "_spawn_egg"] = _spawn_egg(_base, _spot, _outline)

# Stage 4 creature eggs (laid by egg-birth species, hatched in an Incubator): a paler shell in the
# species palette so an egg never reads as its spawn egg.
EGG_SPECIES = ("lumen_moth", "lode_tortoise", "slag_beetle", "magnet_crab", "spore_drifter", "void_manta")

for _creature in EGG_SPECIES:
    _base, _spot, _outline = PALETTES[_creature]
    MOTIFS[_creature + "_egg"] = _spawn_egg(_lighten(_base, 40), _spot, _darken(_outline, 10))


# --- entity sheets --------------------------------------------------------
# 64x64. Top half (y 0..31) is the body field the models sample with texOffs(0, 0); bottom half
# (y 32..63) is the limb/head field they sample with texOffs(0, 32).


def _entity_sheet(base: Colour, accent: Colour, dark: Colour, band_step: int, speckles: int):
    """Builds a motif function for one creature's entity sheet."""

    def motif(c: Canvas, rng: random.Random) -> None:
        limb = _darken(base, 22)
        # Body field.
        c.rect(0, 0, ENTITY_SIZE - 1, 31, base)
        for y in range(0, 32, band_step):
            c.rect(0, y, ENTITY_SIZE - 1, y, dark)
        c.speckle(rng, speckles, accent, (0, 0, ENTITY_SIZE - 1, 31))
        c.speckle(rng, speckles // 2, dark, (0, 0, ENTITY_SIZE - 1, 31))
        # Limb / head field: the same palette a shade down, banded the other way so a limb never
        # looks like a stray piece of torso.
        c.rect(0, 32, ENTITY_SIZE - 1, ENTITY_SIZE - 1, limb)
        for x in range(0, ENTITY_SIZE, band_step):
            c.rect(x, 32, x, ENTITY_SIZE - 1, dark)
        c.speckle(rng, speckles, _lighten(limb, 20), (0, 32, ENTITY_SIZE - 1, ENTITY_SIZE - 1))
        # Two eye spots near the top-left of the limb field, which is where the models put faces.
        for cx in (5, 12):
            c.rect(cx, 36, cx + 1, 37, accent)
            c.set(cx, 36, _lighten(accent, 40))

    return motif


ENTITY_MOTIFS = {
    "void_crawler": _entity_sheet(*PALETTES["void_crawler"], band_step=6, speckles=40),
    "lunar_stalker": _entity_sheet(*PALETTES["lunar_stalker"], band_step=8, speckles=30),
    "asteroid_worm": _entity_sheet(*PALETTES["asteroid_worm"], band_step=4, speckles=50),
    "plasma_slime": _entity_sheet(*PALETTES["plasma_slime"], band_step=5, speckles=60),
    # Stage 4. Band step doubles as a "surface" cue: wide bands read as slabs (golem, android),
    # tight ones as fabric and panelling (pirate, drone).
    "crystal_golem": _entity_sheet(*PALETTES["crystal_golem"], band_step=10, speckles=45),
    "space_pirate": _entity_sheet(*PALETTES["space_pirate"], band_step=6, speckles=25),
    "rogue_drone": _entity_sheet(*PALETTES["rogue_drone"], band_step=4, speckles=20),
    "rogue_android": _entity_sheet(*PALETTES["rogue_android"], band_step=8, speckles=30),
    # Stage 5. The two pets are small and bright; the drone reuses the machine banding of its
    # hostile cousins so the family reads, but in the green of a tool rather than a threat.
    "glacite_wisp": _entity_sheet(*PALETTES["glacite_wisp"], band_step=3, speckles=55),
    "xertz_forager": _entity_sheet(*PALETTES["xertz_forager"], band_step=7, speckles=35),
    "terraforming_drone": _entity_sheet(*PALETTES["terraforming_drone"], band_step=4, speckles=20),
    # Stage 6. The boss gets the widest bands in the mod — big slabs of cooled slag — and the
    # heaviest speckle count, which reads as the ember cracks running through them.
    "cinder_tyrant": _entity_sheet(*PALETTES["cinder_tyrant"], band_step=12, speckles=70),
}


def art_owned_items() -> set:
    """Item texture names the art kit paints (``tools/art/paint/items.py``): never placeholder-written."""
    sys.path.insert(0, os.path.join(REPO_ROOT, "tools", "art", "paint"))
    sys.path.insert(0, os.path.join(REPO_ROOT, "tools", "art"))
    sys.dont_write_bytecode = True
    import items  # noqa: E402  (standard library only)
    return set(items.ITEMS)


def art_owned_entities() -> set:
    """Entity ids with an art spec (``art/models/<id>.json``): their textures are the art kit's."""
    models = os.path.join(REPO_ROOT, "art", "models")
    return {n[:-5] for n in os.listdir(models) if n.endswith(".json")} if os.path.isdir(models) else set()


def _write_set(motifs, directory: str, size: int, args, owned: set = frozenset()) -> tuple:
    """Renders one motif set into `directory`. Returns (written, skipped)."""
    os.makedirs(directory, exist_ok=True)
    written = 0
    skipped = 0
    for name, motif in sorted(motifs.items()):
        path = os.path.join(directory, name + ".png")
        if os.path.exists(path) and (not args.force or name in owned):
            skipped += 1
            continue
        if args.list:
            print("would write " + path)
            written += 1
            continue
        canvas = Canvas(size)
        motif(canvas, random.Random(name))
        with open(path, "wb") as handle:
            handle.write(canvas.to_png())
        print("wrote " + os.path.relpath(path, REPO_ROOT))
        written += 1
    return written, skipped


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--multiloader", action="store_true",
                        help="accepted for parity with the shared gradle task; has no effect")
    parser.add_argument("--force", action="store_true", help="overwrite existing textures")
    parser.add_argument("--list", action="store_true", help="report only, write nothing")
    args = parser.parse_args(argv)

    owned_items = art_owned_items() if args.force else frozenset()
    owned_entities = art_owned_entities() if args.force else frozenset()
    items = _write_set(MOTIFS, TEXTURE_DIR, SIZE, args, owned_items)
    entities = _write_set(ENTITY_MOTIFS, ENTITY_TEXTURE_DIR, ENTITY_SIZE, args, owned_entities)
    written = items[0] + entities[0]
    skipped = items[1] + entities[1]
    print("gen_textures: {} written, {} already present".format(written, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
