# Mars

Mars' creatures spawn in the `nerocreatures:habitat/mars` biome tag (dunes, canyons and polar caps when
a planet mod provides them; empty otherwise), at any hour.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Dune Loper | Mount (beetroot) | A bounding low-gravity leap on the jump bar, paid for in stamina; carries panniers. Ridden with a [Creature Saddle](Mounts.md). | Fast surface travel with cargo. | Leather |
| Strata Hound | Tameable (bone) | Show it an ore, raw ore or natural stone (`#nerocreatures:strata_trackable`; never containers or other block entities): it tracks the nearest match within `strataHoundRange` (24), leads you there and marks it. A read-only scan. | Prospecting without x-ray. | Bone |
| Dust Devil | Hostile | A whirlwind: it spins up (cannot be hit while it does), then lifts and throws players and dropped items and cuts visibility. | An arena hazard that changes a fight. | Charged Sand |

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Dune Loper | 8 | 1–2 |
| Strata Hound | 10 | 1–2 |
| Dust Devil | 12 | 1 |

## Config

`strataHoundRange` (24), `dustDevilThrowsItems` (true; false = it throws players only) and the mount
keys on [Mounts](Mounts.md). See the [Drop Map](Drop-Map.md) for Charged Sand.
