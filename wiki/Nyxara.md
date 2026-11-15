# Nyxara

The twilight world's creatures spawn in the `nerocreatures:habitat/nyxara` biome tag (dayside,
twilight band and nightside when a planet mod provides them; empty otherwise), at any light. Both
are crystal-bodied (`#nerocreatures:crystalline`).

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Prism Stag | Livestock, neutral (amethyst shards; live birth) | Grows and sheds crystal antlers (2 per shed, every 12000 loaded ticks; more with Bountiful, at most 4). At night a herd of 3+ adults lights up and gives nearby players Regeneration I. | Renewable crystal: a Prism Antler cuts into 2 Refined Crystal. Fights back only if hit. | Prism Antler |
| Shardback | Hostile | A mirror fan reflects projectiles and blocks hits from the front (140 degrees). It telegraphs a charge (20 ticks of pawing, plates folding), then runs. | Hit it from behind, or while it charges and recovers. Drops Mirror Scales. | Mirror Scale |

## Details

- The stag's regrow time is data: `product_interval_ticks` in `creature_data/species.json` (12000).
- The herd aura is `prismStagHerdAura` (true): every 4 seconds, one stag per herd gives Regeneration I
  for 5 seconds to at most 8 players within 8 blocks. Nothing about a player is stored.
- The Shardback reflects at most 6 projectiles per second; past that they are blocked and drop. Fall,
  lava and void damage always land.

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Prism Stag | 8 | 3–4 |
| Shardback | 12 | 1–2 |

## Config

`prismStagHerdAura` (true). See [Config](Config.md). The Facet Colossus boss will also live here.
