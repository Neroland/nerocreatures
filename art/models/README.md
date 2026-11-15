# Model specs

One JSON file per creature, `<id>.json`. A spec is the single source for the creature's model,
its UV layout and its animation. The Java model class, the textures and the preview sheet are all
produced from it; nobody edits those by hand.

```
python tools/art/run.py            # palette, validate, pack UVs, paint, Radiant
python tools/art/run.py --check    # change nothing, exit 1 on anything stale or invalid
python tools/art/preview.py <id>   # contact sheet to build/art-preview/
```

Everything under `tools/art/` is Python standard library only.

## Space and units

- Model space is the game's: **1 unit = 1 texel = 1/16 block**, **y points down**, the ground is
  `y = 24`, the creature **faces -z**, and **+x is its left**.
- Angles are **degrees** in the spec. A part applies them as Z, then Y, then X (`Rz * Ry * Rx`),
  the order a `ModelPart` uses. Positive `x` tips the front (-z side) of a part down and swings a
  hanging limb backward; when in doubt, check a pose in the preview
  rather than reasoning about signs.
- Times are whole **ticks** (20 per second).

## Top level

| Key | Meaning |
|---|---|
| `format` | `1` |
| `id`, `name` | registry id (must match the file name) and display name |
| `habitat` | a habitat in `art/palette.json`; sets the ramps and the accent |
| `size_class` | `S`, `M`, `L`, `XL`, `XXL`; sets the box, animated-part and sheet-size budgets |
| `silhouette` | the one-line shape this model must read as at 16 blocks |
| `hitbox_guide` | `[width, height]` in blocks, for reference only |
| `texture` | `{"width", "height"}` of the sheet |
| `render_type` | `cutout` (default) or `translucent` (five named species only) |
| `renderer` | `{"scale", "shadow"}` passed to the renderer, plus an optional `"cull"` (below) |
| `materials` | 1 to 3 ramp names from the habitat; the first is the main body and must pass the ground-contrast rule |
| `accent` | the habitat accent ramp used for glow (default `accent`); `accent_2` only where the palette has a second |
| `glow_organs` | which organs carry glow, for the reviewer |
| `radiant` | `null`, or for trait carriers a map `material ramp -> pearl ramp` |
| `budget_exception` | optional `{"boxes": n}`, signed off in review |
| `variants` | optional per-variant look (below) |
| `glow_rules` | optional glow clamps and flickers by visual state (below) |
| `parts`, `procedural`, `clips` | below |
| `uv_fill` | written by the packer |

## Parts

`parts` is a flat list; a part names its `parent` (or `null`), and parents come first. At most three
levels deep.

```json
{"name": "neck", "parent": "body", "pivot": [0, -2, -8], "rotation": [8, 0, 0], "cubes": [
  {"name": "neck", "origin": [-2, -3, -5], "size": [4, 5, 5], "uv": [32, 14]}
]}
```

- `pivot` is the joint, relative to the parent's pivot (to the model origin for a top-level part).
- `rotation` is the rest pose.
- A cube's `origin` is its minimum corner **relative to its part's pivot**; `size` is whole units.
  Optional `inflate` grows it on every side without changing its UVs.
- `uv` is the top-left of the cube's standard box unwrap. The packer writes it; do not hand-edit.
- `uv_like: "<part>"` on a part makes it share that part's texels, mirrored: the way a right leg
  reuses the left leg. The cubes must match by name and size. The packer writes the shared `uv` and
  `mirror: true` into the cubes, so a reader of the spec needs no special case.
- `visible_when` on a part shows it only for some synced visual state (a **condition**, below):
  `{"stage": [2, 3]}`, `{"variant": [0]}`, `{"form": [1]}`, `{"phase": [3]}`, `{"pose": ["ability"]}`
  or `{"flag": "saddled"}`. A hidden part hides its children.

### Conditions

`visible_when`, a spin's `when` and a glow rule's `when` share one shape. Every key present must
hold; a missing key means "any".

| Key | Values | Synced from |
|---|---|---|
| `stage` | list of 0-3 | `stage` (growth, antlers, plates, barnacles) |
| `variant` | list of 0-7 | `variant` (ore family, skin) |
| `form` | list of 0-7 | `form` (host / sporeling / pod, folded plates, column) |
| `phase` | list of 0-3 | boss phase, 0 outside a fight |
| `pose` | list of `idle`, `ability`, `ability_2`, `rest` | the synced pose |
| `flag` | a name or a list, all required: `saddled`, `panniers`, `radiant`, `alpha` | flags |

### Faces

Painters address a cube face by name. In the sheet, a box at `uv = [u, v]` with size `[w, h, d]`:

| Face | Side of the box | Rectangle | Runs |
|---|---|---|---|
| `up` | top (min y) | `u+d, v, w, d` | x left to right; back of the creature at the top row |
| `down` | underside (max y) | `u+d+w, v, w, d` | same as `up` |
| `west` | min x | `u, v+d, d, h` | back to front, left to right |
| `north` | front (min z) | `u+d, v+d, w, h` | min x to max x |
| `east` | max x | `u+d+w, v+d, d, h` | front to back, left to right |
| `south` | back (max z) | `u+d+w+d, v+d, w, h` | max x to min x |

On the four side faces the top row is the top of the box. `Painter.flanks()`, `.top()`, `.under()`
and `.around()` hand these back already turned so that `x = 0` (or `y = 0`) is the creature's front.

## Procedural motion

```json
"procedural": {
  "swing": [{"part": "leg_fl", "phase": 0, "amplitude": 36}],
  "wave":  [{"part": "tail", "axis": "y", "frequency": 0.09, "phase": 0, "amplitude": 12, "fade_with_walk": true}],
  "look":  [{"part": "head", "yaw": 0.6, "pitch": 0.7, "limit": 45}]
}
```

- `swing`: walk cycle on the part's x axis, `cos(walkPos * 0.6662 + phase) * amplitude * walkSpeed`.
- `wave`: `sin(age * frequency + phase) * amplitude`, faded out by walk speed unless `fade_with_walk` is false.
- `look`: the share of head yaw and pitch the part takes, each clamped to `limit` degrees.
- `spin`: a continuous turn, for rings and rotors that go all the way round (a `wave` only rocks):
  `{"part": "ring", "axis": "y", "speed": 4.5, "phase": 0, "when": {"pose": ["idle"]}}` adds
  `(age * speed + phase) mod 360` degrees on the axis. `speed` is degrees per tick, signed
  (counter-rotating rings: one negative). `when` is optional; while it fails the spin is off, and
  where it names poses the spin fades out and back in over the 4-tick pose blend (the ring winds
  back to rest during that fade, so let the action's own track carry the ring if it must stop
  square). Spins are never faded by walking. A spun part counts as animated.

`phase`, `amplitude`, `limit` and `speed` are degrees; `frequency` is radians per tick.

## Clips

Names are fixed: `idle`, `move`, `ability`, `ability_2`, `hurt`, `rest`, `phase_1`..`phase_3`.
`idle`, `move` and `hurt` are required.

```json
"ability": {"length": 40, "loop": false, "key_pose": 22, "telegraph": 10, "damaging": false,
  "glow": [{"t": 0, "v": 0.35}, {"t": 10, "v": 1.0}, {"t": 30, "v": 1.0}, {"t": 40, "v": 0.35}],
  "tracks": {
    "neck": {"rot": [{"t": 0, "v": [0, 0, 0]}, {"t": 14, "v": [-42, 0, 0], "ease": "smooth"}]}
  }}
```

- A track has a `rot` channel (degrees) and/or a `pos` channel (units). Values are **added to the
  rest pose**, so `[0, 0, 0]` is "at rest".
- In an action clip (`ability`, `ability_2`, `rest`) a track may say `"blend": "add"`: its values
  are added to whatever the held layer has on that part (the idle or the phase stance) instead of
  replacing it. Use it for a dip or a recoil on top of a stance, e.g. the Rime Matriarch's beam
  body dip while phase 3 holds her a block up.
- A keyframe's `ease` (`linear` default, or `smooth`) shapes the segment arriving at it. Before the
  first key and after the last, the value holds.
- `glow` is an optional 0 to 1 track for the glow layer. Without it the glow sits at the dim level.
- `key_pose` is the tick the preview draws. `note` is free text.
- Limits: 8 keys per channel, 12 parts per clip, `hurt` is exactly 6 ticks, a looping channel
  starts at tick 0 and ends at `length` on the same value, a `damaging` clip declares a `telegraph`
  of 10 ticks or more.

### How clips are played

`tools/art/spec.py` `evaluate()` is the reference; the preview uses it and the in-game interpolator
must give the same pose.

1. Reset every part to its rest pose, then apply `swing`, `wave` and `look`.
2. Base layer: `idle` on the age clock, weighted `1 - walkSpeed`, plus `move` weighted `walkSpeed`.
   The `move` clock is the walk cycle: tick `= frac(walkPos * 0.6662 / 2pi) * length`, so tick 0 is
   the moment a phase-0 swing limb is furthest back. In boss phase N, `phase_N` replaces `idle`.
3. Action layer: when the synced pose is `ability`, `ability_2` or `rest`, that clip plays on the
   pose's own tick counter (held at its last tick unless it loops). It **replaces the base layer
   only on the channels it keys** (part x `rot`/`pos`), or adds to it on `"blend": "add"` tracks;
   every part and channel it does not key keeps the base layer, so a `phase_N` stance (or the idle
   motion of an untouched part) carries on under an ability.
4. Every pose change cross-fades over 4 ticks **from what was showing**: the base layer, or the
   previous action clip frozen where it stopped. So an ability fired from a held `rest` starts from
   the rest pose (Frost Lurker: the eruption starts buried even with a surface `idle`), and the end
   of an action eases back to idle instead of snapping. Procedural motion is not faded (bar a spin
   whose `when` names poses).
5. `hurt` is added on top at full weight for its 6 ticks.
6. `glow_rules` (below) adjust the glow last.

## Glow rules

`glow_rules` is a list applied in order to the clip-driven glow (0 to 1), after `hurt`:

```json
"glow_rules": [
  {"when": {"form": [0]}, "max": 0.0},
  {"when": {"stage": [1]}, "min": 1.0, "flicker": 4}
]
```

- `when`: a condition; without one the rule always applies.
- `min` / `max`: clamp. `{"max": 0.0}` turns the glow off; `{"min": 1.0}` holds it full.
- `flicker: n`: on every other `n`-tick step of the age, drop to `low` (default the dim 0.35; never
  raises the glow). The art direction's weak-point rule (full, flickering every 4 ticks) is
  `{"min": 1.0, "flicker": 4}`.

Use them for glow that follows synced state rather than a clip: Prism Stag antlers lit only at
`form` 1 (`{"when": {"form": [0]}, "max": 0.35}` or `0.0`), the Shardback seam flickering at `form` 1,
the Dust Devil core flickering in its whirl (`stage` 1). The glow layer is one texture, so a rule
dims or lights every glow texel (eyes included); keep that in mind when painting.

## Variants

`variants` gives each synced `variant` (0-7) its own look. Keys are the variant numbers as strings:

```json
"variants": {
  "0": {"name": "coal", "tint": "#5A5A60", "glow": "#9AA0B0"},
  "2": {"name": "iron", "glow": "#E8C9A8", "texture": true},
  "6": {"name": "diamond", "glow": "#5FE8E0"}
}
```

- `tint`: `#RRGGBB` multiplied into the whole base texture (like the alpha darkening; white = no
  change). It colours the whole model, so it suits creatures whose variant is the whole body.
- `glow`: `#RRGGBB` for the glow layer while that variant is synced (paint the glow texels white or
  near-white so the colour reads true). Ore families: Lode Tortoise nodule glints, the Strata Hound
  back-stripe. An alpha's crimson still wins; a variant glow wins over Radiant's white-gold.
- `texture: true`: the variant uses its own base sheet `textures/entity/<id>_v<n>.png` (same
  layout, painted by the habitat painter). Until that file exists the base texture is used and
  `gen_entity_models.py` prints a note. Radiant's texture wins over a variant texture.
- `name` is for people.

Variants without an entry use the base look. `preview.py` draws one cell per declared variant.

## Render culling

The generator measures how far the model reaches from the entity's feet (every cube corner at rest
and at every clip key, times `renderer.scale`, plus 10% and 1/8 block for waves and spins) and
writes it into `ART`. The game culls with the hitbox grown to that reach (frustum and view distance
still apply; never "always visible"), so a long model such as the Drift Leviathan no longer pops
out at the screen edges. To override the measurement:

```json
"renderer": {"scale": 1.0, "shadow": 3.0, "cull": {"radius": 11.0, "up": 5.0, "down": 0.5}}
```

`radius` is horizontal (any yaw), `up` above the feet, `down` below, in blocks before the entity's
own scale attribute. Name only the values to replace.

## Translucency

`"render_type": "translucent"` (only the five species the art direction allows: `dust_devil`,
`static_swarm`, `plasma_slime`, `spore_drifter`, `glacite_wisp`) draws the body with the entity
translucent type, so texel alpha below 255 shows through (Plasma Slime's shell at alpha 168). Every
other species is cutout: texels are fully opaque or fully clear.

## Preview states

`preview.py` hides parts by `visible_when` as the game does. Every sheet adds a **states** row with
one cell per value the spec reacts to (each `visible_when`, spin and glow-rule stage, form, phase and
flag, and each declared variant with its tint, glow colour and texture). `--state` sets the state
every cell starts from, so no wrapper script is needed:

```
python tools/art/preview.py lode_tortoise --state stage=3,variant=2
python tools/art/preview.py dune_loper --state flags=saddled+panniers
```

`ability_2` is a valid clip for any species whose spec declares it (Slag Beetle cough, Strata Hound
pointer); checks treat a declared clip as allowed.

## What the model generator reads

Per spec: `texture`, `render_type`, `renderer` (with `cull`), `variants`, `glow_rules`; every
part's `name`, `parent`, `pivot`, `rotation`, `visible_when`; every cube's `origin`, `size`, `uv`,
`mirror`, `inflate`; all of `procedural` (with `spin`); and every clip's `length`, `loop`, `tracks`
(with `blend`) and `glow`. It ignores `silhouette`, `hitbox_guide`,
`glow_organs`, `key_pose`, `note`, `uv_like`, `uv_fill`, `materials`, `accent` and `radiant`.
