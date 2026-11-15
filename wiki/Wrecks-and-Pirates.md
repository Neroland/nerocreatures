# Wrecks & Pirates

Derelict stations and wrecks spawn in the `nerocreatures:habitat/wrecks` biome tag (the void biome
on a space level when a planet mod provides one; never in the overworld). Pirate captains turn up on
any off-Earth surface (`neroland:space/planet_biomes`). The machines here are
`#nerocreatures:mechanical`.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Cache Mimic | Hostile, ambusher | A salvage crate (exactly one block, on the grid) until you touch or hit it: the lid splits open over 20 ticks and it bites. With no target for 30 seconds it settles back into a crate. | A loot piñata that bites: its table is a full salvage cache. | Contraband, Salvaged Circuitry, Plasma Cells, iron, gold; Android Core 35%, diamond 20% |
| Static Swarm | Hostile, flyer | Every 2 seconds drains one charge from each of up to 3 players within 4 blocks (a Plasma Cell, or an item a mod registers) and heals itself; shocks on contact. | Bottle it: use a glass bottle on a swarm at half health or less to get a **Static Jar**. | Redstone, Glowstone Dust, Plasma Cell 25% |
| Scrap Hulk | Elite, never spawned by a rule | Assembles itself where three machines died within 8 blocks and 10 seconds. Sheds a part each health quarter (less armour); a telegraphed big-arm sweep hits up to 4 players. | A big salvage payout, and a reason to fight machines in the open. | Salvaged Circuitry 6–12 (+1 per shed part), Android Core 1–2, iron |
| Pirate Captain | Elite humanoid | Arrives with two crew. Jet-dashes at a target 5–16 blocks away after a 12-tick nacelle flare; rallies up to 6 pirates within 16 blocks. | Kill it for the **Captain's Ledger**, a map to a pirate stash. | Contraband, gold, the ledger (to the killer) |

## Static Jar

Throw it (one per second). It flies like a thrown item and bursts where it lands: every mechanical
creature within 5 blocks (Rogue Drones and Androids, Scrap Hulks; at most 12) is disabled for 5
seconds. With `staticSwarmDrain` off the jar just shatters. A jar and a redstone dust also craft into
2 Plasma Cells.

## Captain's Ledger

The ledger goes only to the player the kill is credited to, straight into their inventory (at their
feet if it is full); otherwise nobody gets one. It marks a spot 96–256 blocks from where the captain
fell, in the same world. Use it to see a bearing ("The stash lies N blocks to the north-east"); within
6 blocks it opens: the ledger is used up, the stash loot (`nerocreatures:gameplay/pirate_stash`)
goes to you, and two guards come for you. Nothing about the mark is stored outside the item. A
structure mod may later place a real stash at the mark; the item will not change.

## Details

- **Chargeable items.** Core has no item-energy API yet, so a swarm drains only
  `#nerocreatures:chargeable` items: a Plasma Cell loses one cell per unit. Another mod can register
  a handler for its own chargeable items; with none, other tagged items are left alone.
- **Scrap Hulk assembly** is a world change gated by `scrapHulkAssembly`, `mobGriefing` and the
  loader's checks; at most one hulk per 64 blocks and 4 per dimension. Hulks never assemble from
  hulk deaths.
- A tamed Lumen Moth reveals a dormant Cache Mimic nearby (`#nerocreatures:lumen_reveals`), so a
  moth is the safe way to sweep a cargo hold.

## Spawning

| Creature | Biome tag | Weight | Group |
| -------- | --------- | ------ | ----- |
| Cache Mimic | `nerocreatures:habitat/wrecks` | 4 | 1 (space levels, `cacheMimicSpawns`, one per 48 blocks; loot only from player kills) |
| Static Swarm | `nerocreatures:habitat/wrecks` | 8 | 1 (in the air; at most 2 per 32 blocks) |
| Static Swarm | `nerocreatures:habitat/overworld` | 4 | 1 (thunderstorms only, see [Spawning](Spawning.md)) |
| Pirate Captain | `neroland:space/planet_biomes` | 2 | 1 (+2 crew) |
| Scrap Hulk | — | — | assembled, never spawned |

## Config

`cacheMimicSpawns`, `staticSwarmDrain`, `scrapHulkAssembly` (all true); the overworld swarm also
follows `overworldSpawns` and `overworldSpawnCap`. See [Config](Config.md).
