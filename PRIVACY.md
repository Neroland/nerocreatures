# NeroCreatures — Privacy & Data Protection

NeroCreatures is designed to comply with POPIA and GDPR. This document describes what player
data the mod stores and how players and server admins control it.

## What is stored

Nothing yet, and by design very little ever. The mod's creatures live as ordinary world
entities; they are saved with the world like any other mob and carry no player information.

Two player-keyed stores are planned, both world-save scoped and both keyed by the player's
existing Minecraft game UUID:

- **Pet and drone ownership** — which tamed creatures and deployed drones belong to which
  UUID, plus a "last updated" timestamp used only for retention.
- **Boss contribution** — how much damage a UUID contributed to a summoned boss fight, plus a
  timestamp, so rewards can be split fairly.

No names, IP addresses, chat, coordinate history, or any other personal information is stored —
nothing beyond what Minecraft itself already keeps.

## Erasure

NeroCreatures registers with Neroland Core's shared per-player data-erasure hook. A single
request purges the player across all Nero mods, including NeroCreatures:

- players: `/neroland data eraseme`
- admins: `/neroland data erase <uuid>`

Erasing a player untames their pets, deactivates their drones and drops their contribution
counters. Erasure never logs player identity.

The hook is registered at mod construction, before the stores it will purge exist, precisely so
that a later store can never be added without being covered by it.

## Retention

Player records for inactive players are purged automatically when Core's `purge-inactive` runs:
the erasure hook above clears NeroCreatures' records along with every other Nero mod's data.
Each record carries a single "last updated" timestamp for exactly this purpose, and the sweep
logs only *how many* records were pruned — never who.

## Access / export

An admin-safe JSON export of **a single player's own ownership and contribution records and no
one else's** is planned for data-access requests, printed to the operator who ran the command
and to nobody else.

## Companion app (link module)

NeroCreatures will be able to expose your own bestiary progress, your pets and your boss
contribution to a **Neroland companion app** through Neroland Core's link API. NeroCreatures
ships no server, no HTTP and no outbound connection of its own — it only registers what it is
able to show; a separate bridge mod serves that to a paired app, and **that pairing is the
consent step**. With no bridge installed, nothing is exposed.

What an app can see is **your own data only**. Never another player's records, never names,
never coordinates.

## Telemetry

NeroCreatures ships anonymous crash reporting via **Sentry** (EU ingest servers), matching the
rest of the Neroland ecosystem. It is **on by default and opt-out**:

- **Opt out:** set `telemetryEnabled=false` in `config/nerocreatures.properties` (takes effect
  on restart). This is a client-local setting — a server can never force it on or off.
- **NeroCreatures-only:** a report is sent only if its stack trace touches
  `za.co.neroland.nerocreatures`; everything else is dropped before it leaves the game.

> **Current status: nothing is sent.** This build ships a placeholder Sentry DSN, and the
> telemetry module is a hard no-op while that placeholder is in place — no network connection is
> opened at all, regardless of the config value. Reporting begins only in a build that carries a
> real DSN, at which point everything described here applies.

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
