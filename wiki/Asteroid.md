# Asteroid

The asteroid field's creatures spawn in the `nerocreatures:habitat/asteroid` biome tag (asteroid and
orbit biomes when a planet mod provides them; empty otherwise), at any light. The Asteroid Worm (see
the [Bestiary](Bestiary.md)) lives here too.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Magnet Crab | Pet (iron nuggets; egg birth) | Wild: grabs plain metal items (`#nerocreatures:magnetic`: ingots, nuggets, raw metals) lying within 5 blocks into its shell, 3 stacks of 16 at most. Tamed: every half second pulls one ground item within 6 blocks to your feet. | An item magnet for a dig site: park it (it works while sitting) and the drops come to you. Wild crabs give back everything they stole when they die. | Iron Nugget (vanilla) + its shell |
| Gravity Leech | Hostile, flyer | Winds up for 13 ticks, then latches a tether that lightens your gravity to 30% (or, one latch in three, flips it for a second, then lightens). | Kill it fast or break the tether: 6 damage to the leech, 12 blocks of distance, or 5 seconds ends it. Drops Graviton Nodes. | Graviton Node |

## Details

- **What a tamed crab takes.** Only items nobody threw, or that you threw yourself; never an item
  reserved for a Slag Beetle, an item only one player may pick up, or anything within 2 blocks of you.
  You must be the crab's owner, online, in the same world and within 24 blocks. Vanilla pick-up then
  does the rest, so a full inventory leaves the item at your feet. Gate: `magnetCrabCollects`.
- **What a wild crab steals** goes through `magnetCrabSteals`, `mobGriefing` and the loader's
  checks. A crab holding loot never despawns, and taming one drops its shell at once. Its shell
  plates (0–3) show how much it holds.
- **The tether** is a temporary `minecraft:gravity` modifier that is never saved: it lapses on its
  own within a second and a half of the leech letting go, even if the leech is unloaded or killed
  elsewhere. Two leeches on one player share one tether.
- The Gravity Leech changes nothing in the world, so it has no config key.

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Magnet Crab | 10 | 1–3 |
| Gravity Leech | 12 | 1–2 |

## Config

`magnetCrabSteals`, `magnetCrabCollects` (both true). See [Config](Config.md).
