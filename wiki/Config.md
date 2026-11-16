# Config

NeroCreatures' settings live in `nerocreatures.properties`, through Neroland Core's config system.
Every gameplay key is **server-authoritative**: the server's value wins. The one exception is
`telemetryEnabled`, which each client and server decides for itself. This page lists every key;
[Spawning](Spawning.md), [Pets & Drones](Pets-and-Drones.md), [Habitat Blocks](Habitat-Blocks.md) and
[Gear](Gear.md) explain the ones that belong to them in more depth.

## General

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `telemetryEnabled` | true | | Opt-out crash reporting for this mod only. Local to each client and server; takes effect on restart. See [Telemetry](Telemetry.md). |
| `spawnsEnabled` | true | | Master switch for natural spawning. false = creatures come only from spawn eggs, structures and summons. |
| `globalSpawnWeightMultiplier` | 1.0 | 0-10 | Multiplies every creature's spawn weight. 0 = no natural spawns. |
| `maxCreaturesPerChunk` | 8 | 0-128 | Most NeroCreatures mobs alive in one chunk. A full chunk gets no new spawn. |
| `maxCreaturesPerDimension` | 200 | 0-10000 | Most NeroCreatures mobs alive in one dimension. |
| `hostileAggressionMultiplier` | 1.0 | 0-5 | Scales hostile detection range and attack pressure. 0 = hostiles only retaliate. |
| `pirateVillagerAggression` | false | | true = Space Pirates also attack villagers and colony NPCs (`nerocreatures:pirate_raid_targets`). |
| `dropRateMultiplier` | 1.0 | 0-10 | Scales the bonus loot rolls added on top of each loot table; the table itself is untouched. |
| `naturalBossSpawnsEnabled` | true | | false = the Cinder Tyrant no longer turns up on its own; bosses come only from the Apex Altar or a summon. |
| `bossDifficultyMultiplier` | 1.0 | 0.1-10 | Scales boss damage, phase pacing and add budgets. |
| `bossHpMultiplier` | 1.0 | 0.1-10 | Scales boss maximum health. |
| `maxPetsPerPlayer` | 3 | 0-64 | Pets one player may have tamed at once. 0 = no taming. |
| `maxDronesPerPlayer` | 2 | 0-64 | Terraforming Drones one player may have deployed at once. 0 = no drones. |

## Mounts and eggs

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `mountOwnerOnly` | true | | Only the owner may ride. false = anyone may ride; only the owner saddles, equips, opens panniers or gives orders. |
| `maxMountsPerPlayer` | 2 | 0-16 | Mounts one player may own. 0 = no mounts. Separate from the pet cap. |
| `maxIncubatingEggsPerPlayer` | 8 | 0-64 | Eggs one player may have incubating. 0 = none. |
| `mountStaminaRegenTicks` | 10 | 1-200 | Ticks per point of stamina regained. |
| `mountAbilityCost` | 35 | 0-100 | Stamina the mount's ability costs. |

## Alphas

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `alphasEnabled` | true | | false = no new alphas (existing ones keep their state). |
| `alphaChanceMultiplier` | 1.0 | 0-10 | Scales the alpha chance from the data pack. 0 = no new alphas. |
| `alphaMaxPerDimension` | -1 | -1-64 | Most alphas loaded per dimension. -1 = the data pack's value (4). 0 = no new alphas. |

## Overworld spawns

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `overworldSpawns` | true | | Lumen Moths, Lode Tortoises and Static Swarms also spawn in the overworld. |
| `overworldSpawnCap` | 12 | 0-64 | Most of those three alive in the overworld at once. |

## Tick budget

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `farTickDistance` | 48 | 16-256 | A creature with no player this close is "far". |
| `farTickInterval` | 4 | 1-20 | A far creature runs its own AI work on one tick in this many. 1 = never slow down. Movement and physics are not slowed. |

Measure the cost with `/nerocreatures budget` ([Commands](Commands.md)).

## Creature abilities that change the world

Each is on by default. Turning one off makes that creature use its harmless fallback instead. Every
one of them also respects `mobGriefing`, the loader's block-place checks and, for an owned creature,
its owner's own permission to build there.

`lumenMothLight`, `lodeTortoiseNodules`, `dustDevilThrowsItems`, `magmaWaderSlag`,
`rimeYakFreezesWater`, `mossbackFertilises`, `sporeDrifterGrowth`, `bloomHostSporelings`,
`magnetCrabSteals`, `magnetCrabCollects`, `slagBeetleSmelting`, `scrapHulkAssembly`,
`bossArenaBlocks`, `terraformingDroneWork`, `cacheMimicSpawns`, `staticSwarmDrain` (all true), and
`strataHoundRange` (24, 4-48 blocks).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `lodeTortoiseNoduleTicks` | 36000 | 1200-240000 | Grazing ticks for a Lode Tortoise to grow a common-ore nodule (rare ores take 4x). New tortoises use the new value. |
| `maxNodulesPerHarvest` | 4 | 0-16 | The most items one nodule harvest yields. 0 = nothing. |
| `rimeYakWoolRegrowTicks` | 6000 | 1200-72000 | Ticks for a shorn Rime Yak to regrow full wool. |
| `rimeYakMilkCooldownTicks` | 2400 | 200-72000 | Ticks between milkings of one Rime Yak. |
| `prismStagHerdAura` | true | | Prism Stag herds (3+ adults) give nearby players a short Regeneration I at night. false = no aura; antlers still grow and shed. |
| `bloomHostInfectsLivestock` | false | | true = Bloom Hosts also hunt and raise sporelings from bred livestock (babies, animals in love or that bred recently). Tamed, owned, named and leashed animals are always spared. |

## Bosses and the Apex Altar

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `apexAltarCooldownTicks` | 12000 | 0-1008000 | Ticks an Apex Altar recharges after it summons a boss, per altar (12000 = 10 minutes). 0 = no cooldown; a live fight from that altar still blocks it. |

`bossArenaBlocks` (above) gates the Rime Matriarch's ice pillars and frozen water and the Facet
Colossus's prism crystals; `bloomHostSporelings` gates the Bloom Sovereign's sporeling adds. The boss
health and difficulty multipliers are on [Bosses](Bosses.md#configuration).

## Companion app (link module)

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `linkRecallCooldownSeconds` | 10 | 0-600 | Seconds a player waits between two companion-app recalls (`pet_recall`). 0 = only the bridge's own per-device limit. |
| `linkCommandCooldownMillis` | 1000 | 0-60000 | Milliseconds a player waits between two companion-app orders (`pet_command`). 0 = only the bridge's own per-device limit. |

See [Link module](Link-Module.md#validation-and-refusals).

## Habitat blocks and gear

The habitat-block keys are on [Habitat Blocks](Habitat-Blocks.md#config) and the gear keys on
[Gear](Gear.md): `feedingTroughIntervalTicks`, `incubatorHatchTicks`, `incubatorPoweredHatchTicks`,
`incubatorEnergyPerTick`, `lureBeaconBaitTicks`, `lureBeaconWeightMultiplier`, `sonicWardFuelTicks`,
`nestHealIntervalTicks`, `mirrorShieldCooldownTicks`, `lurkerCloakSilences`, `blinkCharmCooldownTicks`,
`blinkCharmRange`, `rimeFlaskCooldownTicks`, `heatGlandGreavesLavaTicks`,
`heatGlandGreavesCooldownTicks`, `gravitonBootsGravityPercent`, `resonantWhistleCooldownTicks`,
`resonantWhistleRange`, `chitinBulwarkCooldownTicks`, `bloomAmuletCooldownTicks`.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `spacePirateTakesContraband` | true | | Space Pirates pick up loose contraband (max 16, dropped on death). Also needs `mobGriefing`. false = they ignore items. |

## See also

- [Spawning](Spawning.md)
- [Mounts](Mounts.md)
- [Alphas](Alphas.md)
