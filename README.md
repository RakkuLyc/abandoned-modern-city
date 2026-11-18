# Abandoned Modern City

A NeoForge mod for Minecraft 1.21.1 that generates abandoned modern cities taken over by nature, linked by highways
with tunnels and bridges.

## What it generates

- **Cities** on a regular grid of 2 × 2 city blocks (~459 × ~339 blocks with the streets), one per world cell of 736
  blocks (70% of the cells, not over the sea or too high). Streets 33 wide with sidewalks, center lines, crosswalks
  and street lights; hills are cut and valleys filled to the city's level.
- **Structures**: 52 hand-built structures (30 models, some in several colors) stand in a ring around each block:
  12 kinds of houses, pharmacies, car workshops, gas stations (always 2 per city), 10 buildings up to 143 blocks tall,
  a police station and, in about 1 city in 10, a large special building. No two of one model in a block, and each
  building at most once per city.
- **Loot**: the chests, barrels and pots of the structures have loot tables of their own (vanilla items only), by
  kind of structure (house, pharmacy, workshop, gas station, building, police station). The buildings get their
  chests drawn anew in every copy; containers that are hard to reach (underground, on a roof, in an attic, out of
  walking reach) get more generous loot.
- **Nature taking over**: the cores of the blocks are woods with real trees (their leaves decay when the trunk is
  cut), bushes and rubble; some blocks have a park with paths, benches and a fountain. The structures have cracked
  stone, moss and plants on the floors and vines on the facades (switch it off in the config).
- **Highways** between neighbouring cities (up to 4 per city), as wide as the streets, with walls, tunnels through
  the mountains and bridges over water and valleys. Roads and bridges are worn: taken-over patches, cracks, broken
  walls and holes in the bridges.

## Requirements and installation

- Minecraft **1.21.1** and **NeoForge 21.1.219** or newer (21.1.x). No other mod needed.
- Put `abandonedmoderncity-1.0.0.jar` in the `mods` folder. The mod is needed on the server (or in single player);
  whether a client without it can join a server that has it was not tested.
- The cities appear in **new chunks** of the overworld. In a world created before the mod (or with an older version)
  the chunks already generated stay as they are: a city may be cut where old and new chunks meet. Use a new world.

## Configuration

`config/abandonedmoderncity-common.toml` (written at the first start):

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | generate cities in the overworld |
| `cellSize` | `736` | distance between neighbouring cities, in blocks (at least 734; a smaller value is raised to 734); the highways get longer with it |
| `cityChance` | `0.7` | share of the cells that get a city |
| `maxCityLevel` | `150` | highest ground level a city may sit at |
| `highways` | `true` | link neighbouring cities with highways |
| `highwayMaxLength` | `4000` | longest highway, in blocks of path |
| `structureOvergrowth` | `true` | nature taking over the structures; read at startup (restart the game after changing it) |

Changing the config changes where the cities go: use it in a new world. Config files of older versions may have
`minRadius` and `maxRadius`: they never changed the cities, and NeoForge removes them.

## Commands (operators, level 2)

- `/amcs locate city|highway|tunnel|bridge` — the nearest one, with coordinates to click (they suggest a `/tp`).
- `/amcs locate structure <category> [name]` — the nearest structure of a category (`house`, `pharmacy`,
  `workshop`, `gas_station`, `building_small`, `building_large`, `police_station`, `special`), or with that name.
- `/amcs spawn <category> <name>` — places a structure in front of you, with its loot.
- `/amcs info` — what the mod plans at the column you stand on.
- `/amcs plan [radius]`, `/amcs audit [radius]`, `/amcs network [radius]`, `/amcs column <x> <z> <y0> <y1>` —
  diagnostics (images go to `amcs_renders/` in the server folder). `plan`, `audit` and `network` make the server
  wait for up to ~25 s.

## Compatibility and known limitations

- Other world generation mods: not tested. The cities are built in every biome tagged `#minecraft:is_overworld`; a
  biome of another mod without that tag would leave a hole in a city (not tested).
- The city and its plan are the same for a seed whatever the order of the chunks; the natural decoration next to a
  city (trees, vines, plants, lakes) can differ with the order of generation, as in Minecraft itself.
- Minecraft itself sometimes leaves vines or plants hanging in the air (under jungle trees, over lakes); the mod
  removes what its own cuts leave floating, not those.
- Changing the config or the mod's version in an existing world leaves seams between old and new chunks.

## License and credits

MIT (see [LICENSE](LICENSE)). By RakkuLyc. For building, testing and the full specification see
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), [docs/TESTING.md](docs/TESTING.md) and [CLAUDE.md](CLAUDE.md).
