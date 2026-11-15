#!/usr/bin/env python3
"""Generate the mod-side wiring for every sound in tools/sound/manifest.json.

  python3 -B tools/sound/gen_sound_assets.py           write the three outputs
  python3 -B tools/sound/gen_sound_assets.py --check   fail if any output is stale

Outputs (all derived from the manifest, which synth.py writes):
  - assets/nerocreatures/sounds.json: one event per manifest row, its files, the subtitle key,
    volume (clamped to 1.0, the client's ceiling) and attenuation_distance = the row's range.
  - registry/ModSounds.java: one SoundEvent registration per row, as a CreatureSound constant
    carrying the row's pitch range. Fixed-range events, so the server sends a sound as far as
    the client can hear it.
  - lang/en_us.json: the English subtitle keys (appended; existing keys are never touched).

Standard library only, so CI can run --check.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MANIFEST = os.path.join(HERE, "manifest.json")
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "nerocreatures")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(ROOT, "common", "src", "main", "java", "za", "co", "neroland", "nerocreatures", "registry",
                    "ModSounds.java")


def events():
    with open(MANIFEST, encoding="utf-8") as f:
        return json.load(f)["events"]


def sounds_json(evs):
    doc = {}
    for e in evs:
        vol = round(min(1.0, e["volume"]), 2)
        doc[e["id"]] = {
            "subtitle": e["subtitle_key"],
            "sounds": [{"name": s["name"], "volume": vol, "attenuation_distance": e["range"]} for s in e["sounds"]],
        }
    return json.dumps(doc, indent=2) + "\n"


def const(eid):
    return eid.replace(".", "_").upper()


def java(evs):
    out = ["package za.co.neroland.nerocreatures.registry;", "",
           "import net.minecraft.core.registries.Registries;", "import net.minecraft.sounds.SoundEvent;", "",
           "import za.co.neroland.nerolandcore.registry.RegistrationProvider;", "",
           "import za.co.neroland.nerocreatures.NeroCreaturesCommon;",
           "import za.co.neroland.nerocreatures.sound.CreatureSound;", "",
           "/**",
           " * Every NeroCreatures sound event. <b>Generated</b> by {@code tools/sound/gen_sound_assets.py} from",
           " * {@code tools/sound/manifest.json}; do not edit by hand. All sounds are original and synthesised",
           " * ({@code tools/sound/synth.py}); subtitles live in {@code sounds.json} + {@code en_us.json}.",
           " */",
           "public final class ModSounds {", "",
           "    public static final RegistrationProvider<SoundEvent> SOUNDS =",
           "            RegistrationProvider.get(Registries.SOUND_EVENT, NeroCreaturesCommon.MOD_ID);", ""]
    owner = None
    for e in evs:
        if e["owner"] != owner:
            owner = e["owner"]
            out.append("    // %s (%s)" % (owner, e["owner_kind"]))
        out.append('    public static final CreatureSound %s = CreatureSound.register(SOUNDS, "%s", %d, %.2fF, %.2fF);'
                   % (const(e["id"]), e["id"], e["range"], e["pitch"][0], e["pitch"][1]))
    out += ["", "    private ModSounds() {", "    }", "",
            "    /** Forces this class to initialise (and therefore its events to register on Fabric). */",
            "    public static void init() {", "    }", "}", ""]
    return "\n".join(out)


def lang(evs, text):
    data = json.loads(text)
    missing = [e for e in evs if e["subtitle_key"] not in data]
    wrong = [e["subtitle_key"] for e in evs if e["subtitle_key"] in data and data[e["subtitle_key"]] != e["subtitle"]]
    if not missing:
        return text, wrong
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    rows = ",\n".join('  %s: %s' % (json.dumps(e["subtitle_key"]), json.dumps(e["subtitle"], ensure_ascii=False))
                      for e in missing)
    return body + ",\n\n" + rows + "\n}\n", wrong


def read(p):
    if not os.path.exists(p):
        return None
    with open(p, encoding="utf-8", newline="") as f:
        return f.read()


def write(p, text):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def main(argv):
    check = "--check" in argv
    evs = events()
    problems = []
    for path, want in ((SOUNDS_JSON, sounds_json(evs)), (JAVA, java(evs))):
        have = read(path)
        if have != want:
            if check:
                problems.append(os.path.relpath(path, ROOT) + " is stale (run gen_sound_assets.py)")
            else:
                write(path, want)
    text = read(LANG)
    new, wrong = lang(evs, text)
    problems += ["en_us.json: %s differs from the manifest subtitle" % k for k in wrong]
    if new != text:
        if check:
            problems.append("en_us.json lacks subtitle keys (run gen_sound_assets.py)")
        else:
            write(LANG, new)
    for p in problems:
        print("FAIL " + p)
    print("sound assets: %d events, %s" % (len(evs), "%d problem(s)" % len(problems) if problems else "ok"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
