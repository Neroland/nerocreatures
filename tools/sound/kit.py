"""Sound synthesis kit for NeroCreatures.

Standard library only. Every sound in the mod is built from the oscillators,
noise sources, envelopes and filters in this file; no samples or recordings
are used anywhere. Signals are plain lists of floats at ``SR`` Hz.
"""
import math
import random
import struct
import zlib

SR = 44100          # synthesis and file rate
TAU = 2.0 * math.pi


def N(d):
    return max(1, int(round(d * SR)))


def rng_for(name):
    """Deterministic generator per sound name (never Python's salted hash)."""
    return random.Random(zlib.crc32(name.encode("ascii")))


def _arr(v, n):
    if isinstance(v, list):
        if len(v) >= n:
            return v
        return v + [v[-1]] * (n - len(v))
    return [v] * n


# ---------------------------------------------------------------- envelopes

def env(points, d, log=False):
    """Piecewise curve through (fraction 0..1, value) points over d seconds.

    log=True interpolates in the log domain (use for frequency glides)."""
    n = N(d)
    out = [0.0] * n
    pts = sorted(points)
    if pts[0][0] > 0:
        pts.insert(0, (0.0, pts[0][1]))
    if pts[-1][0] < 1:
        pts.append((1.0, pts[-1][1]))
    for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
        i0, i1 = int(t0 * n), min(n, int(t1 * n))
        span = max(1, i1 - i0)
        if log:
            l0, l1 = math.log(v0), math.log(v1)
            for i in range(i0, i1):
                out[i] = math.exp(l0 + (l1 - l0) * (i - i0) / span)
        else:
            for i in range(i0, i1):
                out[i] = v0 + (v1 - v0) * (i - i0) / span
    return out


def perc(d, a=0.005, k=5.0):
    """Linear attack of a seconds, then exponential decay (k = decay steepness)."""
    n = N(d)
    na = max(1, int(a * SR))
    dec = math.exp(-k / n)
    out = [0.0] * n
    g = 1.0
    for i in range(n):
        if i < na:
            out[i] = i / na
        else:
            g *= dec
            out[i] = g
    return out


def swell(d, peak=0.6, k=2.0):
    """Rise to a peak at fraction `peak`, then fall; a smooth hump."""
    n = N(d)
    out = [0.0] * n
    for i in range(n):
        t = i / n
        x = t / peak if t < peak else (1.0 - t) / (1.0 - peak)
        out[i] = x ** k if x > 0 else 0.0
    return out


def lfo(rate, depth, d, base=1.0, ph=0.0):
    """base * (1 + depth * sin): vibrato or tremolo curve."""
    n = N(d)
    w = TAU * rate / SR
    sin = math.sin
    p = TAU * ph
    return [base * (1.0 + depth * sin(p + w * i)) for i in range(n)]


def jitter(d, rng, rate, depth, base=1.0):
    """Random-walk curve around base, re-targeted `rate` times a second."""
    n = N(d)
    step = max(1, int(SR / rate))
    out = [0.0] * n
    a = base * (1 + depth * (rng.random() * 2 - 1))
    i = 0
    while i < n:
        b = base * (1 + depth * (rng.random() * 2 - 1))
        m = min(step, n - i)
        for j in range(m):
            out[i + j] = a + (b - a) * j / step
        a = b
        i += m
    return out


# -------------------------------------------------------------- oscillators

def osc(shape, f, d, ph=0.0, duty=0.5):
    """sin | tri | saw | sq oscillator; f is a number or a per-sample list."""
    n = N(d)
    fl = _arr(f, n)
    out = [0.0] * n
    inc = 1.0 / SR
    if shape == "sin":
        sin = math.sin
        for i in range(n):
            out[i] = sin(TAU * ph)
            ph += fl[i] * inc
    elif shape == "tri":
        for i in range(n):
            x = ph % 1.0
            out[i] = 4.0 * abs(x - 0.5) - 1.0
            ph += fl[i] * inc
    elif shape == "saw":
        for i in range(n):
            out[i] = 2.0 * (ph % 1.0) - 1.0
            ph += fl[i] * inc
    elif shape == "sq":
        for i in range(n):
            out[i] = 1.0 if (ph % 1.0) < duty else -1.0
            ph += fl[i] * inc
    else:
        raise ValueError(shape)
    return out


def fm(fc, ratio, index, d):
    """Two-operator FM: carrier fc, modulator fc*ratio, index number or list."""
    n = N(d)
    fl = _arr(fc, n)
    il = _arr(index, n)
    out = [0.0] * n
    sin = math.sin
    pc = pm = 0.0
    inc = TAU / SR
    for i in range(n):
        out[i] = sin(pc + il[i] * sin(pm))
        pc += fl[i] * inc
        pm += fl[i] * ratio * inc
    return out


def noise(d, rng):
    r = rng.random
    return [r() * 2.0 - 1.0 for _ in range(N(d))]


def impulses(d, rng, rate, rate_end=None):
    """Sparse random spikes (Poisson-like), rate per second, for crackle."""
    n = N(d)
    out = [0.0] * n
    r = rng.random
    r0 = rate / SR
    r1 = (rate if rate_end is None else rate_end) / SR
    for i in range(n):
        if r() < r0 + (r1 - r0) * i / n:
            out[i] = r() * 2.0 - 1.0
    return out


# ------------------------------------------------------------------ filters

def lp(x, fc):
    """One-pole low-pass; fc number or list."""
    n = len(x)
    out = [0.0] * n
    y = 0.0
    if isinstance(fc, list):
        fl = _arr(fc, n)
        k = TAU / SR
        for i in range(n):
            a = fl[i] * k
            a = a / (1.0 + a)
            y += a * (x[i] - y)
            out[i] = y
    else:
        a = TAU * fc / SR
        a = a / (1.0 + a)
        for i in range(n):
            y += a * (x[i] - y)
            out[i] = y
    return out


def hp(x, fc):
    low = lp(x, fc)
    return [a - b for a, b in zip(x, low)]


def svf(x, fc, q=1.0, mode="bp"):
    """State-variable filter (trapezoidal, stable to Nyquist).

    mode lp | bp | hp; fc number or list (coefficients refreshed every 32
    samples). Band-pass is normalised to unity gain at the centre."""
    n = len(x)
    out = [0.0] * n
    k = 1.0 / q
    ic1 = ic2 = 0.0
    var = isinstance(fc, list)
    fl = _arr(fc, n) if var else None
    top = SR * 0.45
    g = math.tan(math.pi * min(top, fc if not var else fl[0]) / SR)
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    m = 0 if mode == "lp" else 1 if mode == "bp" else 2
    for i in range(n):
        if var and not (i & 31):
            g = math.tan(math.pi * min(top, fl[i]) / SR)
            a1 = 1.0 / (1.0 + g * (g + k))
            a2 = g * a1
            a3 = g * a2
        xi = x[i]
        v3 = xi - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        if m == 0:
            out[i] = v2
        elif m == 1:
            out[i] = v1 * k
        else:
            out[i] = xi - k * v1 - v2
    return out


def formants(x, bands):
    """Sum of band-passes: bands = [(fc, q, gain), ...]. A simple vocal tract."""
    out = [0.0] * len(x)
    for fc, q, gain in bands:
        y = svf(x, fc, q, "bp")
        for i, v in enumerate(y):
            out[i] += v * gain
    return out


# ----------------------------------------------------------------- utility

def mul(x, e):
    if isinstance(e, list):
        e = _arr(e, len(x))
        return [a * b for a, b in zip(x, e)]
    return [a * e for a in x]


def mix(*sigs):
    """Sum signals (each a list or a (list, gain) pair), padded to the longest."""
    parts = [(s if isinstance(s, tuple) else (s, 1.0)) for s in sigs]
    n = max(len(s) for s, _ in parts)
    out = [0.0] * n
    for s, g in parts:
        for i, v in enumerate(s):
            out[i] += v * g
    return out


def at(x, t):
    """Delay a signal by t seconds."""
    return [0.0] * int(t * SR) + x


def lay(*items):
    """lay((t, sig[, gain]), ...): place signals on a timeline."""
    return mix(*[(at(it[1], it[0]), it[2] if len(it) > 2 else 1.0) for it in items])


def drive(x, g):
    tanh = math.tanh
    return [tanh(v * g) for v in x]


def ring(x, f):
    """Ring modulation by a sine at f Hz."""
    w = TAU * f / SR
    sin = math.sin
    return [v * sin(w * i) for i, v in enumerate(x)]


def echo(x, delay, fb=0.4, taps=3):
    out = list(x) + [0.0] * int(delay * SR * taps)
    g = fb
    for t in range(1, taps + 1):
        off = int(delay * SR * t)
        for i, v in enumerate(x):
            out[off + i] += v * g
        g *= fb
    return out


def comb(x, delay, fb, tail):
    d = max(1, int(delay * SR))
    n = len(x) + int(tail * SR)
    out = [0.0] * n
    nx = len(x)
    for i in range(n):
        v = x[i] if i < nx else 0.0
        if i >= d:
            v += out[i - d] * fb
        out[i] = v
    return out


def reverb(x, wet=0.25, size=1.0, tail=0.5):
    """Small Schroeder room: three combs, damped. Adds `tail` seconds."""
    a = comb(x, 0.0297 * size, 0.72, tail)
    b = comb(x, 0.0371 * size, 0.70, tail)
    c = comb(x, 0.0411 * size, 0.68, tail)
    w = lp(mix(a, b, c), 3200)
    return mix(x, (w, wet / 3.0))


def fade(x, tin=0.004, tout=0.02):
    n = len(x)
    out = list(x)
    a = min(n // 2, int(tin * SR))
    b = min(n // 2, int(tout * SR))
    for i in range(a):
        out[i] *= i / a
    for i in range(b):
        out[n - 1 - i] *= i / b
    return out


def norm(x, peak=0.708):
    """Remove DC, then peak-normalise (0.708 = -3 dBFS)."""
    y = hp(x, 25.0)
    m = max(max(y), -min(y)) or 1.0
    g = peak / m
    return [v * g for v in y]


def trim(x, d):
    return x[:N(d)]


# --------------------------------------------------------- compound voices

def bell(f, d, partials, k=5.0, beat=0.0):
    """Additive bell: partials = [(ratio, amp, decay_mult)]. beat > 0 adds a
    detuned twin to each partial `beat` Hz away (slow beating overtones)."""
    n = N(d)
    out = [0.0] * n
    sin = math.sin
    for ratio, amp, dm in partials:
        for det in ((0.0, beat) if beat else (0.0,)):
            w = TAU * (f * ratio + det) / SR
            dec = math.exp(-k * dm / n)
            g = amp
            for i in range(n):
                out[i] += g * sin(w * i)
                g *= dec
    a = max(1, int(0.002 * SR))
    for i in range(min(a, n)):
        out[i] *= i / a
    return out


GLASS = [(1.0, 1.0, 1.0), (2.76, 0.5, 1.6), (5.4, 0.25, 2.4), (8.93, 0.12, 3.5)]
CRYSTAL = [(1.0, 1.0, 1.0), (1.5, 0.45, 1.2), (2.0, 0.5, 1.5), (3.01, 0.3, 2.0), (4.2, 0.15, 3.0)]
METAL = [(1.0, 1.0, 1.0), (1.59, 0.7, 1.3), (2.31, 0.5, 1.7), (3.47, 0.35, 2.2), (5.13, 0.2, 3.0)]


def pluck(f, d, rng, damp=0.5, bright=1.0):
    """Karplus-Strong string, excited by filtered noise."""
    n = N(d)
    L = max(2, int(SR / f))
    buf = [(rng.random() * 2 - 1) for _ in range(L)]
    if bright < 1.0:
        buf = lp(buf, 800 + 8000 * bright)
    out = [0.0] * n
    j = 0
    fbk = 0.999 - 0.02 * damp
    prev = 0.0
    for i in range(n):
        v = buf[j]
        out[i] = v
        buf[j] = (v + prev) * 0.5 * fbk
        prev = v
        j += 1
        if j == L:
            j = 0
    return out


def voice(f, d, bands, rng, src="saw", breath=0.0, rough=0.0, rough_hz=30.0):
    """Throat: oscillator (+breath noise, +roughness AM) through formant bands."""
    s = osc(src, f, d)
    if rough:
        s = mul(s, lfo(rough_hz, rough, d, 1.0 - rough * 0.5))
    if breath:
        s = mix(s, (noise(d, rng), breath))
    return formants(s, bands)


def click(f, d=0.03, ratio=3.7, index=4.0, k=9.0):
    """Short FM tick with a metallic edge."""
    return mul(fm(f, ratio, env([(0, index), (1, 0.2)], d), d), perc(d, 0.001, k))


def wind(d, rng, f, q=2.0):
    """Band-passed noise; f number or curve."""
    return svf(noise(d, rng), f, q, "bp")


def crackle(d, rng, rate, fc=2500, q=3.0, rate_end=None):
    """Random pops: spikes rung through a band-pass."""
    return svf(impulses(d, rng, rate, rate_end), fc, q, "bp")


# -------------------------------------------------------------------- files

def to_pcm16(x):
    c = [max(-32767, min(32767, int(round(v * 32767.0)))) for v in x]
    return struct.pack("<%dh" % len(c), *c)


def wav_bytes(pcm):
    hdr = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", 36 + len(pcm), b"WAVE", b"fmt ", 16,
                      1, 1, SR, SR * 2, 2, 16, b"data", len(pcm))
    return hdr + pcm
