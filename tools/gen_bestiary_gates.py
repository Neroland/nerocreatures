#!/usr/bin/env python3
"""Write the Bestiary's Neroland Core record gates (decision 002): two player-scope gates per species.

    python tools/gen_bestiary_gates.py           # write the gate files
    python tools/gen_bestiary_gates.py --check   # change nothing; exit 1 if a file is missing, stale or stray

For every species with an art spec (``art/models/<id>.json``, the 38 roster entities) this writes

- ``data/nerocreatures/neroland_gates/bestiary/scanned/<id>.json``: ``{"scope": "player", "title": "<Name> scanned"}``
- ``data/nerocreatures/neroland_gates/bestiary/studied/<id>.json``: the same, requiring the scanned gate.

Core maps the sub-folders to ``nerocreatures:bestiary/scanned/<id>`` ids. Names come from ``en_us.json``.
Standard library only; files are written with CRLF line endings like the rest of the repo's JSON.
"""
from __future__ import annotations

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "common", "src", "main", "resources")
GATES = os.path.join(RES, "data", "nerocreatures", "neroland_gates", "bestiary")
LANG = os.path.join(RES, "assets", "nerocreatures", "lang", "en_us.json")
SPECS = os.path.join(ROOT, "art", "models")


def species() -> list:
    return sorted(n[:-5] for n in os.listdir(SPECS) if n.endswith(".json"))


def render(obj: dict) -> bytes:
    return (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").replace("\n", "\r\n").encode("utf-8")


def wanted() -> dict:
    with open(LANG, encoding="utf-8") as handle:
        lang = json.load(handle)
    out = {}
    for sid in species():
        name = lang.get("entity.nerocreatures." + sid, sid.replace("_", " ").title())
        out[os.path.join("scanned", sid + ".json")] = render({"scope": "player", "title": name + " scanned"})
        out[os.path.join("studied", sid + ".json")] = render({
            "scope": "player",
            "requires": ["nerocreatures:bestiary/scanned/" + sid],
            "title": name + " studied",
        })
    return out


def main(argv: list) -> int:
    check = "--check" in argv
    files = wanted()
    failed = 0
    for rel, data in sorted(files.items()):
        path = os.path.join(GATES, rel)
        current = open(path, "rb").read() if os.path.isfile(path) else None
        if current == data:
            continue
        if check:
            print("FAIL  %s is %s" % (os.path.relpath(path, ROOT), "missing" if current is None else "stale"))
            failed += 1
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as handle:
                handle.write(data)
    for tier in ("scanned", "studied"):
        folder = os.path.join(GATES, tier)
        for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
            if os.path.join(tier, name) not in files:
                print("FAIL  stray gate %s" % os.path.relpath(os.path.join(folder, name), ROOT))
                failed += 1
    print("bestiary gates: %d files, %s" % (len(files), "FAILED" if failed else "ok"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
