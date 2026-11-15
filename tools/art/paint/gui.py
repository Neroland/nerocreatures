#!/usr/bin/env python3
"""GUI painter: the Bestiary screen textures and the Field Scanner HUD sprite sheet.

    python tools/art/paint/gui.py                  # write every GUI texture
    python tools/art/paint/gui.py --check          # change nothing; exit 1 if a file is stale
    python tools/art/paint/gui.py --skip-portraits # only the hand-drawn sheets (fast)
    python tools/art/paint/gui.py --mockups <dir>  # also draw one screen mockup per GUI scale there

Standard library only. Writes, under ``assets/nerocreatures/textures/gui/``:

* ``bestiary/panel.png``            24x24 nine-slice window frame (8 px corners)
* ``bestiary/widgets.png``          128x128 sprite sheet; the regions are the ``WIDGETS`` table below
* ``bestiary/portrait/<id>.png``    24x24 portrait of every roster creature, software-rendered from
  its model spec and painted texture (so it can never drift from the entity art)
* ``bestiary/silhouette/<id>.png``  the same shape as one flat colour, for undiscovered entries
* ``scanner_hud.png``               64x64 sprite sheet; regions are the ``HUD`` table below

This file has no entity painters (``PAINTERS`` is empty), so ``tools/art/run.py`` imports it
harmlessly. The interface colours are the family's dark interface palette, not the creature ramps
in ``art/palette.json``: those rules are for entity texels. Habitat pips do use the habitat accents.

The mockups use a small built-in 5x7 font to stand in for the game font. They are review pictures,
not game assets, and are written only to the directory given on the command line.
"""

from __future__ import annotations

import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from pixelkit import ENTITY_TEXTURES, REPO_ROOT, Image, Palette, hex_to_rgb, write_or_check  # noqa: E402

PAINTERS = {}

GUI_DIR = os.path.join(os.path.dirname(ENTITY_TEXTURES), "gui")
ITEM_DIR = os.path.join(os.path.dirname(ENTITY_TEXTURES), "item")

# --- interface palette (ARGB values quoted in the screen spec) ---------------

INK = "#04070D"
VOID_TOP = "#0A1122"
VOID_BOTTOM = "#050913"
PANEL = "#0D1322"
PANEL_HI = "#141D31"
PANEL_EDGE = "#26344F"
EDGE_HI = "#3B5072"
TROUGH = "#090E19"
TITLE = "#E6F2FF"
SUBTLE = "#93A4BF"
MUTED = "#5B6982"
CYAN = "#52D9F2"
GREEN = "#5BD69A"
AMBER = "#FFB74D"
LOCKED = "#6E7B93"
LINE_IDLE = "#2F3B52"
DANGER = "#FF5A5A"
HOVER_FILL = "#1B2B45"
SELECT_FILL = "#1B3A52"
SILHOUETTE = "#34405A"
RADIANT = "#FFF6D6"
ALPHA_RED = "#FF2E4D"
WHITE = "#FFFFFF"

# Bestiary order: habitats in travel order, creatures in roster order inside each, bosses last.
HABITATS = [
    ("luna", "Luna", ["lunar_stalker", "lumen_moth", "echo_shrike", "lode_tortoise"]),
    ("mars", "Mars", ["dune_loper", "strata_hound", "dust_devil"]),
    ("cindara", "Cindara", ["slag_beetle", "magma_wader", "cinder_tyrant"]),
    ("glacira", "Glacira", ["glacite_wisp", "rime_yak", "frost_lurker", "rime_matriarch"]),
    ("greenxertz", "Greenxertz", ["xertz_forager", "spore_drifter", "bloom_host", "mossback",
                                  "drift_leviathan", "bloom_sovereign"]),
    ("nyxara", "Nyxara", ["crystal_golem", "prism_stag", "shardback", "facet_colossus"]),
    ("asteroid", "Asteroid Fields", ["asteroid_worm", "magnet_crab", "gravity_leech"]),
    ("wrecks", "Wrecks and Stations", ["rogue_drone", "rogue_android", "terraforming_drone", "space_pirate",
                                       "cache_mimic", "static_swarm", "scrap_hulk", "pirate_captain"]),
    ("deep_space", "Deep Space", ["void_crawler", "plasma_slime", "void_manta"]),
]
BOSSES = ("cinder_tyrant", "rime_matriarch", "bloom_sovereign", "facet_colossus")
ALL_IDS = [entity for _, _, ids in HABITATS for entity in ids]

# Sprite regions: name -> (u, v, w, h). The screen spec repeats this table; keep them in step.
WIDGETS = {
    "tile": (0, 0, 28, 28), "tile_hover": (28, 0, 28, 28), "tile_selected": (56, 0, 28, 28),
    "tile_unknown": (84, 0, 28, 28),
    "frame_boss": (0, 28, 28, 28), "frame_studied": (28, 28, 28, 28), "frame_focus": (56, 28, 28, 28),
    "close": (0, 56, 14, 14), "close_hover": (14, 56, 14, 14),
    "tab": (28, 56, 12, 14), "tab_active": (40, 56, 12, 14), "tab_hover": (52, 56, 12, 14),
    "button": (64, 56, 14, 14), "button_hover": (78, 56, 14, 14), "button_on": (92, 56, 14, 14),
    "scroll_track": (106, 56, 6, 12), "scroll_thumb": (112, 56, 6, 12), "scroll_thumb_hover": (118, 56, 6, 12),
    "icon_rotate_left": (0, 70, 12, 12), "icon_pause": (12, 70, 12, 12), "icon_play": (24, 70, 12, 12),
    "icon_rotate_right": (36, 70, 12, 12), "icon_radiant": (48, 70, 12, 12), "icon_lock": (60, 70, 12, 12),
    "icon_back": (72, 70, 12, 12), "icon_makes": (84, 70, 12, 12),
    "trait_hardy": (0, 82, 12, 12), "trait_swift": (12, 82, 12, 12), "trait_keen": (24, 82, 12, 12),
    "trait_bountiful": (36, 82, 12, 12), "trait_calm": (48, 82, 12, 12), "trait_thrifty": (60, 82, 12, 12),
    "trait_stout": (72, 82, 12, 12), "trait_radiant": (84, 82, 12, 12),
    "record_kills": (0, 94, 9, 9), "record_owned": (9, 94, 9, 9), "record_bred": (18, 94, 9, 9),
    "record_harvested": (27, 94, 9, 9), "record_boss": (36, 94, 9, 9),
    "badge_scanned": (48, 94, 7, 7), "badge_studied": (55, 94, 7, 7), "pip_boss": (62, 94, 7, 7),
    "pip_alpha": (69, 94, 7, 7),
}
for _index, (_habitat, _, _) in enumerate(HABITATS):
    WIDGETS["habitat_" + _habitat] = (_index * 8, 104, 8, 8)

HUD = {
    "reticle_idle": (0, 0, 25, 25), "reticle_target": (25, 0, 25, 25), "reticle_refused": (0, 25, 25, 25),
    "bar_frame": (0, 50, 52, 7), "bar_fill": (0, 57, 50, 5), "bar_fill_done": (0, 62, 50, 2),
}


# --- drawing helpers ---------------------------------------------------------

def rgba(colour, alpha: int = 255) -> tuple:
    return (hex_to_rgb(colour) if isinstance(colour, str) else tuple(colour[:3])) + (alpha,)


def mix(a, b, t: float) -> str:
    ra, rb = rgba(a), rgba(b)
    return "#%02X%02X%02X" % tuple(int(round(ra[i] + (rb[i] - ra[i]) * t)) for i in range(3))


def put(image: Image, x: int, y: int, colour) -> None:
    """Alpha-blends one pixel onto an opaque or transparent canvas."""
    if not (0 <= x < image.width and 0 <= y < image.height):
        return
    a = colour[3]
    if a == 255:
        image.px[y][x] = colour
    elif a:
        under = image.px[y][x]
        if under[3] == 0:
            image.px[y][x] = colour
        else:
            image.px[y][x] = tuple((colour[i] * a + under[i] * (255 - a)) // 255 for i in range(3)) + (255,)


def fill(image: Image, x: int, y: int, w: int, h: int, colour, alpha: int = 255) -> None:
    c = rgba(colour, alpha)
    for yy in range(max(0, y), min(image.height, y + h)):
        for xx in range(max(0, x), min(image.width, x + w)):
            put(image, xx, yy, c)


def gradient(image: Image, x: int, y: int, w: int, h: int, top, bottom) -> None:
    for row in range(h):
        fill(image, x, y + row, w, 1, mix(top, bottom, row / max(1, h - 1)))


def outline(image: Image, x: int, y: int, w: int, h: int, colour, alpha: int = 255) -> None:
    fill(image, x, y, w, 1, colour, alpha)
    fill(image, x, y + h - 1, w, 1, colour, alpha)
    fill(image, x, y + 1, 1, h - 2, colour, alpha)
    fill(image, x + w - 1, y + 1, 1, h - 2, colour, alpha)


def corners(image: Image, x: int, y: int, w: int, h: int, length: int, colour) -> None:
    for cx, cy, sx, sy in ((x, y, 1, 1), (x + w - 1, y, -1, 1), (x, y + h - 1, 1, -1), (x + w - 1, y + h - 1, -1, -1)):
        for step in range(length):
            put(image, cx + sx * step, cy, rgba(colour))
            put(image, cx, cy + sy * step, rgba(colour))


def stamp(image: Image, x: int, y: int, rows: list, colours: dict) -> None:
    """Draws ASCII art: '.' is clear, every other character is looked up in ``colours``."""
    for dy, row in enumerate(rows):
        for dx, char in enumerate(row):
            if char != ".":
                put(image, x + dx, y + dy, rgba(colours[char]))


def blit(image: Image, source: Image, x: int, y: int, region=None, scale: int = 1) -> None:
    u, v, w, h = region or (0, 0, source.width, source.height)
    for sy in range(h):
        for sx in range(w):
            colour = source.px[v + sy][u + sx]
            if colour[3] == 0:
                continue
            for oy in range(scale):
                for ox in range(scale):
                    put(image, x + sx * scale + ox, y + sy * scale + oy, colour)


def nine(image: Image, source: Image, region, corner: int, x: int, y: int, w: int, h: int) -> None:
    """Nine-slice with tiled edges and centre, the way the screen draws ``panel.png`` and the tabs."""
    u, v, sw, sh = region
    xs = [(0, corner, 0, corner), (corner, sw - 2 * corner, corner, w - 2 * corner), (sw - corner, corner, w - corner, corner)]
    ys = [(0, corner, 0, corner), (corner, sh - 2 * corner, corner, h - 2 * corner), (sh - corner, corner, h - corner, corner)]
    for sy0, sh0, dy0, dh0 in ys:
        for sx0, sw0, dx0, dw0 in xs:
            for dy in range(dh0):
                for dx in range(dw0):
                    colour = source.px[v + sy0 + dy % sh0][u + sx0 + dx % sw0]
                    if colour[3]:
                        put(image, x + dx0 + dx, y + dy0 + dy, colour)


# --- the hand-drawn sheets ---------------------------------------------------

def paint_panel() -> Image:
    image = Image(24, 24)
    fill(image, 0, 0, 24, 24, INK)
    fill(image, 1, 1, 22, 22, PANEL_EDGE)
    fill(image, 1, 1, 22, 1, EDGE_HI)
    fill(image, 1, 1, 1, 22, EDGE_HI)
    fill(image, 2, 2, 20, 20, PANEL_HI)
    fill(image, 3, 3, 18, 18, PANEL)
    corners(image, 1, 1, 22, 22, 5, CYAN)
    for x, y in ((0, 0), (23, 0), (0, 23), (23, 23)):
        image.px[y][x] = (0, 0, 0, 0)
    return image


def tile(image: Image, x: int, y: int, edge, top, bottom, sheen: int = 0x26) -> None:
    fill(image, x, y, 28, 28, edge)
    gradient(image, x + 1, y + 1, 26, 26, top, bottom)
    if sheen:
        fill(image, x + 1, y + 1, 26, 1, WHITE, sheen)


def button(image: Image, x: int, y: int, w: int, h: int, edge, top, bottom) -> None:
    fill(image, x, y, w, h, edge)
    gradient(image, x + 1, y + 1, w - 2, h - 2, top, bottom)
    fill(image, x + 1, y + 1, w - 2, 1, WHITE, 0x20)


ICONS = {
    "icon_rotate_left": ["............", "....aaaa....", "..aa....aa..", ".a........a.", ".a........a.", "aaa.......a.",
                         ".a........a.", "..........a.", ".........a..", "...aaaaaa...", "............", "............"],
    "icon_pause": ["............", "............", "..aaa..aaa..", "..aaa..aaa..", "..aaa..aaa..", "..aaa..aaa..",
                   "..aaa..aaa..", "..aaa..aaa..", "..aaa..aaa..", "..aaa..aaa..", "............", "............"],
    "icon_play": ["............", "............", "...aa.......", "...aaaa.....", "...aaaaaa...", "...aaaaaaaa.",
                  "...aaaaaaaa.", "...aaaaaa...", "...aaaa.....", "...aa.......", "............", "............"],
    "icon_radiant": ["............", ".....bb.....", ".....bb.....", "..b..aa..b..", "...a.aa.a...", ".bbaaaaaabb.",
                     ".bbaaaaaabb.", "...a.aa.a...", "..b..aa..b..", ".....bb.....", ".....bb.....", "............"],
    "icon_lock": ["............", "....aaaa....", "...a....a...", "...a....a...", "...a....a...", "..aaaaaaaa..",
                  "..aaaaaaaa..", "..aaa..aaa..", "..aaa..aaa..", "..aaaaaaaa..", "..aaaaaaaa..", "............"],
    "icon_back": ["............", "............", ".....a......", "....aa......", "...aaaaaaaa.", "..aaaaaaaaa.",
                  "...aaaaaaaa.", "....aa......", ".....a......", "............", "............", "............"],
    "icon_makes": ["............", "............", "......a.....", "......aa....", ".aaaaaaaa...", ".aaaaaaaaa..",
                   ".aaaaaaaa...", "......aa....", "......a.....", "............", "............", "............"],
    "trait_hardy": ["............", "..aa...aa...", ".abba.abba..", ".abbbabbba..", ".abbbbbbba..", ".abbbbbbba..",
                    "..abbbbba...", "...abbba....", "....aba.....", ".....a......", "............", "............"],
    "trait_swift": ["............", "......aaaa..", ".....abba...", "....abba....", "...abbaaaa..", "..aabbbba...",
                    "....abba....", "...abba.....", "..abba......", "..aaa.......", "............", "............"],
    "trait_keen": ["............", "............", "...aaaaaa...", "..abbbbbba..", ".abbaccabba.", ".abbaccabba.",
                   "..abbbbbba..", "...aaaaaa...", "............", "............", "............", "............"],
    "trait_bountiful": ["............", ".....a......", "....aba.....", "....aba.....", "...abbba....", "...abbba....",
                        "..abbbbba...", "..abbbbba...", "..abbbbba...", "...aaaaa....", "............", "............"],
    "trait_calm": ["............", "............", ".....aaaa...", "...aabbbba..", "..abbbbba...", ".abbbbba....",
                   ".abbbba.....", ".abbbbbaaa..", "..abbbbbba..", "...aaaaaa...", "............", "............"],
    "trait_thrifty": ["............", "..aaaaaaaa..", "..abbbbbba..", "...abbbba...", "....abba....", ".....aa.....",
                      "....a..a....", "...a.bb.a...", "..abbbbbba..", "..aaaaaaaa..", "............", "............"],
    "trait_stout": ["............", "....aaaa....", "...a....a...", ".aaaaaaaaaa.", ".abbbbbbbba.", ".abbbccbbba.",
                    ".aaaaccaaaa.", ".abbbbbbbba.", ".abbbbbbbba.", ".aaaaaaaaaa.", "............", "............"],
    "record_kills": [".......aa", "......aba", ".....aba.", "....aba..", "a..aba...", ".aaba....", "..aa.....", ".a.aa....",
                     "a....a..."],
    "record_owned": [".........", ".aa...aa.", "abba.abba", "abbbabbba", "abbbbbbba", ".abbbbba.", "..abbba..", "...aba...",
                     "....a...."],
    "record_bred": ["....a....", "...aba...", "..abbba..", "..abbba..", ".abbbbba.", ".abbbbba.", ".abbbbba.", "..abbba..",
                    "...aaa..."],
    "record_harvested": ["aa.....aa", "aba...aba", ".aba.aba.", "..abaa...", "...aa....", "..abaa...", ".aa..aa..",
                         "a.a..a.a.", "aa...aa.."],
    "record_boss": [".........", "a...a...a", "aa.aba.aa", "abaabaaba", "abbbbbbba", "abbbbbbba", "aaaaaaaaa", "abbbbbbba",
                    "aaaaaaaaa"],
    "badge_scanned": [".aaaaa.", "aabbbaa", "ab...ba", "ab...ba", "ab...ba", "aabbbaa", ".aaaaa."],
    "badge_studied": ["..aaa..", ".aabaa.", "aabbbaa", "abbbbba", "aabbbaa", ".aabaa.", "..aaa.."],
    "pip_boss": [".......", "a..a..a", "ab.b.ba", "abbbbba", "abbbbba", "aaaaaaa", "......."],
    "pip_alpha": ["...a...", "..aba..", "..aba..", ".abbba.", ".abbba.", "abbbbba", "aaaaaaa"],
}
ICONS["icon_rotate_right"] = [row[::-1] for row in ICONS["icon_rotate_left"]]
ICONS["trait_radiant"] = ICONS["icon_radiant"]
ICON_COLOURS = {
    "icon_radiant": {"a": RADIANT, "b": "#FCD37C"},
    "trait_hardy": {"a": "#7A1F2B", "b": DANGER}, "trait_swift": {"a": "#9A5B12", "b": AMBER},
    "trait_keen": {"a": "#1E6C80", "b": CYAN, "c": INK}, "trait_bountiful": {"a": "#1F6B47", "b": GREEN},
    "trait_calm": {"a": "#3B5072", "b": "#BFD3F2"}, "trait_thrifty": {"a": "#5A4A9A", "b": "#A083FF"},
    "trait_stout": {"a": "#6A4A2A", "b": "#C99A62", "c": AMBER}, "trait_radiant": {"a": RADIANT, "b": "#FCD37C"},
    "record_kills": {"a": SUBTLE, "b": TITLE}, "record_owned": {"a": "#1F6B47", "b": GREEN},
    "record_bred": {"a": "#9A5B12", "b": RADIANT}, "record_harvested": {"a": SUBTLE, "b": TITLE},
    "record_boss": {"a": "#9A5B12", "b": AMBER},
    "badge_scanned": {"a": INK, "b": CYAN}, "badge_studied": {"a": INK, "b": AMBER},
    "pip_boss": {"a": INK, "b": DANGER}, "pip_alpha": {"a": INK, "b": ALPHA_RED},
}


def paint_widgets(palette: Palette) -> Image:
    image = Image(128, 128)
    tile(image, 0, 0, PANEL_EDGE, PANEL_HI, PANEL)
    tile(image, 28, 0, EDGE_HI, HOVER_FILL, PANEL_HI)
    tile(image, 56, 0, CYAN, SELECT_FILL, PANEL_HI)
    tile(image, 84, 0, LINE_IDLE, TROUGH, TROUGH, sheen=0)
    outline(image, 1, 29, 26, 26, DANGER)                    # frame_boss: an inner ring
    corners(image, 0, 28, 28, 28, 6, DANGER)
    corners(image, 28, 28, 28, 28, 7, AMBER)                 # frame_studied: gold brackets
    corners(image, 29, 29, 26, 26, 6, AMBER)
    outline(image, 56, 28, 28, 28, WHITE)                    # frame_focus: keyboard focus ring
    outline(image, 57, 29, 26, 26, INK)
    for index, (edge, top) in enumerate(((PANEL_EDGE, PANEL_HI), (DANGER, "#4A1A22"))):
        x = index * 14
        button(image, x, 56, 14, 14, edge, top, PANEL)
        cross = TITLE if index else SUBTLE
        for step in range(6):
            fill(image, x + 4 + step, 60 + step, 1, 1, cross)
            fill(image, x + 9 - step, 60 + step, 1, 1, cross)
    for index, (edge, top, bottom) in enumerate(((PANEL_EDGE, PANEL_HI, PANEL), (CYAN, SELECT_FILL, PANEL_HI),
                                                 (EDGE_HI, HOVER_FILL, PANEL_HI))):
        button(image, 28 + index * 12, 56, 12, 14, edge, top, bottom)
    fill(image, 41, 69, 10, 1, PANEL_HI)                     # the active tab opens onto the page below
    for index, (edge, top, bottom) in enumerate(((PANEL_EDGE, PANEL_HI, PANEL), (EDGE_HI, HOVER_FILL, PANEL_HI),
                                                 (CYAN, SELECT_FILL, PANEL_HI))):
        button(image, 64 + index * 14, 56, 14, 14, edge, top, bottom)
    fill(image, 106, 56, 6, 12, TROUGH)
    outline(image, 106, 56, 6, 12, INK)
    for index, colour in enumerate((EDGE_HI, CYAN)):
        fill(image, 112 + index * 6, 56, 6, 12, colour)
        fill(image, 113 + index * 6, 57, 1, 10, WHITE, 0x40)
        outline(image, 112 + index * 6, 56, 6, 12, INK, 0x80)
    for name, rows in ICONS.items():
        u, v, _, _ = WIDGETS[name]
        stamp(image, u, v, rows, ICON_COLOURS.get(name, {"a": TITLE}))
    diamond = ["...aa...", "..abba..", ".abccba.", "abccccba", "abccccba", ".abccba.", "..abba..", "...aa..."]
    for habitat, _, _ in HABITATS:
        u, v, _, _ = WIDGETS["habitat_" + habitat]
        steps = palette.ramp(habitat, "accent")
        colours = {"a": steps[1], "b": steps[2], "c": steps[3]}
        stamp(image, u, v, diamond, colours)
        if habitat == "nyxara":                              # the one two-colour accent: split the diamond
            other = palette.ramp(habitat, "accent_b")
            for dy, row in enumerate(diamond):
                for dx in range(4, 8):
                    if row[dx] != ".":
                        image.px[v + dy][u + dx] = rgba(other[{"a": 1, "b": 2, "c": 3}[row[dx]]])
    return image


def paint_hud() -> Image:
    image = Image(64, 64)

    def reticle(x: int, y: int, colour, length: int, inset: int, locked: bool) -> None:
        for cx, cy, sx, sy in ((inset, inset, 1, 1), (24 - inset, inset, -1, 1), (inset, 24 - inset, 1, -1),
                               (24 - inset, 24 - inset, -1, -1)):
            for step in range(length):
                for px, py in ((cx + sx * step, cy), (cx, cy + sy * step)):
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if image.get(x + px + ox, y + py + oy)[3] == 0:
                            image.set(x + px + ox, y + py + oy, rgba(INK, 0xB0))
            for step in range(length):
                image.set(x + cx + sx * step, y + cy, rgba(colour))
                image.set(x + cx, y + cy + sy * step, rgba(colour))
        if locked:
            for px, py in ((12, 2), (12, 22), (2, 12), (22, 12)):
                image.set(x + px, y + py, rgba(colour))

    reticle(0, 0, SUBTLE, 4, 3, False)
    reticle(25, 0, CYAN, 6, 1, True)
    reticle(0, 25, DANGER, 4, 3, False)
    for step in range(5):                                    # refused: a small cross under the brackets
        image.set(10 + step, 35 + step, rgba(DANGER))
        image.set(14 - step, 35 + step, rgba(DANGER))
    fill(image, 0, 50, 52, 7, INK, 0xD0)
    outline(image, 0, 50, 52, 7, EDGE_HI)
    gradient(image, 0, 57, 50, 5, "#9BEBFA", CYAN)
    fill(image, 0, 57, 50, 1, WHITE, 0x70)
    fill(image, 0, 62, 50, 2, AMBER)
    return image


# --- portraits ---------------------------------------------------------------

PORTRAIT = 24
PORTRAIT_FILL = 22          # the creature's longest side, in portrait pixels
PORTRAIT_VIEW = (140, 22)   # yaw, pitch: the kit's three-quarter view


def render_creature(entity_id: str, palette: Palette, size: int, flags=()) -> Image:
    """The creature's rest pose from the three-quarter view, cropped square around what is visible."""
    import preview
    import spec as specs

    spec = specs.load(entity_id)
    state = {"flags": list(flags)} if flags else {}
    deltas, level = specs.evaluate(spec, palette, state)
    centre, radius = preview.bounds(spec)
    canvas = max(96, size * 2)
    stem, body_tint, glow_tint = preview.variant_look(spec, state)
    if "radiant" in flags and os.path.exists(os.path.join(ENTITY_TEXTURES, entity_id + "_radiant.png")):
        stem, glow_tint = entity_id + "_radiant", hex_to_rgb(palette.data["radiant"]["glow_tint"])
    base = Image.load(os.path.join(ENTITY_TEXTURES, stem + ".png"))
    glow = Image.load(os.path.join(ENTITY_TEXTURES, entity_id + "_glow.png"))
    dim = palette.rules["glow"]["intensity"]["dim"]
    big = preview.render(spec, deltas, canvas, canvas / (2 * radius * 1.05), PORTRAIT_VIEW[0], PORTRAIT_VIEW[1], centre,
                         base=base, glow=glow, glow_level=max(level, dim), glow_tint=glow_tint, state=state,
                         base_tint=body_tint)
    xs = [x for row in big.px for x, c in enumerate(row) if c[3]]
    ys = [y for y, row in enumerate(big.px) if any(c[3] for c in row)]
    if not xs:
        return Image(size, size)
    side = max(max(xs) - min(xs) + 1, max(ys) - min(ys) + 1)
    left = (min(xs) + max(xs) + 1 - side) / 2.0
    top = (min(ys) + max(ys) + 1 - side) / 2.0
    out = Image(size, size)
    for ty in range(size):
        for tx in range(size):
            x0, x1 = int(left + tx * side / size), max(int(left + (tx + 1) * side / size), int(left + tx * side / size) + 1)
            y0, y1 = int(top + ty * side / size), max(int(top + (ty + 1) * side / size), int(top + ty * side / size) + 1)
            cells = [big.get(x, y) for y in range(y0, y1) for x in range(x0, x1)]
            solid = [c for c in cells if c[3]]
            if len(solid) * 5 >= len(cells) * 2:
                out.px[ty][tx] = tuple(sum(c[i] for c in solid) // len(solid) for i in range(3)) + (255,)
    return out


def paint_portrait(entity_id: str, palette: Palette) -> tuple:
    inner = render_creature(entity_id, palette, PORTRAIT_FILL)
    portrait, shape = Image(PORTRAIT, PORTRAIT), Image(PORTRAIT, PORTRAIT)
    flat = rgba(SILHOUETTE)
    for y in range(PORTRAIT_FILL):
        for x in range(PORTRAIT_FILL):
            if inner.px[y][x][3]:
                portrait.px[y + 1][x + 1] = inner.px[y][x]
                shape.px[y + 1][x + 1] = flat
    return portrait, shape


# --- layout: the same arithmetic the screen spec gives ----------------------

def layout(width: int, height: int) -> dict:
    """Every rectangle of the screen for a scaled window of ``width`` x ``height`` GUI pixels."""
    pw, ph = min(720, width - 16) & ~1, min(400, height - 16) & ~1
    px, py = (width - pw) // 2, (height - ph) // 2
    bx, by, bw, bh = px + 6, py + 30, pw - 12, ph - 52
    two_pane = pw >= 440
    cols = (6 if pw >= 600 else 4) if two_pane else (bw - 18) // 30
    lw = cols * 30 + 14 if two_pane else bw
    out = {"window": (width, height), "panel": (px, py, pw, ph), "header": (px + 4, py + 4, pw - 8, 22),
           "close": (px + pw - 22, py + 8, 14, 14), "footer": (px + 4, py + ph - 18, pw - 8, 14),
           "body": (bx, by, bw, bh), "two_pane": two_pane, "columns": cols, "list": (bx, by, lw, bh)}
    if two_pane:
        dx, dw = bx + lw + 6, bw - lw - 6
        side = dw >= 400
        view = (176 if dw >= 480 else 144) if side else 96
        out.update(detail=(dx, by, dw, bh), detail_mode="side" if side else "stacked", viewport=(dx, by, view, view))
        if side:
            out.update(records=(dx, by + view + 4, view, bh - view - 4), name=(dx + view + 8, by + 2),
                       text=(dx + view + 8, by + 30, dw - view - 8, bh - 30))
        else:
            out.update(records=(dx + view + 8, by + 30, dw - view - 8, view - 30), name=(dx + view + 8, by + 2),
                       text=(dx, by + view + 4, dw, bh - view - 4))
    else:
        view = 72
        out.update(detail=(bx, by, bw, bh), detail_mode="single", back=(bx, by, 14, 14), name=(bx + 18, by + 3),
                   viewport=(bx, by + 18, view, view), records=(bx + view + 8, by + 30, bw - view - 8, view - 12),
                   text=(bx, by + 18 + view + 4, bw, bh - view - 22))
    return out


# --- mockups (review pictures only) -----------------------------------------

BODY = "#C9D6EA"
FONT_HEX = (
    "0000000000 00005F0000 0007000700 147F147F14 242A7F2A12 2313086462 3649552250 0005030000 001C224100 0041221C00"
    " 14083E0814 08083E0808 0050300000 0808080808 0060600000 2010080402 3E5149453E 00427F4000 4261514946 2141454B31"
    " 1814127F10 2745454539 3C4A494930 0171090503 3649494936 064949291E 0036360000 0056360000 0814224100 1414141414"
    " 0041221408 0201510906 324979413E 7E1111117E 7F49494936 3E41414122 7F4141221C 7F49494941 7F09090901 3E4149497A"
    " 7F0808087F 00417F4100 2040413F01 7F08142241 7F40404040 7F020C027F 7F0408107F 3E4141413E 7F09090906 3E4151215E"
    " 7F09192946 4649494931 01017F0101 3F4040403F 1F2040201F 3F4038403F 6314081463 0708700807 6151494543 007F414100"
    " 0204081020 0041417F00 0402010204 4040404040 0001020400 2054545478 7F48444438 3844444420 384444487F 3854545418"
    " 087E090102 0C5252523E 7F08040478 00447D4000 2040443D00 7F10284400 00417F4000 7C04180478 7C08040478 3844444438"
    " 7C14141408 081414187C 7C08040408 4854545420 043F444020 3C4040207C 1C2040201C 3C4030403C 4428102844 0C5050503C"
    " 4464544C44 0008364100 00007F0000 0041360800 08082A1C08"
)
GLYPHS = {}
for _code, _cell in enumerate(FONT_HEX.split()):
    _columns = [int(_cell[i:i + 2], 16) for i in range(0, 10, 2)]
    while len(_columns) > 1 and _columns[0] == 0:
        _columns.pop(0)
    while len(_columns) > 1 and _columns[-1] == 0:
        _columns.pop()
    GLYPHS[chr(32 + _code)] = _columns
GLYPHS[" "] = [0, 0, 0]


def text_width(message: str) -> int:
    return sum(len(GLYPHS.get(ch, GLYPHS["?"])) + 1 for ch in message) - (1 if message else 0)


def text(image: Image, x: int, y: int, message: str, colour=BODY, shadow: bool = True, right: bool = False) -> int:
    if right:
        x -= text_width(message)
    for ch in message:
        columns = GLYPHS.get(ch, GLYPHS["?"])
        for dx, bits in enumerate(columns):
            for dy in range(7):
                if bits >> dy & 1:
                    if shadow:
                        put(image, x + dx + 1, y + dy + 1, rgba(INK, 0xC0))
                    put(image, x + dx, y + dy, rgba(colour))
        x += len(columns) + 1
    return x


def wrap(message: str, width: int) -> list:
    lines, line = [], ""
    for word in message.split():
        trial = (line + " " + word).strip()
        if text_width(trial) > width and line:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + ([line] if line else [])


def chip(image: Image, x: int, y: int, label: str, colour) -> int:
    w = text_width(label) + 8
    fill(image, x, y, w, 11, mix(PANEL, colour, 0.18))
    outline(image, x, y, w, 11, colour, 0xB0)
    text(image, x + 4, y + 2, label, colour, shadow=False)
    return x + w + 4


MOCK_STUDIED = {"lunar_stalker", "lumen_moth", "glacite_wisp", "rime_yak", "void_crawler", "cinder_tyrant", "plasma_slime"}
MOCK_SCANNED = {"echo_shrike", "dune_loper", "dust_devil", "slag_beetle", "magma_wader", "frost_lurker", "xertz_forager",
                "spore_drifter", "mossback", "crystal_golem", "asteroid_worm", "magnet_crab", "rogue_drone",
                "rogue_android", "space_pirate", "static_swarm", "rime_matriarch"}
MOCK_TRAITS = [("hardy", "Hardy", "+20% max health."), ("swift", "Swift", "+15% movement speed."),
               ("keen", "Keen", "+25% ability range."), ("bountiful", "Bountiful", "50% chance of an extra product."),
               ("calm", "Calm", "Panics half as long. Never bucks a rider."), ("thrifty", "Thrifty", "Ability cooldowns 20% shorter."),
               ("stout", "Stout", "Mount panniers hold 18 slots, not 9."),
               ("radiant", "Radiant", "Rare pearl colour morph. Cosmetic only.")]


class Mock:
    def __init__(self, width: int, height: int, palette: Palette, cache: dict, sheets: dict) -> None:
        self.image = Image(width, height)
        self.lay = layout(width, height)
        self.palette, self.cache, self.sheets = palette, cache, sheets
        gradient(self.image, 0, 0, width, height, "#2B3D55", "#141C2B")          # a stand-in for the world behind
        fill(self.image, 0, 0, width, height, "#040812", 0xC8)

    def sprite(self, name: str, x: int, y: int, sheet: str = "widgets") -> None:
        table = WIDGETS if sheet == "widgets" else HUD
        blit(self.image, self.sheets[sheet], x, y, table[name])

    def art(self, entity_id: str, kind: str) -> Image:
        key = (entity_id, kind)
        if key not in self.cache:
            if isinstance(kind, int):
                self.cache[key] = render_creature(entity_id, self.palette, kind)
            else:
                self.cache[(entity_id, "portrait")], self.cache[(entity_id, "silhouette")] = paint_portrait(
                    entity_id, self.palette)
        return self.cache[key]

    def tier(self, entity_id: str, state: str) -> int:
        if state == "empty":
            return 0
        if state == "full":
            return 2
        return 2 if entity_id in MOCK_STUDIED else 1 if entity_id in MOCK_SCANNED else 0

    def chrome(self, page: str, state: str) -> None:
        img, lay = self.image, self.lay
        px, py, pw, ph = lay["panel"]
        fill(img, px - 3, py - 2, pw + 6, ph + 6, "#000000", 0x66)
        nine(img, self.sheets["panel"], (0, 0, 24, 24), 8, px, py, pw, ph)
        fill(img, px + 4, py + 26, pw - 8, 1, PANEL_EDGE)
        fill(img, px + 4, py + ph - 19, pw - 8, 1, PANEL_EDGE)
        x = text(img, px + 10, py + 12, "Bestiary", TITLE) + 12
        for label, active in (("Creatures", page == "creatures"), ("Traits", page == "traits")):
            w = text_width(label) + 16
            nine(img, self.sheets["widgets"], WIDGETS["tab_active" if active else "tab"], 3, x, py + 12, w, 14)
            text(img, x + 8, py + 16, label, TITLE if active else SUBTLE, shadow=False)
            x += w + 2
        cx, cy, _, _ = lay["close"]
        self.sprite("close", cx, cy)
        scanned = sum(1 for e in ALL_IDS if self.tier(e, state) >= 1)
        studied = sum(1 for e in ALL_IDS if self.tier(e, state) == 2)
        wide = pw >= 440
        right = text(img, cx - 8, py + 12, ("Studied %d/38" if wide else "%d/38") % studied, AMBER, right=True)
        right = cx - 8 - text_width(("Studied %d/38" if wide else "%d/38") % studied) - 10
        self.sprite("badge_studied", right, py + 12)
        label = ("Scanned %d/38" if wide else "%d/38") % scanned
        text(img, right - 8, py + 12, label, CYAN, right=True)
        self.sprite("badge_scanned", right - 8 - text_width(label) - 10, py + 12)
        hint = "Arrows: select   Tab: next area   Enter: open   Esc: close" if wide else "Arrows: select  Enter: open  Esc: close"
        text(img, px + 10, py + ph - 14, hint, SUBTLE, shadow=False)

    def creature_list(self, state: str, selected: str) -> None:
        img, lay = self.image, self.lay
        lx, ly, lw, lh = lay["list"]
        cols = lay["columns"]
        fill(img, lx, ly, lw, lh, TROUGH)
        outline(img, lx, ly, lw, lh, PANEL_EDGE)
        pane = Image(lw - 2, lh - 2)
        y = 3
        row_w = cols * 30 - 2
        for habitat, name, ids in HABITATS:
            blit(pane, self.sheets["widgets"], 3, y + 1, WIDGETS["habitat_" + habitat])
            text(pane, 15, y + 1, name, BODY)
            got = sum(1 for e in ids if self.tier(e, state) >= 1)
            done = sum(1 for e in ids if self.tier(e, state) == 2)
            text(pane, 3 + row_w, y + 1, "%d/%d" % (got, len(ids)), SUBTLE, right=True)
            fill(pane, 3, y + 11, row_w, 2, LINE_IDLE)
            fill(pane, 3, y + 11, row_w * got // len(ids), 2, CYAN)
            fill(pane, 3, y + 11, row_w * done // len(ids), 2, AMBER)
            y += 16
            for index, entity in enumerate(ids):
                tx, ty = 3 + (index % cols) * 30, y + (index // cols) * 30
                tier = self.tier(entity, state)
                name_ = "tile_selected" if entity == selected else "tile" if tier else "tile_unknown"
                blit(pane, self.sheets["widgets"], tx, ty, WIDGETS[name_])
                blit(pane, self.art(entity, "portrait" if tier else "silhouette"), tx + 2, ty + 2)
                if entity in BOSSES:
                    blit(pane, self.sheets["widgets"], tx, ty, WIDGETS["frame_boss"])
                    blit(pane, self.sheets["widgets"], tx + 2, ty + 2, WIDGETS["pip_boss"])
                if tier == 2:
                    blit(pane, self.sheets["widgets"], tx, ty, WIDGETS["frame_studied"])
                if tier:
                    blit(pane, self.sheets["widgets"], tx + 19, ty + 19, WIDGETS["badge_studied" if tier == 2 else "badge_scanned"])
            y += ((len(ids) + cols - 1) // cols) * 30 + 4
        blit(img, pane, lx + 1, ly + 1)
        sx, sy, sh = lx + lw - 8, ly + 2, lh - 4
        nine(img, self.sheets["widgets"], WIDGETS["scroll_track"], 2, sx, sy, 6, sh)
        if y > lh:
            nine(img, self.sheets["widgets"], WIDGETS["scroll_thumb"], 2, sx, sy, 6, max(16, sh * lh // y))

    def records(self, compact: bool, rows: list, goal: str, tier: int) -> None:
        img = self.image
        x, y, w, h = self.lay["records"]
        fill(img, x, y, w, h, PANEL_HI)
        outline(img, x, y, w, h, PANEL_EDGE)
        colour, label = (AMBER, "Studied") if tier == 2 else (CYAN, "Scanned")
        top = y + 4
        if not compact:
            text(img, x + 5, top, "Your records", SUBTLE)
            top += 12
        self.sprite("badge_studied" if tier == 2 else "badge_scanned", x + 5, top)
        text(img, x + 15, top, label, colour)
        fill(img, x + 5, top + 10, w - 10, 3, LINE_IDLE)
        fill(img, x + 5, top + 10, w - 10 if tier == 2 else (w - 10) // 3, 3, colour)
        top += 17
        for line in wrap(goal, w - 10)[:1 if compact else 3]:
            text(img, x + 5, top, line, SUBTLE)
            top += 10
        top += 3
        half = (w - 10) // 2
        for index, (icon, name, value) in enumerate(rows):
            rx = x + 5 + (index % 2) * half if compact else x + 5
            ry = top + (index // 2 if compact else index) * 11
            self.sprite(icon, rx, ry - 1)
            text(img, rx + 13, ry, name, SUBTLE)
            text(img, rx + (half - 6 if compact else w - 10), ry, value, TITLE, right=True)

    def detail(self, state: str) -> None:
        img, lay = self.image, self.lay
        vx, vy, vw, vh = lay["viewport"]
        accent = self.palette.ramp("luna", "accent")[3]
        gradient(img, vx, vy, vw, vh, mix(TROUGH, accent, 0.14), TROUGH)
        outline(img, vx, vy, vw, vh, PANEL_EDGE)
        corners(img, vx, vy, vw, vh, 5, CYAN)
        if state == "empty":
            dx, dy, dw, dh = lay["detail"]
            mid = dx + dw // 2
            fill(img, dx, dy, dw, dh, PANEL)
            for index, (line, colour) in enumerate((("No entries yet", TITLE),
                                                    ("Hold Use with the Field Scanner while looking at", SUBTLE),
                                                    ("a creature within 8 blocks to scan it.", SUBTLE))):
                text(img, mid - text_width(line) // 2, dy + dh // 2 - 16 + index * 12, line, colour)
            return
        size = vw * 3 // 4
        for step in range(6):
            fill(img, vx + vw // 2 - size // 3 + step * 2, vy + vh - 26 + step // 2, size * 2 // 3 - step * 4, 1, "#000000", 0x50)
        blit(img, self.art("lumen_moth", size), vx + (vw - size) // 2, vy + (vh - size) // 2 - 4)
        for index, icon in enumerate(("icon_rotate_left", "icon_pause", "icon_rotate_right")):
            self.sprite("button", vx + 3 + index * 15, vy + vh - 17)
            self.sprite(icon, vx + 4 + index * 15, vy + vh - 16)
        self.sprite("button", vx + vw - 17, vy + vh - 17)
        self.sprite("icon_radiant", vx + vw - 16, vy + vh - 16)
        nx, ny = lay["name"]
        text(img, nx, ny, "Lumen Moth", TITLE)
        cx = chip(img, nx, ny + 12, "Tameable", GREEN)
        chip(img, cx, ny + 12, "Studied", AMBER)
        self.records(lay["detail_mode"] != "side", [("record_kills", "Defeated", "1"), ("record_owned", "Owned now", "2")],
                     "Tame, hatch or breed one: 1/1", 2)
        tx, ty, tw, th = lay["text"]
        pane = Image(tw, th)
        y = 0
        inner = tw - 10

        def heading(label: str) -> None:
            nonlocal y
            end = text(pane, 0, y, label, SUBTLE)
            fill(pane, end + 4, y + 3, inner - end - 4, 1, LINE_IDLE)
            y += 11

        def para(message: str, colour=BODY) -> None:
            nonlocal y
            for line in wrap(message, inner):
                text(pane, 0, y, line, colour)
                y += 10
            y += 5

        heading("Habitat")
        blit(pane, self.sheets["widgets"], 0, y - 1, WIDGETS["habitat_luna"])
        text(pane, 12, y, "Luna", BODY)
        y += 10
        para("Also found in Overworld dark forests at night.", SUBTLE)
        heading("Ability")
        para("Living lantern: a tamed moth lights the air around its owner. Wild moths gather at light sources."
             " Reveals burrowed and cloaked creatures nearby.")
        heading("Weakness")
        para("Fragile, and Echo Shrikes hunt it. Keep it close in open ground.")
        heading("Drops and uses")
        for item, name, makes in (("lumen_dust", "Lumen Dust", ("incubator", "Incubator, Lure Beacon")),
                                  ("lumen_moth_egg", "Lumen Moth Egg", (None, "Hatches a moth with its parents' traits"))):
            fill(pane, 0, y, 18, 18, TROUGH)
            outline(pane, 0, y, 18, 18, PANEL_EDGE)
            path = os.path.join(ITEM_DIR, item + ".png")
            if os.path.exists(path):
                blit(pane, Image.load(path), 1, y + 1)
            text(pane, 23, y + 1, name, BODY)
            blit(pane, self.sheets["widgets"], 22, y + 8, WIDGETS["icon_makes"])
            text(pane, 36, y + 10, makes[1], SUBTLE)
            y += 21
        y += 3
        heading("Traits")
        para("Carries two traits. Can be Radiant: a rare pearl colour morph, cosmetic only.", SUBTLE)
        blit(img, pane, tx, ty)
        if y > th:
            nine(img, self.sheets["widgets"], WIDGETS["scroll_track"], 2, tx + tw - 6, ty, 6, th)
            nine(img, self.sheets["widgets"], WIDGETS["scroll_thumb"], 2, tx + tw - 6, ty, 6, max(16, th * th // y))

    def traits(self) -> None:
        img = self.image
        bx, by, bw, bh = self.lay["body"]
        pane = Image(bw, bh)
        y = 2
        for line in wrap("Tameable creatures, mounts and livestock each carry two traits. Offspring take one trait"
                         " from each parent; each inherited trait has a 5% chance to mutate into a new one.", bw - 12):
            text(pane, 2, y, line, BODY)
            y += 10
        y += 6
        cols = 2 if bw >= 440 else 1
        cw = (bw - 8 - (cols - 1) * 6) // cols
        for index, (key, name, effect) in enumerate(MOCK_TRAITS):
            cx, cy = (index % cols) * (cw + 6), y + (index // cols) * 38
            fill(pane, cx, cy, cw, 34, PANEL_HI)
            outline(pane, cx, cy, cw, 34, PANEL_EDGE)
            fill(pane, cx + 5, cy + 5, 16, 16, TROUGH)
            blit(pane, self.sheets["widgets"], cx + 7, cy + 7, WIDGETS["trait_" + key])
            text(pane, cx + 27, cy + 5, name, RADIANT if key == "radiant" else TITLE)
            for row, line in enumerate(wrap(effect, cw - 32)[:2]):
                text(pane, cx + 27, cy + 16 + row * 9, line, SUBTLE)
        blit(img, pane, bx, by)


def mockup(width: int, height: int, scale: int, palette: Palette, cache: dict, sheets: dict, page: str = "creatures",
           state: str = "partial") -> Image:
    mock = Mock(width, height, palette, cache, sheets)
    mock.chrome(page, state)
    if page == "traits":
        mock.traits()
    else:
        mock.creature_list(state, None if state == "empty" else "lumen_moth")
        if mock.lay["two_pane"]:
            mock.detail(state)
    out = Image(width * scale, height * scale)
    for y, row in enumerate(mock.image.px):
        wide = [c for c in row for _ in range(scale)]
        for oy in range(scale):
            out.px[y * scale + oy] = wide
    return out


def hud_mockup(sheets: dict) -> Image:
    small = Image(760, 96)
    gradient(small, 0, 0, 760, 96, "#5E86B8", "#3F6A4A")
    for index, (reticle, label, colour, progress, line) in enumerate((
            ("reticle_idle", "", SUBTLE, None, "Aim at a creature and hold Use"),
            ("reticle_target", "Unknown creature", TITLE, 0.62, "Scanning... 62%"),
            ("reticle_target", "Lumen Moth", CYAN, None, "Scanned - hold Use to rescan"),
            ("reticle_refused", "Gallery display", DANGER, None, "Cannot be scanned"))):
        cx, cy = 95 + index * 190, 36
        fill(small, cx - 4, cy, 9, 1, WHITE)
        fill(small, cx, cy - 4, 1, 9, WHITE)
        blit(small, sheets["hud"], cx - 12, cy - 12, HUD[reticle])
        if label:
            text(small, cx - text_width(label) // 2, cy + 16, label, colour)
        if progress is not None:
            blit(small, sheets["hud"], cx - 26, cy + 27, HUD["bar_frame"])
            u, v, w, h = HUD["bar_fill"]
            blit(small, sheets["hud"], cx - 25, cy + 28, (u, v, int(w * progress), h))
        text(small, cx - text_width(line) // 2, cy + (38 if progress is not None else 27 if label else 16), line, BODY)
    out = Image(760 * 2, 96 * 2)
    for y, row in enumerate(small.px):
        wide = [c for c in row for _ in range(2)]
        for oy in range(2):
            out.px[y * 2 + oy] = wide
    return out


# --- entry point -------------------------------------------------------------

def main(argv: list) -> int:
    check = "--check" in argv
    palette = Palette()
    sheets = {"panel": paint_panel(), "widgets": paint_widgets(palette), "hud": paint_hud()}
    ok = write_or_check(os.path.join(GUI_DIR, "bestiary", "panel.png"), sheets["panel"], check)
    ok &= write_or_check(os.path.join(GUI_DIR, "bestiary", "widgets.png"), sheets["widgets"], check)
    ok &= write_or_check(os.path.join(GUI_DIR, "scanner_hud.png"), sheets["hud"], check)
    cache = {}
    wanted = [a for a in argv if not a.startswith("--") and a in ALL_IDS]
    if "--skip-portraits" not in argv:
        for entity_id in wanted or ALL_IDS:
            portrait, shape = paint_portrait(entity_id, palette)
            cache[(entity_id, "portrait")], cache[(entity_id, "silhouette")] = portrait, shape
            ok &= write_or_check(os.path.join(GUI_DIR, "bestiary", "portrait", entity_id + ".png"), portrait, check)
            ok &= write_or_check(os.path.join(GUI_DIR, "bestiary", "silhouette", entity_id + ".png"), shape, check)
    if "--layout" in argv:
        for width, height in ((960, 540), (640, 360), (480, 270), (320, 240)):
            print(layout(width, height))
    if "--mockups" in argv:
        target = argv[argv.index("--mockups") + 1]
        for entity_id in ALL_IDS:                          # reuse the committed portraits when they were not just drawn
            for kind in ("portrait", "silhouette"):
                path = os.path.join(GUI_DIR, "bestiary", kind, entity_id + ".png")
                if (entity_id, kind) not in cache and os.path.exists(path):
                    cache[(entity_id, kind)] = Image.load(path)
        for scale in (2, 3, 4):
            mockup(1920 // scale, 1080 // scale, scale, palette, cache, sheets).save(
                os.path.join(target, "bestiary-scale-%d.png" % scale))
        mockup(640, 360, 3, palette, cache, sheets, page="traits").save(os.path.join(target, "bestiary-traits-scale-3.png"))
        mockup(640, 360, 3, palette, cache, sheets, state="empty").save(os.path.join(target, "bestiary-empty-scale-3.png"))
        mockup(320, 240, 3, palette, cache, sheets).save(os.path.join(target, "bestiary-small-window.png"))
        hud_mockup(sheets).save(os.path.join(target, "scanner-hud.png"))
        print("mockups written")
    print("gui: " + ("clean" if ok else "DRIFT" if check else "FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
