# Cindara

The ember world's creatures spawn in the `nerocreatures:habitat/cindara` biome tag (the ember plains and
sulfur vents when a planet mod provides them; empty otherwise), at any light. Both are fire-immune.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Slag Beetle | Tameable (coal, charcoal) | Eats raw ore its owner throws to it and, after digesting, drops the smelted result, with a 10% chance of one extra (Bountiful adds a second 10% roll). Raw ore items only, never ore blocks. Only items its owner threw. | A living furnace for a field base. | Slag Carapace |
| Magma Wader | Hostile | Wades through lava and, after a clear wind-up, lobs cooling slag that hardens into temporary **Ephemeral Crust**, which turns back into what was there after 30–60 seconds. | Drops Heat Glands for fire-proof gear. | Heat Gland |

## Ephemeral Crust

A temporary block (no item, no loot) that heals the world by itself: it reverts to the air, lava or
water it replaced. Bosses use the same block for their arena pieces. The Wader places it only where the
world allows it (`mobGriefing`, the loader's place check) and only with `magmaWaderSlag` on; otherwise
its slag burns and leaves nothing.

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Slag Beetle | 10 | 1–3 |
| Magma Wader | 12 | 1–2 |

## Config

`slagBeetleSmelting` (true) and `magmaWaderSlag` (true). The Cinder Tyrant boss also lives here; see
[Bosses](Bosses.md).
