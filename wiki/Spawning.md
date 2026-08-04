# Spawning

Where NeroCreatures put themselves, why nothing appears on Earth, and every lever a server owner
has over it.

> **The short version:** creatures spawn on **off-Earth biomes**, chosen by Neroland Core's
> `neroland:space/*` biome tags. With no planet mod installed those tags are empty, so nothing
> spawns anywhere. That is by design, not a bug.

## Earth stays quiet

NeroCreatures does not touch vanilla biomes at all. Every creature is placed against a **Core space
biome tag**, and those tags start out empty: Core ships them with `"required": false` entries for
planets that may or may not be present (Nerospace's worlds, Ad Astra's), so on a server with no
planet mod installed every tag has no members and every spawn rule matches nowhere.

Nothing special-cases the Overworld. It is simply not in the data.

| Tag | What belongs in it |
| --- | --- |
| `neroland:space/dark_biomes` | Dim, sunless or ash-choked worlds |
| `neroland:space/moon_biomes` | Airless, low-gravity moons |
| `neroland:space/crystalline_biomes` | Crystal fields and glass plains |
| `neroland:space/asteroid_biomes` | Rubble fields and asteroid belts |
| `neroland:space/planet_biomes` | The umbrella: every off-Earth surface |

A pack can add its own biomes to any of these with an ordinary datapack tag file — that is the
supported way to make NeroCreatures spawn on a planet it has never heard of.

## The spawn table

Every line is declared in code, not in a datapack, and the whole table is public:

| Creature | Biome tag | Weight | Group |
| --- | --- | ---: | --- |
| Void Crawler | `dark_biomes` | 30 | 1–2 |
| Lunar Stalker | `moon_biomes` | 24 | 2–4 |
| Plasma Slime | `crystalline_biomes` | 20 | 1–3 |
| Plasma Slime | `dark_biomes` | 8 | 1–2 |
| Xertz Forager | `crystalline_biomes` | 14 | 1–3 |
| Glacite Wisp | `moon_biomes` | 12 | 1–2 |
| Glacite Wisp | `crystalline_biomes` | 6 | 1–2 |
| Space Pirate | `planet_biomes` | 10 | 2–3 |
| Crystal Golem | `crystalline_biomes` | 8 | 1 |
| Asteroid Worm | `asteroid_biomes` | 6 | 1 |
| Rogue Drone | `dark_biomes` | 6 | 1–2 |
| Rogue Android | `dark_biomes` | 3 | 1 |
| Cinder Tyrant | `dark_biomes` | 1 | 1 (**gated** — see [Bosses](Bosses.md)) |
| Terraforming Drone | — | — | never spawns; it is [deployed](Pets-and-Drones.md) |

**Weights are local, not global.** A candidate position only ever picks between the rules that
apply *there*, so the Asteroid Worm's weight of 6 competes with nothing at all in an asteroid field
— it is the only line in that tag. Compare weights within a column, never across the table.

**No dimension guard.** Every line is biome-tagged only. A dimension guard would silently exclude
any planet that reuses a vanilla dimension type — Nerospace's Greenxertz reuses
`minecraft:overworld`, which cannot be tagged without dragging the real Overworld in with it — and
no vanilla Earth biome is a member of any `neroland:space/*` tag, so the guard would cost a planet
and buy nothing.

## How placement works

NeroCreatures runs **its own placement sweep** on the server tick rather than adding entries to
biome spawn settings. That is a deliberate portability decision: NeoForge does biome modification
with JSON modifiers, Forge with an event and Fabric with the Fabric API, and splitting the most
gameplay-visible behaviour in the mod across three data systems is the fastest way to make six build
targets behave differently.

The sweep is shaped to feel like vanilla's:

- **Player-anchored.** Candidates are drawn in a ring **24–48 blocks** around a player — far enough
  not to pop into view, close enough to matter. (Vanilla uses the same 24-block floor.)
- **Budgeted.** One sweep every **40 ticks** (twice a second), at most **8** players considered per
  level, at most **3** candidate positions each, and at most **2** groups actually placed per level
  per sweep. The worst case is a small fixed number of heightmap reads and tag tests, and it does
  not grow with world size.
- **Vanilla placement rules.** Each candidate goes through the same "is this a legal spawn position"
  and "do this creature's spawn rules pass" checks vanilla uses, plus a collision check — so light
  levels, block validity and difficulty behave exactly as they do for a vanilla mob. Peaceful stops
  everything, including the harmless pets.
- **Capped at placement time**, not cleaned up afterwards.

## Population caps

Two caps, both counted from what is **loaded**, both checked before anything is placed.

| Setting | Default | What it means |
| --- | ---: | --- |
| `maxCreaturesPerChunk` | 8 | Most NeroCreatures allowed in one chunk column |
| `maxCreaturesPerDimension` | 200 | Most NeroCreatures loaded in one dimension |

Set either to **0** and NeroCreatures stops existing in that scope — a legitimate way to disable the
mod's spawning without disabling the mod.

The dimension count is deliberately **bounded and cached**: it stops counting at the cap and says
nothing about how far past it a world might be, because the only question anyone asks of it is "is
there room?". It is cached for one second per dimension, so a sweep costs roughly one query.

Caps count **two class roots** — the mod's own creature base *and* the tameable pets, which derive
from vanilla's `TamableAnimal` instead. A wild pet is inside the caps exactly like a monster.

Everything that is placed on purpose is **persistent** and never despawns: bosses, structure
placements, event waves, tamed pets and deployed drones. Everything that spawned naturally despawns
like a vanilla monster, which is what lets the population come back down.

## Configuration

All of these live in `config/nerocreatures.properties` and are **server-authoritative** —
hot-reloadable with `/neroland config reload`.

| Key | Default | Effect |
| --- | ---: | --- |
| `spawnsEnabled` | `true` | Master switch for the sweep |
| `globalSpawnWeightMultiplier` | `1.0` | Scales every weight; `0.0` stops all spawning |
| `maxCreaturesPerChunk` | `8` | Per-chunk cap |
| `maxCreaturesPerDimension` | `200` | Per-dimension cap |
| `hostileAggressionMultiplier` | `1.0` | Attack damage and follow range, applied once at spawn |
| `naturalBossSpawnsEnabled` | `true` | Whether a boss may appear on its own — see [Bosses](Bosses.md) |
| `maxPetsPerPlayer` | `3` | See [Pets & Drones](Pets-and-Drones.md) |
| `maxDronesPerPlayer` | `2` | See [Pets & Drones](Pets-and-Drones.md) |

Use [`/nerocreatures caps`](Commands.md) to see the caps and what is currently using them, and
[`/nerocreatures list`](Commands.md) for the roster with live counts in your dimension.

## For event mods: `InvasionBudget`

NeroCreatures ships a public helper, `spawn/InvasionBudget`, for a mod that wants to put a **wave**
of creatures into a world — a NeroEvents raid, for instance. Nothing in NeroCreatures 0.1.0 uses it;
it exists so the mod that needs it does not have to reach around the caps to get it.

```java
InvasionBudget.Handle wave = InvasionBudget.reserve(level, 12);
InvasionBudget.spawnWithin(wave, ModEntities.SPACE_PIRATE.get(), pos);   // up to 12 times
// …the event runs…
InvasionBudget.close(wave);                                              // the world is tidy again
```

**The budget is an allowance, not a bypass.** A wave may push a dimension temporarily above
`maxCreaturesPerDimension`, by at most the reserved budget, because an event that has to wait for
wandering monsters to despawn is not an event. Everything else still applies:

- `maxCreaturesPerChunk` is **never** exceeded — the dimension cap protects the server, the chunk
  cap protects the player standing in that chunk.
- A cap of `0` means zero. A server that has turned NeroCreatures spawning off does not get a wave.
- The budget is spent per successful spawn, whether or not that creature survives.

**Cleaning up.** Closing a wave removes whatever is left of it — `discard`, not a kill, so an event
ending does not shower the arena in loot nobody earned. Two mechanisms run:

1. The handle remembers what it spawned and removes each one directly. Exact, works for any mob,
   but lives in memory.
2. Every NeroCreatures mob in a wave also carries the wave's id in its **own saved data**, so
   `InvasionBudget.sweep(level, waveId)` can clear up a wave whose handle is long gone — after a
   crash, or in a later session.

A creature from another mod placed through the API is covered by (1) but not by (2), which is the
honest limit of what NeroCreatures can promise about somebody else's entity.

A wave id identifies **a batch of mobs**. It is not a player id and nothing about a player is
derivable from it.

## See also

- [Bestiary](Bestiary.md) — per-creature behaviour, drops and spawn eggs
- [Bosses](Bosses.md) — why the boss line is gated far harder than a weight
- [Pets & Drones](Pets-and-Drones.md) — the two creatures that are owned rather than spawned
- [Commands](Commands.md) — `list` and `caps`
