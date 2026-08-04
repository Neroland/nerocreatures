# Changelog

All notable changes to **NeroCreatures** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

**Neroland Core dependency (1.10.0)**

- NeroCreatures now builds and runs against **Neroland Core 1.10.0**, its only hard dependency.
  Every loader manifest declares it as required with `ordering = "AFTER"`, and the version floor
  is the compiled Core version so an outdated Core is refused by the loader instead of failing
  later with a missing method.
- Core supplies the registration seam, the shared config framework, the shared creative tab, the
  per-player data-erasure hook, the `neroland:space/*` biome and dimension vocabulary, and the
  cross-loader entity registration seam (default attributes + spawn placements).

**Configuration**

- New `config/nerocreatures.properties`, hot-reloadable with `/neroland config reload`:
  `spawnsEnabled`, `globalSpawnWeightMultiplier`, `dropRateMultiplier`, `maxCreaturesPerChunk`,
  `maxCreaturesPerDimension`, `hostileAggressionMultiplier`, `bossDifficultyMultiplier`,
  `bossHpMultiplier`, `maxPetsPerPlayer`, `maxDronesPerPlayer`, `naturalBossSpawnsEnabled` — all
  server-authoritative — plus the client-local `telemetryEnabled` opt-out.

**Entity framework**

- `NeroCreatureEntity`, the shared base for every creature: tier-driven attributes, config-scaled
  aggression applied once at spawn, sane despawn rules, and automatic population-cap accounting.
- A reusable AI goal library designed to stay cheap under load: `BlinkStrikeGoal` (teleport-close
  ambush), `BurrowGoal` + `EmergeAttackGoal` (submerged and untargetable, with a telegraphed
  surfacing), `PackCoordination` (event-driven target sharing, flank offsets, night boldness),
  `SplitOnDamageBehaviour` (size-tiered splitting, budgeted against the population caps) and
  `EnergyAuraGoal` (periodic, victim-capped area damage).

**Spawn engine**

- Creature spawns are declared once in code as `SpawnRule` entries against Core's space biome
  tags and placed by a server-side sweep that mirrors vanilla's spawner (player-anchored ring,
  vanilla placement checks, difficulty and light rules), rather than through three
  loader-specific biome-modifier systems.
- Population caps are enforced at placement time, per chunk and per dimension.
- **Earth stays quiet by design:** with no planet mod installed, Core's space tags are empty,
  every rule matches nowhere and nothing spawns.

**Hostile fauna — the first four creatures**

- **Void Crawler** — a low-light ambusher that blinks onto its target instead of chasing it.
  Spawns in `neroland:space/dark_biomes`; drops Void Essence.
- **Lunar Stalker** — a pack hunter that shares targets on sighting, flanks instead of queueing,
  and grows bolder after dark. In a biome tagged `nerospace:gravity_low` it takes reduced gravity
  and a raised safe-fall distance — a tag lookup only, so NeroCreatures still declares no
  dependency on Nerospace. Spawns in `neroland:space/moon_biomes`; drops Stalker Hide and Sinew.
- **Asteroid Worm** — an armoured elite that fights from underground: invulnerable and untargetable
  while burrowed, with a one-second telegraph before every surfacing strike, and a hard limit on
  how long it may stay under. Its body is segmented in the model only; on the server it is a single
  entity with one long hitbox. Spawns in `neroland:space/asteroid_biomes`; drops Worm Chitin and
  Ore Slurry.
- **Plasma Slime** — a charged blob with a slow area aura that splits into smaller, quicker slimes
  when killed, budgeted against the population caps. Spawns in
  `neroland:space/crystalline_biomes` and, more rarely, `dark_biomes`; drops Plasma Cell.
- Each creature ships default attributes and spawn placements through Core's entity seam, a
  hand-authored loot table, a spawn egg (in `neroland:highlight/tools`), lang entries and a
  programmer-art model, renderer and 64×64 texture. All four are documented in
  [`wiki/Bestiary.md`](wiki/Bestiary.md).
- All creature sounds are mapped to existing vanilla sound events; NeroCreatures ships no audio.

**Neutrals & humanoids — four more creatures**

- **Crystal Golem** — the first neutral: it never starts a fight, but a hit provokes it (and its
  neighbours) for 20–40 seconds, iron-golem style. Heavily armoured, knockback-resistant and slow
  enough to walk away from. A walking resource node: it always drops Refined Crystal, and killing it
  with a `#minecraft:pickaxes` item drops more. Spawns alone in
  `neroland:space/crystalline_biomes`. Its anger is deliberately **not** persisted — vanilla's
  anger save data writes the offending player's UUID into the entity, and this mod keeps no player
  identity on a mob.
- **Space Pirate** — a humanoid raider whose fight is decided by its **kit**, not its species: one
  entity type, four loadouts (recruit/raider × blade/crossbow) rolled at spawn and applied as real
  vanilla equipment, so the weapon's damage counts and the worn pieces can drop. Crossbow carriers
  fight at range; the rest close. Natural bands are recruits, spawning 2–3 at a time in
  `neroland:space/planet_biomes`. New public helper `PirateSpawner.spawnBand(...)` places a themed
  band at a chosen kit tier — the seam a future NeroEvents raid will use.
- **Rogue Drone** — a small machine that hops and glides (a vanilla leap goal plus a permanently
  reduced gravity attribute) rather than flying. Drops Salvaged Circuitry.
- **Rogue Android** — the heavy frame, built around a **hit-counting shield**: four absorbed hits,
  then a shatter and a three-second stagger window at half speed and 1.5× damage, then a recharge.
  `/kill` and the void bypass the shield, so nothing here is unkillable. Drops Salvaged Circuitry
  and, at 35%, an Android Core.
- **Salvage nod:** finish an android during its stagger window with non-fire damage and it yields
  one extra Salvaged Circuitry — burning a frame destroys the boards. The full salvage system stays
  deferred.
- Both androids are mechanical: poison, wither and hunger do not apply to them; fire does. Natural
  android spawns are deliberately the rarest in the table — the pair is a ruins creature, and the
  new `AndroidSpawner` helper is the structure-friendly placement seam NeroRuins will use.
- New server-authoritative config key **`pirateVillagerAggression`** (default **false**): whether
  pirates also attack villagers and colony NPCs. The target set is the datapack tag
  `nerocreatures:pirate_raid_targets` (vanilla villagers and wandering traders ship as optional
  members), so a colony mod can enroll its own NPCs without NeroCreatures referencing it.
- `dropRateMultiplier` now has observable effect: it scales the golem's pickaxe bonus and the
  android's salvage bonus. Both are documented in [`wiki/Drop-Map.md`](wiki/Drop-Map.md), including
  why they are code rather than loot-table conditions (the entity loot context has no `tool`
  parameter, so `minecraft:match_tool` can never match for a mob).
- Each of the four ships attributes and placements through Core's entity seam, a hand-authored loot
  table, a spawn egg, lang entries and a programmer-art model, renderer and 64×64 texture, and is
  documented in [`wiki/Bestiary.md`](wiki/Bestiary.md). All ten creature drops are now obtainable in
  survival.

**Tameables — alien pets, and the first ownable machine**

- **Glacite Wisp** — a drifting knot of ice shards from the frozen worlds, tamed with **Stalker
  Sinew**. Perk: while its owner is falling within eight blocks it lends them Slow Falling. Spawns
  in `neroland:space/moon_biomes` and, more rarely, `crystalline_biomes`. Drops nothing.
- **Xertz Forager** — a low quartz-crested grazer from the lush worlds, tamed with a **Plasma
  Cell**. Perk: in light level 7 or below its crest lights up and lends its owner Night Vision.
  Spawns in `neroland:space/crystalline_biomes`. Drops nothing.
- Both are harmless: no target goal at all, so a wild one never starts a fight, and both perks are
  deliberately comfort-tier — **no perk touches damage, armour, health or speed**, so pets are never
  a combat advantage.
- **Taming and commands.** Feed a wild pet its reagent (a one-in-three roll, vanilla-wolf odds).
  Sneak-interact your own pet to cycle its standing order — **sit → stay → follow**; "stay" is a
  third state vanilla has no equivalent for, and holds a chosen area rather than freezing the animal.
  Feeding an injured pet heals it. Anyone who is not the owner gets no interaction at all.
- **The pet cap is enforced at tame time**, server-side, against `maxPetsPerPlayer` (default 3). A
  player at their cap is told so on the action bar and **loses no reagent** — the check runs before
  the roll. Setting the key to 0 disables taming outright.
- **Terraforming Drone** — the mod's first ownable utility mob, and the only creature that **never
  spawns naturally**: no spawn rule, no spawn placement, no spawn egg. It exists only where a player
  deploys the new crafted **Terraforming Drone Shell** (`salvaged_circuitry` ×3 + `plasma_cell` ×2 +
  `android_core` + iron), which binds it to them in the same action. `maxDronesPerPlayer` (default 2)
  is checked at deploy time, and nothing is consumed if you are at your limit.
- Deployed drones work a bounded area around where they landed: every five seconds one
  bone-meal-equivalent growth tick on a random valid block within eight blocks, consuming nothing.
  They stay in that area, never attack anything, and are persistent. **Sneak-interact with an empty
  hand returns the shell** and folds the drone away; killing one also drops its shell, so deploying
  is never a one-way spend. Life-support assist is a documented, deliberate no-op hook — the real
  integration will arrive as a Neroland Core event, not as a dependency on a planet mod.
- The drone has **no energy buffer**, by decision: Core's energy framework is block-entity shaped and
  there is no cross-loader seam for a capability on a mob, so the time-based duty cycle *is* the
  power model. Documented in [`wiki/Pets-and-Drones.md`](wiki/Pets-and-Drones.md).
- New reusable AI goal `HoldAreaGoal` — keeps a mob inside a circle around a movable anchor, used by
  both the pets' "stay" order and the drone's work area, with the usual documented cost profile.
- Population accounting now counts **two class roots**: the pets derive from vanilla `TamableAnimal`
  rather than the mod's own base (that is where vanilla puts owner storage and the four owner-aware
  goals), so `CreatureCensus` counts both and a wild pet is inside the spawn caps like everything
  else.

**Boss framework, and the first planet boss**

- New reusable boss framework: `boss/BossController` (the multi-phase state machine), `boss/BossPhase`
  (a phase description — health threshold, display title, goal factory, one-off entry action),
  `boss/BossGoals` (the narrow seam through which the controller swaps a boss's goal set, because
  `Mob.goalSelector` is protected) and `entity/boss/NeroBossEntity` (the entity base that wires all of
  it into spawn, tick, damage, death, removal and save/load). A new boss declares its phases and its
  numbers and inherits everything else.
- The controller owns: a `ServerBossEvent` **boss bar** showing health and the current phase name;
  **arena awareness** (an anchor and a radius captured on arrival — pulled outside it a boss drops its
  target, walks home and regenerates fast, and dragged twice as far it is simply put back at full
  health); **phase transitions that only ever advance**; and config scaling applied once, at spawn, to
  attribute base values so a reload cannot compound it.
- **Cinder Tyrant** — the first signature boss, themed off the real character of Nerospace's Cindara
  biome (temperature 2.0, no precipitation, ash-black foliage, a member of
  `neroland:space/dark_biomes`): a fire-immune Apex-tier slag construct with a 32-block arena and three
  phases — **Slag and Stone** (heavy melee plus a telegraphed ground slam), **Ashfall** at 66% health
  (fireball volleys plus Plasma Slime reinforcements, capped at four alive and budgeted against the
  population caps) and **Meltdown** at 33% (a permanent enrage: 35% faster, 30% harder, both attacks on
  roughly half the cooldown). A boss **never despawns**.
- Three new reusable AI goals with the usual documented cost profiles: `GroundSlamGoal` (telegraphed
  area shockwave, bounded victim count, no pathfinding), `FireVolleyGoal` (vanilla `SmallFireball`
  bursts, cached line-of-sight rather than fresh ray casts) and `SummonAddsGoal` (waves bounded four
  ways — per wave, by concurrency, by the population caps and by a cooldown).
- **Two ways a boss turns up, and they pay differently.** A boss found in the world drops its loot
  table and records nothing about anybody. A boss started through the new public
  `BossSummons.summon(level, pos, bossId, initiator)` API — the seam NeroEvents will use — runs
  **contribution tracking** and pays every participant an enhanced share on top of the loot table.
- **The contribution reward rule** (documented in full in [`wiki/Bosses.md`](wiki/Bosses.md)): every
  recorded participant gets a base share of 1 Apex Trophy + 2 Refined Crystal + 2 Plasma Cell,
  including the summoner at zero damage; a damage share **below 5%** gets the base share and nothing
  more; at or above 5% a participant also gets `round(share × 12)` Refined Crystal and
  `round(share × 8)` Plasma Cell; at or above 25% they get a second trophy. All of it is a *bonus
  roll*, so `dropRateMultiplier` scales it and `0` switches it off while leaving the loot table alone.
- **Natural boss spawns are gated far harder than a weight.** `boss/BossSpawns` requires all of
  `naturalBossSpawnsEnabled`, a one-Minecraft-day per-dimension cooldown (restarted both when a boss
  appears and when one dies) and "no boss already alive in this dimension" — the last of those a
  bounded, cached query. `spawn/SpawnRule` gained an optional per-level `gate` for it, checked before
  the biome lookup because it is the cheaper question.
- **New drop: Apex Trophy** (`nerocreatures:apex_trophy`) — the boss-kill token and the ecosystem's
  top-tier reagent, dropped by *every* planet boss so a downstream recipe can ask for "a boss kill"
  without naming a world. Stacks to 8. Tagged `neroland:materials/apex_trophy` +
  `neroland:highlight/materials` and added to Core's shared creative tab; the Drop Map is updated.
- **`nerocreatures:boss_pressure`** — every phase transition and defeat is published on Core's
  `ThresholdEvents` bus, so NeroEvents (or anything depending only on Core) can react without importing
  NeroCreatures. **Scope is the dimension id — a place, never a player**, per Core's contract. Entering
  phase *n* fires `value = threshold = n, rising = true`; a defeat fires `value = 0`,
  `threshold = phase count`, `rising = false`, and is the only crossing on this channel with
  `rising == false`.
- New **boss contribution store** `nerocreatures:boss_contribution` — a codec-based `SavedData` behind
  the same `SavedDataRecovery` guard as the ownership index, holding only (fight id, boss id,
  timestamp) and (player UUID, damage) pairs. **No names, no coordinates, no per-hit history.** The row
  is deleted the moment a fight resolves or its boss leaves the world, so in normal play the store is
  empty; an abandoned fight is pruned after six hours regardless. Wired into the shared erasure hook,
  retention-stamped against Core's `dataRetentionDays`, and exportable per player.
  [`PRIVACY.md`](PRIVACY.md) is updated to declare it.
- Boss spawn egg (`nerocreatures:cinder_tyrant_spawn_egg`, an operator convenience — an egg-placed boss
  is a *natural* fight and pays no contribution share), hand-authored loot table, lang entries, and a
  programmer-art model, renderer and 64×64 texture in Cindara's basalt-and-ember palette.
- New wiki page [`wiki/Bosses.md`](wiki/Bosses.md): the fight guide, both entry modes, the exact reward
  rule, what is stored about a player and for how long, the threshold-channel contract for other mods,
  and a contributor's guide to building the next boss on the framework.

**Ownership storage, erasure and retention**

- New server-side store `nerocreatures:ownership` — a codec-based `SavedData` index of who owns which
  creature. Per creature it holds the *creature's* entity UUID, whether it is a pet or a drone, its
  entity-type id and the id of the dimension it was last registered in, plus one "last updated"
  timestamp per player. **No names and no coordinates of any kind**; the dimension id exists purely
  so an erase request knows which level to look in.
- Every saved-data read routes through a new `SavedDataRecovery` guard, ported from Nerospace by way
  of NeroQuests: a corrupt or unreadable file degrades to an empty index and a clean file at the next
  save instead of crashing the server repeatedly.
- **The Stage-1 erasure hook is now wired to a real store.** `/neroland data eraseme` (or an admin
  erase) removes the player's rows *and* frees the creatures: pets are returned to the wild (owner
  cleared, order reset, despawnable again) and drones are recalled, dropping their shells. It then
  sweeps every loaded level for anything still carrying that UUID, so the erase is complete even if
  the index and the world had drifted apart. Nothing on that path logs who was erased — only a count.
- **Retention** honours Core's `dataRetentionDays`: the first time the store is read in a server
  session, any player untouched for longer than that is erased in full — row *and* creatures — so a
  pruned record leaves nothing behind. Only the number pruned is logged.
- **Access:** a per-player JSON export returns exactly one player's own ownership rows and nobody
  else's.
- [`PRIVACY.md`](PRIVACY.md) is updated to describe what is actually stored, including the fact that
  a tamed pet carries its owner's UUID in its own entity data — vanilla's design for every tameable
  animal — and that erasure covers it.

**NeroCompanion hand-off**

- New `compat/CompanionBridge` + `compat/CompanionPetHooks`: the published boundary between the two
  mods, and the answer to the long-standing open question. **NeroCreatures owns** taming, ownership
  and caps, the sit/stay/follow cycle, owner-only interaction, defending its owner, the species perk,
  loot and rendering; **everything deeper** — personality, moods, levelling, named abilities, pet
  inventories — is NeroCompanion's.
- The seam is a service interface NeroCreatures declares and a companion mod implements, discovered
  through `ServiceLoader`. **There is no reflection into another mod anywhere**, no companion mod
  appears in any manifest, and with none installed the lookup finds nothing and every hook is a
  no-op. A companion mod may declare that it is driving a pet's idle behaviour, in which case
  NeroCreatures stands down from the species perk and from nothing else.
- Documented for players in [`wiki/Pets-and-Drones.md`](wiki/Pets-and-Drones.md).

**Client rendering**

- New cross-loader client seam `client/ClientEntityRenderers` — the renderer list is declared once
  and each loader supplies its own registration sink (NeoForge/Forge
  `EntityRenderersEvent.RegisterRenderers`, Fabric `EntityRendererRegistry`). Models are baked
  directly, so no model-layer registry is needed on any loader. Client wiring is gated on the
  client dist, so a dedicated server never loads a rendering class.

**Creature drops**

- Ten drop items with programmer-art textures: Void Essence, Stalker Hide, Stalker Sinew,
  Refined Crystal, Worm Chitin, Ore Slurry, Plasma Cell, Contraband, Salvaged Circuitry and
  Android Core. All join Neroland Core's shared creative tab.
- Tags: `neroland:materials/<drop>` for every drop, `neroland:highlight/materials` for the
  coloured slot borders, and `c:gems/refined_crystal` / `c:dusts/ore_slurry` for cross-mod
  interop.
- The canonical creature → drop → tag → consumer mapping is published at
  [`wiki/Drop-Map.md`](wiki/Drop-Map.md).

**Privacy & telemetry**

- [`PRIVACY.md`](PRIVACY.md) documents what is stored, erasure, retention, the companion-app
  boundary and telemetry.
- Opt-out Sentry crash reporting is **live**: NeroCreatures-originated crashes only, no personal
  data, EU ingest, and `telemetryEnabled=false` switches it off entirely (a client-local setting a
  server cannot override). A build carrying no DSN remains a hard no-op.
- NeroCreatures' player-data erasure hook is registered with Core at mod construction, ahead of
  the stores it will purge.

**Companion-app link module**

- New `link/CreatureLinkModule` — NeroCreatures' plug into Neroland Core's link API, registered
  **last** during common init so a companion client is never told about something before the mod has
  finished reacting to it. A failure to register is logged and swallowed: creatures work perfectly
  well with no link module. Module id `nerocreatures`, **schema version 1**. NeroCreatures itself
  ships no server, no HTTP and no outbound connection — a separate bridge mod serves the registry.
- **Three snapshot sections**, every one of them scoped to the requesting player:
  - `bestiary` — the whole roster with each creature's spawn-table lines, plus **that player's own**
    kill counts, read from **vanilla's own statistics** rather than a NeroCreatures store. That is
    deliberate: a bestiary with its own store would be a third player-keyed record to erase, retain
    and declare, in exchange for a number the game already keeps. Kill counts need the player to be
    online, and `player_online` says so rather than reporting zeroes.
  - `pets` — that player's own ownership rows with live status (loaded/unloaded, health, standing
    order) where the creature happens to be loaded, plus both caps. One entity-index lookup per row;
    no chunk is loaded to answer it.
  - `bosses` — the summoned fights in progress, each with the asking player's **own** damage and
    share. There is no participant list: `participants` is a count, and a share is a fraction of an
    aggregate.
- **One action, `pet_recall`** — brings your own tamed pets to you, placed on a valid block by a
  bounded search so a recall never puts an animal in a wall or over a drop, with the standing order
  re-applied so a guarding pet guards its new spot. Owner-scoped (somebody else's pet answers exactly
  like a creature that does not exist), online-only permanently, and **drones are deliberately never
  recalled** — a drone belongs to the area it was deployed to work.
- **Three live events** — `pet_state_changed` (tamed / released / deployed / recalled / died, routed
  to the owner's sessions alone) and the broadcast `boss_phase` / `boss_defeated`. A broadcast reaches
  every session, so its payload carries a boss, a phase and a **dimension** and nothing player-shaped
  at all — the same rule Core's threshold-event contract imposes on `nerocreatures:boss_pressure`.
- **Two Core alerts** — "your pet died" (owner only) and "a boss you fought was defeated" (each
  recorded participant of a *summoned* fight; a natural fight records nobody and alerts nobody).
  Alert text names a creature or a boss and never who killed it or who else was there.
- New `boss/BossFights`, the live index of contribution-tracked fights, so "which summoned fights are
  running" is one map read rather than a sweep over every loaded entity in every dimension. A fight is
  adopted by both routes that can produce one (a fresh summon and a saved boss being read back in) and
  dropped the moment its boss leaves the world.
- Documented in full in the new [`wiki/Link-Module.md`](wiki/Link-Module.md).

**Commands**

- New `/nerocreatures` operator tree, registered identically on all three loaders and gated at
  permission level 2 like `/neroland` and `/neroquests`:
  - `list` — the registered roster with how many of each creature are loaded in your dimension and
    the spawn-table lines that place them (two bounded queries, tallied by type);
  - `caps` — the population and ownership caps and what is using them here;
  - `summon-boss <boss>` — starts a contribution-tracked fight where you stand, through the same
    public `BossSummons.summon` API a future NeroEvents raid will use, with you as the fight's
    initiator. Refused rather than routed around when a cap is full;
  - `export <player>` — **the documented POPIA/GDPR data-access path**: one player's own ownership
    rows and their own boss contribution as JSON, and nobody else's. Takes an online name *or* a raw
    UUID, because an access request has to work for someone who has left;
  - `telemetry-test` — fires one synthetic crash-reporting event, and answers honestly when nothing
    was sent (opted out, or a build carrying no DSN).
- All output goes to the invoker alone and stays out of `latest.log` under `logAdminCommands`. No
  subcommand prints anything about a player other than the one named.
- Documented in the new [`wiki/Commands.md`](wiki/Commands.md).

**Gallery & screenshot capture**

- New `/nerocreatures gallery` (and `gallery clear`) — a creative-only showcase scene built where you
  stand: the whole registry-driven roster twice over (a frozen row for clean shots, a live row inside
  a sealed glass vivarium), an ember boss arena with the Cinder Tyrant, both pet species and the
  Terraforming Drone on themed patches, and every drop, the drone shell and every spawn egg floating
  against a wall. Gated three ways — permission level 2, player-only, creative-only — because it
  rewrites a box roughly 100 × 80 × 16 blocks. `command/CreatureGallery`.
- **The gallery writes no player data**, and that is enforced by construction, not by care:
  - the boss is a **display spawn** created without the spawn-finalisation step that starts a fight,
    so there is no boss bar, no `nerocreatures:boss_pressure` crossing, no companion broadcast and no
    dimension-wide boss cooldown — and, since contribution is only ever switched on by a deliberate
    summon, no `BossContributionState` row can exist for it;
  - the pets are shown **untamed** and the drone **unowned**, so no `CreatureOwnershipState` row is
    written and nobody's pet or drone cap is spent;
  - the live row is contained by glass (which breaks line of sight) rather than by relying on
    Peaceful difficulty, so a live hostile can neither reach nor shoot the photographer.
- New client-side `/ncgallery capture [time]` and `/ncgallery capture shot <name>` — an automated,
  reproducible screenshot pass over that scene: teleport to a fixed origin, rebuild from scratch,
  freeze time/weather/clouds, hide every overlay, then fly a framed camera through eleven shots and
  write a PNG each. `client/GalleryCaptureHarness`, wired from all three loaders' client setups and
  client-tick hooks, and dist-gated so it never loads on a dedicated server.
- The client root is `/ncgallery`, deliberately **not** `/nerocreatures`: a client-dispatcher root
  that shares its name with a server command shadows the server subtree.
- Output lands in `screenshots/nerocreatures/`, which is the folder the repository's Modrinth gallery
  sync already reads from the NeoForge 26.2 client run directory.
- Documented in the new [`wiki/Gallery.md`](wiki/Gallery.md).

**Invasion seam for event mods**

- New public `spawn/InvasionBudget` — how a future NeroEvents raid puts a **wave** of creatures into
  a world without breaking the population caps or leaving its mobs behind. Nothing in NeroCreatures
  calls it; it ships now so the mod that needs it does not have to reach around the caps to get it.
- **The budget is an allowance, not a bypass.** A wave may push a dimension temporarily above
  `maxCreaturesPerDimension` by at most its reserved budget — but `maxCreaturesPerChunk` is never
  exceeded, and a cap of `0` still means zero.
- **Two cleanup mechanisms**, because one cannot survive a restart and the other cannot be exact: the
  handle remembers every entity it spawned (exact, works for any mob, in-memory), and every
  NeroCreatures mob in a wave additionally carries the wave id in its **own saved data**, so
  `InvasionBudget.sweep` can clear up a wave whose handle is long gone. Wave mobs are `discard`ed
  rather than killed, so an event ending does not shower the arena in unearned loot.
- A wave id identifies a batch of mobs; nothing about a player is derivable from it.
- `NeroCreatureEntity` gained the persistent `WaveId` marker that makes the restart-safe sweep
  possible, and `ModEntities.roster()` now publishes the creature list so nothing that walks the whole
  roster can silently miss a creature.

**Documentation**

- Five new wiki pages: [Spawning](wiki/Spawning.md) (the space biome tags, the full weight table, the
  placement sweep, the caps, every config key and the `InvasionBudget` contract),
  [Commands](wiki/Commands.md), [Link module](wiki/Link-Module.md),
  [Data storage](wiki/Data-Storage.md) (the two stores, erasure, retention and export in practical
  terms) and [Gallery](wiki/Gallery.md), plus [Telemetry](wiki/Telemetry.md). `wiki/Home.md` indexes
  all ten.

**Networking**

- `nerocreatures:main` payload channel with the ecosystem's declare-once / register-per-loader
  split. No payloads yet: the mod is server-authoritative and the client currently only renders.
