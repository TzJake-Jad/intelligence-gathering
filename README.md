# Intelligence Gathering

A RuneLite plugin that tracks Shayzien organised crime meetings and guides you to the current one.

Read the notice board in the Shayzien Encampment and the plugin remembers the meeting for you —
where it is, when it starts, and which worlds you've already checked.

## Features

- **Side panel** showing the current meeting location with a picture of the spot, plus a
  per-world status list so you can keep track while world hopping — worlds are marked done
  when their gang boss is killed
- **Navigation help**: world map marker, tile highlight at the meeting spot, an optional hint
  arrow, and optional routing via the Shortest Path plugin
- **Combat overlays**: highlights gangsters and the gang boss, with separate colours for
  dangerous, safe, and unknown bosses
- **Intelligence drops**: highlights dropped intelligence and shows a despawn timer
- **Timers**: infoboxes counting down to the meeting start and item despawn
- **Filtering**: track only the areas you care about (Arceuus, Hosidius, Lovakengj,
  Piscarilius, Shayzien, or elsewhere), multicombat-only mode, and safe-worlds-only mode
- **Share codes**: **Copy code** puts the current meeting and everyone's world scouting on
  your clipboard as a short line of text; a friend hits **Import** to pull it in. Their own
  scouting is merged, not overwritten, and an expired or damaged code is rejected with a reason
- **Data logging** (optional): appends observed board reads to
  `.runelite/intelligence-gathering/rotations.csv` for rotation analysis

## Usage

1. Enable the plugin and open the **Intelligence Gathering** panel from the sidebar
2. Read the notice board in the Shayzien Encampment
3. Follow the world map marker / tile highlight to the meeting spot
4. Hop worlds and repeat — the panel tracks the status of each world for you
5. To split the work with friends, hit **Copy code** in the panel's Share section and paste it
   to them — they hit **Import** and get the location plus every world scouted so far

## Acknowledgements

Heavily inspired by the organised crime plugins of **Dylan Lange** and
**[Mordo95](https://github.com/Mordo95/shayzien-organised-crime)** — thank you both for your
versions, which this plugin builds on.
