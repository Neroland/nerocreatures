#!/usr/bin/env python3
"""Rebuilds the ramp steps in ``art/palette.json`` from each ramp's seed colour.

The seeds, hues and rules in the JSON are the source; the ``steps`` lists are derived, so a hue
change is one edit and a regenerate. Painters read only ``steps``.

    python tools/art/palette.py           # rewrite derived values
    python tools/art/palette.py --check   # exit 1 if the file is out of date

How a material ramp is derived from its seed (the mid step):

* each step toward shadow turns the hue ``hue_shift_deg_per_step`` degrees toward the habitat's
  shadow hue; each step toward light turns it the same amount toward the light hue. A step never
  overshoots its target, and when the target is nearly opposite, lights turn away from the shadow
  hue (and shadows away from the light hue) so the two ends never meet.
* saturation is highest at the mid step
* lightness steps are even, clamped so no ramp reaches black or white
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelkit import PALETTE_PATH, Palette, hex_to_rgb, hsl_to_rgb, lightness, rgb_to_hex, rgb_to_hsl  # noqa: E402
from spec import dump_json  # noqa: E402

SATURATION = (0.70, 0.86, 1.0, 0.86, 0.70)


def arc(a: float, b: float) -> float:
    """Signed shortest turn from hue ``a`` to hue ``b``."""
    return (b - a + 180.0) % 360.0 - 180.0


def turn(hue: float, target: float, avoid: float, degrees: float) -> float:
    gap = arc(hue, target)
    if abs(gap) > 150.0:
        direction = -1.0 if arc(hue, avoid) > 0 else 1.0
        return hue + direction * degrees
    if abs(gap) <= degrees:
        return target
    return hue + (degrees if gap > 0 else -degrees)


def material_ramp(seed: str, shadow_hue: float, light_hue: float, per_step: float) -> list:
    h, s, l = rgb_to_hsl(hex_to_rgb(seed))
    down = min(0.09, (l - 0.07) / 2.0)
    up = min(0.10, (0.93 - l) / 2.0)
    out = []
    for i in (-2, -1, 0, 1, 2):
        hue = h
        for _ in range(abs(i)):
            hue = turn(hue, shadow_hue, light_hue, per_step) if i < 0 else turn(hue, light_hue, shadow_hue, per_step)
        light = l + (i * down if i < 0 else i * up)
        out.append(rgb_to_hex(hsl_to_rgb(hue, s * SATURATION[i + 2], light)))
    out[2] = seed.upper()
    return out


def accent_ramp(seed: str, shadow_hue: float) -> list:
    """Accent ramps put the named colour at step 3; step 4 is the hot core, 0 to 2 are dim states."""
    h, s, l = rgb_to_hsl(hex_to_rgb(seed))
    out = []
    for factor, sat, turns in ((0.40, 0.70, 3), (0.58, 0.85, 2), (0.80, 0.95, 1)):
        hue = h
        for _ in range(turns):
            hue = turn(hue, shadow_hue, shadow_hue + 180.0, 6.0)
        out.append(rgb_to_hex(hsl_to_rgb(hue, s * sat, l * factor)))
    out.append(seed.upper())
    out.append(rgb_to_hex(hsl_to_rgb(h, s * 0.9, min(0.95, l + (1.0 - l) * 0.6))))
    return out


def rebuild(data: dict) -> dict:
    rules = data["rules"]
    per_step = rules["hue_shift_deg_per_step"]
    radiant = data["radiant"]
    radiant["accent_steps"] = accent_ramp(radiant["glow_tint"], 30.0)
    for habitat in data["habitats"].values():
        ground = lightness(hex_to_rgb(habitat["ground"]))
        habitat["ground_lightness"] = round(ground, 1)
        for ramp in habitat["ramps"].values():
            ramp["steps"] = material_ramp(ramp["seed"], habitat["shadow_hue"], habitat["light_hue"], per_step)
            contrast = abs(lightness(hex_to_rgb(ramp["seed"])) - ground)
            ramp["ground_contrast"] = round(contrast, 1)
            ramp["main_body"] = contrast >= rules["min_ground_contrast"]
        for accent in habitat["accents"].values():
            accent["steps"] = accent_ramp(accent["seed"], habitat["shadow_hue"])
        pearl_hue = habitat["hue"] + radiant["hue_turn_deg"]
        habitat["radiant_ramps"] = {}
        for name, light, sat in radiant["pearl_ramps"]:
            seed = rgb_to_hex(hsl_to_rgb(pearl_hue, sat, light))
            habitat["radiant_ramps"][name] = {
                "seed": seed,
                "steps": material_ramp(seed, pearl_hue - 40.0, 48.0, per_step),
            }
    return data


def main(argv: list) -> int:
    palette = Palette()
    text = dump_json(rebuild(palette.data)) + "\n"
    with open(PALETTE_PATH, "r", encoding="utf-8") as handle:
        current = handle.read().replace("\r\n", "\n")
    if "--check" in argv:
        if current != text:
            print("DRIFT art/palette.json (run tools/art/palette.py)")
            return 1
        print("ok    art/palette.json")
        return 0
    with open(PALETTE_PATH, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("wrote art/palette.json")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
