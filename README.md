# NeroCreatures

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

NeroCreatures is the creature layer: hostile fauna for the planets other mods build, neutral
resource creatures, humanoid raiders, tameable companions and planet bosses. Its drops are raw
materials the rest of the ecosystem spends.

**Status:** feature-complete for 0.1.0 — version `0.0.1-alpha.1` until the release bump.

- **Twelve creatures.** Four hostile fauna (Void Crawler, Lunar Stalker, Asteroid Worm, Plasma
  Slime), a neutral Crystal Golem, a Space Pirate with equipment-driven kits, two Rogue Androids,
  two tameable alien pets, the deployable Terraforming Drone, and the first planet boss — the
  **Cinder Tyrant**.
- **Entity framework and AI goal library** — tier-driven attributes, config scaling, population-cap
  accounting, and eleven reusable goals with documented cost profiles.
- **Spawn engine** — a code-declared spawn table against Core's `neroland:space/*` biome tags,
  placed by a vanilla-shaped server-tick sweep with per-chunk and per-dimension caps.
- **Boss framework** — a multi-phase controller with boss bar, arena leash and config scaling; two
  entry modes (natural and summoned) that pay differently; contribution-split rewards; and the
  `nerocreatures:boss_pressure` threshold channel for other mods.
- **Ownership, erasure and retention** — two player-keyed stores, both wired into Core's shared
  data-erasure hook, both exportable, neither holding names or coordinates.
- **Companion-app link module** — `bestiary`, `pets` and `bosses` snapshot sections, a `pet_recall`
  action, three live events and two alerts, all scoped to the asking player.
- **`/nerocreatures` command tree** — `list`, `caps`, `summon-boss`, `export`, `telemetry-test`.
- **`InvasionBudget`** — the public seam a future NeroEvents raid uses to place a wave inside the
  population caps and clean it up again afterwards.
- **Opt-out, PII-free crash reporting** — this mod's own crashes only; set `telemetryEnabled=false`
  to switch it off.

## Requirements

- **Neroland Core 1.10.0+** — required, and the only hard dependency.
- Every other Nero mod is optional. With no planet mod installed, Core's `neroland:space/*` biome
  tags are empty, so NeroCreatures spawns nothing at all and vanilla Earth is left alone by design.

## Build targets

- **Minecraft:** 26.1.2 and 26.2
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "6 cells")
- **Java:** 25
- Mod id: `nerocreatures` · package `za.co.neroland.nerocreatures`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build \
          :forge:26.1.2:build :forge:26.2:build \
          :fabric:26.1.2:build :fabric:26.2:build   # all six
```

## Documentation

- [`wiki/`](wiki/Home.md) — player- and contributor-facing docs, including the
  [Drop Map](wiki/Drop-Map.md) (the cross-mod contract for creature drops),
  [Spawning](wiki/Spawning.md) (the weight table, caps and the `InvasionBudget` seam),
  [Pets & Drones](wiki/Pets-and-Drones.md) (taming, ownership caps, stored data and erasure),
  [Commands](wiki/Commands.md), [Link module](wiki/Link-Module.md),
  [Data storage](wiki/Data-Storage.md) and [Telemetry](wiki/Telemetry.md)
- [`CHANGELOG.md`](CHANGELOG.md) — release notes
- [`PRIVACY.md`](PRIVACY.md) — what is stored, erasure, retention, telemetry
- [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) — agent and contributor context
