# The Menagerie

`/nerocreatures gallery` builds the **Menagerie**: a walkable creative-mode showcase of everything in
NeroCreatures, built where you stand. Every creature stands in a pavilion of its habitat with a sign
that says what it is for and a gold **demo plate** that plays its ability once. There is a mount
paddock you can ride in, a wall of Radiant morphs, a boss hall with every boss in each of its three
phases, working rows of every block and gear item, and the alpha form of every hostile beside its
base form.

It is a debug and showcase scene. It is **creative-only**, writes **nothing about any player**, and
`/nerocreatures gallery clear` removes it again.

## Building it

```text
/nerocreatures gallery          build the Menagerie at your feet
/nerocreatures gallery grid     the compact flat layout instead
/nerocreatures gallery tour     walk the 25 tour stops
/nerocreatures gallery clear    remove the nearest gallery
```

You need operator level 2, you must be a **player** and you must be in **creative**. Stand somewhere
open: the Menagerie is 111 x 105 blocks and 16 high, centred on you. Everything is within 76 blocks of
the centre, so a render distance of 8 shows all of it. The build bulldozes its box from your feet up
(the layer you stand on and everything below it is never touched) and moves you to the plaza at the
centre, facing north.

A gallery refuses to build within 160 blocks of another one. Run `clear` first.

## The layout

Three east-west streets cross a central concourse. The pavilions face the streets.

| Where | What |
| --- | --- |
| Plaza (centre) | Where you start and where the tour begins and ends. |
| Central Street, north side | Luna, Mars, Cindara, Glacira, and every drop in the mod on a stepped stand. |
| Central Street, south side | Greenxertz (with the Drift Leviathan hanging over it), Nyxara, Asteroid and the Field Scanner kiosk. |
| North Street, south side | Wrecks: Machines, Wrecks: Crews, the Gear bay and the Hatchery. |
| North end | The boss hall: Cinder Tyrant, Rime Matriarch, Bloom Sovereign and Facet Colossus, each posed in phase 1, 2 and 3. |
| South Street, north side | Deep Space, the Habitat Blocks bay and the Trait Wall. |
| South end | The mount paddock: two Dune Lopers and two Void Mantas, saddled. |

Each creature slot has, from the street inward: a strip in the habitat's colour, the gold demo plate
with the creature's sign beside it and its spawn egg on a stand, then the creature on an inlay with a
small diorama of its habitat behind it. Alphas stand in the slot beside their base form with the
guaranteed alpha drop on the stand instead of a spawn egg.

## Demo plates

Step onto a gold plate in creative and the creature beside it shows its ability once: the animation
plays, with its particles and its sound. Step off and back on to see it again (there is a short
cooldown, and at most four demos run at once).

A demo is a **script of visuals**, not the ability itself. It never hurts, heals, moves or buffs
anyone, never drops an item or experience, and never changes a block outside its own slot. A few
demos place a temporary prop inside their slot (a light for the Lumen Moth, a slag crust for the
Magma Wader, ripe wheat under the Spore Drifter) and put it back afterwards. In survival or
adventure, the plates do nothing but click.

`/nerocreatures gallery demo <slot>` plays one demo by name, for example
`/nerocreatures gallery demo frost_lurker` or `/nerocreatures gallery demo facet_colossus_p2`.

## The tour

`/nerocreatures gallery tour` takes you to stop 1. Each stop is one chat line with a caption and
clickable **Back** and **Next**. `/nerocreatures gallery tour <n>` jumps to stop `n` (1 to 25). The
server keeps no record of where you are; walk away to leave.

## The paddock

Right-click a saddled Dune Loper or Void Manta in the paddock (in creative) to ride it; jump uses its
ability. It cannot be tamed, owned, fed, bred, captured or unsaddled, its panniers do not open, and it
never counts against your mount cap. Ride it out of its yard, or get off, and it goes back to its post.
The Void Manta flies only where the dimension's gravity allows (in normal gravity it glides).

## Working blocks

The Habitat Blocks bay holds one of every habitat block, real and usable: Nest, Feeding Trough,
Incubator, Lure Beacon, Sonic Ward, Terrarium (holding a Lumen Moth), Trophy Plaque (holding an Apex
Trophy) and the Apex Altar. They are placed **empty and unfuelled**, because a baited Lure Beacon would
pull real spawns into the gallery and a stocked trough would breed passing livestock. Whatever you put
in is yours, and `clear` empties the blocks without dropping anything. A sigil on the altar starts a
real boss fight. The crust plate shows the three temporary crust blocks, which melt back by themselves.

## What a gallery display is not

Every creature in the gallery is a **display**. Displays carry the `nerocreatures_gallery` tag and:

- never count toward any population cap, the alpha cap, the boss-per-dimension rule, swarm or mimic
  area caps, or the per-species lines of `/nerocreatures budget` (they get one `gallery` line,
  outside the total);
- are never targeted by predators, fled from by prey, recruited by packs, repelled by a Sonic Ward or
  bred by a Feeding Trough;
- cannot be scanned (the Field Scanner says "Gallery display"), studied, killed for a statistic,
  tamed, captured, saddled, sheared, milked, bred, named into a pet or tied to a Nest;
- drop nothing and give no experience;
- appear in nothing the companion app sees and raise no push.

The bosses in the hall are posed, not fighting: no boss bar, no arena, no contribution, no cooldown.

## Clearing

`/nerocreatures gallery clear` finds the nearest gallery within 96 blocks (you can be anywhere inside
it), stops its demos, puts any rider off a paddock mount, empties every container, turns every block in
its box to air (never the layer below the floor) and removes **only the entities the gallery placed**.
Your own pets, mounts and any other animal that wandered in stay where they are. Nothing is killed, so
nothing drops.

With no gallery nearby, `clear` falls back to clearing the old 0.4 showcase box around you, again
removing only tagged entities.

## Privacy

The gallery stores nothing about any player. While you play, the server remembers in memory only the
last plate you stood on and each exhibit's demo cooldown; both are gone when you leave or the server
stops. Chat lines count things and name nobody, and nothing is logged.

## Screenshots

The client-side capture harness photographs the Menagerie the same way every time. See
[Gallery & screenshot capture](Gallery.md).
