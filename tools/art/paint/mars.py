#!/usr/bin/env python3
"""Mars painters: one function per entity.

    python tools/art/paint/mars.py            # paint and write
    python tools/art/paint/mars.py --check    # prove the committed PNGs match

Mars reads as mid-value rust ground, so bodies are pale: ``bone`` and ``sand``, with deep ``umber``
as the dark mass and static mint as the only glow.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")


def dune_loper(p) -> None:
    """Light mass: bone hide. Mid mass: sand belly, shanks and tail end. Dark mass: umber back bars,
    crest, tail fin, toes and all the tack. Accent: eyes and the shank springs that charge before a leap."""

    def hide(key: str, belly: int = 0, bars=(), depth: int = 0) -> None:
        p.box(key, "bone", 2)
        for side in p.flanks(key):
            side.row(0, "bone", 3)
            for x in bars:
                side.rect(x, 0, 2, depth, "umber", 3)
            if belly:
                side.row(-belly - 1, "bone", 1)
                side.rect(0, side.h - belly, side.w, belly, "sand", 2)
        top = p.top(key)
        for x in bars:
            top.rect(0, x, top.w, 2, "umber", 3)
        if belly:
            p.under(key).fill("sand", 3)

    # Torso: three saddle-back bars that stop above the belly line.
    hide("body.torso", belly=3, bars=(2, 8, 14), depth=5)
    hide("body.chest", belly=3)
    breast = p.face("body.chest", "north")
    breast.rect(2, 3, 4, 6, "sand", 2)
    breast.rect(3, 4, 2, 5, "sand", 3)
    hide("body.rump", belly=2, bars=(3,), depth=4)
    p.face("body.rump", "south").rect(0, 6, 8, 2, "sand", 2)

    # Neck: pale throat in front, a dark mane edge behind.
    p.box("neck.neck", "bone", 2)
    throat = p.face("neck.neck", "north")
    throat.rect(1, 2, 4, 13, "sand", 2)
    throat.rect(2, 4, 2, 11, "sand", 3)
    p.face("neck.neck", "south").rect(2, 0, 2, 15, "umber", 3)
    for side in p.flanks("neck.neck"):
        side.rect(side.w - 1, 0, 1, 15, "umber", 3)
        for y in (3, 8):
            side.rect(side.w - 3, y, 2, 2, "umber", 3)

    # Head: long skull, sand muzzle, a swept-back blade crest.
    p.box("head.skull", "bone", 2)
    p.top("head.skull").rect(2, 0, 2, 8, "umber", 3)
    for side in p.flanks("head.skull"):
        side.row(0, "bone", 3)
        side.rect(1, 1, 4, 1, "umber", 2)
        side.glow(2, 2, 2, 1)
        side.row(-1, "sand", 2)
    p.face("head.skull", "north").row(0, "bone", 3)
    p.box("head.snout", "sand", 3)
    p.top("head.snout").rect(0, 0, 4, 2, "umber", 3)
    p.face("head.snout", "north").row(0, "umber", 3)
    for side in p.flanks("head.snout"):
        side.rect(0, 0, 2, 1, "umber", 3)
        side.row(-1, "sand", 2)
    p.box("head.jaw", "sand", 1)
    p.box("head.crest", "umber", 3)
    for side in p.flanks("head.crest"):
        side.row(0, "umber", 4)
        side.rect(5, 0, 2, 3, "sand", 2)
    p.top("head.crest").rect(0, 5, 2, 2, "sand", 2)

    # Tail: ringed and level, ending in the dark rudder fin with two pale bars.
    p.box("tail.tail", "bone", 2)
    for strip in p.around("tail.tail"):
        for x in (2, 7):
            strip.rect(x, 0, 2, strip.h, "umber", 3)
    p.box("tail_tip.tip", "sand", 2)
    for strip in p.around("tail_tip.tip"):
        for x in (1, 6):
            strip.rect(x, 0, 2, strip.h, "umber", 3)
    p.box("tail_tip.fin", "umber", 3)
    for side in p.flanks("tail_tip.fin"):
        side.row(0, "umber", 4)
        side.rect(1, 2, side.w - 1, 2, "bone", 2)
        side.rect(1, 6, side.w - 1, 2, "bone", 2)
    back = p.face("tail_tip.fin", "south")
    back.rect(0, 2, 2, 2, "bone", 2)
    back.rect(0, 6, 2, 2, "bone", 2)

    # Forelimbs: small, sand, dark claws. The right side mirrors.
    p.box("arm_l.upper", "sand", 2)
    for name in SIDES:
        p.face("arm_l.upper", name).row(0, "bone", 2)
    p.box("arm_l.claw", "umber", 3)
    p.face("arm_l.claw", "north").row(-1, "bone", 3)

    # Legs: barred bone haunch, sand shank with the glowing spring behind it, dark splayed toes.
    hide("leg_l.thigh", bars=(2, 6), depth=6)
    for side in p.flanks("leg_l.thigh"):
        side.rect(0, 10, side.w, 4, "sand", 2)
        side.row(9, "bone", 1)
    for name in ("north", "south"):
        p.face("leg_l.thigh", name).rect(0, 10, 6, 4, "sand", 2)
    p.box("shank_l.shank", "sand", 2)
    for name in SIDES:
        face = p.face("shank_l.shank", name)
        face.row(0, "sand", 3)
        face.rect(0, 9, 4, 2, "umber", 3)
    p.box("shank_l.spring", "umber", 2)
    tendon = p.face("shank_l.spring", "south")
    tendon.glow(1, 0, 2, 6)
    tendon.glow(1, 2, 2, 2, step=4, under=2)
    p.box("foot_l.cannon", "sand", 1)
    for name in SIDES:
        p.face("foot_l.cannon", name).rect(0, 4, 3, 3, "umber", 3)
    p.box("foot_l.toes", "umber", 2)
    p.top("foot_l.toes").fill("umber", 3)
    claws = p.face("foot_l.toes", "north")
    claws.rect(0, 1, 2, 1, "bone", 3)
    claws.rect(4, 1, 2, 1, "bone", 3)

    # Saddle (flag saddled): sand blanket with a dark hem, umber leather seat and girth.
    p.box("saddle.blanket", "sand", 1)
    for side in p.flanks("saddle.blanket") + [p.face("saddle.blanket", "north"), p.face("saddle.blanket", "south")]:
        side.row(-1, "umber", 3)
        side.row(0, "sand", 2)
        side.dither(0, 1, side.w, 2, "umber", 3, cluster=2)
    p.box("saddle.seat", "umber", 3)
    p.top("saddle.seat").rect(2, 2, 4, 5, "umber", 4)
    p.box("saddle.pommel", "umber", 2)
    p.top("saddle.pommel").fill("bone", 2)
    p.box("saddle.cantle", "umber", 2)
    p.face("saddle.cantle", "north").row(0, "umber", 4)
    p.face("saddle.cantle", "south").row(0, "umber", 4)
    p.box("saddle.girth", "umber", 2)
    for side in p.flanks("saddle.girth"):
        side.rect(0, 5, 3, 2, "bone", 3)
        side.rect(1, 5, 1, 2, "umber", 1)

    # Panniers (flag panniers): two sand bags with dark flaps on the haunches, a strap and a bedroll.
    for key in ("panniers.pannier_l", "panniers.pannier_r"):
        p.box(key, "sand", 1)
        p.top(key).fill("umber", 3)
        for name in SIDES:
            face = p.face(key, name)
            face.rect(0, 0, face.w, 3, "umber", 3)
            face.row(3, "umber", 2)
            face.row(-1, "sand", 0)
        for side in p.flanks(key):
            side.rect(3, 2, 2, 3, "bone", 3)
    p.box("panniers.strap", "umber", 2)
    p.box("panniers.roll", "bone", 1)
    for name in ("north", "south", "up", "down"):
        face = p.face("panniers.roll", name)
        face.col(1, "umber", 3)
        face.col(-2, "umber", 3)


def strata_hound(p) -> None:
    """Mid mass: sand hide laid down in strata bands. Dark mass: umber mantle, ears, paws and the worn
    shovel edge. Light mass: bone shovel blade, blaze and belly. Accent: eyes and the back-stripe
    on the three ridge plates, which the renderer tints to the ore family being tracked."""

    def strata(key: str, bands) -> None:
        p.box(key, "sand", 2)
        for side in p.flanks(key):
            y = 0
            for rows, ramp, step in bands:
                side.rect(0, y, side.w, rows, ramp, step)
                y += rows
        top = p.top(key)
        top.fill("umber", 3)
        top.rect(top.w // 2 - 1, 0, 2, top.h, "umber", 1)

    strata("body.chest", ((2, "umber", 3), (1, "umber", 4), (3, "sand", 2), (1, "sand", 0), (2, "sand", 3),
                          (1, "sand", 1), (2, "bone", 3)))
    strata("body.loin", ((1, "umber", 3), (1, "umber", 4), (3, "sand", 2), (1, "sand", 0), (2, "sand", 3)))
    p.under("body.chest").fill("bone", 3)
    p.under("body.loin").fill("bone", 3)
    blaze = p.face("body.chest", "north")
    blaze.rect(0, 0, 10, 2, "umber", 3)
    blaze.rect(3, 5, 4, 7, "bone", 2)
    blaze.rect(4, 6, 2, 6, "bone", 3)
    rear = p.face("body.loin", "south")
    rear.row(0, "umber", 3)
    rear.rect(0, 6, 8, 2, "sand", 3)

    # Head: small skull behind the bone shovel; the leading edge is worn dark from digging.
    p.box("head.skull", "sand", 2)
    p.top("head.skull").fill("umber", 3)
    for side in p.flanks("head.skull"):
        side.row(0, "umber", 3)
        side.rect(0, 1, 4, 1, "umber", 1)
        side.glow(1, 2, 2, 1)
        side.row(-1, "sand", 1)
    p.box("head.shovel", "bone", 2)
    blade = p.top("head.shovel")
    blade.rect(0, 0, 8, 2, "umber", 2)
    blade.rect(1, 2, 6, 1, "bone", 1)
    for side in p.flanks("head.shovel"):
        side.rect(0, 0, 2, 3, "umber", 2)
        side.row(-1, "bone", 1)
    p.face("head.shovel", "north").fill("umber", 2)
    p.box("head.bridge", "sand", 2)
    nose = p.top("head.bridge")
    nose.rect(0, 0, 4, 2, "umber", 1)
    nose.rect(1, 2, 2, 4, "sand", 3)
    p.face("head.bridge", "north").fill("umber", 1)
    p.box("ear_l.ear", "umber", 2)
    for side in p.flanks("ear_l.ear"):
        side.rect(0, 4, 3, 2, "umber", 3)

    # Ridge: three dark plates, each with a lit stripe along its crown and flanks.
    for key in ("ridge_a.plate", "ridge_b.plate", "ridge_c.plate"):
        p.box(key, "umber", 1)
        p.top(key).glow(0, 1, 2, 3, step=4, under=2)
        for side in p.flanks(key):
            side.glow(1, 1, 3, 1)
            side.row(-1, "umber", 2)

    p.box("tail.stub", "umber", 2)
    for strip in p.around("tail.stub"):
        strip.col(0, "umber", 3)
    p.face("tail.stub", "south").fill("sand", 2)

    # Legs: sand, banded like the flanks, on dark digging paws.
    p.box("leg_fl.upper", "sand", 2)
    for name in SIDES:
        face = p.face("leg_fl.upper", name)
        face.row(1, "sand", 0)
        face.row(-1, "sand", 1)
    p.box("forepaw_l.paw", "umber", 2)
    p.top("forepaw_l.paw").fill("umber", 3)
    p.face("forepaw_l.paw", "north").row(-1, "bone", 2)
    p.box("leg_rl.haunch", "sand", 2)
    for name in SIDES:
        face = p.face("leg_rl.haunch", name)
        face.row(0, "umber", 4)
        face.row(3, "sand", 0)
        face.row(-1, "sand", 1)


def dust_devil(p) -> None:
    """Translucent, so gaps in the paint are gaps in the whirl. Light mass: the bone top ring and the
    spin-up column. Mid mass: the sand middle ring. Dark mass: the rusty low ring, the foot and the
    umber core shell. Accent: the charged core, its spindle and, in the whirl, the arcs round it. No face, no rods."""

    def streaks(face, rows, lean: int, gap: int = 2, period: int = 7) -> None:
        """Slanted dashes: each row is a run of paint with a gap that walks sideways down the face."""
        for y in range(face.h):
            ramp, step = rows[min(len(rows) - 1, y * len(rows) // face.h)]
            start = None
            for x in range(face.w + 1):
                on = x < face.w and (x + y * lean) % period < period - gap
                if on and start is None:
                    start = x
                if not on and start is not None:
                    if x - start >= 2:
                        face.rect(start, y, x - start, 1, ramp, step)
                    start = None

    def ring(key: str, rows, rim: int, lean: int, grit) -> None:
        for name in SIDES:
            face = p.face(key, name)
            streaks(face, rows, lean)
            for x, y in grit:
                if x + 2 <= face.w:
                    face.rect(x, y, 2, 1, "umber", 4)
        for name in ("up", "down"):
            face = p.face(key, name)
            ramp, step = rows[0] if name == "up" else rows[-1]
            for i in range(rim):
                span = face.w - 2 * i
                for x in range(span):
                    if (x + i * 3) % 6 < (5 if i == 0 else 3):
                        for fx, fy in ((i + x, i), (face.w - 1 - i - x, face.h - 1 - i),
                                       (i, face.h - 1 - i - x), (face.w - 1 - i, i + x)):
                            face.px(fx, fy, ramp, step if i == 0 else step - 1)

    ring("ring_top.ring", (("bone", 3), ("bone", 2), ("sand", 3), ("sand", 2)), 3, 2, ((3, 1), (11, 2)))
    ring("ring_mid.ring", (("sand", 3), ("sand", 2), ("sand", 2), ("sand", 1)), 2, -2, ((2, 2), (8, 1)))
    ring("ring_low.ring", (("sand", 2), ("sand", 1), ("sand", 1), ("sand", 0)), 2, 2, ((4, 1),))

    # Foot: the thin dark tail the cone stands on.
    for name in SIDES:
        streaks(p.face("funnel.wisp", name), (("sand", 1), ("sand", 0)), -1, gap=1, period=4)
        streaks(p.face("funnel.wisp_tip", name), (("umber", 4), ("umber", 3)), 1, gap=1, period=3)
    p.face("funnel.wisp", "up").fill("sand", 0)

    # Core: a dark shell split by a lit cross, on a charged spindle.
    p.box("core.core", "umber", 1)
    for name in SIDES:
        face = p.face("core.core", name)
        face.glow(1, 0, 2, 4)
        face.glow(1, 1, 2, 2, step=4, under=2)
    p.face("core.core", "up").glow(1, 1, 2, 2, step=4, under=2)
    p.face("core.core", "down").glow(1, 1, 2, 2)
    p.box("core.spindle", "umber", 2)
    for name in ("north", "south"):
        face = p.face("core.spindle", name)
        for y in (1, 5):
            face.glow(0, y, 2, 2)

    # Spin-up column (form 1): tight, pale and nearly closed, so the core cannot be seen.
    for key, rows in (("column.col_top", (("bone", 3), ("bone", 2), ("bone", 3), ("sand", 3))),
                      ("column.col_low", (("sand", 3), ("bone", 2), ("sand", 2), ("sand", 1)))):
        for name in SIDES:
            streaks(p.face(key, name), rows, 3, gap=1, period=6)

    # Whirl (stage 1, the hit-now window): a loose cage of arcs tumbling round the bared core.
    for name in SIDES:
        face = p.face("halo.arcs", name)
        face.glow(0, 0, 2, 1)
        face.glow(4, 2, 2, 1, step=4, under=2)
        face.glow(1, 5, 2, 1)


PAINTERS = {
    "dune_loper": dune_loper,
    "strata_hound": strata_hound,
    "dust_devil": dust_devil,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
