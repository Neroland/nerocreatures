# Mounts

0.5.0 adds rideable creatures. They share one mount base, so they all work the same way on every
Minecraft line NeroCreatures supports.

## Taming and the cap

A mount is tamed like a pet (its species food, one in three feeds) but counts against its own cap,
`maxMountsPerPlayer` (default 2), never against `maxPetsPerPlayer`. At the cap the feed is refused
and nothing is used up.

## Saddle

Craft a **Creature Saddle** (three Stalker Hide over two Stalker Sinew and two iron ingots) and use
it on your tamed, grown-up mount. The vanilla saddle does not fit. Use shears on the mount to take
the saddle off again.

## Riding

Use a saddled mount with an empty hand to ride it. WASD steers, a flying mount climbs and dives with
where you look, sneak dismounts, and the **jump bar** fires the mount's ability.

By default only the owner may ride (`mountOwnerOnly`). With it off, anyone may ride, but only the
owner saddles it, equips panniers, opens them or gives orders. Someone refused gets a short message
that never says whose mount it is.

## Stamina

Stamina runs from 0 to 100. The ability costs `mountAbilityCost` (default 35; Thrifty cuts it by
20%), and stamina comes back one point every `mountStaminaRegenTicks` ticks (default 10) while the
ability is not in use. Below the cost the jump bar does nothing.

## Panniers

Use a chest on a saddled mount to give it panniers: 9 slots, or 18 with the Stout trait. Sneak-use
the mount with an empty hand to open them (owner only). Shears take the panniers off; the chest and
everything in it drop.

The saddle, the panniers and their contents also drop when the mount dies or is released, including
when its owner's data is erased.

## See also

- [Traits](Traits.md)
- [Pets & Drones](Pets-and-Drones.md)
- [Config](Config.md)
