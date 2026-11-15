#!/usr/bin/env python3
"""Asset checker: proves every registered NeroCreatures thing has its client assets.

    python3 -B tools/check_assets.py           # report (exit 1 on any gap)
    python3 -B tools/check_assets.py --check   # same; the form CI and the stage gate use

Ids are read from the registry classes themselves (``registry/Mod{Entities,Items,Blocks,SpawnEggs}``),
so a new registration without assets fails here before it reaches a client. Checks:

- **Items**: ``items/<id>.json`` (1.21.4+ item model definition) and every model it names; the
  ``models/item`` (or block) model that 1.21.1 reads; every texture those models use; a lang key
  (``item.`` or, for block items, ``block.``).
- **Blocks**: ``blockstates/<id>.json``, every model each variant names, their textures, ``block.`` lang.
- **Entities**: ``entity.`` lang; ``textures/entity/<id>.png``; a generated ``client/<Id>Model.java``;
  an art spec ``art/models/<id>.json`` unless listed in ``LEGACY_EXEMPT`` with a reason; and every
  synced pose the entity's code plays (``VisualPose.ABILITY``/``ABILITY_2``/``REST``) has a clip in its
  spec, counting the class and its own goal classes (``ability_2`` is allowed wherever a spec declares it).
- **Equipment**: every ``equipment/<id>.json`` layer texture exists.
- **Sounds**: every ``sounds.json`` event has its ``.ogg`` files and a ``subtitle`` key in ``en_us.json``,
  and every manifest event is in ``sounds.json``.

Standard library only.
"""
from __future__ import annotations

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "nerocreatures"
JAVA = os.path.join(ROOT, "common", "src", "main", "java", "za", "co", "neroland", NS)
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", NS)
SPECS = os.path.join(ROOT, "art", "models")

# Entities whose art predates the spec kit and is deliberately kept as is (id -> reason). Empty: all 38 have specs.
LEGACY_EXEMPT: dict = {}

POSE_CLIPS = {"ABILITY": "ability", "ABILITY_2": "ability_2", "REST": "rest"}


def read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def load_json(path: str):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def registry_ids(cls: str, pattern: str) -> list:
    return re.findall(pattern, read(os.path.join(JAVA, "registry", cls + ".java")))


def entity_classes() -> dict:
    """id -> entity class file, from ``ModEntities`` (``register("id", ... Builder.of(Cls::new``)."""
    text = read(os.path.join(JAVA, "registry", "ModEntities.java"))
    pairs = re.findall(r'register\(\s*"(\w+)",\s*key -> EntityType\.Builder\.of\((\w+)::new', text)
    files = {}
    for d, _, names in os.walk(os.path.join(JAVA, "entity")):
        for n in names:
            files.setdefault(n[:-5], os.path.join(d, n))
    return {eid: files.get(cls) for eid, cls in pairs}


def resource(ref: str, kind: str, ext: str) -> str:
    """``nerocreatures:item/x`` -> assets path; vanilla refs return ``None`` (always present)."""
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    if ns != NS:
        return None
    return os.path.join(ASSETS, kind, path + ext)


class Checker:
    def __init__(self) -> None:
        self.problems = []
        self.lang = load_json(os.path.join(ASSETS, "lang", "en_us.json"))
        self.counts = {}

    def fail(self, msg: str) -> None:
        self.problems.append(msg)

    def need_lang(self, key: str) -> None:
        if not self.lang.get(key, "").strip():
            self.fail("lang: missing %s" % key)

    def need_file(self, path, what: str) -> bool:
        if path is not None and not os.path.isfile(path):
            self.fail("%s: missing %s" % (what, os.path.relpath(path, ROOT).replace(os.sep, "/")))
            return False
        return True

    def model(self, ref: str, what: str, seen=None) -> None:
        """A block/item model: the file, its parent chain (ours) and every texture it names."""
        seen = seen if seen is not None else set()
        if ref in seen:
            return
        seen.add(ref)
        path = resource(ref, "models", ".json")
        if path is None or not self.need_file(path, what):
            return
        data = load_json(path)
        for tex in data.get("textures", {}).values():
            if not tex.startswith("#"):
                self.need_file(resource(tex, "textures", ".png"), what)
        if "parent" in data:
            self.model(data["parent"], what, seen)
        for override in data.get("overrides", []):
            self.model(override["model"], what, seen)

    def item_definition(self, node, what: str) -> None:
        """Walks a 1.21.4+ ``items/<id>.json`` tree and checks every model it can select."""
        if isinstance(node, dict):
            if node.get("type") in ("minecraft:model", "model") and "model" in node:
                self.model(node["model"], what)
            for value in node.values():
                if isinstance(value, (dict, list)):
                    self.item_definition(value, what)
        elif isinstance(node, list):
            for value in node:
                self.item_definition(value, what)

    # ------------------------------------------------------------------ sections

    def items(self, blocks: set) -> None:
        ids = registry_ids("ModItems", r'(?:ITEMS\.register|gear)\("(\w+)"')
        ids += registry_ids("ModSpawnEggs", r'egg\("(\w+)"')
        self.counts["items"] = len(ids)
        for iid in ids:
            what = "item %s" % iid
            definition = os.path.join(ASSETS, "items", iid + ".json")
            if self.need_file(definition, what):
                self.item_definition(load_json(definition), what)
            legacy = os.path.join(ASSETS, "models", "item", iid + ".json")
            if self.need_file(legacy, what + " (1.21.1 model)"):
                self.model("%s:item/%s" % (NS, iid), what)
            self.need_lang(("block.%s.%s" if iid in blocks else "item.%s.%s") % (NS, iid))

    def blocks(self) -> set:
        ids = registry_ids("ModBlocks", r'BLOCKS\.register\(\s*"(\w+)"')
        self.counts["blocks"] = len(ids)
        for bid in ids:
            what = "block %s" % bid
            state = os.path.join(ASSETS, "blockstates", bid + ".json")
            if self.need_file(state, what):
                data = load_json(state)
                variants = list(data.get("variants", {}).values())
                for part in data.get("multipart", []):
                    variants.append(part.get("apply"))
                for variant in variants:
                    for v in (variant if isinstance(variant, list) else [variant]):
                        self.model(v["model"], what)
            self.need_lang("block.%s.%s" % (NS, bid))
        return set(ids)

    def entities(self) -> None:
        classes = entity_classes()
        self.counts["entities"] = len(classes)
        models = os.path.join(JAVA, "client")
        for eid, src in classes.items():
            what = "entity %s" % eid
            self.need_lang("entity.%s.%s" % (NS, eid))
            self.need_file(os.path.join(ASSETS, "textures", "entity", eid + ".png"), what)
            camel = "".join(p.capitalize() for p in eid.split("_"))
            self.need_file(os.path.join(models, camel + "Model.java"), what)
            spec_path = os.path.join(SPECS, eid + ".json")
            if not os.path.isfile(spec_path):
                if eid not in LEGACY_EXEMPT:
                    self.fail("%s: no art spec art/models/%s.json and no legacy exemption" % (what, eid))
                continue
            clips = set(load_json(spec_path).get("clips", {}))
            if src is None:
                self.fail("%s: entity class not found" % what)
                continue
            # The entity's class plus its package-mates that name it (its goals, e.g. ShrikeLungeGoal).
            cls = os.path.basename(src)[:-5]
            code = read(src)
            folder = os.path.dirname(src)
            for name in sorted(os.listdir(folder)):
                other = os.path.join(folder, name)
                if other != src and name.endswith("Goal.java") and re.search(r"\b%s\b" % cls, read(other)):
                    code += read(other)
            for pose, clip in POSE_CLIPS.items():
                if re.search(r"(?:play|hold)Pose\(\s*VisualPose\.%s\b" % pose, code) and clip not in clips:
                    self.fail("%s: code plays %s but the spec has no '%s' clip" % (what, pose, clip))

    def equipment(self) -> None:
        folder = os.path.join(ASSETS, "equipment")
        names = sorted(n for n in os.listdir(folder) if n.endswith(".json")) if os.path.isdir(folder) else []
        self.counts["equipment"] = len(names)
        for name in names:
            for layer, entries in load_json(os.path.join(folder, name)).get("layers", {}).items():
                for entry in entries:
                    ns, _, path = entry["texture"].partition(":")
                    if ns == NS:
                        self.need_file(os.path.join(ASSETS, "textures", "entity", "equipment", layer, path + ".png"),
                                       "equipment %s" % name[:-5])

    def sounds(self) -> None:
        events = load_json(os.path.join(ASSETS, "sounds.json"))
        self.counts["sound events"] = len(events)
        for eid, event in events.items():
            what = "sound %s" % eid
            subtitle = event.get("subtitle")
            if not subtitle:
                self.fail("%s: no subtitle" % what)
            else:
                self.need_lang(subtitle)
            for sound in event.get("sounds", []):
                name = sound["name"] if isinstance(sound, dict) else sound
                ns, _, path = name.partition(":")
                if ns == NS:
                    self.need_file(os.path.join(ASSETS, "sounds", path + ".ogg"), what)
        manifest = os.path.join(ROOT, "tools", "sound", "manifest.json")
        if os.path.isfile(manifest):
            for row in load_json(manifest)["events"]:
                if row["id"] not in events:
                    self.fail("sound %s: in the manifest but not in sounds.json" % row["id"])


def main(argv: list) -> int:
    c = Checker()
    blocks = c.blocks()
    c.items(blocks)
    c.entities()
    c.equipment()
    c.sounds()
    for p in c.problems:
        print("FAIL " + p)
    summary = ", ".join("%d %s" % (n, k) for k, n in c.counts.items())
    print("asset check: %s; %s" % (summary, "%d problem(s)" % len(c.problems) if c.problems else "ok"))
    return 1 if c.problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
