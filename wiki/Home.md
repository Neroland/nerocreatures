# NeroCreatures Wiki

Player- and contributor-facing documentation for **NeroCreatures**, the creature layer of the
Neroland sci-fi ecosystem. Built on **Neroland Core**.

NeroCreatures requires **Neroland Core 1.23.0 or newer** (below 2.0). Minecraft 1.20.5
runs on Fabric, and 1.20.6 runs on Fabric, Forge and NeoForge.

NeroCreatures populates the worlds other mods build: hostile fauna on the planets, neutral
resource creatures, humanoid raiders, tameable companions, mounts, livestock and planet bosses. Its
drops are deliberately raw materials that other mods spend, and every drop also has a use inside the
mod.

> **Status: 0.5.0-beta.1 "Menagerie".** 38 creatures across nine habitats, including four bosses
> (the Cinder Tyrant and the altar-summoned Rime Matriarch, Bloom Sovereign and Facet Colossus);
> mounts, husbanded livestock, traits and breeding; habitat blocks and creature gear; the Field
> Scanner and Bestiary; alphas; a companion-app link (schema 2); and the walkable Menagerie gallery.
> See the [Changelog](https://github.com/Neroland/nerocreatures/blob/main/CHANGELOG.md).

## Contents

- [Bestiary](Bestiary.md) — every creature: behaviour, spawn biome, drops, spawn eggs and how to
  fight it.
- [Spawning](Spawning.md) — where creatures appear and why Earth stays quiet: the space biome tags,
  the full weight table, the placement sweep, the population caps, every config key, and the
  `InvasionBudget` seam for event mods.
- Habitats: [Luna](Luna.md), [Mars](Mars.md), [Cindara](Cindara.md), [Glacira](Glacira.md),
  [Greenxertz](Greenxertz.md), [Nyxara](Nyxara.md), [Asteroid](Asteroid.md),
  [Wrecks & Pirates](Wrecks-and-Pirates.md), [Deep Space](Deep-Space.md) — the 0.5.0 creatures of
  each world.
- [Drop Map](Drop-Map.md) — every creature drop, its tags, and the mods meant to consume it. The
  canonical cross-mod contract.
- [Pets & Drones](Pets-and-Drones.md) — taming, commands, the Terraforming Drone, the ownership
  caps, what is stored about you and how to erase it, and where NeroCreatures stops and
  NeroCompanion starts.
- [Field Scanner and Bestiary](Field-Scanner-and-Bestiary.md) — scan creatures, the Scanned and Studied tiers,
  the one-time reward and the Bestiary screen.
- [Traits](Traits.md) — the two traits every tameable carries, inheritance, and the Radiant morph.
- [Mounts](Mounts.md) — taming, the Creature Saddle, riding, stamina and panniers.
- [Habitat Blocks](Habitat-Blocks.md) — Nest, Feeding Trough, Incubator, Lure Beacon, Sonic Ward,
  Terrarium and Trophy Plaque.
- [Livestock and Breeding](Livestock-and-Breeding.md) — breeding foods, eggs and hatchlings.
- [Gear](Gear.md) — the creature gear and the Capture Pod.
- [Alphas](Alphas.md) — rare, larger hostiles: how often, how many, what they drop.
- [Config](Config.md) — every config key: spawning, caps, bosses, mounts, eggs, alphas, overworld
  spawns, the tick budget, the world-changing abilities, habitat blocks and gear.
- [Bosses and the Apex Altar](Bosses.md) — the four bosses, their phases and arenas, the Apex Altar
  and its sigils, the exact contribution reward split, and the `nerocreatures:boss_pressure` event
  channel other mods can listen to.
- [Commands](Commands.md) — the `/nerocreatures` operator tree: `list`, `caps`, `summon-boss`,
  `export`, `budget`, `telemetry-test` and `gallery` (`grid`, `tour`, `demo`, `clear`).
- [Menagerie](Menagerie.md) — the creative showcase: every creature with a sign and a demo plate,
  the boss hall, the paddock, the Trait Wall, the tour and `gallery clear`.
- [Gallery & screenshot capture](Gallery.md) — the `/ncgallery` capture harness, the shot list, and
  where the screenshots are saved.
- [Link module (schema 2)](Link-Module.md) — what a Neroland companion app can see and do, section by
  section, and the own-data-only rule behind it.
- [Privacy](Privacy.md) — what is stored about players, erasure, export, retention and the
  companion app, in short.
- [Data storage](Data-Storage.md) — the two player-keyed stores, erasure, retention and export, in
  practical terms.
- [Telemetry](Telemetry.md) — opt-out crash reporting: what it sends, what it never sends, and how
  to switch it off.
- [NeroQuests integration](NeroQuests-Integration.md) — the optional "Bestiary" quest chapter, the
  two milestone gates and the family tags a quest can count on.

## Requirements

- **Neroland Core** — required, and the only hard dependency. NeroCreatures uses Core's
  registration seam, config framework, data-erasure hook, space biome tags,
  entity registration seam and link API.
- Every other Nero mod is **optional**. NeroCreatures degrades gracefully without them: with no
  planet mod installed, Core's space biome tags are empty and nothing spawns — vanilla Earth is
  left entirely alone.

## Privacy

NeroCreatures keeps a small, world-save-scoped record of who owns which pet, mount, drone or egg, and
(only while a **summoned** boss fight is running) each participant's damage. No names, no position
history. Everything is erasable through Neroland Core's shared request, retained only as long as the
server configures, and exportable on request; see [Privacy](Privacy.md) and
[Data storage](Data-Storage.md). A companion app sees only the asking player's own data
([Link module](Link-Module.md)). Crash reporting is opt-out, anonymous and covers this mod's own
crashes only ([Telemetry](Telemetry.md)).

## See also

- [Changelog](https://github.com/Neroland/nerocreatures/blob/main/CHANGELOG.md)
