#!/usr/bin/env python3
"""Radiant morph: a palette swap of the committed base texture.

    python tools/art/radiant.py [<id> ...]    # write <id>_radiant.png for specs with a "radiant" map
    python tools/art/radiant.py --check

A spec's ``radiant`` map names, for each of its material ramps, one of the habitat's pearl ramps
(``pearl_light``, ``pearl``, ``pearl_deep``). Every base texel is swapped step for step; accent
texels under the glow go to the shared white-gold ramp. Nothing else changes: same model, same UVs,
same glow texture (the renderer tints it). A texel that is not a ramp colour is an error, which is
also the proof that the base texture is palette-clean.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelkit import ENTITY_TEXTURES, Image, Palette, write_or_check  # noqa: E402
import spec as specs  # noqa: E402


def colour_map(spec: dict, palette: Palette, mapping: dict) -> dict:
    habitat = spec["habitat"]
    table = {}
    for source, target in mapping.items():
        for a, b in zip(palette.ramp(habitat, source), palette.ramp(habitat, target)):
            table[a] = b
    accents = [spec.get("accent", "accent")] + ([spec["accent_2"]] if spec.get("accent_2") else [])
    for name in accents:
        for a, b in zip(palette.ramp(habitat, name), palette.ramp(habitat, "radiant_accent")):
            table[a] = b
    return table


def remap(spec: dict, palette: Palette, base: Image, mapping: dict = None) -> Image:
    table = colour_map(spec, palette, mapping or spec["radiant"])
    out = Image(base.width, base.height)
    for y in range(base.height):
        for x in range(base.width):
            r, g, b, a = base.px[y][x]
            if a == 0:
                continue
            if (r, g, b) not in table:
                raise ValueError("%s: texel (%d, %d) #%02X%02X%02X is not a ramp colour of this spec"
                                 % (spec["id"], x, y, r, g, b))
            out.px[y][x] = table[(r, g, b)] + (a,)
    return out


def default_mapping(spec: dict) -> dict:
    """Main body to ``pearl``, second ramp to ``pearl_deep``, third to ``pearl_light``."""
    return dict(zip(spec["materials"], ("pearl", "pearl_deep", "pearl_light")))


def main(argv: list) -> int:
    check = "--check" in argv
    ids = [a for a in argv if not a.startswith("--")] or specs.all_ids()
    palette = Palette()
    failed = 0
    for entity_id in ids:
        spec = specs.load(entity_id)
        if not spec.get("radiant"):
            continue
        try:
            base = Image.load(os.path.join(ENTITY_TEXTURES, entity_id + ".png"))
            image = remap(spec, palette, base)
        except (OSError, ValueError) as exc:
            print("FAIL  %s: %s" % (entity_id, exc))
            failed += 1
            continue
        failed += not write_or_check(os.path.join(ENTITY_TEXTURES, entity_id + "_radiant.png"), image, check)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
