#!/usr/bin/env python3
"""Generate the Java entity models from the model specs.

    python tools/gen_entity_models.py              # write client/<Id>Model.java for every spec
    python tools/gen_entity_models.py <id> ...     # only these
    python tools/gen_entity_models.py --check      # write nothing; exit 1 if any model is stale
    python tools/gen_entity_models.py --adopt <id> # replace a hand-written model that has no markers

Reads ``art/models/<id>.json`` (format in ``art/models/README.md``) and writes, between the
``// gen:begin`` and ``// gen:end`` markers of ``client/<Id>Model.java``:

- ``ART``: the renderer settings (render type, scale, shadow), which textures exist
  (``<id>_glow.png`` turns the glow layer on, ``<id>_radiant.png`` the Radiant switch,
  ``<id>_v<n>.png`` a variant's own texture), the ``variants`` tints and the culling reach (measured
  from the geometry at rest and at every clip key, unless ``renderer.cull`` says otherwise);
- ``RIG``: the part tree, procedural ``swing``/``wave``/``look``/``spin``, the clips (``"blend":
  "add"`` tracks as ``rotAdd``/``posAdd``), ``visible_when`` groups and ``glow_rules``, played by
  ``CreatureModel`` / ``CreatureRig`` (a port of ``tools/art/spec.py`` evaluate());
- ``createBodyLayer()``: the geometry, with the packed UVs.

A missing model file is created from a template; code outside the markers is kept. The output only
uses ``MeshDefinition``, ``PartDefinition``, ``CubeListBuilder``, ``CubeDeformation`` and ``PartPose``,
which are the same on every Minecraft line, so it needs no version guards. Output is deterministic.
Standard library only. Run after the art kit (``tools/art/run.py``), since texture presence is read.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "art"))

import math  # noqa: E402

import spec as specs  # noqa: E402
from pixelkit import ENTITY_TEXTURES, REPO_ROOT, Palette  # noqa: E402
from preview import apply, mat_mul, rotation  # noqa: E402

CLIENT_DIR = os.path.join(REPO_ROOT, "common", "src", "main", "java", "za", "co", "neroland", "nerocreatures",
                          "client")
RENDERERS = os.path.join(CLIENT_DIR, "ClientEntityRenderers.java")
BEGIN = "// gen:begin"
END = "// gen:end"
FLAGS = {"saddled": 1, "panniers": 2, "radiant": 4, "alpha": 8}
GLOW_DIM = 0.35  # must equal CreatureRig.GLOW_DIM
INDENT = "    "


def class_name(entity_id: str) -> str:
    return "".join(word.capitalize() for word in entity_id.split("_")) + "Model"


def var_name(part: str) -> str:
    words = part.split("_")
    return words[0] + "".join(w.capitalize() for w in words[1:]) + "Part"


def num(value) -> str:
    """A number for a double context: integers bare, others as Python's shortest repr."""
    value = float(value)
    if value == int(value):
        return str(int(value))
    return repr(value)


def fl(value) -> str:
    """A Java float literal."""
    value = float(value)
    if value == int(value):
        return "%d.0F" % int(value)
    return repr(value) + "F"


def rad(value) -> str:
    value = float(value)
    if value == 0.0:
        return "0.0F"
    return "(float) Math.toRadians(%s)" % (num(value) if value != int(value) else "%d.0" % int(value))


def jstr(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def mask(values) -> int:
    out = 0
    for v in values:
        out |= 1 << int(v)
    return out


def condition(cond) -> str:
    """A ``CreatureRig.when(...)`` expression for a spec condition, or ``null``."""
    if not cond:
        return "null"
    flags = cond.get("flag", [])
    flags = [flags] if isinstance(flags, str) else flags
    return "CreatureRig.when(%d, %d, %d, %d, %d, %d)" % (
        mask(cond.get("stage", [])), mask(cond.get("variant", [])), mask(cond.get("form", [])),
        sum(FLAGS[f] for f in flags), mask(cond.get("phase", [])),
        mask(specs.POSE_NAMES.index(p) for p in cond.get("pose", [])))


def argb(colour: str, alpha: bool) -> str:
    return ("0xFF" if alpha else "0x") + colour[1:].upper()


# --- culling reach -----------------------------------------------------------------------------

def reach_states(spec: dict) -> list:
    """Rest, plus every clip at each of its key ticks (actions fully blended in)."""
    states = [{}]
    for name, clip in spec.get("clips", {}).items():
        ticks = sorted({0, clip["length"]} | {k["t"] for track in clip.get("tracks", {}).values()
                                              for channel, keys in track.items() if channel != "blend"
                                              for k in keys})
        for t in ticks:
            if name == "move":
                states.append({"walk_speed": 1.0, "age": t,
                               "walk_pos": 2 * math.pi * t / clip["length"] / specs.WALK_FREQUENCY})
            elif name == "hurt":
                states.append({"hurt_ticks": t})
            elif name.startswith("phase_"):
                states.append({"phase": int(name[-1]), "age": t})
            elif name in specs.ACTION_CLIPS:
                states.append({"pose": name, "pose_ticks": t, "from_pose": name, "from_ticks": t, "age": t})
            else:
                states.append({"age": t})
    return states


def measure_reach(spec: dict, palette: Palette) -> tuple:
    """(radius, up, down) in model units: the farthest any cube corner gets from the feet."""
    identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    radius, top, bottom = 0.0, 24.0, 24.0
    for state in reach_states(spec):
        deltas, _ = specs.evaluate(spec, palette, state)
        frames = {}
        for part in spec["parts"]:
            pm, pt = frames[part["parent"]] if part.get("parent") else (identity, [0, 0, 0])
            rot_d, pos_d = deltas[part["name"]]
            base = part.get("rotation", [0, 0, 0])
            m = mat_mul(pm, rotation(base[0] + rot_d[0], base[1] + rot_d[1], base[2] + rot_d[2]))
            t = list(apply(pm, pt, [part["pivot"][i] + pos_d[i] for i in range(3)]))
            frames[part["name"]] = (m, t)
            for cube in part.get("cubes", []):
                grow = float(cube.get("inflate", 0))
                lo = [cube["origin"][i] - grow for i in range(3)]
                hi = [cube["origin"][i] + cube["size"][i] + grow for i in range(3)]
                for corner in range(8):
                    p = [hi[i] if corner >> i & 1 else lo[i] for i in range(3)]
                    x, y, z = apply(m, t, p)
                    radius = max(radius, math.hypot(x, z))
                    top = min(top, y)
                    bottom = max(bottom, y)
    return radius, 24.0 - top, bottom - 24.0


def cull_box(spec: dict, palette: Palette) -> tuple:
    """Blocks, scaled by the renderer scale, with a 10% + 1/8 block margin (spins and waves), rounded up
    to 1/16; ``renderer.cull`` replaces any value it names."""
    scale = float(spec.get("renderer", {}).get("scale", 1.0))
    measured = measure_reach(spec, palette)
    out = []
    for value in measured:
        blocks = max(0.0, value) / 16.0 * scale
        out.append(math.ceil((blocks * 1.1 + 0.125) * 16.0 - 1e-9) / 16.0 if blocks > 0 else 0.0)
    override = spec.get("renderer", {}).get("cull", {})
    for i, key in enumerate(("radius", "up", "down")):
        if key in override:
            out[i] = float(override[key])
    return tuple(out)


# --- emitters ----------------------------------------------------------------------------------

def emit_art(spec: dict, palette: Palette) -> list:
    eid = spec["id"]
    renderer = spec.get("renderer", {})
    glow = os.path.exists(os.path.join(ENTITY_TEXTURES, eid + "_glow.png"))
    radiant = os.path.exists(os.path.join(ENTITY_TEXTURES, eid + "_radiant.png"))
    variants = spec.get("variants", {})
    if variants:
        body, lit, textures = [], [], 0
        for v in range(8):
            entry = variants.get(str(v), {})
            body.append(argb(entry["tint"], True) if "tint" in entry else "-1")
            lit.append(argb(entry["glow"], False) if "glow" in entry else "-1")
            stem = specs.variant_texture(spec, v)
            if stem and os.path.exists(os.path.join(ENTITY_TEXTURES, stem + ".png")):
                textures |= 1 << v
        looks = ["new CreatureArt.Variants(",
                 INDENT * 2 + "new int[] {%s}," % ", ".join(body),
                 INDENT * 2 + "new int[] {%s}," % ", ".join(lit),
                 INDENT * 2 + "0b%s)," % format(textures, "08b")]
    else:
        looks = ["CreatureArt.Variants.NONE,"]
    cull = cull_box(spec, palette)
    return [
        "/** Renderer settings, the textures present, the variant look and the culling reach. */",
        "public static final CreatureArt ART = new CreatureArt(%s, %s, %s, %s, %s, %s," % (
            jstr(eid), "true" if spec.get("render_type", "cutout") == "translucent" else "false",
            fl(renderer.get("scale", 1.0)), fl(renderer.get("shadow", 0.5)),
            "true" if glow else "false", "true" if radiant else "false"),
    ] + [INDENT * 2 + line for line in looks] + [
        INDENT * 2 + "new CreatureArt.Cull(%s, %s, %s));" % (fl(cull[0]), fl(cull[1]), fl(cull[2])),
    ]


def art_notes(spec: dict) -> list:
    out = []
    for v in range(8):
        stem = specs.variant_texture(spec, v)
        if stem and not os.path.exists(os.path.join(ENTITY_TEXTURES, stem + ".png")):
            out.append("variant %d declares texture but %s.png is missing (base texture used)" % (v, stem))
    return out


def keys5(keys: list) -> str:
    out = []
    for key in keys:
        v = key["v"]
        out.append("%s, %s, %s, %s, %d" % (num(key["t"]), num(v[0]), num(v[1]), num(v[2]),
                                           1 if key.get("ease") == "smooth" else 0))
    return ",  ".join(out)


def keys3(keys: list) -> str:
    return ",  ".join("%s, %s, %d" % (num(k["t"]), num(k["v"]), 1 if k.get("ease") == "smooth" else 0)
                      for k in keys)


def emit_rig(spec: dict) -> list:
    lines = ["/** Parts, procedural motion, clips and visibility groups (see CreatureRig). */",
             "public static final CreatureRig RIG = CreatureRig.builder()"]
    body = []
    for part in spec["parts"]:
        parent = part.get("parent")
        body.append(".part(%s, %s)" % (jstr(part["name"]), jstr(parent) if parent else "null"))
    proc = spec.get("procedural", {})
    for e in proc.get("swing", []):
        body.append(".swing(%s, %s, %s)" % (jstr(e["part"]), num(e.get("phase", 0)), num(e["amplitude"])))
    for e in proc.get("wave", []):
        body.append(".wave(%s, '%s', %s, %s, %s, %s)" % (
            jstr(e["part"]), e["axis"], num(e["frequency"]), num(e.get("phase", 0)), num(e["amplitude"]),
            "true" if e.get("fade_with_walk", True) else "false"))
    for e in proc.get("look", []):
        body.append(".look(%s, %s, %s, %s)" % (jstr(e["part"]), num(e.get("yaw", 0)), num(e.get("pitch", 0)),
                                               num(e.get("limit", 180))))
    for e in proc.get("spin", []):
        when = (", " + condition(e["when"])) if e.get("when") else ""
        if e.get("clock") == "game":
            # On AnimInput.gameTime instead of the entity's age (same angle on every client).
            when = (when or ", null") + ", true"
        body.append(".spin(%s, '%s', %s, %s%s)" % (jstr(e["part"]), e["axis"], num(e["speed"]),
                                                    num(e.get("phase", 0)), when))
    for name, clip in spec.get("clips", {}).items():
        body.append(".clip(%s, %s, %s)" % (jstr(name), num(clip["length"]), "true" if clip.get("loop") else "false"))
        if "glow" in clip:
            body.append("    .glow(%s)" % keys3(clip["glow"]))
        for pname, track in clip.get("tracks", {}).items():
            suffix = "Add" if track.get("blend") == "add" else ""
            for channel in ("rot", "pos"):
                if channel in track:
                    body.append("    .%s%s(%s, %s)" % (channel, suffix, jstr(pname), keys5(track[channel])))
    for part in spec["parts"]:
        when = part.get("visible_when")
        if not when:
            continue
        if "phase" in when or "pose" in when:
            body.append(".visibleWhen(%s, %s)" % (jstr(part["name"]), condition(when)))
            continue
        flags = when.get("flag", [])
        if isinstance(flags, str):
            flags = [flags]
        body.append(".visibleWhen(%s, %d, %d, %d, %d)" % (
            jstr(part["name"]), mask(when.get("stage", [])), mask(when.get("variant", [])),
            mask(when.get("form", [])), sum(FLAGS[f] for f in flags)))
    for rule in spec.get("glow_rules", []):
        body.append(".glowRule(%s, %s, %s, %d, %s)" % (
            condition(rule.get("when")), num(rule["min"]) if "min" in rule else "Double.NaN",
            num(rule["max"]) if "max" in rule else "Double.NaN", int(rule.get("flicker", 0)),
            num(rule.get("low", GLOW_DIM))))
    body.append(".build();")
    return lines + [INDENT * 2 + line for line in body]


def emit_layer(spec: dict) -> list:
    parts = spec["parts"]
    parents = {p.get("parent") for p in parts if p.get("parent")}
    out = ["/** The geometry; UVs are the packed ones in the spec. */",
           "public static LayerDefinition createBodyLayer() {",
           INDENT + "MeshDefinition mesh = new MeshDefinition();",
           INDENT + "PartDefinition root = mesh.getRoot();"]
    for part in parts:
        parent = var_name(part["parent"]) if part.get("parent") else "root"
        decl = ("PartDefinition %s = " % var_name(part["name"])) if part["name"] in parents else ""
        out.append(INDENT + "%s%s.addOrReplaceChild(%s, CubeListBuilder.create()" % (decl, parent, jstr(part["name"])))
        mirror = False
        for cube in specs.resolved_cubes(spec, part):
            uv = cube.get("uv", [0, 0])
            prefix = ""
            if bool(cube.get("mirror")) != mirror:
                mirror = bool(cube.get("mirror"))
                prefix = ".mirror(%s)" % ("true" if mirror else "false")
            o, s = cube["origin"], cube["size"]
            inflate = float(cube.get("inflate", 0.0))
            deform = "CubeDeformation.NONE" if inflate == 0.0 else "new CubeDeformation(%s)" % fl(inflate)
            out.append(INDENT * 3 + "%s.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, %s, %s)" % (
                prefix, uv[0], uv[1], jstr(cube["name"]), fl(o[0]), fl(o[1]), fl(o[2]),
                fl(s[0]), fl(s[1]), fl(s[2]), deform))
        out[-1] += ","
        p, r = part["pivot"], part.get("rotation", [0, 0, 0])
        if any(float(x) != 0.0 for x in r):
            pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (
                fl(p[0]), fl(p[1]), fl(p[2]), rad(r[0]), rad(r[1]), rad(r[2]))
        else:
            pose = "PartPose.offset(%s, %s, %s)" % (fl(p[0]), fl(p[1]), fl(p[2]))
        out.append(INDENT * 3 + pose + ");")
    out.append(INDENT + "return LayerDefinition.create(mesh, %d, %d);" % (
        spec["texture"]["width"], spec["texture"]["height"]))
    out.append("}")
    return out


def region(spec: dict, palette: Palette) -> list:
    body = [BEGIN + " generated by tools/gen_entity_models.py from art/models/%s.json; edit the spec, not this"
            % spec["id"], ""]
    body += emit_art(spec, palette) + [""] + emit_rig(spec) + [""] + emit_layer(spec) + ["", END]
    return [(INDENT + line) if line else "" for line in body]


TEMPLATE = """package za.co.neroland.nerocreatures.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * %(name)s: %(silhouette)s.
 *
 * <p>Generated from {@code art/models/%(id)s.json} by {@code tools/gen_entity_models.py}; the block
 * between the markers is rewritten on every run. Motion, clips and visibility are data in {@link #RIG},
 * played by {@link CreatureModel}.
 */
public class %(cls)s extends CreatureModel {

    public %(cls)s(ModelPart root) {
        super(root, RIG);
    }

%(region)s
}
"""


def render(spec: dict, existing: str | None, adopt: bool, palette: Palette) -> str:
    gen = "\n".join(region(spec, palette))
    if existing is None or (adopt and BEGIN not in existing):
        return TEMPLATE % {"name": spec.get("name", spec["id"]), "silhouette": spec.get("silhouette", ""),
                           "id": spec["id"], "cls": class_name(spec["id"]), "region": gen}
    text = existing.replace("\r\n", "\n")
    start = text.find(INDENT + BEGIN)
    end = text.find(END, start)
    if start < 0 or end < 0:
        raise ValueError("no %s / %s markers (pass --adopt <id> to replace the hand-written model)" % (BEGIN, END))
    end += len(END)
    return text[:start] + gen + text[end:]


def main(argv: list) -> int:
    check = "--check" in argv
    adopt = "--adopt" in argv
    ids = [a for a in argv if not a.startswith("--")] or specs.all_ids()
    palette = Palette()
    if abs(palette.rules["glow"]["intensity"]["dim"] - GLOW_DIM) > 1e-12:
        print("FAIL  palette glow dim changed; update GLOW_DIM here and CreatureRig.GLOW_DIM")
        return 1
    renderers = open(RENDERERS, encoding="utf-8").read() if os.path.exists(RENDERERS) else ""
    failed = 0
    for entity_id in ids:
        spec = specs.load(entity_id)
        errors = specs.validate(spec, palette, entity_id)
        if errors:
            print("FAIL  %s: invalid spec" % entity_id)
            for message in errors:
                print("      " + message)
            failed = 1
            continue
        path = os.path.join(CLIENT_DIR, class_name(entity_id) + ".java")
        existing = None
        if os.path.exists(path):
            with open(path, "rb") as handle:
                existing = handle.read().decode("utf-8")
        try:
            text = render(spec, existing, adopt, palette)
        except ValueError as err:
            print("FAIL  %s: %s" % (entity_id, err))
            failed = 1
            continue
        crlf = existing is not None and "\r\n" in existing
        data = (text.replace("\n", "\r\n") if crlf else text).encode("utf-8")
        current = existing.encode("utf-8") if existing is not None else None
        if data == current:
            print("ok    %s" % entity_id)
        elif check:
            print("STALE %s: run python tools/gen_entity_models.py %s" % (entity_id, entity_id))
            failed = 1
        else:
            with open(path, "wb") as handle:
                handle.write(data)
            print("wrote %s" % os.path.relpath(path, REPO_ROOT).replace(os.sep, "/"))
        for message in art_notes(spec):
            print("note  %s: %s" % (entity_id, message))
        if "%s.ART" % class_name(entity_id) not in renderers:
            print("note  %s: not registered with its ART in ClientEntityRenderers yet" % entity_id)
    return failed


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
