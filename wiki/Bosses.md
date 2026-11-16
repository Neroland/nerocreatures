# Bosses and the Apex Altar

Planet bosses: what they are, how a fight works, how one turns up, and exactly how the rewards are
split. Four bosses share one framework: the **Cinder Tyrant** (Cindara), the **Rime Matriarch**
(Glacira), the **Bloom Sovereign** (Greenxertz) and the **Facet Colossus** (Nyxara). The Tyrant can
also turn up on its own; the other three come only from the **[Apex Altar](#the-apex-altar)**.

| Boss | World | Health | Arena | Phases | Signature drop |
| --- | --- | --- | --- | --- | --- |
| Cinder Tyrant | Cindara | 320 | 32 blocks | Slag and Stone, Ashfall, Meltdown | Tyrant Ember |
| Rime Matriarch | Glacira | 300 | 24 blocks | Veiled, Spreading Frost, Shattered Crown | Matriarch Frostcore |
| Bloom Sovereign | Greenxertz | 340 | 20 blocks | Budding, Full Bloom, Wilting Heart | Sovereign Seed |
| Facet Colossus | Nyxara | 380 | 28 blocks | Turning Facets, False Light, Shattered Ring | Colossus Facet |

Health is before `bossHpMultiplier`. Every boss changes phase at 66% and 33% health; a phase change is
a 30-tick pause in which the boss starts no attack.

**Every boss has to be fought.** A boss takes damage only from a player or from something a player
owns (a pet, a mount, a drone): lava, fire, suffocation in a wall, falling, cacti, dispensers and
other mobs do nothing to it. `/kill` and the void still work. A boss never goes through a portal and
never rides a minecart or boat, so it cannot be carried out of its arena. Every boss drops one **Apex Trophy**, its
signature drop and Refined Crystal (see each boss and the [Drop Map](Drop-Map.md)).

## The Cinder Tyrant

*Something the ember world made out of its own crust, and then could not put back.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/dark_biomes` (the tag Cindara belongs to) |
| **Tier** | Apex — the band used by bosses and nothing else |
| **Health** | 320, before `bossHpMultiplier` |
| **Arena** | 32 blocks around wherever it arrived |
| **Immune to** | fire and lava (it is made of slag), poison and wither |
| **Drops** | [Apex Trophy](Drop-Map.md) ×1, [Tyrant Ember](Drop-Map.md) ×1, [Refined Crystal](Drop-Map.md) ×6–12, [Plasma Cell](Drop-Map.md) ×4–8, [Void Essence](Drop-Map.md) ×1–3 at 50% |
| **Sigil** | Cinder Tyrant Sigil ([Apex Altar](#the-apex-altar)) |
| **Spawn egg** | `nerocreatures:cinder_tyrant_spawn_egg` |

It is themed off the real character of Nerospace's Cindara: a hot, rainless, ash-black world with no
weather and a dark sky. So the Tyrant throws fire, cannot be burned, and pulls live plasma out of the
ground when it needs help.

### The three phases

The boss bar shows the phase name next to the boss's name, so you always know which fight you are
in. Phases only ever advance — healing a boss does not rewind it.

| # | Phase | From | What changes |
| - | ----- | ---- | ------------ |
| 1 | **Slag and Stone** | full health | Heavy melee plus a **ground slam**: a one-second wind-up, then a shockwave that damages and throws everything within 5 blocks. |
| 2 | **Ashfall** | 66% health | It stops relying on reach. **Fireball volleys** (three shots, from 5–24 blocks) make cover matter, and it starts **calling up Plasma Slimes** — up to 4 alive at once, in waves of 3. |
| 3 | **Meltdown** | 33% health | It **enrages**: permanently 35% faster and 30% harder-hitting, with a bigger, quicker slam *and* a five-shot volley on roughly half the cooldown. The shortest phase, and the one that decides the fight. |

**Fighting it.** Phase 1 is about spacing — the slam is telegraphed and you can simply walk out of
the ring, so the cost of greedy melee is the whole mechanic. Phase 2 is about cover and about
deciding what to do with the adds: they are budgeted against the population caps and capped at four,
so **killing them is what lets it make more** and ignoring them is what stops it. Phase 3 is about
having saved something.

**The adds are small.** The Tyrant summons Plasma Slimes at their smallest size, which are terminal
— they do not split further. They are pressure, not a second boss fight.

### The arena

The Tyrant's arena is anchored **wherever it arrives** and reaches 32 blocks. Pull it beyond that and
it drops its target, walks home and **regenerates fast** on the way; pull it more than twice as far
and it is simply put back at full health.

This is deliberate and it is not negotiable: kiting a boss into a hole, up a pillar or off the map is
not a strategy in NeroCreatures, it is a reset. Fight it where you found it. The same leash holds for
every boss below, at its own arena radius.

## The Rime Matriarch

*Glacira's queen of the white-out; the cold follows her across the floor.*

| | |
| --- | --- |
| **Health** | 300 · arena 24 blocks · immune to freezing |
| **Drops** | Apex Trophy ×1, [Matriarch Frostcore](Drop-Map.md) ×1–2, Refined Crystal ×4–8 |
| **Sigil** | Rime Matriarch Sigil · altar only, no natural spawn |

- **Blizzard beam** (every phase; cooldown 160 / 120 / 80 ticks): a one-second telegraph, then the aim
  **locks** and a beam fires for a second along that line, 24 blocks, hitting up to 8 players and
  freezing them. Anything behind a solid block is safe.
- **Ice pillars**: three-high columns of temporary rime ice rise around her (3 / 4 / 5 by phase, never
  more than 6) — your cover from the beam. They melt after a minute.
- **Freezing floor**: a ring that widens each phase (5 / 9 / 13 blocks) builds up frost on anyone
  standing in it, harmless in phases 1–2 and lethal-cold in phase 3; still water in it freezes over
  (and thaws back to water afterwards).
- **When arena blocks are off** (`bossArenaBlocks=false`, `mobGriefing` off, or a claim): no pillars
  and no frozen water; instead the beam's telegraph is **twice as long** and any solid block is cover.
  The fight stays fair with zero world changes.

## The Bloom Sovereign

*A rooted flower the size of a house, and everything she has infected.*

| | |
| --- | --- |
| **Health** | 340 · arena 20 blocks · rooted (never moves), knockback-immune |
| **Drops** | Apex Trophy ×1, [Sovereign Seed](Drop-Map.md) ×1–2, Refined Crystal ×4–8 |
| **Sigil** | Bloom Sovereign Sigil · altar only, no natural spawn |

- **Spore pods** grow in a ring 5 blocks out (2 / 3 / 4 by phase, never more than 4) and regrow.
- **Heal**: a three-second channel that heals 2.5% of her health **per pod alive when it ends**. Kill
  the pods mid-channel and the heal is lost. Pods drop nothing.
- **Infecting lash** (reach 14, needs line of sight): poison, then a sporeling at the target's feet
  while she has fewer than 2 / 4 / 6 adds and the world's sporeling cap allows. With
  `bloomHostSporelings=false` the lash still poisons but raises nothing.
- **Bark**: she only takes damage from an attacker within **16 blocks that she can see**, the reach of
  her lash. Arrows from further away, or blows struck from under an overhang where she has no line of
  sight, glance off her bark with a puff of spores. Her pods are not protected. Fight her in the open.
- When she dies or leaves, her pods and sporelings go with her.

## The Facet Colossus

*A crystal giant wearing a ring of six mirrors; only one of them is ever really there.*

| | |
| --- | --- |
| **Health** | 380 · arena 28 blocks · armour 14 |
| **Drops** | Apex Trophy ×1, [Colossus Facet](Drop-Map.md) ×1, Refined Crystal ×6–12, Plasma Cell ×4–8 |
| **Sigil** | Facet Colossus Sigil · altar only, no natural spawn |

- **One lit facet**: of the six plates turning round it, only the **lit** one takes damage, and only
  from within 40° of where it faces. Other plates block with a clink. The lit facet moves every
  160 / 120 / 100 ticks, never to the same plate twice.
- **Refracting beam**: aim locks after half a second, then three pulses (6 / 8 / 10 damage by phase)
  that bounce off its arena crystals (temporary prism pillars, at most 12 blocks, gone after 45 s).
  With arena blocks off it fires two extra split beams instead of growing crystals. The beam hurts
  only players inside the arena and wild hostile mobs; pets, mounts, livestock, villagers and players
  outside the arena are never hit.
- **Phase 2, False Light**: two **decoy** plates glow too. Hitting a decoy does nothing to it and
  sparks 3 damage back at you (at most once every half second).
- **Phase 3, Shattered Ring**: after every turn of the ring its core is exposed for 1.5 seconds and
  takes half damage from any side.

## The Apex Altar

The altar is how a planned boss fight starts. Craft it (Refined Crystal, Void Essence, Plasma Cell
and obsidian), place it, and use a **boss sigil** on it.

| Sigil | Recipe: top and bottom, left and right, centre | Summons |
| --- | --- | --- |
| Cinder Tyrant Sigil | Slag Carapace, Heat Gland, Plasma Cell | Cinder Tyrant |
| Rime Matriarch Sigil | Rime Wool, Cryo Fang, Coolant Milk | Rime Matriarch |
| Bloom Sovereign Sigil | Mossback Hide, Drifter Spores, Bloom Heart | Bloom Sovereign |
| Facet Colossus Sigil | Prism Antler, Mirror Scale, Refined Crystal | Facet Colossus |

Sigils are made from habitat drops, never from a boss's signature drop. The sigils are in the item
tag `nerocreatures:sigils`.

**What happens when you use a sigil.** The server checks, in this order:

1. the fight this altar started is still going → refused;
2. the altar is still **recharging** → refused, with the seconds left;
3. the boss is known → otherwise refused;
4. the world is not on **Peaceful** (where a boss could never fight back) → otherwise refused;
5. there is room for the **boss's whole body** above the altar, plus half a block on every side and
   above → otherwise refused. The largest boss (the Bloom Sovereign, 3 × 4.5 blocks) needs about a
   5 × 5 × 5 open space; the smallest (the Rime Matriarch) about 3 × 3 × 5;
6. the dimension and chunk population caps have room → otherwise "the area is too crowded".

On success the boss appears on top of the altar as a **summoned, contribution-tracked fight** (below),
you are recorded as a zero-damage participant, and the sigil is used up (not in creative). A refused
attempt uses nothing. The altar then **locks**: its top glows, it cannot be mined in survival, and it
refuses sigils until the fight is over **and** the cooldown has run (`apexAltarCooldownTicks`,
default 12000 ticks = 10 minutes). A fight older than one in-game hour never keeps an altar locked, and an altar brought in from
another world never stays locked longer than the longest possible cooldown.

The cooldown belongs to the **altar**, never to a player. The altar stores no items (the sigil goes
straight from your hand into the summon) and has no screen; it is immune to withers and dragons.

## How a boss turns up

Two entry modes, and they pay differently.

### 1. Natural — you found it (Cinder Tyrant only)

Rare, and gated much harder than any other creature in the mod, because a boss **never despawns**.
All four of these must hold before the spawn engine will even consider one:

1. `naturalBossSpawnsEnabled` is on (it is, by default).
2. No boss has appeared or died in that dimension in the last **Minecraft day**.
3. There is **no boss already alive** in that dimension.
4. The ordinary weighted roll picks it — at weight **1**, against a Void Crawler's 30.

A boss found this way drops **its loot table and nothing else**. No contribution is recorded, and
nothing about the players who fought it is stored anywhere. Whoever kills it takes what falls.

### 2. Summoned — someone started it

Using a sigil on the [Apex Altar](#the-apex-altar) starts a **tracked** fight. Under it is the public
API `BossSummons.summon(level, pos, bossId, initiator)`, which is also the seam NeroEvents will call
and what the operator summon command uses. A summoned fight:

- anchors its arena at the summon position;
- records **damage per player** for as long as the fight lasts;
- pays out **enhanced rewards split by that damage**, on top of the loot table, when the boss dies.

The `initiator` is optional and is used for exactly one thing: being put on the reward list at zero
damage, so whoever paid to start the fight gets the base share even if they never land a hit.

A summon is refused if the dimension or the chunk is already at its NeroCreatures population cap.

> A boss placed with the **spawn egg** counts as a natural fight: it anchors an arena and drops its
> plain loot table. Contribution belongs to a deliberate summon, not to a right-click.

## The contribution reward rule

This is the exact rule, and it applies **only to summoned fights**.

Let `share` be a participant's damage divided by all damage recorded for that fight.

| Who | Gets |
| --- | ---- |
| **Every recorded participant** (including the summoner at zero damage) | 1 Apex Trophy, 2 Refined Crystal, 2 Plasma Cell — the **base share** |
| `share` **below 5%** | the base share **and nothing more** |
| `share` **at or above 5%** | the base share **plus** `round(share × 12)` Refined Crystal and `round(share × 8)` Plasma Cell |
| `share` **at or above 25%** | all of the above **plus a second Apex Trophy** |

Three things to note:

- **The 5% floor is the anti-leech rule.** Turning up is worth the base share; landing one arrow
  from the back of the room is not worth a boss's worth of materials.
- **The pool is what the fight is worth, divided by who did the work.** Because the shares of
  everyone above the floor sum to at most 1, twelve Refined Crystal and eight Plasma Cell is the
  most the scaled part can ever pay out in total, however many people turn up.
- **The loot table still drops, in full, where the boss fell.** A summon only ever adds.

**Where your share lands.** If you are online in the boss's dimension when it dies, your share drops
at your feet. If you have logged out or left the dimension, it drops where the boss fell — leave the
fight before it ends and your share is on the floor with everybody else's. There is deliberately no
mailbox: holding a player's unclaimed items would mean keeping a player-keyed record indefinitely,
and this mod does not do that.

**Scaling.** Every count above is a *bonus roll* in the [Drop Map](Drop-Map.md#bonus-rolls) sense and
is scaled by `dropRateMultiplier`. Set it to `0` to switch the whole enhanced reward off and leave
the loot table alone.

## What is stored about you, and for how long

A summoned fight keeps one thing: **your game UUID and a running damage total**, in a world-save
store called `nerocreatures:boss_contribution`, plus one "last updated" timestamp per player.

- **No names, no coordinates, no per-hit history.** A row is a UUID and a number.
- **Natural fights store nothing at all.** Wandering into a wild boss never puts you in this store.
- **The row is deleted when the fight ends.** Resolving a fight both reads the shares and removes
  it; a boss that leaves the world unbeaten discards it. In normal play this store is empty.
- A fight that somehow survives both (a crash mid-fight) is pruned after 6 hours.
- **Erasure:** `/neroland data eraseme` removes your UUID from every fight it appears in, alongside
  everything else NeroCreatures holds. See [Pets & Drones](Pets-and-Drones.md#what-is-stored-and-how-to-get-rid-of-it)
  and [`PRIVACY.md`](https://github.com/Neroland/nerocreatures/blob/main/PRIVACY.md).
- **Retention:** honours Core's `dataRetentionDays` like the ownership store does.
- **Access:** a per-player export returns exactly your own contribution and nobody else's.

## For other mods: the `nerocreatures:boss_pressure` channel

Every phase transition and every defeat is published on **Neroland Core's threshold event bus**, so
another mod can react to a boss fight escalating while depending only on Core. No NeroCreatures
import, no hard dependency, and it degrades to nothing when NeroCreatures is absent.

```text
channel   nerocreatures:boss_pressure
scope     the dimension id the fight is in, as a string (e.g. "nerospace:space")
```

| Event | `value` | `threshold` | `rising` |
| ----- | ------- | ----------- | -------- |
| Entered phase *n* (1-based) | *n* | *n* | `true` |
| Boss defeated | `0` | the boss's phase count | `false` |

**A defeat is the only crossing this channel publishes with `rising == false`**, so one boolean
tells a consumer "the fight got worse" from "the fight is over".

**The scope is a place, never a person.** Core's `ThresholdCrossing` contract forbids encoding a
player identifier into a scope, and this channel never does — no UUID, no name, no position ever
goes on the bus. If you are building a consumer, treat the scope as a dimension id and nothing else.

Crossings are fired with `nerocreatures` as the **source mod id** (Core 1.15.0's sourced bus), so a
consumer listening with `ThresholdEvents.onSourcedCrossing` can tell this mod's channel from a datapack's.
Since the Apex Altar, every boss publishes on this channel, in any dimension an altar stands in, and
a boss that is removed without dying publishes no falling crossing.

### `nerocreatures:cinder_tyrant_defeated`

A second channel, published once when a **Cinder Tyrant** (and no other boss) is defeated: scope =
the dimension id, `value` `0`, `threshold` `1`, `rising` `false`. Same rules: a place, never a
person. The shipped quest pack's `cindara_quiet` world-log quest listens here, so it completes only on
a Tyrant's defeat on Cindara ([NeroQuests integration](NeroQuests-Integration.md)).

## Configuration

All server-authoritative, in `nerocreatures.properties`:

| Key | Default | Effect |
| --- | ------- | ------ |
| `naturalBossSpawnsEnabled` | `true` | Whether the Cinder Tyrant may appear on its own (the other bosses never do). Off leaves bosses reachable only through the Apex Altar, the summon API and the spawn egg. |
| `apexAltarCooldownTicks` | `12000` | How long an Apex Altar recharges after it summons a boss (per altar). `0` = no cooldown; a live fight still blocks. |
| `bossArenaBlocks` | `true` | Rime Matriarch ice pillars and frozen water, Facet Colossus prism crystals. Off = each boss's block-free fallback. Always temporary, always restored when the fight ends. |
| `bossHpMultiplier` | `1.0` | Scales a boss's maximum health, applied once at spawn. |
| `bossDifficultyMultiplier` | `1.0` | Scales a boss's attack damage **and its pacing** — every phase cooldown is divided by it, so a higher value makes a boss slam and shoot more often, not just harder. |
| `dropRateMultiplier` | `1.0` | Scales the contribution reward. `0` switches it off entirely; the loot table is untouched. |
| `maxCreaturesPerChunk` / `maxCreaturesPerDimension` | `8` / `200` | A boss and its adds are inside the same population caps as everything else. A summon into a full dimension is refused. |

Both boss multipliers are applied to attribute **base values** at spawn, which are saved with the
entity. A boss already in the world keeps the numbers it was born with; a config change applies to
the next one.

## Building a boss (contributors)

The framework is three pieces:

- **`boss/BossController`** — the state machine. Phases, the boss bar, the arena leash, the config
  scaling, the contribution hook and the threshold events. A boss holds one.
- **`boss/BossPhase`** — a description: an id, a display title, a health fraction, a *factory* for
  the goals active during the phase, and a one-off entry action. Goals carry cooldown state, so a
  phase must build fresh ones every time it is entered; the controller adds them on entry and
  removes them on exit. Anything the boss should always be able to do belongs in its own
  `registerGoals` instead, where the controller will not touch it.
- **`entity/boss/NeroBossEntity`** — the entity base that wires the controller into `finalizeSpawn`,
  `customServerAiStep`, `hurtServer`, `die`, `remove` and save/load. A boss extends this, declares
  its phases, and is done.

Three reusable goals landed with it, all with documented cost profiles like the rest of
`entity/ai/`: `GroundSlamGoal`, `FireVolleyGoal` and `SummonAddsGoal` (which is budgeted against the
population caps exactly as slime splitting is).

## See also

- [Bestiary](Bestiary.md) — every non-boss creature.
- [Spawning](Spawning.md) — the spawn table the boss's gated line sits in, and the caps a summon
  respects.
- [Drop Map](Drop-Map.md) — what the Apex Trophy and the rest of the drops are for.
- [Pets & Drones](Pets-and-Drones.md) — the mod's other player-keyed store.
- [Commands](Commands.md) — `/nerocreatures summon-boss`.
- [Link module](Link-Module.md) — what a companion app sees of a fight you are in.
- [Data storage](Data-Storage.md) — what a summoned fight records, and how to erase it.
