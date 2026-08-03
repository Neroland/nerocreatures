# NeroCreatures

**Mobs across planets and dimensions, with drops that matter — a bestiary that supplies the whole ecosystem.**

NeroCreatures is the **bestiary** mod of the Neroland ecosystem: hostile predators, resource-bearing neutrals, tameable alien pets, utility drones and multi-phase planet bosses that inhabit Nerospace planets, guard NeroRuins structures and fill NeroEvents invasions. Every creature is designed around its drops — void essence, hide and sinew, crystal, chitin, plasma cells, salvaged circuitry and android cores — deliberately wired into machines, armour, quests and the economy rather than existing for spectacle alone.

Built on **Neroland Core**, so its shared materials, loot framework, progression tiering, `c:` material tags and space compat tags are shared with the rest of the lineup — so killing a creature always produces something useful downstream. *(Planned — in design; not yet released.)*

---

## The roster

1. **Void Crawlers.** Fast, low-light ambush predators native to dark and deep-space biomes, with a short phase/blink to close distance. They drop void-essence components used in advanced machinery — a **Nerotech** catalyst — and lurk in unlit **NeroRuins**.
2. **Lunar Stalkers.** Pack-hunting hostiles on low-gravity moons that grow bolder at night and flank in groups. Their hide and sinew feed light armour (via Core material tags) and a tradeable **NeroEconomy** reagent.
3. **Crystal Golems.** Slow, heavily-armoured neutrals that only retaliate — effectively walking resource nodes of refined crystal. Their drops feed power and tech crafting (**NeroPower / Nerotech**) and surface as high-value crystal for trade.
4. **Asteroid Worms.** Large burrowing creatures in asteroid fields that surface to attack then submerge, drawn with a custom burrow/emerge behaviour. They drop chitin and ore-rich gut material for machines — and are a genuine hazard on mining routes.
5. **Plasma Slimes.** Splitting energy creatures that divide when damaged and leave residual plasma; a crowd-control threat with an energy-damage aura. They drop plasma cells used as a fuel/power ingredient and make ideal invasion fodder.
6. **Space Pirates.** Humanoid NPC raiders — ranged and melee variants — who attack bases, convoys and stations and carry looted goods. They drop currency, gear and contraband, anchor **NeroEvents** Pirate Raids and give **NeroFactions** a hostile flavour.
7. **Rogue Androids.** Malfunctioning mechanical units guarding wrecks and labs, from light drones to heavy combat frames with shield/armour mechanics. They drop salvageable circuitry and android cores for crafting.
8. **Alien Pets & Terraforming Drones.** Tameable, non-hostile fauna that follow, defend and take commands, plus neutral utility drones that assist with terrain, planting and oxygen support — the constructive counterpart to the hostile machines.
9. **Planet Bosses.** Unique, multi-phase apex creatures — one signature boss per major planet or biome — with a bossbar, contribution tracking and arena awareness, built to anchor server-wide fights and gate the rarest, most economy-critical drops.

## Built to matter

- 🎯 **No mob without a purpose** — every creature exists to drop something the ecosystem consumes, mapped by a maintained drop-to-system table so no drop is ever a dead end.
- 🪐 **Planets feel inhabited** — biome- and dimension-tagged spawn rules put the right fauna on the right worlds, gating mid-tier materials behind reaching space.
- ⚔️ **Readable threat** — each creature has a clear behavioural identity and counter-play, not just bigger numbers, on goal-based AI kept cheap enough for crowded invasions.
- 🎛️ **Server-tunable** — per-creature spawn weights, drop rates, boss difficulty/HP scaling, aggression and pet caps, with per-chunk/per-dimension limits to prevent crowding and lag.

## Privacy (POPIA / GDPR)

NeroCreatures stores **no personal data**. Pet ownership and boss contribution are ordinary gameplay state tied to the world save, never a player profile; where boss contribution reuses NeroEvents' participation tracking it inherits that mod's minimisation and retention posture. Any optional crash telemetry is anonymous and opt-out, carrying version strings only — never IPs, names, UUIDs or world data.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — shared materials, loot/item framework, progression tiering, `c:` material tags and space compat tags, plus its own creative tab for introduced items and spawn eggs.
- 🔌 **Interoperates, never hard-depends** — no Nero mod hard-depends on NeroCreatures, and it depends on none but Core. **Nerospace** supplies the planets, moons and asteroid fields it spawns in; **NeroRuins** places it as structure guardians; **NeroEvents** invokes its invasions and bosses; **Nerotech**, **NeroPower**, **NeroEconomy** and **NeroQuests** consume its drops; **NeroCompanion** extends its alien pets. All optional at runtime — behaviour adapts when present.
- 🌐 **External interop via Core tags only** — drops carrying `c:` material tags reach Create, AE2, Mekanism and Energized Power recipes with no hard dependency, and creatures recognise Ad Astra planet/dimension tags through Core's space compat tags.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroCreatures (it loads first).
- Optional but recommended: **[Nerospace](https://modrinth.com/mod/nerospace)** for the planets, moons and biomes the roster spawns across.
- Conventional `c:` material tags and Core space compat tags mean Create, AE2, Mekanism, Ad Astra and Energized Power interoperate as the 26.x ecosystem fills in — no hard dependency on any of them.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroCreatures by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/nerocreatures) and the [GitHub repository](https://github.com/Neroland/nerocreatures). Full terms: [LICENSE](https://github.com/Neroland/nerocreatures/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerocreatures/wiki)** — every creature, drop and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerocreatures/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerocreatures/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/nerocreatures)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
