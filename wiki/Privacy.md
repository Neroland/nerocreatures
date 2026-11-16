# Privacy

What NeroCreatures keeps about players, for how long, and how to see or remove it. This page is the
short version; the repository's `PRIVACY.md` is the full data inventory (every store, field, purpose,
legal-basis note and retention rule), written to help a server meet **POPIA** and the **GDPR**. The
server operator is the responsible party (POPIA) / controller (GDPR); the developers receive no player
data from the mod.

## What is stored

| Record | Where | What it holds | How long |
| --- | --- | --- | --- |
| Ownership index | the world save | your game UUID and, per creature you own, its id, kind (pet, mount, drone, egg), type, dimension and `parked` flag; an egg's hatch time | until you are erased |
| Boss contribution | the world save | per **summoned** fight: a random fight id, the boss, and each participant's damage | deleted when the fight ends |
| Owner on the creature | the creature itself | your UUID on a tamed pet or mount (vanilla's own field) or a deployed drone | until it is freed |
| Nest home | the creature itself | the Nest's dimension and block position | until un-homed or freed |
| Bestiary and milestone gates | Neroland Core's progression store | which species you scanned or studied; first tame, deploy and breed | Core's retention |
| Erasure markers | the world save | a salted one-way hash of an erased player (never the UUID) and the ids of their creatures that were not loaded | until each creature is freed; the hash for `max(dataRetentionDays, 365)` days |

No names, IP addresses, chat or position history are stored. The Apex Altars you lit and your
companion-app rate-limit times are kept **in memory only** and are lost on a restart.

## Erasure

One request removes you from every Nero mod at once:

- players: `/neroland data eraseme`
- operators: `/neroland data erase <uuid>`

For NeroCreatures this removes every ownership row, frees your loaded pets and mounts (a mount drops
its saddle and panniers), recalls your drones into their shells, clears vanilla's breeding cause that
names you, removes you from running boss fights and rewrites the crash-recovery backups. A creature in
an unloaded chunk is freed the next time it loads, through an erasure marker. Nothing is sent to you
while this happens. Run `/save-all` afterwards if you need the erase on disk before the next autosave.
See [Data storage](Data-Storage.md) for the details.

## Export

`/nerocreatures export <player>` (operator level 2) writes everything NeroCreatures holds about one
player, and nothing about anyone else, to a JSON file under the world folder. It works with a raw UUID
for a player who has left. See [Commands](Commands.md#nerocreatures-export-player).

## Retention

Retention follows Neroland Core's `dataRetentionDays` (default 0: erasure on request only). A player
counts as inactive only by Core's last-seen record; Core's purge-inactive sweep runs the full erasure.
Boss fight records never outlive their fight (an abandoned one goes six hours after its last hit).

## Companion app

With the NeroLink bridge installed and an app paired (pairing is your consent), the app sees **only
your own** Bestiary progress, pets, mounts, eggs, altar cooldowns and boss share. Never another
player's records or names, and never a coordinate. Pushes are off by default and name the species
only. See [Link module](Link-Module.md).

## Logs and crash reports

Logs at INFO and above carry counts and exception types, never a UUID, name, coordinate or free text.
Crash reporting is anonymous, NeroCreatures-only and opt-out (`telemetryEnabled=false`); every report
is scrubbed of names, UUIDs, world names and coordinates. See [Telemetry](Telemetry.md).

## See also

- [Data storage](Data-Storage.md)
- [Link module](Link-Module.md)
- [Telemetry](Telemetry.md)
- [Commands](Commands.md)
