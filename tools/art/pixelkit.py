#!/usr/bin/env python3
"""Shared pixel kit for the NeroCreatures art pipeline.

Standard library only. Everything a painter needs lives here:

* ``Image``    - a plain RGBA raster with a PNG encoder and decoder
* ``Palette``  - the named ramps from ``art/palette.json``
* ``Painter``  - one entity's base sheet and glow sheet, addressed by cube face
* ``Face``     - a single cube face; every drawing call takes a ramp name and a step, never a
  raw colour, so a texture cannot contain an off-palette texel

Coordinates on a face are face-local texels, ``(0, 0)`` at the top-left of the face as it sits in
the sheet. For the four side faces "up" on the sheet is up on the creature. See
``art/models/README.md`` for which way each face runs.

Baked light is applied for you: a step painted on the ``up`` face comes out one step lighter, on
``east`` and ``west`` one step darker, on ``down`` two steps darker. Pass ``raw=True`` to skip it.
"""

from __future__ import annotations

import colorsys
import json
import os
import struct
import sys
import zlib

sys.dont_write_bytecode = True

TOOLS_ART = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(TOOLS_ART))
ART_DIR = os.path.join(REPO_ROOT, "art")
MODELS_DIR = os.path.join(ART_DIR, "models")
PALETTE_PATH = os.path.join(ART_DIR, "palette.json")
ENTITY_TEXTURES = os.path.join(
    REPO_ROOT, "common", "src", "main", "resources", "assets", "nerocreatures", "textures", "entity"
)

CLEAR = (0, 0, 0, 0)
FACES = ("up", "down", "north", "south", "west", "east")


# --- colour ----------------------------------------------------------------

def hex_to_rgb(text: str) -> tuple:
    text = text.lstrip("#")
    return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16))


def rgb_to_hex(rgb) -> str:
    return "#%02X%02X%02X" % (rgb[0], rgb[1], rgb[2])


def rgb_to_hsl(rgb) -> tuple:
    """Returns (hue 0-360, saturation 0-1, lightness 0-1)."""
    h, l, s = colorsys.rgb_to_hls(rgb[0] / 255.0, rgb[1] / 255.0, rgb[2] / 255.0)
    return (h * 360.0, s, l)


def hsl_to_rgb(h: float, s: float, l: float) -> tuple:
    r, g, b = colorsys.hls_to_rgb((h % 360.0) / 360.0, min(1.0, max(0.0, l)), min(1.0, max(0.0, s)))
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))


def lightness(rgb) -> float:
    """Perceived lightness 0-100 (Rec. 601 luma), the scale the contrast rule uses."""
    return (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 2.55


def multiply(rgb, tint, strength: float = 1.0) -> tuple:
    return tuple(int(round(rgb[i] * (1 - strength + strength * tint[i] / 255.0))) for i in range(3))


# --- image -----------------------------------------------------------------

class Image:
    """RGBA raster. ``px[y][x]`` is an ``(r, g, b, a)`` tuple."""

    def __init__(self, width: int, height: int, fill=CLEAR) -> None:
        self.width = width
        self.height = height
        self.px = [[fill] * width for _ in range(height)]

    def get(self, x: int, y: int):
        if 0 <= x < self.width and 0 <= y < self.height:
            return self.px[y][x]
        return CLEAR

    def set(self, x: int, y: int, colour) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            self.px[y][x] = colour

    def rect(self, x: int, y: int, w: int, h: int, colour) -> None:
        for yy in range(max(0, y), min(self.height, y + h)):
            row = self.px[yy]
            for xx in range(max(0, x), min(self.width, x + w)):
                row[xx] = colour

    def blit(self, other: "Image", x: int, y: int, scale: int = 1) -> None:
        """Alpha-tested copy (a texel is either copied or skipped), optionally scaled up."""
        for sy in range(other.height):
            for sx in range(other.width):
                colour = other.px[sy][sx]
                if colour[3] == 0:
                    continue
                if scale == 1:
                    self.set(x + sx, y + sy, colour)
                else:
                    self.rect(x + sx * scale, y + sy * scale, scale, scale, colour)

    def to_png(self) -> bytes:
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for colour in row:
                raw += bytes(colour)

        def chunk(tag: bytes, data: bytes) -> bytes:
            return (struct.pack(">I", len(data)) + tag + data
                    + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

        header = struct.pack(">2I5B", self.width, self.height, 8, 6, 0, 0, 0)
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as handle:
            handle.write(self.to_png())

    @staticmethod
    def load(path: str) -> "Image":
        """Reads an 8-bit RGBA or RGB, non-interlaced PNG (all this pipeline ever writes)."""
        with open(path, "rb") as handle:
            data = handle.read()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError("not a PNG: " + path)
        pos, idat, width, height, channels = 8, b"", 0, 0, 4
        while pos < len(data):
            length, tag = struct.unpack(">I4s", data[pos:pos + 8])
            body = data[pos + 8:pos + 8 + length]
            pos += 12 + length
            if tag == b"IHDR":
                width, height, depth, ctype, _, _, interlace = struct.unpack(">2I5B", body)
                if depth != 8 or ctype not in (2, 6) or interlace:
                    raise ValueError("unsupported PNG layout: " + path)
                channels = 4 if ctype == 6 else 3
            elif tag == b"IDAT":
                idat += body
        raw = zlib.decompress(idat)
        stride = width * channels
        image = Image(width, height)
        prev = bytearray(stride)
        offset = 0
        for y in range(height):
            ftype = raw[offset]
            line = bytearray(raw[offset + 1:offset + 1 + stride])
            offset += 1 + stride
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                if ftype == 1:
                    line[i] = (line[i] + a) & 255
                elif ftype == 2:
                    line[i] = (line[i] + b) & 255
                elif ftype == 3:
                    line[i] = (line[i] + ((a + b) >> 1)) & 255
                elif ftype == 4:
                    p = a + b - c
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
            for x in range(width):
                p = line[x * channels:(x + 1) * channels]
                image.px[y][x] = (p[0], p[1], p[2], p[3] if channels == 4 else 255)
            prev = line
        return image


# --- palette ---------------------------------------------------------------

class Palette:
    """``art/palette.json``. Ramps are five RGB steps, 0 deep shadow to 4 highlight."""

    def __init__(self, path: str = PALETTE_PATH) -> None:
        with open(path, "r", encoding="utf-8") as handle:
            self.data = json.load(handle)
        self.rules = self.data["rules"]

    def habitat(self, name: str) -> dict:
        return self.data["habitats"][name]

    def ramp(self, habitat: str, name: str) -> list:
        """A material ramp, the habitat ``accent`` ramp, or one of its Radiant ramps."""
        hab = self.habitat(habitat)
        for table in ("ramps", "radiant_ramps"):
            if name in hab[table]:
                return [hex_to_rgb(c) for c in hab[table][name]["steps"]]
        if name in hab["accents"]:
            return [hex_to_rgb(c) for c in hab["accents"][name]["steps"]]
        if name == "radiant_accent":
            return [hex_to_rgb(c) for c in self.data["radiant"]["accent_steps"]]
        raise KeyError("no ramp '%s' in habitat '%s'" % (name, habitat))

    def ground(self, habitat: str) -> tuple:
        return hex_to_rgb(self.habitat(habitat)["ground"])

    def glow_cap(self, size_class: str) -> float:
        return self.rules["glow"]["coverage_cap"][size_class]


# --- UV layout -------------------------------------------------------------

def box_uv_size(size) -> tuple:
    """Sheet footprint of one box in the standard unwrap."""
    w, h, d = size
    return (2 * d + 2 * w, d + h)


def face_rect(uv, size, face: str) -> tuple:
    """``(x, y, w, h)`` of one face in the sheet for a box whose unwrap starts at ``uv``."""
    u, v = uv
    w, h, d = size
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }[face]


# --- painter ---------------------------------------------------------------

class Face:
    """One cube face. All coordinates are face-local texels."""

    def __init__(self, painter: "Painter", rect, shift: int, label: str, flip_x: bool = False,
                 flip_y: bool = False) -> None:
        self.painter = painter
        self.x0, self.y0, self.w, self.h = rect
        self.shift = shift
        self.label = label
        self.flip_x = flip_x
        self.flip_y = flip_y

    def _at(self, x: int, y: int) -> tuple:
        return (self.x0 + (self.w - 1 - x if self.flip_x else x), self.y0 + (self.h - 1 - y if self.flip_y else y))

    def _colour(self, ramp: str, step: int, raw: bool):
        steps = self.painter.ramp(ramp)
        index = step if raw else step + self.shift
        return steps[max(0, min(len(steps) - 1, index))] + (255,)

    def px(self, x: int, y: int, ramp: str, step: int, raw: bool = False) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            sx, sy = self._at(x, y)
            self.painter.base.set(sx, sy, self._colour(ramp, step, raw))

    def rect(self, x: int, y: int, w: int, h: int, ramp: str, step: int, raw: bool = False) -> None:
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.px(xx, yy, ramp, step, raw)

    def fill(self, ramp: str, step: int, raw: bool = False) -> None:
        self.rect(0, 0, self.w, self.h, ramp, step, raw)

    def row(self, y: int, ramp: str, step: int, raw: bool = False) -> None:
        """A full-width row; negative ``y`` counts from the bottom edge."""
        self.rect(0, y % self.h, self.w, 1, ramp, step, raw)

    def col(self, x: int, ramp: str, step: int, raw: bool = False) -> None:
        """A full-height column; negative ``x`` counts from the right edge."""
        self.rect(x % self.w, 0, 1, self.h, ramp, step, raw)

    def bands(self, ramp: str, steps, vertical: bool = False, raw: bool = False) -> None:
        """Splits the face into equal bands, one ramp step each (top to bottom, or left to right)."""
        span = self.w if vertical else self.h
        for i in range(span):
            step = steps[min(len(steps) - 1, i * len(steps) // span)]
            if vertical:
                self.col(i, ramp, step, raw)
            else:
                self.row(i, ramp, step, raw)

    def dither(self, x: int, y: int, w: int, h: int, ramp: str, step: int, cluster: int = 2,
               raw: bool = False) -> None:
        """A checker of ``cluster``-sized blocks. Clusters under 2 texels are rejected (no speckle)."""
        if cluster < 2:
            raise ValueError("dither clusters must be 2 texels or more")
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if ((xx // cluster) + (yy // cluster)) % 2 == 0:
                    self.px(xx, yy, ramp, step, raw)

    def glow(self, x: int, y: int, w: int = 1, h: int = 1, step: int = 3, under: int = 1,
             accent: str = None) -> None:
        """Marks accent texels: accent ``step`` on the glow sheet, a dim accent ``under`` on the base.

        The dim base texel is what shows when the glow layer is at low intensity, so the organ never
        vanishes. Glow is never baked-light shifted.
        """
        accent = accent or self.painter.accent
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if 0 <= xx < self.w and 0 <= yy < self.h:
                    sx, sy = self._at(xx, yy)
                    self.painter.base.set(sx, sy, self._colour(accent, under, True))
                    self.painter.glow.set(sx, sy, self._colour(accent, step, True))


class _Turned:
    """A top or underside face addressed as (distance from front, across), to match a flank."""

    def __init__(self, face: Face) -> None:
        self.face = face
        self.w, self.h = face.h, face.w

    def rect(self, x: int, y: int, w: int, h: int, ramp: str, step: int, raw: bool = False) -> None:
        self.face.rect(y, x, h, w, ramp, step, raw)

    def col(self, x: int, ramp: str, step: int, raw: bool = False) -> None:
        self.face.row(x, ramp, step, raw)


class Painter:
    """The base and glow sheets for one entity.

    ``face("part.cube", "north")`` returns the face to draw on. Cubes that mirror another part's UVs
    (``uv_like``) are painted through their source; asking for one is an error, which keeps the
    left/right pair from being painted twice.
    """

    def __init__(self, spec: dict, palette: Palette) -> None:
        self.spec = spec
        self.palette = palette
        self.habitat = spec["habitat"]
        self.accent = spec.get("accent", "accent")
        self.allowed = list(spec["materials"]) + [self.accent] + (
            [spec["accent_2"]] if spec.get("accent_2") else [])
        tex = spec["texture"]
        self.base = Image(tex["width"], tex["height"])
        self.glow = Image(tex["width"], tex["height"])
        self.light = palette.rules["baked_light"]
        self._cubes = {}
        for part in spec["parts"]:
            for cube in part.get("cubes", []):
                self._cubes[part["name"] + "." + cube["name"]] = (part, cube)

    def ramp(self, name: str) -> list:
        if name not in self.allowed:
            raise KeyError("%s: ramp '%s' is not in this spec's materials %s"
                           % (self.spec["id"], name, self.allowed))
        return self.palette.ramp(self.habitat, name)

    def cubes(self, include_mirrors: bool = False) -> list:
        """``part.cube`` keys in spec order."""
        return [key for key, (part, _) in self._cubes.items()
                if include_mirrors or not part.get("uv_like")]

    def face(self, key: str, face: str, flip_x: bool = False, flip_y: bool = False) -> Face:
        part, cube = self._cubes[key]
        if part.get("uv_like"):
            raise KeyError("%s shares UVs with '%s'; paint that part instead" % (key, part["uv_like"]))
        if "uv" not in cube:
            raise KeyError("%s has no UV yet; run uvpack.py first" % key)
        return Face(self, face_rect(cube["uv"], cube["size"], face), self.light[face], key + ":" + face,
                    flip_x, flip_y)

    def flanks(self, key: str) -> list:
        """Both side faces (east, west) with x = 0 at the creature's front, so one loop paints both."""
        return [self.face(key, "east"), self.face(key, "west", flip_x=True)]

    def top(self, key: str) -> Face:
        """The up face with y = 0 at the creature's front."""
        return self.face(key, "up", flip_y=True)

    def under(self, key: str) -> Face:
        """The down face with y = 0 at the creature's front."""
        return self.face(key, "down", flip_y=True)

    def around(self, key: str) -> list:
        """The four faces that wrap the long axis of a tail or limb lying along z: both flanks, top
        and underside turned so that x = 0 is the front on every one of them."""
        return self.flanks(key) + [_Turned(self.top(key)), _Turned(self.under(key))]

    def box(self, key: str, ramp: str, step: int) -> None:
        """Fills all six faces of one cube with a ramp step (baked light applied per face)."""
        for name in FACES:
            self.face(key, name).fill(ramp, step)

    def each_face(self, key: str, faces=FACES):
        return [self.face(key, name) for name in faces]

    def coverage(self) -> float:
        """Glow texels as a share of painted texels."""
        painted = sum(1 for row in self.base.px for c in row if c[3])
        lit = sum(1 for row in self.glow.px for c in row if c[3])
        return lit / painted if painted else 0.0

    def unpainted(self) -> list:
        """Faces with any transparent texel left, for cutout species that should have none."""
        holes = []
        for key in self.cubes():
            _, cube = self._cubes[key]
            for name in FACES:
                x0, y0, w, h = face_rect(cube["uv"], cube["size"], name)
                if any(self.base.px[y][x][3] == 0 for y in range(y0, y0 + h) for x in range(x0, x0 + w)):
                    holes.append(key + ":" + name)
        return holes


def write_or_check(path: str, image: Image, check: bool) -> bool:
    """Writes ``image`` to ``path``, or with ``check`` reports whether the file already matches."""
    data = image.to_png()
    if check:
        try:
            with open(path, "rb") as handle:
                same = handle.read() == data
        except OSError:
            same = False
        if not same:
            print("DRIFT " + os.path.relpath(path, REPO_ROOT))
        return same
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(data)
    print("wrote " + os.path.relpath(path, REPO_ROOT))
    return True


def run_painters(painters: dict, argv: list) -> int:
    """Entry point for a ``paint/<habitat>.py`` file.

    ``painters`` maps entity id to a function taking a ``Painter``. Each entity's spec is validated,
    painted, checked (no unpainted face on cutout species, glow within the cap), and written as
    ``<id>.png`` and ``<id>_glow.png``. With ``--check`` nothing is written and any difference from
    the committed files is an error. Ids on the command line limit the run.
    """
    import spec as specs

    check = "--check" in argv
    wanted = [a for a in argv if not a.startswith("--")]
    palette = Palette()
    failed = 0
    for entity_id, paint in painters.items():
        if wanted and entity_id not in wanted:
            continue
        spec = specs.load(entity_id)
        errors = specs.validate(spec, palette, entity_id)
        if errors:
            print("FAIL  %s: spec invalid: %s" % (entity_id, "; ".join(errors)))
            failed += 1
            continue
        painter = Painter(spec, palette)
        paint(painter)
        problems = []
        if spec.get("render_type", "cutout") == "cutout":
            holes = painter.unpainted()
            if holes:
                problems.append("unpainted faces: " + ", ".join(holes[:6]) + (" ..." if len(holes) > 6 else ""))
        coverage = painter.coverage()
        cap = palette.glow_cap(spec["size_class"])
        if coverage > cap and entity_id not in palette.rules["glow"]["coverage_exempt"]:
            problems.append("glow covers %.1f%%, cap is %.0f%%" % (coverage * 100, cap * 100))
        if coverage == 0:
            problems.append("no glow texels; every creature has a glow layer")
        for message in problems:
            print("FAIL  %s: %s" % (entity_id, message))
        ok = not problems
        if ok:
            ok = write_or_check(os.path.join(ENTITY_TEXTURES, entity_id + ".png"), painter.base, check)
            ok = write_or_check(os.path.join(ENTITY_TEXTURES, entity_id + "_glow.png"), painter.glow, check) and ok
            if ok:
                print("ok    %s  glow %.1f%% of %.0f%% cap" % (entity_id, coverage * 100, cap * 100))
        failed += not ok
    return 1 if failed else 0
