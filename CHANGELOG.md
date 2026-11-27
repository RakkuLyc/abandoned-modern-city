# Changelog

## 1.1.0 — not released yet (in testing, 2026-11-24)

### Added
- Zombie spawners in the tall buildings: near their chests, on the street floor and one floor in two above it, and
  in the underground garage of the wide building; by the area of each floor under a roof, one per 500 blocks, 1 to 5
  a floor, so a wide floor spreads more of them and a narrow one does not crowd them (~3 to 4 per small building, ~14
  in the narrow green tower, ~20 to 27 in the wide ones). They make plain zombies in any light (a torch does not stop them:
  break them), 2 at a time, at most 4 around each, when a player is within 12 blocks; nothing spawns in Peaceful.
  `/amcs spawn` places them too.
- Config option `zombieSpawners` (on by default; read at startup). Off, the buildings have no spawners, as in 1.0.0.
- A new world's spawn is put about 150 blocks outside the city nearest to where the game would put it, on dry ground
  away from the highways (the game's spot, near 0, 0, is always the farthest place from the cities around it: their
  streets ~235 blocks away, ~880 when none of the 4 cells around it has a city). Config option `spawnNearCity` (on by
  default); existing worlds keep their spawn. With Outpost Zero its starter bunker is still placed, near that spawn.

### Fixed
- The stairs of the apocalyptic building faced the wrong way (they could be climbed, but looked reversed).
- With Outpost Zero: its bunkers were placed inside the cities and on the highways. They now stay at least 48 blocks
  (from the point it checks) away from them.

### Changed
- The crash building is now a special structure, like the ramp building: it stands only in the cities that draw a
  special structure (~40%), at most once, and such a city may have both (at most 2 special structures). It is in ~13%
  of the cities instead of ~26%. Inside it is the same (drawn chests, barrels, zombie spawners). `/amcs spawn` and
  `/amcs locate structure` list it under `special` instead of `building_large`. In a world made before, the cities
  that had it without the draw (~1 in 8) are planned differently: where its chunks were already generated it stays,
  cut where the new chunks meet them, and the structures around it can differ; a new world has none of that.

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
- The cities, roads and structures generated are the same as in the last development build with a valid config: no
  change of the review moved a block. A config file with a `cellSize` from 512 to 733 (refused before, so the defaults
  were used) now gives 734 and keeps its other values, so such a world comes out different.

### Known limitations
- Old worlds keep the chunks already generated: a city can be cut where old and new chunks meet.
- The natural decoration next to a city can differ with the order in which the chunks are generated; Minecraft
  itself sometimes leaves vines or plants in the air.
- Other world generation mods and clients without the mod were not tested.
