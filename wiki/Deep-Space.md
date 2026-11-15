# Deep Space

Deep space has one creature in 0.5.0, the **Void Manta**, a flying mount. It spawns in the
`nerocreatures:habitat/deep_space` biome tag, and only on an off-Earth dimension type (so a void
superflat or skyblock overworld never gets one), at any light, alone or as a breeding pair.

| Creature | Kind | Ability | Use to you | Drop |
| -------- | ---- | ------- | ---------- | ---- |
| Void Manta | Mount (chorus fruit; egg birth) | Flies freely in low or zero gravity, glides everywhere else (never takes fall damage). Its jump-bar ability is a **blink**: up to 8 blocks along your look, 10 ticks after you release. | A mount that crosses gaps and space stations. | Leather 0–2, Void Essence 0–1 |

## Flight

- **Free flight** (climb and dive with your look, hover with no input) where the mount's
  `minecraft:gravity` is 0.06 or less (three quarters of normal, so any planet mod's low or zero
  gravity), or in a deep-space biome on an off-Earth dimension.
- **Glide** everywhere else: it cannot climb, and in the air it sinks at most 0.12 blocks a tick.
- **Blink** (35 stamina, Thrifty 20% less): stops 1.5 blocks short of the first solid block, never
  passes through blocks and never enters an unloaded chunk; a blink of under a block fizzles.
- While you fly freely the server marks the mount as not falling, so a server with
  `allow-flight=false` does not kick you for hovering.

Taming, the saddle, stamina and panniers are the same as every mount: see [Mounts](Mounts.md).

## Spawning

| Creature | Weight | Group |
| -------- | ------ | ----- |
| Void Manta | 4 | 1–2 |

## Config

No key of its own; the mount keys apply (`mountAbilityCost`, `mountStaminaRegenTicks`,
`mountOwnerOnly`, `maxMountsPerPlayer`). See [Config](Config.md).
