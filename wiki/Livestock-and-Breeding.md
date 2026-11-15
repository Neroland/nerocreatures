# Livestock and Breeding

Every livestock (L) and tameable (T) species breeds. Feed two adults their breeding food; egg species
lay an egg, the rest give live birth. A baby gets one [trait](Traits.md) from each parent (5% chance of
a fresh mutation instead).

| Species | Breeding food | Birth |
| --- | --- | --- |
| Lumen Moth | Glow Berries | egg |
| Lode Tortoise | Moss Block | egg |
| Slag Beetle | Coal, Charcoal | egg |
| Spore Drifter | Bone Meal | egg (Spore Pod) |
| Magnet Crab | Iron Nugget | egg |
| Void Manta | Chorus Fruit | egg |
| Glacite Wisp | Stalker Sinew | live |
| Xertz Forager | Plasma Cell | live |
| Dune Loper | Beetroot | live |
| Strata Hound | Bone | live |
| Rime Yak | Sweet Berries | live |
| Mossback | Fern, Short Grass | live |
| Prism Stag | Amethyst Shard | live |

The foods are item tags (`nerocreatures:food/<species>`), so a data pack can change them.

## Eggs

An egg-birth pair drops one `<species>_egg` (stacks to 16) instead of a baby. The egg carries the
species and the baby's two traits and nothing else; an egg on the ground belongs to nobody. An egg from
the creative tab or a command still hatches, with two fresh traits fixed when it goes into the
Incubator. If an egg item is missing (a pack removed it) the pair gives live birth instead.

Put the egg in an [Incubator](Habitat-Blocks.md#incubator): 20 minutes unpowered, 5 minutes with
power. Each player may have 8 eggs incubating (`maxIncubatingEggsPerPlayer`); the count is kept against
a random incubation id and removed when the egg hatches, is taken out or the Incubator is broken.

## Hatchlings and babies

- A hatched **tameable** baby is untamed, but its first feed tames it at 100% (still subject to your
  pet cap).
- A hatched **livestock** baby is born husbanded, so a [Feeding Trough](Habitat-Blocks.md#feeding-trough)
  will breed it once grown.
- Wild herds are never fed by a trough.

## See also

- [Traits](Traits.md)
- [Habitat Blocks](Habitat-Blocks.md)
- [Pets & Drones](Pets-and-Drones.md)
