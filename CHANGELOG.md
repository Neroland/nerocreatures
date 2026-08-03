# Changelog

All notable changes to **NeroCreatures** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

**Neroland Core dependency (1.10.0)**

- NeroCreatures now builds and runs against **Neroland Core 1.10.0**, its only hard dependency.
  Every loader manifest declares it as required with `ordering = "AFTER"`, and the version floor
  is the compiled Core version so an outdated Core is refused by the loader instead of failing
  later with a missing method.
- Core supplies the registration seam, the shared config framework, the shared creative tab, the
  per-player data-erasure hook, the `neroland:space/*` biome and dimension vocabulary, and the
  cross-loader entity registration seam (default attributes + spawn placements).

**Configuration**

- New `config/nerocreatures.properties`, hot-reloadable with `/neroland config reload`:
  `spawnsEnabled`, `globalSpawnWeightMultiplier`, `dropRateMultiplier`, `maxCreaturesPerChunk`,
  `maxCreaturesPerDimension`, `hostileAggressionMultiplier`, `bossDifficultyMultiplier`,
  `bossHpMultiplier`, `maxPetsPerPlayer`, `maxDronesPerPlayer`, `naturalBossSpawnsEnabled` — all
  server-authoritative — plus the client-local `telemetryEnabled` opt-out.

**Entity framework**

- `NeroCreatureEntity`, the shared base for every creature: tier-driven attributes, config-scaled
  aggression applied once at spawn, sane despawn rules, and automatic population-cap accounting.
- A reusable AI goal library designed to stay cheap under load: `BlinkStrikeGoal` (teleport-close
  ambush), `BurrowGoal` + `EmergeAttackGoal` (submerged and untargetable, with a telegraphed
  surfacing), `PackCoordination` (event-driven target sharing, flank offsets, night boldness),
  `SplitOnDamageBehaviour` (size-tiered splitting, budgeted against the population caps) and
  `EnergyAuraGoal` (periodic, victim-capped area damage).

**Spawn engine**

- Creature spawns are declared once in code as `SpawnRule` entries against Core's space biome
  tags and placed by a server-side sweep that mirrors vanilla's spawner (player-anchored ring,
  vanilla placement checks, difficulty and light rules), rather than through three
  loader-specific biome-modifier systems.
- Population caps are enforced at placement time, per chunk and per dimension.
- **Earth stays quiet by design:** with no planet mod installed, Core's space tags are empty,
  every rule matches nowhere and nothing spawns.

**Creature drops**

- Ten drop items with programmer-art textures: Void Essence, Stalker Hide, Stalker Sinew,
  Refined Crystal, Worm Chitin, Ore Slurry, Plasma Cell, Contraband, Salvaged Circuitry and
  Android Core. All join Neroland Core's shared creative tab.
- Tags: `neroland:materials/<drop>` for every drop, `neroland:highlight/materials` for the
  coloured slot borders, and `c:gems/refined_crystal` / `c:dusts/ore_slurry` for cross-mod
  interop.
- The canonical creature → drop → tag → consumer mapping is published at
  [`wiki/Drop-Map.md`](wiki/Drop-Map.md).

**Privacy & telemetry scaffolding**

- [`PRIVACY.md`](PRIVACY.md) documents what is stored, erasure, retention, the companion-app
  boundary and telemetry.
- Opt-out Sentry crash reporting is wired end to end but ships a **placeholder DSN**, and is a
  hard no-op until a real one is configured — this build sends nothing anywhere.
- NeroCreatures' player-data erasure hook is registered with Core at mod construction, ahead of
  the stores it will purge.

**Networking**

- `nerocreatures:main` payload channel with the ecosystem's declare-once / register-per-loader
  split. No payloads yet: the mod is server-authoritative and the client currently only renders.
