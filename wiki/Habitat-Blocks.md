# Habitat Blocks

Seven blocks for keeping, breeding and showing creatures, plus the [Apex Altar](Bosses.md#the-apex-altar).
None of them needs another mod. None of them stores anything about a player: the Incubator keeps only a
random incubation id, a nest is remembered by the creature, and a Terrarium or Capture Pod never holds
an owner. Craft them from the recipe book (each recipe unlocks when you pick up an ingredient).

| Block | Tool | What it does |
| --- | --- | --- |
| Nest | Hoe | A home point for your pets and mounts. |
| Feeding Trough | Axe | Breeds your husbanded livestock from stocked food. |
| Incubator | Pickaxe | Hatches one creature egg; faster with power. |
| Lure Beacon | Pickaxe | Makes one species more likely to spawn nearby. |
| Sonic Ward | Pickaxe | Keeps hostile NeroCreatures from spawning or walking in. |
| Terrarium | Any | Shows one small captured creature. |
| Trophy Plaque | Axe | Shows one trophy or signature drop. |

## Nest

Use the nest with an empty hand: your nearest own pet or mount within 8 blocks now calls it home.
Sneak-use to un-home all of yours homed there. A homed creature that is not following you walks back
when it strays more than 10 blocks (up to 64), rests within 3 blocks and heals 1 HP every
`nestHealIntervalTicks` (200) while it rests. The nest is saved on the creature, not the block, and is
forgotten if the nest is broken, the creature is released or its owner's data is erased. An unloaded
nest is never loaded just to check it.

## Feeding Trough

Put breeding food in it (5 slots). Every `feedingTroughIntervalTicks` (200) it looks at up to 32
livestock within 6 blocks and puts one eligible pair of **husbanded adults** in love with their species'
food. Wild herds and babies are never fed. Love from a trough has no player cause, so nothing about a
player is recorded. 0 = troughs never feed. It stops feeding while `feedingTroughMaxLivestock` (16)
livestock, babies included, are already in range, so one trough cannot fill a pen without limit.

## Incubator

Use a creature egg on it to put the egg in (one egg; no screen). Empty hand: time left. Sneak with an
empty hand: take the egg out. Unpowered it hatches in `incubatorHatchTicks` (24000, 20 minutes); with
Nero energy from any adjacent storage (`incubatorEnergyPerTick`, 20) it hatches in
`incubatorPoweredHatchTicks` (6000, 5 minutes). No power mod, or a lookup that fails, simply means the
unpowered rate. Each player may have `maxIncubatingEggsPerPlayer` (8) eggs incubating. See
[Livestock and Breeding](Livestock-and-Breeding.md).

## Lure Beacon

Burn any species' breeding food as bait (`lureBeaconBaitTicks` 6000 per item). The first bait decides
the one species lured. Within 24 blocks (12 up and down) that species' spawn weight is multiplied by
`lureBeaconWeightMultiplier` (4, hard cap 8). Population caps still apply, so a lure never adds more
creatures than the world allows. 0 bait ticks = lures do nothing.

## Sonic Ward

Burn `#nerocreatures:sonic_ward_fuel` (Amethyst Shard, Resonant Membrane; `sonicWardFuelTicks` 6000
per item). Within 16 blocks (12 up and down) hostile NeroCreatures do not spawn, and every second up
to 24 hostile NeroCreatures near the edge are walked out or stopped at the edge. Nothing is hurt or
teleported, bosses are never touched and other mods' mobs are not affected. At most 64 lure and ward
fields count per dimension.

## Terrarium

Use a filled [Capture Pod](Gear.md#capture-pod) on an empty Terrarium to move its creature in; use an
empty pod on a full one to take it back out. Only small species fit
(`#nerocreatures:terrarium_small`). A full Terrarium cannot be mined in survival and resists
explosions, so a creature is never lost: empty it with a pod first. If a full Terrarium is removed some
other way (creative mode, a command, another mod's block breaker), its creature drops as a filled
Capture Pod.

## Trophy Plaque

Use a trophy (`#nerocreatures:trophies`: the Apex Trophy, boss signature drops and the habitat drops)
on an empty plaque to hang it; use it with an empty hand to take it down. One item, never a stack,
never a swap.

## Config

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `feedingTroughIntervalTicks` | 200 | 0-24000 | Ticks between a trough's feeding pulses. 0 = never feeds. |
| `feedingTroughMaxLivestock` | 16 | 2-256 | No pair is fed while this many livestock are in range. |
| `incubatorHatchTicks` | 24000 | 1200-240000 | Unpowered hatch time. |
| `incubatorPoweredHatchTicks` | 6000 | 600-240000 | Fully powered hatch time. |
| `incubatorEnergyPerTick` | 20 | 0-10000 | Energy pulled per tick from adjacent storages. 0 = power ignored. |
| `lureBeaconBaitTicks` | 6000 | 0-72000 | Ticks one bait item burns. 0 = lures do nothing. |
| `lureBeaconWeightMultiplier` | 4.0 | 1-8 | Spawn-weight multiplier for the lured species. |
| `sonicWardFuelTicks` | 6000 | 0-72000 | Ticks one fuel item burns. 0 = wards do nothing. |
| `nestHealIntervalTicks` | 200 | 0-24000 | A resting creature heals 1 HP this often. 0 = no healing. |

## See also

- [Livestock and Breeding](Livestock-and-Breeding.md)
- [Gear](Gear.md)
- [Pets & Drones](Pets-and-Drones.md)
