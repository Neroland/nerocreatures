"""Parametric creature voices and habitat timbres.

Each voice function renders one of three kinds -- "ambient", "hurt",
"death" -- so every creature gets a consistent set from one description.
`tint` adds the habitat's family timbre.
"""
from kit import *

E = env


def animal(r, p, kind, f0, bands, rough=0.2, breath=0.05, src="saw", dur=1.0, rough_hz=30.0):
    """Throated call: growls, lows, yelps."""
    f0 *= p
    if kind == "ambient":
        d = 0.7 * dur
        fc = E([(0, f0 * 0.9), (0.3, f0 * 1.12), (1, f0 * 0.8)], d, True)
        e = swell(d, 0.3, 1.5)
    elif kind == "hurt":
        d = 0.32 * dur
        fc = E([(0, f0 * 1.5), (0.2, f0 * 1.9), (1, f0 * 1.25)], d, True)
        e = perc(d, 0.012, 3.0)
        rough = min(0.9, rough + 0.25)
    else:
        d = 1.1 * dur
        fc = E([(0, f0 * 1.4), (0.25, f0 * 1.1), (1, f0 * 0.5)], d, True)
        e = E([(0, 0), (0.05, 1), (0.55, 0.6), (1, 0)], d)
    f = mul(fc, jitter(d, r, 12, 0.025))
    return mul(voice(f, d, bands, r, src, breath, rough, rough_hz), e)


def glassy(r, p, kind, f0, partials=GLASS, beat=0.0, heavy=False):
    """Bell voice: pings, a dissonant crack, a falling cascade."""
    f0 *= p
    scale = (1.0, 1.125, 1.25, 1.5, 1.667, 2.0)
    if kind == "ambient":
        items, t = [], 0.0
        for _ in range(3 if not heavy else 2):
            items.append((t, bell(f0 * r.choice(scale), 0.6 if not heavy else 1.0, partials, 6.0, beat),
                          0.5 + r.random() * 0.5))
            t += 0.12 + r.random() * 0.3
        s = lay(*items)
    elif kind == "hurt":
        s = mix(bell(f0 * 1.5, 0.3, partials, 9.0, beat), (bell(f0 * 1.5 * 1.07, 0.3, partials, 9.0), 0.8),
                (click(f0 * 2.5, 0.03), 0.6))
    else:
        items = [(i * 0.11, bell(f0 * sc, 0.5, partials, 7.0, beat), 1.0 - i * 0.12)
                 for i, sc in enumerate((2.0, 1.667, 1.5, 1.25, 1.0, 0.75))]
        items.append((0.0, mul(hp(noise(0.5, r), 3500), perc(0.5, 0.002, 6.0)), 0.35))
        s = lay(*items)
    if heavy:
        d = 0.5 if kind != "death" else 1.1
        thump = mul(osc("sin", E([(0, f0), (1, f0 * 0.4)], d, True), d), perc(d, 0.004, 5.0))
        grind = mul(svf(noise(d, r), 420, 1.2), mul(lfo(21, 0.7, d), perc(d, 0.02, 3.0)))
        s = mix(s, (thump, 0.9), (grind, 0.5))
    return s


def insect(r, p, kind, f0, rate=40.0, fc=3000.0, q=4.0):
    """Stridulation: click trains and chirrs."""
    f0 *= p
    fc *= p

    def chirr(d, r0, r1):
        gate = osc("sq", E([(0, r0), (1, r1)], d, True), d, duty=0.35)
        gate = [0.5 + 0.5 * g for g in gate]
        body = mix(svf(noise(d, r), fc, q), (osc("tri", f0, d), 0.25))
        return mul(body, gate)

    if kind == "ambient":
        a = mul(chirr(0.22, rate, rate * 1.2), swell(0.22, 0.5, 1.0))
        b = mul(chirr(0.3, rate * 1.1, rate * 0.8), swell(0.3, 0.3, 1.0))
        return lay((0, a), (0.3 + r.random() * 0.15, b, 0.8))
    if kind == "hurt":
        sq = mul(fm(E([(0, f0 * 2.4), (1, f0 * 1.6)], 0.22, True), 1.41, 3.0, 0.22), perc(0.22, 0.004, 4.0))
        return mix(sq, (mul(chirr(0.22, rate * 2, rate * 2), perc(0.22, 0.002, 5.0)), 0.8))
    d = 1.0
    body = mul(chirr(d, rate * 1.5, max(4.0, rate * 0.15)), E([(0, 0), (0.03, 1), (0.6, 0.5), (1, 0)], d))
    fall = mul(osc("tri", E([(0, f0 * 1.5), (1, f0 * 0.5)], d, True), d), perc(d, 0.01, 4.0))
    return mix(body, (fall, 0.4))


def mech(r, p, kind, f0, duty=0.3, heavy=False):
    """Servos and static."""
    f0 *= p

    def servo(d, pts):
        return lp(osc("sq", E(pts, d, True), d, duty=duty), 2600)

    def static(d, k=4.0):
        return mul(hp(noise(d, r), 3000), mul(osc("sq", 47, d, duty=0.6), perc(d, 0.002, k)))

    if kind == "ambient":
        d = 0.7
        s = mul(servo(d, [(0, f0), (0.4, f0 * 1.5), (0.7, f0 * 1.45), (1, f0)]), swell(d, 0.4, 1.0))
        s = mix(s, (at(static(0.15), 0.5), 0.3))
    elif kind == "hurt":
        s = mix(bell(f0 * 2.2, 0.3, METAL, 10.0), (static(0.25), 0.7),
                (mul(servo(0.2, [(0, f0 * 3), (1, f0 * 1.5)]), perc(0.2, 0.003, 4.0)), 0.6))
    else:
        d = 1.2
        gate = [0.5 + 0.5 * g for g in osc("sq", E([(0, 22), (1, 5)], d, True), d, duty=0.6)]
        s = mul(servo(d, [(0, f0 * 2), (0.2, f0 * 1.6), (1, f0 * 0.22)]), mul(gate, E([(0, 1), (0.7, 0.7), (1, 0)], d)))
        s = mix(s, (static(0.9, 2.5), 0.5), (at(bell(f0 * 1.3, 0.4, METAL, 9.0), 0.75), 0.7))
    if heavy:
        d = len(s) / SR
        s = mix(s, (mul(svf(noise(d, r), 300, 1.0), mul(lfo(17, 0.8, d), perc(d, 0.02, 2.5))), 0.9))
    return s


def elemental(r, p, kind, fc, q=1.5, amb=1.6, spark=0.0):
    """Wind, dust and static: shaped noise with no throat."""
    fc *= p
    if kind == "ambient":
        d = amb
        s = mul(wind(d, r, jitter(d, r, 5, 0.45, fc), q), mul(swell(d, 0.5, 1.0), lfo(6.5, 0.3, d)))
    elif kind == "hurt":
        d = 0.3
        s = mul(wind(d, r, E([(0, fc * 2.2), (1, fc * 1.2)], d, True), q), mul(perc(d, 0.004, 4.0), lfo(38, 0.6, d)))
    else:
        d = 1.2
        am = [0.5 + 0.5 * v for v in osc("sin", E([(0, 16), (1, 2)], d, True), d)]
        s = mul(wind(d, r, E([(0, fc * 1.6), (1, fc * 0.3)], d, True), q), mul(am, E([(0, 0), (0.04, 1), (1, 0)], d)))
    if spark:
        d = len(s) / SR
        s = mix(s, (mul(crackle(d, r, 260, 4200, 4.0), perc(d, 0.003, 2.0) if kind != "ambient" else swell(d, 0.5, 1.0)), spark))
    return s


def wet(r, p, kind, f0):
    """Burbles and drips: rising sine blips with a plucked body."""
    f0 *= p

    def blip(f, d=0.07, up=2.2):
        return mul(osc("sin", E([(0, f), (1, f * up)], d, True), d), perc(d, 0.004, 3.0))

    if kind == "ambient":
        items, t = [], 0.0
        for _ in range(4):
            items.append((t, blip(f0 * (0.7 + r.random() * 0.8)), 0.6 + 0.4 * r.random()))
            t += 0.07 + r.random() * 0.14
        items.append((0.05, mul(pluck(f0 * 0.5, 0.5, r, 0.8, 0.3), perc(0.5, 0.004, 4.0)), 0.5))
        return lay(*items)
    if kind == "hurt":
        return mix(blip(f0 * 1.6, 0.16, 0.5), (mul(lp(noise(0.14, r), 1500), perc(0.14, 0.003, 5.0)), 0.6))
    items = [(i * 0.13, blip(f0 * (1.5 - i * 0.18), 0.12, 0.6), 1.0 - i * 0.13) for i in range(6)]
    items.append((0.0, mul(lp(noise(0.9, r), E([(0, 1800), (1, 300)], 0.9, True)), perc(0.9, 0.01, 4.0)), 0.5))
    return lay(*items)


def human(r, p, kind, f0):
    """Masked grunt: throat through a rebreather band, plus valve hiss."""
    bands = [(620, 4, 1.0), (1080, 5, 0.7), (2450, 6, 0.3)]
    if kind == "ambient":
        d = 1.1
        inh = mul(wind(0.45, r, E([(0, 900), (1, 1700)], 0.45, True), 1.2), swell(0.45, 0.6, 1.0))
        exh = mul(wind(0.5, r, E([(0, 1500), (1, 700)], 0.5, True), 1.0), swell(0.5, 0.25, 1.0))
        return lay((0, inh, 0.8), (0.55, exh), (0.5, click(1400 * p, 0.02, 2.0, 2.0), 0.4))
    v = animal(r, p, kind, f0, bands, rough=0.35, breath=0.25, dur=0.9 if kind == "hurt" else 0.8)
    v = svf(v, 1250, 0.9)
    d = len(v) / SR
    return mix(v, (mul(hp(noise(d, r), 3500), perc(d, 0.005, 3.0)), 0.25))


def deep(r, p, kind, f0, dur=1.0):
    """Sub-heavy, slow-sweeping call for the void and the sky giants."""
    f0 *= p
    if kind == "ambient":
        d, pts = 2.2 * dur, [(0, f0), (0.35, f0 * 1.5), (0.7, f0 * 1.33), (1, f0 * 0.9)]
        e = swell(d, 0.4, 1.3)
    elif kind == "hurt":
        d, pts = 0.5 * dur, [(0, f0 * 2.2), (1, f0 * 1.3)]
        e = perc(d, 0.02, 3.0)
    else:
        d, pts = 2.0 * dur, [(0, f0 * 1.8), (0.3, f0 * 1.4), (1, f0 * 0.45)]
        e = E([(0, 0), (0.06, 1), (0.5, 0.6), (1, 0)], d)
    f = mul(E(pts, d, True), lfo(4.2, 0.012, d))
    s = mix(osc("sin", f, d), (osc("sin", [v * 2 for v in f], d), 0.5), (osc("tri", [v * 3 for v in f], d), 0.3),
            (svf(osc("saw", f, d), [v * 6 for v in f], 3.0), 0.4))
    sweep = mul(wind(d, r, E([(0, 250), (0.5, 900), (1, 200)], d, True), 2.5), 0.25)
    return mul(mix(s, sweep), e)


def plasma(r, p, kind, f0):
    """Fizzing FM droplet."""
    f0 *= p
    if kind == "ambient":
        d = 0.9
        s = mul(fm(mul(jitter(d, r, 9, 0.2, f0), 1.0), 2.01, lfo(5, 0.6, d, 3.0), d), swell(d, 0.4, 1.0))
        return mix(s, (mul(crackle(d, r, 180, 5200, 5.0), swell(d, 0.5, 1.0)), 0.7))
    if kind == "hurt":
        d = 0.25
        return mul(fm(E([(0, f0 * 2.5), (1, f0 * 1.2)], d, True), 2.01, 6.0, d), perc(d, 0.003, 4.0))
    d = 1.0
    s = mul(fm(E([(0, f0 * 2), (1, f0 * 0.4)], d, True), 2.01, E([(0, 7), (1, 0.5)], d), d), perc(d, 0.01, 3.5))
    return mix(s, (mul(crackle(d, r, 600, 5200, 5.0, 20), perc(d, 0.005, 2.0)), 0.8))


def tint(hab, r, p, d):
    """Habitat family timbre, laid quietly under a voice of length d."""
    d = max(0.25, d)
    if hab == "luna":        # glassy and sparse
        return at(bell(r.choice((1975, 2349, 2637)) * p, min(0.6, d), GLASS, 7.0), d * 0.35)
    if hab == "mars":        # dry and granular
        return mul(crackle(d, r, 420, 3000, 2.5), perc(d, 0.004, 3.0))
    if hab == "cindara":     # low and crackling
        return mix(mul(crackle(d, r, 150, 900, 2.0), perc(d, 0.01, 2.0)), (mul(osc("sin", 58 * p, d), swell(d, 0.3, 1.0)), 0.8))
    if hab == "glacira":     # breathy with high chimes
        return mix(mul(wind(d, r, 2600, 0.9), swell(d, 0.4, 1.0)), (at(bell(3136 * p, min(0.5, d), GLASS, 8.0), d * 0.3), 0.6))
    if hab == "greenxertz":  # wet and plucked
        b = mul(osc("sin", E([(0, 420 * p), (1, 1100 * p)], 0.06, True), 0.06), perc(0.06, 0.003, 3.0))
        return mix(at(b, d * 0.2), (at(mul(pluck(196 * p, min(0.5, d), r, 0.7, 0.4), perc(min(0.5, d), 0.004, 4.0)), d * 0.1), 0.8))
    if hab == "nyxara":      # bell-like, beating overtones
        return at(bell(r.choice((784, 988, 1175)) * p, min(0.8, d), CRYSTAL, 5.0, 5.0), d * 0.15)
    if hab == "asteroid":    # metallic clicks
        return lay((d * 0.1, click(2100 * p, 0.03)), (d * 0.1 + 0.06, click(1700 * p, 0.03), 0.7), (d * 0.6, click(2500 * p, 0.025), 0.5))
    if hab == "wrecks":      # servo and static
        return mul(hp(noise(d, r), 3500), mul([0.5 + 0.5 * g for g in osc("sq", 31, d, duty=0.3)], perc(d, 0.003, 3.0)))
    if hab == "pirates":     # comm chirp
        return at(mul(osc("sq", 1250 * p, 0.04), perc(0.04, 0.002, 2.0)), d * 0.85)
    if hab == "deep":        # sub-bass, slow sweep
        return mul(mix(osc("sin", E([(0, 62 * p), (1, 48 * p)], d, True), d), (osc("sin", 124 * p, d), 0.3)), swell(d, 0.4, 1.0))
    raise ValueError(hab)
