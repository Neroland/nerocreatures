#!/usr/bin/env python3
"""Worn-gear armour layers: the 64x32 humanoid textures for the three gear items that are worn.

    python tools/art/paint/equipment.py           # write textures/entity/equipment/<layer>/<id>.png
    python tools/art/paint/equipment.py --check   # prove the committed files match

1.21.4+ only (the items carry an ``Equippable`` with ``asset_id`` = ``nerocreatures:<id>``, and
``assets/nerocreatures/equipment/<id>.json`` names these textures). 1.21.1 gear is not wearable, so it
has no layer. Colours come from each item's own palette ramps (``items.py``), so the worn piece reads
as the item: the Lurker Cloak in Glacira indigo and wool, the Heat Gland Greaves in Cindara clinker with
ember vents, the Graviton Boots in asteroid gunmetal with a lit graviton band.

These are first-pass layers drawn by rule (Opus, Stage 5 wiring); Fable may repaint them in place.

Layout is the vanilla humanoid armour sheet (64x32): head 0..32 x 0..16, legs at (0,16), body at
(16,16), arms at (40,16); each box face is cut from its ``(u, v)`` origin like a vanilla cube.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from _sprite import ASSETS, colour  # noqa: E402
from pixelkit import Image, write_or_check  # noqa: E402

EQUIPMENT = os.path.join(ASSETS, "textures", "entity", "equipment")
LAYERS = {}


def box_faces(u: int, v: int, w: int, h: int, d: int) -> dict:
    """Vanilla cube UVs: face name to (x, y, width, height) on the sheet."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


BODY = box_faces(16, 16, 8, 12, 4)
ARM = box_faces(40, 16, 4, 12, 4)
LEG = box_faces(0, 16, 4, 12, 4)
SIDES = ("right", "front", "left", "back")


class Layer:
    def __init__(self, habitat: str) -> None:
        self.habitat = habitat
        self.image = Image(64, 32)

    def fill(self, rect, ramp: str, step: int, y0: int = 0, y1: int = None) -> None:
        """Fills rows ``y0..y1`` (relative to the face) of one face, shaded: lit top row, dark bottom."""
        x, y, w, h = rect
        y1 = h if y1 is None else y1
        for yy in range(y0, y1):
            s = step + (1 if yy == y0 else 0) - (1 if yy == y1 - 1 else 0)
            for xx in range(w):
                self.image.set(x + xx, y + yy, colour(self.habitat, ramp, s))

    def px(self, rect, fx: int, fy: int, ramp: str, step: int) -> None:
        x, y, _, _ = rect
        self.image.set(x + fx, y + fy, colour(self.habitat, ramp, step))


def cloak() -> Image:
    """Lurker Cloak (chest): an indigo mantle over body and arms, wool collar, a frost clasp."""
    t = Layer("glacira")
    for name in SIDES:
        t.fill(BODY[name], "indigo", 2)
        t.fill(BODY[name], "wool", 2, 0, 2)
        t.fill(ARM[name], "indigo", 2, 0, 9)
        t.fill(ARM[name], "wool", 3, 0, 1)
    t.fill(BODY["top"], "wool", 3)
    t.fill(ARM["top"], "wool", 3)
    for fy in range(3, 12):                       # the cloak's fold lines
        t.px(BODY["back"], 2, fy, "indigo", 1)
        t.px(BODY["back"], 5, fy, "indigo", 3)
        t.px(BODY["front"], 3, fy, "indigo", 1)
    t.px(BODY["front"], 3, 2, "accent", 3)        # clasp
    t.px(BODY["front"], 4, 2, "accent", 4)
    return t.image


def greaves() -> Image:
    """Heat Gland Greaves (legs): clinker plates with ash cuffs and ember vents; a belt on the waist."""
    t = Layer("cindara")
    for name in SIDES:
        t.fill(LEG[name], "clinker", 2, 0, 10)
        t.fill(LEG[name], "ash", 2, 0, 1)
        t.fill(LEG[name], "ash", 2, 9, 10)
    for vent in ("front", "right", "left"):
        t.px(LEG[vent], 1, 4, "accent", 3)
        t.px(LEG[vent], 2, 4, "accent", 4)
        t.px(LEG[vent], 1, 5, "accent", 2)
    for name in SIDES:                            # waist belt (lowest body rows)
        t.fill(BODY[name], "ash", 2, 9, 12)
    t.px(BODY["front"], 3, 10, "accent", 3)
    t.px(BODY["front"], 4, 10, "accent", 3)
    return t.image


def boots() -> Image:
    """Graviton Boots (feet): gunmetal boots, rust soles, the graviton band lit along the ankle."""
    t = Layer("asteroid")
    for name in SIDES:
        t.fill(LEG[name], "gunmetal", 2, 6, 12)
        t.fill(LEG[name], "rust", 2, 11, 12)
        t.fill(LEG[name], "accent", 3, 8, 9)
    t.fill(LEG["bottom"], "rust", 1)
    t.px(LEG["front"], 1, 8, "accent", 4)
    t.px(LEG["front"], 2, 8, "accent", 4)
    return t.image


LAYERS[("humanoid", "lurker_cloak")] = cloak()
LAYERS[("humanoid_leggings", "heat_gland_greaves")] = greaves()
LAYERS[("humanoid", "graviton_boots")] = boots()


def main(argv: list) -> int:
    check = "--check" in argv
    failed = 0
    for (layer, name), image in sorted(LAYERS.items()):
        failed += not write_or_check(os.path.join(EQUIPMENT, layer, name + ".png"), image, check)
    print("equipment: %d layers, %s" % (len(LAYERS), "FAILED" if failed else "clean"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
