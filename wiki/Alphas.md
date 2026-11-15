# Alphas

Any hostile NeroCreatures creature (not bosses, and not anything in the
`nerocreatures:alpha_excluded` entity tag) can spawn as an **alpha**: a rarer, larger, tougher version
of itself.

- **How rare:** 2% of eligible natural spawns by default (spawn eggs, summons and structures never
  roll), scaled by `alphaChanceMultiplier`.
- **How many:** at most 4 loaded per dimension by default (`alphaMaxPerDimension`; -1 keeps the data
  pack's value, 0 stops new alphas).
- **What changes:** 30% bigger (the vanilla `minecraft:scale` attribute, so its hitbox grows too),
  +50% max health, +25% attack damage, a crimson accent, and one extra modifier picked by weight: Brutal, Fireproof,
  Ironhide, Keen, Regenerating, Swift or Vital.
- **Reward:** killed by a player, an alpha drops one extra rare item from its species' alpha drop
  table, on top of its normal loot.

`alphasEnabled = false` stops new alphas. Alphas that already exist keep their state.

Everything is data-driven under `data/nerocreatures/nerocreatures/alpha/`: `settings.json` (chance,
cap, size, base bonuses), `modifiers/*.json` and `drops/<species>.json`.

## See also

- [Bestiary](Bestiary.md)
- [Config](Config.md)
