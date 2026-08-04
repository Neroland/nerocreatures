# Telemetry

NeroCreatures can report **its own crashes** to the developer, so bugs get fixed without anybody
having to notice, reproduce and file them. This page says exactly what that means, what it does not
mean, and how to turn it off.

> **It is on by default and you can turn it off.** Crash reporting is live from 0.1.0 onward. It
> reports NeroCreatures' own crashes and nothing about you — see [Turning it off](#turning-it-off).

## What it is

Opt-out crash reporting via [Sentry](https://sentry.io/), the same wiring every other Nero mod uses.
When it is active, it sends:

- **Unhandled exceptions that originate in NeroCreatures code.** The filter is by package: a crash
  in another mod, or in Minecraft itself, is not sent. If NeroCreatures is not in the stack trace, it
  is not our bug and it is not our business.
- **Handled failures** the mod chose to report anyway — a command that threw, a link surface that
  failed — so an error that was survived is still visible as a defect.
- A small amount of **timing data** on a 5% sample (the spawn sweep, boss phase transitions), to
  catch performance regressions.

## What it never sends

- **No player names, UUIDs, IP addresses, chat or coordinates.** `sendDefaultPii` is off and the
  server's hostname is never attached.
- **No world data.** Not your seed, your save name or your dimension list.
- **No file paths from your machine** — your OS account name is scrubbed out of any path before an
  event leaves.
- **No session linking.** The session id is random per launch and is not tied to anything across
  launches, so two crashes cannot be connected to the same person.

What *does* travel with a report is public, non-personal context: the mod version, the Minecraft
version, the loader, whether it is a client or a dedicated server, whether it is a development run,
a handful of **server configuration** values (`spawnsEnabled` and friends) — because a crash that
only reproduces at non-default settings is otherwise impossible to chase — and **the ids and
versions of your other installed mods**, capped at 300, because most hard crashes in a modded game
are conflicts and the mod list is the first thing that makes one diagnosable. Those are public
manifest strings, identifying the mods and not you.

## Turning it off

In `config/nerocreatures.properties`:

```properties
telemetryEnabled=false
```

This is the **one setting in NeroCreatures that is not server-authoritative**. Every other key is
decided by the server; this one is a personal choice and is read from your own config, on your own
machine, so a server cannot switch your crash reporting back on.

Set it before launching. When telemetry is off, Sentry is never initialised at all — there is no
"collected but not sent" state.

## For the developer

- The DSN lives in `telemetry/NeroCreaturesTelemetry` as `DSN` — a public, write-only ingest key,
  safe to ship in the jar. `PLACEHOLDER_DSN` remains as a guard: if `DSN` is ever set back to it (a
  fork, a stripped build), **`init()` returns immediately** and nothing is started. Do not remove
  that guard.
- `/nerocreatures telemetry-test` fires one synthetic event to confirm end-to-end reporting on a
  real jar (see [Commands](Commands.md)). It reports honestly when nothing was sent — opted out, or
  an unconfigured build. Repeat calls in one session collapse into one event; restart to test again.
- Development and IDE runs report under a dedicated `development` environment, so they never mix
  with real releases.

## Why it is opt-out rather than opt-in

Because the alternative is not "more privacy", it is "no crash reports". A mod that crashes for 2% of
players on one loader will never hear about it from an opt-in reporter, and those players simply
stop playing. The trade is only defensible if the reporter is genuinely PII-free and genuinely easy
to switch off — which is what the two lists above are for.

None of this is player data under POPIA or GDPR, because none of it identifies a person. The things
that *are* player data live in the world save and are covered by [Data storage](Data-Storage.md) and
[`PRIVACY.md`](../PRIVACY.md).

## See also

- [Data storage](Data-Storage.md) — the player data that actually is stored, and how to erase it
- [`PRIVACY.md`](../PRIVACY.md) — the formal statement
- [Commands](Commands.md) — `telemetry-test`
