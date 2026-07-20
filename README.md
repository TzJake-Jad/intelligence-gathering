# Intelligence Gathering

A RuneLite plugin that tracks Shayzien organised crime meetings and guides you to the current one.

Read the notice board in the Shayzien Encampment and the plugin remembers the meeting for you —
where it is, when it starts, and which worlds you've already checked.

## Features

- **Side panel** showing the current meeting location with a picture of the spot, plus a
  per-world status list so you can keep track while world hopping
- **Navigation help**: world map marker, tile highlight at the meeting spot, an optional hint
  arrow, and optional routing via the Shortest Path plugin
- **Combat overlays**: highlights gangsters and the gang boss, with separate colours for
  dangerous, safe, and unknown bosses
- **Intelligence drops**: highlights dropped intelligence and shows a despawn timer
- **Timers**: infoboxes counting down to the meeting start and item despawn
- **Filtering**: track only the areas you care about (Arceuus, Hosidius, Lovakengj,
  Piscarilius, Shayzien, or elsewhere), multicombat-only mode, and safe-worlds-only mode
- **Data logging** (optional): appends observed board reads to
  `.runelite/intelligence-gathering/rotations.csv` for rotation analysis

## Usage

1. Enable the plugin and open the **Intelligence Gathering** panel from the sidebar
2. Read the notice board in the Shayzien Encampment
3. Follow the world map marker / tile highlight to the meeting spot
4. Hop worlds and repeat — the panel tracks the status of each world for you
