# NeroCreatures — Privacy & Data Protection

NeroCreatures is built to help a server meet its obligations under **POPIA** (South Africa) and the
**GDPR** (EU/UK). This document lists every piece of player data the mod stores or sends, why, for how
long, and how a player or an operator inspects or removes it.

The server operator decides why and how the server processes player data, so under both laws the
operator is the responsible party (POPIA) / controller (GDPR). The "legal basis" notes below are
guidance for operators, not legal advice. The developers receive no player data from the mod (see
[Telemetry](#telemetry)).

## At a glance

- **Two player-keyed stores** in the world save: the creature **ownership index** and the summoned
  **boss contribution** record. Both are keyed by the player's Minecraft game UUID.
- **No names, IP addresses, chat or position history** are stored. The one coordinate the mod writes
  is a pet's **Nest** home, kept on the pet itself (see below).
- One **erase** request removes a player from every Nero mod at once, frees their creatures and
  refreshes the crash-recovery backups. One **export** command writes everything about one player, and
  nothing about anyone else, to a file. **Retention** follows Neroland Core's `dataRetentionDays` and
  its last-seen record of each player.
- Logs at INFO and above never carry a UUID, a name, a coordinate or free text: they carry counts and
  exception types.

## Data inventory

| # | Record | Where | Fields | Purpose | Legal basis (operator note) | Retention | Erasure | Export |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Ownership index | `data/nerocreatures_ownership.dat` (overworld) | player UUID; per creature: creature UUID, kind (`pet`, `mount`, `drone`, `egg`), entity-type id, dimension id, `parked` flag, and for an egg a random incubation id and its hatch game time; when the player's rows last changed; the player's lifetime hand-tame count, stored only up to 25 | enforce per-player caps; find a player's creatures for erasure and recall; answer access requests | providing the game feature the player chose (taming, riding, breeding); legitimate interests | until the player is erased, on request or by Core's inactivity purge | rows removed, creatures freed | `ownership` |
| 2 | Erasure markers | same file | a random per-world salt; a 128-bit salted SHA-256 hash of each erased owner's UUID, with the time it was written; the creature UUIDs of erased creatures that were not loaded, with the time | release a creature that was unloaded at erasure time when it next loads | complying with an erasure request (GDPR art. 6(1)(c) with art. 17; POPIA s 11(1)(c) with s 24) | creature marker: until that creature is freed; owner hash: `max(dataRetentionDays, 365)` days; the salt is deleted with the last owner hash | (they are the erasure record) | `erasure_marker`: yes or no |
| 3 | Boss contribution | `data/nerocreatures_boss_contribution.dat` | per **summoned** fight: random fight id, boss id, last-updated time, (player UUID, damage) pairs; one last-updated time per player | split a summoned fight's rewards by damage | providing the game feature; legitimate interests | deleted when the fight ends; an abandoned fight once it is 6 hours past its last hit (checked at start-up and daily, so up to about 30 hours); `dataRetentionDays` | the player's pairs removed; a fight nobody is left in is dropped | `boss_contribution` (own share only) |
| 4 | Last-known-good backups of 1–3 | `data/nerocreatures_ownership_backup.dat`, `data/nerocreatures_boss_contribution_backup.dat` | exactly the fields of the store they copy | restore a corrupt file instead of losing it | as the store | refreshed straight after every erasure and retention purge | same request | (same data as 1–3) |
| 5 | Owner on the creature | the creature's entity data, in the chunk | the owner's UUID on a tamed pet or mount (vanilla's own field for every tameable animal) and on a deployed Terraforming Drone | what "owned" means to the game | as row 1 | as row 1 (the creature is freed) | owner cleared / drone recalled | `loaded_creatures` |
| 6 | Nest home | an entity tag on the creature | the nest's dimension id and block position | send a homed pet back to its nest | as row 1 | until un-homed, freed or captured | cleared when the creature is freed | `loaded_creatures[].nest` |
| 7 | Bestiary and milestone gates | Neroland Core's progression store | per player: `nerocreatures:bestiary/scanned/<species>`, `nerocreatures:bestiary/studied/<species>`, `nerocreatures:first_tame`, `first_deploy`, `first_breed`, `tamed/10`, `tamed/25`, `bestiary/complete` | Bestiary tiers, the one-time Studied reward, quest hooks | providing the game feature | Core's `dataRetentionDays` sweep | Core's eraser (same request) | `progression_gates` |
| 8 | Data-access export files | `data/nerocreatures/exports/export-<time>-<random>.json` (world folder) | one player's export (see Access / export) | answer an access request | complying with an access request (GDPR art. 15; POPIA s 23) | 7 days, or `dataRetentionDays` if shorter (checked on every new export and daily) | every export file about the player is deleted | (it is the export) |

Nothing else NeroCreatures does is saved against a player. In particular:

- **Kept in memory only, never saved:** a Field Scanner scan in progress; Bestiary and client-request
  flood counters; gear charge and cooldown state; the Space Pirate's search for loot; boss-bar viewer
  lists. These are keyed by the live player object and vanish when the player leaves.
- **Kept in memory only, never saved, keyed by the player's game UUID:** the Apex Altars a player lit
  (dimension, block position, cooldown end, last boss summoned), so the companion app can show the
  cooldowns of the player's own altars by label number (never the position). Lost on a restart,
  dropped an hour after the altar is ready again or when it is broken, at most 8 per player, forgotten
  by an erasure request and listed by the export (`altars_lit`). And the time of a player's last
  companion-app recall and order, for the per-player rate limit; useless and pruned after its interval
  (at most 10 minutes), forgotten by an erasure request.
- **Read, not written:** vanilla's kill statistics (the Bestiary's "Defeated" counts), and vanilla's
  "who threw this" mark on a ground item (a tamed Magnet Crab refuses items its owner did not drop).
  Both belong to Minecraft and are not part of Neroland erasure.
- **Vanilla's breeding cause:** vanilla writes `LoveCause`, the UUID of the player who fed an animal into
  love mode, on every bred or hand-fed animal, wild ones included. NeroCreatures reads it (the online
  breeder gets Bestiary credit) and never writes it. An erasure request clears it on every **loaded**
  animal that names the erased player; an animal in an unloaded chunk keeps it until vanilla overwrites
  it at the next breeding.
- **Vanilla's own files:** recipe-book unlocks are ordinary vanilla advancements in
  `world/advancements/<uuid>.json`; per-species kill counts are in `world/stats/<uuid>.json`; a Capture
  Pod in a player's inventory, and a mount the player is riding at logout, are in
  `world/playerdata/<uuid>.dat`. Neroland erasure does not edit vanilla's per-player files: a full
  erasure also means deleting those three files for the player. **World backups and copies of region
  files are outside the mod's reach**; an operator who keeps them must handle them in their own
  erasure process.

### Things that are game data, not player data

- **Apex Altar** (block): its cooldown, the random id of the fight it started and the sigil used. The
  summoner is recorded only as a zero-damage row in the boss contribution store (row 3).
- **Eggs** (items): species and traits. No owner, no position. The Incubator holds the egg and the
  random incubation id that points at the egg row; that row is the only link to a player.
- **Capture Pod** (item): only the parts of the creature's saved data it needs to be the same animal
  (health, age, name, attributes, effects, equipment, variant, tags and this mod's own traits and
  growth), chosen by an allow-list: no creature UUID, owner, love cause, last attacker, anger target,
  brain memories, leash, mount state, loader or other mods' attachment data, Nest home or position. The
  same filter runs again when a pod is read, so a pod written by an older build cannot carry an owner
  either. A name the creature was given with a name tag travels with it, as it would on the creature.
- **Captain's Ledger** (item): the position of its pirate stash (vanilla's lodestone-tracker component),
  with no record of who killed the captain.
- **Space Pirate carried loot:** an item stack of `contraband` picked up from the ground. Only the
  stack is kept, never who dropped it; it drops when the pirate dies.
- **Crystal Golem anger** is not saved, so the provoking player's UUID is never written to the golem.
- **Gravity Leech tether** is a temporary attribute modifier that is never saved.

## Erasure

NeroCreatures registers with Neroland Core's shared per-player erasure hook. One request removes a
player from every Nero mod:

- players: `/neroland data eraseme`
- operators: `/neroland data erase <uuid>`

For NeroCreatures the request:

1. removes every ownership row for that UUID: pets, mounts, drones, eggs and parked pets;
2. follows each row to its creature and frees it if it is loaded. A **pet** goes wild (owner cleared,
   order reset, Nest home cleared, despawnable again). A **mount** is unsaddled (saddle, panniers and
   their contents drop where it stands) and goes wild. A **drone** is recalled into its shell. An
   **egg** row is simply dropped: the egg still hatches, owned by nobody;
3. sweeps every loaded level for any pet, mount or drone still carrying that UUID and frees it too, so
   erasure is complete even if the index and the world had drifted apart;
4. clears vanilla's `LoveCause` on every loaded animal that names the player;
5. removes the player from every summoned boss fight, and drops a fight nobody is left in;
6. refreshes both stores' last-known-good backups at once, so the erased rows do not survive in a
   backup copy. Marker expiry does the same;
7. deletes every data-access export file about the player (row 8), so an export made just before the
   erase does not outlive it.

Nothing on this path sends anything to the player being erased: no companion-app event, push or
alert is published or raised for them while their creatures are released, because the request has
already removed their alerts and anything sent now would re-create data about them.

Core's own eraser, in the same request, removes the Bestiary and milestone gates (row 7) and any
companion-app alerts. Erasure logs only how many creatures were released.

**When it reaches the disk.** The stores are marked changed at once and the crash-recovery backups are
rewritten at once, but the main data files are written at the game's next autosave (or a clean stop).
If the server crashes between the erase and that save, the old main file loads again and the erased
rows are back. Operators who need certainty should run `/save-all` after an erasure; Neroland Core has
been asked to save the affected stores itself at the end of every erase request.

### Creatures in unloaded chunks: erasure markers

A creature whose chunk is not loaded (or that a companion mod has parked in storage) cannot be reached
during the request, and its saved data still names the owner. So erasure leaves two kinds of
**erasure marker** in the ownership file:

- the **creature UUID** of each removed row that could not be freed;
- a **salted one-way hash** of the erased player's UUID, which also catches creatures the index never
  knew about (for example a pet from an older save).

When such a creature next loads it finds its marker and is released silently (a drone is recalled),
and nothing is sent to anyone. A creature is only ever released on a marker: an owner the index simply
does not know (an older save, a store restored empty) keeps their creature, which is re-indexed.

**Is a marker personal data?** We treat it as such. The hash cannot be reversed, but an operator who
already holds a list of player UUIDs (Minecraft's own `usercache.json`, for example) could hash each
one with the salt in the same file and test it. That makes the marker **pseudonymised** personal data
(GDPR recital 26; under POPIA, information that can be re-identified is not de-identified). It is kept
only to finish the erasure the player asked for, and is minimised:

- **Content:** a 128-bit truncation of SHA-256 over a random salt and the UUID. Never the UUID, never a
  name. Creature markers name only the creature.
- **Salt:** random per world, stored only in this one file, and **deleted** as soon as no owner marker
  needs it. A new salt is made for the next erasure.
- **Lifetime, creature markers:** kept until that creature is freed (it loads and is released), then
  deleted. A creature marker is the random UUID of a game object; it names no player and cannot be tied
  to one without reading the very region file where the creature itself sits beside its owner tag, so
  keeping it adds no identifying information. It is what finishes the erasure.
- **Lifetime, owner hash:** `max(dataRetentionDays, 365)` days, so a short retention period never makes
  the tombstone expire before the erasure it records is finished. Expired owner hashes are pruned when
  the store is first read each session and daily after that.
- **Size:** each marker set is capped at 65,536 entries. Reaching the cap is never silent: the server
  log gets a WARN with the number of markers dropped (a count only).
- **Use:** markers are never logged, never sent to the companion app, never put in telemetry, and an
  export reports only whether one exists for the player (yes or no). A player who returns and tames or
  deploys something again removes their own owner marker.

**The trade-off, stated plainly.** Every creature the ownership index ever held is released whenever it
next loads, however long that takes. The one remaining gap: a creature the index never held (tamed
under a version before 0.5.0 and not loaded since, or tamed while the index was being rebuilt), whose
owner was erased, and which stays unloaded for longer than the owner hash lives. That creature loads
still tamed and is re-indexed under the erased UUID. Keeping the owner hash forever would close it, at
the price of a re-identifiable record of every erased player for the life of the world; we chose not
to. A shared tombstone service in Neroland Core, used by every Nero mod, is the planned long-term
answer.

The eraser is registered when the mod starts, before either store is ever loaded, so a later store
cannot be added without being covered.

## Retention

Retention follows Neroland Core's `dataRetentionDays` (default `0`: no timed purge; erasure is on
request). NeroCreatures has no separate setting.

- **Who is inactive** has one definition across the Neroland mods: Core's record of when each player was
  last seen on the server. Core's purge-inactive sweep (`/neroland data purge-inactive`) runs the full
  erasure above for every player not seen for longer than `dataRetentionDays`: rows **and** creatures,
  so no owner UUID is left in a pet's entity data.
- NeroCreatures does **not** treat "has not tamed anything new lately" as inactivity: a player who logs
  in every day keeps every pet, mount and drone however long ago they were tamed.
- NeroCreatures expires its own short-lived and tombstone records the first time each store is read in
  a server session and once a day after that:
  - **boss contribution:** a player whose contribution in a still-open fight has not changed in
    `dataRetentionDays` days is removed (a fight record lives only for one fight);
  - **erasure markers:** owner hashes expire as described above; creature markers do not expire.
- **Unconditional limits:** a boss contribution row is deleted when its fight ends, and an abandoned
  fight is dropped once it is six hours past its last hit (checked at start-up and then daily), whatever
  the retention setting.
- **Bestiary and milestone gates** are Core's records and follow Core's retention.
- **Export files** are deleted 7 days after they are written, or after `dataRetentionDays` days when
  that is set and shorter (never under one day). The check runs whenever a new export is made and with
  the daily sweep, whatever the retention setting.

Sweeps log only how many records were removed, never whose.

## Access / export

`/nerocreatures export <player>` (operator level 2) writes, as JSON, everything NeroCreatures holds
about **one** player and nothing about anyone else, to a file under the world folder:
`data/nerocreatures/exports/export-<time>-<random>.json`. The file name names nobody. Where the file
system supports it the folder and file are readable by the server's own account only; on Windows they
inherit the world folder's access rules. The whole export is written, however long. The command
replies with the file's path and size only, so nothing personal appears in chat or, when run from the
console or RCON, in the server log. The operator gives the file to the player securely and deletes it
afterwards. If they do not, the mod deletes it after 7 days (or `dataRetentionDays` if shorter), and
erasing the player deletes every export about them at once.

| Section | Content |
| --- | --- |
| `player`, `exported_at`, `format` | the subject's UUID, when the export was made, the export format |
| `ownership` | their last-updated time and every ownership row: creature UUID, kind, type, dimension, `parked`, and an egg's `hatch_at`; their hand-tame count (`tames`, up to 25) when above zero |
| `boss_contribution` | their own damage in any summoned fight still running: fight id, boss id, damage. Never another participant's totals |
| `progression_gates` | this mod's gates open for them in Core's store (Bestiary tiers, first tame, deploy and breed, the 10/25 tame counts and the completed Bestiary) |
| `loaded_creatures` | creatures loaded right now whose own data names them as owner: creature UUID, type, kind, dimension, and its Nest home (dimension and block position) if it has one |
| `erasure_marker` | `true` if an earlier erasure left an owner marker for them |
| `altars_lit` | the Apex Altars they lit that the server still remembers in memory: label number, dimension, block position, cooldown end, last boss |

A player is named by their online name **or** by a raw UUID, so an access request works for somebody
who has left. The reply goes only to the operator who ran the command and is not broadcast to other
operators. An operator is required because an export is somebody's personal record; a player can also
see their own data, scoped to them automatically, in the companion app. Per-species kill counts are
vanilla statistics and are not in this export; they are in `world/stats/<uuid>.json`.

The same document is also part of Neroland Core's combined export (`/neroland data exportme` for a
player's own data, `/neroland data export <uuid>` for operators), under `mods.nerocreatures`. Core's path
builds it in memory and hands it over in chat: it writes no file and logs nothing.

## Companion app (link module)

NeroCreatures shows a player's **own** Bestiary progress, creatures and boss share in a Neroland
companion app through Neroland Core's link API. NeroCreatures opens no network connection of its own:
a separate bridge mod serves a paired app, and **pairing the app is the player's consent**. With no
bridge installed, nothing is exposed.

- **Only the asking player's data**, in every section and every schema version: the Bestiary
  (species facts and the player's own discovery tier and kill counts), the player's own pets, mounts,
  drones and egg timers (species name, traits, health, saddle, stamina, breeding timer, standing order),
  and boss fights (boss, phase, participant **count** and the player's **own** damage share, and
  cooldowns of the player's own altars). Never another player's records or names, and **never
  coordinates**: at most a dimension id.
- **Map layer.** Through Neroland Core's map API a map mod can show the asking player where their own
  loaded pets, mounts and drones are, read live from the creature on each request and never stored. It is
  the one surface that carries a position, and only of the asking player's own creatures. A creature is
  not shown while another player is riding it (or riding with it), because its position is then theirs.
- **The link module saves nothing new.** Every read goes to the stores above, Core's gates or vanilla's
  kill statistics, so an erased player immediately reads as owning nothing. Its only own state is the
  in-memory altar list and rate-limit times described above.
- **Actions are owner-scoped and rate-limited.** Recall and pet orders (follow, stay, sit) act only on
  the asking player's own pets and mounts, checked against the ownership index; somebody else's
  creature answers exactly like one that does not exist. Each player may recall once per
  `linkRecallCooldownSeconds` (10) and give one order per `linkCommandCooldownMillis` (1000).
- **Events.** Only four events are sent to a player, and only to that player's sessions: their
  creature died, is badly hurt, their egg hatched, or a summoned fight they joined ended (with their
  own share). Routine changes are not sent. Boss phase and defeat events go only to the players
  recorded in that summoned fight (the summoner included) and carry a boss, a phase and a dimension
  id, and nothing about any player; nobody outside the fight receives them, and the `bosses` section
  lists only fights the asking player is in.
- **Push notifications are off by default.** A player turns on the "Creatures" category in the app.
  A push names the **species only** (for example "Your Rime Yak is badly hurt."): never a custom name,
  a dimension, a position or another player.
- **Alerts** ("your pet died", "a summoned fight you joined ended") are raised through Core's alert
  store, name a creature or a boss only, and are erased by the same request as everything else.

The full contract is in [`wiki/Link-Module.md`](wiki/Link-Module.md).

## Neighbours, claims and requests from the client

- **Creatures never change another player's world for anyone.** Every ability that places, replaces or
  advances a block, moves a ground item or spawns a helper asks first: its own config key, the
  `mobGriefing` game rule (through the loader's mob-griefing event on NeoForge and Forge, which claim
  mods hook), the loader's block-place event (NeoForge and Forge), and, for an owned creature, whether
  its owner could do the same thing in person (online in that dimension, allowed to build there, not
  under spawn protection). With the owner offline, owned creatures change nothing. A refused check falls
  back to a harmless variant and logs nothing.
- **Fabric gap:** Fabric has no standard claim event. A claim mod that does not hook `mobGriefing` is
  not consulted for **wild** creatures on Fabric. Owned creatures are still limited to what their owner
  may do. Turn the ability keys off, or `mobGriefing` off, if this matters on your server.
- **What "the owner may do" means.** For an owned creature the check is the game's own build permission
  and spawn protection, on every loader. A claim mod that hooks neither the build permission nor
  `mobGriefing` is not consulted for owned creatures either. Each such change (a tamed Magnet Crab
  picking up loose items, a Lumen Moth's light, a Rime Yak's frost) is one the owner could make in
  person or is cosmetic.
- **Requests from the client** (for example "open my mount's panniers", "show my Bestiary") carry only a
  creature's session network id, an action number or a species id. The server checks that the sender
  owns the creature, is near it and is not flooding requests, and answers only with the sender's own
  data.
- **`/nerocreatures budget`** reports species and timings only, to the operator who ran it.
- **The gallery** (`/nerocreatures gallery`) stores nothing about any player. Its creatures are
  displays that are never tamed or owned, write no ownership row, statistic, gate or link event, and
  are invisible to the companion app. Two transient in-memory maps (the last demo plate a player
  stood on, and each exhibit's demo cooldown) hold a block position and a tick, are weakly keyed, and
  are gone when the player leaves or the server stops. Nothing about the gallery is logged or sent.

## Logs

Nothing NeroCreatures logs at INFO, WARN or ERROR carries a player UUID, a player or creature name, a
coordinate or free text: erasure, retention and recovery log counts; failures log the subsystem and the
exception type (its class name, never its message or stack trace). The repository's
`tools/check_log_privacy.py --check` checks every log call for this, including that no call passes a
whole exception, and runs in CI on every publish.

## Telemetry

NeroCreatures ships anonymous crash reporting via **Sentry** (EU ingest servers), matching the rest of
the Neroland ecosystem. It is **on by default and opt-out** (legitimate interest in fixing crashes;
reports are anonymous):

- **Opt out:** set `telemetryEnabled=false` in `config/nerocreatures.properties` (takes effect on
  restart). This is a client-local setting; a server can never force it on or off.
- **NeroCreatures only:** a report is sent only if its stack trace touches
  `za.co.neroland.nerocreatures`; everything else is dropped before it leaves the game.
- A build that carries no DSN (a fork, a stripped build) is a hard no-op whatever the config says.
- **Its own client:** NeroCreatures reports through a Sentry client of its own and never touches the
  shared global Sentry hub, so another mod's crash reporter can neither read its reports nor switch it
  off.

**A report contains:** the stack trace; NeroCreatures, Minecraft, loader, OS and Java versions; the ids
and versions of other installed mods; this mod's own config values; recent NeroCreatures actions
(breadcrumbs); anonymous stability and timing data.

**A report is not meant to contain** an IP address, username, player UUID, world name or seed,
coordinates, chat, or any ownership, contribution or Bestiary data, and every report is scrubbed of
them before it leaves the game. `sendDefaultPii` is off, the machine hostname is never attached and the
Sentry user object is cleared on every event. Exception messages and breadcrumbs are free text (the
game itself puts an entity's name, world and position into some of them), so each one passes a
scrubber that replaces UUIDs, entity and player names, world and save-folder names, coordinates and the
OS account name in file paths. A FATAL log line that mentions NeroCreatures without an exception is
never sent as text: the report carries a fixed message and the logger's name only. Events are
de-duplicated and capped at 10 per game session.

### CurseForge telemetry blurb

Keep the following (or equivalent) in the project description while telemetry is enabled, as
CurseForge moderation policy requires:

> **Telemetry notice:** NeroCreatures sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, pet ownership or world data) to the developers
> via Sentry (EU servers) so crashes can be fixed. On by default — opt out any time by setting
> `telemetryEnabled = false` in `config/nerocreatures.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerocreatures/blob/main/PRIVACY.md).
