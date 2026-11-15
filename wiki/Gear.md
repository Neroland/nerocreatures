# Gear

Ten creature-gear items, each made from a habitat drop, plus the Capture Pod. Gear does not stack,
is not enchantable and keeps its effect on a vanilla item cooldown (never shorter than one second).
**Passive** gear works when worn in its slot (Minecraft 1.21.2 and newer) or held in either hand
(the only way on 1.21.1). **Active** gear works on use. Every number is a server config key.

| Item | Made from | Kind | Effect | Config |
| --- | --- | --- | --- | --- |
| Mirror Shield | Mirror Scale + Shield (or Colossus Facet + Shield) | held | Reflects one incoming projectile per cooldown. | `mirrorShieldCooldownTicks` 200 |
| Lurker Cloak | Cryo Fang + Rime Wool | chest | Silent to sound hunters (Echo Shrikes) while walking, landing and mining; sprinting is still heard. | `lurkerCloakSilences` true |
| Blink Charm | Charged Sand + Void Essence + Ender Pearl | use | Short look-direction teleport. A blink with no room costs no cooldown. | `blinkCharmCooldownTicks` 100, `blinkCharmRange` 8 |
| Rime Flask | Coolant Milk + Glass Bottle + Snowball (or 4 from one Matriarch Frostcore) | use | 10 s Fire Resistance and puts you out. Reusable. | `rimeFlaskCooldownTicks` 1200 |
| Heat-gland Greaves | Heat Gland x2 + Leather Leggings + Magma Cream (or one Tyrant Ember + Leather Leggings) | legs | Walk on lava for a few seconds, then recharge. | `heatGlandGreavesLavaTicks` 100, `heatGlandGreavesCooldownTicks` 600 |
| Graviton Boots | Graviton Node x2 + Leather Boots + Feather | feet | Lower gravity (sneak for normal) and 4 extra blocks of safe fall. | `gravitonBootsGravityPercent` 50 |
| Resonant Whistle | Resonant Membrane x2 + Copper | use | Calls up to 8 of your own loaded pets and mounts in this dimension within range; sitting ones stay. | `resonantWhistleCooldownTicks` 600, `resonantWhistleRange` 48 |
| Chitin Bulwark | Slag Carapace + Worm Chitin + Lode Scute | held | +0.4 knockback resistance while held; braces (Absorption II, 10 s) at 40% health or less. | `chitinBulwarkCooldownTicks` 1800 |
| Bloom Amulet | Bloom Heart + String + Gold | held | Regeneration I for 5 s at 60% health or less; never stacks. | `bloomAmuletCooldownTicks` 900 |
| Static Jar | bottling a weakened Static Swarm | thrown | Disables mechanical creatures for 5 s. See [Wrecks & Pirates](Wrecks-and-Pirates.md). | |

## Capture Pod

Crafted from glass, iron and an Ender Pearl, or cheaply from a Leviathan Barnacle, an Ender Pearl and
iron. Use it on a small tameable creature to put it in the pod; use the filled pod on a block to let it
out. It holds one creature (stacks to 1).

- **What fits:** tameable creatures in `#nerocreatures:capturable` (the small pets, Strata Hound and
  Lode Tortoise), no larger than 1.2 x 1.6 blocks. Never hostile creatures, bosses, alphas, mounts,
  gallery exhibits, or a creature that is leashed, riding or ridden.
- **Whose:** a wild creature by anyone; a tamed one only by its owner.
- **What is kept:** the creature itself, its traits and whether it was tamed. **No owner, UUID,
  position or nest is stored** in the pod.
- **Release:** a tamed creature becomes the releaser's pet (refused at your pet cap; the pod keeps
  it), livestock are marked as husbanded, and nothing released despawns. Capture is a single step:
  the pod is filled before the creature is removed, and emptied only once the release succeeded.
- A filled pod can move its creature into a [Terrarium](Habitat-Blocks.md#terrarium).

## Space Pirates and contraband

Space Pirates pick up loose Contraband (at most 16) and drop all of it when they die. Gated by
`spacePirateTakesContraband` (true) and `mobGriefing`.

## See also

- [Habitat Blocks](Habitat-Blocks.md)
- [Drop Map](Drop-Map.md)
