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
  items → creative tab → data/erasure → network → spawn rules. Fabric registers eagerly, so
  anything that must exist first has to be listed first on every loader.
- `config/NeroCreaturesConfig` — Core-backed schema (`config/nerocreatures.properties`). Everything
  gameplay-related is server-authoritative; `telemetryEnabled` deliberately is not.
- `platform/{Services,PlatformInfo,NetworkPlatform}` — ServiceLoader seams, one impl per loader plus a
  `META-INF/services` entry. NeroCreatures needs its own `PlatformInfo` because Core's helper reports
  *Core's* version.
- `network/CreatureNetwork` — declare-once/register-per-loader payloads on the `nerocreatures:main`
  channel. **Never** add payloads to Core's `CoreNetwork`: Forge seals that channel inside Core's
  constructor. The payload list is legitimately empty while the mod is server-authoritative only.
- `telemetry/` — opt-out Sentry wiring, ported from the sibling mods. **Ships a placeholder DSN and
  is a hard no-op until a real one is set.** Do not remove that guard.
- `data/CreatureData` — registers the Core `PlayerDataErasure` lambda at construction, ahead of the
  stores it will purge.
- `entity/base/` — `NeroCreatureEntity` (shared base: tier attributes, config scaling, despawn rules)
  and `CreatureTier`. Every creature must extend the base, or it escapes the population caps.
- `entity/ai/` — the reusable goal library: `BlinkStrikeGoal`, `BurrowGoal`/`EmergeAttackGoal`
  (+ the `Burrower` contract), `PackCoordination`, `SplitOnDamageBehaviour`, `EnergyAuraGoal`.
  **Every goal documents its cost profile in its Javadoc — keep that up to date.** Goals must be
  cheap: no per-tick pathfinding beyond vanilla costs, reuse the mob's sensing cache, hard caps
  everywhere.
- `spawn/` — `SpawnRule` (code-declared spawn table entries against Core's space tags),
  `CreatureSpawns` (the server-tick placement sweep) and `CreatureCensus` (bounded, cached
  population counts). Read `CreatureSpawns`' class Javadoc before touching spawning: biome-modifier
  injection is loader-divergent, which is why this mod runs its own vanilla-shaped sweep.
- `registry/ModItems` — the ten creature drops, registered through Core's seam and contributed to
  Core's shared creative tab.

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
- Item textures: `tools/gen_textures.py` writes the 16×16 programmer-art placeholders. It is
  **additive** — it never overwrites an existing file, so hand-drawn replacements survive reruns.
- Loader entry points: `NeroCreaturesFabric` (+ `NeroCreaturesFabricClient`), `NeroCreaturesForge`,
  `NeroCreaturesNeoForge` — each calls `NeroCreaturesCommon.init()` first, then attaches Core's
  registration seams (NeoForge/Forge only) and registers its own network + tick hooks.
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
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** — document only NeroCreatures here.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.
- Add any Nero mod other than `nerolandcore` to a manifest.
- Assume a Core space tag has members — an empty tag means "no spawns", and must never crash.
