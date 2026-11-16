# Commands

NeroCreatures adds one command tree, `/nerocreatures`, for server owners and operators. There are no
player-facing commands: everything a player does with a creature, they do in the world.

(There is also a client-only `/ncgallery` tree — the screenshot harness. It never reaches the server
and is documented on the [Gallery](Gallery.md) page.)

**Permission level 2** (`gamemasters`) is required for the whole tree, matching `/neroland` and
`/neroquests`. `summon-boss` and `export` genuinely need it; `list` and `caps` are harmless and are
held at the same level only so the tree has one consistent answer to "who may run this".

**Output goes to whoever ran the command**, never broadcast to other operators, and never into
`latest.log` under the `logAdminCommands` game rule.

## `/nerocreatures list`

The registered roster, with how many of each creature are **loaded in your current dimension** and
the spawn-table lines that place them.

```text
12 registered creatures — loaded counts are for nerospace:cindara:
  nerocreatures:void_crawler — 3 here [dark_biomes w30 1-2]
  nerocreatures:lunar_stalker — 0 here [moon_biomes w24 2-4]
  …
  nerocreatures:terraforming_drone — 1 here [no natural spawn]
Summoned boss fights in progress: 0
```

The counts come from two bounded queries over your level (the mod's two class roots — see
[Spawning](Spawning.md#population-caps)), tallied by type. They say nothing about who owns anything.

## `/nerocreatures caps`

What the population and ownership caps are set to, and what is using them here.

```text
NeroCreatures population caps:
  dimension nerospace:cindara — 41 / 200
  this chunk — 2 / 8
  per player — pets 3, drones 2
  spawns enabled — true · weight ×1.0
```

The dimension figure is the same bounded, cached count the spawn sweep uses: it stops at the cap, so
a world well over its cap still reads as "at the cap". The only question it exists to answer is "is
there room?".

## `/nerocreatures summon-boss <boss>`

Starts a **contribution-tracked** boss fight where you are standing, through the same public API a
future NeroEvents raid will use — so what you test with this command is exactly what an event does.

```text
/nerocreatures summon-boss cinder_tyrant
/nerocreatures summon-boss nerocreatures:cinder_tyrant
```

A bare id is read as `nerocreatures:`. Tab completion lists every registered boss.

- If you run it as a player, **you become the fight's initiator**: registered as a zero-damage
  participant so you are on the reward list even if you never land a hit. Nothing else about you is
  recorded. Run from the console or a command block, the fight simply has no initiator.
- The summon is **refused** if the dimension or the chunk is already at its creature cap. Raise the
  cap; the command will not route around it.
- A summoned fight pays the enhanced, contribution-split rewards. See
  [Bosses](Bosses.md#how-a-boss-turns-up) for the exact rule, and for why a boss you merely *found*
  pays differently.

## `/nerocreatures export <player>`

**This is the POPIA/GDPR data-access path.** It writes exactly one player's own NeroCreatures record
as JSON to a file under the world folder (`data/nerocreatures/exports/export-<time>-<random>.json`)
and replies with the file's path and size only. Nobody else's rows appear, ever — that is a property
of the export itself, not of how it is delivered. Nothing personal is printed, so running it from the
console or RCON does not put the record in the server log.

```text
/nerocreatures export Kestrel
/nerocreatures export 7f0a1c3e-…-b21d          # works for a player who has left
```

A player is given as an **online player's name or a raw player UUID**, not an entity selector,
because a data-access request has to work for someone who is not connected.

```json
{
  "player": "7f0a1c3e-…-b21d",
  "exported_at": 1750000000000,
  "ownership": {
    "last_updated": 1749999000000,
    "owned": [
      { "entity": "…", "kind": "pet", "type": "nerocreatures:glacite_wisp",
        "dimension": "nerospace:glacira" }
    ]
  },
  "boss_contribution": { "last_updated": 0, "boss_contribution": [] }
}
```

Operator permission is required because this is somebody's personal record. A player asking for
their own data asks an operator to run it — see [`PRIVACY.md`](https://github.com/Neroland/nerocreatures/blob/main/PRIVACY.md). To *erase* rather than
read, use Core's own `/neroland data eraseme` (player) or `/neroland data erase <uuid>` (operator),
which purges the player across every Nero mod at once — see [Data storage](Data-Storage.md).

The whole export is written, however long. Where the file system supports it, the file is readable
by the server's own account only. Give it to the player securely, then delete it.

## `/nerocreatures budget`

Measures how much server tick time NeroCreatures' creatures cost, per species and in total.

| Form | Does |
| --- | --- |
| `/nerocreatures budget` | starts a 10-second sample; run it again for progress, then the result |
| `/nerocreatures budget start [seconds]` | starts a fresh sample of 1 to 60 seconds |
| `/nerocreatures budget stop` | ends the sample now and shows the result |

The result lists each species id with its milliseconds per server tick and how many were ticking on
average, most expensive first, then the total. Outside a sample the measurement costs one check per
creature tick. The output goes to you alone and names species, never players or places.

Far creatures (no player within `farTickDistance`, default 48 blocks) run their own AI work on one
tick in `farTickInterval` (default 4). Owned, leashed, ridden and fighting creatures never slow down.

**Measured cost (0.5.0).** A 200-creature world with every species mixed in, on a NeoForge 1.21.1
dedicated server: **2.0 ms per tick** with every creature at full rate (players near all of them) and
**1.6 ms per tick** with the default far-tick slowing (no player near). One of each of the 35 species
costs 0.55 ms. On a NeoForge 26.3 dedicated server the same world costs **2.1 ms** and **1.6 ms**, and
one of each species 0.58 ms. No species costs more than 0.05 ms per creature. Builders can repeat the measurement with the dev-only probe:
`gradlew :neoforge:<mc>:runBudgetProbe -PbudgetProbe` (a throwaway flat world; it writes
`runs/probe/budget-probe-<mc>.txt` and stops on its own).

## `/nerocreatures telemetry-test`

Fires one synthetic crash-reporting event, to confirm end-to-end reporting on a real jar.

It answers honestly when nothing was sent — because you opted out, or because the build carries no
DSN. See [Telemetry](Telemetry.md).

## `/nerocreatures gallery`

Builds the **Menagerie** at your feet: a walkable showcase of every creature (with signs and demo
plates), every boss in each phase, the alphas, the Radiant morphs, a rideable mount paddock and working
rows of every block and gear item. See [Menagerie](Menagerie.md).

```text
/nerocreatures gallery                 build the Menagerie (refused within 160 blocks of another gallery)
/nerocreatures gallery grid            build the compact flat layout instead
/nerocreatures gallery tour            go to tour stop 1 of the nearest Menagerie
/nerocreatures gallery tour <n>        go to stop n (1 to 25); the chat line has Back and Next
/nerocreatures gallery demo <slot>     play one exhibit's demo, as its plate would
/nerocreatures gallery clear           remove the nearest gallery within 96 blocks
```

Gated harder than the rest of the tree: a **player**, in **creative** (checked when the command runs).
The Menagerie rewrites a box of 117 x 111 blocks, 21 high, from your feet up; the layer below your
feet is never touched.

It writes nothing about any player. Bosses are posed and start no fight, bar or cooldown; no display
is tamed, owned or counted; demo plates play visuals only. `clear` removes **only the entities the
gallery placed** (by tag) plus dropped items and XP: your own pets, mounts and other animals inside the
box stay. With no gallery nearby, `clear` clears the old 0.4 showcase box around you the same way.

The client-side `/ncgallery capture` screenshot harness that photographs it is on the
[Gallery](Gallery.md) page.

## Commands NeroCreatures deliberately does not add

- **No spawn command.** Spawn eggs already exist for every creature except the Terraforming Drone,
  and `/summon` already works.
- **No "give me a pet" or "untame" command.** Taming is a thing you do to an animal in front of you;
  ownership is checked at tame time and would be trivially defeated by a command that skipped it.
- **No cap override command.** The caps are configuration, hot-reloadable with
  `/neroland config reload`. A per-session override would be a second source of truth.

## See also

- [Spawning](Spawning.md) — what `list` and `caps` are reporting on
- [Bosses](Bosses.md) — what `summon-boss` starts
- [Menagerie](Menagerie.md) — what `gallery` builds, the demo plates, the tour and the paddock
- [Gallery](Gallery.md) — the capture harness that photographs it
- [Data storage](Data-Storage.md) — what `export` exports, and how to erase it
- [Link module](Link-Module.md) — the companion-app equivalent of the read-only half
