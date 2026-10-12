# NeroQuests integration

NeroCreatures ships a quest chapter inside its own jar. When **NeroQuests** is installed the chapter
appears in the quest book; without it the files are inert data and nothing changes. There is **no
dependency in either direction** — NeroQuests reads the pack, NeroCreatures never references NeroQuests.

## The "Bestiary" chapter

Chapter `nerocreatures:bestiary`, 14 quests. Every creature spawns in Nerospace biomes, so the whole
chapter stays hidden until Neroland Core's `nerolandcore:reached_orbit` gate is open for you.

| Quest | What it asks | Follows |
| ----- | ------------ | ------- |
| Pale Hunters | 4 Lunar Stalkers, 2 stalker sinew | — |
| Crystal Clarity | a Crystal Golem, 4 refined crystal | Pale Hunters |
| Jelly Logic | 6 Plasma Slimes, 2 plasma cells | Pale Hunters |
| New Friend | tame your first pet (`first_tame` gate) | Crystal Clarity |
| First Contact | a Void Crawler, void essence | — |
| Scrap Rights | 3 Rogue Drones, 4 salvaged circuitry | First Contact |
| Shield Breaker | a Rogue Android, its android core | Scrap Rights |
| Scrapyard | 10 kills on the `nerocreatures:mechanical` tag | Shield Breaker |
| Second Hands | craft a drone shell | Shield Breaker, Jelly Logic |
| Drone Deployed | deploy your first drone (`first_deploy` gate) | Second Hands |
| Boarding Party | 5 Space Pirates, contraband | — |
| Ember Crown | defeat the Cinder Tyrant, keep the Apex Trophy | Shield Breaker |
| Cindara Falls Quiet | *server-wide*: the first Tyrant defeat on `nerospace:cindara` | — |
| Field Guide | complete Crystal Clarity, Second Hands, Boarding Party and Ember Crown | all four |

"Cindara Falls Quiet" is a world-log quest: it listens to the `nerocreatures:boss_pressure` channel
([Bosses](Bosses.md)) for a falling crossing on the Cindara dimension and completes once for the whole
server. A crossing never names a player, which is why personal milestones use gates instead.

The Asteroid Worm has no quest yet: it has no natural spawn, so a quest naming it could never be
finished.

## Milestone gates

Two player-scope Neroland Core gates (`data/nerocreatures/neroland_gates/`) are opened by the mod the
first time something notable happens:

| Gate | Opens when |
| ---- | ---------- |
| `nerocreatures:first_tame` | you tame a Glacite Wisp or Xertz Forager |
| `nerocreatures:first_deploy` | you deploy a Terraforming Drone from a drone shell |

They are **records, not locks**: they have no requirements, NeroCreatures never checks them for
access to anything, and a quest reads them with a read-only `gate_open` objective. The rows live in
Core's progression store, covered by Core's privacy statement and removed by the shared erasure hook
([Pets & Drones](Pets-and-Drones.md), [`../PRIVACY.md`](../PRIVACY.md)).

## Family tags

`kill_entity` objectives accept an entity-type tag, so the roster is grouped into
`nerocreatures:mechanical`, `crystalline`, `void` and `stalkers` — see [Bestiary](Bestiary.md).

## Writing your own quests against NeroCreatures

A server or modpack pack can use everything above: the entity ids and drops in the
[Bestiary](Bestiary.md), the family tags, the two gates, and the `boss_pressure` channel with a
dimension id as `event_scope`. Packs record progress; nothing in a pack can open a NeroCreatures gate.

## See also

- [Bosses](Bosses.md) — the `nerocreatures:boss_pressure` channel contract
- [Pets & Drones](Pets-and-Drones.md) — taming, the drone and what is stored
- [Data storage](Data-Storage.md) — erasure, retention and export
