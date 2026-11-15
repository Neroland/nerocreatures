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
| `renderer` | `{"scale", "shadow"}` passed to the renderer |
| `materials` | 1 to 3 ramp names from the habitat; the first is the main body and must pass the ground-contrast rule |
| `accent` | the habitat accent ramp used for glow (default `accent`); `accent_2` only where the palette has a second |
| `glow_organs` | which organs carry glow, for the reviewer |
| `radiant` | `null`, or for trait carriers a map `material ramp -> pearl ramp` |
| `budget_exception` | optional `{"boxes": n}`, signed off in review |
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
- `visible_when` on a part shows it only for some synced visual state: `{"stage": [2, 3]}`,
  `{"variant": [0]}`, `{"form": [1]}` or `{"flag": "saddled"}` (flags: `saddled`, `panniers`,
  `radiant`, `alpha`). Several keys must all hold.

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

`phase`, `amplitude` and `limit` are degrees; `frequency` is radians per tick.

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
   pose's own tick counter (held at its last tick unless it loops) and cross-fades against the base
   layer over 4 ticks. Procedural motion is not faded.
4. `hurt` is added on top at full weight for its 6 ticks.

## What the model generator reads

Per spec: `texture`, `render_type`, `renderer`; every part's `name`, `parent`, `pivot`, `rotation`,
`visible_when`; every cube's `origin`, `size`, `uv`, `mirror`, `inflate`; all of `procedural`; and
every clip's `length`, `loop`, `tracks` and `glow`. It ignores `silhouette`, `hitbox_guide`,
`glow_organs`, `key_pose`, `note`, `uv_like`, `uv_fill`, `materials`, `accent` and `radiant`.
