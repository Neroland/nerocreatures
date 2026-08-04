# Bosses

Planet bosses: what they are, how a fight works, how one turns up, and exactly how the rewards are
split. The framework here is shared — every future planet boss is built on it — and the first boss
to use it is the **Cinder Tyrant**.

> **Status:** the boss framework and the first signature boss are in. Remaining planet bosses
> ("one per major world") are a follow-on.

## The Cinder Tyrant

*Something the ember world made out of its own crust, and then could not put back.*

| | |
| --- | --- |
| **Biome tag** | `neroland:space/dark_biomes` (the tag Cindara belongs to) |
| **Tier** | Apex — the band used by bosses and nothing else |
| **Health** | 320, before `bossHpMultiplier` |
| **Arena** | 32 blocks around wherever it arrived |
| **Immune to** | fire and lava (it is made of slag), poison and wither |
| **Drops** | [Apex Trophy](Drop-Map.md) ×1, [Refined Crystal](Drop-Map.md) ×6–12, [Plasma Cell](Drop-Map.md) ×4–8, [Void Essence](Drop-Map.md) ×1–3 at 50% |
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
not a strategy in NeroCreatures, it is a reset. Fight it where you found it.

## How a boss turns up

Two entry modes, and they pay differently.

### 1. Natural — you found it

Rare, and gated much harder than any other creature in the mod, because a boss **never despawns**.
All four of these must hold before the spawn engine will even consider one:

1. `naturalBossSpawnsEnabled` is on (it is, by default).
2. No boss has appeared or died in that dimension in the last **Minecraft day**.
3. There is **no boss already alive** in that dimension.
4. The ordinary weighted roll picks it — at weight **1**, against a Void Crawler's 30.

A boss found this way drops **its loot table and nothing else**. No contribution is recorded, and
nothing about the players who fought it is stored anywhere. Whoever kills it takes what falls.

### 2. Summoned — someone started it

The public API `BossSummons.summon(level, pos, bossId, initiator)` starts a **tracked** fight. This
is the seam NeroEvents will call when it lands, and what the operator summon command will use. A
summoned fight:

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
  and [`../PRIVACY.md`](../PRIVACY.md).
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

## Configuration

All server-authoritative, in `nerocreatures.properties`:

| Key | Default | Effect |
| --- | ------- | ------ |
| `naturalBossSpawnsEnabled` | `true` | Whether a boss may appear on its own. Off leaves bosses reachable only through the summon API and the spawn egg. |
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
