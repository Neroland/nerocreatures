"""Hand-written recipes: creature abilities, boss stingers, blocks and gear.

Every function takes (r, p): a seeded generator and a pitch factor, and
returns a list of samples built only from the kit's oscillators and noise.
"""
from kit import *
from voices import animal, E


def _sweep(r, d, f0, f1, q=1.5, peak=0.5, k=1.2):
    return mul(wind(d, r, E([(0, f0), (1, f1)], d, True), q), swell(d, peak, k))


def _thump(f0, f1, d, k=5.0):
    return mul(osc("sin", E([(0, f0), (1, f1)], d, True), d), perc(d, 0.003, k))


def _burst(r, d, fc, k=5.0, mode="lp"):
    n = noise(d, r)
    return mul(lp(n, fc) if mode == "lp" else hp(n, fc), perc(d, 0.002, k))


def _glide(f0, f1, d, shape="sin", k=3.0):
    return mul(osc(shape, E([(0, f0), (1, f1)], d, True), d), perc(d, 0.004, k))


def _chord(fs, d, src="saw"):
    return mix(*[(osc(src, f, d), 1.0 / len(fs)) for f in fs])


# ------------------------------------------------------------------- Luna

def moth_pulse(r, p):
    rise = mul(osc("sin", E([(0, 520 * p), (1, 1560 * p)], 0.5, True), 0.5), swell(0.5, 0.85, 2.0))
    shimmer = mul(osc("sin", E([(0, 1040 * p), (1, 3120 * p)], 0.5, True), 0.5), mul(swell(0.5, 0.85, 2.0), lfo(24, 0.8, 0.5)))
    return reverb(lay((0, rise), (0, shimmer, 0.4), (0.45, bell(1760 * p, 0.8, GLASS, 6.0)), (0.47, bell(2637 * p, 0.6, GLASS, 7.0), 0.5)), 0.3)


def shrike_screech(r, p):
    d = 0.7
    f = E([(0, 2100 * p), (0.25, 3300 * p), (0.8, 2900 * p), (1, 1700 * p)], d, True)
    s = mul(fm(f, 1.41, 3.2, d), mul(lfo(68, 0.55, d), E([(0, 0), (0.04, 1), (0.8, 0.8), (1, 0)], d)))
    return echo(hp(mix(s, (mul(hp(noise(d, r), 4000), perc(d, 0.01, 3.0)), 0.3)), 900), 0.13, 0.35, 3)


def shrike_listen(r, p):
    c = echo(click(2600 * p, 0.025, 2.9, 3.0), 0.11, 0.5, 4)
    return lay((0, c), (0.26, c, 0.8), (0.75, bell(2093 * p, 0.6, GLASS, 7.0), 0.35))


def tortoise_graze(r, p):
    items = [(t, mul(crackle(0.14, r, 900, 700 * p, 2.0), perc(0.14, 0.004, 3.0))) for t in (0.0, 0.22, 0.45, 0.66)]
    grind = mul(lp(noise(0.9, r), 420), mul(lfo(18, 0.8, 0.9), swell(0.9, 0.5, 0.8)))
    return lay(*items, (0, grind, 0.9), (0.5, bell(1975 * p, 0.4, GLASS, 8.0), 0.2))


def stalker_howl(r, p):
    d = 1.8
    f = mul(E([(0, 215 * p), (0.2, 430 * p), (0.6, 410 * p), (1, 290 * p)], d, True), lfo(5.5, 0.012, d))
    v = voice(f, d, [(520, 4, 1.0), (1100, 5, 0.5), (2350, 6, 0.2)], r, breath=0.06, rough=0.15)
    return reverb(mul(v, E([(0, 0), (0.1, 1), (0.7, 0.8), (1, 0)], d)), 0.35, 1.3, 0.5)


# ------------------------------------------------------------------- Mars

def loper_leap(r, p):
    kick = mul(crackle(0.12, r, 1500, 2200, 1.5), perc(0.12, 0.002, 4.0))
    return lay((0, kick), (0, _thump(130 * p, 55 * p, 0.14), 0.9), (0.05, _sweep(r, 0.6, 450 * p, 2600 * p, 1.6, 0.45), 0.9))


def hound_sniff(r, p):
    return lay(*[(t, _sweep(r, 0.1, 1700 * p, 3100 * p, 1.2, 0.6, 1.0), g) for t, g in ((0, 0.8), (0.16, 1.0), (0.31, 0.9))])


def hound_point(r, p):
    bands = [(820, 4, 1.0), (1750, 5, 0.6)]
    bark = animal(r, p, "hurt", 290, bands, 0.3, 0.1, dur=0.5)
    ping = mul(osc("tri", 1480 * p, 0.25), perc(0.25, 0.003, 6.0))
    return lay((0, bark), (0.2, bark, 0.9), (0.42, ping, 0.5), (0.42, mul(crackle(0.2, r, 500, 3200, 2.5), perc(0.2, 0.003, 4.0)), 0.4))


def devil_whirl(r, p):
    d = 1.6
    am = [0.55 + 0.45 * v for v in osc("sin", E([(0, 4), (1, 19)], d, True), d)]
    w = mul(wind(d, r, E([(0, 300 * p), (1, 2500 * p)], d, True), 2.2), mul(am, E([(0, 0), (0.85, 1), (1, 0.7)], d)))
    return fade(mix(w, (mul(crackle(d, r, 60, 3200, 2.5, 900), E([(0, 0.2), (1, 1)], d)), 0.7)), 0.004, 0.08)


def devil_throw(r, p):
    return lay((0, mul(wind(0.5, r, E([(0, 2300 * p), (1, 450 * p)], 0.5, True), 1.4), perc(0.5, 0.004, 3.5))),
               (0, _thump(95 * p, 42 * p, 0.3), 0.9), (0.02, mul(crackle(0.4, r, 800, 2800, 2.0, 50), perc(0.4, 0.003, 3.0)), 0.6))


# ---------------------------------------------------------------- Cindara

def beetle_eat(r, p):
    return lay(*[(t, mul(crackle(0.09, r, 2400, 1800 * p, 2.0), perc(0.09, 0.002, 3.0))) for t in (0, 0.13, 0.27)],
               (0, mul(osc("sin", 62, 0.4), swell(0.4, 0.3, 1.0)), 0.5))


def beetle_digest(r, p):
    items, t = [], 0.0
    while t < 1.25:
        f = (110 + r.random() * 190) * p
        items.append((t, _glide(f, f * 1.9, 0.07, k=2.5), 0.5 + 0.5 * r.random()))
        t += 0.05 + r.random() * 0.14
    bed = mul(lp(noise(1.4, r), 260), mul(lfo(11, 0.6, 1.4), swell(1.4, 0.5, 0.8)))
    return lay(*items, (0, bed, 1.2), (0, mul(crackle(1.4, r, 90, 900, 2.0), swell(1.4, 0.6, 1.0)), 0.6))


def beetle_cough(r, p):
    c = mul(svf(noise(0.12, r), 950 * p, 2.0), perc(0.12, 0.003, 4.0))
    return lay((0, c), (0.15, c, 1.0), (0.15, _thump(160 * p, 70 * p, 0.1), 0.6), (0.3, bell(2400 * p, 0.35, METAL, 9.0), 0.5),
               (0.3, mul(crackle(0.3, r, 300, 1200, 2.0), perc(0.3, 0.003, 3.0)), 0.4))


def wader_slag(r, p):
    d = 0.6
    f = E([(0, 52 * p), (1, 118 * p)], d, True)
    sw = mul(voice(f, d, [(240, 3, 1.0), (520, 3, 0.6)], r, breath=0.1, rough=0.5, rough_hz=13), swell(d, 0.9, 1.5))
    hurl = mul(lp(noise(0.4, r), E([(0, 3200), (1, 300)], 0.4, True)), perc(0.4, 0.004, 3.5))
    return lay((0, sw), (0.55, hurl, 0.9), (0.55, _thump(120 * p, 50 * p, 0.2), 0.7))


def wader_slag_land(r, p):
    hiss = mul(hp(noise(1.0, r), 4200), perc(1.0, 0.004, 4.0))
    return lay((0, _burst(r, 0.18, 650 * p, 4.0)), (0, _thump(100 * p, 45 * p, 0.2), 0.8), (0.03, hiss, 0.6),
               (0.1, mul(crackle(0.9, r, 220, 1100, 2.0, 15), perc(0.9, 0.01, 2.0)), 0.7))


def tyrant_slam(r, p):
    boom = drive(mul(osc("sin", E([(0, 115 * p), (1, 31 * p)], 0.9, True), 0.9), perc(0.9, 0.002, 4.0)), 2.2)
    return lay((0, boom), (0, _burst(r, 0.5, 950, 4.0), 0.8), (0.05, mul(crackle(1.3, r, 500, 800, 1.6, 20), perc(1.3, 0.01, 2.5)), 0.8),
               (0.02, mul(lp(noise(1.0, r), 180), perc(1.0, 0.01, 3.0)), 0.9))


def tyrant_phase(r, p):
    d = 1.5
    brass = lp(_chord([55 * p, 82.4 * p, 110.6 * p, 164.2 * p], d), E([(0, 200), (0.6, 2600), (1, 600)], d, True))
    roar = animal(r, p, "death", 62, [(210, 2, 1.0), (460, 3, 0.7), (1100, 4, 0.3)], 0.7, 0.3, dur=1.2, rough_hz=21)
    return lay((0, mul(brass, E([(0, 0), (0.5, 1), (0.8, 0.9), (1, 0)], d))), (0.25, roar, 0.9),
               (0, mul(crackle(d, r, 40, 900, 2.0, 700), E([(0, 0.1), (0.7, 1), (1, 0)], d)), 0.7))


def tyrant_summon(r, p):
    d = 1.5
    rum = mul(lp(noise(d, r), E([(0, 90), (1, 500)], d, True)), mul(lfo(15, 0.6, d), E([(0, 0), (1, 1)], d)))
    roar = animal(r, p, "ambient", 58, [(210, 2, 1.0), (460, 3, 0.7), (1100, 4, 0.3)], 0.7, 0.3, dur=1.4, rough_hz=21)
    return lay((0, rum), (0, mul(crackle(d, r, 30, 900, 2.0, 500), E([(0, 0), (1, 1)], d)), 0.6), (1.4, tyrant_slam(r, p), 0.9), (1.45, roar, 0.9))


# ---------------------------------------------------------------- Glacira

def yak_shiver(r, p):
    rustle = mul(wind(0.5, r, 3000 * p, 0.8), mul(lfo(14, 0.8, 0.5), swell(0.5, 0.3, 1.0)))
    grunt = animal(r, p, "hurt", 105, [(400, 3, 1.0), (900, 4, 0.4)], 0.15, 0.35, dur=0.7)
    return lay((0, rustle), (0.08, grunt, 0.6), (0.3, bell(3136 * p, 0.4, GLASS, 8.0), 0.25))


def lurker_erupt(r, p):
    shards = [(0.05 + r.random() * 0.35, bell((2200 + r.random() * 2600) * p, 0.35, GLASS, 9.0), 0.3 + 0.4 * r.random()) for _ in range(7)]
    return lay((0, _burst(r, 0.4, 2500, 4.0, "hp")), (0, _thump(140 * p, 45 * p, 0.35), 1.0), (0, click(3200 * p, 0.03), 0.8),
               (0.03, click(2500 * p, 0.03), 0.6), (0.02, _sweep(r, 0.5, 600, 3500, 1.2, 0.2), 0.6), *shards)


def wisp_cushion(r, p):
    notes = (1.0, 1.25, 1.5, 2.0, 2.5)
    arp = [(i * 0.07, bell(1320 * p * n, 0.6, CRYSTAL, 6.0), 0.7) for i, n in enumerate(notes)]
    return reverb(lay(*arp, (0, _sweep(r, 0.6, 3500, 1800, 0.9, 0.3), 0.35)), 0.3)


def matriarch_beam(r, p):
    d = 2.2
    e = E([(0, 0), (0.12, 1), (0.85, 0.9), (1, 0)], d)
    bl = mul(wind(d, r, mul(E([(0, 1400), (0.3, 4200), (1, 3600)], d, True), lfo(3.1, 0.15, d)), 1.1), e)
    wh = mul(osc("sin", mul(E([(0, 2400 * p), (0.2, 3300 * p), (1, 3100 * p)], d, True), lfo(6, 0.01, d)), d), mul(e, 0.35))
    ch = [(0.3 + i * 0.37, bell((2637, 3136, 3520, 2794, 3951)[i] * p, 0.5, GLASS, 7.0), 0.3) for i in range(5)]
    return lay((0, bl), (0, wh), *ch)


def matriarch_freeze(r, p):
    d = 1.2
    creep = mul(crackle(d, r, 40, 4200, 5.0, 1400), E([(0, 0.2), (0.9, 1), (1, 0)], d))
    gl = mul(osc("sin", E([(0, 4200 * p), (1, 1500 * p)], d, True), d), mul(swell(d, 0.7, 1.0), 0.25))
    return lay((0, creep), (0, gl), (1.15, click(2900 * p, 0.04, 2.3, 5.0)), (1.15, bell(1568 * p, 0.6, GLASS, 6.0), 0.7), (1.15, _thump(160, 60, 0.2), 0.6))


def _choir(r, fs, d):
    out = []
    for f in fs:
        v = voice(mul(lfo(5.0 + r.random(), 0.008, d), f), d, [(800, 5, 1.0), (1150, 6, 0.6), (2900, 7, 0.3)], r, breath=0.5)
        out.append((v, 1.0 / len(fs)))
    return mul(mix(*out), E([(0, 0), (0.25, 1), (0.7, 0.9), (1, 0)], d))


def matriarch_phase(r, p):
    return reverb(lay((0, _choir(r, [392 * p, 466.2 * p, 587.3 * p], 1.5)), (0, bell(2349 * p, 0.8, GLASS, 5.0), 0.5),
                      (0.5, bell(1865 * p, 0.8, GLASS, 5.0), 0.5), (0, _sweep(r, 1.2, 1500, 4500, 1.0, 0.6), 0.4)), 0.35, 1.2, 0.5)


def matriarch_summon(r, p):
    return reverb(lay((0, _sweep(r, 1.6, 500, 4200, 1.3, 0.85, 1.5)), (0.5, _choir(r, [293.7 * p, 349.2 * p, 440 * p, 587.3 * p], 1.6), 0.9),
                      (1.5, bell(1175 * p, 0.9, GLASS, 4.5)), (1.5, bell(3520 * p, 0.6, GLASS, 6.0), 0.5), (1.5, _burst(r, 0.3, 3000, 4.0, "hp"), 0.5)), 0.3, 1.2, 0.4)


# ------------------------------------------------------------- Greenxertz

def drifter_puff(r, p):
    return lay((0, mul(lp(noise(0.28, r), E([(0, 1500), (1, 450)], 0.28, True)), perc(0.28, 0.01, 4.0))),
               (0.03, _glide(480 * p, 950 * p, 0.07), 0.6), (0.1, mul(pluck(392 * p, 0.4, r, 0.8, 0.4), perc(0.4, 0.004, 4.0)), 0.5))


def host_raise(r, p):
    d = 0.6
    sq = mul(lp(noise(d, r), mul(lfo(13, 0.6, d), 900)), swell(d, 0.4, 1.0))
    arp = [(0.3 + i * 0.16, mul(pluck(f * p, 0.6, r, 0.5, 0.5), perc(0.6, 0.004, 3.0)), 0.9) for i, f in enumerate((147, 174.6, 220))]
    gr = animal(r, p, "ambient", 88, [(350, 3, 1.0), (800, 4, 0.5)], 0.5, 0.2, dur=1.3, rough_hz=9)
    return lay((0, sq, 0.8), *arp, (0.1, gr, 0.6))


def mossback_graze(r, p):
    rips = [(t, mul(wind(0.13, r, 2500 * p, 0.8), perc(0.13, 0.006, 3.0))) for t in (0, 0.25)]
    chew = [(0.5 + i * 0.14, _glide((230 - i * 15) * p, 120 * p, 0.08, k=2.5), 0.7) for i in range(3)]
    return lay(*rips, *chew, (0.5, mul(lp(noise(0.4, r), 700), mul(lfo(7, 0.9, 0.4), swell(0.4, 0.5, 1.0))), 0.5))


def forager_dig(r, p):
    sc = [(i * 0.085, mul(svf(noise(0.06, r), (1300 + r.random() * 500) * p, 1.5), perc(0.06, 0.003, 3.0)), 0.7 + 0.3 * r.random()) for i in range(6)]
    snort = animal(r, p, "hurt", 380, [(1100, 4, 1.0), (2300, 5, 0.5)], 0.4, 0.4, dur=0.6)
    return lay(*sc, (0.56, snort, 0.7), (0.3, _glide(420 * p, 900 * p, 0.06), 0.3))


def leviathan_song(r, p):
    d = 2.3
    f = mul(E([(0, 110 * p), (0.25, 165 * p), (0.5, 98 * p), (0.8, 147 * p), (1, 123 * p)], d, True), lfo(3.3, 0.01, d))
    s = mix(osc("sin", f, d), (osc("sin", [v * 2 for v in f], d), 0.5), (osc("tri", [v * 3 for v in f], d), 0.25),
            (svf(osc("saw", f, d), [v * 5 for v in f], 4.0), 0.5))
    s = mul(s, E([(0, 0), (0.1, 1), (0.45, 0.7), (0.6, 1), (0.9, 0.7), (1, 0)], d))
    pl = [(0.4 + i * 0.6, mul(pluck((220, 294, 196)[i] * p, 0.7, r, 0.6, 0.3), perc(0.7, 0.004, 3.0)), 0.15) for i in range(3)]
    return trim(reverb(lay((0, s), *pl), 0.4, 1.6, 0.2), 2.5)


def sovereign_infect(r, p):
    return lay((0, mul(wind(0.2, r, E([(0, 300), (1, 4200)], 0.2, True), 2.5), swell(0.2, 0.85, 2.0))), (0.19, click(2300 * p, 0.03, 2.1, 6.0)),
               (0.2, _burst(r, 0.2, 900, 4.0), 0.8), (0.22, mul(pluck(82 * p, 0.6, r, 0.9, 0.3), perc(0.6, 0.003, 3.0)), 0.9),
               (0.25, _glide(300 * p, 150 * p, 0.2), 0.5))


def sovereign_heal(r, p):
    pulses = [(i * 0.4, mul(pluck(98 * p, 0.5, r, 0.6, 0.3), perc(0.5, 0.004, 3.0))) for i in range(3)]
    blips = [(0.1 + i * 0.1, _glide((400 + i * 70) * p, (800 + i * 140) * p, 0.07), 0.35) for i in range(12)]
    return lay(*pulses, *blips, (0, mul(osc("sin", 49 * p, 1.4), mul(lfo(2.5, 0.8, 1.4), swell(1.4, 0.5, 1.0))), 0.8))


def _creak(r, f0, f1, d):
    return mul(svf(mul(osc("saw", mul(E([(0, f0), (1, f1)], d, True), jitter(d, r, 25, 0.08)), d), lfo(23, 0.9, d)), 900, 1.5), swell(d, 0.5, 1.0))


def sovereign_phase(r, p):
    ch = [(i * 0.03, mul(pluck(f * p, 1.4, r, 0.3, 0.6), perc(1.4, 0.003, 2.5))) for i, f in enumerate((65.4, 69.3, 98, 138.6))]
    gr = animal(r, p, "death", 62, [(280, 3, 1.0), (600, 3, 0.6), (1500, 5, 0.2)], 0.6, 0.2, dur=1.2, rough_hz=7)
    return lay(*ch, (0.1, _creak(r, 140 * p, 260 * p, 0.9), 0.7), (0.3, gr, 0.8))


def sovereign_summon(r, p):
    d = 1.4
    rum = mul(lp(noise(d, r), 220), mul(lfo(12, 0.7, d), E([(0, 0), (1, 1)], d)))
    bloom = [(1.4 + i * 0.05, mul(pluck(f * p, 1.0, r, 0.3, 0.7), perc(1.0, 0.003, 2.5))) for i, f in enumerate((98, 146.8, 196, 246.9, 293.7))]
    return lay((0, rum), (0.2, _creak(r, 110 * p, 330 * p, 1.2), 0.8), (1.35, _burst(r, 0.3, 1200, 4.0), 0.7), *bloom,
               (1.4, mul(crackle(0.5, r, 500, 1500, 2.0, 30), perc(0.5, 0.003, 3.0)), 0.5))


# ----------------------------------------------------------------- Nyxara

def stag_shed(r, p):
    return lay((0, click(2600 * p, 0.03)), (0, bell(2217 * p, 0.5, CRYSTAL, 7.0, 6.0), 0.8), (0.28, bell(1319 * p, 0.4, CRYSTAL, 9.0, 4.0), 0.9),
               (0.36, bell(1480 * p, 0.3, CRYSTAL, 11.0), 0.6), (0.44, bell(1245 * p, 0.3, CRYSTAL, 12.0), 0.4), (0.28, _thump(150, 70, 0.08), 0.4))


def stag_aura(r, p):
    d = 2.0
    f = 659.3 * p
    ch = mix(*[(mul(mix(osc("sin", f * m, d), osc("sin", f * m + 3.0, d)), 0.5), g) for m, g in ((1, 1.0), (1.25, 0.7), (1.5, 0.7), (2, 0.4), (3.01, 0.15))])
    return reverb(lay((0, mul(ch, swell(d, 0.4, 1.3))), (0.3, bell(f * 2, 0.9, CRYSTAL, 5.0, 4.0), 0.3), (0.9, bell(f * 3, 0.8, CRYSTAL, 5.0, 4.0), 0.2)), 0.3, 1.2, 0.4)


def shardback_deflect(r, p):
    return lay((0, bell(2900 * p, 0.5, METAL, 8.0, 7.0)), (0, click(3400 * p, 0.02), 0.7), (0.02, _glide(2800 * p, 5200 * p, 0.09), 0.4),
               (0, _burst(r, 0.05, 5000, 4.0, "hp"), 0.4))


def shardback_charge(r, p):
    clacks = [(i * 0.08, bell((900 + i * 120) * p, 0.12, METAL, 14.0), 0.8) for i in range(4)]
    snort = animal(r, p, "hurt", 105, [(400, 3, 1.0), (1300, 5, 0.5)], 0.6, 0.4, dur=1.0)
    return lay(*clacks, (0.4, _thump(110, 50, 0.12), 0.9), (0.62, _thump(110, 50, 0.12), 0.9), (0.4, _burst(r, 0.1, 1500, 4.0), 0.4),
               (0.62, _burst(r, 0.1, 1500, 4.0), 0.4), (0.8, snort, 0.9))


def golem_smash(r, p):
    return lay((0, drive(_thump(125 * p, 42 * p, 0.6, 4.0), 1.8)), (0, bell(330 * p, 1.0, CRYSTAL, 5.0, 3.0), 0.7), (0, bell(509 * p, 0.8, CRYSTAL, 6.0, 5.0), 0.5),
               (0, _burst(r, 0.4, 3000, 4.0, "hp"), 0.5), (0.05, mul(crackle(0.6, r, 400, 3500, 4.0, 20), perc(0.6, 0.003, 3.0)), 0.5))


def colossus_beam(r, p):
    ch = 1.0
    rise = mul(mix(osc("sin", E([(0, 392 * p), (1, 1568 * p)], ch, True), ch), osc("sin", E([(0, 396 * p), (1, 1590 * p)], ch, True), ch)), swell(ch, 0.9, 1.5))
    d = 1.1
    beam = mul(mix(lp(osc("saw", 196 * p, d), 2400), (osc("saw", 198.5 * p, d), 0.6), (fm(784 * p, 1.5, 2.5, d), 0.5)),
               mul(lfo(31, 0.25, d), E([(0, 0), (0.03, 1), (0.8, 0.9), (1, 0)], d)))
    return lay((0, rise, 0.7), (0.3, bell(1568 * p, 0.6, CRYSTAL, 6.0, 5.0), 0.3), (1.0, beam), (1.0, bell(784 * p, 1.0, CRYSTAL, 4.0, 4.0), 0.6))


def colossus_rotate(r, p):
    grind = mul(svf(noise(0.7, r), 520, 1.3), mul(lfo(22, 0.8, 0.7), E([(0, 0), (0.1, 1), (0.9, 0.8), (1, 0)], 0.7)))
    return lay((0, grind), (0, mul(osc("sin", E([(0, 180), (1, 90)], 0.7, True), 0.7), swell(0.7, 0.3, 1.0)), 0.5),
               (0.7, bell(523 * p, 1.0, METAL, 5.0, 5.0)), (0.7, _thump(140, 55, 0.15), 0.7))


def colossus_phase(r, p):
    return trim(reverb(lay((0, bell(130.8 * p, 2.0, METAL, 3.5, 2.5)), (0, bell(185 * p, 2.0, METAL, 3.5, 2.5), 0.8), (0.04, bell(261.6 * p, 1.8, CRYSTAL, 4.0, 3.0), 0.7),
                           (0, _thump(100, 38, 0.5, 4.0), 0.8), (0, _burst(r, 0.3, 3500, 4.0, "hp"), 0.3)), 0.35, 1.5, 0.4), 2.4)


def colossus_summon(r, p):
    gl = [(i * 0.2, bell(f * p, 0.8, CRYSTAL, 5.0, 4.0), 0.4 + i * 0.08) for i, f in enumerate((196, 261.6, 370, 523.3, 740, 1046.5))]
    return trim(lay(*gl, (0, mul(osc("sin", E([(0, 40), (1, 80)], 1.3, True), 1.3), E([(0, 0), (1, 1)], 1.3)), 0.6), (1.3, colossus_phase(r, p), 1.0)), 2.5)


# --------------------------------------------------------------- Asteroid

def crab_snap(r, p):
    return lay((0, click(1800 * p, 0.03)), (0.07, click(2200 * p, 0.03)), (0.07, bell(900 * p, 0.25, METAL, 10.0), 0.6))


def crab_magnet(r, p):
    d = 2.0
    hum = mix(osc("sin", 120 * p, d), (osc("sin", 240 * p, d), 0.6), (osc("tri", 361 * p, d), 0.35), (fm(480 * p, 0.5, 1.2, d), 0.2))
    hum = mul(hum, mul(lfo(6, 0.25, d), E([(0, 0), (0.1, 1), (0.9, 1), (1, 0)], d)))
    ticks, t, gap = [], 0.15, 0.3
    while t < 1.85:
        ticks.append((t, click((1500 + r.random() * 900) * p, 0.025), 0.35))
        t += gap
        gap = max(0.07, gap * 0.82)
    return lay((0, hum), *ticks)


def leech_latch(r, p):
    d = 0.8
    wh = mul(mix(fm(E([(0, 380 * p), (1, 900 * p)], d, True), 2.0, 1.5, d), osc("sin", E([(0, 386 * p), (1, 912 * p)], d, True), d)), swell(d, 0.7, 1.0))
    return lay((0, _thump(320 * p, 75 * p, 0.12, 4.0)), (0, _burst(r, 0.08, 1200, 4.0), 0.6), (0.06, wh, 0.7), (0.1, click(2100 * p, 0.03), 0.4))


def leech_detach(r, p):
    return lay((0, _glide(950 * p, 190 * p, 0.25, "tri")), (0.22, click(1700 * p, 0.04, 2.3, 5.0)), (0.22, _thump(200, 80, 0.08), 0.7))


def worm_emerge(r, p):
    d = 1.0
    rum = mul(lp(noise(d, r), E([(0, 120), (1, 420)], d, True)), mul(lfo(14, 0.7, d), E([(0, 0), (1, 1)], d)))
    roar = animal(r, p, "ambient", 60, [(180, 2, 1.0), (420, 3, 0.6), (900, 4, 0.3)], 0.7, 0.3, dur=1.5, rough_hz=16)
    return lay((0, rum), (0.9, _burst(r, 0.4, 1400, 4.0)), (0.9, mul(crackle(0.7, r, 900, 800, 1.6, 30), perc(0.7, 0.003, 3.0)), 0.8),
               (0.95, roar, 0.9), (0.9, click(1900 * p, 0.03), 0.5), (1.0, click(2400 * p, 0.03), 0.4))


# ----------------------------------------------------------------- Wrecks

def mimic_reveal(r, p):
    hiss = mul(voice(E([(0, 300 * p), (1, 520 * p)], 0.5, True), 0.5, [(1400, 3, 1.0), (2900, 4, 0.7)], r, breath=0.9, rough=0.5, rough_hz=40), swell(0.5, 0.3, 1.0))
    return lay((0, _creak(r, 190 * p, 640 * p, 0.25)), (0.24, _thump(170, 60, 0.15), 1.0), (0.24, bell(410 * p, 0.3, METAL, 10.0), 0.6),
               (0.24, _burst(r, 0.12, 2500, 4.0), 0.6), (0.38, _sweep(r, 0.22, 500, 3500, 2.0, 0.7), 0.6), (0.45, hiss, 0.8))


def swarm_drain(r, p):
    d = 1.1
    cr = mul(crackle(d, r, 50, 3500 * p, 4.0, 1400), E([(0, 0.3), (1, 1)], d))
    buzz = mul(hp(osc("sq", E([(0, 100 * p), (1, 220 * p)], d, True), d, duty=0.2), 1200), mul(lfo(50, 0.9, d), E([(0, 0), (1, 0.6)], d)))
    return lay((0, cr), (0, buzz, 0.5), (1.1, _burst(r, 0.2, 3000, 4.0, "hp")), (1.1, _glide(2600 * p, 500 * p, 0.12, "sq", 4.0), 0.3))


def hulk_assemble(r, p):
    items, t, gap = [], 0.0, 0.34
    for i in range(7):
        items.append((t, bell((220 + r.random() * 380) * p, 0.3, METAL, 11.0), 0.6 + 0.05 * i))
        items.append((t, _burst(r, 0.05, 2500, 4.0), 0.3))
        t += gap
        gap *= 0.8
    servo = mul(lp(osc("sq", E([(0, 60 * p), (1, 240 * p)], 1.45, True), 1.45, duty=0.3), 1800), E([(0, 0), (0.2, 0.6), (1, 1)], 1.45))
    return lay(*items, (0, servo, 0.5), (1.5, drive(_thump(120 * p, 38 * p, 0.5, 4.0), 2.0)), (1.5, bell(147 * p, 0.7, METAL, 6.0, 2.0), 0.8))


def hulk_sweep(r, p):
    return lay((0, mul(wind(0.5, r, E([(0, 200), (0.5, 950), (1, 220)], 0.5, True), 1.5), swell(0.5, 0.5, 1.0))),
               (0, mul(lp(osc("sq", E([(0, 140 * p), (0.5, 300 * p), (1, 110 * p)], 0.5, True), 0.5, duty=0.3), 2000), swell(0.5, 0.5, 1.0)), 0.5),
               (0.45, bell(260 * p, 0.4, METAL, 9.0), 0.8), (0.45, _thump(110, 45, 0.15), 0.8))


def hulk_shed(r, p):
    return lay((0, bell(380 * p, 0.4, METAL, 8.0)), (0, mul(crackle(0.3, r, 1200, 5000, 4.0, 50), perc(0.3, 0.002, 3.0)), 0.8),
               (0.3, bell(520 * p, 0.2, METAL, 12.0), 0.5), (0.45, bell(610 * p, 0.15, METAL, 14.0), 0.3), (0, _thump(150, 60, 0.1), 0.6))


def drone_leap(r, p):
    d = 0.6
    rot = mul(lp(osc("sq", E([(0, 90 * p), (0.6, 270 * p), (1, 230 * p)], d, True), d, duty=0.25), 2200), mul(lfo(45, 0.5, d), swell(d, 0.55, 1.0)))
    return lay((0, rot), (0.05, _glide(1500 * p, 2500 * p, 0.12), 0.4), (0, mul(wind(d, r, 1800, 1.0), swell(d, 0.6, 1.0)), 0.3))


def android_shield(r, p):
    sv = mul(lp(osc("sq", E([(0, 310 * p), (1, 175 * p)], 0.3, True), 0.3, duty=0.3), 2400), swell(0.3, 0.4, 1.0))
    return lay((0, sv, 0.7), (0.3, bell(300 * p, 0.25, METAL, 11.0)), (0.38, bell(250 * p, 0.3, METAL, 10.0)), (0.38, _thump(120, 55, 0.1), 0.7),
               (0.4, mul(mix(osc("sin", 160 * p, 0.5), (osc("sin", 320 * p, 0.5), 0.4)), swell(0.5, 0.2, 1.0)), 0.6))


def tfdrone_work(r, p):
    bl = lambda f0, f1: mul(lp(osc("sq", E([(0, f0 * p), (1, f1 * p)], 0.1, True), 0.1, duty=0.4), 2600), swell(0.1, 0.5, 1.0))
    beep = lambda f: mul(osc("sin", f * p, 0.09), fade(perc(0.09, 0.004, 1.5), 0.002, 0.02))
    return lay((0, bl(260, 420), 0.6), (0.14, _thump(140, 70, 0.08), 0.7), (0.3, bl(420, 260), 0.6), (0.44, _thump(140, 70, 0.08), 0.7),
               (0.3, mul(crackle(0.25, r, 500, 2200, 2.0), perc(0.25, 0.003, 3.0)), 0.3), (0.6, beep(880), 0.5), (0.72, beep(1108.7), 0.5))


# ---------------------------------------------------------------- Pirates

def pirate_volley(r, p):
    return lay((0, mul(fm(E([(0, 1850 * p), (1, 290 * p)], 0.2, True), 1.5, E([(0, 5), (1, 1)], 0.2), 0.2), perc(0.2, 0.002, 3.5))),
               (0, _burst(r, 0.06, 4000, 4.0, "hp"), 0.7), (0, _thump(180, 70, 0.08), 0.5))


def captain_dash(r, p):
    d = 0.8
    jet = drive(mul(wind(d, r, E([(0, 800), (0.25, 3600), (1, 1400)], d, True), 1.0), E([(0, 0), (0.08, 1), (0.6, 0.7), (1, 0)], d)), 2.5)
    roar = mul(lp(osc("saw", E([(0, 70 * p), (0.2, 120 * p), (1, 80 * p)], d, True), d), 600), E([(0, 0), (0.08, 1), (1, 0)], d))
    return lay((0, jet), (0, roar, 0.7), (0, click(1500 * p, 0.03), 0.5))


def captain_rally(r, p):
    bands = [(650, 4, 1.0), (1100, 5, 0.7), (2450, 6, 0.3)]

    def syl(f0, f1, d):
        v = voice(mul(E([(0, f0 * p), (1, f1 * p)], d, True), jitter(d, r, 12, 0.02)), d, bands, r, breath=0.2, rough=0.3)
        return svf(mul(v, swell(d, 0.3, 1.0)), 1250, 0.9)

    beep = mul(osc("sq", 1250 * p, 0.05), perc(0.05, 0.002, 2.0))
    return lay((0, syl(150, 170, 0.22)), (0.26, syl(175, 235, 0.45)), (0.78, beep, 0.3), (0.86, beep, 0.3))


# ------------------------------------------------------------- Deep space

def crawler_blink(r, p):
    return lay((0, _glide(210 * p, 58 * p, 0.16)), (0.16, mul(wind(0.22, r, E([(0, 4200), (1, 300)], 0.22, True), 2.0), perc(0.22, 0.002, 3.5))),
               (0.16, _glide(300 * p, 1300 * p, 0.1), 0.6), (0.16, _thump(90, 40, 0.2), 0.7))


def slime_pulse(r, p):
    d = 0.7
    s = mul(fm(mul(lfo(7, 0.03, d), 140 * p), 2.01, E([(0, 0), (0.8, 6.5), (1, 1)], d), d), swell(d, 0.8, 1.5))
    return lay((0, s), (0.55, mul(crackle(0.35, r, 1500, 5000, 5.0, 40), perc(0.35, 0.002, 3.0)), 0.7), (0.55, _thump(110 * p, 45 * p, 0.25), 0.8))


def manta_blink(r, p):
    sub = mul(mix(osc("sin", E([(0, 52 * p), (1, 112 * p)], 0.45, True), 0.45), (osc("sin", E([(0, 104 * p), (1, 224 * p)], 0.45, True), 0.45), 0.5)), swell(0.45, 0.9, 1.5))
    return reverb(lay((0, sub), (0, mul(wind(0.45, r, E([(0, 300), (1, 5200)], 0.45, True), 2.0), swell(0.45, 0.92, 2.0)), 0.7),
                      (0.45, bell(220 * p, 0.8, CRYSTAL, 5.0, 3.0), 0.7), (0.45, _thump(130 * p, 36 * p, 0.5, 4.0)), (0.45, _glide(400 * p, 1800 * p, 0.1), 0.4)), 0.3, 1.4, 0.4)


# --------------------------------------------------------- blocks and gear

def incubator_hatch(r, p):
    cr = [(t, mul(crackle(0.06, r, 2500, 1900, 3.0), perc(0.06, 0.002, 3.0))) for t in (0, 0.14, 0.24)]
    return lay(*cr, (0.24, click(2400, 0.03), 0.6), (0.4, bell(659.3, 0.5, GLASS, 6.0), 0.7), (0.52, bell(987.8, 0.6, GLASS, 5.0), 0.7),
               (0.7, _glide(1500, 2300, 0.07), 0.4), (0.8, _glide(1700, 2500, 0.07), 0.3))


def lure_beacon_pulse(r, p):
    d = 1.1
    tone = mul(mix(osc("sin", 523.3, d), (osc("sin", 784.9, d), 0.5), (osc("tri", 1046.5, d), 0.15)), mul(lfo(5, 0.5, d), swell(d, 0.3, 1.0)))
    return lay((0, tone), (0.05, _glide(420, 1000, 0.07), 0.4), (0.5, _glide(420, 1000, 0.07), 0.25))


def sonic_ward_hum(r, p):
    d = 3.0  # every component completes whole cycles in 3 s, so the file loops
    s = mix(osc("tri", 98.0, d), (osc("sin", 196.0, d), 0.6), (osc("sin", 98.0 + 2.0 / 3.0, d), 0.8), (osc("sin", 294.0 + 1.0 / 3.0, d), 0.25),
            (mul(osc("sin", 2352.0, d), [0.5 - 0.5 * math.cos(TAU * 2.0 * i / (3.0 * SR)) for i in range(N(d))]), 0.05))
    return mul(s, [0.85 + 0.15 * math.sin(TAU * i / (1.5 * SR)) for i in range(N(d))])


def altar_summon(r, p):
    d = 1.6
    sh = mul(mix(*[(osc("sin", E([(0, f), (1, f * 3)], d, True), d), 0.3) for f in (220, 277.2, 330, 415.3)]), mul(lfo(11, 0.5, d), swell(d, 0.9, 1.5)))
    return trim(reverb(lay((0, bell(98, 2.2, METAL, 3.0, 1.5)), (0, _thump(80, 34, 0.6, 3.5), 0.8), (0, sh, 0.5), (1.55, bell(784, 0.8, CRYSTAL, 5.0, 4.0), 0.5),
                           (1.55, _burst(r, 0.25, 3000, 4.0, "hp"), 0.3)), 0.35, 1.5, 0.4), 2.5)


def pod_capture(r, p):
    return lay((0, mul(wind(0.3, r, E([(0, 3200), (1, 400)], 0.3, True), 2.0), swell(0.3, 0.3, 1.0))), (0.05, _glide(600, 1250, 0.2, "tri"), 0.5),
               (0.32, click(1900, 0.03), 0.9), (0.32, bell(880, 0.25, METAL, 11.0), 0.4))


def pod_release(r, p):
    return lay((0, click(1900, 0.03), 0.9), (0, bell(740, 0.2, METAL, 12.0), 0.4), (0.04, mul(wind(0.32, r, E([(0, 400), (1, 3400)], 0.32, True), 2.0), swell(0.32, 0.7, 1.0))),
               (0.06, _glide(1250, 600, 0.12, "tri"), 0.4), (0.2, _glide(700, 1400, 0.12, "tri"), 0.4))


def shield_reflect(r, p):
    return lay((0, bell(2400, 0.6, METAL, 7.0, 9.0)), (0, bell(3600, 0.4, METAL, 9.0), 0.4), (0, click(3000, 0.02), 0.7),
               (0.03, _glide(2400, 5600, 0.1), 0.35), (0, _thump(220, 90, 0.06), 0.4))


def blink_charm(r, p):
    sp = [(0.14 + i * 0.05, bell((2000 + r.random() * 2000), 0.25, GLASS, 10.0), 0.25) for i in range(5)]
    return lay((0, _glide(200, 1450, 0.13)), (0.12, _thump(140, 40, 0.3, 4.0), 0.9), (0.12, mul(wind(0.2, r, E([(0, 4500), (1, 400)], 0.2, True), 2.0), perc(0.2, 0.002, 3.0)), 0.5), *sp)


def whistle(r, p):
    d = 0.6
    f = mul(E([(0, 1400), (0.35, 1420), (0.45, 1900), (1, 1880)], d, True), lfo(7, 0.006, d))
    e = E([(0, 0), (0.05, 1), (0.38, 0.8), (0.42, 0.3), (0.47, 1), (0.9, 0.8), (1, 0)], d)
    return lay((0, mul(mix(osc("sin", f, d), (osc("sin", [v * 2 for v in f], d), 0.12), (wind(d, r, f, 6.0), 0.5)), e)),
               (0.28, bell(1900, 0.6, GLASS, 6.0), 0.2))


def jar_shatter(r, p):
    sh = [(r.random() * 0.12, bell(2500 + r.random() * 3000, 0.3, GLASS, 10.0), 0.4) for _ in range(6)]
    return lay((0, _burst(r, 0.15, 3000, 4.0, "hp")), *sh, (0.03, mul(crackle(0.6, r, 1800, 3800, 4.0, 60), perc(0.6, 0.002, 3.0)), 0.9),
               (0.03, mul(hp(osc("sq", 110, 0.4, duty=0.2), 1500), mul(lfo(50, 0.9, 0.4), perc(0.4, 0.002, 3.0))), 0.3))


def scanner_scan(r, p):
    beep = lambda f, d=0.07: mul(osc("tri", f, d), fade(perc(d, 0.003, 1.2), 0.002, 0.02))
    chirr = mul(osc("sq", 2400, 0.12, duty=0.5), mul([0.5 + 0.5 * g for g in osc("sq", 60, 0.12)], 0.25))
    return lay((0, beep(900)), (0.1, beep(1200)), (0.2, beep(1600, 0.12)), (0.34, chirr, 0.4))
