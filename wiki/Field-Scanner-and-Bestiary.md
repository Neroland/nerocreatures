# Field Scanner and Bestiary

The **Field Scanner** records the creatures you meet; the **Bestiary** screen shows what you have
found, how each creature works, what it drops and what those drops make.

## The Field Scanner

| | |
| --- | --- |
| **Item** | `nerocreatures:field_scanner` (stack size 1, no durability, no fuel) |
| **Recipe** | Shaped: an amethyst shard over iron, redstone, iron, over iron, a glass pane, iron. Vanilla materials only, so it works on a server with no planet mod. |
| **Hold Use** | Scans the creature you are looking at. |
| **Sneak + Use** | Opens the Bestiary. |
| **Key** | *Open Bestiary* (NeroCreatures category, unbound by default) opens it when a scanner is anywhere in your inventory. |

A scan takes **2 seconds** (40 ticks) of looking at the same creature within **8 blocks**, with a
clear line of sight. Up to 5 ticks off target are forgiven; a sixth, or a different creature, starts
over, and so does letting go. After a scan the scanner rests for one second.

While you hold a scanner, a small reticle around the crosshair shows the target, its tier and the
scan bar. Everything is decided by the server; the bar is only a prediction.

What cannot be scanned:

- A creature in a hidden state: a buried Frost Lurker, a Cache Mimic still posing as a crate, a
  burrowed Asteroid Worm, a Dust Devil still spinning up, anything invisible. The scanner treats it as
  if nothing were there, so it is never a detector.
- A gallery display ("Gallery display").
- Anything that is not a NeroCreatures creature ("Not a Bestiary creature").

Other players' pets and mounts can be scanned; the scanner shows nothing about their owner. An alpha
scans as its species, a Bloom Host's sporelings and pods as the Bloom Host, and Radiant changes
nothing.

## Discovery tiers

Each of the 38 creatures has two tiers.

| Tier | How | What it unlocks |
| --- | --- | --- |
| **Scanned** | A completed scan. | The entry: model, habitat, ability, drops and their uses. |
| **Studied** | Scanned, and then the species' rule below. | The weakness line and a one-time reward. |

| Kind | Studied when |
| --- | --- |
| Hostile, neutral | You have defeated 5 (vanilla kill statistic; earlier kills count). |
| Elite (Scrap Hulk, Pirate Captain) | You have defeated 3. |
| Tameable, mount, drone | You tame, hatch or breed one, or deploy the drone. Owning one when you scan it counts. |
| Livestock, ambient | You harvest from one (shear, milk, mine a nodule, shear barnacles) or breed one. |
| Boss | You took part in a **summoned** fight (Apex Altar) when it was won. |

The **reward** is experience only: 25 points, 60 for an elite, 150 for a boss. It is granted exactly
once per creature, when the Studied tier opens, and only while you are online (a tier that opens while
you are offline, after a hatch or a boss fight, opens without it).

Tiers are Neroland Core record gates (`nerocreatures:bestiary/scanned/<id>` and
`nerocreatures:bestiary/studied/<id>`), so quest packs can use them and Core erases them with the rest
of your progression. Nothing new is stored about you; see [Privacy](Privacy.md).

## The Bestiary screen

- **Creatures** page: every creature grouped by habitat, with per-habitat progress bars. Undiscovered
  creatures show as silhouettes; bosses always carry a crown, so you can see they exist.
- **Detail**: a turning model (turn it with `,` and `.`, pause with `P`, drag it with the mouse;
  Radiant carriers can show the Radiant morph with `R`), the kind and tier chips, *Your records*
  (Defeated, and Owned now for pets and mounts), Habitat, Ability, Weakness (Studied only), Drops and
  uses, and notes for traits, alphas, forms and bosses.
- **Traits** page: what each trait does, with the numbers from the trait data.
- Every control has a label or a tooltip, and the tooltip also shows on keyboard focus. Tab moves
  between areas, arrows move inside one, Enter opens, Backspace goes back in a small window, `1` and
  `2` switch pages, Esc closes.

The screen asks the server for your own counts when it opens; the server answers only for you, at most
once a second, and sends only the creatures you have scanned.
