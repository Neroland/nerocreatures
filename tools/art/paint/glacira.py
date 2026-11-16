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
    make the cold aura. Wool stage 0 wears the lot; sheared (stage 1) shows the clipped barrel and legs."""

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


def frost_lurker(p) -> None:
    """Dark mass: the indigo back. Light mass: the cream belly and jaw that flash when it rears, and
    the frost crust along its edges. Accent: stalk eyes, the fangs in the mouth and the cores of the
    tall spines; the spines themselves are ice, so buried they pass for shards in the snow."""

    def slab(key: str, scales) -> None:
        p.box(key, "indigo", 2)
        top = p.top(key)
        top.rect(0, 0, 2, top.h, "wool", 2)                                # frost crust on both edges
        top.rect(top.w - 2, 0, 2, top.h, "wool", 2)
        top.rect(2, 0, 1, top.h, "indigo", 1)
        top.rect(top.w - 3, 0, 1, top.h, "indigo", 1)
        for x, y in scales:
            top.rect(x, y, 2, 2, "indigo", 1)
            top.rect(top.w - 2 - x, y, 2, 2, "indigo", 1)
        for name in SIDES:
            face = p.face(key, name)
            face.fill("indigo", 1)
            face.row(0, "wool", 2)
        p.under(key).fill("wool", 3)
        p.under(key).rect(2, 0, p.under(key).w - 4, p.under(key).h, "wool", 4)

    slab("body.mid", ((4, 1), (6, 4), (4, 6)))
    slab("fore.fore", ((3, 1), (4, 3)))
    slab("aft.aft", ((4, 0), (3, 3)))
    p.box("body.ridge", "indigo", 1)
    p.top("body.ridge").rect(2, 0, 2, 9, "indigo", 0)

    # Head and jaws. The mouth lining only shows when the jaw drops.
    key = "head.upper"
    p.box(key, "indigo", 2)
    top = p.top(key)
    top.rect(0, 0, 8, 2, "indigo", 1)
    top.rect(0, 4, 2, 2, "wool", 2)
    top.rect(6, 4, 2, 2, "wool", 2)
    front = p.face(key, "north")
    front.fill("indigo", 1)
    front.rect(1, 0, 2, 1, "indigo", 0)
    front.rect(5, 0, 2, 1, "indigo", 0)
    front.row(1, "wool", 3)
    for side in p.flanks(key):
        side.fill("indigo", 1)
        side.row(1, "wool", 3)
    roof = p.under(key)
    roof.fill("indigo", 2, raw=True)
    roof.rect(1, 0, 6, 1, "wool", 4, raw=True)
    roof.glow(1, 1, 2, 1)
    roof.glow(5, 1, 2, 1)
    key = "jaw.jaw"
    p.box(key, "wool", 2)
    floor = p.top(key)
    floor.fill("indigo", 0, raw=True)
    floor.rect(1, 0, 6, 1, "wool", 4, raw=True)
    floor.rect(3, 2, 2, 4, "indigo", 2, raw=True)
    p.face(key, "south").fill("indigo", 1)

    # Eye stalks.
    p.box("stalk_l.stem", "indigo", 1)
    p.box("stalk_l.eye", "indigo", 1)
    p.face("stalk_l.eye", "north").glow(0, 1, 3, 1)
    for side in p.flanks("stalk_l.eye"):
        side.glow(0, 1, 2, 1)
    p.top("stalk_l.eye").fill("indigo", 2)

    # Spines: ice, rooted in dark hide, with a lit core in the three tall ones.
    for key, core in (("spine_1.shard", 0), ("spine_2.shard", 2), ("spine_3.shard", 3), ("spine_4.shard", 2),
                      ("aft.spine_5", 0)):
        p.box(key, "accent", 3)
        for name in SIDES:
            face = p.face(key, name)
            face.rect(0, 0, 2, 2, "accent", 4)
            face.rect(0, face.h - 2, 2, 2, "indigo", 2)
            if name in ("north", "south"):
                face.rect(0, 2, 1, face.h - 4, "accent", 2)
        if core:
            for side in p.flanks(key):
                side.glow(0, core, 2, 2)
        p.face(key, "down").fill("indigo", 2)

    p.box("tail.tail", "indigo", 2)
    p.top("tail.tail").rect(0, 0, 1, 5, "wool", 2)
    p.top("tail.tail").rect(5, 0, 1, 5, "wool", 2)
    p.under("tail.tail").fill("wool", 4)
    p.box("tail.tip", "indigo", 1)
    for strip in p.around("tail.tip"):
        strip.rect(3, 0, 2, strip.h, "wool", 3)
    p.face("tail.tip", "south").fill("wool", 3)


def glacite_wisp(p) -> None:
    """Translucent, and exempt from the glow cap. Dark mass: the indigo bodies of the shards, which
    is what lets a pale ice creature read on pale ground. Mid: the blue facets. Light: the core,
    shard tips, orbiters and the fading tail, all lit."""

    core = "body.core"
    for name in ("up", "down") + SIDES:
        face = p.face(core, name)
        face.glow(0, 0, 4, 4, step=3, under=2)
        face.glow(1, 1, 2, 2, step=4, under=3)

    def shard(key: str, lean: int) -> None:
        p.box(key, "indigo", 1)
        for i, name in enumerate(SIDES):
            face = p.face(key, name)
            face.rect(0, 0, 2, 1, "accent", 4)                             # frosted tips
            face.rect(0, face.h - 1, 2, 1, "accent", 4)
            col = (i + lean) % 2
            face.rect(col, 1, 1, face.h - 2, "accent", 2)                  # one lit facet per side
            face.glow(col, 3, 1, 2)
            face.glow(col, face.h - 5, 1, 2)
        p.face(key, "up").fill("accent", 4)
        p.face(key, "down").fill("accent", 3)

    shard("shard_a.shard", 0)
    shard("shard_b.shard", 1)
    shard("shard_c.shard", 0)
    for key in ("shard_a.chip", "shard_b.chip"):
        p.box(key, "accent", 2)
        for name in SIDES:
            p.face(key, name).glow(0, 0, 1, 2)

    for key in ("ring.orb_n", "ring.orb_s", "ring.orb_e", "ring.orb_w"):
        p.box(key, "accent", 2)
        for name in SIDES:
            face = p.face(key, name)
            face.glow(0, 0, 1, face.h - 1)
            face.rect(0, face.h - 1, 1, 1, "indigo", 3)
        p.face(key, "up").fill("accent", 4)

    # Tail: dark at the root, brightening to a lit point.
    p.box("tail_1.seg_1", "indigo", 2)
    for strip in p.around("tail_1.seg_1"):
        strip.rect(2, 0, 2, strip.h, "accent", 2)
    p.box("tail_2.seg_2", "accent", 2)
    for strip in p.around("tail_2.seg_2"):
        strip.rect(0, 0, 1, strip.h, "indigo", 3)
        strip.rect(2, 0, 2, strip.h, "accent", 3)
    for name in ("up", "down", "east", "west"):
        p.face("tail_2.seg_3", name).glow(0, 0, 4 if name in ("east", "west") else 1, 1 if name in ("east", "west") else 4)
    p.face("tail_2.seg_3", "south").glow(0, 0, 1, 1, step=4, under=3)
    p.face("tail_2.seg_3", "north").fill("accent", 3)


def rime_matriarch(p) -> None:
    """Dark mass: the indigo bodice, face and ice skirt. Light mass: the cream hood and shoulder
    mantle. Accent: the antler crown, eyes, frostcore, claws, and the cold edge of the skirt and stem
    that freezes the floor. The stem only shows once the skirt has shattered (phase 3)."""

    # Bodice.
    p.box("body.chest", "indigo", 1)
    front = p.face("body.chest", "north")
    front.rect(0, 0, 12, 2, "indigo", 2)
    for x in (1, 9):
        front.rect(x, 3, 2, 7, "indigo", 2)                                # boning
    front.rect(5, 6, 2, 4, "indigo", 0)
    for name in ("south", "east", "west"):
        face = p.face("body.chest", name)
        for y in (2, 5, 8):
            face.rect(0, y, face.w, 1, "indigo", 2)
    p.box("body.waist", "indigo", 1)
    for name in SIDES:
        face = p.face("body.waist", name)
        face.rect(0, 4, face.w, 2, "wool", 2)                               # sash where the skirt hangs
        face.rect(0, 3, face.w, 1, "indigo", 0)
    for name in ("up", "down") + SIDES:
        face = p.face("body.frostcore", name)
        face.fill("accent", 2, raw=True)
    core = p.face("body.frostcore", "north")
    core.glow(0, 0, 3, 3, step=3, under=2)
    core.glow(1, 0, 1, 3, step=4, under=3)

    # Shoulder mantle.
    key = "body.shoulders"
    p.box(key, "wool", 3)
    for name in SIDES:
        face = p.face(key, name)
        face.row(-1, "indigo", 2)
        for x in range(1, face.w - 1, 4):
            face.rect(x, 1, 2, 2, "wool", 2)
    top = p.top(key)
    top.rect(7, 0, 4, 9, "wool", 2)
    for x, y in ((1, 1), (4, 5), (12, 2), (15, 6)):
        top.rect(x, y, 2, 2, "wool", 2)

    # Icicle stem, dark at the waist and bright at the point.
    for key, rows in (("body.stem_a", (("indigo", 1), ("indigo", 1), ("indigo", 2))),
                      ("body.stem_b", (("indigo", 2), ("indigo", 3), ("accent", 1))),
                      ("body.stem_c", (("accent", 1), ("accent", 2), ("accent", 2)))):
        p.box(key, rows[0][0], rows[0][1])
        for name in SIDES:
            face = p.face(key, name)
            for y in range(face.h):
                ramp, step = rows[min(2, y * 3 // face.h)]
                face.rect(0, y, face.w, 1, ramp, step)
            face.rect(0, 0, 2, face.h, rows[1][0], rows[1][1])              # one facet edge
        p.face(key, "down").fill(rows[2][0], rows[2][1], raw=True)
    p.box("body.stem_d", "accent", 3)
    for name in SIDES:
        p.face("body.stem_d", name).glow(0, 1, 2, 3)

    # Face in the hood's shadow.
    key = "head.face"
    p.box(key, "indigo", 1)
    front = p.face(key, "north")
    front.fill("indigo", 0)
    front.rect(2, 1, 4, 6, "indigo", 1)
    front.rect(3, 4, 2, 3, "indigo", 2)
    front.glow(1, 3, 2, 1)
    front.glow(5, 3, 2, 1)
    front.rect(3, 7, 2, 1, "indigo", 0)

    # Hood.
    for key in ("hood.top", "hood.back", "hood.side_l", "hood.side_r", "hood.peak"):
        p.box(key, "wool", 3)
    p.top("hood.top").rect(4, 0, 2, 10, "wool", 2)
    p.face("hood.top", "north").row(-1, "indigo", 2)
    p.under("hood.top").fill("indigo", 2, raw=True)
    back = p.face("hood.back", "south")
    back.rect(4, 0, 2, 9, "wool", 2)
    back.row(-1, "indigo", 2)
    back.rect(0, 6, 10, 2, "wool", 2)
    p.face("hood.back", "north").fill("indigo", 1)
    for key, outer, inner in (("hood.side_l", "east", "west"), ("hood.side_r", "west", "east")):
        face = p.face(key, outer)
        face.row(-1, "indigo", 2)
        face.rect(0, 5, 9, 2, "wool", 2, raw=True)
        p.face(key, inner).fill("indigo", 1, raw=True)
        p.face(key, "north").fill("indigo", 2)
    p.top("hood.peak").rect(2, 0, 2, 7, "wool", 4, raw=True)

    # Crown: ice antlers. The tines are the beam organ.
    p.box("crown_l.beam", "indigo", 2)
    for face in p.each_face("crown_l.beam", ("north", "south", "up", "down")):
        face.rect(4, 0, 9, face.h, "accent", 2)
        face.rect(10, 0, 3, face.h, "accent", 3)
    p.face("crown_l.beam", "east").fill("accent", 3)
    for key in ("crown_l.tine_out", "crown_l.tine_in"):
        p.box(key, "accent", 3)
        for name in SIDES:
            face = p.face(key, name)
            face.rect(0, face.h - 3, 2, 3, "accent", 2)
            face.rect(0, 0, 1, face.h - 3, "accent", 4)
            face.glow(0, 0, 2, 4, step=4, under=3)
        p.face(key, "up").glow(0, 0, 2, 2, step=4, under=3)

    # Arms: cream sleeve, dark forearm, ice claws.
    p.box("arm_l.upper", "wool", 2)
    for name in SIDES:
        face = p.face("arm_l.upper", name)
        face.rect(0, 7, 3, 3, "wool", 1)
        face.row(-1, "indigo", 2)
    p.box("forearm_l.fore", "indigo", 1)
    for name in SIDES:
        p.face("forearm_l.fore", name).rect(0, 6, 3, 3, "indigo", 2)
    p.box("forearm_l.claw", "accent", 2)
    for name in SIDES:
        face = p.face("forearm_l.claw", name)
        face.rect(0, 0, 4, 1, "indigo", 2)
        face.glow(0, 2, 2, 1)
        face.rect(2, 1, 2, 2, "accent", 3)
    p.face("forearm_l.claw", "down").fill("accent", 3, raw=True)

    # Skirt panels: south is the outside. Facets run the length; frost at the waist, cold light at the hem.
    for key, cols in (("skirt_f.panel", (2, 1, 2, 3, 1)), ("skirt_fl.panel", (1, 2, 1, 1, 3))):
        out = p.face(key, "south")
        for i, step in enumerate(cols):
            out.rect(i * 2, 0, 2, out.h, "indigo", step)
        out.rect(0, 0, 10, 2, "wool", 3)
        for x, drip in ((0, 2), (2, 4), (4, 1), (6, 5), (8, 3)):
            out.rect(x, 2, 2, drip, "wool", 2)
        for x, rise in ((0, 4), (2, 7), (4, 3), (6, 6), (8, 4)):
            out.rect(x, out.h - rise, 2, rise, "accent", 1)
            out.rect(x, out.h - rise // 2, 2, rise // 2, "accent", 2)
        out.glow(0, out.h - 1, 10, 1)
        inner = p.face(key, "north")
        inner.fill("indigo", 0)
        inner.rect(2, 4, 2, inner.h - 4, "indigo", 1)
        inner.rect(6, 8, 2, inner.h - 8, "indigo", 1)
        for name in ("west", "east"):
            p.face(key, name).fill("indigo", 2, raw=True)
            p.face(key, name).rect(0, 26, 2, p.face(key, name).h - 26, "accent", 1, raw=True)
        p.face(key, "up").fill("wool", 3, raw=True)
        p.face(key, "down").fill("accent", 2, raw=True)


PAINTERS = {
    "rime_yak": rime_yak,
    "frost_lurker": frost_lurker,
    "glacite_wisp": glacite_wisp,
    "rime_matriarch": rime_matriarch,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
