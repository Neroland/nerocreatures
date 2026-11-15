# Config

NeroCreatures' settings live in `nerocreatures.properties`, through Neroland Core's config system.
Every gameplay key is **server-authoritative**: the server's value wins. Spawning keys are listed on
[Spawning](Spawning.md) and the ownership caps on [Pets & Drones](Pets-and-Drones.md); this page lists
the keys added in 0.5.0.

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

## See also

- [Spawning](Spawning.md)
- [Mounts](Mounts.md)
- [Alphas](Alphas.md)
