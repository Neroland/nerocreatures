#!/usr/bin/env python3
"""Greenxertz painters: one function per entity.

    python tools/art/paint/greenxertz.py            # paint and write
    python tools/art/paint/greenxertz.py --check    # prove the committed PNGs match

Greenxertz ground is a saturated mid green, so every body leads with pale ``cream``. ``ochre`` (olive
in its shadow steps) is moss, bark, hooves and deck plating; ``rose`` is petal, quartz and soft
tissue. Spore chartreuse is the only glow.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")


def sides(p, key):
    return [p.face(key, name) for name in SIDES]


# --- xertz_forager ---------------------------------------------------------------------------

def xertz_forager(p) -> None:
    """Light mass: cream barrel and skull. Mid mass: ochre snout, belly, legs and flank bars.
    Dark-to-bright accent: the rose quartz sawtooth crest. Glow: eyes and the crest tips."""
    p.box("body.barrel", "cream", 2)
    for side in p.flanks("body.barrel"):
        side.row(0, "cream", 3)
        for x in (2, 6, 10):
            side.rect(x, 1, 2, 3, "ochre", 3)
        side.row(-2, "cream", 1)
        side.row(-1, "ochre", 3)
    top = p.top("body.barrel")
    top.rect(3, 0, 2, top.h, "rose", 1)
    p.under("body.barrel").fill("ochre", 4)
    rear = p.face("body.barrel", "south")
    rear.row(-1, "ochre", 2)
    rear.rect(3, 1, 2, 2, "ochre", 2)
    p.face("body.barrel", "north").fill("cream", 1)
    p.box("body.back", "cream", 3)
    back = p.top("body.back")
    back.fill("cream", 2)
    back.rect(2, 0, 2, back.h, "rose", 1)
    p.box("body.belly", "ochre", 3)

    # Crest: four rose quartz teeth, each lit along its crown.
    for name in ("tooth_a", "tooth_b", "tooth_c", "tooth_d"):
        key = "crest." + name
        p.box(key, "rose", 2)
        for face in sides(p, key):
            face.row(-1, "rose", 0)
            face.col(0, "rose", 3)
        for side in p.flanks(key):
            side.glow(0, 0, 2, 1)
        p.face(key, "up").glow(0, 0, 2, 2, step=4, under=2)

    p.box("head.skull", "cream", 2)
    p.top("head.skull").rect(2, 0, 2, 4, "rose", 1)
    for side in p.flanks("head.skull"):
        side.row(0, "cream", 3)
        side.glow(1, 2, 1, 2)
        side.rect(2, 2, 1, 2, "ochre", 0)
        side.row(-1, "ochre", 3)
    brow = p.face("head.skull", "north")
    brow.row(0, "cream", 3)
    brow.rect(0, 2, 6, 1, "cream", 1)
    brow.row(-1, "ochre", 2)
    p.box("head.snout", "ochre", 2)
    nose = p.face("head.snout", "north")
    nose.fill("rose", 2)
    nose.rect(1, 1, 2, 1, "rose", 0)
    nose.row(-1, "rose", 1)
    for side in p.flanks("head.snout"):
        side.rect(0, 0, 1, 3, "rose", 3)
    p.top("head.snout").rect(0, 0, 4, 1, "rose", 1)
    for key in ("head.ear_l", "head.ear_r"):
        p.box(key, "ochre", 1)
        p.face(key, "north").fill("rose", 2)
    p.box("tail.tail", "ochre", 2)
    p.face("tail.tail", "south").fill("rose", 2)
    for key in ("leg_fl.leg", "leg_rl.leg"):
        p.box(key, "ochre", 2)
        for face in sides(p, key):
            face.row(0, "cream", 1)
            face.row(-1, "ochre", 0)


# --- spore_drifter ---------------------------------------------------------------------------

def spore_drifter(p) -> None:
    """Translucent: unpainted texels are gaps (the rim fringe). Light mass: the cream canopy.
    Mid mass: rose gills, ribs and tendrils. Glow: the spore gland under the canopy and the
    tendril tips, where a growth tick lands."""
    p.box("canopy.crown", "cream", 3)
    p.face("canopy.crown", "up").rect(2, 2, 2, 2, "rose", 2)
    for face in sides(p, "canopy.crown"):
        face.row(-1, "cream", 2)
    p.box("canopy.dome", "cream", 2)
    for face in sides(p, "canopy.dome"):
        face.row(0, "cream", 3)
        for x in (1, 4, 7):
            face.rect(x, 1, 2, 2, "rose", 3)
        face.row(-1, "cream", 1)
    dome = p.face("canopy.dome", "up")
    for x, y in ((0, 0), (8, 0), (0, 8), (8, 8)):
        dome.rect(x, y, 2, 2, "rose", 2)
    # Rim: a solid lip above a scalloped fringe; the gaps let the world show through.
    for face in sides(p, "canopy.rim"):
        face.row(0, "cream", 2)
        for x in range(0, face.w, 4):
            face.rect(x, 1, 2, 1, "rose", 2)
    rim = p.face("canopy.rim", "up")
    rim.fill("cream", 1)
    for i in range(0, 14, 4):
        rim.rect(i, 0, 2, 1, "rose", 2)
        rim.rect(i, 13, 2, 1, "rose", 2)
        rim.rect(0, i, 1, 2, "rose", 2)
        rim.rect(13, i, 1, 2, "rose", 2)
    gills = p.face("canopy.rim", "down")
    gills.fill("rose", 4)
    for i in range(1, 14, 3):
        gills.rect(i, 0, 1, 14, "rose", 3)
    gills.rect(4, 4, 6, 6, "rose", 2)
    p.face("canopy.gland", "up").fill("rose", 1)
    for face in sides(p, "canopy.gland"):
        face.fill("rose", 1)
        face.glow(0, 1, 4, 1)
        face.glow(1, 0, 2, 1)
    p.face("canopy.gland", "down").glow(0, 0, 4, 4)
    p.face("canopy.gland", "down").glow(1, 1, 2, 2, step=4, under=2)

    for key in ("tendril_fl", "tendril_ml", "tendril_rl"):
        for face in sides(p, key + ".strand"):
            face.rect(0, 0, 2, 3, "cream", 2)
            face.rect(0, 3, 2, 3, "rose", 3)
            face.rect(0, 6, 2, 2, "rose", 2)
        p.face(key + ".strand", "up").fill("cream", 1)
        p.face(key + ".strand", "down").fill("rose", 3)
        for face in sides(p, key + ".tip"):
            face.rect(0, 0, 1, 2, "rose", 1)
            face.glow(0, 2, 1, 2)
        p.face(key + ".tip", "up").fill("rose", 1)
        p.face(key + ".tip", "down").glow(0, 0, 1, 1, step=4, under=2)


# --- mossback --------------------------------------------------------------------------------

def mossback(p) -> None:
    """Light mass: cream hide. Dark mass: the olive moss carpet and tufts, hooves and tail tuft.
    Mid mass: rose shovel muzzle and ears, ochre hide dapples. Glow: eyes and the spore buds on
    the tufts."""
    for key, dapples in (("body.fore", ((2, 3, 3, 2), (6, 5, 2, 2))), ("body.aft", ((1, 4, 2, 2), (5, 2, 3, 2)))):
        p.box(key, "cream", 2)
        for side in p.flanks(key):
            side.row(0, "ochre", 1)
            side.row(1, "cream", 3)
            for x, y, w, h in dapples:
                side.rect(x, y, w, h, "ochre", 3)
            side.row(-1, "cream", 1)
        p.top(key).fill("ochre", 0)
        under = p.under(key)
        under.fill("cream", 3)
        under.rect(4, 0, 2, under.h, "cream", 4)
    p.face("body.fore", "north").fill("cream", 1)
    rear = p.face("body.aft", "south")
    rear.row(0, "ochre", 0)
    rear.rect(3, 1, 4, 3, "ochre", 2)

    # Moss carpet: the rear half mirrors this one.
    slab = p.face("carpet_f.slab", "up")
    slab.fill("ochre", 1, raw=True)
    slab.dither(0, 0, 12, 9, "ochre", 0, cluster=2, raw=True)
    for x, y in ((1, 1), (7, 2), (4, 5), (9, 6)):
        slab.rect(x, y, 2, 1, "cream", 0, raw=True)
    for face in sides(p, "carpet_f.slab"):
        face.row(0, "ochre", 2)
        face.row(1, "ochre", 1)
        for x in range(1, face.w, 4):
            face.rect(x, 1, 2, 1, "cream", 0, raw=True)
    p.face("carpet_f.slab", "down").fill("ochre", 2)
    for key, bud in (("carpet_f.tuft_a", (0, 1, 2, 1)), ("carpet_f.tuft_b", (0, 0, 2, 1))):
        p.box(key, "ochre", 1)
        for face in sides(p, key):
            face.row(0, "cream", 0, raw=True)
        up = p.face(key, "up")
        up.fill("cream", 0, raw=True)
        up.glow(*bud)

    p.box("head.skull", "cream", 2)
    for side in p.flanks("head.skull"):
        side.row(0, "cream", 3)
        side.glow(1, 2, 2, 1)
        side.rect(1, 3, 2, 1, "ochre", 1)
        side.row(-1, "rose", 3)
    top = p.top("head.skull")
    top.rect(0, 3, 8, 2, "ochre", 0)
    front = p.face("head.skull", "north")
    front.row(0, "cream", 3)
    front.rect(3, 1, 2, 2, "cream", 3)
    p.box("head.muzzle", "rose", 2)
    pad = p.top("head.muzzle")
    pad.rect(0, 0, 10, 1, "ochre", 0)
    pad.rect(0, 4, 10, 1, "rose", 1)
    lip = p.face("head.muzzle", "north")
    lip.fill("rose", 1)
    lip.rect(2, 0, 2, 1, "rose", 0)
    lip.rect(6, 0, 2, 1, "rose", 0)
    lip.row(-1, "ochre", 1)
    for side in p.flanks("head.muzzle"):
        side.row(-1, "ochre", 2)
    for key in ("head.ear_l", "head.ear_r"):
        p.box(key, "rose", 1)
        p.face(key, "south").fill("cream", 1)
    p.box("jaw.jaw", "cream", 1)
    p.face("jaw.jaw", "north").fill("rose", 1)
    p.box("tail.tail", "cream", 1)
    p.box("tail.tuft", "ochre", 1)
    p.face("tail.tuft", "down").fill("ochre", 2)
    for key in ("leg_fl.leg", "leg_rl.leg"):
        p.box(key, "cream", 1)
        for face in sides(p, key):
            face.row(0, "cream", 2)
            face.rect(0, 3, 4, 2, "ochre", 0)
        p.face(key, "down").fill("ochre", 2)


# --- bloom_host ------------------------------------------------------------------------------

def bloom_host(p) -> None:
    """Dark mass: the olive stalk body, limbs, roots and pod runners. Mid mass: the rose cap, petals
    and pod lobes. Light: cream petal lips, gill lines, spots and the pod's crown valves. Glow: the
    gill throat under the cap, the cap spots (host and sporeling alike), and on the pod the spore
    core in the crown and a slit on each lobe."""

    def cap(key: str, spots) -> None:
        up = p.face(key, "up")
        up.fill("cream", 1)
        up.rect(0, 0, up.w, 1, "rose", 2)
        up.rect(0, up.h - 1, up.w, 1, "rose", 2)
        up.rect(0, 0, 1, up.h, "rose", 2)
        up.rect(up.w - 1, 0, 1, up.h, "rose", 2)
        up.rect(1, 1, up.w - 2, 1, "cream", 2)
        for x, y in spots:
            up.rect(x - 1, y - 1, 4, 4, "rose", 2)
            up.glow(x, y, 2, 2)
        for face in sides(p, key):
            face.row(0, "cream", 2)
            face.row(1, "rose", 2)
            face.row(2, "rose", 1)
            for x in range(2, face.w, 4):
                face.rect(x, 0, 2, 2, "cream", 1)
        under = p.face(key, "down")
        under.fill("rose", 2)
        for i in range(1, under.w, 3):
            under.rect(i, 0, 1, under.h, "cream", 4)

    # Host.
    p.box("h_body.torso", "ochre", 1)
    for face in sides(p, "h_body.torso"):
        face.col(1, "ochre", 2)
        face.rect(0, 9, face.w, 1, "ochre", 0)
    p.box("h_body.hump", "ochre", 0)
    p.face("h_body.hump", "south").rect(1, 1, 2, 2, "rose", 1)
    p.face("h_body.hump", "up").fill("rose", 0)
    p.box("h_arm_l.arm", "ochre", 1)
    for face in sides(p, "h_arm_l.arm"):
        face.rect(0, 7, 2, 3, "ochre", 0)
        face.row(0, "ochre", 2)
    p.box("h_leg_l.shank", "ochre", 1)
    for face in sides(p, "h_leg_l.shank"):
        face.col(1, "ochre", 2)
        face.rect(0, 6, 3, 3, "ochre", 0)
    p.box("h_leg_l.root", "ochre", 0)
    cap("h_cap.brim", ((3, 3), (9, 5), (4, 9)))
    p.box("h_cap.dome", "rose", 2)
    for face in sides(p, "h_cap.dome"):
        face.row(0, "rose", 3)
        face.row(-1, "rose", 1)
    crown = p.face("h_cap.dome", "up")
    crown.rect(2, 2, 3, 4, "rose", 2)
    crown.glow(3, 3, 2, 2)
    p.face("h_cap.throat", "up").fill("rose", 0)
    for face in sides(p, "h_cap.throat"):
        face.glow(0, 0, 4, 2)
    p.face("h_cap.throat", "down").glow(0, 0, 4, 4)
    p.face("h_cap.throat", "down").glow(1, 1, 2, 2, step=4, under=2)
    p.box("h_cap.petal_back", "cream", 2)
    for name in ("south", "east", "west"):
        p.face("h_cap.petal_back", name).row(-1, "rose", 2)
    p.face("h_cap.petal_back", "south").rect(2, 0, 2, 3, "rose", 3)
    p.box("h_lobe.lobe", "cream", 2)
    for side in p.flanks("h_lobe.lobe"):
        side.row(0, "cream", 3)
        side.rect(3, 0, 1, 5, "rose", 3)
        side.row(-2, "rose", 3)
        side.row(-1, "rose", 2)
    for name in ("north", "south"):
        p.face("h_lobe.lobe", name).row(-1, "rose", 2)
    p.face("h_lobe.lobe", "down").fill("rose", 3)

    # Sporeling: the same cap, small, on stubs.
    cap("s_cap.brim", ((3, 3),))
    p.box("s_cap.dome", "rose", 2)
    for face in sides(p, "s_cap.dome"):
        face.row(-1, "rose", 1)
    p.box("s_cap.stem", "cream", 1)
    for face in sides(p, "s_cap.stem"):
        face.row(-1, "ochre", 2)
    p.face("s_cap.stem", "down").fill("ochre", 3)
    p.box("s_leg_l.stub", "ochre", 1)
    for face in sides(p, "s_leg_l.stub"):
        face.row(-1, "ochre", 0)

    # Pod: a rooted bulb of four swollen lobes, split at the crown around the spore core. The core
    # is the brightest thing on it; each lobe carries a lit slit where it will tear.
    p.box("p_root.stem", "ochre", 1)
    for face in sides(p, "p_root.stem"):
        face.col(0, "ochre", 0)
        face.row(-1, "ochre", 0)
    p.box("p_runner_a.runner", "ochre", 0)
    crawl = p.face("p_runner_a.runner", "up")
    crawl.rect(1, 0, crawl.w - 2, 1, "ochre", 1)
    crawl.rect(3, 0, 3, 1, "ochre", 2)
    p.box("p_bulb.cup", "ochre", 1)
    for face in sides(p, "p_bulb.cup"):
        face.row(0, "ochre", 2)
        face.rect(1, 0, 2, 1, "ochre", 3)
        face.row(-1, "ochre", 0)
    p.face("p_bulb.cup", "down").fill("ochre", 0)
    p.box("p_bulb.belly", "rose", 0)
    for face in sides(p, "p_bulb.belly"):
        face.row(0, "rose", 1)
        face.rect(1, 1, 4, 4, "rose", 1)
        face.row(-1, "ochre", 1)
    crown = p.face("p_bulb.belly", "up")
    crown.fill("rose", 1)
    crown.rect(1, 1, 4, 4, "rose", 2)
    p.face("p_bulb.belly", "down").fill("ochre", 1)
    p.box("p_lobe_f.lobe", "rose", 1)
    swell = p.face("p_lobe_f.lobe", "north")
    swell.fill("rose", 2)
    swell.row(0, "rose", 3)
    swell.row(-1, "rose", 1)
    swell.rect(0, 1, 1, 2, "cream", 2)
    swell.rect(3, 1, 1, 2, "rose", 1)
    swell.glow(1, 1, 2, 2)
    swell.glow(1, 3, 1, 1, step=2, under=0)
    p.face("p_lobe_f.lobe", "up").fill("rose", 3)
    p.box("p_bulb.collar", "cream", 1)
    for face in sides(p, "p_bulb.collar"):
        face.row(0, "cream", 2)
        face.row(-1, "rose", 2)
    mouth = p.face("p_bulb.collar", "up")
    mouth.fill("cream", 2)
    mouth.rect(1, 1, 2, 2, "rose", 0)
    p.box("p_valve_f.valve", "cream", 2)
    lip = p.face("p_valve_f.valve", "north")
    lip.row(0, "cream", 3)
    lip.row(-1, "rose", 2)
    lip.rect(1, 0, 2, 1, "rose", 3)
    inner = p.face("p_valve_f.valve", "south")
    inner.fill("rose", 3)
    inner.glow(1, 1, 2, 1, step=2, under=1)
    p.face("p_valve_f.valve", "up").fill("cream", 4)
    p.box("p_core.core", "cream", 3)
    for face in sides(p, "p_core.core"):
        face.glow(0, 0, 3, 3, step=2, under=1)
        face.glow(1, 0, 1, 3, step=3, under=2)
        face.glow(0, 0, 3, 1, step=3, under=2)
    spore = p.face("p_core.core", "up")
    spore.glow(0, 0, 3, 3, step=3, under=2)
    spore.glow(1, 1, 1, 1, step=4, under=3)
    p.face("p_core.core", "down").glow(0, 0, 3, 3, step=1, under=0)


# --- drift_leviathan -------------------------------------------------------------------------

def drift_leviathan(p) -> None:
    """Light mass: the cream flanks, belly pleats, head and fins. Mid mass: the ochre plated deck.
    Dark mass: barnacles, the mouth line and plate seams. Rose trims the deck rim, the spine, the
    fin edges and the streamers. Glow: eyes, the song pores along the flanks and throat, and the
    crowns of grown barnacles.

    Hull halves share texels left and right and along the length, sometimes mirrored, so every
    hull pattern is symmetric across the half and the inner (hidden) side is painted like the outer."""

    def pleats(face, step: int, every: int = 4, start: int = 1) -> None:
        for x in range(start, face.w, every):
            face.rect(x, 0, 1, face.h, "cream", step)

    def flank(face, pores, pleat_from: int) -> None:
        face.fill("cream", 2)
        face.rect(0, 0, face.w, 2, "rose", 1)
        face.row(2, "cream", 3)
        for x in pores:
            face.glow(x, 7, 3, 1)
        face.row(pleat_from - 1, "ochre", 3)
        face.rect(0, pleat_from, face.w, face.h - pleat_from, "cream", 1)
        for x in range(1, face.w, 4):
            face.rect(x, pleat_from + 1, 1, face.h - pleat_from - 1, "cream", 0)

    def deck(face, algae) -> None:
        """A deck top, ``x`` across (both edges trimmed alike), ``y`` along."""
        face.fill("ochre", 0)
        face.rect(0, 0, 2, face.h, "rose", 0)
        face.rect(face.w - 2, 0, 2, face.h, "rose", 0)
        face.rect(2, 0, face.w - 4, 1, "ochre", -1)
        face.rect(2, face.h - 1, face.w - 4, 1, "ochre", -1)
        mid = face.h // 2
        face.rect(5, mid, face.w - 10, 1, "ochre", -1)
        for y in range(3, face.h - 3, 6):
            face.rect(face.w // 2, y, 1, 3, "ochre", -1)
        for x, y, w, h in algae:
            face.dither(x, y, w, h, "cream", 0, cluster=2, raw=True)

    for key, algae in (("hull_1_l", ((5, 4, 6, 4), (17, 18, 6, 4))), ("hull_2_l", ((16, 3, 6, 6), (5, 17, 8, 5)))):
        deck(p.face(key + ".deck", "up"), algae)
        for side in p.flanks(key + ".deck"):
            flank(side, (3, 12, 21), 13)
        under = p.face(key + ".deck", "down")
        under.fill("cream", 4)
        pleats(under, 3)
        for name in ("north", "south"):
            end = p.face(key + ".deck", name)
            end.fill("cream", 1)
            end.rect(0, 0, end.w, 2, "rose", 1)
    for side in p.flanks("hull_2_l.belly"):
        side.fill("cream", 1)
        side.row(0, "cream", 2)
        for x in range(1, side.w, 4):
            side.rect(x, 1, 1, side.h - 1, "cream", 0)
    under = p.face("hull_2_l.belly", "down")
    under.fill("cream", 3)
    pleats(under, 2)
    for y in (6, 20):
        under.glow(7, y, 4, 1)
    for name in ("north", "south", "up"):
        p.face("hull_2_l.belly", name).fill("cream", 1)
    p.box("hull.spine", "rose", 2)

    # Head: pale and blunt, the deck's rose spine running out along it.
    p.box("head.cranium", "cream", 2)
    top = p.top("head.cranium")
    top.rect(22, 0, 4, top.h, "rose", 1)
    top.rect(0, top.h - 5, top.w, 5, "ochre", 0)
    top.dither(0, top.h - 9, top.w, 4, "ochre", 0, cluster=2)
    top.rect(22, 0, 4, top.h, "rose", 1)
    top.rect(0, 0, 2, top.h, "rose", 0)
    top.rect(top.w - 2, 0, 2, top.h, "rose", 0)
    for side in p.flanks("head.cranium"):
        flank(side, (15,), 11)
        side.rect(4, 4, 9, 7, "ochre", 3)
    brow = p.face("head.cranium", "north")
    brow.rect(0, 0, brow.w, 2, "rose", 1)
    brow.rect(0, 11, brow.w, 6, "cream", 1)
    p.face("head.cranium", "south").fill("cream", 1)
    under = p.face("head.cranium", "down")
    under.fill("cream", 4)
    pleats(under, 3)
    p.box("head.snout", "cream", 2)
    top = p.top("head.snout")
    top.rect(16, 0, 4, top.h, "rose", 1)
    top.rect(0, 0, top.w, 2, "cream", 3)
    for side in p.flanks("head.snout"):
        side.row(0, "cream", 3)
        side.rect(0, side.h - 2, side.w, 2, "rose", 0)
    face = p.face("head.snout", "north")
    face.rect(0, 0, face.w, 1, "cream", 3)
    face.rect(16, 0, 4, 4, "rose", 1)
    for x in (7, 25):
        face.rect(x, 3, 4, 2, "ochre", 1)
    face.rect(0, face.h - 2, face.w, 2, "rose", 0)
    p.face("head.snout", "down").fill("rose", 3)
    for key, name in (("head.eye_l", "east"), ("head.eye_r", "west")):
        p.box(key, "ochre", 0)
        eye = p.face(key, name)
        eye.glow(1, 1, 4, 3)
        eye.glow(2, 2, 2, 1, step=4, under=2)
    p.box("jaw.jaw", "cream", 1)
    mouth = p.top("jaw.jaw")
    mouth.fill("rose", 1)
    for x in range(1, mouth.w, 3):
        mouth.rect(x, 0, 2, 5, "cream", 2)
    lip = p.face("jaw.jaw", "north")
    lip.row(0, "rose", 0)
    lip.rect(0, 1, lip.w, 3, "cream", 2)
    for side in p.flanks("jaw.jaw"):
        side.row(0, "rose", 0)
    throat = p.under("jaw.jaw")
    throat.fill("cream", 4)
    pleats(throat, 3)
    for y in (6, 14, 22):
        throat.glow(9, y, 4, 1)
        throat.glow(19, y, 4, 1)

    # Tail: the deck plating narrows out; flukes and ribbons carry the rose.
    for key, pores in (("tail.root", (4, 13)), ("tail.mid", (9,))):
        deck(p.face(key, "up"), ((4, 4, 6, 4),))
        centre = p.face(key, "up")
        centre.rect(centre.w // 2 - 2, 0, 4, centre.h, "rose", 0)
        for side in p.flanks(key):
            flank(side, pores, side.h - 5)
        under = p.face(key, "down")
        under.fill("cream", 4)
        pleats(under, 3)
        for name in ("north", "south"):
            end = p.face(key, name)
            end.fill("cream", 1)
            end.rect(0, 0, end.w, 2, "rose", 1)
    for name, light in (("up", 2), ("down", 4)):
        blade = p.face("fluke.fluke", name, flip_y=True)
        blade.fill("cream", light)
        for x in range(2, blade.w, 6):
            blade.rect(x, 0, 1, blade.h - 3, "ochre", light + 1)
        blade.rect(0, blade.h - 3, blade.w, 3, "rose", light - 1)
        blade.rect(0, 0, 2, blade.h, "rose", light - 1)
        blade.rect(blade.w - 2, 0, 2, blade.h, "rose", light - 1)
    for face in sides(p, "fluke.fluke"):
        face.fill("rose", 1)
    for strip in p.around("streamer_l.ribbon"):
        strip.rect(0, 0, 30, strip.h, "rose", 2)
        for x in range(4, 24, 8):
            strip.rect(x, 0, 4, strip.h, "rose", 1)
        strip.rect(24, 0, 6, strip.h, "cream", 3)
    for name in ("north", "south"):
        p.face("streamer_l.ribbon", name).fill("rose", 1)

    # Fins: pale wings, a worn leading edge, rays, and a rose hem that widens toward the tip.
    def fin(key: str, hem: int) -> None:
        for name, light in (("up", 2), ("down", 4)):
            wing = p.face(key, name, flip_y=True)
            wing.fill("cream", light)
            wing.rect(0, 0, wing.w, 3, "ochre", light)
            for y in range(6, wing.h - hem, 5):
                wing.rect(2, y, wing.w - 2, 1, "cream", light - 1)
            wing.rect(0, wing.h - hem, wing.w, hem, "rose", light - 1)
            wing.rect(0, wing.h - hem - 1, wing.w, 1, "rose", light)
            wing.rect(wing.w - 2, 0, 2, wing.h, "rose", light - 1)
        for face in sides(p, key):
            face.fill("rose", 1)
        p.face(key, "north").fill("ochre", 2)

    fin("fin_fl.panel", 3)
    fin("fin_fl_tip.tip", 6)
    fin("fin_rl.panel", 3)
    fin("fin_rl_tip.tip", 5)

    # Barnacles: stage 1 dull stubs, stage 2 open cups with a lit core, stage 3 fully lit crowns.
    for key in p.cubes():
        if not key.startswith("barnacles_"):
            continue
        stage = int(key[10])
        p.box(key, "ochre", 0)
        for face in sides(p, key):
            face.row(0, "rose", 1 if stage < 3 else 2)
            if face.h > 3:
                face.row(-1, "ochre", 1)
        up = p.face(key, "up")
        up.fill("rose", 0)
        if stage == 1:
            up.rect(1, 1, max(1, up.w - 2), max(1, up.h - 2), "ochre", -1)
        elif stage == 2:
            up.glow(1 if up.w > 2 else 0, 1 if up.h > 2 else 0, max(2, up.w - 2), max(1, up.h - 2))
        else:
            up.glow(0, 0, up.w, up.h)
            if up.w > 2 and up.h > 2:
                up.glow(1, 1, up.w - 2, up.h - 2, step=4, under=2)


# --- bloom_sovereign -------------------------------------------------------------------------

def bloom_sovereign(p) -> None:
    """Dark mass: the olive trunk, roots and vines. Light mass: the cream petals and calyx, the
    same petal the Bloom Host wears. Mid mass: rose petal hems, disc, pods and vine ends. Glow:
    the heart behind the bark doors (and the hairline seam that gives it away), the crown disc,
    the pod seams and the vine buds."""
    p.box("trunk.base", "ochre", 0)
    for face in sides(p, "trunk.base"):
        face.row(0, "ochre", 1)
        for x in range(1, face.w, 5):
            face.rect(x, 1, 2, 3, "ochre", 1)
    for key, body, groove in (("trunk.lower", 1, 0), ("trunk.upper", 2, 1)):
        p.box(key, "ochre", body)
        for face in sides(p, key):
            for x in range(1, face.w, 3):
                face.rect(x, 0, 1, face.h, "ochre", groove)
            face.rect(0, 0, face.w, 1, "ochre", body + 1)
            face.rect(2, 8, 3, 2, "rose", 1)
            face.rect(face.w - 4, 14, 2, 2, "rose", 1)
    p.box("trunk.collar", "cream", 1)
    for face in sides(p, "trunk.collar"):
        face.row(0, "cream", 2)
        for x in range(0, face.w, 4):
            face.rect(x, 1, 2, 1, "rose", 2)
    p.face("trunk.collar", "down").fill("rose", 3)
    p.box("trunk.heart", "rose", 0)
    heart = p.face("trunk.heart", "north")
    heart.glow(0, 0, 6, 10)
    heart.glow(1, 2, 4, 6, step=4, under=2)
    heart.rect(0, 0, 1, 1, "rose", 0)
    heart.rect(5, 0, 1, 1, "rose", 0)
    heart.rect(0, 9, 1, 1, "rose", 0)
    heart.rect(5, 9, 1, 1, "rose", 0)
    for name in ("root_n", "root_s", "root_e", "root_w"):
        p.box("trunk." + name, "ochre", 0)
        p.face("trunk." + name, "up").fill("ochre", 0)
    p.box("bark_l.door", "ochre", 2)
    door = p.face("bark_l.door", "north")
    door.rect(2, 0, 1, 12, "ochre", 1)
    door.row(0, "ochre", 3)
    door.rect(0, 0, 1, 12, "ochre", 0)
    door.glow(0, 3, 1, 6)
    p.face("bark_l.door", "south").fill("rose", 0)

    p.box("crown.calyx", "cream", 1)
    for face in sides(p, "crown.calyx"):
        face.row(0, "cream", 2)
        for x in range(0, face.w, 4):
            face.rect(x, 3, 2, 3, "ochre", 2)
        face.row(-1, "ochre", 1)
    p.face("crown.calyx", "down").fill("ochre", 3)
    p.box("crown.disc", "rose", 1)
    disc = p.face("crown.disc", "up")
    disc.fill("rose", 0)
    for x, y in ((1, 1), (7, 1), (4, 4), (1, 7), (7, 7)):
        disc.glow(x, y, 2, 2)
    disc.glow(4, 4, 2, 2, step=4, under=2)

    # One petal serves all four, turned and mirrored, so its pattern is the same from every side.
    for name, light in (("up", 1), ("down", 3)):
        face = p.face("petal_n.petal", name)
        face.fill("cream", light)
        for i in range(2):
            face.rect(i, i, 14 - 2 * i, 1, "rose", light + 1 - i)
            face.rect(i, 13 - i, 14 - 2 * i, 1, "rose", light + 1 - i)
            face.rect(i, i, 1, 14 - 2 * i, "rose", light + 1 - i)
            face.rect(13 - i, i, 1, 14 - 2 * i, "rose", light + 1 - i)
        face.rect(2, 2, 10, 1, "cream", light + 1)
        face.rect(2, 2, 1, 10, "cream", light + 1)
        face.rect(6, 4, 2, 6, "rose", light + 2)
        face.rect(4, 6, 6, 2, "rose", light + 2)
    for face in sides(p, "petal_n.petal"):
        face.row(0, "rose", 2)
        face.row(1, "rose", 1)

    for name in ("pod_a", "pod_b", "pod_c", "pod_d"):
        key = "pods." + name
        p.box(key, "rose", 1)
        for face in sides(p, key):
            face.row(0, "cream", 1)
            face.glow(1, 1, 2, 3)
            face.rect(2, 1, 1, 3, "rose", 0)
        p.face(key, "down").fill("rose", 4)
        p.face(key, "down").glow(1, 1, 2, 2)

    for key, ends in (("vine_fl.stem", ()), ("vine_fl_tip.stem", (10,))):
        p.box(key, "ochre", 1)
        for face in sides(p, key):
            for y in range(3, face.h, 4):
                face.rect(0, y, 2, 1, "ochre", 0)
            for y in ends:
                face.rect(0, y, 2, face.h - y, "rose", 1)
    p.box("vine_fl_tip.bud", "rose", 2)
    for face in sides(p, "vine_fl_tip.bud"):
        face.row(0, "rose", 1)
        face.glow(0, 2, 3, 1)
    bud = p.face("vine_fl_tip.bud", "down")
    bud.glow(0, 0, 3, 3)
    bud.glow(1, 1, 1, 1, step=4, under=2)


PAINTERS = {
    "xertz_forager": xertz_forager,
    "spore_drifter": spore_drifter,
    "mossback": mossback,
    "bloom_host": bloom_host,
    "drift_leviathan": drift_leviathan,
    "bloom_sovereign": bloom_sovereign,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
