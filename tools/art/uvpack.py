#!/usr/bin/env python3
"""Packs box UVs for a model spec and writes them back into the spec.

    python tools/art/uvpack.py <id> ...     # place any cube that has no "uv" yet
    python tools/art/uvpack.py --check      # every spec: all cubes placed, none overlap, file canonical
    python tools/art/uvpack.py --repack <id>  # throw the layout away and pack again (repaint needed)

Rectangles already in the spec are never moved, so adding a cube later does not disturb painted
texels. A part with ``uv_like`` gets its donor's rectangles copied in with ``mirror`` flipped; that
is the only overlap allowed. The spec is rewritten in the canonical layout.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelkit import Palette, box_uv_size  # noqa: E402
import spec as specs  # noqa: E402


def pack(spec: dict, repack: bool = False) -> list:
    """Places unplaced cubes. Returns a list of problems (empty when the layout is sound)."""
    width, height = spec["texture"]["width"], spec["texture"]["height"]
    used = [[None] * width for _ in range(height)]
    problems = []
    parts = specs.parts_by_name(spec)
    owners = [(part, cube) for part in spec["parts"] if not part.get("uv_like")
              for cube in part.get("cubes", [])]
    if repack:
        for _, cube in owners:
            cube.pop("uv", None)

    def claim(label: str, u: int, v: int, w: int, h: int) -> None:
        for y in range(v, v + h):
            for x in range(u, u + w):
                if not (0 <= x < width and 0 <= y < height):
                    problems.append(label + " runs off the sheet")
                    return
                if used[y][x] is not None:
                    problems.append("%s overlaps %s" % (label, used[y][x]))
                    return
        for y in range(v, v + h):
            for x in range(u, u + w):
                used[y][x] = label

    for part, cube in owners:
        if "uv" in cube:
            w, h = box_uv_size(cube["size"])
            claim(part["name"] + "." + cube["name"], cube["uv"][0], cube["uv"][1], w, h)

    todo = [(part, cube) for part, cube in owners if "uv" not in cube]
    todo.sort(key=lambda pc: (-box_uv_size(pc[1]["size"])[1], -box_uv_size(pc[1]["size"])[0],
                              pc[0]["name"], pc[1]["name"]))
    for part, cube in todo:
        w, h = box_uv_size(cube["size"])
        label = part["name"] + "." + cube["name"]
        spot = None
        for v in range(0, height - h + 1):
            for u in range(0, width - w + 1):
                if used[v][u] is None and used[v][u + w - 1] is None and all(
                        used[y][x] is None for y in range(v, v + h) for x in range(u, u + w)):
                    spot = (u, v)
                    break
            if spot:
                break
        if not spot:
            problems.append(label + " does not fit; the sheet is full")
            continue
        cube["uv"] = [spot[0], spot[1]]
        claim(label, spot[0], spot[1], w, h)

    for part in spec["parts"]:
        if part.get("uv_like"):
            donor = {cube["name"]: cube for cube in parts[part["uv_like"]].get("cubes", [])}
            for cube in part.get("cubes", []):
                if "uv" in donor[cube["name"]]:
                    cube["uv"] = list(donor[cube["name"]]["uv"])
                cube["mirror"] = not donor[cube["name"]].get("mirror", False)
    spec["uv_fill"] = round(sum(1 for row in used for cell in row if cell) / float(width * height), 3)
    return problems


def main(argv: list) -> int:
    check = "--check" in argv
    repack = "--repack" in argv
    ids = [a for a in argv if not a.startswith("--")] or specs.all_ids()
    palette = Palette()
    failed = 0
    for entity_id in ids:
        spec = specs.load(entity_id)
        errors = specs.validate(spec, palette, entity_id)
        if not errors:
            errors = pack(spec, repack and not check)
        text = specs.dump_json(spec) + "\n"
        with open(specs.spec_path(entity_id), "r", encoding="utf-8") as handle:
            current = handle.read().replace("\r\n", "\n")
        if check and not errors and current != text:
            errors = ["spec is not packed or not in canonical layout (run tools/art/uvpack.py %s)" % entity_id]
        if not check and not errors and current != text:
            specs.save_json(specs.spec_path(entity_id), spec)
        print("%s  %s  sheet %d%% used" % ("FAIL" if errors else "ok  ", entity_id,
                                          round(100 * spec.get("uv_fill", 0))))
        for message in errors:
            print("      " + message)
        failed += bool(errors)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
