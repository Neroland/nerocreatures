# Link module (companion app)

NeroCreatures can show your bestiary, your pets and the boss fight you are in to a **Neroland
companion app**. It does that through **Neroland Core's link API**: NeroCreatures registers what it
can show and what it can do, and a separate bridge mod serves that to your paired app over your own
network.

NeroCreatures itself ships **no server, no HTTP, no accounts and no outbound connection**. It only
fills in a registry entry inside Core. With no bridge mod installed, the link module does nothing at
all.

## What it exposes

| Kind | Name | What it is |
| --- | --- | --- |
| Section | `bestiary` | The whole creature roster, with **your** kill count for each |
| Section | `pets` | **Your** tamed pets and deployed drones, with their caps |
| Section | `bosses` | Summoned boss fights in progress, with **your** contribution |
| Action | `pet_recall` | Bring your own pets to you |
| Event | `pet_state_changed` | One of your creatures was tamed, released, deployed, recalled or died |
| Event | `boss_phase` | A boss fight escalated (broadcast) |
| Event | `boss_defeated` | A boss fight ended in the boss's defeat (broadcast) |
| Alert | your pet died | Raised for the owner |
| Alert | a boss you fought was defeated | Raised for each participant of a summoned fight |

Module id `nerocreatures`, **schema version 1**. The schema version is bumped whenever the shape of
a section changes, so an app can tell what it is parsing.

## Section: `bestiary`

```json
{
  "schema_version": 1,
  "player_online": true,
  "total_kills": 41,
  "species_killed": 5,
  "spawns_enabled": true,
  "creatures": [
    {
      "id": "nerocreatures:void_crawler",
      "name": "Void Crawler",
      "translation_key": "entity.nerocreatures.void_crawler",
      "killed": 12,
      "spawns": [
        { "biome_tag": "neroland:space/dark_biomes", "weight": 30,
          "group_min": 1, "group_max": 2 }
      ]
    }
  ]
}
```

Kill counts come from **vanilla's own statistics** — the same `minecraft:killed` numbers on your
statistics screen — not from a NeroCreatures store. That is deliberate: a bestiary with its own store
would be a third player-keyed record to erase, retain and declare, in exchange for a number the game
already keeps.

The one consequence is that kill counts need you to be **online**, because vanilla's stats counter
is loaded with you. While you are offline the roster and its spawn data still come back — those are
facts about the world — and `player_online` is `false`, so an app can say why the numbers are missing
rather than showing zeroes as though you had never killed anything.

A creature with an empty `spawns` array never spawns naturally (the Terraforming Drone).

## Section: `pets`

```json
{
  "schema_version": 1,
  "player_online": true,
  "caps": { "pets": { "used": 2, "max": 3 }, "drones": { "used": 1, "max": 2 } },
  "pets": [
    { "entity": "…", "kind": "pet", "type": "nerocreatures:glacite_wisp",
      "name": "Glacite Wisp", "dimension": "nerospace:glacira",
      "status": "loaded", "health": 8.0, "max_health": 12.0, "command": "follow" },
    { "entity": "…", "kind": "drone", "type": "nerocreatures:terraforming_drone",
      "name": "Terraforming Drone", "dimension": "nerospace:greenxertz",
      "status": "unloaded" }
  ]
}
```

Your own rows and nobody else's. `status` is `loaded` when the creature happens to be in a loaded
chunk — answered with a single lookup in the level's entity index, so an unloaded pet simply reads
as `unloaded` rather than being dragged into memory to be described.

**There are no coordinates**, here or in the store behind it. The `dimension` is present because
that is what the ownership index holds (so an erase request knows which level to look in); *where*
your pets are is not something NeroCreatures records. See
[Pets & Drones](Pets-and-Drones.md#what-is-stored-and-how-to-get-rid-of-it).

## Section: `bosses`

```json
{
  "schema_version": 1,
  "player_online": true,
  "fights": [
    { "fight": "…", "boss": "nerocreatures:cinder_tyrant", "name": "Cinder Tyrant",
      "dimension": "nerospace:cindara", "phase": 2, "phase_count": 3, "health": 0.41,
      "your_damage": 184.5, "your_share": 0.37, "participants": 3 }
  ]
}
```

Only **summoned** fights appear, because only summoned fights exist as fights: a boss you find in
the world is a mob, not an event, and records nothing about anybody
([Bosses](Bosses.md#how-a-boss-turns-up)).

`your_damage` and `your_share` are yours alone, and read `0` for a fight you have not touched. There
is no participant list — `participants` is a **count**, so an app can say "you and two others"
without knowing who they are. `your_share` is your damage over the fight's total, which is an
aggregate of the fight rather than a fact about any identifiable other player.

Any section name other than these three returns an empty object.

## Action: `pet_recall`

```json
{}                                       // recall all of your pets
{ "pet": "<creature UUID>" }             // recall one
```

Brings your own tamed pets to where you are standing. Each pet is placed on a valid block near you —
solid ground, open air, room to stand, found by a small bounded search — so a recall never puts an
animal inside a wall or over a drop. A pet's standing order is preserved and re-applied, so a
**guarding** pet guards its new spot rather than walking back to the old one.

```json
{ "schema_version": 1, "recalled": 1,
  "pets": [ { "entity": "…", "type": "nerocreatures:glacite_wisp",
              "name": "Glacite Wisp", "command": "follow" } ],
  "skipped": [ { "entity": "…", "type": "…", "reason": "other_dimension" } ] }
```

`reason` is one of `other_dimension`, `not_loaded` or `no_room`.

**Drones are never recalled by this action**, and that is not an oversight: a drone's whole purpose
is the area it was deployed to work, and teleporting it to you would quietly break it. Folding a
drone away stays a deliberate in-world act (sneak-interact with an empty hand).

Refusals use Core's shared action error codes:

| Code | When |
| --- | --- |
| `PLAYER_OFFLINE_REQUIRED` | You are not online (see below) |
| `NOT_OWNER` | The `pet` id is not one of your own creatures |
| `VALIDATION` | Malformed `pet` id, you have no pets, or none could be recalled |
| `INTERNAL` | No world is running, or something went wrong server-side |

A pet that belongs to somebody else answers **exactly like a creature that does not exist**, so the
action cannot be used to probe for other players' pets.

`pet_recall` is **online-only, permanently**. A recall moves an animal to where its owner is
standing, and an offline player is not standing anywhere; there is no sensible offline semantics to
add later.

### Why only one action

Everything else an app might want to do to a creature — taming, commanding, deploying a drone,
summoning a boss — is an *in-world* act with an in-world cost and an in-world audience. Doing any of
those from a phone would let a player affect the world without being in it. Recalling a pet you
already own to a place you are already standing changes nothing that was not already yours, which is
what makes it the one safe write.

## Events

| Topic | Scope | Payload |
| --- | --- | --- |
| `pet_state_changed` | Your session | `entity`, `kind`, `type`, `name`, `dimension`, `state`, `timestamp` |
| `boss_phase` | Broadcast | `boss`, `name`, `dimension`, `phase`, `phase_count`, `summoned`, `timestamp` |
| `boss_defeated` | Broadcast | as above, plus `participants` |

`state` is one of `tamed`, `released`, `deployed`, `recalled` or `died`.

**Broadcasts carry no player data at all.** A boss event reaches every session, so it says which
boss, how far the fight got and where — a *place*, not a person. There is no participant list, no
killer and no position, which is the same rule Core's `ThresholdEvents` contract imposes on the
[`nerocreatures:boss_pressure` channel](Bosses.md).

## Alerts

Two, and deliberately only two. A Core alert survives until it is acknowledged, so it is reserved
for things worth telling you about while the game is closed.

| Alert | Severity | Raised for | Text |
| --- | --- | --- | --- |
| Your pet died | `WARN` | The owner | "Your Glacite Wisp did not survive." |
| A boss you fought was defeated | `INFO` | Each recorded participant of a **summoned** fight | "The Cinder Tyrant you fought has been defeated." |

Alert text names a creature or a boss and **nothing else** — never who killed it, never who else was
in the fight. A natural boss records no participants, so it alerts nobody.

Alerts are stored by Core under your UUID and are purged by the same shared erasure request that
clears everything else.

## Privacy

*(See also [`../PRIVACY.md`](../PRIVACY.md) and [Data storage](Data-Storage.md).)*

- **Your own data only.** Every section is scoped to your player id before it leaves the mod: your
  kill counts, your ownership rows, your contribution. No other player's rows, shares, names, UUIDs
  or positions appear in a snapshot, an action result, an event payload or an alert.
- **No coordinates.** The mod stores none, so the link cannot leak any.
- **The link adds no stored data.** Every read goes to a store that already exists, which is also
  why erasure needs no extra wiring: a player erased through Core's shared hook immediately reads as
  owning nothing and having contributed nothing.
- **Consent lives with the bridge.** Whether an app may connect at all, and which of your data it
  receives, is governed by the bridge mod's pairing — NeroCreatures only answers questions the
  bridge is already authorised to ask.

## Notes for server admins

- The link module is server-side only and starts with the world. Nothing is exposed until a bridge
  mod is installed and an app has been paired with it.
- Creature data reaches an app read-only apart from the single `pet_recall` action. There is no way
  to spawn, tame, summon or configure anything through the link — those stay operator commands (see
  [Commands](Commands.md)).
- If the link module fails to register for any reason, it is logged as a warning and the mod carries
  on. Creatures do not depend on it.

## See also

- [Pets & Drones](Pets-and-Drones.md) — what the `pets` section is describing
- [Bosses](Bosses.md) — the two entry modes, and the threshold channel other **mods** listen to
- [Data storage](Data-Storage.md) — the stores behind all of this, and how to erase them
- [Commands](Commands.md) — the operator-side equivalents
