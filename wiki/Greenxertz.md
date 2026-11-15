# Greenxertz

The jungle world's creatures spawn in the `nerocreatures:habitat/greenxertz` biome tag (empty without
a planet mod). Drift Leviathans also use `nerocreatures:habitat/gas_giant`, empty until a pack or
planet mod fills it.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Spore Drifter | Tameable flyer (bone meal; egg birth) | Drifts over crops and gives one crop below it an extra growth tick every 4 seconds (at most 4 per 4 seconds in one area). | Bottle its spores with a glass bottle (refills every 5 minutes; wild: anyone, tamed: owner only): Drifter Spores grow a crop twice like bone meal and give the bottle back. | Bone meal |
| Bloom Host | Hostile | Kills animals and raises up to 3 short-lived **sporelings** from them (30 seconds, no drops, never saved; at most 24 per dimension). | Drops Bloom Hearts. | Bloom Heart |
| Mossback | Livestock herd (fern, short grass; live birth) | Grazes grass and bone-meals it, at most once a minute. Herds flee predators together. | Leather-class hide and meat. | Mossback Hide, Raw Mossback |
| Drift Leviathan | Passive sky giant | A walkable back: stand on its deck and it holds still while any player is aboard. Grows barnacles over a day; wild ones arrive half to fully grown, and a leviathan is saved with its chunk. | Shear the barnacles when grown (shears, 2–3 Leviathan Barnacles). A rare sight: at most 2 loaded per dimension. | Leviathan Barnacle |

## Sporelings and your animals

Sporelings rise only from animals in `#nerocreatures:sporeling_hosts` (cow, pig, sheep, chicken, rabbit,
goat, camel, Mossback, Rime Yak) that the host killed itself. **Owner-friendly by default:** a Bloom
Host never hunts or raises a player, a tamed or owned animal, a named or leashed one, a boss or a
villager, and with `bloomHostInfectsLivestock` off (the default) it also leaves bred livestock alone:
babies, animals in love and animals that bred in the last five minutes. Wild herds are fair game.
`bloomHostSporelings` turns sporelings off entirely. Sporelings are small (half a block) and only bite.

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Spore Drifter | 10 | 1–3, in the air |
| Bloom Host | 10 | 1–2, dark ground |
| Mossback | 12 | 2–4 |
| Drift Leviathan | 1 (2 on gas giants) | 1 (**gated**: at most 2 loaded per dimension) |

## Config

`sporeDrifterGrowth`, `bloomHostSporelings`, `mossbackFertilises` (all true) and
`bloomHostInfectsLivestock` (false). See [Config](Config.md). The Bloom Sovereign boss will also live
here.
