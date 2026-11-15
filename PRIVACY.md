# NeroCreatures — Privacy & Data Protection

NeroCreatures is designed to comply with POPIA and GDPR. This document describes what player
data the mod stores and how players and server admins control it.

## What is stored

By design, very little. Most of the mod's creatures live as ordinary world entities: they are
saved with the world like any other mob and carry no player information at all.

**Two player-keyed stores exist**, both world-save scoped and both keyed by the player's existing
Minecraft game UUID:

- **Pet, mount, drone and egg ownership** (`nerocreatures:ownership`) — for each creature a player
  owns: the *creature's* entity UUID (a game-internal id, not a player id), its kind (`pet`,
  `mount`, `drone` or `egg`), its entity-type id (for example `nerocreatures:glacite_wisp`), and the
  id of the dimension it was last registered in. An **egg** row is an egg the player has incubating:
  instead of an entity UUID it holds a random incubation id, plus the game time it hatches
  (`hatch_at`). Mounts and eggs exist so the per-player caps (`maxMountsPerPlayer`,
  `maxIncubatingEggsPerPlayer`) can be enforced; they carry nothing a pet row does not. Plus one
  "last updated" timestamp per player, used only for retention.
- **Boss contribution** (`nerocreatures:boss_contribution`) — while a **summoned** boss fight is
  running, how much damage each participating UUID has done to it, so the fight's rewards can be
  split fairly. Per fight it holds a random fight id, the boss's id, a timestamp, and a list of
  (player UUID, damage) pairs. Plus one "last updated" timestamp per player, used only for
  retention.

Each store also has a **last-known-good backup** kept by Neroland Core's crash-recovery guard, in the
same world save (`data/nerocreatures_ownership_backup.dat` and
`data/nerocreatures_boss_contribution_backup.dat`). A backup holds exactly the same fields as its
store — nothing more — and exists only so a corrupt file can be restored instead of lost. Erasure and
retention reach it in the same request (see below).

The dimension id in the ownership store is there for exactly one reason: so an erase request knows
which level to look in instead of having to walk the whole save. **No coordinates and no position
history are stored anywhere in this mod** — where your pets have been, and where you fought a boss,
are not things NeroCreatures records.

Three things about the contribution store are worth stating plainly, because they mean it is almost
always empty:

- **A boss you simply found in the world records nothing at all.** Contribution exists only for a
  fight that was deliberately summoned (an event, or an operator command). Wandering into a wild
  boss never puts you in this store.
- **The row is deleted when the fight ends.** Paying the rewards out both reads the totals and
  removes them; a boss that leaves the world unbeaten discards them. There is no history kept.
- A fight that somehow survives both of those — the server crashed mid-fight — is pruned after six
  hours.

### Owner data on the entity itself

A tamed pet also carries its owner's UUID in its own entity data, and a deployed Terraforming
Drone carries the UUID of the player who placed it. That is vanilla Minecraft's own design for
every tameable animal (a wolf, a cat and a parrot all do the same thing) and it is inherent to
the creature belonging to someone — an owner reference is what "owned" *means* to the game.
NeroCreatures adds nothing beyond it: no names, no coordinates, no interaction history.

Erasure covers it. Clearing a player's data does not merely delete the index row; it also finds
the creatures themselves and severs the link — see below.

No names, IP addresses, chat, coordinate history, or any other personal information is stored —
nothing beyond what Minecraft itself already keeps.

Two items carry game data, never player data: a **Captain's Ledger** holds the position of its pirate
stash on the item itself (vanilla's lodestone-tracker component), with no record of who killed the
captain; it goes only to the player the kill is credited to. A tamed **Magnet Crab** reads vanilla's
own "who threw this" mark on ground items, in memory and only to refuse items its owner did not drop;
it stores nothing. A **Gravity Leech** tether is a temporary attribute modifier that is never saved.

Two milestone records — "tamed a first pet" and "deployed a first drone" — are kept as player-scope
progression gates in Neroland Core's store (`nerocreatures:first_tame`, `nerocreatures:first_deploy`),
covered by Core's privacy statement and erased by the same shared erasure hook described below.

## Erasure

NeroCreatures registers with Neroland Core's shared per-player data-erasure hook. A single
request purges the player across all Nero mods, including NeroCreatures:

- players: `/neroland data eraseme`
- admins: `/neroland data erase <uuid>`

For NeroCreatures the request does four things:

1. removes every ownership row for that UUID (pets, mounts, drones and eggs);
2. follows each row to its creature and frees it — **tamed pets are returned to the wild** (owner
   cleared, standing order reset, ordinary despawnable animals again), **mounts are unsaddled**
   (saddle, panniers and their contents dropped where the mount stands) and returned to the wild,
   **deployed drones are recalled**, dropping their shell where they stood, and **egg rows are
   dropped** (the egg still hatches, owned by nobody);
3. sweeps every loaded level for any pet or drone still carrying that UUID and frees those too,
   so the erase is complete even if the index and the world had drifted apart;
4. removes that UUID from every boss fight it appears in, and drops any fight it was the last
   participant in;
5. refreshes both stores' last-known-good backups straight away, so the erased rows do not linger
   in the backup copy. Retention purges do the same.

Erasure never logs player identity — only a count of how many creatures were released.

**Creatures in unloaded chunks.** A pet, mount or drone whose chunk is not loaded at the time of the
request cannot be reached then. So the request leaves two markers in the ownership store: the
creature UUIDs of the rows it removed, and a **salted one-way hash** of the erased player's UUID (a
random salt per world, kept only in that file). Neither is the player's UUID, and neither can be
turned back into it. When such a creature next loads, it finds its marker and is released (or, for a
drone, recalled) silently, with no message to anyone. A creature's marker is removed once it is
freed; the hash is removed if that player tames or deploys something again. Each marker set is capped
(oldest first). A creature is only ever released on such a marker: an owner the store simply does not
know (an older save, a store restored from backup) keeps their creature, which is re-indexed.

The hook is registered at mod construction, before the store it purges is ever loaded, precisely
so that a later store can never be added without being covered by it.

## Retention

Two mechanisms, and both end at the same place:

- Core's `purge-inactive` sweep calls the erasure hook above for every player inactive longer
  than `dataRetentionDays`.
- NeroCreatures additionally prunes **both** of its stores the first time each is read in a server
  session: any player whose ownership has not changed in `dataRetentionDays` days is erased in full
  — row *and* creatures — so a pruned record leaves nothing behind, not even an owner UUID sitting
  in a pet's entity data; and any player whose boss contribution has not changed in that long is
  dropped from the contribution store.

`dataRetentionDays` is Core's setting and defaults to `0`, which disables the timed sweep and
leaves erasure entirely on request. Each record carries a single "last updated" timestamp for
exactly this purpose, and the sweeps log only *how many* records were pruned — never who. The
contribution store has one further, unconditional control on top of retention: an abandoned fight
is dropped after six hours regardless of the setting.

## Access / export

NeroCreatures can produce an admin-safe JSON export of **a single player's own records and no one
else's**: their last-updated timestamp and their list of owned creatures (entity UUID, kind, type,
dimension, and for an incubating egg its hatch time), and their own contribution to any boss fight currently running (fight id, boss id,
damage) — never another participant's totals in the same fight.

The operator command is **`/nerocreatures export <player>`** (permission level 2). A player is
named by their online name *or* by a raw UUID, so an access request works for someone who has
already left. Output goes to the operator who ran the command and to nobody else: it is never
broadcast to other operators and never written to `latest.log`.

Operator permission is required because an export is somebody's personal record. A player asking
for their own data asks an operator to run it — or reads the same data themselves through a
companion app, where it is scoped to them automatically.

## Companion app (link module)

NeroCreatures exposes your own bestiary progress, your pets and your boss contribution to a
**Neroland companion app** through Neroland Core's link API. NeroCreatures ships no server, no HTTP
and no outbound connection of its own — it only registers what it is able to show; a separate bridge
mod serves that to a paired app, and **that pairing is the consent step**. With no bridge installed,
nothing is exposed.

What an app can see is **your own data only**. Never another player's records, never names, never
coordinates.

- **The link module stores nothing new.** Every read goes to one of the two stores above, or to
  vanilla's own kill statistics — so a player erased through the shared hook immediately reads as
  owning nothing and having contributed nothing, with no extra wiring.
- **One write, and it is owner-scoped:** `pet_recall` brings your own pets to where you are
  standing. Somebody else's pet answers exactly like a creature that does not exist, so the action
  cannot be used to find out what other players own.
- **Live events.** A change to one of your own creatures is routed to your sessions alone. Boss
  phase and defeat events are broadcast to every session and therefore carry a boss, a phase and a
  **dimension** and nothing player-shaped at all — no participant list, no killer, no position.
- **Alerts.** Two per-player alerts are raised through Core's alert store — "your pet died" and "a
  boss you fought was defeated" (the latter only for a *summoned* fight, which is the only kind that
  records participants). Alert text names a creature or a boss and nothing else, and alerts are
  purged by the same shared erasure request as everything else.

The full contract, section by section, is published at
[`wiki/Link-Module.md`](wiki/Link-Module.md).

## Neighbours, claims and requests from the client

- **Creatures never change another player's world for anyone.** Every creature ability that places,
  replaces or advances a block, moves a ground item, or spawns a helper entity asks one check first:
  its own config key, the `mobGriefing` game rule (through the loader's mob-griefing event on NeoForge
  and Forge, which claim mods hook), the loader's block-place event (NeoForge and Forge), and, for an
  owned creature, whether its owner could do the same thing in person (online in that dimension,
  allowed to build there, not under spawn protection). With the owner offline, owned creatures make no
  world changes. A refused check falls back to a harmless variant and logs nothing.
- **Fabric gap:** Fabric has no standard claim event. On Fabric a claim mod that does not hook the
  `mobGriefing` rule is not consulted for **wild** creatures. Owned creatures are still limited to what
  their owner may do. Turn the ability keys off, or `mobGriefing` off, if this matters on your server.
- **Requests from the client** (for example "open my mount's panniers") carry only the creature's
  session network id and an action number. The server checks that the sender owns the creature, is
  close to it and is not flooding requests. The flood counter lives in memory only, is keyed by the
  connected player object, is never saved, and disappears when the player leaves.
- **`/nerocreatures budget`** reports creature species and timings only, to the operator who ran it.

## Telemetry

NeroCreatures ships anonymous crash reporting via **Sentry** (EU ingest servers), matching the
rest of the Neroland ecosystem. It is **on by default and opt-out**:

- **Opt out:** set `telemetryEnabled=false` in `config/nerocreatures.properties` (takes effect
  on restart). This is a client-local setting — a server can never force it on or off.
- **NeroCreatures-only:** a report is sent only if its stack trace touches
  `za.co.neroland.nerocreatures`; everything else is dropped before it leaves the game.

> **Current status: active from 0.1.0.** This build carries a real Sentry DSN, so everything
> described here applies. Set `telemetryEnabled=false` and no network connection is opened at all.
> A build that carries no DSN (a fork, a stripped build) stays a hard no-op regardless of config.

### What a report contains

Stack trace; NeroCreatures / Minecraft / loader / OS / Java version strings; the ids and
versions of your other installed mods; this mod's own config values; recent in-game
NeroCreatures actions (breadcrumbs); anonymous stability and timing data.

### What a report never contains

No IP address, username, player UUID, world name or seed, coordinates, chat, or **any ownership
or contribution data**. `sendDefaultPii` is off, the machine hostname is never attached, the
Sentry user object is cleared on every event, and file paths are scrubbed of your OS account
name before sending. Volume is bounded: events are de-duplicated per session and capped at 10
per game session.

### CurseForge telemetry blurb

Keep the following (or equivalent) in the project description while telemetry is enabled, as
required by CurseForge moderation policy:

> **Telemetry notice:** NeroCreatures sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, pet ownership or world data) to the developers
> via Sentry (EU servers) so crashes can be fixed. On by default — opt out any time by setting
> `telemetryEnabled = false` in `config/nerocreatures.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerocreatures/blob/main/PRIVACY.md).
