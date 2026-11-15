# Drop Map

The canonical answer to "what does this creature drop, and what is it *for*". This page is the
shared contract between NeroCreatures and the mods that consume its materials — if a recipe
elsewhere in the ecosystem wants a creature drop, it references the **tag** listed here, never the
item id.

> **Status:** every item, texture and tag below exists and is registered, and **every drop is
> obtainable in survival** — the original fauna, the Crystal Golem, the Space Pirate, the Rogue
> Androids, the first planet boss and the 0.5.0 habitat creatures have all shipped with the loot tables in
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
| Lumen Moth | Lumen Dust | `nerocreatures:lumen_dust` | `neroland:materials/lumen_dust`, `c:dusts/lumen`, `neroland:highlight/materials` | NeroCreatures (Glowstone Dust now; Field Scanner lens later), NeroDecor, Nerotech | Light: glowing decor, lamp and display components. |
| Echo Shrike | Resonant Membrane | `nerocreatures:resonant_membrane` | `neroland:materials/resonant_membrane`, `neroland:highlight/materials` | NeroCreatures (Resonant Whistle, Sonic Ward: coming with the gear and habitat blocks), NeroNotes, NeroSecurity | Sound: instrument reeds and speakers, acoustic sensors. |
| Lode Tortoise | Lode Scute | `nerocreatures:lode_scute` | `neroland:materials/lode_scute`, `neroland:highlight/materials` | NeroCreatures (Incubator casing: coming with the habitat blocks), Nerotech, Nerospace | Mineral plate: machine and suit plating. Also shed on 20% of nodule harvests. |
| Dust Devil | Charged Sand | `nerocreatures:charged_sand` | `neroland:materials/charged_sand`, `c:dusts/charged_sand`, `neroland:highlight/materials` | NeroCreatures (Field Scanner sensor: coming with the scanner), Nerotech, Nerospace | Static charge: capacitor and battery dust, a static-charge reagent. |
| Slag Beetle | Slag Carapace | `nerocreatures:slag_carapace` | `neroland:materials/slag_carapace`, `neroland:highlight/materials` | NeroCreatures (Chitin Bulwark: coming with the gear), Nerotech, Nerospace | Heat-proof plating: shield facing, furnace linings, heat shields. |
| Magma Wader | Heat Gland | `nerocreatures:heat_gland` | `neroland:materials/heat_gland`, `neroland:highlight/materials` | NeroCreatures (Heat-gland Greaves: coming with the gear), Nerotech, Nerospace | Thermal reagent: short lava walking in gear; heat exchangers and re-entry shielding elsewhere. |
| Rime Yak (shearing) | Rime Wool | `nerocreatures:rime_wool` | `neroland:materials/rime_wool`, `neroland:highlight/materials` | NeroCreatures (Lurker Cloak: coming with the gear), Nerospace, Nerotech | Insulation: cloak lining; cold-world suits and cryo-line lagging elsewhere. |
| Rime Yak (milking, bucket) | Coolant Milk | `nerocreatures:coolant_milk` | `neroland:materials/coolant_milk`, `neroland:highlight/materials` | NeroCreatures (Rime Flask: coming with the gear), Nerotech, Nerospace | Cooling reagent: machine coolant and thermal loops. Stacks to 16; the bucket comes back when crafted with. |
| Frost Lurker | Cryo Fang | `nerocreatures:cryo_fang` | `neroland:materials/cryo_fang`, `neroland:highlight/materials` | NeroCreatures (Lurker Cloak: coming with the gear), Nerotech, Nerospace | Cryogenic reagent: freezers and cryo storage. |
| Spore Drifter (bottling) | Drifter Spores | `nerocreatures:drifter_spores` | `neroland:materials/drifter_spores`, `neroland:highlight/materials` | NeroCreatures (use: two bone-meal growths), NeroAgriculture | Renewable fertiliser bottled from a pet. |
| Bloom Host | Bloom Heart | `nerocreatures:bloom_heart` | `neroland:materials/bloom_heart`, `neroland:highlight/materials` | NeroCreatures (Bloom Amulet: coming with the gear), siblings | Organic regen catalyst. |
| Mossback | Mossback Hide | `nerocreatures:mossback_hide` | `neroland:materials/mossback_hide`, `c:leathers`, `neroland:highlight/materials` | NeroCreatures (Creature Saddle, alternative recipe), any leather recipe | Leather-class hide. |
| Mossback | Raw / Cooked Mossback | `nerocreatures:raw_mossback`, `nerocreatures:cooked_mossback` | `c:foods/raw_meat`, `c:foods/cooked_meat` | Food | Livestock meat (smelt, smoke or campfire-cook). |
| Drift Leviathan (shearing) | Leviathan Barnacle | `nerocreatures:leviathan_barnacle` | `neroland:materials/leviathan_barnacle`, `neroland:highlight/materials` | NeroCreatures (Terrarium: coming with the habitat blocks), Nerospace, Nerotech | Rare renewable: hull sealant, organic polymer. |
| Prism Stag (shedding) | Prism Antler | `nerocreatures:prism_antler` | `neroland:materials/prism_antler`, `neroland:highlight/materials` | NeroCreatures (2 Refined Crystal; Facet Colossus sigil later), Nerotech, Nerospace | Renewable optical crystal, shed by living stags; lenses and resonators elsewhere. |
| Shardback | Mirror Scale | `nerocreatures:mirror_scale` | `neroland:materials/mirror_scale`, `neroland:highlight/materials` | NeroCreatures (Mirror Shield: coming with the gear), Nerotech, Nerospace | Reflective plating: solar mirrors and beam relays. |
| Gravity Leech | Graviton Node | `nerocreatures:graviton_node` | `neroland:materials/graviton_node`, `neroland:highlight/materials` | NeroCreatures (Graviton Boots: coming with the gear), Nerospace, Nerotech | Gravity reagent: controlled low gravity in gear; anti-grav lifts and low-g suit parts elsewhere. |
| Magnet Crab | Iron Nugget (vanilla) + its stolen shell | `minecraft:iron_nugget` | — | — | Vanilla; stolen metal is always returned on death. |
| Static Swarm (bottled) | Static Jar | `nerocreatures:static_jar` | `neroland:materials/static_jar`, `neroland:highlight/tools` | NeroCreatures (thrown anti-machine tool; 2 Plasma Cells with a redstone), Nerotech, Nerospace | A bottled charge: disables machines when thrown; EMP/capacitor reagent elsewhere. |
| Pirate Captain | Captain's Ledger | `nerocreatures:captains_ledger` | `nerocreatures:treasure_maps`, `neroland:quest/treasure_maps`, `neroland:highlight/tools` | NeroCreatures (opens a pirate stash), NeroQuests, structure mods | Marks one stash for the captain's killer; quest and structure hook. |
| Void Manta | Leather (vanilla), Void Essence | `minecraft:leather`, `nerocreatures:void_essence` | see Void Essence | — | No new drop: Void Essence's second natural source after the Void Crawler. |
| **Any planet boss** | Apex Trophy | `nerocreatures:apex_trophy` | `neroland:materials/apex_trophy`, `neroland:highlight/materials` | Nerotech (exotic recipes), NeroEconomy (high value) | The top of the ladder: proof of a boss kill, and the gating reagent for the ecosystem's most expensive recipes. Stacks to 8, because a trophy is meant to be counted rather than accumulated. |

## Consumption inside NeroCreatures

NeroCreatures spends these of its own drops (the 0.5.0 habitat drops' in-mod uses are in the table
above; several arrive with the gear and habitat blocks). These are the direct sinks, and they are listed
here rather than buried in the code because they are the reason two of the drops have a floor under
their value even with no other Nero mod installed.

| Drop | Spent on | How much | Consumed? |
| ---- | -------- | -------- | --------- |
| Stalker Sinew | Taming a **Glacite Wisp**; feeding a tamed one | 1 per attempt (1-in-3 tames); 1 per heal | Yes |
| Plasma Cell | Taming a **Xertz Forager**; feeding a tamed one; crafting a Drone Shell | 1 per attempt / heal; 2 per shell | Yes |
| Salvaged Circuitry | Crafting a **Terraforming Drone Shell** | 3 per shell | Yes |
| Android Core | Crafting a **Terraforming Drone Shell** | 1 per shell | Yes |
| Static Jar | Thrown: disables machines for 5 s; or crafted with a redstone into 2 Plasma Cells | 1 per throw / craft | Yes |
| Captain's Ledger | Opening its pirate stash | 1 | Yes |

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
| Lumen Moth | Lumen Dust | 0–2 |
| Echo Shrike | Resonant Membrane | 0–2 |
| Lode Tortoise | Lode Scute | 0–1 |
| Dune Loper | Leather | 0–2 |
| Strata Hound | Bone | 0–2 |
| Dust Devil | Charged Sand | 1–2 |
| Dust Devil | Red Sand | 0–2 |
| Slag Beetle | Slag Carapace | 0–1 |
| Magma Wader | Heat Gland | 0–2 |
| Rime Yak | Rime Wool | 0–2 (shearing: 2, Bountiful up to 6) |
| Frost Lurker | Cryo Fang | 0–2 |
| Spore Drifter | Bone Meal | 0–2 |
| Bloom Host | Bloom Heart | 0–1 (sporelings: nothing) |
| Mossback | Mossback Hide | 0–2 |
| Mossback | Raw Mossback | 1–3 |
| Drift Leviathan | Leviathan Barnacle | 1–3 (shearing: 2–3 when grown) |
| Prism Stag | Prism Antler | 0–1 (shedding: 2 per 12000 loaded ticks, Bountiful up to 4) |
| Shardback | Mirror Scale | 0–2 |
| Magnet Crab | Iron Nugget | 1–3 (plus everything in its shell) |
| Gravity Leech | Graviton Node | 0–2 |
| Cache Mimic | Contraband 2–4, Salvaged Circuitry 2–4, Plasma Cell 1–2, Iron Ingot 3–6, Gold Ingot 1–3 | all guaranteed |
| Cache Mimic | Android Core 1 at 35%, Diamond 1 at 20% | |
| Static Swarm | Redstone 1–3, Glowstone Dust 0–2, Plasma Cell 1 at 25% | (bottling: 1 Static Jar) |
| Scrap Hulk | Salvaged Circuitry 6–12, Android Core 1–2, Iron Ingot 4–8 | +1 circuitry per shed part (up to 3) |
| Pirate Captain | Contraband 2–5, Gold Ingot 1–3 | (+ the Captain's Ledger, to the killer only) |
| Pirate stash (ledger) | Contraband 4–8, Gold Ingot 3–7, Plasma Cell 1–3, Salvaged Circuitry 2–5, Emerald 1–4; Android Core 25%, Diamond 30% | `nerocreatures:gameplay/pirate_stash` |
| Void Manta | Leather 0–2, Void Essence 0–1 | |

Lode Tortoise **nodules** are not loot: they are harvested from a living tortoise and yield vanilla
coal, raw copper, iron or gold nuggets, redstone, lapis, diamond or emerald, capped by
`maxNodulesPerHarvest`. Alpha Echo Shrikes, Dust Devils, Magma Waders, Frost Lurkers, Bloom Hosts and Shardbacks
drop 3, 4, 3, 3, 2 and 3 extra of their signature drop; alpha Gravity Leeches 3 Graviton Nodes, alpha
Cache Mimics 1 Android Core and alpha Static Swarms 3 Plasma Cells (see [Alphas](Alphas.md)). The
Scrap Hulk and the Pirate Captain are elites and never alphas.

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
