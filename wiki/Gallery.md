# Gallery & screenshot capture

NeroCreatures ships a **creative-only showcase scene** and a **client-side screenshot harness** that
photographs it. Together they exist so the project's Modrinth gallery can be re-rendered identically
after a model or texture change, instead of somebody hand-framing a dozen shots and hoping the next
person matches them.

They are debug tools. Neither can be triggered by accident, and neither writes anything about a
player.

- `/nerocreatures gallery` — **server side.** Builds the scene where you stand. Operator (level 2),
  player-only, creative-only.
- `/ncgallery capture` — **client side.** Rebuilds the scene, freezes the world and writes one PNG
  per shot.

> **Why two different roots?** A client-side command tree whose root shares a name with a server
> command **shadows** it: the client parser matches the root, fails on the server-only child and
> never forwards the command on. `/ncgallery` is deliberately not `/nerocreatures`.

## `/nerocreatures gallery`

Builds the whole scene centred on your feet, then tells you what it built.

```text
/nerocreatures gallery
/nerocreatures gallery clear
```

Requirements, in the order they are checked:

1. Permission level 2, like the rest of the `/nerocreatures` tree.
2. A **player** ran it — the scene is built relative to your position.
3. That player is in **creative**. The command rewrites a box roughly 100 × 80 × 16 blocks; it is
   not something a survival world should be able to reach.

Stand somewhere flat and open with about 60 blocks clear to the east and 45 to the north, or use the
capture harness, which stages the whole thing in mid-air.

### What gets built

Four spokes around you, far enough apart that a camera aimed at one has none of the others in frame.

| Spoke | Contents |
| ----- | -------- |
| **East** | The whole roster, twice: a **frozen** row (no AI, silent, invulnerable, persistent) for clean shots, and a **live** row behind glass, six blocks further out. Each pair is labelled. |
| **North** | The **boss arena** — an ember-and-ash ring with basalt speckle, crying-obsidian pillars and a glowstone cap, with the Cinder Tyrant frozen on the dais. |
| **South** | Both **pet** species on their themed patches (ice for the Glacite Wisp, moss for the Xertz Forager) and the **Terraforming Drone** hovering over a small work area. |
| **West** | Every NeroCreatures item — the eleven drops, the drone shell and every spawn egg — floating in a grid against a wall. |

The roster row is driven by the registry, not by a hand-written list, so a creature added to
NeroCreatures appears in the gallery automatically.

### Things the gallery deliberately does not do

The gallery is a photograph, not a save-file edit. Three specific restraints:

- **The boss starts no fight.** The Cinder Tyrant in the arena is a *display spawn*: it is created
  and added to the world without the spawn-finalisation step that starts a fight. Starting one would
  mean a boss bar, a `nerocreatures:boss_pressure` phase crossing, a companion-app broadcast and a
  full Minecraft day of natural-boss-spawn cooldown for that dimension — none of which belongs in a
  screenshot. Contribution tracking is only ever switched on by a deliberate summon, so **no
  contribution record can exist for a gallery boss**, by construction.
  - The cost is that **there is no boss bar in the boss shot**. If you want one, run
    `/nerocreatures summon-boss cinder_tyrant` — see [Bosses](Bosses.md).
- **Nothing is tamed and nothing is owned.** The pets are untamed and the drone is unowned. Taming a
  display pet would write an ownership row against you and spend one of your pet slots; deploying a
  display drone would do the same with a drone slot. Both are real, erasable personal data (see
  [Data storage](Data-Storage.md)), and a screenshot command has no business creating it.
- **Live hostiles cannot reach you.** The live row sits inside a sealed glass vivarium. Glass breaks
  line of sight, so nothing in there can target, chase or shoot the photographer. That is deliberate
  rather than relying on Peaceful difficulty — which most test worlds are not on, and which would
  stop half the roster existing in the first place.

Every display creature is invulnerable and persistent, so an exhibit cannot quietly die or despawn
part-way through a shoot.

### `/nerocreatures gallery clear`

Wipes the footprint: every block in the box back to air (from your feet upward, so the ground you
were standing on survives) and every non-player entity in it discarded. Run it **standing where you
built it**.

Always clear before rebuilding — otherwise a second set of creatures, stands and pads lands on top
of the first.

## `/ncgallery capture`

The client-side harness. It drives your own camera; it never reaches the server dispatcher.

```text
/ncgallery capture               # the full pass, at noon
/ncgallery capture midnight      # …or day / night / a raw tick number
/ncgallery capture shot my_shot  # grab the current view once, overlays hidden
```

A full pass:

1. Teleports you to the fixed origin **(0, 100, 0)** in your current dimension and pins the
   environment — daylight and weather cycles off, weather clear, time set.
2. Waits for the chunks, then runs `/nerocreatures gallery clear` followed by
   `/nerocreatures gallery`, so every run photographs a scene built from scratch. The floating
   cluster labels are then stripped (by scoreboard tag, so the item displays survive) and the litter
   swept up.
3. Hides the HUD, switches clouds off and disables view-bob, then flies the camera to each pose,
   waits for it to settle and writes the frame.
4. Restores your HUD, clouds, view-bob and flight state when it finishes.

**Why (0, 100, 0) and not a dedicated flat dimension?** NeroCreatures ships no dimension of its own,
and it will not depend on another mod for one. The gallery lays its own floor pads at your feet, so
staging it 100 blocks up makes the whole set a platform against open sky with the local terrain out
of frame. Reproducibility comes from clearing and rebuilding every run, not from a special world — so
the pass gives the same frames in any world. A superflat creative world gives the cleanest backdrop.

The world needs cheats enabled: the harness issues the two server-side gallery commands on your
behalf.

### The shot list

Written to `screenshots/nerocreatures/<name>.png` inside the game directory.

| Shot | What it frames |
| ---- | -------------- |
| `creature_lineup` | The frozen roster row, shot down the line from the west end. |
| `creature_hostiles` | The first four cells — Void Crawler, Lunar Stalker, Asteroid Worm, Plasma Slime. |
| `creature_machines` | The far end of the row — Rogue Drone, Rogue Android and the pets. |
| `creature_vivarium` | The live row behind its glass. |
| `boss_arena` | The whole ember arena with the Cinder Tyrant on the dais. |
| `boss_closeup` | The Tyrant, tight. |
| `pets` | Glacite Wisp and Xertz Forager on their patches. |
| `drone_work_area` | The Terraforming Drone over its work area. |
| `drops` | The eleven creature drops and the drone shell. |
| `spawn_eggs` | Every spawn egg. |
| `gallery_overview` | The whole set from above — the "what is in this mod" card. |

Camera positions live next to the shot list in the harness and mirror the cluster positions in the
scene builder. **If you move a cluster, move its camera in the same change.**

## How the screenshots reach Modrinth

The repository's `modrinth-gallery.yml` workflow mirrors the tracked PNGs to the Modrinth project
gallery whenever they change. It reads them from the **NeoForge 26.2 client run directory**:

```text
neoforge/versions/26.2/runs/client/screenshots/nerocreatures/
```

That is exactly where the harness writes when run from the NeoForge 26.2 dev client, so the workflow
path and the harness output line up with no copying step. Run the pass from that cell, review the
PNGs, commit them, and the sync happens on push.

The workflow wipes and re-uploads the gallery in a fixed order, featuring the first shot as the
project card. Shots not named in that order list are appended alphabetically, so a newly added shot
still syncs without touching the workflow.

Native-resolution captures can be several megabytes; downscale before committing if the window is
large.

## See also

- [Commands](Commands.md) — the rest of the `/nerocreatures` tree
- [Bestiary](Bestiary.md) — the creatures the gallery lines up
- [Bosses](Bosses.md) — what a real, contribution-tracked fight looks like
- [Pets & Drones](Pets-and-Drones.md) — what taming and deploying actually store, and why the
  gallery does neither
