# Link module (schema 2)

NeroCreatures can show your Bestiary, your creatures and the boss fights you are in to a **Neroland
companion app**, and let you call or order your own pets from it. It does that through **Neroland
Core's link API**: NeroCreatures registers what it can show and what it can do, and a separate bridge
mod serves that to your paired app over your own network.

NeroCreatures itself ships **no server, no HTTP, no accounts and no outbound connection**. It only
fills in a registry entry inside Core. With no bridge mod installed, the link module does nothing at
all.

## What it exposes

| Kind | Name | What it is |
| --- | --- | --- |
| Section | `bestiary` | Every creature with its habitat and kind, and **your** discovery tier, kill counts and study progress |
| Section | `pets` | **Your** pets, mounts, drones and incubating eggs, with health, traits, orders and timers |
| Section | `bosses` | Summoned boss fights **you** are in, with **your** share, and **your** altars' cooldowns |
| Action | `pet_recall` | Bring your own pets and mounts to you |
| Action | `pet_command` | Tell one of your own pets or mounts to follow, stay or sit |
| Event | `pet_state_changed` | One of your creatures died |
| Event | `creature_hurt` | One of your creatures is badly hurt |
| Event | `egg_hatched` | An egg you were incubating hatched |
| Event | `fight_ended` | A summoned boss fight you joined ended |
| Event | `boss_phase` | A boss fight you are in escalated |
| Event | `boss_defeated` | A boss fight you were in ended in the boss's defeat |
| Alert | your pet died | Raised for the owner |
| Alert | a boss you fought was defeated | Raised for each participant of a summoned fight |

Module id `nerocreatures`, **schema version 2** (NeroCreatures 0.5.0). Schema 2 only **adds**
members: every schema-1 member keeps its name and type, so a schema-1 reader still works. The one
narrowing is on purpose: a creature you have not scanned yet is sent as a silhouette (see below).

**Never in any section, action result or event:** a coordinate, a block position, another player's
name or id, or a list of players. At most a dimension id and a participant **count**.

## Section: `bestiary`

```json
{
  "schema_version": 2, "player_online": true, "total_kills": 12, "species_killed": 4,
  "spawns_enabled": true,
  "creatures": [
    { "id": "nerocreatures:frost_lurker", "habitat": "glacira", "kind": "hostile", "tier": 0 },
    { "id": "nerocreatures:lumen_moth", "habitat": "luna", "kind": "tameable", "tier": 2,
      "name": "Lumen Moth", "translation_key": "entity.nerocreatures.lumen_moth", "killed": 0,
      "spawns": [ { "biome_tag": "nerocreatures:habitat/luna", "weight": 10, "group_min": 1, "group_max": 2 } ],
      "ability": "A living lantern: …", "weakness": "Fragile and drawn to light: …",
      "also": "Also in the overworld: dark forests at night.",
      "drops": [ { "item": "nerocreatures:lumen_dust", "name": "Lumen Dust", "use": "…" } ],
      "study": { "kind": "tame_or_breed", "progress": 1, "goal": 1 }, "owned": 2 }
  ]
}
```

| Member | Since | When | Notes |
| --- | --- | --- | --- |
| `id`, `habitat`, `kind`, `tier` | 2 (`id` 1) | always | `tier`: 0 not scanned, 1 Scanned, 2 Studied — **yours** |
| `name`, `translation_key`, `spawns` | 1 | tier ≥ 1 | spawn rows are tags, never places |
| `killed` | 1 | tier ≥ 1, while you are online | vanilla's own kill statistic |
| `ability`, `drops` (≤ 8, each `item`, `name`, `use`) | 2 | tier ≥ 1 | English text, at most 220 characters |
| `weakness` | 2 | tier 2 | the Studied reward |
| `also` | 2 | tier ≥ 1 | the three species with an overworld spawn |
| `study` (`kind`, `progress`, `goal`) | 2 | tier ≥ 1 | a kill study is left out while you are offline, rather than shown as 0 |
| `owned` | 2 | tier ≥ 1, ownable species | how many you own now |
| `sigil` | 2 | tier ≥ 1, bosses | the sigil item's name |

`total_kills` and `species_killed` are present only while you are online. A **tier-0 entry carries
exactly `id`, `habitat`, `kind` and `tier`**, so an app cannot show what you have not discovered.

## Section: `pets`

```json
{
  "schema_version": 2, "player_online": true,
  "caps": { "pets": {"used": 2, "max": 3}, "mounts": {"used": 1, "max": 2},
            "drones": {"used": 0, "max": 2}, "eggs": {"used": 1, "max": 8} },
  "pets": [
    { "entity": "…", "kind": "mount", "type": "nerocreatures:dune_loper", "name": "Dune Loper",
      "dimension": "nerospace:mars", "status": "loaded", "commands": ["follow", "stay", "sit"],
      "can_recall": true, "health": 28.0, "max_health": 30.0, "command": "sit",
      "traits": ["stout", "calm"], "baby": false, "saddled": true, "stamina": 0.8,
      "breed_ready_in_s": 0 },
    { "entity": "…", "kind": "egg", "type": "nerocreatures:lumen_moth", "name": "Lumen Moth",
      "dimension": "nerospace:luna", "status": "loaded", "commands": [], "can_recall": false,
      "hatch_in_s": 250 }
  ]
}
```

| Member | Since | When |
| --- | --- | --- |
| `entity`, `kind`, `type`, `name`, `dimension`, `status` | 1 | always; `kind` is `pet`, `drone`, and from schema 2 `mount` and `egg` |
| `health`, `max_health`, `command` | 1 | the creature is loaded |
| `commands`, `can_recall` | 2 | always: pets and mounts take `follow`, `stay`, `sit` and can be recalled; drones and eggs take nothing |
| `traits` (≤ 2), `baby` | 2 | loaded |
| `saddled`, `stamina` (0–1) | 2 | a loaded mount |
| `breed_ready_in_s` | 2 | a loaded adult of a breedable species; 0 = can breed now |
| `hatch_in_s` | 2 | an egg whose Incubator is loaded (unpowered rate, the worst case) |

`name` is the **species** name, never a name tag. `command` is one of `sit`, `stay`, `guard` or
`follow` (`guard` is the in-game "Guard" order; the app shows it with Stay). `entity` is the
creature's game id, which the app sends back in actions and never shows. Status is read from the
world's entity index without loading a chunk; a parked pet reads as `unloaded`.

## Section: `bosses`

```json
{
  "schema_version": 2, "player_online": true,
  "fights": [
    { "fight": "…", "boss": "nerocreatures:rime_matriarch", "name": "Rime Matriarch",
      "dimension": "nerospace:glacira", "phase": 2, "phase_count": 3, "health": 0.42,
      "your_damage": 120.5, "your_share": 0.3, "participants": 4 }
  ],
  "altars": [
    { "index": 1, "state": "cooldown", "ready_in_s": 412, "last_boss": "nerocreatures:rime_matriarch" },
    { "index": 2, "state": "ready" }
  ]
}
```

- `fights` (schema 1) lists the summoned fights in progress that **you** are in: you summoned it or
  hit the boss. Other players' fights are never listed, so the app never shows where somebody else is
  fighting. `your_damage` and `your_share` are **yours**; `participants` is a count, never a list.
- `altars` (schema 2) are the Apex Altars **you** lit. An altar has no name and no position here,
  only `index`, a label number that stays the same while the altar is remembered. `state` is
  `ready`, `cooldown` (with `ready_in_s`) or `fighting` (your fight there is still going).
  The list is kept in server memory only (see [Privacy](#privacy)): it is empty after a restart, and
  an altar is forgotten an hour after it is ready again or as soon as it is broken.

## Action: `pet_recall`

Parameters: `{"pet": "<entity>"}` for one creature, or `{}` for all your pets and mounts. Only
creatures in **your** dimension, loaded, still yours, and not ridden, perched or leashed are moved; each is placed on solid
ground within 3 blocks of you, or skipped. Result:

```json
{ "schema_version": 2, "recalled": 1, "pets": [ { "entity": "…", "type": "…", "name": "…", "command": "follow" } ],
  "skipped": [ { "entity": "…", "type": "…", "reason": "other_dimension" } ] }
```

Drones are never recalled: a drone belongs to the area it works.

## Action: `pet_command`

Parameters: `{"pet": "<entity>", "command": "follow" | "stay" | "sit"}`. Result:
`{"schema_version": 2, "entity": "…", "command": "stay"}`. Any other command, including `guard`,
upper case or a missing one, is refused with `unknown_command`; it never falls back to Follow.

| Order | In game |
| --- | --- |
| `follow` | Follows you. |
| `stay` | Stands exactly where it is: no following, no wandering and no fighting, not even back. |
| `sit` | Sits down where it is. |

The order applies to a loaded pet or mount wherever it is. Drones and eggs take no orders.

## Validation and refusals

Both actions are checked on the server, in this order:

1. **Rate limit, per player:** one recall per `linkRecallCooldownSeconds` (default 10) and one order
   per `linkCommandCooldownMillis` (default 1000), on top of the bridge's per-device limit.
2. **Parameters:** a malformed `pet` id is refused, never widened into "recall everything".
3. **Online:** you must be in the game.
4. **Ownership:** the creature must be one of **your** rows in the ownership index. Somebody else's
   creature answers exactly like one that does not exist.

Refusals carry a token as the message, never a sentence:

| Token | Error | Meaning |
| --- | --- | --- |
| `rate_limited` | `VALIDATION` | too soon after your last call |
| `bad_request` | `VALIDATION` | missing or malformed parameters |
| `unknown_command` | `VALIDATION` | not `follow`, `stay` or `sit`, or the creature takes no orders |
| `not_owner` | `NOT_OWNER` | not one of your creatures (or no such creature) |
| `no_pets` | `VALIDATION` | you have no pets or mounts to recall |
| `not_loaded`, `other_dimension`, `no_room` | `VALIDATION` | nothing could be recalled or ordered right now |
| `player_offline` | `PLAYER_OFFLINE_REQUIRED` | you are not online |
| `internal` | `INTERNAL` | something failed on the server |

## Events

| Topic | Scope | Payload | Push |
| --- | --- | --- | --- |
| `pet_state_changed` | yours | `entity`, `kind`, `type`, `name`, `dimension`, `state` (`died`), `timestamp`, `_notify` | "Your Dune Loper died." |
| `creature_hurt` | yours | `entity`, `kind`, `type`, `timestamp`, `_notify` | "Your Dune Loper is badly hurt." |
| `egg_hatched` | yours | `type`, `kind` (`egg`), `timestamp`, `_notify` | "A Lumen Moth egg has hatched." |
| `fight_ended` | yours | `type` (boss), `kind` (`boss`), `outcome` (`defeated` or `ended`), `share` (yours), `timestamp`, `_notify` | "The Rime Matriarch fight has ended." |
| `boss_phase` | the fight's participants | `boss`, `name`, `dimension`, `phase`, `phase_count`, `summoned`, `timestamp` | never |
| `boss_defeated` | the fight's participants | as above, plus `participants` | never |

- **Push notifications are off by default.** The bridge sends a push for an event only when you have
  turned on the **Creatures** category in the app (wire category `nerocreatures`) and the app is not
  open. The `_notify` copy is `title` "NeroCreatures", a `body` that names the **species only** —
  never a name tag, a dimension, a position or another player — and a `deepLink` to the module, never
  to one creature.
- **Badly hurt** means its health just dropped below a quarter, and its own owner is not the one
  hurting it. At most one per creature per five minutes.
- **Fight ended** is sent to each player who joined a summoned fight (the summoner included), when the
  boss is defeated or leaves the world unbeaten, with that player's own share only.
- **Routine changes are not events any more.** Schema 1 also published `pet_state_changed` for
  `tamed`, `released`, `deployed` and `recalled`; with the bridge, every player event can become a
  push, so schema 2 publishes only the death. The app polls `pets` for the rest. Orders, traits,
  timers, Bestiary discoveries and altar cooldowns are never events.
- Broadcasts reach every session, so they carry a boss, a phase and a dimension id and nothing about
  any player, the same rule Core's `ThresholdEvents` contract imposes on the
  [`nerocreatures:boss_pressure` channel](Bosses.md).

Each player event is published **before** the matching alert, so the bridge's species-only push
replaces the alert's generic one.

## Alerts

| Alert | Severity | Raised for | Text |
| --- | --- | --- | --- |
| Your pet died | `WARN` | The owner | "Your Glacite Wisp did not survive." |
| A boss you fought was defeated | `INFO` | Each recorded participant of a **summoned** fight | "The Cinder Tyrant you fought has been defeated." |

Alert text names a creature or a boss and **nothing else**. Alerts are stored by Core under your
UUID and are purged by the same shared erasure request that clears everything else.

## Privacy

*(See also [`PRIVACY.md`](https://github.com/Neroland/nerocreatures/blob/main/PRIVACY.md) and [Data storage](Data-Storage.md).)*

- **Your own data only**, in every section and action: your discovery tiers, kill counts, creatures,
  share and altars. Nothing about any other player.
- **No coordinates.** No section carries a position; the altars you lit are shown by label number.
- **Two small things are kept in server memory, never saved:** the altars you lit (to show their
  cooldowns; dropped on restart, an hour after the altar is ready, when it is broken, or by an
  erasure request, and listed in `/nerocreatures export`), and the time of your last recall and order
  (for the rate limit; useless after a few seconds). Everything else is read from stores that already
  exist, so an erased player immediately reads as owning nothing.
- **Consent lives with the bridge.** Whether an app may connect at all is governed by the bridge's
  pairing; NeroCreatures only answers questions the bridge is already authorised to ask.

## Notes for server admins

- The link module is server-side only and starts with the world. Nothing is exposed until a bridge
  mod is installed and an app has been paired with it.
- The only writes are recall and the three orders, on the asking player's own pets and mounts. There
  is no way to spawn, tame, summon or configure anything through the link.
- Tune the rate limits with `linkRecallCooldownSeconds` and `linkCommandCooldownMillis` (see
  [Config](Config.md)).
- If the link module fails to register, it is logged as a warning and the mod carries on.

## See also

- [Pets & Drones](Pets-and-Drones.md) — the orders, and what the `pets` section is describing
- [Field Scanner and Bestiary](Field-Scanner-and-Bestiary.md) — discovery tiers
- [Bosses](Bosses.md) — summoned fights and the Apex Altar
- [Data storage](Data-Storage.md) — the stores behind all of this, and how to erase them
