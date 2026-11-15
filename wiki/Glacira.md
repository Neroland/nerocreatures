# Glacira

The frozen world's creatures spawn in the `nerocreatures:habitat/glacira` biome tag (the snowfields,
methane shores and ice spires when a planet mod provides them; empty otherwise), at any light.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Rime Yak | Livestock (sweet berries; live birth) | A cold aura every 2 seconds, 6 blocks: puts out burning things, cools players, slows hostile mobs (never to damage) and freezes one still water source at a time into frosted ice that melts back. | A walking cold store: shear it for Rime Wool (shears, regrows in `rimeYakWoolRegrowTicks`), milk it with a bucket for Coolant Milk (`rimeYakMilkCooldownTicks` between milkings). | Rime Wool |
| Frost Lurker | Hostile ambusher | Lies buried in snow or ice, silent and unseen (prey animals do not notice it either). A player or wild grazer stepping within 1.5 blocks makes it erupt: a 12-tick telegraph, then a freezing bite if you are still in reach. | Drops Cryo Fangs. Leather armour keeps the frost off. | Cryo Fang |

## The yak's aura

Every 40 ticks, within 6 blocks and at most 12 entities: burning entities are put out, hostile mobs
are chilled to just short of a full freeze (slowed, never hurt), and players are cooled (a hook other
mods can use for a heat system). Water freezing is capped at one block per pass and 8 per yak per
minute, only with `rimeYakFreezesWater` on and only where the world allows it (`mobGriefing`, the
loader's place check). Lurkers and other yaks are exempt from the chill.

A fresh or newborn yak is in full wool. The wool shows its regrowth in four stages; only full wool can
be shorn. Shearing yields 2 wool (more with the Bountiful trait, at most 6).

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Rime Yak | 10 | 2–4 |
| Frost Lurker | 8 | 1 |

## Config

`rimeYakFreezesWater` (true), `rimeYakWoolRegrowTicks` (6000) and `rimeYakMilkCooldownTicks` (2400).
See [Config](Config.md). The Rime Matriarch boss will also live here.
