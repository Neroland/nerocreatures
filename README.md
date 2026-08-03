# NeroCreatures

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

NeroCreatures is the creature layer: hostile fauna for the planets other mods build, neutral
resource creatures, humanoid raiders, tameable companions and planet bosses. Its drops are raw
materials the rest of the ecosystem spends.

**Status:** foundation and framework — version `0.0.1-alpha.1`. The entity framework, AI goal
library, spawn engine and all ten creature drops are in; the creatures themselves land stage by
stage.

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
  [Drop Map](wiki/Drop-Map.md) (the cross-mod contract for creature drops)
- [`CHANGELOG.md`](CHANGELOG.md) — release notes
- [`PRIVACY.md`](PRIVACY.md) — what is stored, erasure, retention, telemetry
- [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) — agent and contributor context
