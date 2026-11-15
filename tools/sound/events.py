"""The sound table: every event the mod ships, and the recipe behind it.

EVENTS is the single source for tools/sound/manifest.json. Ids follow
`<owner>.<event>` so subtitles land on `subtitles.nerocreatures.<owner>.<event>`.
"""
import abilities as A
import voices as V
from kit import GLASS, CRYSTAL, METAL, mix

ASSET_DIR = "common/src/main/resources/assets/nerocreatures/sounds"
EVENTS = []


def _event(owner, event, folder, fn, subtitle, category, use, volume=1.0, pitch=(0.95, 1.05), rng=16,
           variants=1, loop=False, habitat=None, kind="entity"):
    EVENTS.append({
        "id": "%s.%s" % (owner, event), "owner": owner, "owner_kind": kind, "event": event, "folder": folder,
        "fn": fn, "subtitle": subtitle, "category": category, "use": use, "volume": volume,
        "pitch": pitch, "range": rng, "variants": variants, "loop": loop, "habitat": habitat,
    })


def _voiced(hab, gen, kind, tint_gain=0.3):
    def fn(r, p):
        s = gen(r, p, kind)
        return mix(s, (V.tint(hab, r, p, len(s) / 44100.0), tint_gain))
    return fn


def creature(cid, name, hab, cat, gen, verb, size, abil, boss=False, amb_use="idle"):
    """size: S | M | L | XL sets loudness and hearing range. abil rows are
    (event, recipe, subtitle, use[, volume[, range]])."""
    vol = {"S": 0.7, "M": 0.9, "L": 1.0, "XL": 1.2}[size]
    rng = {"S": 16, "M": 16, "L": 24, "XL": 32}[size]
    folder = "boss" if boss else "entity"
    _event(cid, "ambient", folder, _voiced(hab, gen, "ambient"), "%s %s" % (name, verb), cat, amb_use,
           round(vol * 0.8, 2), (0.9, 1.1), rng, variants=2, habitat=hab)
    _event(cid, "hurt", folder, _voiced(hab, gen, "hurt", 0.2), "%s hurts" % name, cat, "hurt", vol, (0.9, 1.15), rng, habitat=hab)
    _event(cid, "death", folder, _voiced(hab, gen, "death"), "%s dies" % name, cat, "death", vol, (0.92, 1.05), rng, habitat=hab)
    for row in abil:
        ev, fn, sub, use = row[:4]
        v = row[4] if len(row) > 4 else vol
        g = row[5] if len(row) > 5 else rng
        _event(cid, ev, folder, fn, sub, cat, use, v, (0.95, 1.05), g, habitat=hab)


H, NEU = "hostile", "neutral"
an, gl, ins, me, el = V.animal, V.glassy, V.insect, V.mech, V.elemental

# ---- Luna: glassy and sparse
creature("lumen_moth", "Lumen Moth", "luna", NEU, lambda r, p, k: gl(r, p, k, 1320), "chimes softly", "S",
         [("pulse", A.moth_pulse, "Lumen Moth pulses", "reveal pulse (ability)")])
creature("echo_shrike", "Echo Shrike", "luna", H,
         lambda r, p, k: A.shrike_listen(r, p * 0.85) if k == "ambient" else an(r, p, k, 1100, [(2400, 5, 1), (3600, 7, .6)], .5, .1, "tri", rough_hz=55),
         "clicks", "S",
         [("screech", A.shrike_screech, "Echo Shrike screeches", "lunge on a heard target (ability)", 1.0, 24),
          ("listen", A.shrike_listen, "Echo Shrike listens", "searching after losing a sneaking player (ability_2)", 0.7)])
creature("lode_tortoise", "Lode Tortoise", "luna", NEU, lambda r, p, k: an(r, p, k, 85, [(300, 3, 1), (650, 4, .5)], .3, .15, dur=1.2),
         "grumbles", "M", [("graze", A.tortoise_graze, "Lode Tortoise grazes", "grazing a block to grow nodules (ability)")])
creature("lunar_stalker", "Lunar Stalker", "luna", H, lambda r, p, k: an(r, p, k, 150, [(500, 4, 1), (1150, 5, .5), (2400, 6, .2)], .5),
         "growls", "M", [("howl", A.stalker_howl, "Lunar Stalker howls", "pack recruiting broadcast (ability)", 1.2, 32)])

# ---- Mars: dry and granular
creature("dune_loper", "Dune Loper", "mars", NEU, lambda r, p, k: an(r, p, k, 240, [(700, 4, 1), (1500, 5, .5)], .25, .3),
         "chuffs", "L", [("leap", A.loper_leap, "Dune Loper leaps", "bounding leap (ability)")])
creature("strata_hound", "Strata Hound", "mars", NEU, lambda r, p, k: an(r, p, k, 300, [(800, 4, 1), (1700, 5, .6)], .3, .1),
         "whuffs", "M", [("sniff", A.hound_sniff, "Strata Hound sniffs", "tracking a shown block (ability)", 0.7),
                         ("point", A.hound_point, "Strata Hound points", "arrived at the tracked block (ability_2)", 1.0)])
creature("dust_devil", "Dust Devil", "mars", H, lambda r, p, k: el(r, p, k, 900, 1.5, 1.8), "whirls", "M",
         [("whirl", A.devil_whirl, "Dust Devil spins up", "invulnerable spin-up (ability)", 1.0, 24),
          ("throw", A.devil_throw, "Dust Devil hurls", "lift and throw (ability_2)", 1.0, 24)])

# ---- Cindara: low and crackling
creature("slag_beetle", "Slag Beetle", "cindara", NEU, lambda r, p, k: ins(r, p, k, 700, 32, 1600), "clicks", "S",
         [("eat", A.beetle_eat, "Slag Beetle eats", "eating a thrown raw ore (ability)"),
          ("digest", A.beetle_digest, "Slag Beetle digests", "digestion timer running; play on stage change"),
          ("cough", A.beetle_cough, "Slag Beetle coughs", "dropping the smelted result")])
creature("magma_wader", "Magma Wader", "cindara", H, lambda r, p, k: an(r, p, k, 70, [(250, 3, 1), (520, 3, .6)], .4, .1, rough_hz=14),
         "gurgles", "L", [("slag", A.wader_slag, "Magma Wader hurls slag", "slag throw (ability)", 1.0, 24),
                          ("slag_land", A.wader_slag_land, "Slag hardens", "slag projectile impact", 0.9, 16)])
creature("cinder_tyrant", "Cinder Tyrant", "cindara", H,
         lambda r, p, k: an(r, p, k, 48, [(200, 2, 1), (450, 3, .7), (1100, 4, .3)], .6, .2, dur=1.6, rough_hz=22), "rumbles", "XL",
         [("slam", A.tyrant_slam, "Cinder Tyrant slams", "ground slam impact (signature attack)", 1.4, 32),
          ("phase", A.tyrant_phase, "Cinder Tyrant rages", "phase change stinger", 1.5, 48),
          ("summon", A.tyrant_summon, "Cinder Tyrant rises", "altar summon", 1.5, 48)], boss=True)

# ---- Glacira: breathy with high chimes
creature("rime_yak", "Rime Yak", "glacira", NEU, lambda r, p, k: an(r, p, k, 120, [(400, 3, 1), (900, 4, .4)], .15, .35),
         "lows", "M", [("shiver", A.yak_shiver, "Rime Yak shivers", "sheared or milked")])
creature("frost_lurker", "Frost Lurker", "glacira", H, lambda r, p, k: ins(r, p, k, 500, 18, 3800, 5.0), "creaks", "M",
         [("erupt", A.lurker_erupt, "Frost Lurker erupts", "ambush eruption (ability)", 1.1, 24)])
creature("glacite_wisp", "Glacite Wisp", "glacira", NEU, lambda r, p, k: gl(r, p, k, 1760, CRYSTAL), "tinkles", "S",
         [("cushion", A.wisp_cushion, "Glacite Wisp cushions a fall", "fall cushion (ability)")])
creature("rime_matriarch", "Rime Matriarch", "glacira", H,
         lambda r, p, k: an(r, p, k, 330, [(800, 5, 1), (1150, 6, .6), (2900, 7, .3)], .1, .5, dur=1.5), "whispers", "XL",
         [("beam", A.matriarch_beam, "Rime Matriarch casts a blizzard", "blizzard beam (signature attack)", 1.4, 32),
          ("freeze", A.matriarch_freeze, "Ice spreads", "arena floor freeze stage", 1.2, 32),
          ("phase", A.matriarch_phase, "Rime Matriarch ascends", "phase change stinger", 1.5, 48),
          ("summon", A.matriarch_summon, "Rime Matriarch arrives", "altar summon", 1.5, 48)], boss=True)

# ---- Greenxertz: wet and plucked
creature("spore_drifter", "Spore Drifter", "greenxertz", NEU, lambda r, p, k: V.wet(r, p, k, 520), "burbles", "S",
         [("puff", A.drifter_puff, "Spore Drifter puffs", "growth tick lands on a crop (ability)", 0.6)])
creature("bloom_host", "Bloom Host", "greenxertz", H, lambda r, p, k: an(r, p, k, 95, [(350, 3, 1), (800, 4, .5)], .5, .2, rough_hz=9),
         "rasps", "M", [("raise", A.host_raise, "Bloom Host raises a sporeling", "raising a kill as a sporeling (ability)", 1.0, 24)])
creature("mossback", "Mossback", "greenxertz", NEU, lambda r, p, k: an(r, p, k, 130, [(450, 3, 1), (1000, 4, .5)], .2, .1),
         "grunts", "M", [("graze", A.mossback_graze, "Mossback grazes", "grazing and fertilising (ability)", 0.7)])
creature("xertz_forager", "Xertz Forager", "greenxertz", NEU, lambda r, p, k: an(r, p, k, 420, [(1100, 4, 1), (2300, 5, .5)], .3, .15),
         "snuffles", "S", [("dig", A.forager_dig, "Xertz Forager digs", "digging (ability)")])
creature("drift_leviathan", "Drift Leviathan", "greenxertz", NEU, lambda r, p, k: V.deep(r, p, k, 82), "hums", "XL",
         [("song", A.leviathan_song, "Drift Leviathan sings", "slow song pose (ability)", 1.5, 64)])
creature("bloom_sovereign", "Bloom Sovereign", "greenxertz", H,
         lambda r, p, k: an(r, p, k, 60, [(280, 3, 1), (600, 3, .6), (1500, 5, .2)], .5, .2, dur=1.6, rough_hz=7), "groans", "XL",
         [("infect", A.sovereign_infect, "Bloom Sovereign lashes", "vine whip that infects adds (signature attack)", 1.3, 32),
          ("heal", A.sovereign_heal, "Spore pods pulse", "healing from spore pods", 1.2, 32),
          ("phase", A.sovereign_phase, "Bloom Sovereign splits open", "phase change stinger", 1.5, 48),
          ("summon", A.sovereign_summon, "Bloom Sovereign takes root", "altar summon", 1.5, 48)], boss=True)

# ---- Nyxara: bell-like, beating overtones
creature("prism_stag", "Prism Stag", "nyxara", NEU, lambda r, p, k: an(r, p, k, 380, [(900, 4, 1), (2000, 6, .5)], .1, .1, "tri"),
         "calls", "M", [("shed", A.stag_shed, "Prism Stag sheds antlers", "antler shed (ability)"),
                        ("aura", A.stag_aura, "Prism Stag antlers ring", "night regeneration aura starts", 0.8, 24)])
creature("shardback", "Shardback", "nyxara", H, lambda r, p, k: an(r, p, k, 110, [(400, 3, 1), (1300, 5, .5)], .5, .2),
         "snorts", "M", [("deflect", A.shardback_deflect, "Shardback deflects", "projectile reflected from the front", 1.0, 24),
                         ("charge", A.shardback_charge, "Shardback charges", "charge telegraph, plates fold (ability)", 1.0, 24)])
creature("crystal_golem", "Crystal Golem", "nyxara", H, lambda r, p, k: gl(r, p, k, 220, CRYSTAL, 3.0, True), "resonates", "L",
         [("smash", A.golem_smash, "Crystal Golem smashes", "overhead smash (ability)", 1.1, 24)])
creature("facet_colossus", "Facet Colossus", "nyxara", H, lambda r, p, k: gl(r, p, k, 131, METAL, 2.0, True), "tolls", "XL",
         [("beam", A.colossus_beam, "Facet Colossus fires a beam", "20-tick telegraph then beam (signature attack)", 1.4, 32),
          ("rotate", A.colossus_rotate, "Facet Colossus turns", "vulnerable facet rotates", 1.2, 32),
          ("phase", A.colossus_phase, "Facet Colossus fractures", "phase change stinger", 1.5, 48),
          ("summon", A.colossus_summon, "Facet Colossus awakens", "altar summon", 1.5, 48)], boss=True)

# ---- Asteroid: metallic clicks
creature("magnet_crab", "Magnet Crab", "asteroid", NEU, lambda r, p, k: ins(r, p, k, 1400, 24, 2600), "clacks", "S",
         [("snap", A.crab_snap, "Magnet Crab snaps", "grabbing a metal item (ability)"),
          ("magnet", A.crab_magnet, "Magnet Crab hums", "tamed item pull active; replay every 2 s", 0.5)])
creature("gravity_leech", "Gravity Leech", "asteroid", H,
         lambda r, p, k: an(r, p, k, 520, [(1000, 3, 1), (2100, 4, .4)], .3, .1, "sin", rough_hz=11), "warbles", "S",
         [("latch", A.leech_latch, "Gravity Leech latches on", "tether attached (ability)", 1.0),
          ("detach", A.leech_detach, "Tether snaps", "tether broken", 1.0)])
creature("asteroid_worm", "Asteroid Worm", "asteroid", H,
         lambda r, p, k: an(r, p, k, 55, [(180, 2, 1), (420, 3, .6)], .7, .3, dur=1.4, rough_hz=16), "grinds", "L",
         [("emerge", A.worm_emerge, "Asteroid Worm emerges", "emerge telegraph and burst (ability)", 1.2, 32)])

# ---- Wrecks and stations: servo and static
creature("cache_mimic", "Cache Mimic", "wrecks", H, lambda r, p, k: ins(r, p, k, 350, 14, 900, 3.0), "chitters", "S",
         [("reveal", A.mimic_reveal, "Cache Mimic springs open", "reveal when opened (ability)", 1.0)], amb_use="idle while awake (dormant is silent)")
creature("static_swarm", "Static Swarm", "wrecks", H, lambda r, p, k: el(r, p, k, 3200, 3.0, 1.2, 0.9), "crackles", "M",
         [("drain", A.swarm_drain, "Static Swarm drains charge", "draining carried energy items (ability)", 1.0)])
creature("scrap_hulk", "Scrap Hulk", "wrecks", H, lambda r, p, k: me(r, p, k, 70, heavy=True), "clanks", "L",
         [("assemble", A.hulk_assemble, "Scrap Hulk assembles", "30-tick self-assembly (ability)", 1.3, 32),
          ("sweep", A.hulk_sweep, "Scrap Hulk sweeps", "big-arm sweep (ability_2)", 1.1, 24),
          ("shed", A.hulk_shed, "Scrap Hulk sheds a part", "health-quarter stage change", 1.0, 24)])
creature("rogue_drone", "Rogue Drone", "wrecks", H, lambda r, p, k: me(r, p, k, 220), "whirs", "S",
         [("leap", A.drone_leap, "Rogue Drone lunges", "leap (ability)")])
creature("rogue_android", "Rogue Android", "wrecks", H, lambda r, p, k: me(r, p, k, 120), "hums", "L",
         [("shield", A.android_shield, "Rogue Android raises shield", "shield plates lock (ability)")])
creature("terraforming_drone", "Terraforming Drone", "wrecks", NEU, lambda r, p, k: me(r, p, k, 330, 0.5), "chirps", "S",
         [("work", A.tfdrone_work, "Terraforming Drone works", "work cycle (ability)", 0.6)])

# ---- Pirates: masked voices and comm chirps
creature("space_pirate", "Space Pirate", "pirates", H, lambda r, p, k: V.human(r, p, k, 120), "breathes", "M",
         [("volley", A.pirate_volley, "Space Pirate fires", "one shot of the volley (ability)", 1.0, 24)])
creature("pirate_captain", "Pirate Captain", "pirates", H, lambda r, p, k: V.human(r, p * 0.82, k, 120), "breathes heavily", "M",
         [("dash", A.captain_dash, "Pirate Captain jet-dashes", "jet-dash (ability)", 1.1, 24),
          ("rally", A.captain_rally, "Pirate Captain rallies the crew", "rally (ability_2)", 1.2, 32)])

# ---- Deep space: sub-bass, slow sweeps
creature("void_crawler", "Void Crawler", "deep", H, lambda r, p, k: ins(r, p, k, 260, 20, 700, 2.5), "skitters", "S",
         [("blink", A.crawler_blink, "Void Crawler blinks", "blink strike (ability)")])
creature("plasma_slime", "Plasma Slime", "deep", H, lambda r, p, k: V.plasma(r, p, k, 180), "fizzes", "S",
         [("pulse", A.slime_pulse, "Plasma Slime pulses", "aura pulse (ability)")])
creature("void_manta", "Void Manta", "deep", NEU, lambda r, p, k: V.deep(r, p, k, 110, 0.8), "croons", "L",
         [("blink", A.manta_blink, "Void Manta blinks", "mount blink (ability)", 1.1, 32)])

# ---- Blocks and gear
B, P = "block", "player"
_event("incubator", "hatch", "block", A.incubator_hatch, "Egg hatches", B, "Incubator hatches an egg", 1.0, (0.95, 1.05), 16, kind="block")
_event("lure_beacon", "pulse", "block", A.lure_beacon_pulse, "Lure Beacon pulses", B, "Lure Beacon bait pulse", 0.6, (0.97, 1.03), 16, kind="block")
_event("sonic_ward", "hum", "block", A.sonic_ward_hum, "Sonic Ward hums", B, "Sonic Ward while fuelled; 3 s seamless loop", 0.4, (1.0, 1.0), 12, loop=True, kind="block")
_event("apex_altar", "summon", "block", A.altar_summon, "Apex Altar tolls", B, "Apex Altar accepts a sigil", 1.4, (1.0, 1.0), 48, kind="block")
_event("capture_pod", "capture", "item", A.pod_capture, "Capture Pod closes", P, "Capture Pod captures a creature", 0.9, (0.95, 1.05), 16, kind="item")
_event("capture_pod", "release", "item", A.pod_release, "Capture Pod opens", P, "Capture Pod releases a creature", 0.9, (0.95, 1.05), 16, kind="item")
_event("mirror_shield", "reflect", "item", A.shield_reflect, "Mirror Shield reflects", P, "Mirror Shield reflects a projectile", 1.0, (0.95, 1.1), 16, kind="item")
_event("blink_charm", "use", "item", A.blink_charm, "Blink Charm snaps", P, "Blink Charm teleport", 0.9, (0.95, 1.05), 16, kind="item")
_event("resonant_whistle", "use", "item", A.whistle, "Whistle calls", P, "Resonant Whistle recalls pets and mount", 1.0, (0.97, 1.03), 32, kind="item")
_event("static_jar", "shatter", "item", A.jar_shatter, "Static Jar shatters", P, "thrown Static Jar impact", 1.0, (0.95, 1.05), 16, kind="item")
_event("field_scanner", "scan", "item", A.scanner_scan, "Scanner completes", P, "Field Scanner finishes a scan", 0.7, (1.0, 1.0), 8, kind="item")
