# Data storage

Everything NeroCreatures saves about a player, where it lives, how long it stays, and how to get rid
of it. The formal statement is [`PRIVACY.md`](../PRIVACY.md); this page is the practical version.

> **The short version:** two small stores, both world-save scoped, both keyed by your existing
> Minecraft game UUID, neither containing your name or any coordinates. One erase request clears
> both — and every other Nero mod — at once.

## The two stores

Most of the mod stores nothing at all. Its creatures are ordinary world entities, saved with the
world like any other mob and carrying no player information.

There are exactly **two** player-keyed stores. Adding a third is a deliberate act that means wiring
it into the shared erasure hook and declaring it in `PRIVACY.md` in the same change.

### 1. Ownership — `nerocreatures:ownership`

The index of who owns which pet or drone. Per creature it holds:

| Field | Example | Why |
| --- | --- | --- |
| the *creature's* entity UUID | `4f3c…` | a game-internal id, not a player id |
| kind | `pet` / `drone` | the two things capped and erased differently |
| entity type id | `nerocreatures:glacite_wisp` | so an app or an export can name it |
| dimension id | `nerospace:glacira` | **only** so an erase request knows which level to look in |

Plus one "last updated" timestamp per player, used only for retention.

It exists for three jobs and no others: enforcing `maxPetsPerPlayer` / `maxDronesPerPlayer` without
loading every chunk in the save, finding every creature bound to a player during an erasure, and
answering a data-access request.

It is an **index, not the source of truth**. Ownership really lives on the entity, where vanilla puts
it. If the two ever disagree, the entity wins.

### 2. Boss contribution — `nerocreatures:boss_contribution`

Damage totals for **summoned** boss fights, so the enhanced rewards can be split fairly. Per fight:
a random fight id, the boss's id, a timestamp, and a list of (player UUID, damage) pairs.

Three things make this store almost always empty:

- **A boss you simply found in the world records nothing about anybody.** Contribution exists only
  for a fight that was deliberately summoned. That is a privacy control, not an optimisation.
- **The row is deleted the moment the fight ends** — paying rewards out both reads the totals and
  removes them; a boss that leaves the world unbeaten discards them. No history is kept.
- A fight that survives both (the server crashed mid-fight) is pruned after **six hours**.

### What is *not* stored, anywhere

No names. No IP addresses. No chat. **No coordinates and no position history** — where your pets
have been and where you fought a boss are not things NeroCreatures records. No per-hit history. No
kill log: the bestiary an app shows you is read from *vanilla's own* statistics, not from a
NeroCreatures store.

## Owner data on the entity itself

A tamed pet carries its owner's UUID in its own entity data, and a deployed Terraforming Drone
carries the UUID of whoever placed it. That is vanilla's design for every tameable animal — a wolf, a
cat and a parrot all do the same — and it is inherent to a creature *belonging* to someone.

NeroCreatures adds nothing beyond it, and **erasure covers it**: clearing a player does not merely
delete an index row.

## Erasure

NeroCreatures registers with Neroland Core's shared per-player erasure hook, so one request purges
you across every Nero mod at once.

- **As a player:** `/neroland data eraseme`
- **As an operator:** `/neroland data erase <uuid>`

For NeroCreatures, that request does four things in order:

1. takes your rows out of the ownership index;
2. follows each row to its creature — in the dimension the row names, which is the only reason that
   dimension id is stored — and **frees it**: a **pet is returned to the wild** (owner cleared, order
   reset, despawnable again) and a **drone is recalled**, dropping its shell where it stood;
3. sweeps every **loaded** level for any pet or drone still carrying your UUID and frees those too,
   so the erase is complete even if the index and the world had drifted apart;
4. removes you from every live boss fight, dropping any fight that leaves empty.

A creature in an unloaded chunk that the index did not know about is the one gap, and it closes
itself: such a creature is unreachable until its chunk loads, its owner row is already gone, and it
can never be counted, recalled or attributed to anyone again.

**Nothing on the erasure path logs who was erased** — only a count of creatures released.

## Retention

Retention honours **Core's** `dataRetentionDays` setting; NeroCreatures has no separate one.

The first time each store is read in a server session, any player untouched for longer than that is
erased **in full** — index row *and* creatures. That is deliberate: dropping only the row would leave
a pruned player's UUID sitting in a pet's entity data, which would not be erasure at all.

Core's default is `0`, which disables the local sweep and leaves retention entirely to Core's own
purge-inactive flow — which reaches both of these stores through the same registered eraser.

Only the number of records pruned is logged, never which ones.

## Access (export)

`/nerocreatures export <player>` prints exactly one player's own record as JSON — their ownership
rows and their own boss contribution, and nobody else's. See [Commands](Commands.md).

Operator permission is required because it is somebody's personal record; a player asking for their
own data asks an operator to run it. A player may be named by their **online name or their raw
UUID**, because a data-access request has to work for someone who has left.

The same data is readable through a companion app, scoped to the asking player automatically — see
[Link module](Link-Module.md).

## Resilience: `SavedDataRecovery`

Every saved-data read in this mod goes through a recovery guard (ported from Nerospace by way of
NeroQuests): a corrupt or unreadable file degrades to an **empty store** and a clean file at the next
save, instead of crashing the server repeatedly on every load.

The cost of that degradation is bounded and deliberate — an unreadable ownership index means pets
have to be re-registered by interacting with them; an unreadable contribution store means one
fight's bonus rewards. Both are better than an unstartable world.

## Telemetry

Crash reporting is a separate thing entirely, contains no player data, and is opt-out.
See [Telemetry](Telemetry.md).

## See also

- [`PRIVACY.md`](../PRIVACY.md) — the formal statement
- [Pets & Drones](Pets-and-Drones.md) — the player-facing view of the ownership store
- [Bosses](Bosses.md) — what a summoned fight records, and for how long
- [Commands](Commands.md) — `export`
- [Link module](Link-Module.md) — the companion-app read path
