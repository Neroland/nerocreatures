#!/usr/bin/env python3
"""Nyxara painters: one function per entity.

    python tools/art/paint/nyxara.py            # paint and write
    python tools/art/paint/nyxara.py --check    # prove the committed PNGs match

Nyxara's ground is dark violet crystal, so bodies are pale: ``silver`` for hide and stone, ``opal``
for crystal and mirror, with ``plum`` as the dark mass. The accent is the prism pair, magenta to
cyan, and it is always one gradient across one organ: up the stag's antlers, along the Shardback's
back seam, up the golem's tall shard, down the colossus's lit facet and up its core.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")


def slab(p, key: str, ramp: str, step: int, lip: bool = True, hem: bool = True) -> None:
    """One cube as a worked block: flat fill, a lit top edge and a shadowed bottom edge on its sides."""
    p.box(key, ramp, step)
    for face in p.each_face(key, SIDES):
        if lip:
            face.row(0, ramp, step + 1)
        if hem and face.h > 2:
            face.row(-1, ramp, step - 1)


def facet(p, key: str, ramp: str, step: int) -> None:
    """One cube as a cut crystal: every side face has a lit leading edge and a shaded trailing edge."""
    p.box(key, ramp, step)
    for face in p.each_face(key, SIDES):
        if face.w >= 2:
            face.col(0, ramp, step + 1)
        if face.w >= 3:
            face.col(-1, ramp, step - 1)


def prism(f: float) -> tuple:
    """The prism gradient: 0 is the magenta end, 1 the cyan end. Returns (accent ramp, step)."""
    if f < 0.3:
        return ("accent", 3)
    if f < 0.5:
        return ("accent", 4)
    if f < 0.7:
        return ("accent_b", 4)
    return ("accent_b", 3)


def vein(face, x: int, w: int, y0: int, y1: int, lo: float = 0.0, hi: float = 1.0, under: int = 2) -> None:
    """A glowing bar from row y1 (the ``lo`` end of the gradient) up to row y0 (the ``hi`` end)."""
    span = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        ramp, step = prism(lo + (hi - lo) * (y1 - y) / span)
        face.glow(x, y, w, 1, step=step, under=under, accent=ramp)


def mirror(face, offset: int = 0) -> None:
    """A mirror face: pale fill with one bright and one grey reflection streak running diagonally."""
    face.fill("opal", 3)
    for y in range(face.h):
        x = (y + offset) // 2
        face.rect(x, y, 2, 1, "opal", 4)
        face.rect(x + 3, y, 1, 1, "silver", 3)


def prism_stag(p) -> None:
    """Mid mass: the silver hide. Dark mass: the plum dorsal stripe, mane, ear linings and hooves.
    Light: opal belly, rump and the crystal antlers, which carry the gradient from burr to tine."""

    for key in ("body.chest", "body.loin"):
        p.box(key, "silver", 2)
        for side in p.flanks(key):
            side.row(0, "silver", 3)
            side.rect(0, side.h - 2, side.w, 2, "opal", 2)        # pale belly line
        top = p.top(key)
        start = (top.w - 2) // 2
        top.rect(start, 0, top.w - 2 * start, top.h, "plum", 2)    # dorsal stripe
        p.under(key).fill("opal", 2)
    p.face("body.chest", "north").rect(1, 2, 4, 5, "opal", 2)      # bib
    p.face("body.loin", "south").rect(1, 1, 3, 4, "opal", 3)       # rump patch
    for side in p.flanks("body.loin"):
        side.rect(2, 1, 2, 1, "opal", 3)                           # dapples
        side.rect(4, 2, 2, 1, "opal", 3)

    p.box("tail.tail", "opal", 3)
    p.face("tail.tail", "south").fill("plum", 2)

    p.box("neck.neck", "silver", 2)
    p.face("neck.neck", "north").fill("opal", 2)                   # throat
    p.face("neck.neck", "south").col(1, "plum", 2)                 # mane

    p.box("head.skull", "silver", 2)
    p.top("head.skull").rect(1, 0, 2, 5, "plum", 2)
    for side in p.flanks("head.skull"):
        side.glow(1, 1, 2, 1, step=3, under=2)                     # eye
        side.row(-1, "opal", 2)
    p.box("head.snout", "silver", 3)
    p.face("head.snout", "north").fill("plum", 1)
    p.under("head.snout").fill("opal", 2)
    for key in ("head.pedicle_l", "head.pedicle_r"):
        p.box(key, "opal", 1)
    p.box("ear_l.ear", "silver", 2)
    p.face("ear_l.ear", "north").fill("plum", 3)

    for key, ramp in (("leg_fl.upper", 2), ("leg_fl.lower", 1), ("leg_rl.haunch", 2), ("leg_rl.lower", 1)):
        p.box(key, "silver", ramp)
    for key in ("leg_fl.upper", "leg_rl.haunch"):
        for face in p.each_face(key, SIDES):
            face.row(-1, "silver", 1)
    for side in p.flanks("leg_rl.haunch"):
        side.row(0, "silver", 3)
    for key in ("leg_fl.lower", "leg_rl.lower"):
        for face in p.each_face(key, SIDES):
            face.rect(0, face.h - 2, face.w, 2, "plum", 1)         # hoof
        p.face(key, "down").fill("plum", 1)

    # Antlers: three growth stages, one gradient. Burr magenta, tines cyan.
    for key in ("antler_l_1.beam_a", "antler_l_2.beam_b", "antler_l_2.brow", "antler_l_3.beam_c",
                "antler_l_3.back_tine", "antler_l_3.out_tine"):
        facet(p, key, "opal", 3)
    for key, lo, hi in (("antler_l_1.beam_a", 0.0, 0.29), ("antler_l_2.beam_b", 0.3, 0.69), ("antler_l_3.beam_c", 0.7, 1.0)):
        north, south, east = p.face(key, "north"), p.face(key, "south"), p.face(key, "east")
        vein(north, 1, 1, 0, north.h - 1, lo, hi)
        vein(south, 0, 1, 0, south.h - 1, lo, hi)
        vein(east, 0, 1, 0, east.h - 1, lo, hi)
    for key, face_name in (("antler_l_2.brow", "east"), ("antler_l_3.back_tine", "east"), ("antler_l_3.out_tine", "north")):
        tine = p.face(key, face_name)
        tine.glow(tine.w - 1, 0, 1, 3, step=4, under=2, accent="accent_b")


def shardback(p) -> None:
    """Light mass: the opal mirror fan. Mid mass: the silver armoured forehand. Dark mass: the bare
    plum hindquarters, where the only glow is the seam along the spine: the weak point."""

    p.box("body.chest", "silver", 2)
    for side in p.flanks("body.chest"):
        side.row(0, "silver", 3)
        side.rect(0, side.h - 2, side.w, 2, "silver", 1)
        side.col(3, "silver", 1)                                   # armour seam
        side.rect(side.w - 1, 0, 1, side.h, "plum", 2)             # where the armour ends
    p.face("body.chest", "north").fill("plum", 1)
    p.face("body.chest", "south").fill("plum", 2)

    p.box("body.rump", "plum", 2)
    for side in p.flanks("body.rump"):
        side.row(0, "plum", 3)
        side.rect(2, 2, 2, 3, "plum", 1)                           # soft folds
        side.rect(5, 2, 2, 3, "plum", 1)
        side.row(-1, "plum", 1)
    p.face("body.rump", "south").rect(2, 1, 4, 4, "plum", 1)
    slab(p, "body.hump", "silver", 3)
    p.box("body.tail", "plum", 1)

    p.box("body.seam", "plum", 1)
    top = p.top("body.seam")
    for y in range(top.h):
        ramp, step = prism(y / (top.h - 1.0))
        top.glow(0, y, top.w, 1, step=step, under=2, accent=ramp)
    for side in p.flanks("body.seam"):
        for x in range(side.w):
            ramp, step = prism(x / (side.w - 1.0))
            side.glow(x, 0, 1, 1, step=step, under=2, accent=ramp)
    p.face("body.seam", "south").glow(0, 0, 2, 1, step=3, under=2, accent="accent_b")

    p.box("head.skull", "silver", 1)
    for side in p.flanks("head.skull"):
        side.glow(1, 1, 2, 1, step=3, under=2)
    p.face("head.skull", "north").rect(0, 2, 4, 2, "plum", 1)
    p.box("head.snout", "plum", 2)

    p.box("collar.hub", "opal", 2)
    hub = p.face("collar.hub", "north")
    hub.rect(1, 1, 4, 4, "opal", 3)
    hub.rect(2, 2, 2, 2, "opal", 4)
    p.face("collar.hub", "south").fill("silver", 1)
    slab(p, "collar.yoke", "silver", 1, hem=False)

    for i, name in enumerate(("plate_c", "plate_li", "plate_lo")):
        key = name + ".plate"
        p.box(key, "opal", 4)
        front = p.face(key, "north")
        mirror(front, i)
        front.row(-1, "silver", 2)
        back = p.face(key, "south")
        back.fill("silver", 1)
        back.rect(2, 0, 1, back.h, "plum", 2)                      # strut
        p.box(name + ".tip", "opal", 4)
        p.face(name + ".tip", "south").fill("silver", 1)

    # The same fan folded flat over the shoulders (form 1): mirror side up.
    for i, key in enumerate(("folded.slab_c", "folded.slab_l", "folded.slab_r")):
        p.box(key, "opal", 4)
        up = p.top(key)
        up.fill("opal", 2)
        for y in range(up.h):
            up.rect((y + i) // 2, y, 2, 1, "opal", 3)
        up.rect(0, up.h - 1, up.w, 1, "silver", 2)
        p.under(key).fill("silver", 1)

    slab(p, "leg_fl.leg", "silver", 2)
    p.box("leg_fl.foot", "silver", 1)
    slab(p, "leg_rl.leg", "plum", 2)
    p.box("leg_rl.foot", "plum", 1)
    for key, ramp, step in (("leg_fl.foot", "opal", 3), ("leg_rl.foot", "opal", 1)):
        toes = p.face(key, "north")
        toes.rect(0, 0, 1, 2, ramp, step)
        toes.rect(2, 0, 1, 2, ramp, step)


def crystal_golem(p) -> None:
    """Mid mass: the silver stone chest, arms and head. Dark mass: plum waist, legs and fists.
    Light: the opal cluster on the left shoulder, with the gradient up the tall shard."""

    slab(p, "hips.pelvis", "plum", 2)
    p.box("body.abdomen", "plum", 2)
    for face in p.each_face("body.abdomen", SIDES):
        face.row(1, "plum", 1)
        face.row(4, "plum", 1)
    p.box("body.chest", "silver", 2)
    for face in p.each_face("body.chest", SIDES):
        face.rect(0, 0, face.w, 2, "silver", 3)
        face.rect(0, face.h - 2, face.w, 2, "silver", 1)
    front = p.face("body.chest", "north")
    front.rect(4, 3, 2, 6, "silver", 1)                            # cracks
    front.rect(14, 2, 2, 5, "silver", 1)
    front.rect(6, 7, 3, 2, "silver", 1)
    back = p.face("body.chest", "south")
    back.rect(3, 2, 2, 7, "silver", 1)
    back.rect(15, 3, 2, 5, "silver", 1)
    for side in p.flanks("body.chest"):
        side.rect(4, 3, 2, 5, "silver", 1)
    top = p.top("body.chest")
    top.rect(top.w - 6, 2, 5, 7, "opal", 1)                        # crystal creeping in from the cluster
    slab(p, "body.backplate", "silver", 1)
    plate = p.face("body.backplate", "south")
    plate.rect(3, 1, 2, 7, "silver", 0)
    plate.rect(7, 1, 2, 7, "silver", 0)
    facet(p, "body.vein", "opal", 2)
    p.face("body.vein", "north").rect(1, 1, 2, 4, "opal", 4)

    p.box("head.skull", "silver", 1)
    face = p.face("head.skull", "north")
    face.rect(0, 0, 6, 2, "silver", 2)                             # brow
    face.rect(0, 3, 6, 1, "plum", 1)
    face.glow(0, 3, 2, 1, step=3, under=2)
    face.glow(4, 3, 2, 1, step=3, under=2)
    p.box("head.jaw", "plum", 2)

    slab(p, "cluster.base", "silver", 3)
    p.top("cluster.base").fill("opal", 1)
    facet(p, "cluster.lump", "opal", 1)
    for name in ("shard_a", "shard_b", "shard_c", "shard_d"):
        facet(p, name + ".shard", "opal", 3)
        p.face(name + ".shard", "up").fill("opal", 3)
    for face in p.each_face("shard_a.shard", SIDES):
        vein(face, 1, 2, 1, face.h - 1)
    for name in ("shard_b", "shard_c"):
        for side in ("north", "south", "east", "west"):
            face = p.face(name + ".shard", side)
            vein(face, 1, 1, 1, face.h - 1, 0.0, 0.8)
    for side in ("north", "south"):
        face = p.face("shard_d.shard", side)
        vein(face, 0, 1, 1, face.h - 1, 0.0, 0.6)

    slab(p, "pauldron_r.stub", "silver", 2)
    facet(p, "pauldron_r.nub", "opal", 1)
    facet(p, "shard_r.shard", "opal", 2)

    p.box("arm_l.upper", "silver", 2)
    for face in p.each_face("arm_l.upper", SIDES):
        face.rect(0, 0, face.w, 2, "plum", 2)                      # shoulder joint
        face.row(-1, "silver", 1)
        face.rect(2, 4, 2, 4, "silver", 1)
    p.box("forearm_l.forearm", "silver", 2)
    for face in p.each_face("forearm_l.forearm", SIDES):
        face.rect(0, 0, face.w, 2, "plum", 2)                      # elbow
        face.row(2, "silver", 3)
        face.rect(0, face.h - 2, face.w, 2, "silver", 1)
    p.box("forearm_l.fist", "plum", 2)
    for face in p.each_face("forearm_l.fist", SIDES):
        face.row(0, "plum", 3)
        face.rect(1, 2, 2, 2, "silver", 1)                         # knuckle studs
        face.rect(5, 2, 2, 2, "silver", 1)
    facet(p, "forearm_l.stud", "opal", 3)

    p.box("leg_l.leg", "plum", 2)
    for face in p.each_face("leg_l.leg", SIDES):
        face.row(0, "plum", 3)
        face.rect(0, 2, face.w, 1, "silver", 1)                    # knee band
        face.row(-1, "plum", 1)
    p.box("leg_l.foot", "plum", 1)
    toes = p.face("leg_l.foot", "north")
    toes.rect(0, 0, 2, 3, "silver", 1)
    toes.rect(4, 0, 2, 3, "silver", 1)
    p.top("leg_l.foot").fill("plum", 2)


def facet_colossus(p) -> None:
    """Mid mass: the silver column. Dark mass: plum plinth, feet and the inner faces of the plates.
    Light: the opal crown and the mirror plates. Glow: the core seam, magenta at the plinth to cyan
    at the cap, and the lit facet panel (shown only on the facet the fight has lit)."""

    p.box("core.plinth", "plum", 2)
    for face in p.each_face("core.plinth", SIDES):
        face.row(0, "plum", 3)
        face.rect(0, 2, face.w, 2, "silver", 1)
        face.row(-1, "plum", 1)
    p.top("core.plinth").fill("silver", 1)
    p.box("core.column", "silver", 2)
    for face in p.each_face("core.column", SIDES):
        face.rect(0, 0, 2, face.h, "silver", 3)
        face.rect(face.w - 2, 0, 2, face.h, "silver", 1)
        face.rect(0, face.h - 2, face.w, 2, "plum", 2)
        face.rect(2, 6, 3, 1, "silver", 1)                         # growth lines
        face.rect(7, 13, 3, 1, "silver", 1)
        vein(face, 5, 2, 3, face.h - 4, 0.0, 0.29)
    p.box("crown.upper", "opal", 2)
    for face in p.each_face("crown.upper", SIDES):
        face.rect(0, 0, 2, face.h, "opal", 3)
        face.rect(face.w - 2, 0, 2, face.h, "opal", 1)
        face.rect(0, face.h - 2, face.w, 2, "silver", 2)
        vein(face, 4, 2, 2, face.h - 4, 0.3, 0.75)
    facet(p, "crown.tip", "opal", 3)
    for face in p.each_face("crown.tip", SIDES):
        face.rect(0, face.h - 2, face.w, 2, "opal", 2)
    p.box("crown.cap", "opal", 4)
    for face in p.each_face("crown.cap", SIDES):
        face.glow(1, 0, 1, face.h, step=3, under=3, accent="accent_b")

    slab(p, "foot_a.hip", "plum", 2)
    p.box("foot_a.shin", "silver", 1)
    for face in p.each_face("foot_a.shin", SIDES):
        face.row(0, "silver", 2)
        face.rect(0, 4, face.w, 2, "plum", 2)
    p.box("foot_a.pad", "plum", 1)
    p.top("foot_a.pad").fill("plum", 2)
    claws = p.face("foot_a.pad", "north")
    for x in (0, 3, 6):
        claws.rect(x, 0, 2, 2, "opal", 2)

    for key in ("plate_0.main", "plate_0.top", "plate_0.bottom", "plate_0.tip_top", "plate_0.tip_bottom"):
        p.box(key, "opal", 4)
        p.face(key, "south").fill("plum", 2)
    outer = p.face("plate_0.main", "north")
    mirror(outer, 0)
    for y in range(outer.h):
        outer.rect((y // 2) + 6, y, 2, 1, "opal", 4)               # second streak on the wide plate
    outer.col(0, "silver", 2)
    outer.col(-1, "silver", 2)
    inner = p.face("plate_0.main", "south")
    inner.col(0, "silver", 1)
    inner.col(-1, "silver", 1)
    for key in ("plate_0.top", "plate_0.bottom"):
        face = p.face(key, "north")
        face.fill("opal", 2)
        face.rect(1, 1, 2, 3, "opal", 3)
    for key in ("plate_0.tip_top", "plate_0.tip_bottom"):
        p.face(key, "north").fill("opal", 3)

    p.box("lit_0.panel", "opal", 1)
    panel = p.face("lit_0.panel", "north")
    for y in range(panel.h):
        ramp, step = prism(y / (panel.h - 1.0))
        panel.glow(0, y, panel.w, 1, step=step, under=2, accent=ramp)
    for name in ("east", "west"):
        edge = p.face("lit_0.panel", name)
        for y in range(edge.h):
            ramp, step = prism(y / (edge.h - 1.0))
            edge.glow(0, y, 1, 1, step=step, under=2, accent=ramp)
    p.face("lit_0.panel", "up").glow(0, 0, 8, 1, step=3, under=2)
    p.face("lit_0.panel", "down").glow(0, 0, 8, 1, step=3, under=2, accent="accent_b")


PAINTERS = {
    "prism_stag": prism_stag,
    "shardback": shardback,
    "crystal_golem": crystal_golem,
    "facet_colossus": facet_colossus,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
