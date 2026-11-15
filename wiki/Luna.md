# Luna

The Moon's creatures. They spawn in the `nerocreatures:habitat/luna` biome tag (the Moon's highlands,
maria and crater fields when a planet mod provides them; empty otherwise). Two of them also live in the
overworld, so a server without a planet mod still meets them.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Lumen Moth | Tameable (glow berries) | A living lantern: a tamed moth lights the air around its owner with one moving light block (air only, always cleaned up). Wild moths gather at light. | Hands-free light, and it reveals hidden and burrowed creatures nearby. | Lumen Dust |
| Echo Shrike | Hostile | Blind: it hunts by sound. Sprinting, mining and landing draw it; a sneaking player is invisible to it. It lunges when it hears you close. | A threat you beat by playing differently. | Resonant Membrane |
| Lode Tortoise | Livestock (moss) | Grows ore nodules on its shell from the ore it grazes near; mine them off with a pickaxe (one durability) without harming it and they regrow. Wild ones often already carry some. Once nodules show, the ore family stays until harvest. | Slow, renewable, low-yield ore without strip-mining. | Lode Scute |

## Spawning

| Creature | Where | Weight | Group |
| -------- | ----- | ------ | ----- |
| Lumen Moth | Luna | 10 | 1–3 |
| Lumen Moth | Overworld dark forests (and pale gardens), at night, in the dark, in the air | 6, 15% per attempt | 1–3 |
| Echo Shrike | Luna, block light 7 or less | 14 | 1–2 |
| Lode Tortoise | Luna | 6 | 1–2 |
| Lode Tortoise | Overworld deep caves: Y 0 or below, no sky, on stone or deepslate | 3, 5% per attempt | 1 |

Overworld moths and tortoises share the `overworldSpawnCap` (default 12) and switch off with
`overworldSpawns`. Wild overworld moths leave at daybreak when no player is near.

## Config

| Key | Default | Meaning |
| --- | ------- | ------- |
| `lumenMothLight` | true | Tamed moths place their light block. false = glow particles and Night Vision for the owner. |
| `lodeTortoiseNodules` | true | Tortoises grow and yield nodules at all. |
| `lodeTortoiseNoduleTicks` | 36000 | Grazing ticks for a common-ore nodule (rare ores take 4x). |
| `maxNodulesPerHarvest` | 4 | The most items one harvest yields. 0 = nothing. |

The existing Lunar Stalker hunts here too: now its pack howls when it shares a target, and the last
survivor of a pack runs when badly hurt. See the [Bestiary](Bestiary.md) and [Drop Map](Drop-Map.md).
