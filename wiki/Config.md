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

## See also

- [Spawning](Spawning.md)
- [Mounts](Mounts.md)
- [Alphas](Alphas.md)
