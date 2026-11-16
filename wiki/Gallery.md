# Gallery & screenshot capture

NeroCreatures ships a creative-only showcase scene, the [Menagerie](Menagerie.md), and a
**client-side screenshot harness** that photographs it. Together they let the project's screenshots
be re-rendered identically after a model or texture change.

- `/nerocreatures gallery` — **server side.** Builds the Menagerie where you stand (operator level 2,
  player-only, creative-only). `gallery grid` builds the compact flat layout. See
  [Menagerie](Menagerie.md) and [Commands](Commands.md).
- `/ncgallery capture` — **client side.** Rebuilds the scene, freezes the world and writes one PNG
  per shot.

> **Why two different roots?** A client-side command tree whose root shares a name with a server
> command **shadows** it: the client parser matches the root, fails on the server-only child and
> never forwards the command on. `/ncgallery` is deliberately not `/nerocreatures`.

## `/ncgallery capture`

```text
/ncgallery capture [time]               the Menagerie pass (160 shots)
/ncgallery capture grid [time]          the grid pass (9 shots)
/ncgallery capture set <set> [time]     one set (see below)
/ncgallery capture shot <name>          grab the current view once
```

`time` is `day`, `noon`, `night`, `midnight` or a tick number (default `noon`). The world needs
cheats: the harness runs server commands.

A full pass:

1. Teleports you to a fixed origin 100 blocks up (0, 100, 0), stops the day cycle and weather, and
   waits 120 ticks for the chunks to load.
2. Runs `nerocreatures gallery clear`, then `nerocreatures gallery` (or `gallery grid`), then removes
   the floating bay titles **by tag** (`nerocreatures_gallery_label`).
3. Hides the HUD, clouds and view bob, flies, and for each shot poses the camera, waits 12 ticks and
   grabs the frame.

Ability shots first run `nerocreatures gallery demo <slot>`, wait for the moment in the demo the shot
is timed for, grab, then wait for the rest of the demo so the next shot starts from idle. The pass
needs a render distance of 8 or more and stops with a message if it is lower. A full pass takes about
5 to 6 minutes. Run it in a superflat creative world for the cleanest backdrop.

The camera positions are computed from the same layout tables the server builds from, so the scene
and the shots cannot drift apart.

## Shot sets

| Set | Shots | File names | Count |
| --- | --- | --- | --- |
| `overview` | top-down, three-quarter, down Central Street | `overview_top`, `gallery_overview`, `overview_street` | 3 |
| `pavilions` | each habitat bay from its street, and the Leviathan from below | `pavilion_<bay>`, `pavilion_leviathan` | 11 |
| `creatures` | a portrait of every creature (bosses in phase 1) | `creature_<id>` | 38 |
| `abilities` | the same portrait during the creature's demo, and each boss pose | `ability_<slot>` | 46 |
| `alphas` | each alpha beside its base form | `alpha_<id>` | 16 |
| `radiant` | each Radiant morph, and the whole Trait Wall | `radiant_<id>`, `trait_wall` | 14 |
| `bosses` | each alcove, and each phase pose | `boss_<id>`, `boss_<id>_p<n>` | 16 |
| `rows` | the habitat blocks (and each block), gear, drops, hatchery, kiosk, both paddock yards | `blocks_row`, `block_<id>`, `gear_row`, `drops_row`, `hatchery`, `kiosk`, `paddock_lopers`, `paddock_mantas` | 16 |

The grid pass shoots `creature_lineup`, `creature_hostiles`, `creature_machines`, `boss_closeup`,
`drops`, `spawn_eggs`, `grid_radiant`, `grid_alphas` and `grid_overview`.

Files land in `screenshots/nerocreatures/<name>.png` in the game directory, at the window's
resolution. Portraits are framed for a centre square crop. `gallery_overview` keeps its old name so
the published lead image does not move.

The companion app's creature portraits are rendered by their own export tool from the same model
specs; the `creature_<id>` and `radiant_<id>` shots are the in-game reference they are compared with.

## Privacy

Neither the scene nor the harness writes anything about a player. The harness drives only your own
client; the server side is described on the [Menagerie](Menagerie.md) page.
