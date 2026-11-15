#!/usr/bin/env python3
"""Block painters: the 16x16 face textures and the block model JSON for all nine blocks.

    python tools/art/paint/blocks.py                  # write textures/block/*.png and models/block/*.json
    python tools/art/paint/blocks.py --check          # prove the committed files match
    python tools/art/paint/blocks.py --sheet out.png  # also write a review sheet

Blocks are station hardware: worn ``white`` plating, ``oxide`` frames and ``hazard`` warning bands
from the wrecks ramps, with each block's function shown in one accent. Model ids, parents and
collision boxes are unchanged; the shaped blocks only gain elements inside their existing box.
Every face is opaque (no block here is registered on a cutout layer), so the incubator and the
terrarium are open frames rather than glass, which keeps what they display visible.
"""

from __future__ import annotations

import json
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from _sprite import ASSETS, SIZE, Tile, write_images, write_text  # noqa: E402
from pixelkit import Image  # noqa: E402

BLOCK_TEXTURES = os.path.join(ASSETS, "textures", "block")
BLOCK_MODELS = os.path.join(ASSETS, "models", "block")
TEXTURES = {}
MODELS = {}
NS = "nerocreatures:block/"


def tile(name: str, habitat: str = "wrecks", ramp: str = None, step: int = 2) -> Tile:
    made = Tile(habitat, ramp, step)
    TEXTURES[name] = made.image
    return made


def rivets(t: Tile, points, ramp: str = "oxide", step: int = 1) -> None:
    for x, y in points:
        t.px(x, y, ramp, step)


def plating(t: Tile, seam_rows=(7,), seam_cols=()) -> None:
    """Worn hull plate: lit top edge, shadowed bottom edge, panel seams."""
    t.rect(0, 0, SIZE, SIZE, "white", 2)
    t.row(0, "white", 3)
    t.row(15, "white", 0)
    t.col(0, "white", 3, 1, 14)
    t.col(15, "white", 1, 1, 14)
    for y in seam_rows:
        t.row(y, "white", 0)
        t.row(y + 1, "white", 3)
    for x in seam_cols:
        t.col(x, "white", 0)


# --------------------------------------------------------------------------- shared tiles

t = tile("habitat_plating")
plating(t, seam_rows=(7,), seam_cols=(8,))
rivets(t, [(2, 2), (13, 2), (2, 12), (13, 12)])
t.rect(10, 10, 2, 3, "oxide", 3)

t = tile("habitat_frame", ramp="oxide", step=1)
t.row(0, "oxide", 2)
t.col(0, "oxide", 2)
t.row(15, "oxide", 0)
t.col(15, "oxide", 0)
for i in range(3, 13, 4):
    t.rect(i, 3, 2, 10, "oxide", 0)
    t.col(i, "oxide", 2, 3, 10)

# --------------------------------------------------------------------------- nest

def weave(t: Tile, a, b) -> None:
    """Basket weave: 4x2 horizontal straps of ``a`` crossing vertical straps of ``b``."""
    for y in range(SIZE):
        for x in range(SIZE):
            if ((x // 4) + (y // 2)) % 2 == 0:
                t.px(x, y, a[0], a[1] + (1 if y % 2 == 0 else -1))
            else:
                t.px(x, y, b[0], b[1] + (1 if x % 2 == 0 else -1))


t = tile("nest_side")
weave(t, ("white", 1), ("oxide", 2))
t.row(10, "hazard", 2)
t.row(11, "hazard", 1)
t.row(15, "oxide", 0)

t = tile("nest_top")
weave(t, ("white", 2), ("oxide", 3))
t.frame(0, 0, 16, 16, "oxide", 1)
t.frame(1, 1, 14, 14, "hazard", 2)

t = tile("nest_bed", ramp="white", step=3)
for y in range(0, SIZE, 4):
    for x in range(0, SIZE, 4):
        off = 2 if (y // 4) % 2 else 0
        t.rect((x + off) % SIZE, y, 2, 2, "white", 4)
        t.rect((x + off + 2) % SIZE, y + 2, 2, 1, "white", 1)
t.frame(0, 0, 16, 16, "oxide", 1)

# --------------------------------------------------------------------------- feeding trough

t = tile("feeding_trough_side")
plating(t, seam_rows=(), seam_cols=(5, 10))
t.rect(0, 0, 16, 8, "white", 1)
t.row(8, "white", 4)
t.stripes(0, 9, 16, 2, ("hazard", 2), ("oxide", 1))
t.row(11, "white", 0)
t.rect(0, 12, 16, 2, "white", 1)
t.rect(0, 14, 16, 2, "oxide", 1)
t.row(14, "oxide", 2)

t = tile("feeding_trough_feed", habitat="greenxertz", ramp="ochre", step=1)
for y in range(0, SIZE, 2):
    for x in range(0, SIZE, 4):
        off = 2 if (y // 2) % 2 else 0
        t.rect((x + off) % SIZE, y, 2, 2, "ochre", 3)
        t.px((x + off) % SIZE, y, "cream", 2)
for x, y in ((2, 3), (9, 5), (13, 10), (5, 12), (10, 1)):
    t.rect(x, y, 2, 1, "accent", 2)

# --------------------------------------------------------------------------- incubator

t = tile("incubator_base")
plating(t, seam_rows=(), seam_cols=())
# The base occupies the bottom four rows of a side; the cap two rows at y 12..14 (rows 2..3).
t.rect(0, 2, 16, 2, "white", 3)
t.row(3, "white", 1)
t.rect(0, 12, 16, 4, "white", 2)
t.row(12, "white", 4)
t.row(15, "oxide", 1)
t.stripes(1, 13, 4, 2, ("hazard", 2), ("oxide", 1))
t.stripes(11, 13, 4, 2, ("hazard", 2), ("oxide", 1))
t.rect(6, 13, 4, 2, "oxide", 0)
t.rect(7, 13, 2, 1, "hazard", 3)   # status lamp (amber)
t.rect(7, 14, 2, 1, "hazard", 1)

t = tile("incubator_deck")
plating(t, seam_rows=(), seam_cols=())
t.frame(1, 1, 14, 14, "oxide", 1)
t.rect(4, 4, 8, 8, "oxide", 0)
t.frame(4, 4, 8, 8, "accent_owned", 1)
t.rect(6, 6, 4, 4, "white", 3)       # cradle pad
t.frame(6, 6, 4, 4, "white", 1)
for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
    t.px(x, y, "accent_owned", 3)

t = tile("incubator_top")
plating(t, seam_rows=(), seam_cols=())
t.frame(2, 2, 12, 12, "white", 0)
t.rect(4, 4, 8, 8, "white", 3)
t.frame(4, 4, 8, 8, "white", 4)
for y in (6, 8):
    t.rect(6, y, 4, 1, "oxide", 1)   # vent slots
rivets(t, [(3, 3), (12, 3), (3, 12), (12, 12)])

# --------------------------------------------------------------------------- terrarium

t = tile("terrarium_base")
plating(t, seam_rows=(), seam_cols=())
t.rect(0, 13, 16, 3, "white", 2)
t.row(13, "white", 4)
t.row(15, "oxide", 1)
t.rect(6, 14, 4, 1, "accent_owned", 2)
t.rect(0, 1, 16, 1, "white", 3)      # top rail (y 14..15)
t.rect(0, 2, 16, 11, "oxide", 1)     # never sampled by the rails; keeps the tile opaque

t = tile("terrarium_floor", ramp="oxide", step=3)
for y in range(0, SIZE, 4):
    for x in range(0, SIZE, 4):
        off = 2 if (y // 4) % 2 else 0
        t.rect((x + off) % SIZE, y + 1, 2, 2, "oxide", 2)
for x, y in ((3, 4), (11, 2), (7, 10), (12, 12), (2, 12)):
    t.rect(x, y, 2, 1, "greenxertz.accent", 1)
t.frame(0, 0, 16, 16, "white", 1)
t.frame(1, 1, 14, 14, "white", 3)

# --------------------------------------------------------------------------- trophy plaque

t = tile("trophy_plaque", ramp="oxide", step=2)
t.grid("""
................
.wwwwwwwwwwwwww.
.wllllllllllllw.
.wl..........dw.
.wl..........dw.
.wl..........dw.
.wl..........dw.
.wl..........dw.
.wl..........dw.
.wwl........dww.
..wl........dw..
..wwl......dww..
...wwlhhhhdww...
....wwhhhhww....
.....wwwwww.....
......wwww......
""".split(), "w=white2 l=oxide3 d=oxide1 h=hazard2")
t.rect(7, 2, 2, 2, "white", 4)       # hook plate, under the hook element
rivets(t, [(2, 2), (13, 2)], "oxide", 0)

# --------------------------------------------------------------------------- lure beacon

def device_side(name: str, motif, legend: str) -> None:
    t = tile(name)
    plating(t, seam_rows=(), seam_cols=())
    t.rect(2, 2, 12, 10, "oxide", 0)
    t.frame(2, 2, 12, 10, "oxide", 1)
    t.stripes(0, 13, 16, 2, ("hazard", 2), ("oxide", 1))
    t.row(15, "oxide", 0)
    t.grid(motif, legend)


LURE_MOTIF = """
................
................
................
.....c....c.....
.....cbbbbc.....
.....ccbbcc.....
......cccc......
.......mm.......
.......mm.......
.......mm.......
.....mmmmmm.....
................
................
................
................
................
""".split()
device_side("lure_beacon_side", LURE_MOTIF, "c=white3 m=white1 b=greenxertz.accent0")
device_side("lure_beacon_side_lit", LURE_MOTIF, "c=white4 m=white1 b=greenxertz.accent3")


def lure_top(name: str, lit: bool) -> None:
    t = tile(name)
    plating(t, seam_rows=(), seam_cols=())
    t.rect(3, 2, 10, 12, "oxide", 1)
    t.rect(2, 3, 12, 10, "oxide", 1)
    t.rect(4, 3, 8, 10, "white", 1)
    t.rect(3, 4, 10, 8, "white", 1)
    t.rect(5, 4, 6, 8, "oxide", 0)
    t.rect(4, 5, 8, 6, "oxide", 0)
    bait = "greenxertz.accent"
    t.rect(6, 5, 4, 6, bait, 2 if lit else 0)
    t.rect(5, 6, 6, 4, bait, 2 if lit else 0)
    t.rect(6, 6, 4, 4, bait, 3 if lit else 1)
    if lit:
        t.rect(7, 7, 2, 2, bait, 4)
    rivets(t, [(1, 1), (14, 1), (1, 14), (14, 14)])


lure_top("lure_beacon_top", False)
lure_top("lure_beacon_top_lit", True)

# --------------------------------------------------------------------------- sonic ward

WARD_MOTIF = """
................
................
................
....r......r....
...r..r..r..r...
...r.r.dd.r.r...
...r.r.dd.r.r...
...r..r..r..r...
....r..pp..r....
.......pp.......
......pppp......
................
................
................
................
................
""".split()
device_side("sonic_ward_side", WARD_MOTIF, "d=white3 p=white1 r=nyxara.accent_b0")
device_side("sonic_ward_side_lit", WARD_MOTIF, "d=white4 p=white1 r=nyxara.accent_b3")


def ward_top(name: str, lit: bool) -> None:
    t = tile(name)
    plating(t, seam_rows=(), seam_cols=())
    ring = "nyxara.accent_b"
    t.rect(1, 1, 14, 14, "oxide", 0)
    t.frame(1, 1, 14, 14, "oxide", 1)
    t.frame(2, 2, 12, 12, ring, 3 if lit else 0)
    t.frame(4, 4, 8, 8, ring, 2 if lit else 0)
    t.rect(6, 6, 4, 4, "white", 2)
    t.frame(6, 6, 4, 4, "white", 4 if lit else 3)
    t.rect(7, 7, 2, 2, ring, 4 if lit else 1)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        t.px(x, y, "oxide", 1)


ward_top("sonic_ward_top", False)
ward_top("sonic_ward_top_lit", True)

# --------------------------------------------------------------------------- apex altar

t = tile("apex_altar_side", ramp="oxide", step=1)
t.rect(0, 0, 16, 3, "white", 2)
t.row(0, "white", 4)
t.row(2, "white", 0)
t.rect(0, 13, 16, 3, "white", 1)
t.row(13, "white", 3)
t.row(15, "oxide", 0)
for x in (1, 6, 11):
    t.rect(x, 4, 4, 8, "oxide", 0)
    t.col(x, "oxide", 2, 4, 8)
    t.rect(x + 1, 6, 2, 4, "hazard", 1)
    t.rect(x + 1, 6, 2, 1, "hazard", 3)

SOCKETS = (("cindara.accent", 2, 2), ("glacira.accent", 11, 2),
           ("greenxertz.accent", 2, 11), ("nyxara.accent", 11, 11))


def altar_top(name: str, lit: bool) -> None:
    t = tile(name, ramp="oxide", step=1)
    t.frame(0, 0, 16, 16, "white", 2)
    t.row(0, "white", 4)
    t.row(15, "white", 0)
    for accent, x, y in SOCKETS:
        t.rect(x - 1, y - 1, 5, 5, "white", 1)
        t.rect(x, y, 3, 3, "oxide", 0)
        t.rect(x, y, 3, 3, accent, 3 if lit else 0)
        t.px(x + 1, y + 1, accent, 4 if lit else 1)
    # the sigil slot
    t.rect(5, 5, 6, 6, "white", 1)
    t.frame(5, 5, 6, 6, "white", 3)
    t.rect(6, 6, 4, 4, "oxide", 0)
    if lit:
        t.rect(6, 6, 4, 4, "luna.accent", 2)
        t.rect(7, 7, 2, 2, "luna.accent", 4)
        for x, y in ((7, 3), (7, 12), (3, 7), (12, 7)):
            t.rect(x, y, 2 if y in (3, 12) else 1, 1 if y in (3, 12) else 2, "luna.accent", 3)
    else:
        t.rect(7, 7, 2, 2, "oxide", 2)


altar_top("apex_altar_top", False)
altar_top("apex_altar_top_lit", True)

# --------------------------------------------------------------------------- ephemeral crust

CRACKS = """
....c...........
....c......c....
...cc.....cc....
..cc.c....c.....
cc....c..cc.....
.......ccc..c...
.......c.ccc....
......cc....cc..
.....cc.......cc
...ccc..........
..cc..c.........
.cc....c....c...
.c.....cc..cc...
........c.cc....
........ccc.....
.........c......
""".split()


def crust(name: str, habitat: str, body: str, shades, crack: str, hot: str = None) -> None:
    t = tile(name, habitat=habitat, ramp=body, step=shades[0])
    for y in range(0, SIZE, 4):
        for x in range(0, SIZE, 4):
            if ((x // 4) * 3 + (y // 4) * 5) % 4 == 0:
                t.rect(x, y, 4, 4, body, shades[1])
            elif ((x // 4) + (y // 4) * 2) % 5 == 0:
                t.rect(x, y, 4, 2, body, shades[2])
    t.grid(CRACKS, "c=" + crack)
    if hot:
        for x, y in ((4, 2), (9, 5), (6, 8), (2, 11), (10, 13)):
            t.px(x, y, hot.split("@")[0], int(hot.split("@")[1]))


crust("ephemeral_crust_slag_side", "cindara", "ash", (1, 0, 2), "accent2", "accent@4")
crust("ephemeral_crust_slag_end", "cindara", "clinker", (1, 0, 2), "accent2", "accent@3")
crust("ephemeral_crust_rime", "glacira", "accent", (3, 4, 2), "indigo3", "wool@4")
crust("ephemeral_crust_prism", "nyxara", "opal", (2, 4, 1), "accent2", "accent_b@3")

# --------------------------------------------------------------------------- models

def cube(parent: str, **textures) -> dict:
    return {"parent": "minecraft:block/" + parent, "textures": {k: NS + v for k, v in textures.items()}}


def box(x1, y1, z1, x2, y2, z2, side: str, up: str = None, down: str = None) -> dict:
    faces = {"down": {"texture": "#" + (down or side)}, "up": {"texture": "#" + (up or side)}}
    for name in ("north", "south", "west", "east"):
        faces[name] = {"texture": "#" + side}
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}


def shaped(particle: str, textures: dict, elements: list) -> dict:
    table = {"particle": NS + particle}
    table.update({k: NS + v for k, v in textures.items()})
    return {"parent": "minecraft:block/block", "textures": table, "elements": elements}


MODELS["nest"] = shaped("nest_side", {"side": "nest_side", "top": "nest_top", "bed": "nest_bed"}, [
    box(1, 0, 1, 15, 2, 15, "side", up="bed"),
    box(0, 0, 0, 16, 6, 2, "side", up="top"),
    box(0, 0, 14, 16, 6, 16, "side", up="top"),
    box(0, 0, 2, 2, 6, 14, "side", up="top"),
    box(14, 0, 2, 16, 6, 14, "side", up="top"),
])

MODELS["feeding_trough"] = shaped(
    "feeding_trough_side",
    {"side": "feeding_trough_side", "top": "feeding_trough_feed", "plate": "habitat_plating",
     "bottom": "habitat_frame"}, [
        box(1, 0, 3, 3, 2, 5, "bottom"),
        box(13, 0, 3, 15, 2, 5, "bottom"),
        box(1, 0, 11, 3, 2, 13, "bottom"),
        box(13, 0, 11, 15, 2, 13, "bottom"),
        box(0, 2, 2, 16, 4, 14, "side", up="plate", down="bottom"),
        box(0, 4, 2, 16, 8, 3, "side", up="plate"),
        box(0, 4, 13, 16, 8, 14, "side", up="plate"),
        box(0, 4, 3, 1, 8, 13, "side", up="plate"),
        box(15, 4, 3, 16, 8, 13, "side", up="plate"),
        box(1, 4, 3, 15, 6, 13, "bottom", up="top"),
    ])

MODELS["incubator"] = shaped(
    "incubator_base",
    {"side": "incubator_base", "top": "incubator_top", "deck": "incubator_deck",
     "bottom": "habitat_frame"}, [
        box(1, 0, 1, 15, 4, 15, "side", up="deck", down="bottom"),
        box(2, 4, 2, 3, 12, 3, "bottom"),
        box(13, 4, 2, 14, 12, 3, "bottom"),
        box(2, 4, 13, 3, 12, 14, "bottom"),
        box(13, 4, 13, 14, 12, 14, "bottom"),
        box(1, 12, 1, 15, 14, 15, "side", up="top", down="bottom"),
    ])

MODELS["terrarium"] = shaped(
    "terrarium_base",
    {"side": "terrarium_base", "top": "habitat_plating", "floor": "terrarium_floor",
     "bottom": "habitat_frame"}, [
        box(1, 0, 1, 15, 3, 15, "side", up="floor", down="bottom"),
        box(1, 3, 1, 2, 14, 2, "bottom"),
        box(14, 3, 1, 15, 14, 2, "bottom"),
        box(1, 3, 14, 2, 14, 15, "bottom"),
        box(14, 3, 14, 15, 14, 15, "bottom"),
        box(1, 14, 1, 15, 15, 2, "side", up="top", down="bottom"),
        box(1, 14, 14, 15, 15, 15, "side", up="top", down="bottom"),
        box(1, 14, 2, 2, 15, 14, "side", up="top", down="bottom"),
        box(14, 14, 2, 15, 15, 14, "side", up="top", down="bottom"),
    ])

MODELS["trophy_plaque"] = shaped(
    "trophy_plaque",
    {"side": "trophy_plaque", "top": "habitat_plating", "bottom": "habitat_frame"}, [
        box(1, 6, 15, 15, 15, 16, "side", up="top", down="bottom"),
        box(2, 4, 15, 14, 6, 16, "side", up="top", down="bottom"),
        box(4, 2, 15, 12, 4, 16, "side", up="top", down="bottom"),
        box(6, 1, 15, 10, 2, 16, "side", up="top", down="bottom"),
        box(7, 12, 14, 9, 14, 15, "top", down="bottom"),
    ])

MODELS["lure_beacon"] = cube("cube_bottom_top", side="lure_beacon_side", top="lure_beacon_top",
                             bottom="habitat_frame")
MODELS["lure_beacon_lit"] = cube("cube_bottom_top", side="lure_beacon_side_lit", top="lure_beacon_top_lit",
                                 bottom="habitat_frame")
MODELS["sonic_ward"] = cube("cube_bottom_top", side="sonic_ward_side", top="sonic_ward_top",
                            bottom="habitat_frame")
MODELS["sonic_ward_lit"] = cube("cube_bottom_top", side="sonic_ward_side_lit", top="sonic_ward_top_lit",
                                bottom="habitat_frame")
MODELS["apex_altar"] = cube("cube_bottom_top", top="apex_altar_top", side="apex_altar_side",
                            bottom="habitat_frame")
MODELS["apex_altar_lit"] = cube("cube_bottom_top", top="apex_altar_top_lit", side="apex_altar_side",
                                bottom="habitat_frame")
MODELS["ephemeral_crust_slag"] = cube("cube_column", end="ephemeral_crust_slag_end",
                                      side="ephemeral_crust_slag_side")
MODELS["ephemeral_crust_rime"] = cube("cube_all", all="ephemeral_crust_rime")
MODELS["ephemeral_crust_prism"] = cube("cube_all", all="ephemeral_crust_prism")


# --------------------------------------------------------------------------- review sheet

def _elements(model: dict) -> list:
    """Every model as boxes with a texture name per face (cube parents become one full box)."""
    tex = {k: v.split("/")[-1] for k, v in model["textures"].items()}
    if "elements" in model:
        return [(e["from"], e["to"], {f: tex[d["texture"][1:]] for f, d in e["faces"].items()})
                for e in model["elements"]]
    parent = model["parent"].split("/")[-1]
    if parent == "cube_all":
        faces = {f: tex["all"] for f in ("up", "down", "north", "south", "west", "east")}
    elif parent == "cube_column":
        faces = {f: tex["side"] for f in ("north", "south", "west", "east")}
        faces.update(up=tex["end"], down=tex["end"])
    else:
        faces = {f: tex["side"] for f in ("north", "south", "west", "east")}
        faces.update(up=tex["top"], down=tex["bottom"])
    return [([0, 0, 0], [16, 16, 16], faces)]


def render(model: dict, scale: int, flip: bool = False) -> Image:
    """A small isometric view (top, south and east faces; ``flip`` looks from the north-west)."""
    voxels = {}
    for lo, hi, faces in _elements(model):
        for x in range(lo[0], hi[0]):
            for y in range(lo[1], hi[1]):
                for z in range(lo[2], hi[2]):
                    voxels[(x, y, z)] = faces
    out = Image(34 * scale, 34 * scale)

    def shade(c, k):
        return (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)

    for (x, y, z) in sorted(voxels, key=lambda p: ((15 - p[0] if flip else p[0]) + (15 - p[2] if flip else p[2]) + p[1])):
        faces = voxels[(x, y, z)]
        vx, vz = (15 - x, 15 - z) if flip else (x, z)
        sx = (vx - vz + 16) * scale
        sy = (vx + vz) * scale // 2 + (16 - y) * scale
        top = TEXTURES[faces["up"]].px[z][x]
        left = TEXTURES[faces["north" if flip else "south"]].px[15 - y][15 - x if flip else x]
        right = TEXTURES[faces["west" if flip else "east"]].px[15 - y][z if flip else 15 - z]
        out.rect(sx, sy, 2 * scale, scale, top)
        out.rect(sx, sy + scale, scale, scale, shade(left, 0.82))
        out.rect(sx + scale, sy + scale, scale, scale, shade(right, 0.64))
    return out


def sheet(path: str) -> None:
    from preview import INK, PAPER, text

    scale, tscale = 6, 5
    names = list(MODELS)
    cell = 34 * scale + 16
    out = Image(5 * cell + 16, 3 * (cell + 14) + 40 + ((len(TEXTURES) + 9) // 10) * (16 * tscale + 24), PAPER)
    text(out, 8, 8, "NEROCREATURES BLOCKS: %d MODELS, %d TEXTURES" % (len(MODELS), len(TEXTURES)), INK, 2)
    for i, name in enumerate(names):
        x0, y0 = 8 + (i % 5) * cell, 30 + (i // 5) * (cell + 14)
        out.rect(x0, y0, cell - 8, cell - 8, (124, 128, 136, 255) if i % 2 else (60, 62, 72, 255))
        out.blit(render(MODELS[name], scale, flip=name == "trophy_plaque"), x0 + 4, y0 + 4)
        text(out, x0, y0 + cell - 4, name, INK, 1)
    ty = 30 + 3 * (cell + 14)
    for i, name in enumerate(TEXTURES):
        x0, y0 = 8 + (i % 10) * (16 * tscale + 26), ty + (i // 10) * (16 * tscale + 24)
        out.blit(TEXTURES[name], x0, y0, tscale)
        text(out, x0, y0 + 16 * tscale + 3, name.replace("ephemeral_", "")[:24], INK, 1)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(out.to_png())
    print("sheet " + path)


def main(argv: list) -> int:
    check = "--check" in argv
    failed = write_images(BLOCK_TEXTURES, TEXTURES, check)
    for name in sorted(MODELS):
        body = json.dumps(MODELS[name], indent=2) + "\n"
        failed += not write_text(os.path.join(BLOCK_MODELS, name + ".json"), body, check)
    used = set(v.split("/")[-1] for m in MODELS.values() for v in m["textures"].values())
    for orphan in sorted(set(TEXTURES) - used):
        print("FAIL  textures/block/%s.png is used by no model" % orphan)
        failed += 1
    for image_name, image in TEXTURES.items():
        if any(c[3] != 255 for row in image.px for c in row):
            print("FAIL  textures/block/%s.png has a transparent texel" % image_name)
            failed += 1
    if "--sheet" in argv:
        sheet(argv[argv.index("--sheet") + 1])
    print("blocks: %d textures, %d models, %s" % (len(TEXTURES), len(MODELS), "FAILED" if failed else "clean"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
