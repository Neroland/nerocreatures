#!/usr/bin/env python3
"""Deep space painters: one function per entity.

    python tools/art/paint/void.py            # paint and write
    python tools/art/paint/void.py --check    # prove the committed PNGs match

Deep space reads as black, so bodies are ``indigo`` lifted well off it, with star-flecked ``navy``
for undersides and limbs, and void violet as the only glow. Violet sits only where the void is
worked: the crawler's blink gland, the slime's nucleus, the manta's horn-fin tips and tail fin,
plus eyes.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import FACES, face_rect, run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")
SHELL_ALPHA = 168


def plate(p, key: str, ramp: str, step: int, lip: bool = True, hem: bool = True) -> None:
    """One cube as a plate: flat fill, a lit top edge and a shadowed bottom edge on its sides."""
    p.box(key, ramp, step)
    for face in p.each_face(key, SIDES):
        if lip and face.h > 1:
            face.row(0, ramp, step + 1)
        if hem and face.h > 2:
            face.row(-1, ramp, step - 1)


def stars(face, count: int, ramp: str = "indigo", step: int = 4) -> None:
    """Deliberate single-texel star flecks on a fixed lattice (no randomness)."""
    for i in range(count):
        face.px((i * 7 + 3) % face.w, (i * 5 + 2 + (i // 3)) % face.h, ramp, step, raw=True)


def void_crawler(p) -> None:
    """Light mass: indigo thorax and thrust head. Mid mass: banded abdomen and navy legs with pale
    claw tips. Accent: two eye bars and the blink gland at the abdomen tip."""

    plate(p, "body.thorax", "indigo", 3)
    top = p.top("body.thorax")
    top.rect(2, 1, 2, 5, "navy", 3)                                # dorsal stripe
    p.under("body.thorax").fill("navy", 4)
    plate(p, "body.abdomen", "indigo", 2)
    for strip in p.around("body.abdomen"):
        strip.rect(1, 0, 2, strip.h, "navy", 3)
        strip.rect(4, 0, 2, strip.h, "navy", 3)
    p.box("body.gland", "navy", 2)
    p.face("body.gland", "south").glow(0, 0, 2, 2, step=4, under=2)
    for side in p.flanks("body.gland"):
        side.glow(1, 0, 1, 2, step=3, under=1)
    p.top("body.gland").glow(0, 1, 2, 1, step=3, under=1)

    plate(p, "head.skull", "indigo", 3, hem=False)
    for side in p.flanks("head.skull"):
        side.glow(1, 1, 2, 1, step=3, under=2)                     # eye bars
        side.rect(3, 2, 2, 1, "navy", 3)
    face = p.face("head.skull", "north")
    face.rect(1, 2, 2, 1, "navy", 2)
    for key in ("head.fang_l", "head.fang_r"):
        p.box(key, "indigo", 4)

    for i in (1, 2, 3):
        p.box("leg_l%d.thigh" % i, "navy", 4)
        key = "shin_l%d.shin" % i
        p.box(key, "navy", 4)
        for face in p.each_face(key, SIDES):
            face.rect(0, 0, 1, 2, "indigo", 3)                     # knee
            face.rect(0, face.h - 3, 1, 3, "indigo", 4)            # claw tip


def plasma_slime(p) -> None:
    """A see-through teardrop in three tiers, darker where the plasma pools at the foot, with a
    glint on each tier. The nucleus is the only solid thing in it and is all accent (exempt from the
    cap by design). Two sub-drops carry a spark each. Shell texels are written part-transparent."""

    tiers = (("shell.base", 2), ("mid.belly", 3), ("top.cone", 3), ("top.tip", 4))
    for key, step in tiers:
        p.box(key, "indigo", step)
        for face in p.each_face(key, SIDES):
            if face.h > 2:
                face.row(-1, "navy", 4)                            # pooled foot of each tier
            face.rect(1, 0, 2, 1, "indigo", 4)                     # glint
        p.under(key).fill("navy", 4, raw=True)
    top = p.top("shell.base")
    top.rect(1, 1, 3, 2, "indigo", 4)
    p.top("mid.belly").rect(1, 1, 2, 2, "indigo", 4)

    for name in FACES:
        face = p.face("nucleus.core", name)
        face.glow(0, 0, 4, 4, step=3, under=3)
        face.glow(1, 1, 2, 2, step=4, under=4)

    p.box("drop_a.drop", "indigo", 4)
    p.face("drop_a.drop", "north").glow(0, 1, 2, 1, step=3, under=3)
    p.face("drop_a.drop", "south").glow(1, 1, 2, 1, step=3, under=3)
    p.box("drop_b.drop", "indigo", 4)
    p.face("drop_b.drop", "up").glow(0, 0, 2, 1, step=3, under=3)

    # Translucent shell: every texel of the droplet and the sub-drops, never the nucleus.
    for key in [k for k, _ in tiers] + ["drop_a.drop", "drop_b.drop"]:
        _, cube = p._cubes[key]
        for name in FACES:
            x0, y0, w, h = face_rect(cube["uv"], cube["size"], name)
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    r, g, b, a = p.base.px[y][x]
                    if a and p.glow.px[y][x][3] == 0:
                        p.base.px[y][x] = (r, g, b, SHELL_ALPHA)


def _wing_top(face, lead: int = 2) -> None:
    """A wing's upper skin, y = 0 at the leading edge: pale leading edge, two dark chevrons."""
    face.rect(0, 0, face.w, lead, "indigo", 3)
    if face.h > 8:
        face.rect(0, face.h // 2 - 1, face.w, 2, "navy", 4)
    face.rect(0, face.h - 2, face.w, 2, "indigo", 1)


def void_manta(p) -> None:
    """Mid mass: the indigo upper disc with a pale leading edge and a raised deck. Dark mass: the
    navy underside, flecked with stars. Light mass: horn-fins, wing tips and the saddle pad. Accent:
    horn-fin tips and the trailing edge of the tail fin (the blink), and violet eyes."""

    p.box("body.core", "indigo", 2)
    top = p.top("body.core")
    top.rect(0, 0, top.w, 2, "indigo", 3)
    top.rect(0, 16, 2, 4, "indigo", 1)
    top.rect(10, 16, 2, 4, "indigo", 1)
    belly = p.under("body.core")
    belly.fill("navy", 4, raw=True)
    belly.rect(3, 3, 6, 1, "navy", 2, raw=True)                    # gill slits
    belly.rect(3, 5, 6, 1, "navy", 2, raw=True)
    belly.rect(3, 7, 6, 1, "navy", 2, raw=True)
    stars(belly, 9)
    for side in p.flanks("body.core"):
        side.rect(0, 2, side.w, 2, "navy", 4)

    p.box("body.nose", "indigo", 3)
    for side in p.flanks("body.nose"):
        side.glow(1, 1, 2, 1, step=3, under=2)                     # eyes
    p.face("body.nose", "north").rect(2, 2, 4, 1, "navy", 2)       # mouth slot
    p.under("body.nose").fill("navy", 4, raw=True)

    plate(p, "body.deck", "indigo", 3, lip=False, hem=False)
    deck = p.top("body.deck")
    deck.rect(3, 0, 2, deck.h, "indigo", 2)
    deck.rect(1, 3, 6, 1, "indigo", 2)
    deck.rect(1, 9, 6, 1, "indigo", 2)
    p.box("body.rump", "indigo", 1)
    p.under("body.rump").fill("navy", 3, raw=True)

    # Horn-fin: pale blade, lit tip. horn_r mirrors it.
    p.box("horn_l.fin", "indigo", 3)
    for strip in p.around("horn_l.fin"):
        strip.rect(0, 0, 2, strip.h, "indigo", 4)
    p.under("horn_l.fin").fill("navy", 4, raw=True)
    p.box("horn_l.tip", "indigo", 4)
    for face in p.each_face("horn_l.tip", ("up", "down", "east", "west")):
        face.glow(0, 0, face.w, face.h, step=3, under=2)
    p.face("horn_l.tip", "north").glow(0, 0, 1, 1, step=4, under=3)

    # Wings: wing_r1, flap_r and wing_r2 mirror these.
    for key in ("wing_l1.panel", "wing_l2.mid"):
        p.box(key, "indigo", 2)
        _wing_top(p.top(key))
        under = p.under(key)
        under.fill("navy", 4, raw=True)
        under.rect(0, 0, under.w, 1, "navy", 3, raw=True)
        stars(under, 8 if key == "wing_l1.panel" else 5)
        p.face(key, "north").fill("indigo", 3)
    p.box("flap_l.flap", "indigo", 1)
    flap = p.top("flap_l.flap")
    flap.rect(0, 3, flap.w, 1, "indigo", 3)
    for x in (1, 5):
        flap.rect(x, 0, 2, 3, "navy", 4)
    p.under("flap_l.flap").fill("navy", 3, raw=True)
    p.box("wing_l2.trail", "indigo", 1)
    p.top("wing_l2.trail").rect(0, 2, 8, 1, "indigo", 3)
    p.under("wing_l2.trail").fill("navy", 3, raw=True)
    p.box("wing_l2.tip", "indigo", 3)
    tip = p.top("wing_l2.tip")
    tip.rect(0, 0, tip.w, 1, "indigo", 4)
    tip.rect(tip.w - 2, 0, 2, tip.h, "indigo", 4)
    under = p.under("wing_l2.tip")
    under.fill("navy", 4, raw=True)
    stars(under, 3)

    # Whip tail and fin.
    p.box("tail_1.root", "navy", 4)
    for strip in p.around("tail_1.root"):
        strip.rect(0, 0, 3, strip.h, "indigo", 1)
        strip.rect(7, 0, 2, strip.h, "indigo", 1)
    p.box("tail_2.whip", "navy", 4)
    p.box("tail_2.fin", "indigo", 3)
    for side in p.flanks("tail_2.fin"):
        side.rect(0, 2, 2, 1, "navy", 4)
        side.glow(4, 0, 1, 5, step=3, under=2)
    p.face("tail_2.fin", "south").glow(0, 0, 1, 5, step=4, under=3)

    # Saddle (shown only when saddled): a pale pad on the deck, dark horn and back rest, a girth band.
    p.box("saddle.pad", "indigo", 4)
    pad = p.top("saddle.pad")
    pad.rect(0, 0, pad.w, 1, "navy", 3)
    pad.rect(0, pad.h - 1, pad.w, 1, "navy", 3)
    pad.rect(4, 2, 2, 5, "indigo", 2)
    plate(p, "saddle.pommel", "navy", 4, hem=False)
    plate(p, "saddle.cantle", "navy", 4, hem=False)
    p.box("saddle.girth", "navy", 3)
    for face in p.each_face("saddle.girth", ("up", "down", "east", "west")):
        face.rect(0, 1, face.w, 1, "indigo", 4, raw=True)          # stitched centre line


PAINTERS = {
    "void_crawler": void_crawler,
    "plasma_slime": plasma_slime,
    "void_manta": void_manta,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
