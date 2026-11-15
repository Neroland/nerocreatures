#!/usr/bin/env python3
"""Generate the Java entity models from the model specs.

    python tools/gen_entity_models.py              # write client/<Id>Model.java for every spec
    python tools/gen_entity_models.py <id> ...     # only these
    python tools/gen_entity_models.py --check      # write nothing; exit 1 if any model is stale
    python tools/gen_entity_models.py --adopt <id> # replace a hand-written model that has no markers

Reads ``art/models/<id>.json`` (format in ``art/models/README.md``) and writes, between the
``// gen:begin`` and ``// gen:end`` markers of ``client/<Id>Model.java``:

- ``ART``: the renderer settings (render type, scale, shadow) and which textures exist
  (``<id>_glow.png`` turns the glow layer on, ``<id>_radiant.png`` the Radiant switch);
- ``RIG``: the part tree, procedural ``swing``/``wave``/``look``, the clips and ``visible_when``
  groups, played by ``CreatureModel`` / ``CreatureRig`` (a port of ``tools/art/spec.py`` evaluate());
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

import spec as specs  # noqa: E402
from pixelkit import ENTITY_TEXTURES, REPO_ROOT, Palette  # noqa: E402

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


# --- emitters ----------------------------------------------------------------------------------

def emit_art(spec: dict) -> list:
    eid = spec["id"]
    renderer = spec.get("renderer", {})
    glow = os.path.exists(os.path.join(ENTITY_TEXTURES, eid + "_glow.png"))
    radiant = os.path.exists(os.path.join(ENTITY_TEXTURES, eid + "_radiant.png"))
    return [
        "/** Renderer settings and the textures present for this creature. */",
        "public static final CreatureArt ART = new CreatureArt(%s, %s, %s, %s, %s, %s);" % (
            jstr(eid), "true" if spec.get("render_type", "cutout") == "translucent" else "false",
            fl(renderer.get("scale", 1.0)), fl(renderer.get("shadow", 0.5)),
            "true" if glow else "false", "true" if radiant else "false"),
    ]


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
    for name, clip in spec.get("clips", {}).items():
        body.append(".clip(%s, %s, %s)" % (jstr(name), num(clip["length"]), "true" if clip.get("loop") else "false"))
        if "glow" in clip:
            body.append("    .glow(%s)" % keys3(clip["glow"]))
        for pname, track in clip.get("tracks", {}).items():
            for channel in ("rot", "pos"):
                if channel in track:
                    body.append("    .%s(%s, %s)" % (channel, jstr(pname), keys5(track[channel])))
    for part in spec["parts"]:
        when = part.get("visible_when")
        if not when:
            continue
        flags = when.get("flag", [])
        if isinstance(flags, str):
            flags = [flags]
        body.append(".visibleWhen(%s, %d, %d, %d, %d)" % (
            jstr(part["name"]), mask(when.get("stage", [])), mask(when.get("variant", [])),
            mask(when.get("form", [])), sum(FLAGS[f] for f in flags)))
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


def region(spec: dict) -> list:
    body = [BEGIN + " generated by tools/gen_entity_models.py from art/models/%s.json; edit the spec, not this"
            % spec["id"], ""]
    body += emit_art(spec) + [""] + emit_rig(spec) + [""] + emit_layer(spec) + ["", END]
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


def render(spec: dict, existing: str | None, adopt: bool) -> str:
    gen = "\n".join(region(spec))
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
            text = render(spec, existing, adopt)
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
        if "%s.ART" % class_name(entity_id) not in renderers:
            print("note  %s: not registered with its ART in ClientEntityRenderers yet" % entity_id)
    return failed


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
