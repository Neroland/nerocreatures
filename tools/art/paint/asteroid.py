#!/usr/bin/env python3
"""Asteroid painters: one function per entity.

    python tools/art/paint/asteroid.py            # paint and write
    python tools/art/paint/asteroid.py --check    # prove the committed PNGs match

The belt reads as dark grey rock, so bodies are warm ``rust`` ochre and cold ``gunmetal`` against it,
with graviton blue as the only glow. Blue sits only where the pull is made: the crab's magnet tips,
the leech's sucker core and the worm's throat ring, plus eyes where a creature has them.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")


def plate(p, key: str, ramp: str, step: int, lip: bool = True, hem: bool = True) -> None:
    """One cube as a cast plate: flat fill, a lit top edge and a shadowed bottom edge on its sides."""
    p.box(key, ramp, step)
    for face in p.each_face(key, SIDES):
        if lip and face.h > 1:
            face.row(0, ramp, step + 1)
        if hem and face.h > 2:
            face.row(-1, ramp, step - 1)


def magnet_crab(p) -> None:
    """Mid mass: the rust shell, ribbed, darker under the rim. Dark mass: rust-shadow arms and legs.
    Light mass: gunmetal horseshoe magnets and the stolen plates stuck to the dome (one more per
    stage). Accent: the four magnet pole tips and the two eye-stalk tips."""

    plate(p, "body.shell", "rust", 2)
    plate(p, "body.dome", "rust", 3, hem=False)
    plate(p, "body.cap", "rust", 3, lip=False, hem=False)
    for key in ("body.shell", "body.dome", "body.cap"):
        p.under(key).fill("rust", 2)
    for side in p.flanks("body.shell"):
        for x in (1, 5):
            side.rect(x, 1, 2, 2, "rust", 3)                       # growth ribs
    front = p.face("body.shell", "north")
    front.rect(4, 2, 4, 2, "rust", 0)                              # mouth slot between the stalks
    for x in (1, 9):
        front.rect(x, 1, 2, 2, "rust", 3)
    back = p.face("body.shell", "south")
    for x in (2, 5, 8):
        back.rect(x, 1, 2, 2, "rust", 3)
    top = p.top("body.dome")
    top.rect(4, 0, 2, top.h, "rust", 2)                            # centre seam
    cap = p.top("body.cap")
    cap.rect(2, 1, 2, 2, "rust", 4)                                # worn crown

    # Eye stalk: gunmetal rod, lit tip. eye_r mirrors it.
    p.box("eye_l.stalk", "gunmetal", 1)
    for face in p.each_face("eye_l.stalk", SIDES):
        face.glow(0, 0, 1, 2, step=3, under=2)
    p.face("eye_l.stalk", "up").glow(0, 0, 1, 1, step=4, under=3)

    # Claw: dark arm, pale yoke, two pole pieces with a dark collar and a lit tip. claw_r mirrors it.
    p.box("claw_l.arm", "rust", 1)
    plate(p, "claw_l.yoke", "gunmetal", 2, hem=False)
    for key in ("claw_l.prong_in", "claw_l.prong_out"):
        p.box(key, "gunmetal", 3)
        for strip in p.around(key):
            strip.rect(2, 0, 1, strip.h, "gunmetal", 0)            # collar behind the pole face
        for side in p.flanks(key):
            side.glow(0, 0, 1, side.h, step=3, under=2)
        p.top(key).glow(0, 0, 2, 1, step=3, under=2)
        p.under(key).glow(0, 0, 2, 1, step=3, under=2)
        p.face(key, "north").glow(0, 0, 2, 2, step=4, under=3)

    # Stolen plates: bright scrap with a dark rivet bar, so each stage adds a light patch.
    for key in ("plate_1.plate", "plate_2.plate", "plate_3.plate"):
        p.box(key, "gunmetal", 3)
    p.top("plate_1.plate").rect(0, 1, 3, 1, "gunmetal", 1)
    p.top("plate_2.plate").rect(1, 0, 2, 2, "gunmetal", 1)
    side = p.face("plate_3.plate", "west")
    side.fill("gunmetal", 4)
    side.rect(1, 1, 2, 2, "gunmetal", 1)

    # Legs: rust-shadow stubs with a pale claw foot. legs_b mirrors these.
    for key in ("legs_a.front", "legs_a.mid", "legs_a.rear"):
        p.box(key, "rust", 1)
        for face in p.each_face(key, SIDES):
            face.row(-1, "gunmetal", 3)
            face.row(0, "rust", 2)


def gravity_leech(p) -> None:
    """Mid mass: the gunmetal sucker head and ribbon. Dark mass: the mouth well and rust rings that
    count down the tail. Light mass: the four pale lips. Accent: the sucker core that makes the
    tether, and a node on the tail fluke."""

    plate(p, "disc.plate", "gunmetal", 2, lip=False, hem=False)
    mouth = p.face("disc.plate", "north")
    mouth.fill("gunmetal", 1)
    mouth.rect(1, 1, 6, 6, "rust", 1)                              # tooth ring
    mouth.rect(2, 2, 4, 4, "gunmetal", 0)                          # the well
    mouth.glow(3, 2, 2, 4, step=3, under=1)
    mouth.glow(2, 3, 4, 2, step=3, under=1)
    mouth.glow(3, 3, 2, 2, step=4, under=3)                        # core
    plate(p, "disc.bulb", "gunmetal", 2)
    for strip in p.around("disc.bulb"):
        strip.rect(1, 0, 2, strip.h, "rust", 2)                    # collar behind the disc
    p.face("disc.bulb", "south").rect(1, 1, 4, 4, "gunmetal", 1)

    for key in ("lip_t.lip", "lip_l.lip"):
        p.box(key, "gunmetal", 3)
        p.face(key, "north").fill("gunmetal", 4)
    p.face("lip_t.lip", "down").fill("rust", 2, raw=True)          # inner faces show when the lips flare
    p.face("lip_l.lip", "west").fill("rust", 2, raw=True)

    p.box("tail_1.seg", "gunmetal", 2)
    for strip in p.around("tail_1.seg"):
        strip.rect(2, 0, 2, strip.h, "rust", 2)
        strip.rect(6, 0, 2, strip.h, "rust", 2)
    p.box("tail_2.seg", "gunmetal", 2)
    for strip in p.around("tail_2.seg"):
        strip.rect(3, 0, 2, strip.h, "rust", 2)
        strip.rect(7, 0, 2, strip.h, "rust", 1)
    p.box("tail_3.tip", "gunmetal", 1)
    for strip in p.around("tail_3.tip"):
        strip.rect(4, 0, 2, strip.h, "rust", 1)
    p.box("tail_3.fluke", "gunmetal", 3)
    p.top("tail_3.fluke").glow(1, 1, 2, 1, step=3, under=2)
    p.under("tail_3.fluke").glow(1, 1, 2, 1, step=3, under=2)


def asteroid_worm(p) -> None:
    """Mid mass: rust body rings, each with a lit band and a dark seam where it tucks into the next.
    Light mass: gunmetal dorsal ridge plates, brow and the four maw petals with pale fangs. Dark
    mass: the throat. Accent: one ring in the throat, seen only when the petals part."""

    for name in ("seg_1", "seg_2", "seg_3", "seg_4", "seg_5", "seg_6", "seg_7"):
        key = name + ".ring"
        p.box(key, "rust", 2)
        for strip in p.around(key):
            strip.rect(0, 0, 1, strip.h, "rust", 0)                # seam at the front lip
            strip.rect(2, 0, 2, strip.h, "rust", 3)                # lit band
            strip.rect(6, 0, 1, strip.h, "rust", 1)
        p.under(key).fill("rust", 3)                               # pale belly, still shaded by the face
        for face in (p.face(key, "north"), p.face(key, "south")):
            face.fill("rust", 1)
            face.rect(1, 1, face.w - 2, face.h - 2, "rust", 0)
        p.box(name + ".ridge", "gunmetal", 3)
        p.top(name + ".ridge").rect(1, 1, 1, 2, "gunmetal", 1)
    p.box("seg_7.spike", "gunmetal", 2)
    p.face("seg_7.spike", "south").fill("gunmetal", 4)

    plate(p, "head.skull", "rust", 2, hem=False)
    for strip in p.around("head.skull"):
        strip.rect(0, 0, 2, strip.h, "gunmetal", 2)                # the hard rim the petals hinge on
        strip.rect(4, 0, 2, strip.h, "rust", 3)
    throat = p.face("head.skull", "north")
    throat.fill("gunmetal", 1)
    throat.rect(1, 1, 8, 8, "rust", 0)
    throat.glow(2, 2, 6, 1, step=3, under=1)
    throat.glow(2, 7, 6, 1, step=3, under=1)
    throat.glow(2, 3, 1, 4, step=3, under=1)
    throat.glow(7, 3, 1, 4, step=3, under=1)
    throat.rect(4, 4, 2, 2, "gunmetal", 0)
    p.face("head.skull", "south").fill("rust", 1)
    p.box("head.brow", "gunmetal", 3)

    p.box("petal_t.blade", "gunmetal", 2)
    p.top("petal_t.blade").rect(2, 1, 2, 4, "gunmetal", 3)         # spine of the blade (outer face)
    inner = p.under("petal_t.blade")
    inner.fill("rust", 3, raw=True)
    inner.rect(0, 0, 6, 1, "gunmetal", 4, raw=True)                # tooth row at the cutting edge
    inner.rect(1, 2, 1, 2, "gunmetal", 4, raw=True)
    inner.rect(4, 2, 1, 2, "gunmetal", 4, raw=True)
    p.box("petal_t.fang", "gunmetal", 4)
    p.box("petal_l.blade", "gunmetal", 2)
    outer = p.face("petal_l.blade", "east")
    outer.rect(1, 2, 4, 2, "gunmetal", 3)
    inner = p.face("petal_l.blade", "west", flip_x=True)           # x = 0 at the cutting edge
    inner.fill("rust", 3, raw=True)
    inner.rect(0, 0, 1, 6, "gunmetal", 4, raw=True)
    inner.rect(2, 1, 2, 1, "gunmetal", 4, raw=True)
    inner.rect(2, 4, 2, 1, "gunmetal", 4, raw=True)
    p.box("petal_l.fang", "gunmetal", 4)


PAINTERS = {
    "magnet_crab": magnet_crab,
    "gravity_leech": gravity_leech,
    "asteroid_worm": asteroid_worm,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
