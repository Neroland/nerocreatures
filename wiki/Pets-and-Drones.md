# Pets & Drones

The two things in NeroCreatures that become *yours*: tameable alien pets, and the deployable
Terraforming Drone. Everything on this page is server-authoritative — the server decides who owns
what, how many you may have, and what happens when you ask for your data to be erased.

For the creatures themselves as encounters, see the [Bestiary](Bestiary.md); for their taming
reagents as materials, see the [Drop Map](Drop-Map.md).

## Alien pets

Two species, one on each of the two worlds they suit. Both are harmless until tamed and harmless
afterwards — a pet in this mod is company and a small comfort, never a weapon.

| | Glacite Wisp | Xertz Forager |
| --- | --- | --- |
| **Theme** | Glacira — ice-lattice fauna | Greenxertz — quartz-forest critter |
| **Biome tags** | `neroland:space/moon_biomes` (weight 12), `neroland:space/crystalline_biomes` (weight 6) | `neroland:space/crystalline_biomes` (weight 14) |
| **Group size** | 1–2 | 1–3 |
| **Tamed with** | [Stalker Sinew](Drop-Map.md) | [Plasma Cell](Drop-Map.md) |
| **Health** | 12 | 16 |
| **Perk** | Frost Cushion | Quartz Glow |
| **Drops** | nothing | nothing |
| **Spawn egg** | `nerocreatures:glacite_wisp_spawn_egg` | `nerocreatures:xertz_forager_spawn_egg` |

### The perks

Deliberately small. NeroCreatures' rule for pets is *comfort, never combat*: no perk touches damage,
armour, health or speed, so a player with three pets is no stronger in a fight than a player with
none.

- **Frost Cushion** (Glacite Wisp) — while you are within 8 blocks and *falling*, the wisp lends you
  Slow Falling. It does nothing at all while you are on the ground.
- **Quartz Glow** (Xertz Forager) — in light level 7 or below, the forager's crest lights up and
  lends you Night Vision while you stay within 8 blocks. Step into decent light and it switches off.

Both are checked once every two seconds, only while the pet is tamed and its owner is nearby.

### Taming

Hold the species' reagent and use it on a wild one. It is a **one-in-three** chance per feed, the
same odds as a vanilla wolf, and the reagent is consumed either way.

If you are already at your pet cap, nothing is consumed and nothing happens — you are told so on the
action bar. The cap is checked *before* the roll, on the server, so being at your limit never costs
you a reagent.

### Commands

**Sneak-interact your own pet** to cycle its standing order. A freshly tamed pet is on Follow, so the
first sneak-click sits it down:

| Order | What it does |
| --- | --- |
| **Sit** | Sits where it is and stays there. |
| **Stay** | Holds the area it was in when you gave the order — it may wander a few blocks, and walks back if it strays more than 8. Still defends you. |
| **Follow** | Follows you around. |

Every order is confirmed on your action bar. A pet on any order will still defend its owner and
still fight back if attacked; it never picks a fight on its own.

### Feeding

Use the species' reagent on your own injured pet to heal it. A pet at full health ignores the offer,
so you cannot waste a reagent on it by accident.

### Owner-only

Only the owner can command, feed or otherwise interact with a tamed pet. Anyone else clicking it
gets nothing at all — their click falls straight through to whatever they were actually doing.

## Terraforming Drone

The mod's one utility mob. It has **no spawn egg and no natural spawn**: the only way one exists is
you deploying a **Terraforming Drone Shell**.

### Crafting the shell

| | | |
| --- | --- | --- |
| Salvaged Circuitry | Iron Ingot | Salvaged Circuitry |
| Plasma Cell | Android Core | Plasma Cell |
| Iron Ingot | Salvaged Circuitry | Iron Ingot |

The iron is `#c:ingots/iron`, so any mod's iron works. Everything else is a NeroCreatures drop — see
the [Drop Map](Drop-Map.md).

### Deploying and recalling

Use the shell on a block face and the drone unfolds there, **bound to you**, with that spot as the
centre of its work area. It counts against `maxDronesPerPlayer`; at your limit, nothing is placed and
the shell is not consumed.

**Sneak-interact your drone with an empty hand** and it hands the shell back and folds away. A
plain interact reports its work radius on your action bar. Killing a drone also drops its shell, so
deploying is never a one-way spend.

A deployed drone is persistent — it stays put through chunk unloads and it never despawns.

### What it does

Every **5 seconds** the drone runs one duty cycle:

- **Planting assist** — it tries up to six random blocks inside its work area (8 blocks out, 3 up and
  down) and applies one bone-meal-equivalent growth tick to the first valid target it finds. It
  consumes nothing: the drone *is* the reagent. Anything a data pack has made bonemealable counts, so
  it works on plants NeroCreatures has never heard of.
- **Oxygen support** — a documented hook that currently does nothing. Making a volume breathable
  belongs to a planet mod, and NeroCreatures references none; when that integration lands it will
  arrive as a Neroland Core event the drone answers, not as a dependency.

A drone strolls slowly around its area and is walked back whenever it drifts more than 10 blocks from
its anchor. It has no target goals at all — it will never attack anything, and it is fragile enough
that the local wildlife is a real hazard to it.

### Why there is no energy bar

The duty cycle *is* the power model, on purpose. Neroland Core's energy framework is block-entity
shaped — the capability, the lookup and the machine base all address a block in a level — and there
is no cross-loader seam for exposing a capability on a *mob*. Inventing one for this would put a
loader-specific surface into the shared module for the sake of a chore. The pacing that matters ("an
assist, not a farm") is already carried by the five-second cycle. If a future release does want a
real charge, the thing to add is a Core-side mob capability, and only the drone's duty-cycle method
changes.

## Caps

Both caps are server-authoritative config values in `nerocreatures.properties`:

| Key | Default | Meaning |
| --- | --- | --- |
| `maxPetsPerPlayer` | 3 | Tamed pets one player may keep. **0 disables taming entirely.** |
| `maxDronesPerPlayer` | 2 | Deployed drones one player may have. **0 disables drones entirely.** |

Both are enforced at the moment of acquisition — tame time and deploy time — because that is the only
moment at which saying no costs the player nothing.

## What is stored, and how to get rid of it

NeroCreatures keeps a small server-side index of who owns what, in the world save. Per owned
creature it holds exactly four things:

- the **creature's** entity UUID (a game id, not a player id),
- whether it is a pet or a drone,
- its entity-type id,
- the id of the dimension it was last registered in,

plus one "last updated" timestamp per player. **No names, no IP addresses, no chat, and no
coordinates of any kind** — the dimension id exists only so an erase request knows which level to
look in.

A tamed pet also carries its owner's UUID in its own entity data. That is vanilla's design for every
tameable animal and is inherent to being tameable; it is declared in
[`../PRIVACY.md`](../PRIVACY.md).

**Erasure.** `/neroland data eraseme` (or an admin's `/neroland data erase <uuid>`) purges you from
this index *and* frees the creatures: your pets are returned to the wild — owner cleared, order
reset, ordinary despawnable animals again — and your drones are recalled, dropping their shells where
they stood. The sweep also checks every loaded level for anything still carrying your UUID, so the
erase is complete even if the index and the world had drifted apart. Nothing on that path logs who
was erased.

**Retention.** If the server sets Neroland Core's `dataRetentionDays` above zero, ownership records
untouched for that long are purged the same way — index row *and* creatures — the first time the
store is read in a session. Only the number of records purged is logged.

**Access.** An operator-run export returns exactly one player's own ownership rows as JSON, and
nobody else's.

## The NeroCompanion boundary

NeroCreatures owns the *shallow* half of pet behaviour and intends to keep owning it. Everything
deeper is **NeroCompanion**'s job when that mod lands.

| NeroCreatures — always | NeroCompanion — if installed |
| --- | --- |
| Taming, ownership and its caps; the sit/stay/follow cycle; owner-only interaction; defending its owner; the one species perk; loot; rendering. | Personality, moods, levelling, named abilities, pet inventories, chat, cross-mod errands. |

The hand-off is a small service interface that **NeroCreatures declares** and NeroCompanion
implements. There is no reflection into the other mod anywhere, no companion mod appears in any
NeroCreatures manifest, and with no companion mod installed the lookup simply finds nothing and every
hook is a no-op. A companion mod may tell NeroCreatures that it is driving a pet's *idle* behaviour,
in which case NeroCreatures stands down from the species perk — and from nothing else, because
taming, caps and the command cycle are its contract with the player.

## See also

- [Bestiary](Bestiary.md) — the rest of the roster
- [Spawning](Spawning.md) — where the wild pets appear, and the caps they count against
- [Drop Map](Drop-Map.md) — where the taming reagents come from and what else spends them
- [Data storage](Data-Storage.md) — the ownership store in practical terms: erasure, retention,
  export
- [Link module](Link-Module.md) — seeing your pets in a companion app, and recalling them
- [`../PRIVACY.md`](../PRIVACY.md) — the full data-protection statement
