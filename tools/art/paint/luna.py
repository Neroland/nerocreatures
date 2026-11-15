#!/usr/bin/env python3
"""Luna painters: one function per entity.

    python tools/art/paint/luna.py            # paint and write
    python tools/art/paint/luna.py --check    # prove the committed PNGs match

Luna reads as pale grey ground, so bodies are dark: charcoal ``slate`` and dusk ``umber``, with
moon-gold as the only glow.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pixelkit import run_painters  # noqa: E402


def lunar_stalker(p) -> None:
    """Dark mass: slate hide with back bars. Mid mass: umber hump, muzzle, socks, belly, tail club.
    Accent: eyes and the three resonator nodes on the hump crest that light when it calls the pack."""

    def hide(key: str, belly_rows: int = 0, bars=(), bar_depth: int = 0) -> None:
        p.box(key, "slate", 2)
        for side in p.flanks(key):
            side.row(0, "slate", 3)
            for x in bars:
                side.rect(x, 0, 2, bar_depth, "slate", 1)
            if belly_rows:
                side.row(-belly_rows - 1, "slate", 1)
                side.rect(0, side.h - belly_rows, side.w, belly_rows, "umber", 1)

    # Torso: bars run down from the spine and stop short of the belly.
    hide("body.chest", belly_rows=2, bars=(5,), bar_depth=4)
    hide("body.loin", belly_rows=1, bars=(1, 5), bar_depth=3)
    for key in ("body.chest", "body.loin"):
        p.under(key).fill("umber", 2)
        top = p.top(key)
        top.rect(top.w // 2 - 1, 0, 2, top.h, "slate", 1)
    front = p.face("body.chest", "north")
    front.rect(2, 4, 4, 4, "umber", 1)
    front.rect(3, 5, 2, 3, "umber", 2)
    back = p.face("body.loin", "south")
    back.row(-1, "umber", 1)

    # Hump: the mid-value mass that makes the outline.
    p.box("body.hump", "umber", 3)
    for side in p.flanks("body.hump") + [p.face("body.hump", "north"), p.face("body.hump", "south")]:
        side.row(0, "umber", 4)
        side.row(-1, "umber", 2)
    p.top("body.hump").rect(2, 0, 2, 6, "slate", 1)

    # Crest: three resonator nodes, the only glow besides the eyes.
    p.box("body.crest", "slate", 1)
    for side in p.flanks("body.crest"):
        for x in (0, 2, 4):
            side.glow(x, 0, 1, 2)
    crest_top = p.top("body.crest")
    for y in (0, 2, 4):
        crest_top.glow(0, y, 2, 1, step=4, under=2)

    # Neck and head: narrow, forward, with a pale throat.
    hide("neck.neck", belly_rows=1)
    p.under("neck.neck").fill("umber", 2)
    p.box("head.skull", "slate", 2)
    p.top("head.skull").rect(1, 0, 2, 5, "slate", 3)
    for side in p.flanks("head.skull"):
        side.row(0, "slate", 3)
        side.row(-1, "slate", 1)
        side.rect(0, 1, 4, 1, "slate", 0)
        side.glow(1, 1, 2, 1)
    brow = p.face("head.skull", "north")
    brow.row(0, "slate", 3)
    brow.rect(0, 1, 4, 1, "slate", 0)
    p.box("head.snout", "umber", 3)
    p.top("head.snout").rect(0, 2, 2, 3, "slate", 2)
    for side in p.flanks("head.snout"):
        side.rect(2, 0, 3, 1, "slate", 2)
        side.rect(0, 1, 5, 1, "umber", 2)
    p.face("head.snout", "north").row(0, "slate", 0)
    p.box("head.jaw", "umber", 1)
    for side in p.flanks("head.jaw"):
        side.rect(0, 0, 2, 1, "umber", 4)
    p.face("head.jaw", "north").fill("umber", 4)
    for ear in ("head.ear_l", "head.ear_r"):
        p.box(ear, "slate", 1)
        for side in p.flanks(ear):
            side.row(0, "slate", 3)
            side.rect(1, 1, 3, 1, "umber", 1)

    # Tail: ringed, ending in an umber club.
    for key, rings in (("tail.tail", (2,)), ("tail_tip.tip", (0,))):
        p.box(key, "slate", 2)
        for strip in p.around(key):
            for x in rings:
                strip.rect(x, 0, 2, strip.h, "slate", 1)
    p.box("tail_tip.club", "umber", 3)
    for strip in p.around("tail_tip.club"):
        strip.col(0, "umber", 2)
    p.face("tail_tip.club", "south").fill("umber", 2)

    # Legs: slate thighs, umber socks, pale claws. The right legs mirror these.
    def paw(key: str) -> None:
        p.box(key, "umber", 2)
        p.top(key).fill("umber", 3)
        toes = p.face(key, "north")
        toes.row(-1, "umber", 4)
        for side in p.flanks(key):
            side.rect(0, 1, 2, 1, "umber", 4)

    hide("leg_fl.upper")
    for face in ("north", "south", "east", "west"):
        p.face("leg_fl.upper", face).rect(0, 4, 3, 2, "slate", 1)
    p.box("leg_fl.lower", "umber", 3)
    for face in ("north", "south", "east", "west"):
        p.face("leg_fl.lower", face).row(0, "slate", 1)
    paw("leg_fl.paw")
    hide("leg_rl.haunch", bars=(1,), bar_depth=4)
    p.box("shin_rl.shin", "umber", 3)
    for face in ("north", "south", "east", "west"):
        p.face("shin_rl.shin", face).rect(0, 0, 2, 2, "slate", 1)
    paw("shin_rl.paw")


def lumen_moth(p) -> None:
    """Dark mass: slate wings with a pale leading edge. Mid mass: umber wing band, thorax and ruff.
    Accent: the lantern abdomen, glowing through dark ribs on every side, plus the eyes."""

    # Wings: the left pair owns the texels. Bands run spanwise so the three panels read as one wing.
    for key, band in (("wing_l.inner", 5), ("wingtip_l.outer", 4), ("wingtip_l.point", 2)):
        p.box(key, "slate", 1)
        top = p.top(key)
        top.fill("slate", 1)
        top.rect(0, 0, top.w, 1, "slate", 3)
        top.rect(0, band, top.w, 2, "umber", 2)
        top.rect(0, top.h - 1, top.w, 1, "slate", 0)
        under = p.under(key)
        under.fill("umber", 4)
        under.rect(0, band, under.w, 2, "slate", 3)
        under.rect(0, under.h - 1, under.w, 1, "slate", 3)
    tip = p.top("wingtip_l.point")
    tip.rect(tip.w - 1, 0, 1, tip.h, "slate", 0)

    # Thorax and ruff: the fuzzy mid-value body the wings hang from.
    p.box("body.thorax", "umber", 2)
    p.top("body.thorax").rect(1, 0, 1, 4, "umber", 1)
    p.under("body.thorax").fill("umber", 3)
    p.box("body.ruff", "umber", 3)
    for face in p.each_face("body.ruff", ("north", "south", "east", "west")):
        face.row(0, "umber", 4)
        face.row(-1, "umber", 2)
    p.box("body.head", "slate", 1)
    p.top("body.head").fill("slate", 2)
    for side in p.flanks("body.head"):
        side.glow(0, 0, 1, 2)

    # Antennae: a dark shaft with pale barbs.
    for key in ("antennae.ant_l", "antennae.ant_r"):
        p.box(key, "umber", 3)
        for face in (p.face(key, "north"), p.face(key, "south")):
            face.row(0, "umber", 4)
            face.row(2, "umber", 1)
            face.row(4, "umber", 1)

    # Lantern: ribs of umber around glowing panels, hottest at the centre.
    p.box("abdomen.lantern", "umber", 1)
    for side in p.flanks("abdomen.lantern"):
        side.glow(1, 1, 4, 3)
        side.glow(2, 2, 2, 1, step=4, under=2)
    for face in (p.top("abdomen.lantern"), p.under("abdomen.lantern")):
        face.glow(1, 1, 3, 4)
        face.glow(2, 2, 1, 2, step=4, under=2)
    rear = p.face("abdomen.lantern", "south")
    rear.glow(1, 1, 3, 1)
    p.box("abdomen.tip", "umber", 2)
    p.face("abdomen.tip", "south").fill("umber", 1)


def echo_shrike(p) -> None:
    """Dark mass: slate body, hood and tail. Mid mass: the blank umber face wedge, ear backs, shanks.
    Accent: the membranes of the two dish ears, its only sense. It has no eyes anywhere."""

    p.box("body.torso", "slate", 2)
    for side in p.flanks("body.torso"):
        side.row(0, "slate", 3)
        side.rect(2, 2, 2, 3, "slate", 1)
        side.rect(5, 1, 2, 3, "slate", 1)
        side.rect(0, side.h - 2, side.w, 2, "umber", 1)
    p.under("body.torso").fill("umber", 3)
    chest = p.face("body.torso", "north")
    chest.rect(1, 3, 3, 3, "umber", 1)
    p.box("body.hunch", "slate", 1)
    for side in p.flanks("body.hunch"):
        side.row(0, "slate", 2)
    p.top("body.hunch").rect(1, 0, 2, 5, "slate", 0)
    p.box("body.rump", "slate", 2)
    p.under("body.rump").fill("umber", 3)

    # Head: a smooth pale wedge where a face should be.
    p.box("head.skull", "slate", 2)
    for side in p.flanks("head.skull"):
        side.row(0, "slate", 3)
        side.rect(0, 1, 2, 3, "umber", 2)
    p.top("head.skull").rect(0, 0, 4, 1, "umber", 2)
    p.box("head.wedge", "umber", 3)
    p.top("head.wedge").rect(1, 0, 1, 3, "umber", 4)
    for side in p.flanks("head.wedge"):
        side.row(-1, "umber", 2)
    p.under("head.wedge").fill("umber", 3)
    p.box("head.beak", "slate", 1)
    for side in p.flanks("head.beak"):
        side.rect(0, 0, 2, 1, "slate", 0)
    p.top("head.beak").fill("slate", 2)

    # Ears: dark rim, lit membrane in front; ribbed umber behind.
    for key in ("ear_l.dish_h", "ear_l.dish_v"):
        p.box(key, "slate", 1)
        back = p.face(key, "south")
        back.fill("umber", 3)
        back.row(0, "umber", 4)
        back.row(-1, "umber", 2)
        p.face(key, "north").fill("umber", 2)
    front = p.face("ear_l.dish_h", "north")
    front.glow(1, 0, 4, 4)
    front.glow(2, 1, 2, 2, step=4, under=2)
    tall = p.face("ear_l.dish_v", "north")
    tall.glow(1, 0, 2, 1)
    tall.glow(1, 5, 2, 1)

    # Tail blades: slate with a pale tip and one ring.
    p.box("tail_l.blade", "slate", 1)
    for strip in p.around("tail_l.blade"):
        strip.rect(4, 0, 2, strip.h, "slate", 2)
        strip.rect(9, 0, 3, strip.h, "umber", 3)
    p.face("tail_l.blade", "south").fill("umber", 3)

    # Legs: the right leg mirrors these.
    p.box("leg_l.thigh", "slate", 2)
    for face in p.each_face("leg_l.thigh", ("north", "south", "east", "west")):
        face.row(-1, "slate", 1)
    p.box("leg_l.shank", "umber", 3)
    p.box("leg_l.foot", "umber", 2)
    p.top("leg_l.foot").fill("umber", 3)
    p.face("leg_l.foot", "north").fill("umber", 4)


def lode_tortoise(p) -> None:
    """Mid mass: the umber shell, lighter toward the crown, with dark scute seams. Dark mass: slate
    skin on head, legs and tail, and the slate nodules. Accent: ore glints in the nodules (the spec's
    variants colour them by ore family), and eyes."""

    def scutes(key: str, step: int, seams) -> None:
        p.box(key, "umber", step)
        sides = p.flanks(key) + [p.face(key, "north"), p.face(key, "south")]
        for side in sides:
            side.row(0, "umber", step + 1)
            side.row(-1, "umber", step - 1)
        for side in p.flanks(key):
            for x in seams:
                side.rect(x, 0, 1, side.h, "umber", step - 2)

    scutes("shell.skirt", 2, (3, 7, 8, 12))
    scutes("shell.dome", 3, (2, 6, 7, 11))
    scutes("shell.crown", 3, (4, 5))
    for face in (p.face("shell.skirt", "north"), p.face("shell.skirt", "south")):
        face.rect(4, 0, 1, face.h, "umber", 0)
        face.rect(9, 0, 1, face.h, "umber", 0)
    for key, edge in (("shell.skirt", 1), ("shell.dome", 2), ("shell.crown", 2)):
        top = p.top(key)
        top.fill("umber", edge)
        top.rect(1, 1, top.w - 2, top.h - 2, "umber", edge + 1)
        top.rect(top.w // 2 - 1, 0, 2, top.h, "umber", edge - 1)
    crown = p.top("shell.crown")
    crown.rect(0, 4, crown.w, 2, "umber", 1)
    plastron = p.under("shell.skirt")
    plastron.fill("umber", 4)
    plastron.rect(6, 0, 2, plastron.h, "slate", 3)
    plastron.rect(0, 7, plastron.w, 2, "slate", 3)
    for key in ("shell.dome", "shell.crown"):
        p.under(key).fill("umber", 3)

    # Skin: slate, wrinkled in rows.
    p.box("head.neck", "slate", 2)
    for strip in p.around("head.neck"):
        strip.rect(1, 0, 1, strip.h, "slate", 1)
        strip.rect(4, 0, 1, strip.h, "slate", 1)
    p.box("head.skull", "slate", 2)
    p.top("head.skull").rect(1, 1, 2, 3, "slate", 3)
    for side in p.flanks("head.skull"):
        side.row(0, "slate", 3)
        side.glow(1, 1, 1, 2)
        side.rect(0, 3, 2, 1, "umber", 4)
    snout = p.face("head.skull", "north")
    snout.row(0, "slate", 3)
    snout.rect(0, 3, 4, 1, "umber", 4)
    p.box("jaw.jaw", "slate", 1)
    p.face("jaw.jaw", "north").fill("umber", 3)
    p.box("tail.tail", "slate", 2)
    p.face("tail.tail", "south").fill("slate", 1)
    p.box("leg_fl.pillar", "slate", 2)
    for face in p.each_face("leg_fl.pillar", ("north", "south", "east", "west")):
        face.row(0, "slate", 1)
        face.row(2, "slate", 1)
        face.row(4, "slate", 3)
        face.row(-1, "slate", 1)
    p.box("leg_fl.toes", "umber", 4)

    # Nodules: dark stone blocks with ore glints; each growth stage adds parts.
    def nodule(key: str, step: int) -> None:
        p.box(key, "slate", step)
        p.top(key).fill("slate", step + 1)
        for face in p.each_face(key, ("north", "south", "east", "west")):
            face.row(-1, "slate", step - 1)

    for key in ("nodules_1.nod_a", "nodules_1.nod_b", "nodules_1.nod_c", "nodules_2.nod_d",
                "nodules_3.nod_e"):
        nodule(key, 1)
        # Glints are the palest accent step so the renderer's ore-family glow colour reads true.
        for face in p.each_face(key, ("east", "north")):
            face.glow(0, 1, 2, 1, step=4, under=2)
        for face in p.each_face(key, ("west", "south")):
            face.glow(1, 0, 2, 1, step=4, under=2)
    for key in ("nodules_2.cap_a", "nodules_2.cap_b", "nodules_3.cap_c", "nodules_3.cap_d"):
        nodule(key, 2)
        p.top(key).glow(0, 0, 2, 2, step=4, under=2)


PAINTERS = {
    "lunar_stalker": lunar_stalker,
    "lumen_moth": lumen_moth,
    "echo_shrike": echo_shrike,
    "lode_tortoise": lode_tortoise,
}

if __name__ == "__main__":
    sys.exit(run_painters(PAINTERS, sys.argv[1:]))
