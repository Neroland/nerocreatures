#!/usr/bin/env python3
"""Cindara painters: one function per entity.

    python tools/art/paint/cindara.py            # paint and write
    python tools/art/paint/cindara.py --check    # prove the committed PNGs match

Cindara reads as black basalt and lava, so bodies are pale ``ash`` and warm ``clinker`` against it,
with ember orange as the only glow. Ember sits only where the heat is made: the beetle's smelting
gut, the wader's slag sack and spout, the tyrant's furnace, shoulder cores and crown vents.
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
        if lip:
            face.row(0, ramp, step + 1)
        if hem and face.h > 2:
            face.row(-1, ramp, step - 1)


def bands(p, key: str, ramp: str, step: int, rows, thick: int = 1) -> None:
    """Horizontal rings around a limb (all four side faces), rows counted from the top."""
    for face in p.each_face(key, SIDES):
        for y in rows:
            face.rect(0, y % face.h, face.w, thick, ramp, step)


def slag_beetle(p) -> None:
    """Light mass: the ash dome, ribbed and hemmed in soot. Mid mass: clinker head, legs and gut.
    Accent: the smelting gut behind the dome, one cube per digestion stage, each larger and hotter
    than the last (dull slit, two bars, a cracked grate, a white-hot core), and the eyes."""

    # Carapace: four stacked tiers. Each tier has a lit rim and a dark seam where the next one sits.
    for key in ("body.skirt", "body.mid", "body.top", "body.cap"):
        plate(p, key, "ash", 2, hem=False)
        top = p.top(key)
        top.rect(top.w // 2 - 1, 0, 2, top.h, "ash", 1)          # the wing-case seam
        p.under(key).fill("clinker", 1)
    for side in p.flanks("body.skirt"):
        side.rect(0, 4, side.w, 2, "clinker", 2)                   # soot hem over the legs
        side.row(3, "ash", 1)
        for x in (2, 6):
            side.rect(x, 1, 2, 2, "ash", 1)                        # ribs
    front = p.face("body.skirt", "north")
    front.rect(0, 4, front.w, 2, "clinker", 2)
    front.row(3, "ash", 1)
    back = p.face("body.skirt", "south")
    back.rect(0, 4, back.w, 2, "clinker", 2)
    back.rect(2, 1, 6, 3, "clinker", 1)                            # the socket the gut hangs from
    for side in p.flanks("body.mid"):
        for x in (1, 5):
            side.rect(x, 1, 2, 2, "ash", 1)
    p.top("body.cap").rect(1, 1, 2, 2, "ash", 4)                   # worn crown of the dome

    # Head and mandibles.
    plate(p, "head.head", "clinker", 2)
    face = p.face("head.head", "north")
    face.rect(0, 1, 2, 1, "clinker", 0)
    face.rect(4, 1, 2, 1, "clinker", 0)
    face.glow(0, 1, 2, 1, step=3, under=2)
    face.glow(4, 1, 2, 1, step=3, under=2)
    face.rect(2, 2, 2, 2, "clinker", 1)
    p.box("mandible_l.jaw", "clinker", 1)
    for strip in p.around("mandible_l.jaw"):
        strip.rect(0, 0, 1, strip.h, "ash", 4)                     # crushing edge
    p.face("mandible_l.jaw", "north").fill("ash", 4)

    # Gut: collar, then the four stages.
    plate(p, "abdomen.collar", "clinker", 1)
    plate(p, "gut_0.sack", "clinker", 1)
    p.face("gut_0.sack", "south").glow(1, 2, 2, 1, step=2, under=0)

    plate(p, "gut_1.sack", "clinker", 2)
    rear = p.face("gut_1.sack", "south")
    rear.glow(1, 1, 4, 1, step=2, under=1)
    rear.glow(1, 3, 4, 1, step=2, under=1)
    for side in p.flanks("gut_1.sack"):
        side.glow(1, 2, 2, 1, step=2, under=1)

    plate(p, "gut_2.sack", "clinker", 3)
    rear = p.face("gut_2.sack", "south")
    rear.glow(1, 1, 5, 1, step=3, under=2)
    rear.glow(1, 4, 5, 1, step=3, under=2)
    rear.glow(3, 2, 1, 2, step=3, under=2)
    rear.glow(1, 2, 1, 2, step=3, under=2)
    rear.glow(5, 2, 1, 2, step=3, under=2)
    for side in p.flanks("gut_2.sack"):
        side.glow(1, 1, 3, 1, step=3, under=2)
        side.glow(1, 4, 3, 1, step=3, under=2)

    plate(p, "gut_3.sack", "clinker", 3, hem=False)
    rear = p.face("gut_3.sack", "south")
    rear.glow(1, 1, 6, 5, step=3, under=3)
    rear.glow(2, 2, 4, 3, step=4, under=4)                         # white-hot core
    rear.rect(3, 1, 2, 1, "clinker", 2)
    rear.rect(3, 5, 2, 1, "clinker", 2)
    for side in p.flanks("gut_3.sack"):
        side.glow(1, 1, 4, 1, step=3, under=3)
        side.glow(1, 3, 4, 1, step=4, under=4)
        side.glow(1, 5, 4, 1, step=3, under=3)
    p.top("gut_3.sack").glow(3, 1, 2, 3, step=3, under=3)

    # Legs: clinker stubs with a pale foot. legs_b mirrors these.
    for key in ("legs_a.front", "legs_a.mid", "legs_a.rear"):
        p.box(key, "clinker", 2)
        for face in p.each_face(key, SIDES):
            face.row(0, "clinker", 1)
            face.row(-1, "ash", 3)
        p.under(key).fill("ash", 3)


def magma_wader(p) -> None:
    """Light mass: four ash stilt legs, heat-stained toward the feet. Mid mass: clinker body, neck
    and sack hide. Dark mass: the hip yokes, knees and feet. Accent: the slag sack under the throat
    (molten bands showing through the hide, a hot underside and drip), the spout mouth, the eyes."""

    # Body: a small plated table top between two dark hip yokes.
    plate(p, "body.torso", "clinker", 2)
    top = p.top("body.torso")
    top.rect(0, 0, top.w, 2, "clinker", 1)
    top.rect(0, top.h - 2, top.w, 2, "clinker", 1)
    for side in p.flanks("body.torso"):
        side.rect(5, 1, 4, 2, "clinker", 3)
    p.under("body.torso").fill("clinker", 2)
    for key in ("body.yoke_f", "body.yoke_r"):
        plate(p, key, "clinker", 1)
        for face in p.each_face(key, ("north", "south")):
            face.rect(0, 0, 2, face.h, "ash", 1)                   # leg sockets at each end
            face.rect(face.w - 2, 0, 2, face.h, "ash", 1)
        for face in p.each_face(key, ("east", "west")):
            face.fill("ash", 1)
    plate(p, "body.ridge", "clinker", 3)
    ridge = p.top("body.ridge")
    for y in (0, 4, 8):
        ridge.rect(0, y, ridge.w, 2, "ash", 3)                     # three ash scutes
    for key in ("body.stack_f", "body.stack_r"):
        p.box(key, "ash", 2)
        for face in p.each_face(key, SIDES):
            face.row(0, "ash", 4)
        p.top(key).fill("clinker", 0)

    # Neck, skull and spout. The spout is the nozzle the slag leaves by.
    plate(p, "head.neck", "clinker", 2)
    for strip in p.around("head.neck"):
        strip.rect(2, 0, 2, strip.h, "clinker", 1)
    plate(p, "head.skull", "clinker", 3)
    crest = p.top("head.skull")
    crest.rect(2, 0, 2, crest.h, "ash", 3)
    for side in p.flanks("head.skull"):
        side.rect(1, 1, 2, 2, "clinker", 0)                        # eye socket
        side.glow(1, 1, 2, 1, step=3, under=2)
    p.under("head.skull").fill("clinker", 1)
    plate(p, "head.spout", "ash", 2)
    for strip in p.around("head.spout"):
        strip.rect(0, 0, 1, strip.h, "ash", 0)                     # scorched lip
    mouth = p.face("head.spout", "north")
    mouth.fill("ash", 0)
    mouth.glow(1, 1, 2, 1, step=4, under=2)
    p.box("jaw.jaw", "ash", 1)
    lip = p.top("jaw.jaw")
    lip.rect(1, 0, 2, 3, "clinker", 0)
    lip.glow(1, 0, 2, 2, step=3, under=1)                          # seen only when the jaw drops
    p.face("jaw.jaw", "north").fill("ash", 3)

    # Slag sack: gullet, bulb and drip. Ember bands are the melt showing through the hide.
    plate(p, "sack.gullet", "clinker", 2)
    for face in p.each_face("sack.gullet", SIDES):
        face.rect(0, 2, face.w, 1, "clinker", 1)
        face.rect(0, 4, face.w, 1, "clinker", 1)
        face.glow(2, 5, 2, 1, step=2, under=1)
    p.box("sack_bulb.bulb", "clinker", 3)
    for face in p.each_face("sack_bulb.bulb", SIDES):
        face.row(0, "clinker", 2)
        face.rect(0, 1, face.w, 1, "clinker", 4)                   # stretched-hide highlight
        face.rect(4, 2, 1, 6, "clinker", 2)                        # a seam down the middle
        face.glow(1, 3, 3, 1, step=2, under=1)
        face.glow(5, 3, 3, 1, step=2, under=1)
        face.glow(1, 5, 3, 1, step=3, under=2)
        face.glow(5, 5, 3, 1, step=3, under=2)
        face.glow(1, 7, 7, 2, step=3, under=2)
        face.glow(2, 8, 5, 1, step=4, under=3)
    under = p.under("sack_bulb.bulb")
    under.fill("clinker", 3)
    under.glow(1, 1, 7, 7, step=3, under=2)
    under.glow(2, 2, 5, 5, step=4, under=3)
    p.top("sack_bulb.bulb").rect(2, 2, 5, 5, "clinker", 2)
    p.box("sack_bulb.drip", "clinker", 2)
    for face in p.each_face("sack_bulb.drip", SIDES):
        face.glow(1, 0, 2, 2, step=4, under=3)
    p.under("sack_bulb.drip").glow(1, 1, 2, 2, step=4, under=3)

    # Legs: pale stilts, a dark knee, soot rising from the feet. The right legs mirror these.
    for leg, shin in (("leg_fl", "shin_fl"), ("leg_rl", "shin_rl")):
        p.box(leg + ".upper", "ash", 2)
        for face in p.each_face(leg + ".upper", SIDES):
            face.rect(0, 0, face.w, 3, "clinker", 1)               # hip boot under the yoke
            face.rect(0, 3, face.w, 1, "ash", 4)
            face.rect(0, 9, face.w, 2, "ash", 3)
            face.rect(0, face.h - 2, face.w, 2, "ash", 1)
        plate(p, leg + ".knee", "clinker", 1)
        p.top(leg + ".knee").fill("clinker", 2)
        p.box(shin + ".shin", "ash", 2)
        for face in p.each_face(shin + ".shin", SIDES):
            face.rect(0, 0, face.w, 2, "ash", 1)
            face.rect(0, 6, face.w, 2, "ash", 3)
            face.rect(0, 11, face.w, 3, "ash", 1)                  # heat stain climbing the shin
            face.rect(0, 14, face.w, 5, "ash", 0)
        p.box(shin + ".foot", "clinker", 1)
        p.top(shin + ".foot").fill("clinker", 2)
        p.face(shin + ".foot", "north").row(-1, "clinker", 3)


def cinder_tyrant(p) -> None:
    """Dark mass: clinker head, waist and legs. Mid mass: the clinker chest slab and arms. Light
    mass: ash slag shoulders, fists and chest doors. Accent: the furnace behind the doors, the two
    shoulder cores that show when the slag splits (phase 2), the crown vent spikes, eyes and maw."""

    # Chest slab: plated, a seam down the breastbone, cracked toward the waist.
    plate(p, "body.chest", "clinker", 2)
    front = p.face("body.chest", "north")
    front.rect(9, 0, 2, front.h, "clinker", 1)
    front.rect(0, 5, 2, 9, "clinker", 1)
    front.rect(front.w - 2, 5, 2, 9, "clinker", 1)
    front.rect(3, 2, 5, 2, "clinker", 3)
    front.rect(12, 2, 5, 2, "clinker", 3)
    back = p.face("body.chest", "south")
    back.rect(3, 0, 14, back.h, "clinker", 1)                      # shadow behind the bellows stack
    for side in p.flanks("body.chest"):
        side.rect(2, 3, 2, 8, "clinker", 1)
        side.rect(7, 3, 2, 8, "clinker", 1)
        side.rect(4, 12, 6, 2, "clinker", 0)
    top = p.top("body.chest")
    top.rect(6, 0, 8, top.h, "clinker", 1)
    plate(p, "body.abdomen", "clinker", 1)
    for face in p.each_face("body.abdomen", SIDES):
        face.rect(0, 3, face.w, 2, "clinker", 0)                   # waist folds
    plate(p, "hips.pelvis", "clinker", 1)
    for face in p.each_face("hips.pelvis", SIDES):
        face.rect(0, 1, face.w, 2, "ash", 1)                       # slag belt
    p.face("hips.pelvis", "north").rect(5, 1, 4, 4, "ash", 2)

    # Bellows stack and its two vents on the back.
    p.box("body.stack", "ash", 1)
    for face in p.each_face("body.stack", ("south", "east", "west")):
        for y in range(0, face.h, 4):
            face.rect(0, y, face.w, 2, "ash", 2)                   # bellows ribs
            face.rect(0, y + 2, face.w, 2, "ash", 0)
    p.top("body.stack").fill("ash", 1)
    for key in ("body.vent_l", "body.vent_r"):
        plate(p, key, "ash", 2)
        p.top(key).fill("clinker", 0)
        p.top(key).rect(0, 0, 3, 1, "ash", 3)

    # Furnace: the whole grate is ember, hottest in the middle. Hidden behind the doors bar a seam.
    p.box("body.furnace", "clinker", 0)
    grate = p.face("body.furnace", "north")
    grate.glow(0, 0, 12, 7, step=2, under=1)
    grate.glow(1, 1, 10, 5, step=3, under=2)
    grate.glow(4, 2, 4, 3, step=4, under=3)
    for x in (2, 8):
        grate.rect(x, 0, 2, 7, "clinker", 0)                       # grate bars
    for key in ("body.core_l", "body.core_r"):
        p.box(key, "clinker", 0)
        for name in ("up", "north", "south", "east", "west"):
            face = p.face(key, name)
            face.glow(0, 0, face.w, face.h, step=2, under=1)
            if face.w >= 8:
                face.glow(1, 1, 2, face.h - 2, step=4, under=3)
                face.glow(5, 1, 2, face.h - 2, step=4, under=3)
            else:
                face.glow(0, 1, face.w, 2, step=4, under=3)
                face.glow(0, face.h - 3, face.w, 2, step=3, under=2)

    # Head, sunk and dark, under a pale brow. Maw glows only when the jaw drops.
    plate(p, "head.skull", "clinker", 1)
    face = p.face("head.skull", "north")
    face.rect(0, 3, 8, 2, "clinker", 0)
    face.glow(1, 3, 2, 1, step=4, under=3)
    face.glow(5, 3, 2, 1, step=4, under=3)
    face.rect(3, 5, 2, 3, "clinker", 0)
    plate(p, "head.brow", "ash", 2)
    p.top("head.brow").rect(4, 0, 2, 3, "ash", 1)
    plate(p, "jaw.jaw", "clinker", 2)
    maw = p.top("jaw.jaw")
    maw.fill("clinker", 0)
    maw.glow(2, 1, 4, 3, step=3, under=1)
    p.face("jaw.jaw", "north").rect(2, 0, 4, 1, "ash", 3)
    for key in ("jaw.tusk_l", "jaw.tusk_r"):
        p.box(key, "ash", 3)
        for side in p.each_face(key, SIDES):
            side.row(0, "ash", 4)

    # Shoulders: the light mass. Poured slag with dark cracks and a rough crust on top.
    plate(p, "shoulder_l.slag", "ash", 2)
    for side in p.each_face("shoulder_l.slag", ("north", "south")):
        side.rect(3, 1, 2, 4, "clinker", 1)
        side.rect(6, 4, 2, 4, "clinker", 1)
        side.rect(0, 8, side.w, 3, "ash", 1)
    outer = p.face("shoulder_l.slag", "east")
    outer.rect(2, 1, 2, 4, "clinker", 1)
    outer.rect(6, 4, 2, 4, "clinker", 1)
    outer.rect(10, 1, 2, 5, "clinker", 1)
    outer.rect(0, 8, outer.w, 3, "ash", 1)
    inner = p.face("shoulder_l.slag", "west")
    inner.fill("clinker", 1)
    inner.rect(3, 2, 8, 6, "clinker", 0)                           # scorched where it sat on the core
    top = p.top("shoulder_l.slag")
    top.rect(1, 2, 3, 2, "ash", 3)
    top.rect(6, 9, 3, 2, "ash", 3)
    top.rect(0, 5, 10, 2, "clinker", 2)
    p.under("shoulder_l.slag").fill("clinker", 0)
    plate(p, "shoulder_l.lump", "ash", 3)
    p.top("shoulder_l.lump").rect(1, 2, 4, 2, "ash", 4)
    plate(p, "shoulder_l.spur", "ash", 1)
    p.face("shoulder_l.spur", "east").rect(1, 1, 4, 2, "ash", 3)

    # Crown: a collar and four vent spikes, ember at the mouths. Right spikes mirror the left.
    plate(p, "crown.collar", "ash", 1)
    for key in ("spike_li.spike", "spike_lo.spike"):
        p.box(key, "clinker", 1)
        for face in p.each_face(key, SIDES):
            face.rect(0, 4, face.w, 2, "ash", 3)                   # a casting ring
            face.rect(0, face.h - 2, face.w, 2, "clinker", 0)
            face.glow(0, 0, face.w, 1, step=4, under=3)
            face.glow(0, 1, face.w, 2, step=3, under=2)
        p.top(key).glow(0, 0, 3, 3, step=4, under=3)

    # Arms: clinker with ash bracers, heavy slag fists. Right arm mirrors the left.
    plate(p, "arm_l.upper", "clinker", 2)
    bands(p, "arm_l.upper", "clinker", 1, (9,), 2)
    plate(p, "forearm_l.forearm", "clinker", 2)
    bands(p, "forearm_l.forearm", "ash", 2, (2,), 3)
    bands(p, "forearm_l.forearm", "ash", 1, (5,), 1)
    plate(p, "forearm_l.fist", "ash", 2)
    for face in p.each_face("forearm_l.fist", SIDES):
        face.rect(2, 2, 2, 5, "clinker", 1)                        # knuckle gaps
        face.rect(5, 2, 2, 5, "clinker", 1)
        face.row(-1, "ash", 0)
    p.under("forearm_l.fist").fill("ash", 1)
    p.box("forearm_l.elbow", "ash", 3)
    p.face("forearm_l.elbow", "south").row(0, "ash", 4)

    # Chest doors: ash plates with a clinker hinge strap; soot-black inside.
    plate(p, "door_l.door", "ash", 2)
    door = p.face("door_l.door", "north")
    door.rect(5, 0, 2, 7, "clinker", 1)                            # hinge strap on the outer edge
    door.rect(1, 2, 3, 1, "ash", 0)
    door.rect(1, 4, 3, 1, "ash", 0)                                # louvre slots
    p.face("door_l.door", "south").fill("clinker", 0)

    # Legs: short pillars with slag-shod feet.
    plate(p, "leg_l.thigh", "clinker", 2)
    bands(p, "leg_l.thigh", "clinker", 1, (4,), 2)
    plate(p, "leg_l.shin", "clinker", 1)
    bands(p, "leg_l.shin", "ash", 1, (2,), 2)
    plate(p, "leg_l.foot", "ash", 1)
    p.top("leg_l.foot").fill("ash", 1)
    toes = p.face("leg_l.foot", "north")
    toes.rect(0, 0, 2, 3, "ash", 3)
    toes.rect(3, 0, 2, 3, "ash", 3)
    toes.rect(6, 0, 2, 3, "ash", 3)


PAINTERS = {
    "slag_beetle": slag_beetle,
    "magma_wader": magma_wader,
    "cinder_tyrant": cinder_tyrant,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
