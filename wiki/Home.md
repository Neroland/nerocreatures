# NeroCreatures Wiki

Player- and contributor-facing documentation for **NeroCreatures**, the creature layer of the
Neroland sci-fi ecosystem. Built on **Neroland Core**.

NeroCreatures populates the worlds other mods build: hostile fauna on the planets, neutral
resource creatures, humanoid raiders, tameable companions and, eventually, planet bosses. Its
drops are deliberately raw materials that other mods spend.

> **Status:** foundation and framework. The entity framework, AI goal library, spawn engine and
> all ten creature drops are in place; the creatures themselves land stage by stage. Pages grow
> alongside the code — a feature is not done until its page here is updated
> (see [`../AGENTS.md`](../AGENTS.md) / [`../CLAUDE.md`](../CLAUDE.md)).

## Contents

- [Drop Map](Drop-Map.md) — every creature drop, its tags, and the mods meant to consume it. The
  canonical cross-mod contract.

Pages still to come as the features land: Bestiary, Spawning, Pets & Drones, Bosses, Commands,
Link Module, Data Storage, Telemetry.

## Requirements

- **Neroland Core** — required, and the only hard dependency. NeroCreatures uses Core's
  registration seam, config framework, shared creative tab, data-erasure hook, space biome tags
  and entity registration seam.
- Every other Nero mod is **optional**. NeroCreatures degrades gracefully without them: with no
  planet mod installed, Core's space biome tags are empty and nothing spawns — vanilla Earth is
  left entirely alone.

## Privacy

NeroCreatures stores no personal data and ships opt-out, PII-free crash reporting (currently
inert — no Sentry project is configured yet). See [`../PRIVACY.md`](../PRIVACY.md).

## See also

- [Build & contributor context](../AGENTS.md)
- [Changelog](../CHANGELOG.md)
