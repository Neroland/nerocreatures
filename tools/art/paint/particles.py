#!/usr/bin/env python3
"""Particle painter: every custom particle sprite, its definition JSON and the wiring manifest.

    python tools/art/paint/particles.py                  # write textures, JSON and the manifest
    python tools/art/paint/particles.py --check          # change nothing; exit 1 if a file is stale
    python tools/art/paint/particles.py --sheet out.png  # also write a review sheet

Standard library only. Writes, under ``assets/nerocreatures/``:

* ``textures/particle/<id>_<n>.png``  one 8x8 sprite per animation frame
* ``particles/<id>.json``             the frame list the particle engine loads for ``<id>``

and ``art/particles/MANIFEST.md``, the table the registration code is written from (id, frames,
which vanilla stand-in each sprite replaces, suggested lifetime, size, gravity and motion).

A sprite is an 8x8 character grid plus a legend that maps each character to a palette ramp step
(``[habitat.]ramp<step>``), so a particle cannot hold an off-palette colour. Every texel is fully
opaque or fully clear, so the opaque particle sheet is enough; a provider may still fade alpha over
the particle's life. Frames are meant to play once over the lifetime (age-driven sprite pick) unless
the manifest says they loop.

This file has no entity painters (``PAINTERS`` is empty), so ``tools/art/run.py`` imports it
harmlessly. Nothing here imports ``random``.
"""

from __future__ import annotations

import json
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from _sprite import ASSETS, colour, palette, write_images, write_text  # noqa: E402
from pixelkit import REPO_ROOT, Image  # noqa: E402

PAINTERS = {}

NS = "nerocreatures:"
SIZE = 8
MAX_FRAMES = 4
TEXTURES = os.path.join(ASSETS, "textures", "particle")
DEFINITIONS = os.path.join(ASSETS, "particles")
MANIFEST = os.path.join(REPO_ROOT, "art", "particles", "MANIFEST.md")

PARTICLES = {}  # id -> {"frames": [Image], "habitat": str, plus the manifest fields}


def particle(name: str, habitat: str, legend: str, art: str, **meta) -> None:
    """Registers one particle. ``art`` is eight rows; each row holds every frame's row, left to right in play order."""
    table = {}
    for entry in legend.split():
        key, token = entry.split("=", 1)
        ramp, step = token[:-1], int(token[-1])
        table[key] = colour(habitat, ramp, step)
    lines = [line.split() for line in art.strip().splitlines()]
    count = len(lines[0])
    if len(lines) != SIZE or not 1 <= count <= MAX_FRAMES or any(len(line) != count for line in lines):
        raise ValueError("%s: %d rows of 1 to %d frames side by side" % (name, SIZE, MAX_FRAMES))
    frames = []
    for f in range(count):
        image = Image(SIZE, SIZE)
        for y, line in enumerate(lines):
            if len(line[f]) != SIZE:
                raise ValueError("%s: row %r is not %d wide" % (name, line[f], SIZE))
            for x, ch in enumerate(line[f]):
                if ch != ".":
                    image.set(x, y, table[ch])
        frames.append(image)
    for field in ("look", "use", "replaces", "life", "size", "gravity", "motion"):
        if field not in meta:
            raise ValueError("%s: manifest field '%s' is missing" % (name, field))
    PARTICLES[name] = dict(meta, frames=frames, habitat=habitat)


# --------------------------------------------------------------------------- luna

particle("lumen_mote", "luna", "a=accent1 b=accent2 c=accent3 w=accent4", """
........ ........ ...a.... ........
........ ...bb... ...bb... ........
...bb... ..bccb.. ..bcwb.. ...ab...
..bccb.. .bcwwcb. abcwwcb. ..abca..
..bccb.. .bcwwcb. .bcwwcba ..acba..
...bb... ..bccb.. ..bwcb.. ...ba...
........ ...bb... ...bb... ........
........ ........ ....a... ........
""",
         look="warm gold glow mote that swells, flares and gutters",
         use="Lumen Moth light trail and Mimic reveal; Xertz Forager night-vision pulse",
         replaces=["LumenMoth: GLOW", "XertzForager: GLOW"],
         life="24-36", size="0.10-0.14", gravity="-0.01 (drifts up)",
         motion="slow random drift, velocity x0.92 per tick; full-bright; frames by age")

particle("echo_ring", "asteroid", "a=accent2 b=accent3 w=accent4", """
........ ........ ........ ..aa.a..
........ ........ ..bbbb.. .a......
........ ...ww... .b....b. a......a
...ww... ..w..w.. .b....b. .......a
...ww... ..w..w.. .b....b. a.......
........ ...ww... .b....b. a......a
........ ........ ..bbbb.. ......a.
........ ........ ........ ..a.aa..
""",
         look="thin pale-blue ring that expands and breaks up",
         use="Echo Shrike: at the heard position; Strata Hound: mark on the found block; "
             "resonant_whistle and sonic_ward pulses",
         replaces=["StrataHound (found-block mark): END_ROD", "EchoShrike: new spawn, no stand-in today"],
         life="12-16", size="0.25 growing to 0.6", gravity="0",
         motion="stationary; quad scale grows with age; full-bright; frames by age, played once")

particle("nodule_glint", "luna", "u=umber2 v=umber3 c=accent3 w=accent4", """
........ ........ w.....w. ........
........ .....w.. .c...c.. ........
...vv... ...vcw.. ..cvc... ...c....
..vvcv.. ..vvwc.. ...ww... ..cwc...
..uvvv.. ..uvcv.. ..cwwc.. ...c....
...uu... ...uu... .c.uu.c. ........
........ ........ w.....w. ........
........ ........ ........ ........
""",
         look="ore chip that catches the light: a diagonal gold flash, then a fading point",
         use="Lode Tortoise: a nodule reaching its last growth stage; Strata Hound: the scent trail "
             "toward the tracked ore",
         replaces=["StrataHound (trail): END_ROD", "LodeTortoise: new spawn, no stand-in today"],
         life="10-14", size="0.12", gravity="0",
         motion="stationary; full-bright; frames by age, played once")

# --------------------------------------------------------------------------- mars

particle("dust_swirl", "mars", "a=sand0 b=sand2 c=bone2 w=bone4", """
........ ........ ..bcc... ...b.c..
..ccc... ...bbc.. .b...c.. .b......
.c...c.. ..b...c. .b.cw.c. b..c...c
.b.ww.c. .b.cc..c b..c..c. ...w....
.b..w.c. .b.w...c b...bb.. b......c
.a....b. .a..wc.. .a...... ....b...
..aabb.. ..a..... ..aab... .a....b.
........ ...aab.. ........ ...a....
""",
         look="pale sand curl that turns a quarter each frame and thins out",
         use="Dune Loper sprint kick-up; Dust Devil column; burrow, emerge and ground-slam wind-up dust",
         replaces=["DuneLoper: CLOUD", "BurrowGoal: POOF", "EmergeAttackGoal: POOF",
                   "GroundSlamGoal (wind-up): SMOKE", "DustDevil: add beside the red-sand BLOCK particle"],
         life="14-20", size="0.15-0.22", gravity="-0.004 (lifts slightly)",
         motion="outward and up at 0.02-0.05, velocity x0.9 per tick; world-lit; frames by age")

particle("static_spark", "mars", "m=accent2 c=accent3 l=accent4 w=wrecks.white4", """
........ .l...... l....... ........
...l.... ..c..... .c....l. .....m..
..lw.... ..lw..l. ..l..c.. .m......
...wl... ...ww.c. ...lc... ........
....wc.. ..cwl... ....l... ...m....
...lc... .l..w... ..c..c.. ........
....l... ....c... .l....l. m.....m.
........ .....l.. ........ ........
""",
         look="white-mint forked spark that branches, then leaves three dying points",
         use="Static Swarm drain arcs and hit point; static_jar burst; Plasma Slime, energy aura, "
             "rogue machines and Scrap Hulk shorts; Dust Devil exposed core",
         replaces=["StaticSwarm: ELECTRIC_SPARK", "StaticJarBurst: ELECTRIC_SPARK", "PlasmaSlime: ELECTRIC_SPARK",
                   "EnergyAuraGoal: ELECTRIC_SPARK and ENCHANTED_HIT", "RogueAndroid: ELECTRIC_SPARK",
                   "RogueDrone: ELECTRIC_SPARK", "ScrapHulk and ScrapHulkAssembly: ELECTRIC_SPARK",
                   "TerraformingDrone (work): ELECTRIC_SPARK"],
         life="5-8", size="0.10-0.16", gravity="0",
         motion="short jump at 0.1-0.2 toward the target, random 90-degree roll per particle; "
                "full-bright; frames by age")

# --------------------------------------------------------------------------- cindara

particle("slag_ember", "cindara", "k=clinker0 r=accent1 o=accent2 y=accent3 w=accent4", """
........ ........ ........ ........
...oo... ........ ........ ........
..oywo.. ...oy... ........ ........
..ywwy.. ..oywo.. ...oy... ...r....
..oywo.. ..rywo.. ..ryo... ..kr....
..roor.. ...ro... ...rk... ...k....
...rr... ........ ........ ........
........ ........ ........ ........
""",
         look="white-hot ember that cools through orange to a dark clinker fleck",
         use="Slag Beetle digestion vent and cough; Magma Wader footfalls; Cinder Tyrant eruption",
         replaces=["SlagBeetle: SMALL_FLAME", "MagmaWader: LAVA", "CinderTyrant: FLAME (keep LAVA for the burst)"],
         life="16-28", size="0.08-0.12", gravity="-0.02 for 8 ticks, then +0.03 (rises, then falls)",
         motion="upward 0.04-0.08 with slight spread; full-bright on frames 0-2, world-lit on 3; frames by age")

# --------------------------------------------------------------------------- glacira

particle("rime_mote", "glacira", "d=accent1 b=accent2 c=accent3 w=accent4", """
........ ...c.... ........
...c.... .c.c.c.. ...b....
.c.c.c.. ..cwc... ..b.b...
..cwc... ccwwwcc. ...w....
.c.c.c.. ..cwc... ..b.b...
...c.... .c.c.c.. ...b....
........ ...c.... ........
........ ........ ........
""",
         look="pale blue six-point ice crystal that opens and thins to a speck",
         use="Rime Yak aura-edge ring; Frost Lurker ambush; Glacite Wisp trail; Rime Matriarch beam, "
             "storm ring and phase bursts",
         replaces=["RimeYak (aura ring): SNOWFLAKE", "FrostLurker: SNOWFLAKE", "GlaciteWisp: SNOWFLAKE",
                   "RimeMatriarch: SNOWFLAKE (keep ITEM_SNOWBALL shatter and the Yak's CLOUD breath)"],
         life="30-50", size="0.08-0.12", gravity="+0.003 (sinks slowly)",
         motion="drifts sideways on a slow sine, velocity x0.96; world-lit plus 6 light levels; "
                "frame 0 to 1 over the first third, 2 for the last quarter")

# --------------------------------------------------------------------------- greenxertz

particle("spore_mote", "greenxertz", "d=accent1 g=accent2 y=accent3", """
........ ........ ........
........ ........ ........
...gg... ...g.... ........
..gyyg.. ..gyg... ...g....
..gyyd.. ...gd... ...d....
...dd... ........ ........
........ ........ ........
........ ........ ........
""",
         look="chartreuse falling dot that shrinks",
         use="Spore Drifter canopy-to-crop fall; Bloom Host and Bloom Sovereign corpse strands; "
             "Mossback grazing",
         replaces=["SporeDrifter: FALLING_SPORE_BLOSSOM and SPORE_BLOSSOM_AIR (ambient)",
                   "BloomHost (strand): SPORE_BLOSSOM_AIR", "BloomSovereign (strand): SPORE_BLOSSOM_AIR",
                   "Mossback: SPORE_BLOSSOM_AIR"],
         life="30-40 (strands: 10)", size="0.06-0.09", gravity="+0.02 (strands: 0)",
         motion="falls straight with a 1-texel sway; full-bright; frames by age")

particle("spore_puff", "greenxertz", "d=accent1 g=accent2 y=accent3 w=accent4 c=cream2", """
........ ........ ..g..y.. .g....d.
........ ..gyg... .gy.g... ...g....
...yy... .gywyg.. y.gcg.yg g.....g.
..ywwy.. .ywcwyg. .gc.cg.. ........
..ywcy.. .gycwy.. g.gcg..y ....d...
...yg... ..gygd.. .y.g.g.. d......g
........ ...d.... ..d..d.. ..g.....
........ ........ ........ .....d..
""",
         look="bright spore burst that opens into a ring of drifting specks",
         use="Spore Drifter: on the crop when a growth tick lands; Bloom Host raise; "
             "Bloom Sovereign pod spawn, pod burst and phase change",
         replaces=["SporeDrifter (tick lands): HAPPY_VILLAGER", "BloomHost (raise): SPORE_BLOSSOM_AIR",
                   "BloomSovereign (bursts): SPORE_BLOSSOM_AIR"],
         life="12-16", size="0.2 growing to 0.35", gravity="0",
         motion="stationary or 0.01 outward; full-bright; frames by age, played once")

particle("bloom_mote", "greenxertz", "p=rose1 r=rose2 l=rose3 y=accent3 g=accent1", """
........ ........ ...l.... ........
........ ...r.... ..lrl... ...l....
...r.... ..rlr... .lr.rl.. ........
..ryr... .rlylr.. lr.y.rl. .l.y.l..
...p.... ..rlp... .pr.rp.. ........
...g.... ...p.... ..prp... ...p....
........ ...g.... ...p.... ........
........ ........ ........ ........
""",
         look="rose blossom that opens from a bud, then scatters four petals",
         use="Bloom Sovereign heal channel and healed pods; Terraforming Drone planted block",
         replaces=["BloomSovereign (heal): HAPPY_VILLAGER and HEART", "TerraformingDrone (plant): HAPPY_VILLAGER"],
         life="20-26", size="0.12-0.16", gravity="-0.006 (floats up)",
         motion="rises 0.02 with a slow roll; full-bright; frames by age "
                "(taming keeps the vanilla HEART)")

# --------------------------------------------------------------------------- nyxara

particle("prism_glint", "nyxara", "m=accent2 p=accent3 c=accent_b2 a=accent_b3 w=opal4", """
........ ...p.... ...a.... ........
........ ...p.... ...a.... ...c....
...p.... ...m.... ...c.... ........
..pwp... ppmwmpp. aacwcaa. .c.w.c..
...p.... ...m.... ...c.... ........
........ ...p.... ...a.... ...c....
........ ...p.... ...a.... ........
........ ........ ........ ........
""",
         look="four-point twinkle that turns from magenta to cyan and breaks into four points",
         use="Prism Stag aura; Shardback reflect; Crystal Golem cracks; Facet Colossus beam and phase bursts; "
             "the Radiant sparkle (client, every 40 ticks)",
         replaces=["PrismStag: END_ROD", "Shardback (reflect): ELECTRIC_SPARK",
                   "FacetColossus: END_ROD (bursts, firing beam) and ELECTRIC_SPARK (telegraph, thorns)"],
         life="8-12", size="0.12-0.18", gravity="0",
         motion="stationary (beam: none; bursts: 0.05 outward); full-bright; frames by age, played once")

# --------------------------------------------------------------------------- asteroid

particle("magnet_spark", "asteroid", "b=accent3 l=accent4 r=wrecks.accent3 s=wrecks.accent4 w=wrecks.white4", """
........ ........ ........ ........
.r....b. ..r..b.. ........ ...s....
.r....b. ..r..b.. ...sl... ........
.r....b. ..rwwb.. ..rwwb.. .r.w.b..
..r..b.. ..rwwb.. ..rwwb.. ........
...ww... ...sl... ...sl... ...l....
........ ........ ........ ........
........ ........ ........ ........
""",
         look="red and blue field lines that snap shut on a white spark",
         use="Magnet Crab: on the pulled item and at the owner when it arrives; magnet gear pulls",
         replaces=["MagnetCrab: ELECTRIC_SPARK (both calls)"],
         life="8-10", size="0.14", gravity="0",
         motion="stationary, or 0.05 toward the crab; full-bright; frames by age, played once")

particle("gravity_ripple", "asteroid", "g=gunmetal1 a=accent2 b=accent3 w=accent4", """
........ ........ ........ ........
........ ........ ........ ........
.bbbbbb. ........ ........ ........
b......b ..aaaa.. ........ ........
b......b .a....a. ...bb... ...ww...
.gbbbbg. ..gaag.. ..b..b.. ...gg...
........ ........ ...gg... ........
........ ........ ........ ........
""",
         look="flat blue ring seen edge-on that sinks and tightens to a bright point",
         use="Gravity Leech: around the player on latch and while tethered; Asteroid Worm emerge warning "
             "on the ground; gravity gear",
         replaces=["GravityLeech (latch): new spawn, no stand-in today",
                   "EmergeAttackGoal (strike-range ring): POOF"],
         life="10-14", size="0.5 shrinking to 0.2", gravity="+0.01",
         motion="stationary, sinks slightly; quad scale shrinks with age; full-bright; frames by age, played once")

# --------------------------------------------------------------------------- deep space

particle("void_trail", "deep_space", "n=navy3 d=accent1 v=accent2 l=accent3 w=accent4", """
........ ........ ........ ........
..vll... ........ ........ ........
.vlwwl.. ..vvl... ...d.... ........
.vlwwlv. .dvllv.. ..dvv... ...d....
.dvllvd. .ddvvd.. .n.dvd.. ..n..d..
..ddvd.. ..nddn.. ..n.d... .....n..
...nn... ....n... ....n... ...n....
........ ........ ........ ........
""",
         look="violet smear with a pale core that fades to navy flecks",
         use="Gravity Leech tether chain; Void Manta phase and Void Crawler blink arrival; blink_charm",
         replaces=["GravityTether: REVERSE_PORTAL", "VoidManta: REVERSE_PORTAL (both calls)",
                   "BlinkStrikeGoal (arrival): REVERSE_PORTAL"],
         life="10-16 (tether: 6)", size="0.12-0.18", gravity="0",
         motion="nearly still, velocity x0.8; full-bright; frames by age")

particle("void_blink", "deep_space", "n=navy0 d=accent1 v=accent2 l=accent3 w=accent4", """
.v.dd.v. ........ ........ ...l....
v......v ..vllv.. ........ ...w....
d......d .v.nn.v. ...ll... ..lwl...
d..nn..d .lnnnnl. ..lwwl.. lwwwwwl.
d..nn..d .lnnnnl. ..lwwl.. ..lwl...
v......v .v.nn.v. ...ll... ...w....
.v.dd.v. ..vllv.. ........ ...l....
........ ........ ........ ........
""",
         look="violet ring that collapses onto a black core, then flashes as a pale star",
         use="Void Crawler blink departure; Void Manta phase start; blink_charm use",
         replaces=["BlinkStrikeGoal (departure): PORTAL"],
         life="8-10", size="0.5 shrinking to 0.3", gravity="0",
         motion="stationary; one per blink at chest height plus 6-8 void_trail; full-bright; "
                "frames by age, played once")

# --------------------------------------------------------------------------- gear

particle("scan_beam", "wrecks", "d=accent_owned1 t=accent_owned2 l=accent_owned3 w=accent_owned4", """
tt....tt tt....tt tt....tt d......d
t......t t......t t......t ........
.llwwll. ........ ........ ........
........ ........ ........ ...ll...
........ .llwwll. ........ ...ll...
........ ........ .llwwll. ........
t......t t......t t......t ........
tt....tt tt....tt tt....tt d......d
""",
         look="teal corner brackets with a scan line sweeping down, closing to a lock dot",
         use="Field Scanner: on the creature being scanned (one at mid-height per scan step) and a "
             "short line of them from the player on completion",
         replaces=["Field Scanner: new spawn, no stand-in today"],
         life="10-12", size="0.3-0.5 (scale with the target's width)", gravity="0",
         motion="stationary, follows nothing; full-bright; frames by age, played once; client-only spawn is fine")


# --------------------------------------------------------------------------- output

def frame_names(name: str) -> list:
    return ["%s_%d" % (name, i) for i in range(len(PARTICLES[name]["frames"]))]


def definition(name: str) -> str:
    return json.dumps({"textures": [NS + frame for frame in frame_names(name)]}, indent=2) + "\n"


def manifest() -> str:
    out = [
        "# Custom particles",
        "",
        "Generated by `tools/art/paint/particles.py`; edit the table in that file, not this one.",
        "",
        "- %d particle types, %d frames, every sprite 8x8, texels fully opaque or clear."
        % (len(PARTICLES), sum(len(p["frames"]) for p in PARTICLES.values())),
        "- Registry id: `nerocreatures:<id>`. All are simple particle types (no colour or other options),",
        "  so registration is the same on every Minecraft line.",
        "- Definition: `assets/nerocreatures/particles/<id>.json`; frames:",
        "  `assets/nerocreatures/textures/particle/<id>_<n>.png`.",
        "- Provider: one sprite-set provider can serve every id. Pick the frame from the particle's age",
        "  unless the motion note says otherwise, and fade alpha over the last quarter of the lifetime.",
        "- Lifetime is in ticks, size is the quad half-size in blocks, gravity is the per-tick change to",
        "  the vertical speed (positive falls). These are starting values for tuning in game.",
        "- \"Replaces\" names the class and the vanilla particle it spawns today. Anything not listed",
        "  (smoke, explosions, lava bursts, snowball shatter, breath cloud, taming hearts) stays vanilla.",
        "",
        "## Summary",
        "",
        "| id | frames | palette | look | life (ticks) | size | gravity |",
        "|---|---|---|---|---|---|---|",
    ]
    for name, p in PARTICLES.items():
        out.append("| `%s` | %d | %s | %s | %s | %s | %s |" % (
            name, len(p["frames"]), p["habitat"], p["look"], p["life"], p["size"], p["gravity"]))
    for name, p in PARTICLES.items():
        out += ["", "## `%s`" % name, "",
                "- **Frames:** " + ", ".join("`%s.png`" % f for f in frame_names(name)),
                "- **Used for:** " + p["use"],
                "- **Replaces:**"]
        out += ["  - " + line for line in p["replaces"]]
        out += ["- **Lifetime:** %s ticks. **Size:** %s. **Gravity:** %s." % (p["life"], p["size"], p["gravity"]),
                "- **Motion and light:** " + p["motion"]]
    return "\n".join(out) + "\n"


def sheet(path: str) -> None:
    """Review sheet: each frame on a dark, own-habitat and pale ground, then at game scale."""
    from preview import INK, PAPER, text

    scale, gap = 6, 4
    cell = SIZE * scale + gap
    grounds = [(18, 18, 24, 255), None, (220, 232, 242, 255)]
    row_h = cell * len(grounds) + 30
    width = 150 + MAX_FRAMES * cell + 30 + 120
    out = Image(width, 30 + row_h * len(PARTICLES), PAPER)
    text(out, 8, 8, "NEROCREATURES PARTICLES (%d) - DARK / HABITAT / PALE GROUND - 2X STRIP" % len(PARTICLES), INK, 2)
    for index, (name, p) in enumerate(PARTICLES.items()):
        y0 = 30 + index * row_h
        text(out, 8, y0 + 4, name, INK, 2)
        text(out, 8, y0 + 22, "%s  %d frames" % (p["habitat"], len(p["frames"])), INK, 1)
        text(out, 8, y0 + 32, "life " + p["life"], INK, 1)
        own = palette().ground(p["habitat"]) + (255,)
        for g, ground in enumerate(grounds):
            for f, frame in enumerate(p["frames"]):
                x = 150 + f * cell
                y = y0 + g * cell
                out.rect(x, y, SIZE * scale, SIZE * scale, ground or own)
                out.blit(frame, x, y, scale)
        x2 = 150 + MAX_FRAMES * cell + 30
        for g, ground in enumerate(grounds):
            out.rect(x2, y0 + g * cell, 4 * 22 + 6, 24, ground or own)
            for f, frame in enumerate(p["frames"]):
                out.blit(frame, x2 + 4 + f * 22, y0 + g * cell + 4, 2)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(out.to_png())
    print("sheet " + path)


def main(argv: list) -> int:
    check = "--check" in argv
    images = {}
    for name, p in PARTICLES.items():
        for frame_name, frame in zip(frame_names(name), p["frames"]):
            images[frame_name] = frame
    failed = write_images(TEXTURES, images, check)
    for name in PARTICLES:
        failed += not write_text(os.path.join(DEFINITIONS, name + ".json"), definition(name), check)
    failed += not write_text(MANIFEST, manifest(), check)
    for folder, wanted, ext in ((TEXTURES, set(images), ".png"), (DEFINITIONS, set(PARTICLES), ".json")):
        present = set(n[:-len(ext)] for n in os.listdir(folder) if n.endswith(ext)) if os.path.isdir(folder) else set()
        for stray in sorted(present - wanted):
            print("FAIL  %s has no painter" % os.path.relpath(os.path.join(folder, stray + ext), REPO_ROOT))
            failed += 1
    if "--sheet" in argv:
        sheet(argv[argv.index("--sheet") + 1])
    print("particles: %d types, %d frames, %s" % (len(PARTICLES), len(images), "FAILED" if failed else "clean"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
