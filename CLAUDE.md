# Project context for AI coding agents — nerocreatures

> This and `CLAUDE.md` are kept identical; update both together.

## The mod

- **NeroCreatures** — the creature layer of the Neroland sci-fi Minecraft mod ecosystem, built on
  **Neroland Core**. Hostile fauna, neutral resource creatures, humanoid raiders, tameable
  companions and planet bosses; its drops are raw materials the rest of the ecosystem spends.
- Mod id: **`nerocreatures`** (matches the registry namespace + every loader manifest). Package
  root: `za.co.neroland.nerocreatures`. Author: **Neroland**.
- Version: **0.0.1-alpha.1**.
- Targets **MC 26.1.2 AND 26.2** on **NeoForge, MinecraftForge/Forge, and Fabric** → the **"6 cells"**.
  **Java 25.** Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).

## Dependencies

- **Neroland Core is the ONLY hard dependency** (`nerolandcore_version` in `gradle.properties`,
  currently `1.10.0`). Declared in all three manifests as required with `ordering = "AFTER"`, with
  the version range floored at the compiled Core version.
- **No other Nero mod may appear in any manifest.** Cross-mod integration is soft: tag entries use
  `"required": false` and runtime checks go through `isModLoaded(...)`. Everything must degrade
  gracefully when a sibling mod is absent.
- What NeroCreatures consumes from Core: `RegistrationProvider`, `CoreCreativeTab`, the config
  framework (`ConfigSchema`/`ConfigManager`), `data.PlayerDataErasure`, `worldgen.SpaceTags`
  (the `neroland:space/*` biome + dimension vocabulary) and `entity.EntityRegistrationSupport`
  (cross-loader default attributes + spawn placements).

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out. Any new
  player-keyed store must be reachable from the `data/CreatureData` erasure hook.
- **NEVER commit or push automatically.** Leave changes **staged**; the developer reviews and commits
  with native git (the source of truth).
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>`
  (`:fabric:26.1.2 :fabric:26.2 :neoforge:26.1.2 :neoforge:26.2 :forge:26.1.2 :forge:26.2`). `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions=26.1.2,26.2`.

## Code map (`common/src/main/java/za/co/neroland/nerocreatures/`)

- `NeroCreaturesCommon` — the single init entry point. **Ordering matters**: config → telemetry →
  entity types → items → spawn eggs → creative tab → entity attributes/placements → data/erasure →
  companion hooks → network → spawn rules → boss registry → **link module (last)**. Fabric registers
  eagerly, so anything that must exist first has to be listed first on every loader — in particular
  the entity types precede the spawn eggs, the spawn table and the boss registry that name them, and
  Core's entity seam is only called once the types exist. The link module goes last so a companion
  client is never told about something before the mod has finished reacting to it. ServiceLoader
  seams (platform, companion hooks) are resolved here rather than lazily mid-tick.
- `config/NeroCreaturesConfig` — Core-backed schema (`config/nerocreatures.properties`). Everything
  gameplay-related is server-authoritative; `telemetryEnabled` deliberately is not.
- `platform/{Services,PlatformInfo,NetworkPlatform}` — ServiceLoader seams, one impl per loader plus a
  `META-INF/services` entry. NeroCreatures needs its own `PlatformInfo` because Core's helper reports
  *Core's* version.
- `network/CreatureNetwork` — declare-once/register-per-loader payloads on the `nerocreatures:main`
  channel. **Never** add payloads to Core's `CoreNetwork`: Forge seals that channel inside Core's
  constructor. The payload list is legitimately empty while the mod is server-authoritative only.
- `telemetry/` — opt-out Sentry wiring, ported from the sibling mods. Ships a **live DSN** (public
  write-only ingest key). The `PLACEHOLDER_DSN` guard stays: any build whose `DSN` is the
  placeholder is a hard no-op. Do not remove it.
- `data/` — `CreatureData` registers the Core `PlayerDataErasure` lambda at construction, ahead of
  the stores it purges, and owns what erasure and retention actually *do*: purge the row, then free
  the creature (pets go wild, drones are recalled), then sweep loaded levels for stragglers, then
  clear the player out of every live boss fight. `CreatureOwnershipState` is the codec-based
  `SavedData` index (`nerocreatures:ownership`) — `OwnedCreature` rows carry entity UUID + kind +
  type id + dimension id and a per-player `updated_at`, and **never coordinates**.
  `SavedDataRecovery` is the ported Nerospace guard: **every** `SavedData` accessor in this mod goes
  through it, never `getDataStorage().computeIfAbsent` directly. Retention honours Core's
  `dataRetentionDays`. Nothing on any of these paths may log a player UUID. **There are exactly two
  player-keyed stores** — this one and `boss/BossContributionState`; adding a third means wiring it
  into `CreatureData` and declaring it in `PRIVACY.md` in the same change.
- `entity/base/` — `NeroCreatureEntity` (shared base: tier attributes, config scaling, despawn rules,
  and the persistent `WaveId` marker `spawn/InvasionBudget` sweeps on) and `CreatureTier`. Every
  creature must extend the base, or it escapes the population caps.
- `entity/hostile/` — the shipped roster: `VoidCrawler` (blink ambusher), `LunarStalker` (pack +
  flank + night boldness; low gravity via a **tag-id-only** lookup of `nerospace:gravity_low`),
  `AsteroidWorm` (owns the `Burrower` contract — burrowed means `hurtServer` refuses damage,
  `canBeSeenAsEnemy` is false and `isInvisible` is true; **one entity, one long hitbox** — the
  segments are a client-model illusion), `PlasmaSlime` (splits on death; size drives health, damage
  and the vanilla `minecraft:scale` attribute, which resizes hitbox and model together so no custom
  render state is needed).
- `entity/neutral/CrystalGolem` — the roster's `NeutralMob`: retaliates and stays angry, with the
  anger timer held in memory only (vanilla's anger save data writes the offending player's UUID —
  this mod keeps no player identity on an entity). Its pickaxe bonus lives in
  `dropCustomDeathLoot`, not in the loot table, because the **entity** loot context has no `tool`
  parameter, so `minecraft:match_tool` can never match for a mob.
- `entity/humanoid/` — `SpacePirate` (one entity type; melee vs ranged is the **kit**, not the
  species — goals are registered in the constructor and the kit is rolled in `finalizeSpawn`, so the
  goals self-gate on it), `PirateLoadout` (the kit table + tiers) and `PirateSpawner` (public: place
  a themed band, for NeroEvents later). Villager/colony aggression is off by default and targets the
  datapack tag `nerocreatures:pirate_raid_targets`, never a hard-coded class.
- `entity/mechanical/` — `AbstractAndroid` (poison/wither/hunger do not apply; fire does),
  `RogueDrone` (hop + glide = vanilla `LeapAtTargetGoal` plus a reduced `minecraft:gravity`
  attribute, no custom movement code), `RogueAndroid` (hit-counting shield → break → stagger window
  → recharge, all server-side with sound/particle feedback so nothing needs syncing),
  `AndroidSpawner` (public: structure-friendly placement, for NeroRuins later) and
  `TerraformingDrone` — the ownable utility mob: `OwnableEntity` on top of `NeroCreatureEntity`, no
  spawn rule/placement/egg (a `drone_shell` is the only way to make one), a **time-based duty
  cycle** rather than a Core `EnergyBuffer` (Core's energy surface is block-entity shaped and there
  is no cross-loader mob-capability seam — see its Javadoc), and a documented no-op oxygen hook that
  must stay reflection- and dependency-free.
- `entity/tame/` — the tameables. `TameableCreature` extends **vanilla `TamableAnimal`**, not
  `NeroCreatureEntity`: owner storage and the four owner-aware goals only exist on that branch. It
  borrows the tier attributes by calling `NeroCreatureEntity.createCreatureAttributes` directly, and
  `spawn/CreatureCensus` counts it as a **second class root** so pets stay inside the population
  caps. `PetCommand` is the sit → stay → follow cycle (server-side state; only vanilla's already
  synced sitting flag reaches the client). `GlaciteWisp` and `XertzForager` are the two species —
  **their perks are comfort-tier by rule: never damage, armour, health or speed.** The pet cap is
  enforced at **tame time**, before the roll, so a refusal costs the player nothing.
- `entity/boss/` — `NeroBossEntity` (the boss entity base: wires a `BossController` into
  `finalizeSpawn`/`customServerAiStep`/`hurtServer`/`die`/`remove`/save-load, implements
  `boss/BossGoals`, and is **always persistent** — a boss never despawns) and `CinderTyrant`, the
  first planet boss (fire-immune, three phases, Cindara-themed).
- `boss/` — the reusable boss framework, kept out of `entity/` because none of it is an entity.
  `BossController` is the phase machine + `ServerBossEvent` bar + arena leash + config scaling +
  contribution hook + `ThresholdEvents` publisher; `BossPhase` is a *description* (threshold, title,
  goal **factory**, one-off entry action) and holds no state, so goals must be built fresh on every
  entry; `BossGoals` exists only because `Mob.goalSelector` is protected. `BossSummons` is the
  public summon API (the NeroEvents seam) and **the only thing that turns contribution on** —
  `BossContributionState` (`nerocreatures:boss_contribution`) records damage for summoned fights
  only, deletes the row the moment a fight resolves, and is erasure-wired and retention-stamped like
  the ownership store. `BossRewards` owns the split rule (5% participation floor, 25% major share) —
  **`wiki/Bosses.md` is where that rule is written down for players; change both together.**
  `BossSpawns` is the natural-spawn gate: config flag, per-dimension cooldown, one-boss-alive.
  `BossFights` is the live index of contribution-tracked fights (fight id → controller), fed by
  `BossController`'s single `bindFight` path so a *reloaded* boss reappears in it, and cleared from
  `CreatureSpawns.reset()` when the engine sees a new server.
- `entity/ai/` — the reusable goal library: `BlinkStrikeGoal`, `BurrowGoal`/`EmergeAttackGoal`
  (+ the `Burrower` contract), `PackCoordination`, `SplitOnDamageBehaviour`, `EnergyAuraGoal`,
  `HoldAreaGoal` (keeps a mob in a circle around a movable anchor — the pets' "stay" order and the
  drone's work area), `GroundSlamGoal`, `FireVolleyGoal` and `SummonAddsGoal` (the boss set; the
  last is budgeted against the population caps exactly as slime splitting is). **Every goal
  documents its cost profile in its Javadoc — keep that up to date.** Goals must be cheap: no
  per-tick pathfinding beyond vanilla costs, reuse the mob's sensing cache, hard caps everywhere.
- `compat/` — `CompanionPetHooks` (the SPI NeroCreatures **declares** and NeroCompanion implements)
  and `CompanionBridge` (ServiceLoader lookup behind an `isModLoaded` pre-filter, cached, and
  no-op-defaulted). **No reflection into another mod, ever.** The boundary: NeroCreatures owns
  taming, ownership/caps, the command cycle, owner-only interaction, defending the owner and the
  species perk; everything deeper is NeroCompanion's. Documented in `wiki/Pets-and-Drones.md`.
- `spawn/` — `SpawnRule` (code-declared spawn table entries against Core's space tags, plus an
  optional cheap per-level `gate` used only by the boss rule — it is tested **before** the biome
  lookup, so a gate must stay cheap), `CreatureSpawns` (the server-tick placement sweep; it also
  owns the `currentServer()` reference the link module reads, because Core's snapshot API hands over
  nothing but a player UUID) and `CreatureCensus` (bounded, cached population counts over **both**
  class roots). Read `CreatureSpawns`' class Javadoc before touching spawning: biome-modifier
  injection is loader-divergent, which is why this mod runs its own vanilla-shaped sweep.
  `InvasionBudget` is the public seam a future NeroEvents raid uses: `reserve` → `spawnWithin` →
  `close`. Its budget is an **allowance on the dimension cap only** — the chunk cap is never
  relaxed and a cap of 0 still means zero — and it cleans up two ways, because the handle's UUID
  set cannot survive a restart and the persistent `WaveId` marker cannot cover another mod's entity.
- `command/CreatureCommands` — the `/nerocreatures` tree (`list`, `caps`, `summon-boss`, `export`,
  `telemetry-test`, `gallery`), built once in common and registered from each loader's
  `*CreatureEvents`.
  Whole tree at `LEVEL_GAMEMASTERS`; every `sendSuccess` passes `false` for "broadcast to ops".
  **`export` is the documented POPIA/GDPR access path** and must keep coming straight from the
  stores' own `exportPlayer` methods — widening what an export contains is a change to a store, not
  to this class.
- `command/CreatureGallery` — the creative-only `/nerocreatures gallery` showcase scene (roster
  frozen + live, boss arena, pets/drone, item wall) and its `clear`. Gated player-only **and**
  creative (the creative check is in the body, because a `requires` predicate is evaluated when the
  tree is sent to the client). **It writes no player data, by construction and not by care**: the
  boss is created and added WITHOUT `finalizeSpawn`, so no fight starts (no bar, no
  `boss_pressure` crossing, no link broadcast, no `BossSpawns` cooldown — and contribution is only
  ever switched on by `BossSummons`, so no `BossContributionState` row can exist for it), and the
  pets/drone are shown untamed and unowned so no `CreatureOwnershipState` row is written and no cap
  is spent. Keep it that way. The live row is contained by **glass** (which breaks line of sight)
  rather than by assuming Peaceful. Its cluster coordinates are mirrored by the capture harness's
  camera poses — tune the two together — and the label stands carry `LABEL_TAG` so the harness can
  strip them without also deleting the item displays (which are invisible armour stands too).
- `link/` — the NeroLink surfaces, ported from NeroQuests: `CreatureLinkModule` (registration + the
  swallow-`RuntimeException` init guard), `CreatureLinkSnapshots` (`bestiary`/`pets`/`bosses`),
  `CreatureLinkActions` (the single owner-scoped `pet_recall`), `CreatureLinkEvents` (the three
  topics **and** the two `LinkAlerts`) and `CreatureLinkAccess` (server lookup + the registry-path
  name helper — a dedicated server has no lang file, so never call `Component#getString` for an
  alert). **Own-data-only is the contract**: a section is scoped to `playerId` before it leaves the
  class, and a *broadcast* event may carry a boss, a phase and a dimension and nothing else. The
  bestiary reads **vanilla's** kill statistics on purpose — a bestiary store would be a third
  player-keyed record to erase, retain and declare.
- `registry/ModItems` — the eleven creature drops (ten roster drops plus the boss `apex_trophy`)
  plus the crafted `drone_shell` deployable (kept out of the drops list and tagged as a tool, not a
  material), registered through Core's seam and contributed to Core's shared creative tab. Three
  things add **bonus rolls** in code on top of a loot table (golem pickaxe kill, android salvage,
  and a summoned boss's contribution split); those are the only things `dropRateMultiplier` scales,
  and `wiki/Drop-Map.md` is where that contract is written down.
- `registry/ModEntities` — entity types (Core `RegistrationProvider`) plus, in a **separate second
  call**, default attributes and spawn placements (Core `EntityRegistrationSupport`). Placement is
  only "may this position be considered"; biome/weight/group size live in `spawn/CreatureSpawns`.
  `roster()` publishes the whole creature list — add a new creature to it, or the command tree and
  the link bestiary will silently not know about it.
- `registry/ModSpawnEggs` + `item/CreatureSpawnEggItem` — one egg per creature **except the
  Terraforming Drone** (an egg makes an ownerless mob, which would sit outside the cap and be
  un-recallable and un-erasable), tagged into `neroland:highlight/tools`. Not vanilla
  `SpawnEggItem`: that binds its `EntityType` in its constructor, and items register before entity
  types on the deferred loaders, so the egg resolves its type lazily through a `Supplier`.
  `item/DroneShellItem` is the drone's counterpart: cap check → bind → add to level → index, in that
  order, so a drone never exists even briefly without an owner.
- `client/` — the cross-loader client seam. `ClientEntityRenderers.Sink` is the one place a renderer
  is named; each loader passes its own sink (NeoForge/Forge `EntityRenderersEvent.RegisterRenderers`,
  Fabric `EntityRendererRegistry`). Models are **baked directly** (`createBodyLayer().bakeRoot()`) —
  no model-layer registry, which is what keeps the three loaders identical. `CreatureModel` is the
  shared model base (walk swing + idle/constant waves), `CreatureRenderer` the single renderer, and
  one `*Model` class per creature. Loader wiring: `NeroCreaturesFabricClient`, plus
  `NeoForgeClientSetup`/`ForgeClientSetup`, gated on `FMLEnvironment` so client classes never load
  on a dedicated server.
  `client/GalleryCaptureHarness` is the screenshot pass over the `/nerocreatures gallery` scene:
  vanilla client classes only, generic in the Brigadier source type `<S>` so one
  `registerClientCommands` serves all three loaders, plus a `tick()` pump each loader calls from its
  own client-tick hook (Fabric `ClientTickEvents.END_CLIENT_TICK` +
  `ClientCommandRegistrationCallback`, NeoForge/Forge `ClientTickEvent.Post` +
  `RegisterClientCommandsEvent`). **Its root is `/ncgallery` and must never be `/nerocreatures`: a
  client-dispatcher root that shares a name with a server command SHADOWS the server subtree** — the
  client parser matches the root, fails on the server-only child and never forwards. The HUD-hide
  flag and the main render target diverge across 26.1.2 ↔ 26.2 and are resolved reflectively.
  Output is `screenshots/nerocreatures/<shot>.png`, which is the folder the Modrinth gallery
  workflow reads from the NeoForge 26.2 run directory.

## Build & verify

- Build the cells with the Gradle wrapper, e.g. `./gradlew :fabric:26.2:build` or all six:
  `:neoforge:26.1.2:build :neoforge:26.2:build :forge:26.1.2:build :forge:26.2:build
  :fabric:26.1.2:build :fabric:26.2:build`.
- Static analysis: `./gradlew :fabric:26.2:ecjCheck` (the VS Code Problems panel, via `tools/ecj.prefs`).
  The task only FAILS on errors.
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.
- Core resolves from `mavenLocal()` first (run Core's `publishToMavenLocal`), then GitHub Packages.
  A Core version that has not been published yet builds locally but fails in CI.

## Conventions (cross-loader)

- **Resources are HAND-AUTHORED in `common/src/main/resources`** — the multiloader does not run datagen.
  Validate JSON after edits.
- **Platform seams via ServiceLoader (no Architectury).** Put loader-agnostic code in `common/`; ship one
  impl per loader plus a `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` /
  `net.fabricmc.*` / `net.minecraftforge.*` imports.
- `common/` compiles against **raw vanilla** (NeoForm), so loader-added `Entity` hooks such as
  `onAddedToLevel` are NOT available there — solve those problems with vanilla queries instead.
- 26.x gotchas: `Item.Properties.setId(key)` is mandatory; `ItemStack.is(TagKey)` is gone (use
  `typeHolder().is(...)`); `ResourceKey.location()` is now `identifier()`; item assets use the
  `assets/<ns>/items/<name>.json` + `models/item/<name>.json` pair.
- Textures: `tools/gen_textures.py` writes the 16×16 item/spawn-egg placeholders **and** the 64×64
  entity sheets (matching the models' `LayerDefinition.create(mesh, 64, 64)`). It is **additive** —
  it never overwrites an existing file, so hand-drawn replacements survive reruns.
- Sounds are **mapped to vanilla `SoundEvents`**, not shipped. Note 26.x moved the wolf set behind
  the per-variant wolf sound registry, so `SoundEvents.WOLF_*` no longer resolves.
- Loader entry points: `NeroCreaturesFabric` (+ `NeroCreaturesFabricClient`), `NeroCreaturesForge`,
  `NeroCreaturesNeoForge` — each calls `NeroCreaturesCommon.init()` first, then attaches Core's
  registration seams (NeoForge/Forge only) and registers its own network + tick hooks, and on the
  client dist its own `*ClientSetup`. Server-side loader events live in `*CreatureEvents`: the spawn
  sweep's tick hook and the `/nerocreatures` command registration (Fabric
  `CommandRegistrationCallback`, NeoForge/Forge `RegisterCommandsEvent`).
- NeoForge/Forge debug tasks use `-PnerocreaturesDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`nerocreatures.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `nerocreatures-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root: the player- and
  contributor-facing docs for NeroCreatures (creatures, drops, spawning, bosses, commands, FAQ).
- **Whenever you add, change, or remove a feature, update `wiki/` in the same change** — treat the
  wiki as part of "done"; code without a matching wiki update is incomplete.
- `wiki/Drop-Map.md` is the **canonical cross-mod contract** for creature drops (creature → drop →
  tag → consuming mod). Balance and consumer changes go through that page first.
- `wiki/Bestiary.md` is the per-creature page: behaviour, spawn biome tag, weight, group size,
  drops and spawn egg. Every new creature gets a section there in the same change.
- `wiki/Pets-and-Drones.md` is the player-facing contract for everything *owned*: taming, the
  command cycle, the drone, the caps, exactly what is stored about a player and how to erase it, and
  the NeroCompanion boundary. Any change to ownership, caps, perks or stored data goes there.
- `wiki/Bosses.md` is the contract for bosses: the fight guide, the two entry modes, the **exact**
  contribution reward split, what a summoned fight stores about a player, and the
  `nerocreatures:boss_pressure` threshold-channel semantics other mods code against. Reward numbers,
  the participation floor and the event value semantics all live there first.
- `wiki/Spawning.md` is the spawn contract: the Core space-biome tags, the **full weight table**,
  the sweep's budget numbers, the caps and every config key, plus the `InvasionBudget` contract for
  event mods. A change to a weight, a group size or a cap default goes there in the same commit.
- `wiki/Gallery.md` is the contract for the creative showcase scene and the `/ncgallery` capture
  harness: the two commands, what the scene contains, what it deliberately does not write, the shot
  list and how the PNGs reach the Modrinth gallery. A new shot, a moved cluster or a changed camera
  goes there in the same change.
- `wiki/Link-Module.md` is the companion-app contract (sections, action, events, alerts, and the
  own-data-only rule), `wiki/Commands.md` documents the `/nerocreatures` tree, `wiki/Data-Storage.md`
  is the practical view of both player-keyed stores and `wiki/Telemetry.md` the opt-out crash
  reporting. **Anything that changes a snapshot's shape must bump `SCHEMA_VERSION` and say so in
  `wiki/Link-Module.md`.**
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** — document only NeroCreatures here.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.
- Add any Nero mod other than `nerolandcore` to a manifest.
- Assume a Core space tag has members — an empty tag means "no spawns", and must never crash.
- Reach a `SavedData` any way except through `data/SavedDataRecovery`.
- Log a player UUID (or anything else identifying) on an erasure, retention or export path.
- Put anything player-shaped into a `ThresholdEvents` scope — Core's contract says a scope is a
  place or a system, and `nerocreatures:boss_pressure` publishes a dimension id and nothing else.
- Record boss contribution for a fight that was not summoned. A boss found in the world stores
  nothing about anybody, and that is a privacy control, not an optimisation.
- Let the gallery tame, deploy, own, summon or track anything. It is a photograph: it may spawn and
  place, and it may not write a row, start a fight or spend a cap.
- Give the client command tree a root that a server command already uses — it shadows it.
- Reflect into another Nero mod. Cross-mod behaviour is an SPI this mod declares plus
  `ServiceLoader`, and it must degrade to a no-op when the other mod is absent.
- Put another player's data — rows, damage totals, names, UUIDs, positions — into a link snapshot,
  an action result, an event payload, an alert text or command feedback. A snapshot is scoped to the
  requesting `playerId`; a **broadcast** event carries a boss, a phase and a dimension and nothing
  else.
- Let a link failure reach gameplay. Registration swallows `RuntimeException` with a warning, and
  every publisher and snapshot section is wrapped; a companion client is a nice-to-have and the
  creature layer is not.
- Reference any out-of-repo planning docs (or any absolute path) from anything in this repository.
