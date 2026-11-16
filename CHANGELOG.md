# Changelog

All notable changes to **NeroCreatures** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `/nerocreatures budget`: milliseconds per server tick per species and in total, over a 1 to 60 second sample. Far creatures (no player within `farTickDistance`, default 48) run their own AI work on fewer ticks (`farTickInterval`, default 4).
- Measured budget (NeoForge 1.21.1 dedicated server, 200 creatures of every species mixed): 2.0 ms per tick with every creature at full rate, 1.6 ms with the default far-tick slowing; sporeling, alpha and swarm caps held under stress. The 26.3 number is still to be recorded. Dev-only probe: `gradlew :neoforge:<mc>:runBudgetProbe -PbudgetProbe` (test source set, never in a release jar).
- Measured budget (NeoForge 26.3 dedicated server, same 200-creature mix): 2.1 ms per tick at full rate, 1.6 ms with the default far-tick slowing; one of each species 0.58 ms; all population caps held.
- Config keys for every world-changing creature ability (each still respects `mobGriefing`, the loader's place check and, for owned creatures, the owner's own build permission), for mounts and eggs, and for overworld spawns.
- **Traits.** Every tameable creature carries two traits (Hardy, Swift, Keen, Bountiful, Calm, Thrifty, Stout, or the rare cosmetic Radiant morph). Pets from older saves roll theirs on first load; nothing they already had changes. Babies inherit one trait from each parent. See the wiki page Traits.
- **Mount base.** Rideable creatures share one base: tamed like pets but capped separately (`maxMountsPerPlayer`, default 2), ridden with a new **Creature Saddle** (crafted from Stalker Hide, Stalker Sinew and iron), steered with WASD, ability on the jump bar with stamina, and chest panniers (9 slots, 18 with Stout). Owner-only riding by default (`mountOwnerOnly`). The mount species themselves arrive with the new creatures.
- **Alphas.** Hostile creatures can spawn as rare, 30% larger alphas with extra health, damage and one modifier, and drop one extra rare item when a player kills them. Data-driven (`alpha/` in the data pack) and configurable: `alphasEnabled`, `alphaChanceMultiplier`, `alphaMaxPerDimension` (default: at most 4 loaded per dimension).
- Lunar Stalkers now also hunt wild grazers (never tamed, owned or named animals), resting two minutes after a kill.
- Every creature now carries a small synced visual state (pose, phase, stage, variant, form and the Radiant, alpha, saddled and panniers flags) for animations, saved with the creature.
- **Luna creatures:** Lumen Moth (tameable living lantern), Echo Shrike (blind sound hunter) and Lode Tortoise (livestock that grows ore nodules). New drops Lumen Dust, Resonant Membrane and Lode Scute; two Lumen Dust craft one Glowstone Dust. Config `lodeTortoiseNoduleTicks` (24000) and `maxNodulesPerHarvest` (4).
- **Mars creatures:** Dune Loper (mount with a leap and panniers), Strata Hound (prospecting pet) and Dust Devil (whirlwind hazard). New drop Charged Sand (`c:dusts/charged_sand`).
- **Cindara creatures:** Slag Beetle (living furnace pet) and Magma Wader (lava wader whose slag hardens into a temporary block). New drops Slag Carapace and Heat Gland, and the self-removing `ephemeral_crust` block.
- **Overworld spawns without a planet mod:** Lumen Moths in dark forests at night and Lode Tortoises in deep caves, rare by a per-attempt chance and capped by `overworldSpawnCap` (default 12); off with `overworldSpawns`.
- Habitat biome tags `nerocreatures:habitat/<world>` (Luna, Mars, Cindara, Glacira, Greenxertz, Nyxara, asteroid, wrecks, deep space, gas giant, overworld dark forest, overworld), every planet entry optional.
- Spawn eggs for the eight Luna, Mars and Cindara creatures. Wiki pages Luna, Mars and Cindara.
- Lunar Stalker packs howl when they share a target (pack-mates glow briefly), and the last survivor of a pack flees when badly hurt.
- **Glacira creatures:** Rime Yak (livestock with a cold aura that puts out fires, chills hostile mobs and freezes still water into frosted ice that melts back; shear it for Rime Wool, milk it with a bucket for Coolant Milk) and Frost Lurker (buried ambusher with a freezing bite). New drops Rime Wool, Coolant Milk (the bucket comes back when crafted with) and Cryo Fang. Config `rimeYakWoolRegrowTicks` (6000) and `rimeYakMilkCooldownTicks` (2400).
- **Greenxertz creatures:** Spore Drifter (tameable flyer that speeds crops below it; bottle its spores), Bloom Host (raises short-lived, capped sporelings from animals it kills), Mossback (herd grazer that fertilises grass) and Drift Leviathan (rare sky giant with a walkable deck that holds still while you stand on it; at most two loaded per dimension). New items Drifter Spores (two bone-meal growths, bottle returned), Bloom Heart, Mossback Hide (`c:leathers`; an alternative Creature Saddle recipe), Raw and Cooked Mossback, and Leviathan Barnacle.
- **Nyxara creatures:** Prism Stag (neutral livestock that sheds crystal antlers and, in herds of three at night, gives nearby players a short Regeneration; config `prismStagHerdAura`) and Shardback (mirror-fanned charger that reflects projectiles from the front). New drops Prism Antler (cuts into two Refined Crystal) and Mirror Scale.
- Bloom Hosts are owner-friendly by default: they never hunt or raise tamed, owned, named or leashed animals, and leave bred livestock (babies, animals in love or that bred recently) alone unless `bloomHostInfectsLivestock` is on. Sporelings and spore pods have their own small hitboxes.
- Spawn eggs for the eight Glacira, Greenxertz and Nyxara creatures. Wiki pages Glacira, Greenxertz and Nyxara.
- **Asteroid creatures:** Magnet Crab (pet: wild crabs grab loose metal into their shell and give it all back on death; a tamed crab pulls nearby ground items to its owner, only unthrown items or the owner's own) and Gravity Leech (flyer whose telegraphed tether lightens, or briefly flips, a player's gravity; never saved, it lapses on its own). New drop Graviton Node (for the Graviton Boots coming with the gear).
- **Wrecks and pirates:** Cache Mimic (a salvage crate that bites), Static Swarm (drains Plasma Cells, bottle a weakened one into a throwable **Static Jar** that disables machines for 5 seconds; a jar and a redstone craft 2 Plasma Cells), Scrap Hulk (an elite that assembles itself where three machines die close together; `scrapHulkAssembly`) and Pirate Captain (elite with a jet-dash, a rally and two crew; its killer gets a **Captain's Ledger** that leads to a guarded pirate stash). The ledger only ever goes to the player credited with the kill; the stash mark lives on the item only.
- **Deep space:** Void Manta, a flying mount (chorus fruit) that flies freely in low or zero gravity, glides elsewhere and blinks up to 8 blocks on the jump bar. It spawns only on off-Earth dimension types.
- Static Swarms also appear in overworld thunderstorms (10% of attempts, `overworldSpawnCap`, off with `overworldSpawns`).
- Spawn eggs for the seven Asteroid, Wrecks, Pirate and Deep Space creatures. Wiki pages Asteroid, Wrecks & Pirates and Deep Space; the Mounts page lists both mounts.
- **Three new bosses**, summoned only at the Apex Altar: the **Rime Matriarch** (Glacira; blizzard beam with a locked aim, temporary ice-pillar cover, a widening freezing floor), the **Bloom Sovereign** (Greenxertz; rooted, heals from spore pods you can kill mid-channel, infecting lash that raises capped sporelings) and the **Facet Colossus** (Nyxara; only its lit facet takes damage, a beam that refracts through temporary arena crystals, decoy facets that spark back in phase 2, an exposed core in phase 3). All arena blocks are temporary, restored when the fight ends, and gated by `bossArenaBlocks`; with it off each boss uses a block-free fallback. New drops Matriarch Frostcore, Sovereign Seed (breaks down into 4 Bloom Hearts) and Colossus Facet; the Cinder Tyrant also drops a Tyrant Ember.
- **Apex Altar** and four **boss sigils** (crafted from habitat drops; item tag `nerocreatures:sigils`). Use a sigil on the altar to start a contribution-tracked fight; the sigil is used only if the boss arrives. The altar locks (glowing, unbreakable in survival) until the fight is over and its cooldown has run: `apexAltarCooldownTicks` (12000), per altar, never per player. Spawn eggs for the three new bosses. Wiki page Bosses (now Bosses and the Apex Altar).
- **Habitat blocks:** Nest (home point where your pets and mounts rest and heal), Feeding Trough (breeds husbanded livestock from stocked food, never wild herds), Incubator (hatches one egg, put in by hand with no screen; 20 minutes, 5 with power from any adjacent energy storage, no power mod needed), Lure Beacon (one species up to 8x more likely to spawn within 24 blocks; caps still apply), Sonic Ward (no hostile NeroCreatures spawn within 16 blocks, and those at the edge are walked out; nothing is hurt), Terrarium (shows one small captured creature; cannot be mined while full) and Trophy Plaque (shows one trophy). Config `feedingTroughIntervalTicks`, `incubatorHatchTicks`, `incubatorPoweredHatchTicks`, `incubatorEnergyPerTick`, `lureBeaconBaitTicks`, `lureBeaconWeightMultiplier`, `sonicWardFuelTicks`, `nestHealIntervalTicks`. Wiki page Habitat Blocks.
- **Capture Pod:** carry a small tameable creature (wild, or your own tamed one) and release it on a block; it keeps its traits and stores no owner, UUID or position. A released tamed creature becomes the releaser's pet (pet cap applies). Cheap recipe from a Leviathan Barnacle.
- **Creature gear:** Mirror Shield, Lurker Cloak, Blink Charm, Rime Flask, Heat-gland Greaves, Graviton Boots, Resonant Whistle, Chitin Bulwark and Bloom Amulet, each crafted from a habitat drop, on cooldowns with a config key each. Worn in their slot on 1.21.2+ or held. Matriarch Frostcore crafts 4 Rime Flasks, a Colossus Facet a Mirror Shield and a Tyrant Ember the Greaves, so every 0.5.0 drop now has an in-mod use. Wiki page Gear.
- **Breeding:** every livestock and tameable species breeds on its own food (item tags `nerocreatures:food/<species>`). Six species lay eggs (`<species>_egg`, traits carried on the egg) that hatch in the Incubator; hatched tameables tame on their first feed, hatched livestock are born husbanded. At most `maxIncubatingEggsPerPlayer` (8) eggs incubating per player. Wiki page Livestock and Breeding.
- Space Pirates pick up loose Contraband (up to 16) and drop it on death (`spacePirateTakesContraband`, plus `mobGriefing`).
- Companion interface: a companion mod can perch small pets, send fetchers on errands, add goals (priorities 3 to 6 only), show a hat and park a tamed pet in its own storage (the ownership row stays, marked parked). No-op without a companion mod.
- **Original sounds.** Every creature, boss, habitat block and gear item now has its own synthesised sound (187 events: voices, ability cues, boss phase and summon stingers, the Incubator hatch, Lure Beacon pulse, Sonic Ward hum, Apex Altar summon, Capture Pod capture/release, Mirror Shield, Blink Charm, Resonant Whistle, Static Jar), all with English subtitles. The Scrap Hulk no longer borrows the iron golem's sounds, and no creature borrows vanilla voices any more.
- Worn gear shows on the player (1.21.4+): the Lurker Cloak, Heat Gland Greaves and Graviton Boots have armour layers.
- A filled Capture Pod looks filled; the Incubator and Terrarium have glass panes; the Incubator's lamp turns green while Core power reaches it; the Apex Altar glows in the colour of the boss whose sigil it holds.
- **Field Scanner and Bestiary.** A craftable Field Scanner (vanilla materials) scans a creature in 2 seconds within 8 blocks; Sneak + Use or the unbound *Open Bestiary* key opens the Bestiary: every creature by habitat (undiscovered ones as silhouettes), a turning model, ability, weakness, drops and their uses, your own records, and a trait glossary, laid out for GUI scales 2, 3 and 4 and small windows, every control labelled or tooltipped. Two tiers per creature, Scanned and Studied (5 kills, 3 for elites; one tame, hatch or breed; one harvest or breed; a summoned boss fight), stored as Neroland Core record gates, with a one-time experience reward (25, 60 elite, 150 boss). New payloads `bestiary_request`, `bestiary_counts`, `bestiary_event` answer only the asking player. Wiki: Field Scanner and Bestiary.
- **Custom particles.** 16 particle types (Lumen motes, echo rings, nodule glints, dust swirls, static sparks, slag embers, rime and spore motes, spore puffs, bloom motes, prism glints, magnet sparks, gravity ripples, void trails and blinks, scan beams) replace the vanilla stand-ins on every creature that used one, plus new cues for the Echo Shrike, Lode Tortoise, Gravity Leech latch and the Field Scanner.
- **Menagerie quest chapter** for NeroQuests: the Field Scanner, the three overworld creatures, a first breed, a survey per habitat, the mounts and the three Apex Altar bosses. New milestone gate `nerocreatures:first_breed`.
- **Companion app link, schema 2.** `bestiary` adds each species' habitat and kind and your own discovery tier, and for scanned species the ability, drops with their uses, study progress, owned count and sigil (Studied adds the weakness; unscanned species are sent as silhouettes: id, habitat, kind, tier only). `pets` adds mounts and incubating eggs, traits, saddle and stamina, breeding and hatch timers, and which orders each creature takes. `bosses` adds the cooldowns of the Apex Altars you lit, by label number only. Every schema-1 member is kept; no section carries a coordinate or another player. See the wiki page Link Module.
- New companion-app action `pet_command`: tell one of your own pets or mounts to follow, stay or sit. Unknown orders are refused (`unknown_command`), never treated as Follow. `pet_recall` now also recalls mounts. Both actions check ownership against the ownership index and are rate-limited per player: `linkRecallCooldownSeconds` (10) and `linkCommandCooldownMillis` (1000). Refusals are tokens (`rate_limited`, `not_owner`, `unknown_command`, ...) instead of English sentences.
- Companion-app pushes (off by default; the app's "Creatures" category): your creature died, is badly hurt (below a quarter health, not hurt by you, at most once per creature per five minutes), your egg hatched, or a summoned boss fight you joined ended. The push names the species only. New player events `creature_hurt`, `egg_hatched` and `fight_ended`.
- **Stay** order: a pet or mount on Stay stands exactly where it is and does not follow, wander or fight. The in-game sneak-click cycle is now Sit, Stay, Guard, Follow; the old "Stay here." order is now called **Guard** ("Guard this spot.") and behaves as before.

### Changed

- The `pet_state_changed` link event is now published only when an owned creature dies; tamed, released, deployed and recalled are no longer sent (a companion app reads them from the `pets` section), because the bridge would turn every player event into a push.
- `/nerocreatures export` (format 3) adds `altars_lit`: the Apex Altars the player lit that the server still remembers in memory for the companion app (dimension, position, cooldown end, last boss).

- Crystal Golems telegraph their smash (the blow lands 12 ticks into a 30-tick wind-up, only if you are still in reach). Glacite Wisps and Xertz Foragers sit in a resting pose; foragers now and then root about (cosmetic). The Crystal Golem, Glacite Wisp and Xertz Forager use their new generated models.
- Livestock a player keeps (fed, bred, shorn, milked, harvested, leashed or named) is now **husbanded**: it never despawns and predators and Bloom Hosts leave it alone; babies inherit it. Older saves: named animals are husbanded on load.
- Lode Tortoise: wild tortoises often arrive with nodules of a common ore; once nodules show, grazing other ore keeps their family and progress instead of resetting; harvesting wears the pickaxe by one; `lodeTortoiseNoduleTicks` default is now 36000 (rare ores 4x).
- Drift Leviathan: saved with its chunk (barnacles included), no longer randomly despawns nearby, and wild ones arrive with barnacles half to fully grown.
- Cache Mimic: loot drops only from a player kill, and natural spawns are capped at one per 48 blocks.
- Strata Hound: tracks only ores, raw ores and natural stone (`#nerocreatures:strata_trackable`), never containers or other block entities.
- Slag Beetle: eats raw ore items only (no ore blocks); Bountiful adds a second 10% bonus roll instead of doubling the output.
- Bloom Sovereign spore pods drop no loot, and give no experience after a reload either.
- Bosses must be fought: they take damage only from a player or something a player owns (lava, fire, suffocation, falls, cacti, dispensers and other mobs do nothing; `/kill` and the void still work), never go through a portal and never ride a minecart or boat.
- Bloom Sovereign: her bark turns away damage from an attacker beyond her lash's reach (16 blocks) or out of her sight, so she can no longer be shot from afar or struck from under an overhang at no risk.
- Facet Colossus: its beam hurts only players inside the arena and wild hostile mobs, never pets, mounts, livestock, villagers or players outside the arena. Its arena crystals are picked back up after a reload, so the fight's end removes them all.
- Apex Altar: refuses a sigil on Peaceful, and checks room for the boss's whole body (plus half a block) instead of a four-block column; the sigil is kept either way. A cooldown carried in from another world is capped at the longest possible cooldown.
- The `cindara_quiet` quest now completes only on a Cinder Tyrant's defeat on Cindara (new channel `nerocreatures:cinder_tyrant_defeated`); `nerocreatures:boss_pressure` is unchanged and is now fired by all four bosses.
- A boss summon that another mod cancels no longer leaves a phantom fight behind, and abandoned summoned fights are cleared when the fight store is full instead of only at server start.
- Cinder Tyrant: its slam now plays a 32-tick animation that starts 12 ticks before impact, and its stance follows the fight's phase.
- Facet Colossus ring: drawn on the level's game time (the same on every client and on the server), so the lit facet is where its vulnerable side really is; its decoy facets are synced for the art.
- The original creatures telegraph their abilities and play their new animations: the Void Crawler crouches with a charge-up sound before it blinks and leaves an afterimage; the Asteroid Worm leaves a dust trail while burrowed, surfaces after 12 ticks inside a dust ring drawn at its strike range and bites at tick 28; the Plasma Slime gathers before each aura pulse, shows the pulse as a ring of sparks and pauses its aura in water or rain; Space Pirate crossbows aim for half a second before each bolt; the Rogue Drone crouches before its pounce and shorts out in a small, harmless push of sparks when it dies; the Rogue Android shows its remaining shield charges as pips and holds its shield up while it fights; the Terraforming Drone loops its work animation and rests when it may not work.
- Every creature now renders with its generated model, including the original twelve.
- The Terraforming Drone's crop growth now goes through the shared world-change gate (`terraformingDroneWork`, `mobGriefing`, the loader's place check and the owner's own build permission), so it pauses while its owner is offline. Sneak near your drone holding a Drone Shell to see its work area outlined.
- Prey animals no longer notice a predator that is hidden (a buried Frost Lurker), so they can walk into its trap.
- The ownership store gains `mount` and `egg` rows (egg rows hold a random incubation id and a hatch time). 0.4.x files load unchanged. See `PRIVACY.md`.
- Erasure now also reaches pets, mounts and drones in unloaded chunks: the erase leaves a marker (the creature ids it removed, and a salted one-way hash of the player, never the UUID) and such a creature is released silently when it next loads. A creature is released only on such a marker, never because the index merely lacks a row, so pets from older saves or a store restored from backup stay tamed and are re-indexed.
- Erasure markers: a creature's marker is kept until that creature loads and is freed, so a pet in an area nobody visits for months is still released, never re-indexed under the erased player. The salted owner hash lives `max(dataRetentionDays, 365)` days and the per-world salt is deleted with the last one. Markers dropped at the (raised) cap are logged as a count instead of vanishing silently.
- Retention no longer releases the creatures of active players. Inactivity is Neroland Core's last-seen record (its purge-inactive sweep erases through the same hook); "no new tames for N days" is no longer treated as inactive.
- Erasure sends nothing to the player being erased (no companion-app event, push or alert), and clears vanilla's `LoveCause` on loaded animals that name them.
- `/nerocreatures export` now returns everything the mod holds about one player: ownership rows (with parked pets and egg hatch times), their own boss share, this mod's Bestiary and milestone gates from Core's store, loaded creatures that name them as owner with any Nest home, and whether an erasure marker exists for them. Still nothing about anyone else. It is written whole to a file under the world folder (owner-only where the file system allows) and only the path is printed, so a console or RCON export no longer lands in the server log and long exports are no longer cut off.
- Capture Pods keep only an allow-list of the creature's saved data (no last attacker, anger target, brain memories or loader and mod attachment data), and the filter runs again when a pod is read.
- Crash reports scrub UUIDs, entity and player names, world names and coordinates from exception messages and breadcrumbs; a FATAL log line is reported as a fixed message with its logger name, never its text. Handled-error reports now carry their source tags.
- Failure logs carry the exception type only, never its message or stack trace.
- `PRIVACY.md` rewritten as a full data inventory (every store and field, purpose, legal-basis notes, retention, erasure, export, link module, pushes and telemetry), including why an erasure marker is treated as pseudonymised personal data.
- New `tools/check_log_privacy.py --check`: fails if any INFO/WARN/ERROR log call passes a UUID, name, coordinate, a generic object that may hold one, or a whole exception. New `tools/check_private_refs.py --check` keeps references to private planning records out of the repository. Both run in the publish workflow; the private-reference guard also runs before the wiki is published.

## [0.4.1-alpha.2] - 2026-10-31

### Changed

- Release uploads now target CurseForge and GitHub Releases only.
- Removed automatic project description and gallery uploads.
- Renamed the website overview source to `art/description.md` and refreshed public documentation links.

## [0.4.0-alpha.2] - 2026-10-31

NeroCreatures now also runs on Minecraft 1.21.x, alongside the 26.x builds. Existing worlds load
unchanged.

### Added

- **Minecraft 1.21.x builds.** NeroCreatures now builds for Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.8
  (also runs on 1.21.7), 1.21.10 (also runs on 1.21.9) and 1.21.11 on NeoForge, Fabric and Forge,
  next to the 26.x jars. These builds need Java 21. Every creature, the boss fight, pets and drones
  are on every line. Minecraft before 1.21.9 has no copper golem sounds, so there the Rogue Android,
  Rogue Drone and Terraforming Drone use the iron golem's hurt, death and step sounds and the
  grindstone for the android's spin-up instead.

### Changed

- Now requires Neroland Core 1.17.0 or later.

### Fixed

- Crafting recipes now unlock in the recipe book the first time a player picks up one of their ingredients (1 recipe-unlock advancements, generated by `tools/gen_recipe_unlocks.py`; run it again after adding a recipe, `--check` verifies).

## [0.3.1-alpha.1] - 2026-10-20

### Changed

- **NeroCreatures has its own creative tab** (`itemGroup.nerocreatures`): the drops, the drone
  shell and the spawn eggs moved out of Neroland Core's shared tab, which now holds only Core's items.
- **Saved-data recovery now uses Neroland Core's shared guard** (`data.SavedDataRecovery`, Core
  1.15.0) instead of NeroCreatures' own minimal copy, which is removed. A corrupt ownership or boss
  contribution file is now restored from Core's last-known-good backup instead of starting empty, and
  26.x's silent decode failures are detected too. The backups hold the same fields as the stores and
  live in the same world save; player erasure and retention purges refresh them in the same request
  (POPIA/GDPR). See `PRIVACY.md`.

### Fixed

- **Block and loot drops work as intended on Minecraft 26.3** (NeoForge, Fabric and Forge). Minecraft
  26.3 changed the loot-table file format and silently ignores the old one, so conditions and drop
  modifiers (explosion survival, door/slab block-state checks, drop counts, random chances) were skipped.
  The 26.3 builds now ship every loot table in the new format. 26.1.2 and 26.2 were not affected.
- The Glacite Wisp and Xertz Forager now have 26.3-format loot tables too, so every creature's loot
  table ships in both formats. They still drop nothing; no gameplay change.

## [0.3.0-alpha.1] - 2026-10-11

Quest-pack release. Requires **Neroland Core 1.15.0** (offline-capable player gates and the sourced
threshold bus). No entity, item id, loot or config change.

### Added

- **NeroQuests quest pack** — chapter `nerocreatures:bestiary` "Bestiary", 14 quests (13 player, 1 server
  world-log quest `cindara_quiet` for the first Cinder Tyrant defeat on Cindara) — that appears in the
  quest book when NeroQuests is installed; inert otherwise, no dependency. Every quest is hidden until
  Core's `nerolandcore:reached_orbit` gate is open, because every creature spawns in Nerospace biomes.
- **Milestone gates** (player-scope Neroland Core *record* gates, `data/nerocreatures/neroland_gates/`):
  `nerocreatures:first_tame` opens the moment a pet is tamed and `nerocreatures:first_deploy` the moment a
  Terraforming Drone is deployed. They have no `requires`, gate nothing and are read by the pack's
  `new_friend` and `drone_deployed` quests. Rows live in Core's progression store and are erased through
  Core's shared erasure hook; nothing new is logged.
- **Entity-type family tags** so a quest or another mod can say "any mechanical foe":
  `nerocreatures:mechanical` (rogue drone, rogue android), `crystalline` (crystal golem), `void` (void
  crawler) and `stalkers` (lunar stalker). The pack's `scrapyard` quest counts kills on `mechanical`.

### Changed

- `nerocreatures:boss_pressure` crossings are now published with `nerocreatures` as the source mod id on
  Core 1.15.0's sourced bus. Channel, scope (dimension id), value and threshold semantics are unchanged.
- Neroland Core pin raised from 1.13.0 to 1.15.0; the manifests' version floor follows it.

## [0.2.1-alpha.1] - 2026-10-11

Data-only release. No entity, item id, loot or config change.

### Added

- **Drop-family item tags** so other mods can key on a whole family instead of single items:
  `nerocreatures:drops/organic` (stalker hide, stalker sinew, worm chitin), `drops/mineral` (ore slurry,
  refined crystal), `drops/tech` (salvaged circuitry, android core), `drops/exotic` (plasma cell, void
  essence) and `drops/loot` (contraband), plus `nerocreatures:drops` covering all five. The Apex Trophy is
  deliberately in none of them.
- The Apex Trophy is added to the optional `neroeconomy:never_commodity` item tag (`"replace": false`), so
  an economy mod never prices it by id. The tag is ignored when no such mod is installed.

## [0.2.0-alpha.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** Every NeroCreatures recipe uses a vanilla recipe type, which EMI shows on its own, so
  nothing needed a plugin. The build now compiles against the community EMI Unofficial Port (Unstable),
  the only EMI build for Minecraft 26.x, and dev clients load it with `-PwithEmi` (default runs stay
  JEI-only). EMI stays optional.

## [0.1.0-alpha.1] - 2026-09-20

Minecraft **26.3** support, plus the changes previously listed under *Unreleased*.

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
- Output lands in `screenshots/nerocreatures/`, which is the folder the repository's screenshot gallery
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

### Minecraft 26.3

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.
- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- Requires **Neroland Core 1.13.0** (was `1.10.0`) — the first Core release with a 26.3
  build. The loader range still derives from the pin (`[${nerolandcore_version},2.0)`).
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- 26.3 API differences are handled with Stonecutter blocks: `PoseStack#rotate` (was `mulPose`), the new `Prediction` argument on `drop` / `placeItemBackInInventory`, `setPermanentlyInvulnerable`, and similar renames.
- Blink-strike teleports pass 26.3's landing predicate (no block exclusions, as before). Ground-slam knockback uses `syncVelocity` (was `hurtMarked`).
- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.
