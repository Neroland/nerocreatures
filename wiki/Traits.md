# Traits

Every tameable creature, pet or livestock carries **two traits**. They are rolled the first time the
creature ticks (wild spawns, and pets from saves made before traits existed, roll then; nothing a pet
already had changes), and a baby inherits one trait from each parent, with a small chance (5%) of a
fresh mutation instead.

| Trait | Effect |
| --- | --- |
| Hardy | +20% max health |
| Swift | +15% movement speed |
| Keen | ability range x1.25 |
| Bountiful | 50% chance of an extra roll on a product (wool, milk, shed parts) |
| Calm | panics half as easily; a mount never bucks |
| Thrifty | ability cooldowns and mount ability cost x0.8 |
| Stout | 18 pannier slots on a mount instead of 9 |
| Radiant | rare colour morph; cosmetic only |

Radiant is rare by weight (about 1 in 100 per slot). It changes the creature's texture and nothing else.

Traits are creature data, never player data. They are saved on the entity and stay with it when it
changes owner or is released.

**Breeding and eggs.** Live-born babies get their traits at birth. An egg carries the baby's two traits
already rolled (one from each parent, or a mutation), and the Incubator hatches a baby with exactly
those; an egg with no data (creative tab, commands) gets two fresh traits when it goes into the
Incubator. See [Livestock and Breeding](Livestock-and-Breeding.md).

**Capture Pods** keep a creature's traits through capture, a Terrarium and release. See
[Gear](Gear.md#capture-pod).

The pool is data-driven: `data/nerocreatures/creature_data/traits.json` (a data pack can change
weights and amounts).

## See also

- [Mounts](Mounts.md) — where Thrifty, Stout and Calm matter most.
- [Pets & Drones](Pets-and-Drones.md)
- [Livestock and Breeding](Livestock-and-Breeding.md)
