# Bestiary

Every creature NeroCreatures adds, what it does, where it lives and what it leaves behind. Drop
*consumers* live in the [Drop Map](Drop-Map.md); this page is about the fight.

> **Status:** the four hostile fauna are in, so are the neutrals and humanoids — Crystal Golem,
> Space Pirate and the two Rogue Androids — and so are the tameables: the two alien pets and the
> Terraforming Drone. The **first planet boss** is in as well; bosses have their own page, since a
> boss fight is a different kind of thing from an encounter — see [Bosses](Bosses.md).

## Where any of this happens

NeroCreatures spawns nothing on vanilla Earth, and that is not a special case — every creature is
placed against one of Neroland Core's `neroland:space/*` biome tags, and on an install with no
planet mod those tags are **empty**. Install a planet mod (Nerospace, or anything that opts its
biomes into the Core tags) and the roster appears there. (A dedicated **Spawning** page covering the
placement sweep itself is still to come.)

Every creature can also be placed directly with its **spawn egg**, which works everywhere,
including on Earth and in creative.

---

## Void Crawler

*A low six-legged shape that was over there a moment ago.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/dark_biomes` |
| **Group size** | 1–2 |
| **Spawn weight** | 30 |
| **Light** | dark only (block light ≤ 7) for natural spawns |
| **Drops** | [Void Essence](Drop-Map.md) ×0–2 |

The crawler does not chase you. It **blinks**: once every five seconds it can teleport straight onto
a target between 5 and 20 blocks away, then immediately resumes normal melee. Everything else about
it is unremarkable on purpose — it is fast, it is fragile, and the whole fight is decided by whether
you saw it before it closed.

**Fighting it.** Keep your back to something. The blink puts the crawler *near* its target, not
behind you specifically, so open ground is what makes a pair of them dangerous. Once it is in melee
it dies quickly.

**Cost note.** One blink per cooldown per crawler is a hard cap, which is what makes a group of
ambushers affordable to the server.

---

## Lunar Stalker

*They arrive together, and they do not queue up.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/moon_biomes` |
| **Group size** | 2–4 |
| **Spawn weight** | 24 |
| **Light** | any |
| **Drops** | [Stalker Hide](Drop-Map.md) ×1–2, [Stalker Sinew](Drop-Map.md) ×0–1 |

Three behaviours make a stalker:

- **Pack broadcast.** The first stalker to spot you tells every idle stalker within 16 blocks. This
  fires once, on sighting — an engaged stalker is never pulled off its own fight.
- **Flanking.** Beyond 6 blocks each member paths to *its own* side of you rather than the same
  side as everyone else, so a pack surrounds instead of forming a line. Inside 6 blocks it switches
  to a normal melee approach.
- **Night boldness.** While it is dark outside, detection range and approach speed both go up by
  35%. Note that most space dimensions have a fixed time of day and therefore never count as
  "dark outside" — that is deliberate, so a permanently dim planet does not permanently buff its
  hunters.

**Low gravity.** If a stalker spawns in a biome tagged `nerospace:gravity_low`, it takes reduced
gravity and a raised safe-fall distance, so it lopes in long arcs. This is a **tag lookup only** —
NeroCreatures does not depend on Nerospace, and a stalker on any other world simply walks.

**Fighting it.** Break line of sight or fight in a corridor; the flanking is what actually kills
you. Killing the one that spotted you does not call the others off.

---

## Asteroid Worm

*The ground moves, and then it does not matter where you were standing.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/asteroid_biomes` |
| **Group size** | 1 |
| **Spawn weight** | 6 (the rarest natural spawn) |
| **Light** | any |
| **Drops** | [Worm Chitin](Drop-Map.md) ×2–4, [Ore Slurry](Drop-Map.md) ×1–3 |

An armoured elite that spends most of the fight underground:

1. **Burrow.** With a target more than 6 blocks away the worm submerges. While burrowed it takes
   **no damage**, cannot be targeted, and is not drawn at all.
2. **Approach.** It tracks the target from below, re-pathing a few times a second.
3. **Telegraph.** Within strike range it holds position for one second while dust erupts from the
   ground above it. That second is your warning and your chance to move.
4. **Strike.** It surfaces and swings once, then fights normally until it decides to dive again.

It can never stay under indefinitely: an unreachable target (someone on a pillar) forces it back up
after at most 20 seconds.

**Segments.** The worm's body is segmented **in the model only**. On the server it is a single
entity with one long, low hitbox — the conservative first cut. Everything you can hit, and
everything that can hit you, is that one box.

**Fighting it.** Do not stand still during a telegraph. Its armour and knockback resistance make
trading hits a losing game; hit it during the window after a strike, before it dives again.

---

## Plasma Slime

*Killing it is the easy part.*

| | |
| --- | --- |
| **Biome tags** | `neroland:space/crystalline_biomes` (weight 20), `neroland:space/dark_biomes` (weight 8) |
| **Group size** | 1–3 (crystalline), 1–2 (dark) |
| **Light** | dark only (block light ≤ 7) for natural spawns |
| **Drops** | [Plasma Cell](Drop-Map.md) ×0–1 |

Two mechanics:

- **Energy aura.** Every two seconds a slime burns everything living within 2.5 blocks for 1 damage.
  Other mobs are excluded, so slimes do not melt each other — it is pressure on *you*, not a damage
  race.
- **Splitting.** A slime has a size from 3 (full) down to 1. Killing a size-2 or size-3 slime leaves
  two smaller ones; size 1 is terminal. Smaller slimes are weaker and quicker, and their size drives
  health, damage, speed and their physical size together.

Splits are budgeted three ways — the size tier caps the depth, a per-split cap applies, and the
children are clipped to whatever room is left in the per-chunk and per-dimension population caps.
A full chunk yields no children at all rather than blowing past the caps.

**Fighting it.** Fight it in the open, not in a corridor: standing in the aura while the split
children surround you is how a trivial mob becomes a real one. Ranged weapons trivialise it.

---

## Crystal Golem

*It was part of the scenery until you swung at it.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/crystalline_biomes` |
| **Group size** | 1 (always alone) |
| **Spawn weight** | 8 |
| **Light** | any |
| **Drops** | [Refined Crystal](Drop-Map.md) ×1–3, **+1–3 more for a pickaxe kill** |

The roster's first **neutral** creature: it will never start a fight. Hit it — or hit another golem
within earshot — and it stays angry for 20–40 seconds and comes after whoever did it, exactly like a
vanilla iron golem or polar bear. Anger spreads to nearby golems, so a crystal field reacts as a
group.

It is built around being slow: heavy armour, almost total knockback resistance, and a walking speed
a player can outrun. Disengaging is always an option, which is what makes attacking one a decision
rather than an ambush.

**The pickaxe bonus.** A golem is a resource node with legs. Kill it with anything in
`#minecraft:pickaxes` and it yields extra Refined Crystal on top of its loot table. That bonus is a
*bonus roll*, so it is the one thing `dropRateMultiplier` scales; the loot table itself is untouched
and stays fully overridable by a data pack. See the [Drop Map](Drop-Map.md#bonus-rolls) for the
exact rule and why it lives in code rather than in a loot condition.

**Anger does not survive a reload.** NeroCreatures never writes a player's identity into an entity,
and vanilla's anger persistence does exactly that — so a golem that unloads calms down.

---

## Space Pirate

*Somebody else got here first, and they are not sharing.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/planet_biomes` (any off-Earth surface) |
| **Group size** | 2–3 |
| **Spawn weight** | 10 |
| **Light** | any |
| **Drops** | [Contraband](Drop-Map.md) ×0–2, plus a 6% chance per worn piece of dropping the gear itself |

A humanoid raider, and the only creature whose fight changes with its **kit** rather than its
species. Every pirate rolls one of four loadouts when it spawns:

| Kit | Weapon | Armour |
| --- | --- | --- |
| Recruit, blade | Stone sword | Leather helmet |
| Recruit, crossbow | Crossbow | Leather helmet |
| Raider, blade | Iron sword | Iron helmet, iron chestplate |
| Raider, crossbow | Crossbow | Iron helmet, chainmail chestplate |

Naturally spawned bands are always **recruits**; raider kits exist for deliberate encounters (a
future NeroEvents raid, an operator summon). About 40% of any band carries a crossbow and will keep
its distance, shooting a bolt every two seconds from up to 15 blocks — the melee half is what closes
while you deal with the shooters.

The kit is real equipment, so the weapon's damage is added to the pirate's own, and each worn piece
can drop. Worn gear is usable: a looted iron chestplate is an ordinary iron chestplate.

**Villagers.** Off by default. `pirateVillagerAggression` lets pirates also raid anything in the
`nerocreatures:pirate_raid_targets` entity-type tag (vanilla villagers and wandering traders ship as
optional members). It is a tag rather than a hard-coded list so a colony mod can enroll its own NPCs
from a data pack. Pirates attack players either way.

> **Programmer-art note.** A pirate's held weapon and armour are *not drawn* yet — the shared
> creature renderer has no item-in-hand layer. The gear is fully functional and fully droppable; it
> is only invisible until the real art pass.

---

## Rogue Drone

*The small one gets to you first.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/dark_biomes` |
| **Group size** | 1–2 |
| **Spawn weight** | 6 |
| **Light** | any |
| **Drops** | [Salvaged Circuitry](Drop-Map.md) ×0–2 |

A machine, and it behaves like one: **immune to poison, wither and hunger**, and entirely unbothered
by the dark. It is not a flyer — it pounces and then *glides*, because it carries about 45% of normal
gravity and a 12-block safe-fall distance. In practice it hops off ledges and floats down onto you.

Fragile enough that it is chaff on its own; the point is that it arrives while you are still dealing
with something heavier.

---

## Rogue Android

*Four hits, and then five seconds that actually matter.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/dark_biomes` |
| **Group size** | 1 |
| **Spawn weight** | 3 (the rarest natural spawn in the mod) |
| **Light** | any |
| **Drops** | [Salvaged Circuitry](Drop-Map.md) ×1–3, [Android Core](Drop-Map.md) ×1 at 35%, **+1 circuitry for a clean salvage** |

The heavy frame. Same machine immunities as the drone, and a shield that turns the fight into a
loop:

1. **Shielded.** Every hit is absorbed whole — no damage, no knockback — but each one burns a
   charge. There are four. You will hear each one land.
2. **Break.** The fourth hit shatters the shield with a loud break and a burst of sparks.
3. **Stagger.** For three seconds the frame moves at half speed and takes **1.5× damage**. This is
   the fight; everything else is the wind-up.
4. **Recharge.** The window closes, the shield comes back at full charges, and it starts again.

Nothing here can make it unkillable: `/kill` and the void bypass the shield entirely.

**Salvage.** Finish it *during* the stagger window with anything that is not fire and you recover an
extra Salvaged Circuitry — burning a frame down destroys the boards. This is a bonus roll and is
scaled by `dropRateMultiplier`.

**Where they really come from.** Both androids spawn naturally only rarely. They are a ruins
creature, and NeroRuins will place them properly when it lands; the mod already exposes the
structure-friendly placement helper it will use.

---

## Glacite Wisp

*A knot of ice that has not decided to fall yet.*

| | |
| --- | --- |
| **Biome tags** | `neroland:space/moon_biomes` (weight 12), `neroland:space/crystalline_biomes` (weight 6) |
| **Group size** | 1–2 |
| **Light** | any |
| **Drops** | nothing |

The first of the two **tameable** creatures, and completely harmless: it has no target goal, so a
wild wisp will never attack you and will only ever fight back if you start it. It carries about 3%
of normal gravity and a 16-block safe-fall distance, so it drifts down slopes instead of walking
them — the same trick the Rogue Drone uses, and, like that one, two plain attribute values rather
than any custom movement code.

Feed it **Stalker Sinew** to tame it. Once tamed it follows you, defends you, and lends you Slow
Falling while you are falling nearby. Full details — the taming odds, the command cycle, the caps —
are on [Pets & Drones](Pets-and-Drones.md).

---

## Xertz Forager

*Something small, busy, and very interested in the undergrowth.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/crystalline_biomes` |
| **Group size** | 1–3 |
| **Spawn weight** | 14 |
| **Light** | any |
| **Drops** | nothing |

The second **tameable**, and the friendliest thing in the mod: a low four-legged grazer with a fused
quartz crest, sharing the crystal biomes with the Crystal Golem and the Plasma Slime without being a
threat to anyone. Like the wisp it has no target goal.

Feed it a **Plasma Cell** to tame it. Its crest lights up in the dark and lends its owner Night
Vision — see [Pets & Drones](Pets-and-Drones.md).

---

## Terraforming Drone

*Yours, and busy.*

| | |
| --- | --- |
| **Biome tag** | none — **it never spawns naturally** |
| **Group size** | n/a |
| **Light** | n/a |
| **Drops** | its own Terraforming Drone Shell |

The mod's one utility mob, and the only creature with no spawn rule, no spawn placement and no spawn
egg. It exists only where a player has deployed a crafted **Terraforming Drone Shell**, which binds it
to them in the same action.

It has no target selector at all — it will not attack anything, ever — and it is a machine, so
poison, wither and hunger do not apply to it. Every five seconds it applies one bone-meal-equivalent
growth tick somewhere in the eight-block area around where it was put down, consuming nothing. It
stays in that area, and it is persistent, so it is still there when you come back.

Sneak-interact it with an empty hand to get the shell back. Everything about deploying, recalling,
the work cycle and the caps is on [Pets & Drones](Pets-and-Drones.md).

---

## Cinder Tyrant

*The ember world made this out of its own crust, and then could not put it back.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/dark_biomes` |
| **Group size** | 1 (always alone) |
| **Spawn weight** | 1 — and the weight is not what decides it |
| **Light** | any |
| **Drops** | [Apex Trophy](Drop-Map.md) ×1, [Refined Crystal](Drop-Map.md) ×6–12, [Plasma Cell](Drop-Map.md) ×4–8, [Void Essence](Drop-Map.md) ×1–3 at 50% |

The mod's first **planet boss**: an Apex-tier three-phase fight with a boss bar, an arena it will not
be kited out of, and a summon API that pays participants by how much they actually fought it. It is
fire-immune, it throws fireballs, and it pulls up Plasma Slimes for help.

A boss is a different shape of encounter from everything else on this page, so it has its own
page — **[Bosses](Bosses.md)** covers the phases, the arena rules, both ways one can turn up, the
exact reward split and the event channel other mods can listen to.

Two things worth knowing here: a boss **never despawns**, and natural boss spawns are gated far
harder than the weight above suggests — at most one alive per dimension, and at most one a
Minecraft day.

---

## Spawn eggs

Each creature has a spawn egg in Core's shared creative tab, tagged into
`neroland:highlight/tools` so it takes the tool highlight rather than the material one. The eggs
resolve their creature lazily, so they work identically on Fabric, Forge and NeoForge, and they
ignore biome and light rules — an egg places the creature wherever you click.

| Creature | Egg |
| --- | --- |
| Void Crawler | `nerocreatures:void_crawler_spawn_egg` |
| Lunar Stalker | `nerocreatures:lunar_stalker_spawn_egg` |
| Asteroid Worm | `nerocreatures:asteroid_worm_spawn_egg` |
| Plasma Slime | `nerocreatures:plasma_slime_spawn_egg` |
| Crystal Golem | `nerocreatures:crystal_golem_spawn_egg` |
| Space Pirate | `nerocreatures:space_pirate_spawn_egg` |
| Rogue Drone | `nerocreatures:rogue_drone_spawn_egg` |
| Rogue Android | `nerocreatures:rogue_android_spawn_egg` |
| Glacite Wisp | `nerocreatures:glacite_wisp_spawn_egg` |
| Xertz Forager | `nerocreatures:xertz_forager_spawn_egg` |
| Cinder Tyrant | `nerocreatures:cinder_tyrant_spawn_egg` |

The boss egg is an operator and builder convenience, not the intended way to start a fight: an
egg-placed Tyrant anchors its arena where it lands and drops only its plain loot table. The
contribution split belongs to a deliberate summon — see [Bosses](Bosses.md#how-a-boss-turns-up).

The **Terraforming Drone has no egg**, and that is deliberate: an egg makes an ownerless mob, and an
ownerless drone would sit outside `maxDronesPerPlayer`, could not be recalled and could not be
erased. Its crafted shell is the only way to make one, because the shell is what binds it to a
player.

## Configuration

All of the following are server-authoritative (see the config file `nerocreatures.properties`):

- `spawnsEnabled` — master switch for natural spawning. Eggs still work.
- `globalSpawnWeightMultiplier` — scales every weight in the table above.
- `maxCreaturesPerChunk` / `maxCreaturesPerDimension` — population caps, enforced at placement time
  and respected by slime splitting.
- `hostileAggressionMultiplier` — scales detection range and attack damage, applied once at spawn.
- `dropRateMultiplier` — scales the **bonus** rolls only (the golem's pickaxe bonus, the android's
  salvage bonus), never a creature's loot table.
- `pirateVillagerAggression` — **off by default.** Lets Space Pirates attack villagers and colony
  NPCs as well as players.
- `maxPetsPerPlayer` / `maxDronesPerPlayer` — ownership caps, enforced at tame time and deploy time.
  Either set to 0 disables that feature outright. See [Pets & Drones](Pets-and-Drones.md).
- `naturalBossSpawnsEnabled` / `bossHpMultiplier` / `bossDifficultyMultiplier` — whether bosses
  appear on their own, and how tough and how fast-paced they are. See [Bosses](Bosses.md#configuration).

## Art

Models and textures are **programmer art**: cube geometry and generated 64×64 sheets, regenerated by
`tools/gen_textures.py` (additive — it never overwrites a file that already exists, so hand-drawn
replacements survive). A real art pass is a follow-on.

## See also

- [Spawning](Spawning.md) — the full weight table, the placement sweep and the population caps
- [Drop Map](Drop-Map.md) — every drop, its tags and the mods meant to consume it
- [Pets & Drones](Pets-and-Drones.md) — the two tameables and the deployable drone
- [Bosses](Bosses.md) — the Cinder Tyrant and the boss framework
- [Commands](Commands.md) — `/nerocreatures list` shows the roster with live counts
