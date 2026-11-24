# Working rules of this project (mandatory)

1. **Ask along the way**: before any relevant change, ask. Do not decide design, behavior, priorities or style alone.
   When there is more than one solution, present the options and ask.
2. **Build a `.jar` after every small or medium change** (`./gradlew build` → `build/libs/`). Always say when the jar
   is ready and exactly what changed in it. Do not pile up big changes.
3. **GitHub (top priority)**: version all progress and push it to GitHub with the GitHub CLI (`gh`). Frequent commits
   with clear messages; after each testable jar, update the repository. A clean history.
4. **Iterative**: small, testable steps. At the end of each stage: a summary of what was done, what changed in the jar
   and the recommended next step.
5. **Do not move on to the next feature before the user has tested and approved the current one.**
6. **Documentation always up to date**: with every change to behavior, numbers, blocks, commands, configuration,
   architecture or tests, update in the **same commit**:
   - `CLAUDE.md` (the specification below: how the mod is now, no history) and `docs/HISTORY.md` (when a change
     replaces something, what it was, when and why);
   - `README.md` (the page for players: what the mod generates, requirements, configuration, commands, limitations) and
     `docs/DEVELOPMENT.md` (overview, tables of cities/buildings/roads, architecture, commands);
   - `docs/TESTING.md` (the tests and what they check, the audits, the problems found and fixed, performance and the
     results of the latest runs).
   Before each commit, check whether any of these files went out of date. The values come from the code and the
   tests, never from memory. In the summary of the stage, say which documents were updated.
7. **World fingerprint**: `FingerprintTest` keeps a SHA-256 per part of what the planner produces (`layout`, `chunks`,
   `lots`, `structures`, `weathering`, `crowns`, `benches`...), and the battery prints per target `mod blocks:` (the
   blocks the mod decides in the generated world; the references are in `tools/verify/reference/` and in
   `docs/TESTING.md`, section 6, one of them for `structureOvergrowth = false`). In a change that must not alter the
   world (cleanup, splitting files, comments, optimizations), the test passes unchanged and the `mod blocks:` and
   `stats:` lines stay the same (`tools/verify/compare_battery.py` ignores the counts of vanilla things:
   `loot_containers_anywhere`, `vegetation_checked`, `vegetation_floating_vanilla`). Only seed 20261027 repeats from
   one run to the next; in the others the order in which the chunks finish can change a few vanilla blocks (finding
   65). What the battery does not cover (bridge vines, fill, slopes, liquid sealing) is checked, in a change that only
   moves code, by an identical bytecode listing of the moved methods. The tools are in `tools/verify/`
   (`compare_battery.py`, `javap_dump.sh`, `compare_methods.py`, `compare_block_detail.py`, `chunk_diff.py`,
   `structure_cells.sh` with `compare_structure_cells.py`, `chest_walk.sh`, `floating_scan.py`, `world_column.py`).
   In a commit that changes the world on purpose, update the expected values of the test and the battery
   references, and say so in the commit message. The list of every time they changed is in `docs/HISTORY.md`.

## Project

Abandoned Modern City (mod id `abandonedmoderncity`, package `com.abandonedmoderncity`, GitHub repository
`RakkuLyc/abandoned-modern-city`, folder `Projetos Claude/AbandonedModernCity`), by RakkuLyc, GPL-3.0-only with
additional terms (`LICENSE`). NeoForge 1.21.1. Abandoned modern cities on a fixed grid, with hand-built `.nbt`
structures (52 files of 30 models; the originals in `structures/` keep their Portuguese names), linked by highways
with tunnels and bridges. It started as a copy of Abandoned Simple City without its buildings; how it got to its
current form is in `docs/HISTORY.md`. The page for players is `README.md`; building and architecture are in
`docs/DEVELOPMENT.md`; the tests in `docs/TESTING.md`.

## Specification (current)

### Cities and streets
- Every city is a rigid grid of 2 × 2 blocks of 180 × 120 of free ground (`Params.GRID`, `BLOCK_LONG_RUN` /
  `BLOCK_SHORT_RUN`; street axes 213 and 153 apart, `BLOCK_LONG` / `BLOCK_SHORT`): 3 × 3 streets, 9 crossings,
  ~459 × ~339 blocks with the streets. Each city draws (50%) whether the long side of its blocks runs east-west or
  north-south (`City.longX`).
- A city stands in the middle of each world cell of `cellSize` (736 by default, `Params.DEFAULT`) when `cityChance`
  (0.7) draws it and the middle is not mostly water nor higher than `maxCityLevel` (150); no position jitter. Water is
  filled and hills are cut to the city's level. Neighbouring centers are `cellSize` apart; the highway between their
  edges is ~277 to ~397 long. Every city reaches 230 from its center (`Params.GRID_RADIUS`); its area of influence
  (`Params.influence`) is the half diagonal of the city (286) + 10 (`INFLUENCE_SLACK`) + a slope of 32 + 4 = ~332, so a
  cell needs at least 734 (`Params.MIN_CELL_SIZE`; `Params.margin` = 335). NeoForge raises a `cellSize` under 734 to
  734 and keeps the other values.
- A street is 33 wide: 3 `smooth_stone` (sidewalk) + 27 `polished_deepslate` (lanes) + 3 `smooth_stone`
  (`Params.STREET_HW` = 16.5, `STREET_LANE_HW` = 13.5). A segment is 180 or 120 (the side of the block) + 33 of
  crossing; a 3 × 3 square of `smooth_stone` at each corner of a crossing. No street is ever cut: every block has its
  4 streets.
- Center line on the middle (17th) block, dashes of 11 with gaps of 6, as many as fit, centered: `white_concrete` in
  the city, `yellow_terracotta` on the highways.
- Crosswalks (`City.crosswalk`): at each end of every segment, 2 lane rows after the square of the crossing, 3 rows
  (`CROSSWALK_GAP` = 2, `CROSSWALK_DEPTH` = 3) of `white_concrete` stripes 1 wide with gaps of 1, parallel to the
  street, across the 27 lanes (14 stripes); zone `ChunkPlan.CROSSWALK`.
- Street lights (`core/StreetLights`, `ChunkPlan.F_LAMP`/`F_POLE`): one every 24 blocks (`LAMP_EVERY`) on both sides
  of every segment, facing each other, centered, away from the crosswalks and the crossings (7 a side on the segments
  of 180, 5 on those of 120). A post of 13 on the sidewalk row next to the lanes, an arm of 4 slabs at 12 over the
  lanes, an unlit glass lamp under its end. 10% are gone (`LAMP_GONE`); the others are one of 5 variants (~18% each):
  mossy, dark, broken (2 slabs of the arm, no lamp), fallen lamp (a chain at the end of the arm, the glass fallen on
  the lane, which is then not drivable) and overgrown (leaves on top); vines start 3 up and hang 1 block under the
  arm; over the lanes nothing is lower than 11 but the fallen lamp. The neighbour repair puts the parts back; the
  audit checks them (`street_light`, `street_light_blocks`).
- Abandoned roads (`core/RoadDecay`, a pure function of the position and the seed), on the streets, lines,
  crosswalks, sidewalks and shoulders of the cities, the highways and the bridges (not the tunnels, the half-slab
  steps of the ramps nor the wall column): ~30% of the columns taken over, in patches (value noise, cells of 5, above
  0.66) and scattered cracks (6%). A taken column keeps its height and only gets plants without collision: grass
  with short grass, tall grass or a fern; moss (`moss_block`, sometimes short grass); `coarse_dirt` or `gravel`;
  cracked asphalt (`blackstone` or `cobbled_deepslate` on the lanes, `cobblestone` on the sidewalk). The other columns
  have a worn texture (`RoadDecay.texture`): patches of `polished_blackstone`, `cobbled_deepslate`, `deepslate_tiles`,
  `cracked_deepslate_tiles`, `blackstone` and `smooth_basalt` on the lanes (`polished_deepslate` stays ~54%);
  `andesite`, `cracked_stone_bricks`, `stone_bricks` and `cobblestone` on the sidewalks; slabs of the same stones on
  the ramps; 20% of the painted lines faded. The neighbour repair and the audit use the same rule (`surface_block`,
  `road_plant`).
- Villages under a city: when it builds each chunk the mod removes the villagers, zombie villagers, iron golems and
  cats standing in the city or up to 32 blocks beyond its border streets (`City.within`, `City.VILLAGE_MARGIN`,
  `ChunkBuilder.removeVillageMobs`); other animals stay; the battery reports how many.

### Highways, tunnels and bridges
- 4 entrances per city (`City.EXITS`), one per side, drawn: the middle 3-way crossing of the side or one of its two
  corners, never the same crossing twice; at a corner the highway continues one of the border streets straight
  outwards. Gabriel neighbours that are among each other's 4 nearest are linked; the entrance is on the side facing
  the other city (the next best direction if that one is taken); each link is agreed by both cities.
- A highway is 33 wide, like the streets: 3 `smooth_stone` (the outer one with a `stone_brick_wall` on top;
  `Params.HIGHWAY_SHOULDER` = 2) + 27 `polished_deepslate` (a `yellow_terracotta` line in the middle) + 3
  `smooth_stone`. The walls are connected by the plan; no wall at the junction with the street. Inside the cities the
  lots have priority. Highways go straight from one city to the other (a Bezier between the two exits), at most
  `highwayMaxLength` (4000) long.
- Tunnels where there are 11+ blocks of terrain over the road (`Params.TUNNEL_COVER`), with 7 blocks of headroom
  (`Params.CLEARANCE`), lights every 12 blocks; their lanes stay clear (no cave-ins).
- Bridges: pillars every 24 blocks; lanes taken over and worn like the streets; the deck and the pillars in patches of
  `mossy_stone_bricks`, `cracked_stone_bricks`, `mossy_cobblestone` and `stone_bricks` (`RoadDecay.bridgeStone`); the
  wall broken in stretches of 1 to 3 (`RoadDecay.wallGap`, noise of 2.5 above 0.8) and mossy in stretches (~half);
  holes through the deck, ~1 per 80 blocks (`Wreck.holes`), 5 to 7 across and 4 to 5 along with an irregular smooth
  outline, their middle up to 6 from the axis, 8+ blocks from the ends; the hole columns are not drivable
  (`ChunkPlan.drivable`). Vines: in 35% of the columns just outside the walled edge of a bridge a strand held by the
  deck hangs down to the first block that is not air (`ChunkBuilder.bridgeVines`, in every chunk).

### City blocks, nature and parks
- Every block gets a ring of structures against its sidewalks (below); the rest (the core) is taken over by nature.
  ~1 block in 8 (`CityRings.PARK_BLOCK_CHANCE`) has a park of 110 × 64 in a drawn corner (`CityRings.PARK_LONG`/
  `PARK_SHORT`, its long side along the block's); the structures then stand along the two other streets, 4+ blocks
  from the park (`Ring.lots` with `keepOut`).
- Nature (`core/Flora`, `core/Wild`, a `WILD` lot under the whole block; the structures win where they stand): ground
  in patches of grass, `moss_block`, `coarse_dirt` and old concrete (`gray_concrete_powder`); trees ~1 per 55 columns,
  trunks 5+ apart: oak 38%, birch 24%, dark oak 18%, spruce 20%, 30% large (crown radius 3–4, conical spruce up to 12),
  crowns with irregular edges. Real trees: leaves not persistent with their distance to the trunk (1 to 6, through the
  leaves, `Flora.Tree.dist`; none farther than 6), so they decay when the trunk is cut. Bushes: leaves on a trunk
  hidden in the ground. Vines hang from 35% of the crown's edge columns, 1 to 4 blocks, only where held. Crowns and
  vines never leave the block or enter a structure (1 block of clearance). Plants only on soil: grass, tall grass,
  fern, large fern, moss carpet on moss. Rubble (`mossy_cobblestone`, `cobblestone`, `cracked_stone_bricks`,
  `gray_concrete` 1 or 2 high, a `mossy_stone_bricks` slab) on 1.2% of the grass columns and 4 × that on concrete.
- Parks (`core/Park`): the same nature (trees ~1 per 60 columns, no trunk on a path, no crown over the ring; rubble
  0.6%), flowers (poppy, dandelion, azure bluet, tulips, oxeye daisy, cornflower, 5%); `dirt_path` paths 3 wide with 30%
  gaps, in two layouts (50%): a "tic-tac-toe" grid (2 + 2 paths 16 from the center) or a pinwheel (a circle of radius
  16 + 4 spiral arms), scaled by the short side (`Park.BASE_SIZE` = 68). In the center the fountain `waterfall.nbt`
  (`resources/amcs/`), ringed by mossy slabs (`mossy_stone_brick_slab`, `mossy_cobblestone_slab`, 10% missing), its
  stone floor turned mossy (`Park.FLOOR_SWAP`), vines of 2 to 6 from its highest point in 55% of the columns with room
  (`Park.VINE_MIN`/`VINE_MAX`), an empty chest in 50% of the fountains (else `mossy_cobblestone`). Benches
  (`Park.benches`): 2 `spruce_stairs` facing the path with an open `spruce_trapdoor` armrest at each end, where 4
  columns of grass touch the path, 8+ apart (`BENCH_APART`), a target of 1 per 15 blocks of path (`BENCH_EVERY`;
  ~1 per 18.6 in the tests), 20% broken (`BENCH_BROKEN`).
- Foundation of the lots: the 3 layers under a lot's floor (`ChunkBuilder.FOUNDATION`) never keep air, a liquid or a
  block entity where the plan keeps the world (`ChunkBuilder.foundationGap`): they become dirt, when the chunk is
  built and in the neighbour repair; the cells a structure plans there are never touched. The audit checks it
  (`lot_foundation`).
- Floating vegetation (`gen/Floating`): after every chunk, starting from the loose blocks next to what it opened, the
  orphan leaves it removed and what the neighbour repair removed, every group of natural decoration
  (`ChunkBuilder.isDecoration`) joined by a face, edge or corner that touches nothing holding it (any other non-air
  block, a block the plan places, `Floating.Planned`, or the edge of the 3 × 3 chunks it may write) is removed, at any height; groups over
  20,000 blocks count as standing (`Floating.MAX_GROUP`). The neighbour repair also removes what a chunk decorated
  later grew into the bridge holes. The audit's `vegetation_floating` fails a group only within 7 columns
  (`Floating.MOD_REACH`) of a road, lot or tunnel wall or of a slope whose ground the mod moved; Minecraft's own
  floating groups farther away are counted in `vegetation_floating_vanilla`.

### Structures
- Files: 52 `.nbt` in `src/main/resources/amcs/buildings/`, copies of the originals in `structures/`, listed in
  `index.txt` (`type|file|ground|front|model|banners|neutral|seed|hard`, described in its header; read and checked by
  `core/IndexRows`: a malformed line stops the loading with its number). Names in English and ASCII (the argument of
  `/amcs spawn`). `core/Structures` loads, measures and turns them; `core/Ring` and `core/CityRings` place them. The
  overgrowth seed of each structure is its `seed` column (the `String.hashCode` of its former Portuguese name), so a
  renamed file keeps its overgrowth.
- Models: 12 houses (type `houses`: abandoned house, abandoned modern house, abandoned mansion, post-apocalyptic
  mansion, garage house, large house, pool house, suburban house, tower house, Victorian house, modern garage house,
  glass house); 6 shops with color variants, 28 files (pharmacy and large pharmacy 5 colors each, medium workshop 3,
  small workshop 5, gas station and small gas station 5 each); 10 buildings (apartment, skeleton, bluish, tower,
  apocalyptic, small, brutalist U, wide, green tower, crash); the ramp building (type `special`); the police station
  (type `police_station`). The ring draws a model (all equally likely, `Structures.pick`), then a color.
- Particular structures: the police station (54 × 38 × 33, ground on layer 3, a half-buried basement of `cave_air`
  rooms dug into the terrain with an inner stairway, 13 blue banners with the letters, 63 containers of which 18 hard,
  loot theme `police_station`); the crash building (91 × 44 × 90, two towers leaning against each other); the wide
  building (84 × 31 × 80, ground on layer 8 over its underground garage, the ramp mouth on the front edge); the ramp
  building (102 × 72 × 134, type `special`); the brutalist U building (75 × 58 × 125), the green tower
  (31 × 31 × 143), the small building (23 × 31 × 41), the modern garage house (42 × 36 × 15) and the glass house
  (48 × 40 × 15, L-shaped stairs from the hall). Leaves saved as not persistent far from any trunk become persistent;
  `/amcs spawn` also digs the garage and opens the ramp.
- Height classes of the buildings (`Structures.heightClass`, by the height of the template's box with its buried
  layers, `Def.h`): small up to 43, medium up to 78, large up to 143, immense above (40, 75, 140 with a margin of 3,
  `HEIGHT_SMALL`, `HEIGHT_MARGIN`); type `buildings_<class>`, category `building_<class>`: small 3 (apartment 37,
  small 41, skeleton 43), large 7 (wide 80, crash 90, bluish 106, apocalyptic 110, tower 118, brutalist U 125, green
  tower 143), medium and immense none. The class changes neither the loot nor the ring. `RingTest` checks it.
- Placing: front = the -Z side of the `.nbt`, facing the street (the `front` field turns the others: the abandoned
  modern house and the post-apocalyptic mansion 180°); box = the box of the solid blocks; the `ground` layer lies at
  the city's level and the layers under it are buried (buried `air` becomes dirt; `cave_air` becomes real air and
  digs the terrain, `Structures.CARVE_STATE`: the garage of the wide building, layers 1–7, and the half-buried basement
  of the police station). On the ground layer, air or `cave_air` over `cave_air` and the air joined to it stays open
  (the ramp mouth and stairwell of the wide building, the rooms of the police station: `StructureLoader.openings`,
  `Def.opening`). Edge rows of the ground layer without a real floor get the floor of the first row that has one
  (`StructureLoader.floorFill`).
- Loading rules for every structure: loose cobwebs removed; lightning rods become lit redstone torches
  (`Structures.swap`); leaves up to 6 from a trunk of the template become not persistent with their distance, leaves
  with no trunk near become persistent; crowns cut by the edge of the template are completed outside it by mirroring
  around the trunk (`StructureLoader.crowns`, `core/Crowns`, `gen/Overhangs`: 189 leaves in 7 structures), over a
  street only higher than 7 above it, never in another structure, only into air; the audit checks them
  (`crown_leaves`, `crown_leaf_missing`). Pharmacy variants get the D R U G S T O R E banners of their original in
  their color (`Template.withBannersFrom`, field `banners`) and the neutral wall of the original where they had a
  painted sign (`Template.withNeutralFrom`, field `neutral`). The medium workshop has 3 chests in its `.nbt` (work bay,
  back room, terrace).
- In a block: rows on the short sides run corner to corner, rows on the long sides fill what is left; structures
  never touch (gaps of 4 to 17, `Ring.GAP_MIN`/`GAP_MAX`); between the backs of opposite rows the woods of the core,
  with a clearance of 20 to 30 (`Ring.CORE_MIN`/`CORE_MAX`); never two of one model in a block (`Ring.row`); the trees
  of the cores never enter a structure. ~7.3 structures in a block without a park, ~52% of the free ground covered.
  The ramp building (102 × 72) only fits the rows of the short sides or a long side whose woods are narrow.
- Limits per city (`Structures.LIMITS`, the rings of the whole city drawn at once, `CityRings.rings`, a group at its
  limit drawn again): pharmacies 3, workshops 2, every building and the special one at most once (`Def.onePerCity`),
  the police station 1; houses may repeat. Gas stations: exactly 2 in every city, never 2 large (2 small or a small
  and a large, half the cities each, `CityRings.twoSmallGasStations`), reserved before the rings in 2 different
  blocks, a drawn row and its 1st or 2nd place (`CityRings.gasStations`, `Ring.Forced`; one that does not fit goes on
  to the next row or block). The special structure only in the cities that draw `CityRings.SPECIAL_CHANCE` = 0.40
  (`CityRings.allowsSpecial`): ~10% of the cities have the ramp building.
- Barrels of the buildings (`core/Barrels`, types `buildings_*`, `Structures.isBuildingType`), drawn per copy (the lot's seed, or a random one
  for `/amcs spawn`, and the barrel's cell): 70% taken out (`Barrels.GONE`; in a stack the top ones), the others
  become single chests facing where the barrel faced; a barrel that holds something (a block on its top, something
  hanging under it, a block fixed to a face) stays a barrel. Hard flags worked out on the template with its barrels.
- Chests of the buildings: the 556 chests saved in the templates of the 10 buildings are taken out when they load,
  after the overgrowth (`Structures.withoutChests`; the log says so); the ramp building, the police station and every
  other structure keep theirs.
- Floors, rooms and spots for a chest of each building (`core/Rooms`, `Def.rooms`, worked out at load on the walk of
  `core/Access`, the same reach and place rules as the hard containers: `Access.walk`, `Walk.reaches`,
  `Access.place`): floors = heights with 60+ cells to stand on (`FLOOR_MIN_CELLS`, `FLOOR_GAP`, `FRAGMENT_BELOW`);
  rooms = a floor cut at the doors and passages 2 wide or less (`NARROW`), corridors by median width and length
  (`CORRIDOR_WIDTH`, `CORRIDOR_LONG`, pillars up to 9 cells ignored, `PILLAR_MAX`), zones of at most 180 cells
  (`ZONE_MAX`). A spot: air saved in the template with air over it, on a full block (`Rooms.floorBlock`, checked by
  `Palette.validate`), against a wall, reached by a player beside it in the walks with and without the barrels, the
  cells around still joined and its front still reached with the chest there (`Access.Walk.reachedAvoiding`, in a window of 8 sideways and
  4 up and down, `Rooms.CHECK_R`/`CHECK_RY`), no
  barrel within 1, no container beside it, no door, trapdoor, gate, ladder or scaffolding within 2 (`DOOR_CLEAR`), no
  stairs within 1 (`STAIRS_CLEAR`), not in a corridor, indoors unless 4+ up under the open sky (a parapet or railing
  counts as a wall). Each spot has a facing, a weight (2 at a wall, 3 in a corner, +1 beside furniture) and flags:
  hard (`Access.place`), open sky, far (the 20% of its floor farthest from where the walk arrives, `FAR_SHARE`),
  street (a room at street level with 24+ cells on the box's edge, `STREET_EDGE`).
- Chests drawn in each copy (`core/ChestDraw`, `Def.chests(seed)`, placed by `Def.code`, through `Lot.code`; the last
  512 copies kept per structure, `DRAWN_KEPT`): a pure function of the copy's seed and the template. How many: evenly
  in the range of the height class (small 12–28, medium 25–47, large 38–65, immense 51–83, `ChestDraw.range`; the
  barrels' chests do not count). Per floor: in proportion to its weight (`Rooms.Layout.floorWeight`, a street spot
  counting half), largest remainders first. Which spots: a weighted draw without replacement (`-ln(u) / weight`, a
  street spot's weight halved), 4+ apart sideways within 2 heights (`Rooms.SPACING`), at most 1 per zone
  (`ZONE_CAP`) until the floor has its share, the cap then raised up to 4, the floor shares dropped (cap up to 8),
  the cap dropped. Exploration (far, open sky or hard): at least 15% of each copy (`EXPLORATION_MIN`, a minimum; the
  plain draw gives 25 to 43%). Chests face where the player stands (`Rooms.FACINGS`); a hard spot gets `chest_hard`
  (`Def.hard`, `Structures.hardSpots`: the garage of the wide building, the terraces of the crash, skeleton and green
  tower).
- Zombie spawners of the buildings (`core/SpawnerDraw`, **drawn but not placed in the world yet**: nothing calls it
  outside the tests), a pure function of the copy's seed and the template, drawn after the chests (which do not
  change): the floors over the ground one in two from the street floor (the 1st, 3rd, 5th...) and every floor under
  it (the garage of the wide building); 1 on each of them in a small building, else 2 to 5 drawn per floor
  (`LARGE_MIN`/`LARGE_MAX`; medium and immense like large). On a spot of `Rooms` not under the open sky nor in a lobby
  open onto the street, touching no chest and no other spawner (within 1 sideways and up or down, `TOUCH`, so never
  in front of a chest), guarding a chest of the copy (a drawn one or a barrel turned into a chest) at most 5 sideways
  and 2 up or down (`GUARD`, `GUARD_DY`), the chest with the fewest guards first, the spots in a weighted draw
  (`-ln(u) / weight`); when none is left near a chest, an exploration spot of the floor (far or hard) farther than 5
  from every chest; else the floor gets fewer.
- Commands (`command/StructureCommands`): `/amcs locate structure <category> [name]` (the nearest planned one,
  coordinates on the sidewalk in front of it) and `/amcs spawn <category> <name>` (2 blocks in front of the player,
  front toward the player, ground on the block the player stands on, the same rules as in the city with loot);
  categories `house` 12, `pharmacy` 10, `workshop` 8, `gas_station` 10, `building_small` 3, `building_medium` 0,
  `building_large` 7, `building_immense` 0, `police_station` 1, `special` 1 (`Structures.CATEGORIES`); the game
  suggests the names of the chosen category, a wrong name is refused with the list.
- Overgrowth (`core/Weathering`, applied to each template at load, before anything reads it; **off with
  `structureOvergrowth = false`**, read at startup): it adds what is missing to reach the measures of the apocalyptic
  building, above the ground layer, inside the box: cracked stone up to 31% of each family (stone bricks, deepslate
  bricks and tiles, polished blackstone bricks, nether bricks); green on the floors (up to 8.2% of the floors under a
  roof and 35.8% under the open sky: `moss_block` 63%, `grass_block` 20%, `coarse_dirt` 7%, `rooted_dirt` 5%, `dirt` 5%,
  with carpet, grass, fern or dead bush on top; wood only gets a moss carpet); vines on each facade up to 25%
  (`Weathering.VINES`), strands of 3 to 12 (`STRAND_MIN`/`STRAND_MAX`) where a noise is highest, 10% of the cells under
  eaves; cobblestone and stone bricks near the green turn mossy (50%). It never touches leaves, logs, glass, block
  entities, doors, water or partial blocks. In the 52 files: 65,082 blocks (38,500 vines, 10,190 soils, 5,088
  carpets, 4,946 plants, 3,907 mossy, 2,451 cracked).
- Loading (`core/Preload`): the 52 templates are read and weathered in parallel, then the walks of `Access` and
  `Rooms` worked out in parallel from each `Structures.Shape`; the block state ids are given out afterwards in the
  order of the index (the world depends on that order). Code run on other threads must not touch `Structures` while
  it is initialized. ~4.3 s outside the game.

### Loot and blocks
- Loot tables of its own, vanilla items only, written by `tools/gen_loot.py` in
  `data/abandonedmoderncity/loot_table/chests/<theme>/<tier>.json`, set when each container is placed (a seed per lot
  and position; `ChunkBuilder.loot`, `Palette.lootTable`). Theme by the structure's type (`Def.lootTheme`,
  `Structures.LOOT_THEMES`): `house`, `pharmacy`, `workshop`, `gas_station`, `building` (every building and the
  special one), `police_station` (the same tables as `building`, its own theme for add-ons). Tier by the container:
  chest `chest` (3 to 6 stacks, 10% a rare item), barrel, dispenser and dropper `barrel` (40% have 1 to 3 common
  stacks), decorated pot `pot` (40%, one stack); a container hard to get to uses `_hard` (`chest_hard` 5 to 8 stacks
  and 1 to 2 rare, `barrel_hard` 75% with 2 to 4 stacks, `pot_hard` 80% with one normal or rare stack).
- Hard to get to (`core/Access`): out of reach of a player walking in from the street (jumps 1, never over a fence,
  wall or pane; falls any height; climbs ladders, vines, scaffolding, water; opens doors, trapdoors and gates but not
  the iron ones; leaves block; the container 1 block beside where the player stands, from 1 below the feet to 1 above
  the head), listed by hand (the `hard` column of `index.txt`, `Access.Result.withHard`: the chest in the tree of the
  abandoned modern house), underground (at or under the ground layer), on the terrace (open sky, 4+ over the ground
  layer, at most 5 under the structure's top, `Access.ABOVE`, `TERRACE_TOP`) or, only in houses, in the attic (one
  ceiling between it and the sky, `Access.ceilings`). 125 of the 2,559 containers of the templates.
- Blocks (`gen/Palette`, every block the generator places, checked at startup by `Palette.validate`): any hardness
  but no unbreakable block. Block entities allowed: the fountain's chest and the containers of the structures
  (chests, barrels, brewing stands, decorated pots, lecterns, furnaces, beds, campfires, daylight detectors,
  jukeboxes, banners, skulls, dispensers, droppers, chiseled bookshelves; `Palette.exempt`); banners get the design
  saved in the template (`Template.banners`, applied by `Palette.applyBanner`), signs come out without text.

### Configuration, commands and determinism
- Config (`config/abandonedmoderncity-common.toml`, `AmcsConfig`): `enabled`, `cellSize` (734–8192), `cityChance`,
  `maxCityLevel`, `highways`, `highwayMaxLength`, `structureOvergrowth`. NeoForge keeps each value in its range.
- Commands (op 2, `command/AmcsCommands`): `info`, `locate city|highway|tunnel|bridge`, `locate structure`, `spawn`,
  `plan [radius ≤ 16]`, `audit [radius ≤ 8]`, `network [radius 500–1000]`, `column <x> <z> <y0> <y1>` (heights kept
  in the world, at most 384 lines); they run on the server thread, so the limits keep them to seconds. Images go to
  `amcs_renders/`.
- The plan is a pure function of the seed and the natural terrain, whatever the order of the chunks or the thread
  (cities decided and highway paths sampled on several cores, `WorldPlan.citiesIn`, `Highway.computeDetail`); the
  natural decoration next to a city can differ with the order of generation.
