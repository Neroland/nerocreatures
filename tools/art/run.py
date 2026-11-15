#!/usr/bin/env python3
"""One entry point for the art kit.

    python tools/art/run.py            # rebuild the palette, pack new cubes, paint, write Radiant
    python tools/art/run.py --check    # change nothing; exit 1 if anything is stale or invalid

Order: palette, spec validation, UV packing, every ``paint/<habitat>.py``, then ``radiant.py``.
"""

from __future__ import annotations

import importlib
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "paint"))

import palette  # noqa: E402
import radiant  # noqa: E402
import spec as specs  # noqa: E402
import uvpack  # noqa: E402
from pixelkit import run_painters  # noqa: E402


def main(argv: list) -> int:
    flags = ["--check"] if "--check" in argv else []
    failed = palette.main(flags)
    failed |= specs.main([])
    failed |= uvpack.main(flags)
    painted = set()
    for name in sorted(os.listdir(os.path.join(HERE, "paint"))):
        if name.endswith(".py") and not name.startswith("_"):
            module = importlib.import_module(name[:-3])
            painters = getattr(module, "PAINTERS", {})
            painted.update(painters)
            failed |= run_painters(painters, flags)
    for entity_id in specs.all_ids():
        if entity_id not in painted:
            print("FAIL  %s has a spec but no painter" % entity_id)
            failed = 1
    failed |= radiant.main(flags)
    print("art kit: " + ("FAILED" if failed else "clean"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
