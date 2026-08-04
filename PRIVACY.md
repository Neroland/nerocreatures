# NeroCreatures — Privacy & Data Protection

NeroCreatures is designed to comply with POPIA and GDPR. This document describes what player
data the mod stores and how players and server admins control it.

## What is stored

By design, very little. Most of the mod's creatures live as ordinary world entities: they are
saved with the world like any other mob and carry no player information at all.

**Two player-keyed stores exist**, both world-save scoped and both keyed by the player's existing
Minecraft game UUID:

- **Pet and drone ownership** (`nerocreatures:ownership`) — for each creature a player owns:
  the *creature's* entity UUID (a game-internal id, not a player id), whether it is a pet or a
  drone, its entity-type id (for example `nerocreatures:glacite_wisp`), and the id of the
  dimension it was last registered in. Plus one "last updated" timestamp per player, used only
  for retention.
- **Boss contribution** (`nerocreatures:boss_contribution`) — while a **summoned** boss fight is
  running, how much damage each participating UUID has done to it, so the fight's rewards can be
  split fairly. Per fight it holds a random fight id, the boss's id, a timestamp, and a list of
  (player UUID, damage) pairs. Plus one "last updated" timestamp per player, used only for
  retention.

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

## Erasure

NeroCreatures registers with Neroland Core's shared per-player data-erasure hook. A single
request purges the player across all Nero mods, including NeroCreatures:

- players: `/neroland data eraseme`
- admins: `/neroland data erase <uuid>`

For NeroCreatures the request does four things:

1. removes every ownership row for that UUID;
2. follows each row to its creature and frees it — **tamed pets are returned to the wild** (owner
   cleared, standing order reset, ordinary despawnable animals again) and **deployed drones are
   recalled**, dropping their shell where they stood;
3. sweeps every loaded level for any pet or drone still carrying that UUID and frees those too,
   so the erase is complete even if the index and the world had drifted apart;
4. removes that UUID from every boss fight it appears in, and drops any fight it was the last
   participant in.

Erasure never logs player identity — only a count of how many creatures were released.

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
dimension), and their own contribution to any boss fight currently running (fight id, boss id,
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
