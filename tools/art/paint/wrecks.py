#!/usr/bin/env python3
"""Wrecks and pirates painters: one function per entity.

    python tools/art/paint/wrecks.py            # paint and write
    python tools/art/paint/wrecks.py --check    # prove the committed PNGs match

Wrecks read as mid-grey plating, so the light mass is worn ``white`` hull plate, the dark mass is
``oxide`` (rusted frames, joints, cloth) and ``hazard`` yellow is the mid mass, used the way a
shipyard uses it: stripes, pads and warning bands. Sensor red is the only glow on hostile machines
and pirates; the owned Terraforming Drone glows teal instead, so the two never read alike.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import FACES, face_rect, run_painters  # noqa: E402

SIDES = ("north", "south", "east", "west")
HAZE_ALPHA = 150


def plate(p, key: str, ramp: str, step: int, lip: bool = True, hem: bool = True) -> None:
    """One cube as a pressed plate: flat fill, a lit top edge and a shadowed bottom edge on its sides."""
    p.box(key, ramp, step)
    for face in p.each_face(key, SIDES):
        if lip and face.h > 2:
            face.row(0, ramp, step + 1)
        if hem and face.h > 3:
            face.row(-1, ramp, step - 1)


def stripes(face, x: int, y: int, w: int, h: int, a=("hazard", 2), b=("oxide", 1), band: int = 2) -> None:
    """Diagonal warning stripes, ``band`` texels wide."""
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            ramp, step = a if ((xx + yy) // band) % 2 == 0 else b
            face.px(xx, yy, ramp, step)


def seams(face, ramp: str, step: int, every: int = 5, start: int = 2) -> None:
    """Horizontal panel seams."""
    for y in range(start, face.h - 1, every):
        face.row(y, ramp, step)


def rust(face, spots, step: int = 2) -> None:
    """Two-texel rust runs under a seam or rivet."""
    for x, y, n in spots:
        if 0 <= x < face.w and y + n <= face.h:
            face.rect(x, y, 2, n, "oxide", step)


# --------------------------------------------------------------------------- cache_mimic

def cache_mimic(p) -> None:
    """Light mass: the grey salvage plating (white ramp, low steps). Dark mass: oxide corner posts, skid
    row, legs and the inside of the maw. Mid: one hazard band under the seam and the lid pad.
    Accent: the hairline seam under the lid, the maw floor and the tongue tip. Skin 1 (variant 1)
    adds a worn white cap plate and name strip. An original cargo crate: full block, banded, no latch knob."""

    p.box("crate.base", "white", 1)
    p.box("lid.lid", "white", 1)
    for key in ("crate.base", "lid.lid"):
        for face in p.each_face(key, SIDES):
            face.rect(0, 0, 2, face.h, "oxide", 1)                 # corner posts
            face.rect(face.w - 2, 0, 2, face.h, "oxide", 1)
            face.rect(0, face.h // 2, 2, 1, "oxide", 3)            # bolt band on each post
            face.rect(face.w - 2, face.h // 2, 2, 1, "oxide", 3)
    for face in p.each_face("crate.base", SIDES):
        stripes(face, 2, 1, 12, 2)                                 # hazard band under the seam
        face.rect(2, 0, 12, 1, "oxide", 0)                         # the dark gap of the seam
        face.rect(2, 3, 12, 1, "white", 2)
        face.rect(4, 5, 8, 3, "white", 0)                          # recessed panel
        face.rect(4, 5, 8, 1, "oxide", 0)
        face.row(-1, "oxide", 0)                                   # skid
    front = p.face("crate.base", "north")
    front.rect(6, 6, 4, 1, "oxide", 2)                             # pull bar in the recess
    rust(p.face("crate.base", "south"), ((5, 6, 2),))
    for face in p.each_face("lid.lid", SIDES):
        face.rect(2, 0, 12, 1, "white", 2)
        face.rect(2, 2, 12, 2, "white", 0)                         # grip channel
        face.rect(2, face.h - 1, 12, 1, "white", 0)
    p.face("lid.lid", "north").glow(3, 5, 10, 1, step=3, under=1)   # the hairline seam (the tell)
    top = p.top("lid.lid")
    top.fill("white", 1)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        top.rect(x, y, 2, 2, "oxide", 1)                           # post heads
    top.rect(2, 2, 12, 12, "white", 0)
    top.rect(3, 3, 10, 10, "white", 1)
    stripes(top, 5, 5, 6, 6)                                       # lift pad
    top.rect(7, 7, 2, 2, "oxide", 0)
    roof = p.under("lid.lid")                                      # roof of the mouth
    roof.fill("oxide", 2, raw=True)
    for y in (3, 7, 11):
        roof.rect(2, y, 12, 1, "oxide", 3, raw=True)
    floor = p.top("crate.base")                                    # maw floor
    floor.fill("oxide", 0, raw=True)
    floor.rect(1, 1, 14, 14, "oxide", 1, raw=True)
    floor.rect(3, 3, 10, 10, "oxide", 0, raw=True)
    floor.glow(5, 5, 6, 6, step=3, under=1)
    floor.glow(7, 7, 2, 2, step=4, under=2)
    p.under("crate.base").fill("oxide", 2)

    for key in ("fangs_top.fang_l", "fangs_top.fang_r", "fangs_low.fang_l", "fangs_low.fang_r"):
        p.box(key, "white", 4)
    for key in ("fangs_top.fang_l", "fangs_top.fang_r"):
        for face in p.each_face(key, ("north", "south")):
            face.row(0, "white", 2)                                # root in shadow, tip bright
    p.box("tongue.tongue", "oxide", 3)
    tongue = p.top("tongue.tongue")
    tongue.rect(0, 2, 2, 4, "oxide", 2)
    tongue.glow(0, 0, 2, 1, step=3, under=2)
    p.face("tongue.tongue", "north").glow(0, 0, 2, 1, step=3, under=2)
    p.box("leg_fl.leg", "oxide", 1)
    for face in p.each_face("leg_fl.leg", SIDES):
        face.row(0, "white", 0)                                    # hip collar
        face.rect(0, 3, 2, 2, "oxide", 0)                          # claw

    # Skin 1: worn white plating.
    p.box("cap.cap", "white", 3)
    cap = p.top("cap.cap")
    cap.fill("white", 3)
    cap.rect(1, 1, 8, 8, "white", 2)
    cap.rect(2, 2, 6, 6, "white", 3)
    cap.rect(4, 2, 2, 6, "hazard", 2)
    rust(cap, ((1, 7, 2), (7, 1, 1)), 3)
    p.box("strip.strip", "white", 3)
    strip = p.face("strip.strip", "north")
    strip.rect(1, 1, 3, 1, "oxide", 1)
    strip.rect(5, 1, 2, 1, "oxide", 1)
    strip.rect(8, 1, 3, 1, "hazard", 1)


# --------------------------------------------------------------------------- static_swarm

def static_swarm(p) -> None:
    """Translucent, so gaps in the paint are gaps in the cloud. Every texel is accent: white-hot mote
    and core centres (accent step 4), sensor-red edges, and a see-through haze of forked sparks round
    the core. There is no body: nothing here uses a material ramp."""

    for key in p.cubes():
        if key.startswith("arc_"):
            for name in FACES:
                face = p.face(key, name)
                face.glow(0, 0, face.w, face.h, step=3, under=3)
                if face.w == 3:
                    face.glow(1, 0, 1, 3, step=4, under=4)
                    face.glow(0, 1, 3, 1, step=4, under=4)
                else:
                    face.glow(0, 0, 2, 1, step=4, under=4)
    for name in FACES:
        face = p.face("core.core", name)
        face.glow(0, 0, 4, 4, step=3, under=3)
        face.glow(1, 1, 2, 2, step=4, under=4)
        for key in ("core.spark_a", "core.spark_b"):
            spark = p.face(key, name)
            spark.glow(0, 0, 2, 2, step=4, under=4)
        haze = p.face("core.haze", name)
        for x, y, w in ((0, 1, 2), (2, 2, 2), (4, 1, 2), (1, 4, 2), (3, 5, 3), (0, 3, 1)):
            haze.glow(x, y, w, 1, step=2, under=2)
        haze.glow(2, 0, 1, 2, step=3, under=3)
        haze.glow(4, 3, 1, 2, step=3, under=3)
    _, cube = p._cubes["core.haze"]
    for name in FACES:
        x0, y0, w, h = face_rect(cube["uv"], cube["size"], name)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                r, g, b, a = p.base.px[y][x]
                if a:
                    p.base.px[y][x] = (r, g, b, HAZE_ALPHA)


# --------------------------------------------------------------------------- shared machine looks

def android_torso(p, key: str) -> None:
    """The android chassis look, shared by the Rogue Android and the Scrap Hulk's big arm."""
    plate(p, key, "white", 2)
    for face in p.each_face(key, SIDES):
        seams(face, "white", 1, 6, 5)
    for side in p.flanks(key):
        side.rect(side.w // 2 - 1, 2, 2, side.h - 4, "white", 1)  # side seam
        stripes(side, 1, side.h - 4, side.w - 2, 2)
    back = p.face(key, "south")
    rust(back, ((2, 6, 3), (back.w - 4, 3, 2)))
    p.under(key).fill("oxide", 2)


def shield_plate(p, key: str) -> None:
    """A tall shield plate: bright slab, hazard chevron band, rusted lower edge."""
    plate(p, key, "white", 3)
    for side in p.flanks(key):
        side.rect(1, 1, side.w - 2, 1, "white", 4)
        stripes(side, 0, 3, side.w, 2)
        side.rect(1, side.h - 4, side.w - 2, 1, "white", 2)
        rust(side, ((2, side.h - 3, 2), (side.w - 4, side.h - 2, 1)))
    for name in ("north", "south"):
        edge = p.face(key, name)
        edge.fill("white", 2)
        edge.rect(0, 3, edge.w, 2, "hazard", 1)
        edge.row(-1, "oxide", 2)
    p.under(key).fill("oxide", 1)


# --------------------------------------------------------------------------- scrap_hulk

def scrap_hulk(p) -> None:
    """Mid mass: the grimy grey chassis (white 0-1) streaked with rust. Light mass: the android torso
    that is its big arm, the chest plate and the dead drone on its back. Dark mass: oxide pelvis, legs,
    pipes, boom and every underside. Hazard: the shoulder pad and warning bands. Accent: the chest
    slit, the android's old chest sensor, and the core that shows once the chest plate is shed."""

    plate(p, "chassis.torso", "white", 0)
    for face in p.each_face("chassis.torso", SIDES):
        seams(face, "oxide", 2, 5, 4)
        rust(face, ((2, 5, 3), (face.w - 5, 10, 3), (face.w // 2, 1, 2)))
    front = p.face("chassis.torso", "north")
    front.rect(3, 2, 10, 10, "oxide", 0)                           # the cavity behind the chest plate
    top = p.top("chassis.torso")
    top.fill("white", 0)
    top.rect(2, 2, 12, 9, "oxide", 2)
    top.rect(3, 3, 10, 7, "oxide", 1)                              # no head: a torn, empty collar ring
    p.under("chassis.torso").fill("oxide", 1)
    plate(p, "chassis.pelvis", "oxide", 2)
    for face in p.each_face("chassis.pelvis", SIDES):
        face.rect(1, 2, face.w - 2, 1, "oxide", 3)
    p.box("chassis.core", "oxide", 1)
    core = p.face("chassis.core", "north")
    core.glow(0, 0, 6, 6, step=3, under=1)
    core.glow(2, 2, 2, 2, step=4, under=2)
    for key in ("chassis.pipe_l", "chassis.pipe_r"):
        p.box(key, "oxide", 2)
        p.top(key).fill("oxide", 0, raw=True)
        for face in p.each_face(key, SIDES):
            face.row(1, "oxide", 3)
    p.box("chassis.rib", "oxide", 2)
    for side in p.flanks("chassis.rib"):
        for x in (1, 5, 9):
            side.rect(x, 0, 1, side.h, "oxide", 0)

    plate(p, "chest_plate.plate", "white", 2)
    chest = p.face("chest_plate.plate", "north")
    stripes(chest, 0, 7, 12, 2)
    chest.rect(1, 1, 10, 1, "white", 3)
    chest.rect(3, 2, 7, 4, "white", 1)                             # frame round the slit
    rust(chest, ((1, 4, 2), (10, 2, 3)))
    p.box("chest_plate.slit", "oxide", 0)
    p.face("chest_plate.slit", "north").glow(0, 0, 5, 2, step=3, under=1)
    p.face("chest_plate.slit", "north").glow(1, 0, 2, 1, step=4, under=2)

    plate(p, "back_drone.hull", "white", 2)                        # a dead drone strapped to the back
    hull = p.face("back_drone.hull", "south")
    hull.rect(1, 1, 8, 8, "white", 1)
    hull.rect(0, 0, 2, 2, "hazard", 2)
    hull.rect(8, 0, 2, 2, "hazard", 2)
    hull.rect(0, 8, 2, 2, "hazard", 2)
    hull.rect(8, 8, 2, 2, "hazard", 2)
    hull.rect(3, 4, 4, 2, "oxide", 1)                              # strap
    for key in ("back_drone.boom_a", "back_drone.boom_b"):
        p.box(key, "oxide", 2)
    p.box("back_drone.lens", "oxide", 0)                           # lens dark: this one is dead

    plate(p, "shoulder_pad.pad", "hazard", 2)
    for side in p.flanks("shoulder_pad.pad"):
        stripes(side, 0, 3, side.w, 2, ("oxide", 1), ("hazard", 2))
        rust(side, ((2, 5, 2), (8, 5, 1)))
    pad = p.top("shoulder_pad.pad")
    pad.rect(1, 1, 4, 10, "hazard", 3)
    pad.rect(2, 5, 2, 2, "oxide", 1)

    plate(p, "arm_big.upper", "oxide", 2)
    for face in p.each_face("arm_big.upper", SIDES):
        seams(face, "oxide", 1, 4, 3)
        face.rect(1, 1, face.w - 2, 1, "white", 0)                 # cable clamp
    android_torso(p, "fore.torso")
    p.box("fore.housing", "oxide", 0)
    p.face("fore.housing", "north").rect(1, 1, 4, 4, "oxide", 1)
    p.face("fore.housing", "north").glow(2, 2, 2, 2, step=3, under=1)
    plate(p, "fore.head", "white", 1)                              # the android's head is the knuckle
    visor = p.face("fore.head", "north")
    visor.rect(1, 1, 6, 2, "oxide", 0)
    p.under("fore.head").fill("oxide", 1)
    for face in p.each_face("fore.head", SIDES):
        face.row(-1, "oxide", 1)                                   # ground scuff
    for key in ("arm_plates.plate_out", "arm_plates.plate_in"):
        shield_plate(p, key)

    p.box("arm_boom.boom", "oxide", 2)
    for face in p.each_face("arm_boom.boom", SIDES):
        for y in (3, 8, 13):
            face.rect(0, y, face.w, 1, "white", 0)                 # truss bands
    plate(p, "arm_boom.collar", "white", 0)
    plate(p, "rotor.hub", "oxide", 1)
    for key, long_x in (("rotor_blades.blade_a", True), ("rotor_blades.blade_b", False)):
        p.box(key, "white", 3)
        for name in ("up", "down"):
            face = p.face(key, name)
            n = face.w if long_x else face.h
            for end in (0, n - 3):
                if long_x:
                    face.rect(end, 0, 3, face.h, "hazard", 2)
                else:
                    face.rect(0, end, face.w, 3, "hazard", 2)

    plate(p, "leg_l.thigh", "oxide", 2)
    for face in p.each_face("leg_l.thigh", SIDES):
        face.rect(1, 2, face.w - 2, 2, "oxide", 3)
    plate(p, "leg_l.foot", "oxide", 1)
    p.top("leg_l.foot").rect(1, 1, 5, 3, "white", 0)
    p.box("leg_r.piston", "white", 0)
    for face in p.each_face("leg_r.piston", SIDES):
        face.rect(0, 2, face.w, 1, "oxide", 2)
        face.rect(0, 5, face.w, 1, "oxide", 2)
    plate(p, "leg_r.foot", "oxide", 1)
    stripes(p.top("leg_r.foot"), 1, 1, 6, 2)


# --------------------------------------------------------------------------- rogue_drone

def rogue_drone(p) -> None:
    """Light mass: the white chassis lid and rotor discs. Dark mass: oxide belly pod, skids, booms and
    lens housing. Mid: hazard corner marks. Accent: the one forward lens."""

    plate(p, "body.chassis", "white", 2)
    top = p.top("body.chassis")
    top.rect(1, 1, 6, 6, "white", 1)
    top.rect(2, 2, 4, 4, "white", 2)
    for x, y in ((0, 0), (6, 0), (0, 6), (6, 6)):
        top.rect(x, y, 2, 2, "hazard", 2)
    for side in p.flanks("body.chassis"):
        side.rect(2, 1, 4, 2, "oxide", 1)                          # vent
    p.face("body.chassis", "south").rect(2, 1, 4, 2, "oxide", 2)
    p.under("body.chassis").fill("oxide", 2)
    plate(p, "body.pod", "oxide", 1)
    for key in ("body.boom_fl", "body.boom_fr", "body.boom_bl", "body.boom_br"):
        p.box(key, "oxide", 2)
    for key in ("body.skid_l", "body.skid_r"):
        p.box(key, "oxide", 1)
        for side in p.flanks(key):
            side.rect(2, 0, 4, 2, "white", 0)                      # open frame: strut colour between the feet
            side.row(-1, "oxide", 0)
    p.box("body.mast", "oxide", 1)
    for face in p.each_face("body.mast", SIDES):
        face.row(0, "hazard", 2)
    p.top("body.mast").fill("hazard", 2)
    plate(p, "lens.housing", "oxide", 1)
    p.box("lens.eye", "oxide", 0)
    p.face("lens.eye", "north").glow(0, 0, 2, 2, step=3, under=1)
    p.face("lens.eye", "north").glow(0, 0, 2, 1, step=4, under=2)
    p.box("rotor_fl.disc", "white", 3)
    disc = p.top("rotor_fl.disc")
    disc.rect(1, 1, 2, 2, "oxide", 1)
    disc.rect(0, 0, 2, 1, "white", 1)                              # blade blur
    disc.rect(2, 3, 2, 1, "white", 1)
    p.under("rotor_fl.disc").fill("white", 2)


# --------------------------------------------------------------------------- rogue_android

def rogue_android(p) -> None:
    """Light mass: white chassis and the two shield plates. Dark mass: oxide pelvis, pistons, pack,
    feet and the sunk head's visor slot. Mid: hazard bands on plates and flanks. Accent: the chest
    sensor eye and the visor bar."""

    android_torso(p, "body.chassis")
    plate(p, "body.pelvis", "oxide", 2)
    plate(p, "body.pack", "oxide", 1)
    pack = p.face("body.pack", "south")
    pack.rect(1, 1, 6, 2, "oxide", 2)
    stripes(pack, 1, 6, 6, 2)
    p.box("body.housing", "oxide", 0)
    p.face("body.housing", "north").rect(0, 0, 6, 1, "oxide", 1)
    p.box("sensor.eye", "oxide", 0)
    p.face("sensor.eye", "north").glow(0, 0, 2, 2, step=3, under=1)
    p.face("sensor.eye", "north").glow(0, 0, 2, 1, step=4, under=2)
    plate(p, "head.head", "white", 1)
    p.face("head.head", "north").rect(1, 1, 6, 2, "oxide", 0)
    p.top("head.head").rect(2, 2, 4, 4, "white", 0)
    p.box("head.visor", "oxide", 0)
    p.face("head.visor", "north").glow(0, 0, 6, 2, step=3, under=1)
    p.face("head.visor", "north").glow(2, 0, 2, 1, step=4, under=2)
    shield_plate(p, "plate_l.plate")
    plate(p, "arm_l.arm", "oxide", 2)
    for face in p.each_face("arm_l.arm", SIDES):
        seams(face, "oxide", 1, 4, 3)
    plate(p, "arm_l.fist", "white", 1)
    p.under("arm_l.fist").fill("oxide", 2)
    plate(p, "leg_l.thigh", "white", 1)
    p.box("leg_l.piston", "oxide", 3)
    for face in p.each_face("leg_l.piston", SIDES):
        face.rect(0, 1, face.w, 2, "oxide", 1)
    plate(p, "leg_l.foot", "oxide", 1)
    foot = p.top("leg_l.foot")
    foot.rect(1, 0, 4, 2, "hazard", 2)                             # toe cap


# --------------------------------------------------------------------------- terraforming_drone

def terraforming_drone(p) -> None:
    """Light mass: the white chassis. Mid mass: the hazard-yellow seed hopper. Dark mass: oxide legs,
    arms, neck and the seed in the bin. Accent (teal, it is owned): the front status bar and the
    hopper's level window."""

    plate(p, "body.chassis", "white", 2)
    for side in p.flanks("body.chassis"):
        stripes(side, 1, 2, side.w - 2, 1, ("hazard", 2), ("white", 1))
    top = p.top("body.chassis")
    top.rect(1, 1, 12, 8, "white", 1)
    top.rect(3, 2, 8, 6, "oxide", 1)                               # hopper seat
    p.under("body.chassis").fill("oxide", 2)
    p.box("body.bar", "oxide", 0)
    p.face("body.bar", "north").glow(0, 0, 6, 2, step=3, under=1)
    p.face("body.bar", "north").glow(2, 0, 2, 1, step=4, under=2)
    p.box("body.vent", "oxide", 1)
    p.face("body.vent", "south").rect(0, 0, 4, 1, "oxide", 0)
    plate(p, "hopper.neck", "oxide", 1)
    plate(p, "hopper.bin", "hazard", 2)
    for face in p.each_face("hopper.bin", SIDES):
        face.rect(0, 1, 2, 2, "oxide", 1)                          # corner brackets
        face.rect(face.w - 2, 1, 2, 2, "oxide", 1)
    p.face("hopper.bin", "north").glow(3, 1, 2, 2, step=3, under=1)
    rim = p.top("hopper.bin")
    rim.fill("hazard", 2)
    rim.rect(1, 1, 6, 6, "oxide", 0)
    p.box("hopper.seed", "oxide", 3)
    seed = p.top("hopper.seed")
    for x, y in ((0, 1), (3, 0), (2, 3), (4, 4), (0, 5)):
        seed.rect(x, y, 2, 1, "hazard", 3)
    p.box("arm_l.upper", "oxide", 2)
    for side in p.flanks("arm_l.upper"):
        side.rect(2, 0, 2, 2, "white", 1)                          # elbow sleeve
    p.box("arm_l.claw", "white", 1)
    for face in p.each_face("arm_l.claw", SIDES):
        face.row(-1, "oxide", 0)
    p.box("leg_fl.leg", "oxide", 2)
    for face in p.each_face("leg_fl.leg", SIDES):
        face.row(0, "white", 1)
        face.rect(0, 3, 2, 2, "oxide", 1)


# --------------------------------------------------------------------------- pirates

def rebreather(p, head: str, mask: str) -> None:
    """The pirate face: dark hood, a goggle band with two lit lenses, a pale ribbed rebreather snout."""
    plate(p, head, "oxide", 2, hem=False)
    face = p.face(head, "north")
    face.rect(1, 2, 6, 3, "oxide", 0)                              # goggle band
    face.glow(1, 3, 2, 1, step=3, under=1)
    face.glow(5, 3, 2, 1, step=3, under=1)
    face.rect(0, 6, 8, 2, "oxide", 1)
    for side in p.flanks(head):
        side.rect(0, 3, 4, 1, "oxide", 0)                          # goggle strap
        side.rect(5, 2, 2, 3, "oxide", 3)                          # ear cup
    p.under(head).fill("oxide", 1)
    p.box(mask, "white", 3)
    snout = p.face(mask, "north")
    snout.row(1, "white", 1)
    snout.rect(1, 3, 2, 1, "oxide", 1)                             # outlet
    for side in p.flanks(mask):
        side.rect(0, 1, 2, 2, "white", 1)


def space_pirate(p) -> None:
    """Light mass: the worn white pressure suit. Dark mass: oxide hood, harness, gloves, boots and
    pack. Mid: the hazard pauldron and the tall canister. Accent: the two goggle lenses."""

    rebreather(p, "head.head", "head.mask")
    p.box("body.torso", "white", 2)
    front = p.face("body.torso", "north")
    front.rect(1, 0, 2, 9, "oxide", 2)                             # harness straps
    front.rect(5, 0, 2, 9, "oxide", 2)
    front.rect(0, 9, 8, 2, "oxide", 1)                             # belt
    front.rect(3, 9, 2, 2, "hazard", 2)
    front.row(-1, "white", 1)
    back = p.face("body.torso", "south")
    back.rect(0, 9, 8, 2, "oxide", 1)
    for side in p.flanks("body.torso"):
        side.rect(0, 9, 4, 2, "oxide", 1)
        side.rect(1, 2, 2, 5, "white", 1)
    plate(p, "body.pack", "oxide", 2)
    pack = p.face("body.pack", "south")
    pack.rect(1, 2, 4, 2, "oxide", 1)
    stripes(pack, 1, 6, 4, 2)
    p.box("body.canister", "hazard", 2)
    for face in p.each_face("body.canister", SIDES):
        face.rect(0, 0, 2, 2, "oxide", 1)                          # valve cap
        face.rect(0, 6, 2, 1, "oxide", 1)
        face.rect(0, 11, 2, 1, "oxide", 1)
    p.top("body.canister").fill("oxide", 2)
    p.box("body.pouch", "oxide", 3)
    p.face("body.pouch", "north").rect(0, 0, 6, 1, "oxide", 2)
    p.box("arm_l.arm", "white", 1)
    for face in p.each_face("arm_l.arm", SIDES):
        face.rect(0, 5, 4, 1, "white", 0)                          # elbow fold
        face.rect(0, 9, 4, 3, "oxide", 2)                          # glove
        face.rect(0, 9, 4, 1, "oxide", 3)
    p.under("arm_l.arm").fill("oxide", 2)
    plate(p, "pauldron.pauldron", "hazard", 2)
    pauldron = p.top("pauldron.pauldron")
    pauldron.rect(1, 1, 3, 4, "hazard", 3)
    for side in p.flanks("pauldron.pauldron"):
        side.rect(2, 1, 2, 2, "oxide", 1)                          # rivet plate
    p.box("leg_l.leg", "white", 1)
    for face in p.each_face("leg_l.leg", SIDES):
        face.rect(0, 4, 4, 2, "white", 0)                          # knee pad
        face.rect(0, 8, 4, 4, "oxide", 1)                          # boot
        face.rect(0, 8, 4, 1, "oxide", 3)
    p.face("leg_l.leg", "north").rect(1, 4, 2, 2, "hazard", 2)
    p.under("leg_l.leg").fill("oxide", 1)


def pirate_captain(p) -> None:
    """Light mass: the long white coat and the two nacelles. Dark mass: the oxide hat, trousers,
    boots and coat lining. Mid: hazard braid, hat band and nacelle rings. Accent: the nacelle nozzles
    and intake rings, and the goggle lenses."""

    rebreather(p, "head.head", "head.mask")
    p.box("head.brim", "oxide", 1)
    brim = p.top("head.brim")
    brim.rect(1, 1, 10, 10, "oxide", 2)
    p.under("head.brim").fill("oxide", 0, raw=True)
    plate(p, "head.crown", "oxide", 2, hem=False)
    for face in p.each_face("head.crown", SIDES):
        face.row(-1, "hazard", 2)                                  # hat band
    p.top("head.crown").rect(2, 2, 4, 4, "oxide", 1)

    p.box("body.torso", "white", 2)
    front = p.face("body.torso", "north")
    front.rect(3, 0, 2, 13, "oxide", 2)                            # open coat front: dark shirt
    front.rect(2, 0, 1, 13, "hazard", 2)                           # braid down both lapels
    front.rect(5, 0, 1, 13, "hazard", 2)
    front.rect(0, 10, 8, 1, "oxide", 1)                            # belt
    front.rect(3, 10, 2, 1, "hazard", 3)
    back = p.face("body.torso", "south")
    back.rect(3, 2, 2, 11, "white", 1)                             # back seam down to the split
    back.rect(0, 10, 8, 1, "oxide", 1)
    for side in p.flanks("body.torso"):
        side.rect(0, 10, 4, 1, "oxide", 1)
    p.box("body.frame", "oxide", 1)
    p.box("arm_l.arm", "white", 2)
    for face in p.each_face("arm_l.arm", SIDES):
        face.rect(0, 0, 4, 2, "hazard", 2)                         # epaulette
        face.rect(0, 8, 4, 1, "hazard", 2)                         # cuff braid
        face.rect(0, 9, 4, 1, "white", 1)
        face.rect(0, 10, 4, 3, "oxide", 2)                         # glove
    p.top("arm_l.arm").fill("hazard", 2)
    p.under("arm_l.arm").fill("oxide", 2)

    plate(p, "nacelle_l.pod", "white", 3)
    for face in p.each_face("nacelle_l.pod", SIDES):
        face.rect(0, 2, 4, 2, "hazard", 2)                         # ring
        face.rect(1, 6, 2, 5, "white", 2)                          # panel
        face.rect(0, 12, 4, 1, "oxide", 1)
        face.rect(0, 13, 4, 2, "oxide", 2)                         # scorched skirt
    intake = p.top("nacelle_l.pod")
    intake.fill("oxide", 0, raw=True)
    intake.glow(1, 1, 2, 2, step=3, under=1)
    p.under("nacelle_l.pod").fill("oxide", 0, raw=True)
    p.box("nacelle_l.nozzle", "oxide", 0)
    for name in SIDES + ("down",):
        p.face("nacelle_l.nozzle", name).glow(0, 0, 2, 2, step=3, under=1)
    p.face("nacelle_l.nozzle", "down").glow(0, 0, 2, 1, step=4, under=2)

    p.box("coat_l.back", "white", 2)
    outer = p.face("coat_l.back", "south")
    outer.rect(0, 0, 6, 1, "white", 3)
    outer.rect(0, 9, 6, 1, "hazard", 2)                            # hem braid
    outer.rect(0, 10, 6, 1, "white", 1)
    outer.rect(4, 0, 1, 9, "white", 1)                             # fold
    lining = p.face("coat_l.back", "north")
    lining.fill("oxide", 2)
    lining.rect(0, 9, 6, 2, "oxide", 1)
    p.box("leg_l.leg", "oxide", 2)
    for face in p.each_face("leg_l.leg", SIDES):
        face.rect(0, 9, 4, 5, "oxide", 0)                          # tall boot
        face.rect(0, 9, 4, 1, "oxide", 3)                          # boot cuff
    p.under("leg_l.leg").fill("oxide", 0)


PAINTERS = {
    "cache_mimic": cache_mimic,
    "static_swarm": static_swarm,
    "scrap_hulk": scrap_hulk,
    "rogue_drone": rogue_drone,
    "rogue_android": rogue_android,
    "terraforming_drone": terraforming_drone,
    "space_pirate": space_pirate,
    "pirate_captain": pirate_captain,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
