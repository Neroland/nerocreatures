# NeroCreatures Wiki

Player- and contributor-facing documentation for **NeroCreatures**, the creature layer of the
Neroland sci-fi ecosystem. Built on **Neroland Core**.

NeroCreatures populates the worlds other mods build: hostile fauna on the planets, neutral
resource creatures, humanoid raiders, tameable companions and, eventually, planet bosses. Its
drops are deliberately raw materials that other mods spend.

> **Status:** feature-complete for 0.1.0 — the entity base, AI goal library, spawn engine, and
> twelve creatures with their loot and programmer-art models: the four **hostile fauna** (Void
> Crawler, Lunar Stalker, Asteroid Worm, Plasma Slime), the **neutrals and humanoids** (Crystal
> Golem, Space Pirate, Rogue Drone, Rogue Android), the **tameables** (Glacite Wisp, Xertz Forager
> and the deployable Terraforming Drone), the **boss framework plus the first planet boss** (the
> Cinder Tyrant), and now the **companion-app link module**, the `/nerocreatures` command tree and
> the invasion seam for event mods. All eleven creature drops are obtainable in survival, and four
> of them have an in-mod sink.
> Pages grow alongside the code — a feature is not done until its page here is updated (see
> [`../AGENTS.md`](../AGENTS.md) / [`../CLAUDE.md`](../CLAUDE.md)).

## Contents

- [Bestiary](Bestiary.md) — every creature: behaviour, spawn biome, drops, spawn eggs and how to
  fight it.
- [Spawning](Spawning.md) — where creatures appear and why Earth stays quiet: the space biome tags,
  the full weight table, the placement sweep, the population caps, every config key, and the
  `InvasionBudget` seam for event mods.
- [Drop Map](Drop-Map.md) — every creature drop, its tags, and the mods meant to consume it. The
  canonical cross-mod contract.
- [Pets & Drones](Pets-and-Drones.md) — taming, commands, the Terraforming Drone, the ownership
  caps, what is stored about you and how to erase it, and where NeroCreatures stops and
  NeroCompanion starts.
- [Bosses](Bosses.md) — the Cinder Tyrant and the boss framework: phases, arenas, the two ways a
  boss turns up, the exact contribution reward split, and the `nerocreatures:boss_pressure` event
  channel other mods can listen to.
- [Commands](Commands.md) — the `/nerocreatures` operator tree: `list`, `caps`, `summon-boss`,
  `export`, `telemetry-test` and `gallery`.
- [Gallery & screenshot capture](Gallery.md) — the creative showcase scene, the `/ncgallery` capture
  harness, the shot list, and how the screenshots reach the Modrinth gallery.
- [Link module](Link-Module.md) — what a Neroland companion app can see and do, section by section,
  and the own-data-only rule behind it.
- [Data storage](Data-Storage.md) — the two player-keyed stores, erasure, retention and export, in
  practical terms.
- [Telemetry](Telemetry.md) — opt-out crash reporting: what it sends, what it never sends, and how
  to switch it off.

## Requirements

- **Neroland Core** — required, and the only hard dependency. NeroCreatures uses Core's
  registration seam, config framework, shared creative tab, data-erasure hook, space biome tags,
  entity registration seam and link API.
- Every other Nero mod is **optional**. NeroCreatures degrades gracefully without them: with no
  planet mod installed, Core's space biome tags are empty and nothing spawns — vanilla Earth is
  left entirely alone.

## Privacy

NeroCreatures stores two small, world-save-scoped, player-keyed records and nothing else: who owns
which tamed pet or deployed drone, and — only while a **summoned** boss fight is actually running —
how much damage each participant has done to it. No names, no coordinates. Both are erasable through
Neroland Core's shared request, retained only as long as the server configures, and exportable on
request; see [Data storage](Data-Storage.md) for the practical version and
[`../PRIVACY.md`](../PRIVACY.md) for the full statement. Anything a companion app can read is scoped
to the asking player and nobody else ([Link module](Link-Module.md)). Crash reporting is opt-out, PII-free and
covers this mod's own crashes only ([Telemetry](Telemetry.md)).

## See also

- [Build & contributor context](../AGENTS.md)
- [Changelog](../CHANGELOG.md)
