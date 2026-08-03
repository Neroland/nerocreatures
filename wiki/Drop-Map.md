# Drop Map

The canonical answer to "what does this creature drop, and what is it *for*". This page is the
shared contract between NeroCreatures and the mods that consume its materials — if a recipe
elsewhere in the ecosystem wants a creature drop, it references the **tag** listed here, never the
item id.

> **Status:** the items, textures and tags below all exist and are registered. The creatures that
> drop them arrive stage by stage, and their loot tables are hand-authored alongside them. Until a
> creature ships, its drop is obtainable in creative only.

## The table

| Creature | Drop | Item id | Tags | Intended consumers | Recipe intent |
| -------- | ---- | ------- | ---- | ------------------ | ------------- |
| Void Crawler | Void Essence | `nerocreatures:void_essence` | `neroland:materials/void_essence`, `neroland:highlight/materials` | Nerotech, NeroEconomy | Exotic-tier reagent: the "impossible" ingredient in quantum/collider recipes, and a high-value trade good. |
| Lunar Stalker | Stalker Hide | `nerocreatures:stalker_hide` | `neroland:materials/stalker_hide`, `neroland:highlight/materials` | Nerospace, NeroDecor | Insulation and soft armour: suit liners, thermal padding, upholstered decor. |
| Lunar Stalker | Stalker Sinew | `nerocreatures:stalker_sinew` | `neroland:materials/stalker_sinew`, `neroland:highlight/materials` | NeroCreatures, NeroLogistics | Taming reagent for alien pets; flexible cabling and belt stock. |
| Asteroid Worm | Worm Chitin | `nerocreatures:worm_chitin` | `neroland:materials/worm_chitin`, `neroland:highlight/materials` | Nerotech, Nerospace | Heavy casing plate: machine frames and hull sections that must survive pressure. |
| Asteroid Worm | Ore Slurry | `nerocreatures:ore_slurry` | `c:dusts/ore_slurry`, `neroland:materials/ore_slurry`, `neroland:highlight/materials` | Nerotech, any ore-processing mod | Half-digested rock: feeds a washer/centrifuge step for a random ore dust. The `c:dusts` membership is what makes it work in third-party processing chains. |
| Crystal Golem | Refined Crystal | `nerocreatures:refined_crystal` | `c:gems/refined_crystal`, `neroland:materials/refined_crystal`, `neroland:highlight/materials` | Nerotech, Nerospace, NeroSecurity | Optics: lenses, sensors, laser and scanner cores. `c:gems` gives external mods a drop-in gem. |
| Plasma Slime | Plasma Cell | `nerocreatures:plasma_cell` | `neroland:materials/plasma_cell`, `neroland:highlight/materials` | Nerotech, NeroCreatures | Portable charge: energy-storage components; also the taming reagent for the plasma-adapted pet. |
| Space Pirate | Contraband | `nerocreatures:contraband` | `neroland:materials/contraband`, `neroland:highlight/materials` | NeroEconomy, NeroFactions | Not a crafting input: a sell-only trade good, and a reputation lever with the factions that want it back. |
| Rogue Android | Salvaged Circuitry | `nerocreatures:salvaged_circuitry` | `neroland:materials/salvaged_circuitry`, `neroland:highlight/materials` | Nerotech, NeroLogistics, NeroSecurity | General electronics: control boards, logic units, terminal parts. The workhorse drop. |
| Rogue Android (heavy) | Android Core | `nerocreatures:android_core` | `neroland:materials/android_core`, `neroland:highlight/materials` | Nerotech, NeroColonies | Rare automation brain: autocrafters, colony managers, anything that needs to "decide". Stacks to 16 — it is meant to feel scarce. |

## Rules for consumers

1. **Reference tags, never ids.** `neroland:materials/<drop>` is the ecosystem-internal "is this
   that material" tag; `c:` memberships are the cross-mod interop surface and the right choice for
   a recipe ingredient.
2. **Every entry is optional.** All tag files ship with `"replace": false` and `"required": false`
   entries, so a pack without NeroCreatures resolves them to empty rather than failing to load.
3. **Empty is a valid answer.** A recipe gated on a NeroCreatures drop simply cannot be crafted
   when the mod is absent. Do not write logic that assumes a member exists.
4. **Balance changes go through this page.** Drop rates and consumers are the shared lever between
   NeroCreatures and every mod that spends these materials; change the table first, then the code.

## Rate scaling

Loot tables are hand-authored per creature and are the base rates. The
`dropRateMultiplier` config value scales the **bonus** rolls NeroCreatures adds on top of a table,
never the table itself — so a data pack that overrides a loot table keeps full, predictable control
of the base drop.

## Highlights

Every drop is in `neroland:highlight/materials`, so Neroland Core's client-side item highlighting
gives them all the shared "crafting material" slot border. See Core's item-highlight documentation
for the colour key.
