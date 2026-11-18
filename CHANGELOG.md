# Changelog

## 1.0.0 — 2026-11-18

The first release. Minecraft 1.21.1, NeoForge 21.1.219 or newer.

### What the mod generates
- Abandoned modern cities on a grid of 2 × 2 blocks, one per world cell of 736 blocks, with streets 33 wide,
  sidewalks, center lines, crosswalks and abandoned street lights; hills cut and valleys filled.
- 52 hand-built structures (30 models, some in several colors) in a ring around each block: 12 kinds of houses,
  pharmacies, car workshops, gas stations (always 2 per city, never 2 large), 10 buildings up to 143 blocks tall, a
  police station and, in about 1 city in 10, a large special building.
- Loot tables of its own (vanilla items) by kind of structure, more generous in containers that are hard to reach;
  the buildings get their chests drawn anew in every copy, on floors and in rooms a player can walk into.
- Nature taking over: woods in the cores of the blocks (real trees whose leaves decay when the trunk is cut), parks
  with paths, benches and a fountain, and cracked, mossy, overgrown structures (`structureOvergrowth`).
- Highways between neighbouring cities with walls, tunnels and bridges, worn roads, broken walls and holes in the
  bridges; floating vegetation left by the city's cuts is removed.
- Commands for operators: `/amcs locate`, `/amcs locate structure`, `/amcs spawn`, `/amcs info` and diagnostics.

### Changed in the review for 1.0.0 (since the development builds)
- Configuration: `minRadius` and `maxRadius` are gone (they never changed the cities); `cellSize` starts at 734 and
  a smaller value is raised to 734 instead of making the mod ignore the whole config file.
- Commands: `/amcs network` (radius 500–1000), `/amcs plan` (up to 16) and `/amcs audit` (up to 8) are limited so
  that the server never waits for minutes; `/amcs column` keeps its heights inside the world.
- Faster startup: the structures load in ~4 s (~5 s before).
- Clearer errors when `index.txt` has a malformed line; the Gradle wrapper checks the checksum of what it downloads.
- License: GNU GPL version 3 with additional terms (keep the attribution "Abandoned Modern City, created by
  RakkuLyc", mark modified versions, use another name); rules for structures by other players in `CONTRIBUTING.md`.
- The cities, roads and structures generated are the same as in the last development build: no change of the
  review moved a block.

### Known limitations
- Old worlds keep the chunks already generated: a city can be cut where old and new chunks meet.
- The natural decoration next to a city can differ with the order in which the chunks are generated; Minecraft
  itself sometimes leaves vines or plants in the air.
- Other world generation mods and clients without the mod were not tested.
