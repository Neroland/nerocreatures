# Drop Map

The canonical answer to "what does this creature drop, and what is it *for*". This page is the
shared contract between NeroCreatures and the mods that consume its materials — if a recipe
elsewhere in the ecosystem wants a creature drop, it references the **tag** listed here, never the
item id.

> **Status:** every item, texture and tag below exists and is registered, and **all eleven drops are
> now obtainable in survival** — the four hostile fauna, the Crystal Golem, the Space Pirate, the
> two Rogue Androids and now the first planet boss have all shipped with the loot tables in
> [Shipped drop rates](#shipped-drop-rates) below. The tameables are the first **consumers**: see
> [Consumption inside NeroCreatures](#consumption-inside-nerocreatures). See the
> [Bestiary](Bestiary.md) for where each creature lives, and [Bosses](Bosses.md) for the Apex
> Trophy's two very different payout paths.

## The table

| Creature | Drop | Item id | Tags | Intended consumers | Recipe intent |
| -------- | ---- | ------- | ---- | ------------------ | ------------- |
| Void Crawler | Void Essence | `nerocreatures:void_essence` | `neroland:materials/void_essence`, `neroland:highlight/materials` | Nerotech, NeroEconomy | Exotic-tier reagent: the "impossible" ingredient in quantum/collider recipes, and a high-value trade good. |
| Lunar Stalker | Stalker Hide | `nerocreatures:stalker_hide` | `neroland:materials/stalker_hide`, `neroland:highlight/materials` | Nerospace, NeroDecor | Insulation and soft armour: suit liners, thermal padding, upholstered decor. |
| Lunar Stalker | Stalker Sinew | `nerocreatures:stalker_sinew` | `neroland:materials/stalker_sinew`, `neroland:highlight/materials` | NeroCreatures (Glacite Wisp taming), NeroLogistics | Taming reagent for the Glacite Wisp; flexible cabling and belt stock. |
| Asteroid Worm | Worm Chitin | `nerocreatures:worm_chitin` | `neroland:materials/worm_chitin`, `neroland:highlight/materials` | Nerotech, Nerospace | Heavy casing plate: machine frames and hull sections that must survive pressure. |
| Asteroid Worm | Ore Slurry | `nerocreatures:ore_slurry` | `c:dusts/ore_slurry`, `neroland:materials/ore_slurry`, `neroland:highlight/materials` | Nerotech, any ore-processing mod | Half-digested rock: feeds a washer/centrifuge step for a random ore dust. The `c:dusts` membership is what makes it work in third-party processing chains. |
| Crystal Golem | Refined Crystal | `nerocreatures:refined_crystal` | `c:gems/refined_crystal`, `neroland:materials/refined_crystal`, `neroland:highlight/materials` | Nerotech, Nerospace, NeroSecurity | Optics: lenses, sensors, laser and scanner cores. `c:gems` gives external mods a drop-in gem. |
| Plasma Slime | Plasma Cell | `nerocreatures:plasma_cell` | `neroland:materials/plasma_cell`, `neroland:highlight/materials` | Nerotech, NeroCreatures (Xertz Forager taming, Drone Shell) | Portable charge: energy-storage components; also the taming reagent for the Xertz Forager and part of the Terraforming Drone Shell. |
| Space Pirate | Contraband | `nerocreatures:contraband` | `neroland:materials/contraband`, `neroland:highlight/materials` | NeroEconomy, NeroFactions | Not a crafting input: a sell-only trade good, and a reputation lever with the factions that want it back. |
| Rogue Android | Salvaged Circuitry | `nerocreatures:salvaged_circuitry` | `neroland:materials/salvaged_circuitry`, `neroland:highlight/materials` | Nerotech, NeroLogistics, NeroSecurity, NeroCreatures (Drone Shell) | General electronics: control boards, logic units, terminal parts. The workhorse drop. |
| Rogue Android (heavy frame only) | Android Core | `nerocreatures:android_core` | `neroland:materials/android_core`, `neroland:highlight/materials` | Nerotech, NeroColonies, NeroCreatures (Drone Shell) | Rare automation brain: autocrafters, colony managers, anything that needs to "decide". Stacks to 16 — it is meant to feel scarce. |
| **Any planet boss** | Apex Trophy | `nerocreatures:apex_trophy` | `neroland:materials/apex_trophy`, `neroland:highlight/materials` | Nerotech (exotic recipes), NeroEconomy (high value) | The top of the ladder: proof of a boss kill, and the gating reagent for the ecosystem's most expensive recipes. Stacks to 8, because a trophy is meant to be counted rather than accumulated. |

## Consumption inside NeroCreatures

NeroCreatures spends four of its own drops. These are the only in-mod sinks, and they are listed
here rather than buried in the code because they are the reason two of the drops have a floor under
their value even with no other Nero mod installed.

| Drop | Spent on | How much | Consumed? |
| ---- | -------- | -------- | --------- |
| Stalker Sinew | Taming a **Glacite Wisp**; feeding a tamed one | 1 per attempt (1-in-3 tames); 1 per heal | Yes |
| Plasma Cell | Taming a **Xertz Forager**; feeding a tamed one; crafting a Drone Shell | 1 per attempt / heal; 2 per shell | Yes |
| Salvaged Circuitry | Crafting a **Terraforming Drone Shell** | 3 per shell | Yes |
| Android Core | Crafting a **Terraforming Drone Shell** | 1 per shell | Yes |

The reagent is consumed on a *failed* taming attempt as well as a successful one — but never when
the attempt is refused for being at the pet cap, which is checked first. See
[Pets & Drones](Pets-and-Drones.md).

The Drone Shell (`nerocreatures:drone_shell`) is not a material and is not in
`neroland:materials/*`; it is a crafted deployable and sits in `neroland:highlight/tools` with the
spawn eggs. It is fully recoverable — recall or kill a drone and the shell comes back — so it is a
one-off cost, not a running one.

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

## Shipped drop rates

The loot tables that exist today, hand-authored at
`data/nerocreatures/loot_table/entities/<creature>.json`. Every count is a uniform roll, and every
table is a plain data file — a pack may override any of them without touching code.

| Creature | Drop | Count per kill |
| -------- | ---- | -------------- |
| Void Crawler | Void Essence | 0–2 |
| Lunar Stalker | Stalker Hide | 1–2 |
| Lunar Stalker | Stalker Sinew | 0–1 |
| Asteroid Worm | Worm Chitin | 2–4 |
| Asteroid Worm | Ore Slurry | 1–3 |
| Plasma Slime | Plasma Cell | 0–1 |
| Crystal Golem | Refined Crystal | 1–3 |
| Space Pirate | Contraband | 0–2 |
| Space Pirate | its own worn gear | 6% per equipped slot |
| Rogue Drone | Salvaged Circuitry | 0–2 |
| Rogue Android | Salvaged Circuitry | 1–3 |
| Rogue Android | Android Core | 1, at 35% |
| Glacite Wisp | — | nothing |
| Xertz Forager | — | nothing |
| Terraforming Drone | its own Drone Shell | 1 |
| Cinder Tyrant | Apex Trophy | 1 |
| Cinder Tyrant | Refined Crystal | 6–12 |
| Cinder Tyrant | Plasma Cell | 4–8 |
| Cinder Tyrant | Void Essence | 1–3, at 50% |

The two pets drop **nothing**, deliberately: a tameable that pays out would turn a companion into a
crop. The drone returns its shell whether you recall it or it is killed, so it is never lost.

The Plasma Slime's rate looks low on purpose: a full-size slime splits twice on the way down, so one
encounter is up to three kills.

The Space Pirate's gear is not in its loot table at all — it is ordinary mob equipment with a low
vanilla drop chance, so what drops is exactly the vanilla item the pirate was wearing (a looted iron
chestplate is an iron chestplate).

## Bonus rolls

Two creatures pay a **bonus** on top of their table for how you fought them. Both are additive, both
are code rather than loot conditions, and both are scaled by `dropRateMultiplier`:

| Creature | Rule | Bonus |
| -------- | ---- | ----- |
| Crystal Golem | Killed by a player holding a `#minecraft:pickaxes` item | +1–3 Refined Crystal |
| Rogue Android | Killed during its shield-break stagger window by damage that is **not** in `#minecraft:is_fire` | +1 Salvaged Circuitry |
| Any planet boss | Killed in a **summoned** fight, per participant, split by damage share | +1–2 Apex Trophy, +2–14 Refined Crystal, +2–10 Plasma Cell — the exact rule is on [Bosses](Bosses.md#the-contribution-reward-rule) |

**Why code and not a loot-table condition.** The obvious data-driven form of the pickaxe rule is
`minecraft:match_tool`, but that condition reads the loot context's `tool` parameter — and the
*entity* loot context does not have one (it carries the damage source and the killer, not the
killer's weapon). `match_tool` therefore works for block loot and silently never matches for a mob.
Reading the killer's weapon in `dropCustomDeathLoot` is the only honest implementation. The android's
salvage rule is code for the same class of reason: it depends on the mob's own shield state, which no
loot condition can see.

The boss rule is code for a third reason again: it depends on *who was in the fight and how much they
did*, which is per-player state accumulated across a whole encounter — nothing a loot table has ever
been able to see. A boss that was simply found in the world pays no bonus at all; only a summoned
fight does.

Consequence for pack makers: **overriding a loot table does not remove these bonuses.** Set
`dropRateMultiplier` to 0 to switch every bonus roll off while leaving the base tables alone.

## Rate scaling

Loot tables are hand-authored per creature and are the base rates. The
`dropRateMultiplier` config value scales the **bonus** rolls NeroCreatures adds on top of a table,
never the table itself — so a data pack that overrides a loot table keeps full, predictable control
of the base drop. The two rules above are the bonus rolls that exist today.

## Highlights

Every drop is in `neroland:highlight/materials`, so Neroland Core's client-side item highlighting
gives them all the shared "crafting material" slot border. See Core's item-highlight documentation
for the colour key.
