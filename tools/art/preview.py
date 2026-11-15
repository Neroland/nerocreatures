#!/usr/bin/env python3
"""Software preview: renders model specs and their textures to a contact sheet PNG.

    python tools/art/preview.py <id> [<id> ...] [--out sheet.png] [--untextured] [--scale 4]
                                [--radiant-demo]

Standard library only. It is an orthographic box rasteriser with a depth buffer that reads the same
spec, the same packed UVs and the same clip arithmetic (``spec.evaluate``) the game uses, so what the
sheet shows is what the generated model will show, bar the game's lighting.

Per entity the sheet has: front, side, three-quarter, rear three-quarter and top views; every
clip's key pose from the side and at three-quarter; glow-only, dim glow, full glow, alpha, Radiant
(carriers only; ``--radiant-demo`` shows the rule on any species without writing a file) and
untextured cells; the 16-block thumbnail in colour, in value only and as a black
silhouette; the texture sheets; and the ramps used. Lit cells sit on the habitat's ground colour so
the ground-contrast rule can be judged by eye.

The default output is ``build/art-preview/contact-<first id>.png``, which is not committed.
"""

from __future__ import annotations

import math
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelkit import (ENTITY_TEXTURES, FACES, REPO_ROOT, Image, Palette, face_rect, hex_to_rgb,  # noqa: E402
                      lightness, multiply)
import radiant  # noqa: E402
import spec as specs  # noqa: E402

# Texture-space corner of each face and the box axes its sheet u and v run along.
# (corner fx, fy, fz), (u axis, u sign), (v axis, v sign)
FACE_FRAMES = {
    "up": ((0, 0, 1), (0, 1), (2, -1)),
    "down": ((0, 1, 1), (0, 1), (2, -1)),
    "north": ((0, 0, 0), (0, 1), (1, 1)),
    "south": ((1, 0, 1), (0, -1), (1, 1)),
    "west": ((0, 0, 1), (2, -1), (1, 1)),
    "east": ((1, 0, 0), (2, 1), (1, 1)),
}
LIGHT = (0.30, 0.86, -0.42)  # world space, Y up, creature faces -Z
INK = (232, 232, 236, 255)
PAPER = (24, 24, 30, 255)

FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111",
    "M": "101111111101101", "N": "110101101101101", "O": "010101101101010", "P": "110101110100100",
    "Q": "010101101111011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101",
    "Y": "101101010010010", "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111",
    "2": "110001010100111", "3": "110001010001110", "4": "101101111001001", "5": "111100110001110",
    "6": "011100111101111", "7": "111001010010010", "8": "111101111101111", "9": "111101111001110",
    " ": "000000000000000", ".": "000000000000010", ":": "000010000010000", "/": "001001010100100",
    "%": "101001010100101", "-": "000000111000000", "(": "001010010010001", ")": "100010010010100",
    "+": "000010111010000", "_": "000000000000111", ",": "000000000010100", "=": "000111000111000",
}


def text(image: Image, x: int, y: int, message: str, colour=INK, scale: int = 2) -> None:
    for ch in message.upper():
        bits = FONT.get(ch, FONT["-"])
        for i, bit in enumerate(bits):
            if bit == "1":
                image.rect(x + (i % 3) * scale, y + (i // 3) * scale, scale, scale, colour)
        x += 4 * scale


# --- geometry --------------------------------------------------------------

def rotation(rx: float, ry: float, rz: float) -> list:
    """Rz * Ry * Rx in degrees: the order a model part applies its angles."""
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    return [
        [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
        [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
        [-sy, cy * sx, cy * cx],
    ]


def mat_mul(a: list, b: list) -> list:
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m: list, t: list, p) -> tuple:
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + t[i] for i in range(3))


def quads(spec: dict, deltas: dict) -> list:
    """Every cube face in model space: (corner, u edge, v edge, normal, sheet rect, part.cube, face)."""
    identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    frames = {}
    out = []
    for part in spec["parts"]:
        pm, pt = frames[part["parent"]] if part.get("parent") else (identity, [0, 0, 0])
        rot_d, pos_d = deltas.get(part["name"], ([0, 0, 0], [0, 0, 0]))
        base = part.get("rotation", [0, 0, 0])
        local = rotation(base[0] + rot_d[0], base[1] + rot_d[1], base[2] + rot_d[2])
        pivot = [part["pivot"][i] + pos_d[i] for i in range(3)]
        m = mat_mul(pm, local)
        t = list(apply(pm, pt, pivot))
        frames[part["name"]] = (m, t)
        for cube in specs.resolved_cubes(spec, part):
            if "uv" not in cube:
                continue
            grow = float(cube.get("inflate", 0))
            lo = [cube["origin"][i] - grow for i in range(3)]
            span = [cube["size"][i] + 2 * grow for i in range(3)]
            mirror = cube.get("mirror", False)
            for face in FACES:
                corner, (ua, us), (va, vs) = FACE_FRAMES[face]
                c = list(corner)
                du = [0, 0, 0]
                dv = [0, 0, 0]
                du[ua] = us
                dv[va] = vs
                if mirror:
                    c[0] = 1 - c[0]
                    du[0] = -du[0]
                    dv[0] = -dv[0]
                p0 = [lo[i] + c[i] * span[i] for i in range(3)]
                eu = [du[i] * span[i] for i in range(3)]
                ev = [dv[i] * span[i] for i in range(3)]
                centre = [lo[i] + span[i] / 2.0 for i in range(3)]
                mid = [p0[i] + (eu[i] + ev[i]) / 2.0 for i in range(3)]
                normal = [mid[i] - centre[i] for i in range(3)]
                length = math.sqrt(sum(v * v for v in normal)) or 1.0
                zero = [0, 0, 0]
                out.append((apply(m, t, p0), apply(m, zero, eu), apply(m, zero, ev),
                            apply(m, zero, [v / length for v in normal]),
                            face_rect(cube["uv"], cube["size"], face), part["name"] + "." + cube["name"], face))
    return out


def bounds(spec: dict) -> tuple:
    """Centre and radius of the rest pose, in model space."""
    rest = {part["name"]: ([0, 0, 0], [0, 0, 0]) for part in spec["parts"]}
    points = []
    for p0, eu, ev, _, _, _, _ in quads(spec, rest):
        for a, b in ((0, 0), (1, 0), (0, 1), (1, 1)):
            points.append(tuple(p0[i] + a * eu[i] + b * ev[i] for i in range(3)))
    lo = [min(p[i] for p in points) for i in range(3)]
    hi = [max(p[i] for p in points) for i in range(3)]
    centre = [(lo[i] + hi[i]) / 2.0 for i in range(3)]
    radius = max(math.sqrt(sum((p[i] - centre[i]) ** 2 for i in range(3))) for p in points)
    return centre, radius


# --- rasteriser ------------------------------------------------------------

def render(spec: dict, deltas: dict, size: int, scale: float, yaw: float, pitch: float, centre,
           base: Image = None, glow: Image = None, glow_level: float = 0.0, glow_tint=None,
           base_mult: float = 1.0, mode: str = "lit", flat=None, background=None, zoom: float = 1.0) -> Image:
    """One view. ``mode``: lit, glow (glow texels on a dark body), flat (one colour), plain (untextured)."""
    image = Image(size, size, background or (0, 0, 0, 0))
    depth = [[-1e9] * size for _ in range(size)]
    view = mat_mul(rotation(pitch, 0, 0), rotation(0, yaw, 0))
    scale = scale * zoom

    def to_view(p, point: bool):
        w = (-(p[0] - (centre[0] if point else 0)), -(p[1] - (centre[1] if point else 0)),
             p[2] - (centre[2] if point else 0))
        return apply(view, [0, 0, 0], w)

    half = size / 2.0
    for p0, eu, ev, normal, rect, _, face in quads(spec, deltas):
        n = to_view(normal, False)
        if n[2] <= 1e-6:
            continue
        a0, au, av = to_view(p0, True), to_view(eu, False), to_view(ev, False)
        ox, oy = half + a0[0] * scale, half - a0[1] * scale
        ux, uy = au[0] * scale, -au[1] * scale
        vx, vy = av[0] * scale, -av[1] * scale
        det = ux * vy - uy * vx
        if abs(det) < 1e-9:
            continue
        xs = (ox, ox + ux, ox + vx, ox + ux + vx)
        ys = (oy, oy + uy, oy + vy, oy + uy + vy)
        world_n = (-normal[0], -normal[1], normal[2])
        shade = 0.70 + 0.30 * max(0.0, sum(world_n[i] * LIGHT[i] for i in range(3)))
        rx, ry, rw, rh = rect
        for py in range(max(0, int(min(ys))), min(size, int(max(ys)) + 1)):
            for px in range(max(0, int(min(xs))), min(size, int(max(xs)) + 1)):
                dx, dy = px + 0.5 - ox, py + 0.5 - oy
                a = (dx * vy - dy * vx) / det
                b = (ux * dy - uy * dx) / det
                if a < 0 or a >= 1 or b < 0 or b >= 1:
                    continue
                z = a0[2] + a * au[2] + b * av[2]
                if z <= depth[py][px]:
                    continue
                tx, ty = rx + int(a * rw), ry + int(b * rh)
                if mode == "flat":
                    colour = flat
                elif mode == "plain":
                    colour = tuple(int(c * shade) for c in flat[face]) + (255,)
                else:
                    texel = base.get(tx, ty)
                    if texel[3] == 0:
                        continue
                    lit = glow.get(tx, ty) if glow else (0, 0, 0, 0)
                    if mode == "glow":
                        colour = (lit[0], lit[1], lit[2], 255) if lit[3] else (30, 30, 36, 255)
                    else:
                        rgb = [texel[i] * shade * base_mult for i in range(3)]
                        if lit[3] and glow_level > 0:
                            hot = multiply(lit, glow_tint) if glow_tint else lit
                            rgb = [rgb[i] + (hot[i] - rgb[i]) * glow_level for i in range(3)]
                        colour = (int(rgb[0]), int(rgb[1]), int(rgb[2]), 255)
                depth[py][px] = z
                image.px[py][px] = colour
    return image


# --- sheet -----------------------------------------------------------------

VIEWS = (("front", 180, 0), ("side", 90, 0), ("three-quarter", 140, 22), ("rear 3/4", 40, 22), ("top", 180, 89))
KEY_STATES = {
    "idle": lambda t: {"age": t},
    "move": lambda t: {"walk_speed": 1.0, "walk_pos": 2 * math.pi * t / 20.0 / specs.WALK_FREQUENCY, "age": t},
    "hurt": lambda t: {"hurt_ticks": t},
}


def state_for(spec: dict, clip_name: str) -> dict:
    clip = spec["clips"][clip_name]
    tick = clip.get("key_pose", clip["length"] // 2)
    if clip_name == "move":
        return {"walk_speed": 1.0, "age": tick,
                "walk_pos": 2 * math.pi * tick / clip["length"] / specs.WALK_FREQUENCY}
    if clip_name in KEY_STATES:
        return KEY_STATES[clip_name](tick)
    if clip_name.startswith("phase_"):
        return {"phase": int(clip_name[-1]), "age": tick}
    return {"pose": clip_name, "pose_ticks": tick, "age": tick}


def entity_block(spec: dict, palette: Palette, scale: int, untextured: bool, radiant_demo: bool = False) -> Image:
    sid = spec["id"]
    habitat = spec["habitat"]
    ground = palette.ground(habitat) + (255,)
    centre, radius = bounds(spec)
    cell = int(2 * radius * scale * 1.12) + 8
    label_h = 14
    rest, rest_glow = specs.evaluate(spec, palette, {})
    main = palette.ramp(habitat, spec["materials"][0])
    light = palette.rules["baked_light"]
    plain = {face: main[max(0, min(4, 2 + light[face]))] for face in FACES}
    base = glow = None
    if not untextured:
        base = Image.load(os.path.join(ENTITY_TEXTURES, sid + ".png"))
        glow = Image.load(os.path.join(ENTITY_TEXTURES, sid + "_glow.png"))
    dim = palette.rules["glow"]["intensity"]["dim"]

    def lit(deltas, yaw, pitch, level=dim, **extra) -> Image:
        if untextured:
            return render(spec, deltas, cell, scale, yaw, pitch, centre, mode="plain", flat=plain, background=ground)
        return render(spec, deltas, cell, scale, yaw, pitch, centre, base=extra.pop("base", base), glow=glow,
                      glow_level=level, background=extra.pop("background", ground), **extra)

    rows = []
    rows.append([(name, lit(rest, yaw, pitch)) for name, yaw, pitch in VIEWS])
    clip_names = [name for name in specs.CLIP_NAMES if name in spec["clips"]]
    for label, yaw, pitch in (("side", 90, 0), ("3/4", 140, 22)):
        row = []
        for name in clip_names:
            deltas, level = specs.evaluate(spec, palette, state_for(spec, name))
            tick = spec["clips"][name].get("key_pose", spec["clips"][name]["length"] // 2)
            row.append(("%s t=%d %s" % (name, tick, label), lit(deltas, yaw, pitch, level)))
        rows.append(row)
    if not untextured:
        alpha = palette.data["alpha"]
        variants = [
            ("glow only", render(spec, rest, cell, scale, 140, 22, centre, base=base, glow=glow, mode="glow",
                                 background=(10, 10, 14, 255))),
            ("glow dim %d%%" % round(dim * 100), lit(rest, 140, 22, dim, background=(40, 42, 50, 255))),
            ("glow full", lit(rest, 140, 22, 1.0, background=(40, 42, 50, 255))),
            ("alpha (x%.1f in game)" % alpha["scale"], lit(rest, 140, 22, 1.0, glow_tint=hex_to_rgb(alpha["glow_tint"]),
                                                           base_mult=alpha["base_multiply"])),
        ]
        radiant_path = os.path.join(ENTITY_TEXTURES, sid + "_radiant.png")
        pearl = hex_to_rgb(palette.data["radiant"]["glow_tint"])
        if spec.get("radiant") and os.path.exists(radiant_path):
            variants.append(("radiant", lit(rest, 140, 22, 0.6, base=Image.load(radiant_path), glow_tint=pearl)))
        elif radiant_demo:
            demo = radiant.remap(spec, palette, base, radiant.default_mapping(spec))
            variants.append(("radiant rule demo", lit(rest, 140, 22, 0.6, base=demo, glow_tint=pearl)))
        variants.append(("untextured", render(spec, rest, cell, scale, 140, 22, centre, mode="plain", flat=plain,
                                              background=ground)))
        rows.append(variants)

    columns = max(len(row) for row in rows)
    width = max(columns * (cell + 6) + 6, 640)
    thumb = int(2 * radius * 2 * 1.12) + 8
    tex = spec["texture"]
    strip_h = max(thumb, tex["height"] * 2) + label_h + 8
    height = 26 + len(rows) * (cell + label_h + 6) + strip_h + 6
    sheet = Image(width, height, PAPER)
    boxes = sum(len(p.get("cubes", [])) for p in spec["parts"])
    coverage = ""
    if glow is not None:
        painted = sum(1 for row in base.px for c in row if c[3])
        coverage = "  glow %.1f%% (cap %d%%)" % (
            100.0 * sum(1 for row in glow.px for c in row if c[3]) / max(1, painted),
            round(100 * palette.glow_cap(spec["size_class"])))
    text(sheet, 8, 6, "%s  %s  class %s  %d boxes  %d anim parts  %dx%d%s" % (
        sid, habitat, spec["size_class"], boxes, len(specs.animated_parts(spec)), tex["width"], tex["height"], coverage))
    y = 26
    for row in rows:
        x = 6
        for label, image in row:
            text(sheet, x + 2, y, label)
            sheet.blit(image, x, y + label_h)
            x += cell + 6
        y += cell + label_h + 6

    # 16-block thumbnails: 2 px per model unit is about what a 1080p screen shows at 16 blocks.
    x = 6
    text(sheet, x + 2, y, "at 16 blocks: colour / value / silhouette")
    small = (render(spec, rest, thumb, 2, 125, 12, centre, mode="plain", flat=plain, background=ground) if untextured
             else render(spec, rest, thumb, 2, 125, 12, centre, base=base, glow=glow, glow_level=dim, background=ground))
    sheet.blit(small, x, y + label_h)
    value = Image(thumb, thumb)
    for yy in range(thumb):
        for xx in range(thumb):
            v = int(lightness(small.px[yy][xx]) * 2.55)
            v = 40 if v < 85 else (128 if v < 150 else 215)
            value.px[yy][xx] = (v, v, v, 255)
    sheet.blit(value, x + thumb + 6, y + label_h)
    sheet.blit(render(spec, rest, thumb, 2, 125, 12, centre, mode="flat", flat=(12, 12, 16, 255), background=ground),
               x + 2 * (thumb + 6), y + label_h)
    x += 3 * (thumb + 6) + 10
    if not untextured:
        for label, image in (("base x2", base), ("glow x2", glow)):
            text(sheet, x + 2, y, label)
            sheet.rect(x, y + label_h, tex["width"] * 2, tex["height"] * 2, (60, 20, 60, 255) if image is base else (10, 10, 14, 255))
            sheet.blit(image, x, y + label_h, 2)
            x += tex["width"] * 2 + 8
    text(sheet, x + 2, y, "ramps")
    names = list(spec["materials"]) + [spec.get("accent", "accent")]
    for row_i, name in enumerate(names):
        for step, colour in enumerate(palette.ramp(habitat, name)):
            sheet.rect(x + step * 14, y + label_h + row_i * 16, 13, 13, colour + (255,))
        text(sheet, x + 5 * 14 + 4, y + label_h + row_i * 16 + 2, name)
    sheet.rect(x, y + label_h + len(names) * 16, 13, 13, ground)
    text(sheet, x + 18, y + label_h + len(names) * 16 + 2, "ground")
    return sheet


def main(argv: list) -> int:
    args = list(argv)
    out = None
    scale = 4
    if "--out" in args:
        out = args.pop(args.index("--out") + 1)
        args.remove("--out")
    if "--scale" in args:
        scale = int(args.pop(args.index("--scale") + 1))
        args.remove("--scale")
    untextured = "--untextured" in args
    ids = [a for a in args if not a.startswith("--")]
    if not ids:
        print(__doc__)
        return 2
    palette = Palette()
    blocks = [entity_block(specs.load(entity_id), palette, scale, untextured, "--radiant-demo" in args)
              for entity_id in ids]
    sheet = Image(max(b.width for b in blocks), sum(b.height for b in blocks), PAPER)
    y = 0
    for block in blocks:
        sheet.blit(block, 0, y)
        y += block.height
    out = out or os.path.join(REPO_ROOT, "build", "art-preview", "contact-%s.png" % ids[0])
    sheet.save(out)
    print("wrote %s (%dx%d)" % (out, sheet.width, sheet.height))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
