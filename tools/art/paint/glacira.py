#!/usr/bin/env python3
"""Glacira painters: one function per entity.

    python tools/art/paint/glacira.py            # paint and write
    python tools/art/paint/glacira.py --check    # prove the committed PNGs match

Glacira reads as white and pale blue ground, so every body leads with deep slate ``indigo``. The
frost-cream ``wool`` ramp is too close to the ground to carry a shape, so it is only ever the light
mass on top of a dark one (the yak's rime mantle, a hood, a frosted edge). Ice blue is the only glow
and sits where the cold is made: horn bands, eye stalks and fangs, the wisp's core, the crown.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")
# Raw steps for a cube whose part is pitched 90 degrees, so its faces do not point where they are named.
TOP, SIDE, FRONT, BELLY = 1, -1, 0, -2


def strands(face, cols, y0: int = 0, y1: int = None) -> None:
    """Hanging hair: 2-texel columns, each a (ramp, step) pair, repeated across the face."""
    y1 = face.h if y1 is None else y1
    for x in range(0, face.w, 2):
        ramp, step = cols[(x // 2) % len(cols)]
        face.rect(x, y0, 2, y1 - y0, ramp, step)


def rime_yak(p) -> None:
    """Dark mass: the indigo coat, curtain, horns and legs. Light mass: the cream rime mantle on the
    back, its drips down the curtain, the forelock and muzzle. Accent: eyes and the horn bands that
    make the cold aura. Stage 0 wears the lot; sheared (stage 1) shows the clipped barrel and legs."""

    # Barrel: its part is pitched 90 degrees, so south is the back, north the belly, up the chest.
    key = "barrel.barrel"
    p.face(key, "south").fill("indigo", 2 + TOP, raw=True)
    p.face(key, "south").rect(4, 0, 2, 15, "indigo", 2, raw=True)          # dorsal stripe
    p.face(key, "north").fill("indigo", 2 + BELLY, raw=True)
    for name in ("up", "down"):
        face = p.face(key, name)
        face.fill("indigo", 2, raw=True)
        face.row(0, "indigo", 3, raw=True)
        face.rect(0, 6, 10, 2, "indigo", 1, raw=True)
    for name in ("west", "east"):
        face = p.face(key, name)
        for zf in range(15):
            for yd in range(8):
                step = 1 if zf % 4 < 2 else 2                              # clipper bands
                if yd == 0:
                    step = 3
                if yd >= 6:
                    step = 0 if yd == 7 else 1
                face.px(yd if name == "west" else 7 - yd, zf, "indigo", step, raw=True)
    p.box("body.hump", "indigo", 2)
    p.top("body.hump").rect(3, 0, 2, 6, "indigo", 1)

    # Mantle: also pitched; south is its top.
    key = "mantle.mantle"
    top = p.face(key, "south")
    top.fill("wool", 4, raw=True)
    top.rect(5, 0, 2, 17, "wool", 2, raw=True)                              # parting
    for x, y in ((1, 2), (8, 4), (2, 7), (9, 10), (1, 12), (8, 14)):
        top.rect(x, y, 2, 2, "wool", 3, raw=True)
    p.face(key, "north").fill("wool", 0, raw=True)
    for name in ("up", "down"):
        p.face(key, name).fill("wool", 3, raw=True)
        p.face(key, name).row(-1, "wool", 2, raw=True)
    for name in ("west", "east"):
        face = p.face(key, name)
        face.fill("wool", 2, raw=True)
        face.col(0 if name == "west" else -1, "wool", 3, raw=True)

    # Curtain: one panel serves all four sides. South is the outside.
    key = "skirt_l.panel"
    out = p.face(key, "south")
    strands(out, (("indigo", 1), ("indigo", 2), ("indigo", 1), ("indigo", 0), ("indigo", 2)))
    out.rect(0, 0, 16, 2, "wool", 3)
    for x, drip in ((0, 3), (2, 5), (4, 2), (6, 6), (8, 3), (10, 4), (12, 7), (14, 3)):
        out.rect(x, 2, 2, drip, "wool", 2)
        out.rect(x, 2 + drip, 2, 1, "wool", 1)
    for x in (2, 8, 12):
        out.rect(x, 14, 2, 2, "indigo", 3)                                  # frosted hem tips
    out.row(-1, "indigo", 0)
    for x in (2, 8, 12):
        out.rect(x, 15, 2, 1, "wool", 1)
    p.face(key, "north").fill("indigo", 0)
    for name in ("west", "east", "down"):
        p.face(key, name).fill("indigo", 1, raw=True)
    p.face(key, "up").fill("wool", 3, raw=True)

    # Head.
    p.box("head.skull", "indigo", 2)
    front = p.face("head.skull", "north")
    front.rect(0, 0, 6, 2, "indigo", 3)
    front.rect(2, 2, 2, 4, "indigo", 1)
    for side in p.flanks("head.skull"):
        side.row(0, "indigo", 2)
        side.glow(1, 2, 2, 1)
        side.rect(0, 4, 5, 2, "indigo", 0)
    p.box("head.muzzle", "indigo", 3)
    p.face("head.muzzle", "north").fill("wool", 2)
    p.face("head.muzzle", "north").rect(0, 1, 4, 1, "indigo", 1)
    p.top("head.muzzle").rect(0, 0, 4, 1, "wool", 2)
    p.box("head.forelock", "wool", 3)
    p.face("head.forelock", "north").row(-1, "wool", 2)
    for key in ("ears.ear_l", "ears.ear_r"):
        p.box(key, "indigo", 1)
        p.face(key, "north").fill("indigo", 3)

    # Horns: dark so they carry at range; the rime band near each tip is the aura organ.
    p.box("horn_l.base", "indigo", 1)
    for face in p.each_face("horn_l.base", ("north", "south", "up", "down")):
        face.rect(0, 0, 2, face.h, "indigo", 3)
        face.rect(face.w - 2, 0, 2, face.h, "indigo", 3)
    p.box("horn_l.tip", "indigo", 1)
    for name in SIDES:
        face = p.face("horn_l.tip", name)
        face.rect(0, 0, 2, 2, "wool", 3)
        face.glow(0, 3, 2, 2)
    p.face("horn_l.tip", "up").fill("wool", 3)

    p.box("tail.tail", "indigo", 1)
    for name in SIDES:
        p.face("tail.tail", name).rect(0, 5, 2, 3, "wool", 2)
    p.face("tail.tail", "down").fill("wool", 2)
    p.box("leg_fl.leg", "indigo", 1)
    for name in SIDES:
        face = p.face("leg_fl.leg", name)
        face.rect(0, 0, 3, 2, "indigo", 2)
        face.rect(0, 7, 3, 2, "indigo", 0)


PAINTERS = {
    "rime_yak": rime_yak,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
