#!/usr/bin/env python3
"""Model spec loading, validation and the reference pose evaluator.

A spec is ``art/models/<id>.json``; the format is documented in ``art/models/README.md``. This
module is the single place that says what a valid spec is and how its clips are evaluated, so the
preview renderer and the game-side interpolator can be checked against the same arithmetic.

    python tools/art/spec.py            # validate every spec
    python tools/art/spec.py <id> ...   # validate some
"""

from __future__ import annotations

import json
import math
import os
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelkit import MODELS_DIR, Palette, box_uv_size  # noqa: E402

FORMAT = 1
CLIP_NAMES = ("idle", "move", "ability", "ability_2", "hurt", "rest", "phase_1", "phase_2", "phase_3")
REQUIRED_CLIPS = ("idle", "move", "hurt")
ACTION_CLIPS = ("ability", "ability_2", "rest")
EASINGS = ("linear", "smooth")
HURT_TICKS = 6
BLEND_TICKS = 4
MIN_TELEGRAPH_TICKS = 10
MAX_KEYS = 8
MAX_CLIP_PARTS = 12
MAX_DEPTH = 3
WALK_FREQUENCY = 0.6662  # radians of walk cycle per unit of walk position, as vanilla limbs use

# size class -> (boxes, animated parts, allowed sheet sizes)
BUDGETS = {
    "S": (18, 8, ((64, 64),)),
    "M": (30, 12, ((64, 64),)),
    "L": (46, 16, ((128, 128),)),
    "XL": (72, 22, ((128, 128), (256, 128))),
    "XXL": (96, 20, ((256, 256),)),
}
TRANSLUCENT_ALLOWED = ("dust_devil", "static_swarm", "plasma_slime", "spore_drifter", "glacite_wisp")
SNAKE = re.compile(r"^[a-z][a-z0-9_]*$")


# --- files -----------------------------------------------------------------

def spec_path(entity_id: str) -> str:
    return os.path.join(MODELS_DIR, entity_id + ".json")


def all_ids() -> list:
    return sorted(name[:-5] for name in os.listdir(MODELS_DIR) if name.endswith(".json"))


def load(entity_id: str) -> dict:
    with open(spec_path(entity_id), "r", encoding="utf-8") as handle:
        return json.load(handle)


def _scalar(value) -> bool:
    return not isinstance(value, (dict, list))


def _inline(value) -> bool:
    if _scalar(value):
        return True
    if isinstance(value, list):
        return all(_scalar(v) or (isinstance(v, list) and all(_scalar(x) for x in v) and len(v) <= 3)
                   for v in value) and len(json.dumps(value)) <= 96
    return all(_inline(v) for v in value.values()) and len(json.dumps(value)) <= 110


def dump_json(value, indent: int = 0) -> str:
    """Canonical layout: short leaves stay on one line, so a cube or a keyframe is one diff line."""
    pad = "  " * indent
    if _inline(value):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        rows = [pad + "  " + dump_json(v, indent + 1) for v in value]
        return "[\n" + ",\n".join(rows) + "\n" + pad + "]"
    rows = [pad + "  " + json.dumps(k) + ": " + dump_json(v, indent + 1) for k, v in value.items()]
    return "{\n" + ",\n".join(rows) + "\n" + pad + "}"


def save_json(path: str, value) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(dump_json(value) + "\n")


# --- structure helpers -----------------------------------------------------

def parts_by_name(spec: dict) -> dict:
    return {part["name"]: part for part in spec["parts"]}


def depth_of(spec: dict, name: str) -> int:
    parts = parts_by_name(spec)
    depth = 1
    while parts[name].get("parent"):
        name = parts[name]["parent"]
        depth += 1
    return depth


def part_path(spec: dict, name: str) -> list:
    """Names from the top-level part down to ``name``, the lookup path under the model root."""
    parts = parts_by_name(spec)
    path = [name]
    while parts[path[0]].get("parent"):
        path.insert(0, parts[path[0]]["parent"])
    return path


def resolved_cubes(spec: dict, part: dict) -> list:
    """The part's cubes with ``uv`` and ``mirror`` settled, following ``uv_like``."""
    source = part.get("uv_like")
    if not source:
        return [dict(cube, mirror=bool(cube.get("mirror", False))) for cube in part.get("cubes", [])]
    donor = {cube["name"]: cube for cube in parts_by_name(spec)[source].get("cubes", [])}
    out = []
    for cube in part.get("cubes", []):
        merged = dict(cube)
        if "uv" in donor[cube["name"]]:
            merged["uv"] = donor[cube["name"]]["uv"]
        merged["mirror"] = not donor[cube["name"]].get("mirror", False)
        out.append(merged)
    return out


def animated_parts(spec: dict) -> set:
    names = set()
    proc = spec.get("procedural", {})
    for kind in ("swing", "wave", "look"):
        names.update(entry["part"] for entry in proc.get(kind, []))
    for clip in spec.get("clips", {}).values():
        names.update(clip.get("tracks", {}).keys())
    return names


# --- validation ------------------------------------------------------------

def validate(spec: dict, palette: Palette, file_id: str = None) -> list:
    errors = []
    err = errors.append
    sid = spec.get("id", "?")
    if spec.get("format") != FORMAT:
        err("format must be %d" % FORMAT)
    if not SNAKE.match(str(sid)) or (file_id and file_id != sid):
        err("id must be snake_case and match the file name")
    habitat = spec.get("habitat")
    if habitat not in palette.data["habitats"]:
        return errors + ["unknown habitat '%s'" % habitat]
    size_class = spec.get("size_class")
    if size_class not in BUDGETS:
        return errors + ["size_class must be one of %s" % ", ".join(BUDGETS)]
    max_boxes, max_animated, sheets = BUDGETS[size_class]
    extra = spec.get("budget_exception", {})
    max_boxes += int(extra.get("boxes", 0))
    tex = spec.get("texture", {})
    sheet = (tex.get("width"), tex.get("height"))
    if sheet not in sheets:
        err("texture %sx%s is not allowed for size class %s" % (sheet[0], sheet[1], size_class))
    render_type = spec.get("render_type", "cutout")
    if render_type not in ("cutout", "translucent"):
        err("render_type must be cutout or translucent")
    if render_type == "translucent" and sid not in TRANSLUCENT_ALLOWED:
        err("translucent is not allowed for " + sid)
    materials = spec.get("materials", [])
    if not 1 <= len(materials) <= palette.rules["max_material_ramps"]:
        err("materials must name 1 to %d ramps" % palette.rules["max_material_ramps"])
    for name in materials + [spec.get("accent", "accent")] + ([spec["accent_2"]] if spec.get("accent_2") else []):
        try:
            palette.ramp(habitat, name)
        except KeyError as exc:
            err(str(exc))
    if materials:
        main = palette.habitat(habitat)["ramps"].get(materials[0], {})
        if main and not main.get("main_body", False):
            err("materials[0] '%s' is the main body ramp but fails the ground contrast rule" % materials[0])

    parts = {}
    boxes = 0
    for part in spec.get("parts", []):
        name = part.get("name", "?")
        if not SNAKE.match(name):
            err("part name '%s' is not snake_case" % name)
        if name in parts:
            err("duplicate part '%s'" % name)
        parent = part.get("parent")
        if parent is not None and parent not in parts:
            err("part '%s': parent '%s' must be declared before it" % (name, parent))
        parts[name] = part
        if len(part.get("pivot", [])) != 3 or len(part.get("rotation", [0, 0, 0])) != 3:
            err("part '%s': pivot and rotation are [x, y, z]" % name)
        seen = set()
        for cube in part.get("cubes", []):
            boxes += 1
            cname = cube.get("name", "?")
            if not SNAKE.match(cname) or cname in seen:
                err("part '%s': cube name '%s' missing, duplicated or not snake_case" % (name, cname))
            seen.add(cname)
            size = cube.get("size", [])
            if len(size) != 3 or any((not isinstance(v, int)) or v < 1 for v in size):
                err("%s.%s: size must be three whole numbers of 1 or more" % (name, cname))
                continue
            if len(cube.get("origin", [])) != 3:
                err("%s.%s: origin is [x, y, z]" % (name, cname))
            if "uv" in cube and not part.get("uv_like"):
                w, h = box_uv_size(size)
                u, v = cube["uv"]
                if u < 0 or v < 0 or u + w > sheet[0] or v + h > sheet[1]:
                    err("%s.%s: uv runs off the sheet" % (name, cname))
        like = part.get("uv_like")
        if like:
            if like not in parts or parts[like].get("uv_like"):
                err("part '%s': uv_like must name an earlier part that owns its UVs" % name)
            else:
                mine = [(c.get("name"), tuple(c.get("size", []))) for c in part.get("cubes", [])]
                theirs = [(c.get("name"), tuple(c.get("size", []))) for c in parts[like].get("cubes", [])]
                if mine != theirs:
                    err("part '%s': cubes must match '%s' by name and size to share UVs" % (name, like))
        cond = part.get("visible_when")
        if cond is not None:
            for key, value in cond.items():
                if key not in ("stage", "variant", "form", "flag"):
                    err("part '%s': visible_when key '%s' is unknown" % (name, key))
                elif key == "flag" and value not in ("radiant", "alpha", "saddled", "panniers"):
                    err("part '%s': unknown flag '%s'" % (name, value))
    if errors:
        return errors
    for name in parts:
        if depth_of(spec, name) > MAX_DEPTH:
            err("part '%s' is deeper than %d levels" % (name, MAX_DEPTH))
    if boxes > max_boxes:
        err("%d boxes is over the %s budget of %d" % (boxes, size_class, max_boxes))

    proc = spec.get("procedural", {})
    for kind in ("swing", "wave", "look"):
        for entry in proc.get(kind, []):
            if entry.get("part") not in parts:
                err("procedural %s: unknown part '%s'" % (kind, entry.get("part")))
    for entry in proc.get("wave", []):
        if entry.get("axis") not in ("x", "y", "z"):
            err("procedural wave on '%s': axis must be x, y or z" % entry.get("part"))

    clips = spec.get("clips", {})
    for name in REQUIRED_CLIPS:
        if name not in clips:
            err("missing required clip '%s'" % name)
    for name, clip in clips.items():
        if name not in CLIP_NAMES:
            err("clip name '%s' is not in the vocabulary" % name)
            continue
        length = clip.get("length")
        if not isinstance(length, int) or length < 1:
            err("clip %s: length must be whole ticks" % name)
            continue
        loop = bool(clip.get("loop", False))
        if name == "hurt" and (length != HURT_TICKS or loop):
            err("clip hurt must be %d ticks and must not loop" % HURT_TICKS)
        if name in ("idle", "move") and not loop:
            err("clip %s must loop" % name)
        if "telegraph" in clip and clip["telegraph"] < MIN_TELEGRAPH_TICKS:
            err("clip %s: a telegraph is at least %d ticks" % (name, MIN_TELEGRAPH_TICKS))
        if clip.get("damaging") and "telegraph" not in clip:
            err("clip %s: a damaging ability must declare its telegraph" % name)
        tracks = clip.get("tracks", {})
        if len(tracks) > MAX_CLIP_PARTS:
            err("clip %s moves %d parts; the cap is %d" % (name, len(tracks), MAX_CLIP_PARTS))
        channels = [("glow", clip["glow"], 1)] if "glow" in clip else []
        for pname, track in tracks.items():
            if pname not in parts:
                err("clip %s: unknown part '%s'" % (name, pname))
            for channel, keys in track.items():
                if channel not in ("rot", "pos"):
                    err("clip %s.%s: channel '%s' is not rot or pos" % (name, pname, channel))
                channels.append((pname + "." + channel, keys, 3))
        for label, keys, width in channels:
            where = "clip %s %s" % (name, label)
            if not keys or len(keys) > MAX_KEYS:
                err("%s: 1 to %d keyframes" % (where, MAX_KEYS))
                continue
            last = -1
            for key in keys:
                t = key.get("t")
                if not isinstance(t, int) or t <= last or t > length:
                    err("%s: keyframe ticks must be whole, rising and within the clip" % where)
                last = t if isinstance(t, int) else last
                if key.get("ease", "linear") not in EASINGS:
                    err("%s: ease must be linear or smooth" % where)
                value = key.get("v")
                if (width == 3 and (not isinstance(value, list) or len(value) != 3)) or (
                        width == 1 and not isinstance(value, (int, float))):
                    err("%s: bad keyframe value" % where)
            if loop and (keys[0].get("t") != 0 or keys[-1].get("t") != length
                         or keys[0].get("v") != keys[-1].get("v")):
                err("%s: a looping channel starts at tick 0 and ends at the clip length on the same value" % where)

    animated = animated_parts(spec)
    if len(animated) > max_animated:
        err("%d animated parts is over the %s budget of %d" % (len(animated), size_class, max_animated))
    if not proc.get("wave") and not clips.get("idle", {}).get("tracks"):
        err("nothing moves at idle; add a wave or idle tracks")
    radiant = spec.get("radiant")
    if radiant:
        if sid not in palette.data["radiant"]["carriers"]:
            err("only trait carriers declare a radiant map")
        for source, target in radiant.items():
            if source not in materials or target not in palette.habitat(habitat)["radiant_ramps"]:
                err("radiant map %s -> %s: unknown ramp" % (source, target))
        if set(radiant) != set(materials):
            err("radiant map must cover every material ramp")
    return errors


# --- reference evaluator ---------------------------------------------------

def ease(fraction: float, mode: str) -> float:
    return fraction * fraction * (3.0 - 2.0 * fraction) if mode == "smooth" else fraction


def sample(keys: list, t: float):
    """Value of one channel at tick ``t``. Holds the first and last keys outside their range.

    A key's ``ease`` shapes the segment that arrives at it.
    """
    if t <= keys[0]["t"]:
        return keys[0]["v"]
    for a, b in zip(keys, keys[1:]):
        if t <= b["t"]:
            f = ease((t - a["t"]) / float(b["t"] - a["t"]), b.get("ease", "linear"))
            if isinstance(a["v"], list):
                return [a["v"][i] + (b["v"][i] - a["v"][i]) * f for i in range(3)]
            return a["v"] + (b["v"] - a["v"]) * f
    return keys[-1]["v"]


def clip_time(clip: dict, clock: float) -> float:
    return clock % clip["length"] if clip.get("loop") else min(clock, clip["length"])


def evaluate(spec: dict, palette: Palette, state: dict) -> tuple:
    """Pose for one frame.

    ``state`` keys (all optional): ``age`` ticks, ``walk_pos``, ``walk_speed`` 0-1, ``pose`` (a clip
    name from ACTION_CLIPS, or None), ``pose_ticks`` (ticks since the pose began), ``phase`` (0, or
    1-3 for a boss phase), ``hurt_ticks`` (0-6 into the hurt, or None).

    Returns ``(deltas, glow)``: ``deltas[part] = (rot_deg, pos)`` to ADD to the part's rest pose,
    and the glow intensity 0-1. The layering is:

    1. swings (legs, scaled by walk speed) and waves (faded by walk speed unless told not to)
    2. base layer: ``idle`` on the age clock times (1 - walk), plus ``move`` on the walk clock times
       walk. In boss phase N, ``phase_N`` stands in for ``idle``.
    3. action layer: the pose's clip on the pose clock, cross-faded against the base layer over
       BLEND_TICKS (procedural motion is not faded)
    4. ``hurt`` added on top at full weight while it lasts
    """
    age = float(state.get("age", 0.0))
    walk_pos = float(state.get("walk_pos", 0.0))
    walk = max(0.0, min(1.0, float(state.get("walk_speed", 0.0))))
    deltas = {part["name"]: ([0.0, 0.0, 0.0], [0.0, 0.0, 0.0]) for part in spec["parts"]}
    proc = spec.get("procedural", {})
    for entry in proc.get("swing", []):
        angle = math.cos(walk_pos * WALK_FREQUENCY + math.radians(entry.get("phase", 0.0)))
        deltas[entry["part"]][0][0] += angle * entry["amplitude"] * walk
    for entry in proc.get("wave", []):
        strength = (1.0 - walk) if entry.get("fade_with_walk", True) else 1.0
        value = math.sin(age * entry["frequency"] + math.radians(entry.get("phase", 0.0)))
        deltas[entry["part"]][0]["xyz".index(entry["axis"])] += value * entry["amplitude"] * strength

    clips = spec.get("clips", {})
    glow = palette.rules["glow"]["intensity"]["dim"]
    pose = state.get("pose")
    action = clips.get(pose) if pose in ACTION_CLIPS else None
    weight = min(1.0, float(state.get("pose_ticks", BLEND_TICKS)) / BLEND_TICKS) if action else 0.0

    def add(clip: dict, t: float, w: float) -> None:
        if w <= 0.0:
            return
        for pname, track in clip.get("tracks", {}).items():
            for channel, slot in (("rot", 0), ("pos", 1)):
                if channel in track:
                    value = sample(track[channel], t)
                    for i in range(3):
                        deltas[pname][slot][i] += value[i] * w

    held = clips.get("phase_%d" % int(state.get("phase", 0)), clips.get("idle"))
    if held:
        add(held, clip_time(held, age), (1.0 - walk) * (1.0 - weight))
        if "glow" in held:
            glow = sample(held["glow"], clip_time(held, age))
    if "move" in clips:
        cycle = (walk_pos * WALK_FREQUENCY) % (2.0 * math.pi) / (2.0 * math.pi)
        add(clips["move"], cycle * clips["move"]["length"], walk * (1.0 - weight))
    if action:
        t = clip_time(action, float(state.get("pose_ticks", 0.0)))
        add(action, t, weight)
        if "glow" in action:
            glow = glow + (sample(action["glow"], t) - glow) * weight
    hurt = state.get("hurt_ticks")
    if hurt is not None and "hurt" in clips:
        add(clips["hurt"], min(float(hurt), HURT_TICKS), 1.0)
        if "glow" in clips["hurt"]:
            glow = sample(clips["hurt"]["glow"], min(float(hurt), HURT_TICKS))
    return deltas, max(0.0, min(1.0, glow))


def main(argv: list) -> int:
    palette = Palette()
    ids = argv or all_ids()
    failed = 0
    for entity_id in ids:
        spec = load(entity_id)
        errors = validate(spec, palette, entity_id)
        boxes = sum(len(p.get("cubes", [])) for p in spec.get("parts", []))
        print("%s  %s: %d boxes, %d animated parts, %d clips" % (
            "FAIL" if errors else "ok  ", entity_id, boxes, len(animated_parts(spec)) if not errors else 0,
            len(spec.get("clips", {}))))
        for message in errors:
            print("      " + message)
        failed += bool(errors)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
