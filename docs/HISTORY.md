# History of Abandoned Modern City (until the review for 1.0.0)

`CLAUDE.md` holds the working rules and the specification as it is now. This file keeps how it got there: the
dates, what each change replaced and why, the values and choices that were tried before, and the measures of each
step. It is the text of `CLAUDE.md` as it stood on 18 Nov 2026, before it was split (review for 1.0.0), copied
unchanged; the problems found and fixed, with their numbers (findings), are in `docs/TESTING.md`, section 3.

## The world fingerprint and what changed it (rule 7 as it stood)

7. **World fingerprint** (since 12 Nov 2026): `FingerprintTest` keeps what the planner produces (the values of commit
   `d960e99`; `layout`, `structures` and `weathering`, which hold the names, since the English names; `layout` again
   since the special structures in ~1 city in 10, commit `0f27bc0`; `structures` again since the loot theme
   `building_large` and again when it was taken out; `lots` since the barrels of the buildings, `core/Barrels`; `lots` and `structures` again since the hard
   containers underground, on the terrace and in the attics; `structures` again since the height classes of the
   buildings (and again with the margin of 3 blocks), their type and category: the blocks did not change; `lots` and `structures` again since the chests saved in the
   templates of the buildings were taken out, 15 Nov 2026; `lots` again since each copy of a building got its drawn
   chests, `core/ChestDraw`, and again when the drawn chests underground or on the terrace became hard to get to, and
   again when the barrels that hold something stayed barrels; `layout`, `chunks`, `lots` and `crowns` since each city
   draws 1 or 2 gas stations, 16 Nov 2026; `lots`, `structures` and `weathering` since the 3 chests added to the medium
   workshop, 17 Nov 2026; `layout`, `chunks`, `lots` and `crowns` since every city has exactly 2 gas stations, never 2
   large ones, 17 Nov 2026) and the battery
   prints per target `mod blocks:` (the blocks the mod decides in the generated world; the reference is in
   `docs/TESTING.md`, section 6, with another one for `structureOvergrowth = false`). In a change that must not alter
   the world (cleanup, splitting files, comments), the test passes and the `mod blocks:` and `stats:` lines stay the
   same (except `loot_containers_anywhere`, which counts vanilla chests, and `vegetation_checked`, which counts vanilla
   trees too). What the battery does not cover (bridge
   vines, fill, slopes, liquid sealing) is checked, in a change that only moves code, by an identical bytecode listing
   (`javap -c -p`) of the moved methods. The tools are in `tools/verify/` (`compare_battery.py`, `javap_dump.sh`,
   `compare_methods.py`, `compare_block_detail.py`, `chunk_diff.py`; since 16 Nov 2026 `structure_cells.sh` with
   `compare_structure_cells.py`, the 52 structures cell by cell between two jars, and `chest_walk.sh`, copies of the
   buildings walked again with their barrels and drawn chests; since 18 Nov 2026 `floating_scan.py`, the natural
   vegetation floating in a saved world, and `world_column.py`, the blocks of a column of a saved world) and the reference reports in
   `tools/verify/reference/`. In a commit that changes the world on purpose, update the expected values of the test and
   the battery reference, and say so in the commit message.

## Project (as it stood)

Abandoned Modern City (mod id `abandonedmoderncity`, package `com.abandonedmoderncity`, GitHub repository
`RakkuLyc/abandoned-modern-city`, folder `Projetos Claude/AbandonedModernCity`). It started on 7 Nov 2026 as a copy of
Abandoned Simple City (`RakkuLyc/abandoned-simple-city`, folder `AbandonedModernCity-Simple`) with the same system of
highways, tunnels, bridges, city blocks, parks and fields, **without the buildings** (and without the small houses of
the 3 × 2 towns) and without the commit history. The buildings are the user's `.nbt` structures (52 files since 13 Nov
2026: 30 models, 6 of them with color variants; 14 on 12 Nov, 7 more and the 22 variants on 13 Nov, 7 more and then 2
more; the first 33 were removed on 10 Nov). The original Abandoned Modern City (folder `AbandonedModernCity-Deprecated`)
is discontinued. Still on 7 Nov 2026 the user had the octagonal cities and the 3 × 2 highway towns removed (with the
rural_highway pattern near them and the fields), then the 4 × 4 grid replaced by 2 × 2 with cells of 1024, then the
blocks changed to 105 × 60 with cells of 512, and on 8 Nov 2026 the blocks to 140 × 90 with cells of 576 (to fit the
structures of the `structures/` folder); on 10 Nov 2026 streets and highways became 33 wide (cells of 640); on 11 Nov
2026 the cities moved to the middle of cells of 648 and got 4 entrances; on 12 Nov 2026 the blocks became 180 × 120
(cells of 736), with gaps between the structures: today there are only the 2 × 2 cities of rectangular blocks.

## Specification of the streets and cities, with its history (as it stood on 18 Nov 2026)

- Rigid grid, straight streets and right-angle corners: city = 2 × 2 blocks of 180 × 120 of free ground
  (`Params.GRID` = 2, `Params.BLOCK_LONG_RUN` / `BLOCK_SHORT_RUN`; street axes 213 and 153 apart, `BLOCK_LONG` /
  `BLOCK_SHORT`), 3 × 3 streets and 9 crossings, ~459 × ~339 blocks with the streets. Each city draws (50%) whether
  the long side of its blocks runs east-west or north-south (`City.longX`); all the blocks of the city follow the same
  orientation. Every city is like this (there are no other variants).
- Water is filled, hills are cut. Cities in the middle of cells of 736 (`Params.DEFAULT`), 736 blocks between
  neighbouring centers; the gap between the edges (the length of the highway) is between ~277 and ~397, depending on
  the orientation of the blocks (the user asked on 11 Nov for 276–360; with the blocks of 180 × 120 the difference
  between the sides of the city became 120). No position jitter: a cell whose middle is over water or too high has no
  city. Every city reaches 230 from its center (half the long side, `Params.GRID_RADIUS`). The area of influence
  (`Params.influence`) is the exact half diagonal of the city's rectangle (230 × 170 → 286) + 10 + a slope of 32 + 4 =
  ~332; with it the cell needs at least 2 × 335 + 64 = 734 (`Params.MIN_CELL_SIZE`; `Params.margin`: the influence
  rounded up, + 2). The config has no city radius (`minRadius` and `maxRadius` were removed in the review for 1.0.0, 18
  Nov 2026: neither changed the cities nor the highways; only `cellSize` sets the distance between cities and so the
  length of the highways). A `cellSize` under 734 in the config file is raised to 734 by NeoForge (it moves a value out
  of its range to the nearest limit, keeps the other values and rewrites the file; checked on a server: 600 → 734, the
  old keys removed); until 18 Nov 2026 the mod then dropped the whole config and
  used the defaults.
- A street 33 wide (10 Nov 2026): 3 `smooth_stone` (sidewalk) + 27 `polished_deepslate` (lanes; until 12 Nov it was
  `polished_blackstone`) + 3 `smooth_stone` (`Params.STREET_HW` = 16.5, `STREET_LANE_HW` = 13.5).
- A street segment of 180 or 120 blocks (the side of the block) + 33 of crossing; a 3 × 3 square of `smooth_stone` at
  each corner of a crossing.
- Center line on the 17th block of the width (the middle one), dashes of 11 with gaps of 6 (as many as fit,
  centered); `white_concrete` in the city, `yellow_terracotta` on the highways.
- Street lights (asked by the user, from the example `poste_exemplo.nbt`; `StreetLights.lampCandidate`,
  `StreetLights.lampParts`, `ChunkPlan.F_LAMP`/`F_POLE`): on every street segment of the city, one every 24 blocks
  (`StreetLights.LAMP_EVERY`) on both sides, facing each other, centered on the segment (7 a side on the segments of
  180, 5 on those of 120; away from the crosswalks and out of the crossings). A post of 13 on the sidewalk row next to
  the lanes (not drivable), an arm of 4 slabs at a height of 12 over the lanes (above the headroom of 7), a glass lamp
  (unlit) under its end. Abandoned (13 Nov 2026, asked by the user: "vines, dark tones, mossy blocks"): 10% are gone
  (`LAMP_GONE`) and the others are one of 5 variants, ~18% each: mossy (mossy cobblestone and bricks, a mossy slab,
  gray glass, a vine climbing the back of the post from 3 to 10 and 2 vines under the arm), dark (polished, cracked
  and cobbled deepslate, a deepslate slab, tinted glass, vines), broken (andesite, tuff and deepslate, only 2 of the 4
  slabs of the arm, no lamp), fallen lamp (the whole arm, only a chain at its end, the gray glass fallen on the lane
  under it: that column is not drivable) and overgrown (mossy bricks and moss block, a tuft of persistent leaves on
  top, vines on the back of the post from 3 to 12 and under the arm). The vines start 3 up (the tall grass of the
  sidewalk is 2 high) and under the arm they hang only 1 block (a vine holding by its top holds no other); over the
  lanes nothing is lower than 11 but the fallen lamp. The neighbour repair puts the parts back (and the cleanup of
  hanging plants spares them); the audit checks them (`street_light`, `street_light_blocks`).
- Crosswalks (11 Nov 2026, `City.crosswalk`): at each end of every street segment, 2 lane rows after the square of
  the crossing, a zebra of 3 rows (`CROSSWALK_GAP` = 2, `CROSSWALK_DEPTH` = 3) with stripes of `white_concrete` 1 block
  wide with gaps of 1, parallel to the street, across the 27 lanes (14 stripes, the two outer ones white); zone
  `ChunkPlan.CROSSWALK`.
- 4 entrances per city (`City.EXITS`), one per side, drawn: each side uses its middle 3-way crossing or one of the two
  corners of that side (never the same crossing twice). Drawing any 4 of the 8 positions was tested and rejected (it
  lost 23% of the highways to sharp curves). At a corner the highway continues, outwards, one of the two border
  streets (it leaves straight, never at 45°); each crossing gets at most one highway. Each link is agreed by both
  cities. The entrance is on the side facing the destination city (the direction pointing most toward it) (the
  entrance drawn on that side; if it already serves another highway, the next best direction). Gabriel neighbours
  that are among each other's 4 nearest are linked.
- Highways as wide as the streets, 33 (10 Nov 2026): 3 `smooth_stone` (the outer one with a `stone_brick_wall` on top;
  `Params.HIGHWAY_SHOULDER` = 2 of shoulder), 27 `polished_deepslate` (a `yellow_terracotta` line in the middle), 3
  `smooth_stone` (the outer one with a wall). The walls are connected by the plan (not by vanilla); at the junction
  with the street there is no wall. Inside the cities the lots have priority. The highways go straight from one city
  to the other. The narrowing near the cities was removed (the widths are equal).
- Abandoned streets (12 Nov 2026, `core/RoadDecay`): ~30% of the road columns taken over (it was 10%), in patches
  (value noise, cells of 5, above 0.66) and scattered cracks (6%), a pure function of the position and the seed. They
  apply to streets, lines, crosswalks, sidewalks and shoulders of the cities, the highways and the bridges; the
  tunnels, the half-slab steps of the ramps and the wall column stay clean. The columns not taken have a worn texture
  (`RoadDecay.texture`): on the lanes, patches and scattered blocks of `polished_blackstone`, `cobbled_deepslate`,
  `deepslate_tiles`, `cracked_deepslate_tiles`, `blackstone` and `smooth_basalt` on the `polished_deepslate` (which
  stays at ~54%); on the sidewalk, `andesite`, `cracked_stone_bricks`, `stone_bricks` and `cobblestone` on the
  `smooth_stone`; on the ramps, slabs of the same stones; 20% of the painted lines (center, crosswalk, yellow) faded.
  A taken column keeps its height (no step for vehicles) and only gets plants without collision: grass
  (`grass_block`) with short grass, tall grass or a fern; moss (`moss_block`), sometimes with short grass (no moss
  carpet, which has collision); `coarse_dirt` or `gravel`; cracked asphalt (`blackstone` or `cobbled_deepslate` on
  the lanes, `cobblestone` on the sidewalk). The neighbour repair and the audit use the same rule (`surface_block`,
  `road_plant`).
- Bridge vines (12 Nov 2026, asked by the user): in 35% of the columns just outside the edge of a bridge (the one with
  the wall), a strand of vine held by the deck hangs from the surface down to the first block that is not air (ground
  or water); done in every chunk, also those with nothing planned (`ChunkBuilder.bridgeVines`).
- Abandoned bridges (12 Nov 2026): lanes taken and worn like the streets; the deck (the block under the surface) and
  the pillars in patches of `mossy_stone_bricks`, `cracked_stone_bricks`, `mossy_cobblestone` and `stone_bricks`
  (`RoadDecay.bridgeStone`); the wall broken in stretches of 1 to 3 blocks (`RoadDecay.wallGap`, a noise of 2.5 above
  0.8) and mossy in stretches (`mossy_stone_brick_wall`, ~half); holes through the deck, ~1 per 80 blocks of bridge
  (`Wreck.holes`), 5 to 7 blocks across and 4 to 5 along, with an irregular, smooth outline (no islands of lane), their
  middle up to 6 from the axis, 8+ blocks from the ends of the bridge.
- Tunnels without obstacles: cave-ins (a pile of stone under a hole in the ceiling) were tested on 12 Nov 2026 and
  removed at the user's request; the lanes of the tunnels stay clear. The columns of the bridge holes are not drivable
  (`ChunkPlan.drivable`).
- Tunnels with 7 blocks of headroom (`Params.CLEARANCE`); there is only a tunnel where there are 11+ blocks of terrain
  over the road (`Params.TUNNEL_COVER`).
- No cut streets: every block has its 4 streets. The random removal of street segments (switched off since the 2 × 2
  grid, `City.MAX_CUTS` = 0) was taken out in the code review (13 Nov 2026, asked by the user); it remains in the git
  history (commit `d960e99`).
- City blocks: all get a ring of structures (see below) and the rest (the core) is taken over by nature (see
  "Nature"). ~1 block in 8 (`CityRings.PARK_BLOCK_CHANCE`) has a park in a corner (13 Nov 2026, asked by the user; before,
  the park took the whole block): 110 × 64 (`CityRings.PARK_LONG`/`PARK_SHORT`, its long side along the long side of the
  block), corner drawn; the structures stand along the two other streets, in an L, and never less than 4 blocks from
  the park (`Ring.lots` with `keepOut`); ~5 structures per park block.
- Nature (11 Nov 2026, `core/Flora` + `core/Wild`, a `WILD` lot covering the whole block under the structures, which
  win where they stand): an abandoned look. The ground in patches (value noise): grass (`grass_block`) for the most
  part, `moss_block`, `coarse_dirt` and the old concrete (`gray_concrete_powder`) showing through. The mod's own
  trees, ~1 per 55 columns with trunks 5+ blocks apart: oak (38%), birch (24%), dark oak (18%) and spruce (20%), 30%
  large (crown radius 3–4, conical spruce up to 12 high), crowns with irregular edges. Real trees (11 Nov 2026):
  leaves **not persistent**, each with its distance to the trunk (1 to 6, counted through the leaves as vanilla does,
  `Flora.Tree.dist`); leaves of the shape that would lie more than 6 from the wood are not placed. Once the trunk is
  cut, the leaves fall by themselves (leaf decay). Bushes: leaves (distance 1 and 2) on a trunk hidden in the ground.
  Hanging vines (35% of the columns at the edge of the crown, 1 to 4 blocks; they only stay when held by a leaf or a
  vine). Crowns and vines never leave the block or enter the structures. Plants on the soil (never on the concrete):
  grass, tall grass, fern, large fern, moss carpet on the moss, bushes (leaves on a buried trunk). Rubble:
  `mossy_cobblestone`, `cobblestone`, `cracked_stone_bricks`, `gray_concrete` (1 or 2 high) and a slab of
  `mossy_stone_bricks`, 1.2% of the columns on the grass and 4 × that on the concrete.
- Parks (`Park`): taken over by the same nature (varied trees with vines, ~1 per 60 columns, no trunk on a path and no
  crown over the ring; ferns, moss, bushes, rubble at 0.6%), with a few flowers left (poppy, dandelion, azure bluet,
  tulips, oxeye daisy, cornflower, 5%); paths of `dirt_path` 3 blocks wide with 30% of gaps (grass, moss or coarse
  dirt), in two variants (50%): a "tic-tac-toe" grid (2 + 2 straight paths 16 from the center) or a pinwheel (a circle
  of radius 16 + 4 spiral arms); distances scaled with the size of the park (base `Park.BASE_SIZE` = 68 on the short
  side; scaled by the short side: the park of 110 × 64 stays at ×1). A center without trees: the fountain
  `waterfall.nbt` (the user's, in `resources/amcs/`) surrounded by mossy slabs (half blocks) from the ring to the
  leaf hedge of the fountain: only `mossy_stone_brick_slab` and `mossy_cobblestone_slab` (half each), 10% missing (11
  Nov 2026). No block of the fountain is removed: in 55% of the columns with room for a vine (air next to its leaves or
  stones) a strand of 2 to 6 blocks hangs from the highest point (`Park.VINE_MIN`/`VINE_MAX`). The stone floor around
  the basin (layer 0 of the template) turns mossy (11 Nov 2026, `Park.FLOOR_SWAP`): `stone` and `cobblestone` →
  `mossy_cobblestone`, `stone_bricks` and `cracked_stone_bricks` → `mossy_stone_bricks`; the basin, the water, the
  stairs, the lanterns and the chest do not change. An empty chest in 50% of the fountains (else `mossy_cobblestone`).
  Benches (13 Nov 2026, asked by the user; `Park.benches`): 2 `spruce_stairs` side by side on the edge of a path, their
  backs away from it (who sits looks at the path), and an open `spruce_trapdoor` at each end as an armrest; only where
  4 columns of grass in a row touch the path (never on it, on the ring of the fountain, on a trunk or under a low
  crown), 8+ blocks from each other (`BENCH_APART`); a target of 1 per 15 blocks of path (`BENCH_EVERY`), but the
  straight stretches of 4 blocks limit it: in the tests, 565 benches in 30 parks, 1 per 18.6 blocks of path; 20%
  broken (a seat and the armrest on that side gone, `BENCH_BROKEN`). High crowns pass over them; no plant grows
  through them.
- Police station and crash building (asked by the user after 13 Nov 2026): an abandoned police station (54 × 38 × 33,
  type `police_station`, at most 1 per city, the user's rule; `Structures.LIMITS`): ground on layer 3 (the 3 below
  buried), with a half-buried basement (rooms of `cave_air` on layers 1 to 5, floor on 0, an inner stairway) dug into
  the terrain; the rooms cross the street layer open (the same openings as the garage of the wide building: 405
  cells); 13 blue banners with the letters on the facade (design of the template), 3 redstone torches saved, leaves
  not persistent without a trunk become persistent; front on -Z (banners and the entrance stairs); 63 containers, 18
  hard to get to (the basement: 13 of them in its closed rooms); loot of the `police_station` theme. Crash building (91 × 44 × 90, two
  towers leaning against each other, `buildings_large`, 1 per city like the other buildings): ground on layer 0, front
  on -Z (stairs and door under the gap between the towers), 201 containers saved (68 chests and 133 barrels), all reached
  by its stairs; since 15 Nov 2026 its chests are those drawn in each copy (38–65) and those of its barrels.
- New structures (asked by the user after 13 Nov 2026, with vanilla loot): modern garage house (42 × 36 × 15), glass
  house (48 × 40 × 15), small building (23 × 31 × 41), brutalist U building (75 × 58 × 125), wide building
  (84 × 31 × 80, with underground parking), green tower (31 × 31 × 143) and ramp building (folder "Especial",
  102 × 72 × 134; type `special`, at most 1 per city, `Structures.LIMITS`; made rarer at the user's request after 13
  Nov 2026: ~1 city in 10, see the limits below). All
  with the front on -Z (a hypothesis from the templates: doors, the courtyard of the U, the garage ramp, the glass
  facade; to be checked in game with `/amcs spawn`) and the ground on layer 0, except the wide building: ground on
  layer 8, the 8 below buried; the garage (layers 1 to 7 of `cave_air`) is dug into the terrain, and on the ground
  layer the mouth of the ramp (5 × 15, on the front edge) and the stairwell stay open: air or `cave_air` with
  `cave_air` under it does not become city ground, nor the air of the ground layer joined to those openings (the top
  of the ramp, which has the ramp itself under it; fixed on 13 Nov 2026 after the user found two rows of terrain
  there) (`StructureLoader.openings`, `Def.opening`, `Lot.code`; 99 cells, all the air of its ground layer);
  `/amcs spawn` also digs the garage and opens the ramp. The ramp building only fits the rows of the short sides of the
  block
  (front of 102 in the 120, depth of 72 along the 180) or, on the long sides, when the woods drawn are narrow. Leaves
  saved as not persistent far from any trunk (modern garage house, glass house, brutalist, ramp building) become
  persistent, or they would fall in the world. The stairs of the glass house started against the glass wall of the
  front (one could only climb from the side); the template was replaced (13 Nov 2026, commit `55a8ce0`) with L-shaped
  stairs from the hall, a 2 × 2 landing in the corner and a continuous iron-bar railing up to the gallery. The 7
  already come overgrown; the overgrowth adds what is missing. The loot applies unchanged: the houses use the `house`
  theme, the buildings and the special one the `building` theme.
- Structure names in English (13 Nov 2026, asked by the user): files (`abandoned_house`, `pharmacy_red`,
  `workshop_small_green`, `gas_station_small_blue`, `abandoned_wide_building`, `abandoned_police_station`...), types
  (`houses`, `pharmacy_large`, `workshop_medium`, `gas_station_small`, `buildings_small|medium|large|immense`, `special`, `police_station`),
  the `/amcs spawn` categories and the limit groups (`house`, `pharmacy`, `workshop`, `gas_station`, `building` (since 15 Nov 2026 split by
  height into `building_small`, `building_medium`, `building_large` and `building_immense`), `police_station`, `special`) and the loot tables (`chests/<theme>/<tier>`, themes `house`, `pharmacy`, `workshop`,
  `gas_station`, `building`, `police_station`; tiers `chest`, `barrel`, `pot` and `_hard`). The overgrowth of each structure stays the
  same: its seed came from the file name and now comes from the `seed` column of `index.txt`, holding the value of the
  old name (`String.hashCode` of the Portuguese name). The originals in `structures/` (at the root) keep their
  Portuguese names. Unopened chests of worlds generated before this change point to the old tables and come out
  empty.
- Structures (`core/Structures` loads, measures and turns; `core/Ring` builds the ring): 52 `.nbt` files of 30
  models, copies in `src/main/resources/amcs/buildings/` of the originals in `structures/`, listed in `index.txt`
  (its header describes the columns): 12 houses (type `houses`: abandoned house, abandoned modern house, abandoned
  mansion, post-apocalyptic mansion, garage house, large house, pool house, suburban house, tower house, Victorian
  house, modern garage house and glass house, all abandoned); 6 shops with color variants, 28 files (pharmacy and
  large pharmacy, 5 colors each; medium workshop, 3; small workshop, 5; gas station and small gas station, 5 each);
  10 buildings, each at most 1 per city (apartment building, skeleton building, bluish building, tower, apocalyptic
  building, small building, brutalist U building, wide building, green tower and crash building); the ramp building
  (type `special`) and the police station (type `police_station`), at most 1 per city each. History: 14 structures
  on 12 Nov 2026 (the first 4 houses, the 6 shops, the apartment building, the bluish building, the tower and the
  apocalyptic building); 7 more on 13 Nov (the next 6 houses and the skeleton building: 21 models; the file
  `prédio_esqueleto_31x29.nbt` became `skeleton_building_31x29`, without the accent, because the argument of
  `/amcs spawn` only takes ASCII); then the 22 color variants (43 files), the 7 new structures (50 files) and the
  police station and the crash building (52 files, 30 models). The post-apocalyptic mansion was updated on 13 Nov
  with the new version of the folder (only the state of 12 doors changed). The houses are ~52% of the structures of
  the cities (39% of a ring without the per-city limits). Color variants (13 Nov 2026, asked by the user; the `model`
  field of the index): pharmacy (yellow, blue, purple, green besides the red one), large pharmacy (blue, purple,
  green, red besides the yellow one), medium workshop (yellow and blue, yellow and red besides the blue and black
  one), small workshop (yellow, blue, purple, red besides the green one), gas station (yellow, blue, purple, green
  besides the red one), small gas station (yellow, blue, purple, green besides the red one): 22 files, each variant
  of the size, front and ground of the original (99.6 to 100% of the blocks in the same place). The medium workshop
  (its 3 colors, the copies and the originals in `structures/Oficina de carros/`) got 3 chests in its `.nbt` (asked by
  the user on 17 Nov 2026, not part of the procedural chests): one in the work bay against the inner wall (18,1,10,
  facing west), one in the back room against the back wall (28,1,24, facing north) and one on the terrace behind the
  middle post of the sign (11,6,7, facing south), written like its first chest (`village_toolsmith` in the template,
  replaced by the `workshop` loot when placed); 65 containers each (62 before), none hard (the terrace one lies 10
  under the top of the sign, more than `Access.TERRACE_TOP`; it is reached by the vines of the overgrowth: with
  `structureOvergrowth = false` the roof has no way up and that chest is out of reach, so hard). The overgrowth moved 4 of its blocks: the vine it had
  put in the terrace cell of the yellow and red one, and one green spot of the roof of the yellow and blue one and of
  the yellow and red one (its share of the floors under the open sky).
  The ring draws the model (all equally likely, `Structures.pick`) and then the color: the variants do not change the
  frequencies. The pharmacy variants came without the banners; the index copies them from the original at load time
  (`Template.withBannersFrom`, field `banners` = `source@dx,dz:color=color`): on the small pharmacy the letters are in
  the variant's color on a white field (like the red ones of the original), on the large one the field is in the
  variant's color with black letters (like the yellow of the original); light blue, purple, lime and red on the large
  ones, yellow, blue, purple and green on the small ones. The column of banners on the side of the large pharmacy lies
  outside the template saved for the variants (25 wide): the template grows 1 block. Loading fails if a banner falls
  on a block or has no wall behind it. The pharmacy variants also had an F A R M Á C I A sign in colored blocks on the
  white wall (on the side of the small one, on the front and the side of the large one) that the originals do not
  have; at the user's request (13 Nov 2026) the index puts back the neutral blocks of the original where the variant
  painted them (`Template.withNeutralFrom`, field `neutral`: `white_concrete`, `smooth_quartz`, `calcite`,
  `polished_diorite`): 76 blocks on each small one, 152 on each large one; the rest of the variant's color stays. The
  variants match the original block by block in the same positions, with the banners in the same positions (until 13
  Nov those of the small ones were 1 block to the left: the first alignment went by the box of the solid blocks, not
  by the positions of the template). Rules decided by the user:
  - front = the -Z side of the `.nbt`, facing the street; the `front` field of the index turns the template when the
    front is another side: the abandoned modern house and the post-apocalyptic mansion have their front on +Z (180°,
    found by the user in game on 12 Nov 2026); the 7 of 13 Nov have their front on -Z (path and entrance steps on the
    north side of the template);
  - loose cobwebs (touching only air, cobweb, vine, plant or carpet) are removed from every structure (asked by the
    user for the post-apocalyptic mansion; 41 in all: 23 in the large house, 13 in the post-apocalyptic mansion, 3 in
    the abandoned mansion, 1 in the abandoned house, 1 in the apocalyptic building);
  - lightning rods become lit redstone torches (13 Nov 2026, asked by the user; `Structures.swap`, when the templates
    load, for any structure): standing when the rod points up or down, on the wall when it points sideways; today
    there are 5, one on top of each: tower house, post-apocalyptic mansion, apartment building, apocalyptic building
    and tower (the new structures already come with 12 redstone torches saved and have no lightning rods);
  - barrels of the buildings (asked by the user on 14 Nov 2026; `core/Barrels`, `Structures.Def.code(..., seed)`):
    in the 10 structures of the `building_*` categories (types `buildings_*`; not the police station nor the ramp
    building, which have their own categories), 70% of the barrels (`Barrels.GONE`) are taken out (air; no barrel is on
    or under the ground layer) and the others become chests (`chest[type=single]`, facing where the barrel's opening faced; a barrel
    facing up or down gives the first side, north, east, south, west, with air in front). The draw is per copy (the
    seed of the lot, or a random one for `/amcs spawn`, and the cell of the barrel in the template), so two copies of
    one building differ (in the test cities of the flat terrain, 205 copies, each with its own seed and its own
    chests); the chunk builder, the neighbour repair, the audit and the fingerprint all read it through
    `Lot.code`. A barrel that holds something is never taken out and stays the barrel of the template (asked by the
    user on 16 Nov 2026: until then it became a chest, and the counters of the tower's ground floor, 16 with a stone
    slab on top, 7 with a decorated pot, 1 with an iron trapdoor, gave ~24 chests in every copy, ~33 chests from
    barrels on that floor against ~9 now): a block standing on the top of
    its stack (the stone slabs of the counters and the decorated pots of the tower), something hanging under it, or a
    block fixed to one of its faces (a vine or lichen holding on it, a wall torch, sign, ladder, button...); a vine
    near it held by a wall or by the vine above does not count. 26 of the 794 barrels (tower 25, crash building 1);
    the others are taken out with a higher chance so that 70% of each building's barrels go (tower 84%). In a stack
    (only the tower: 14 stacks of 2 or 3) the barrels taken out are the top ones, so nothing floats. Measured over 200
    copies: 69.9% taken out (tower 70.0%, brutalist U 69.8%, crash 69.9%, wide 69.5%, green tower 69.8%), ~212
    chests and the 26 barrels that hold something in the 10 buildings (~239 chests while those 26 became chests; the
    tower 19.7 chests from barrels a copy, 44.7 before). The chests use the `chest` tier of the loot (or `chest_hard` where the barrel was
    hard to get to: the 7 of the apocalyptic building). The hard flags are still worked out once on the template with
    its barrels: with every barrel taken out, or every barrel a chest, no other container changes (`BarrelsTest`);
  - chests of the buildings (asked by the user on 15 Nov 2026, the first step of the procedural chests;
    `Structures.withoutChests`): the 556 chests saved in the templates of the 10 structures of the `building_*`
    categories (apartment 10, skeleton 16, bluish 15, tower 29, apocalyptic 36, small 19, brutalist U 168, wide 133,
    green tower 62, crash 68; all single, none trapped, none at or under the ground layer) are taken out when the
    template loads, after the overgrowth, and become air (cave_air under the ground layer). The overgrowth does not
    change (taking them out of the `.nbt`, before it, would have moved 47 of its blocks in the 10 buildings, so the
    user chose the load time); the box, the floor, the openings, the leaves and the access of every other container
    stay; the barrels still read the template with its chests (a barrel facing up turns into a chest facing its first
    side with air in front: 5 of them would have turned another way). The `.nbt` files, every other structure (the
    ramp building, 157 chests, and the police station, 29, included) and the park fountain keep their chests. The log
    says "Chests saved in the buildings' templates removed: 556 in 10 buildings"; `BuildingChestsTest` checks it. The
    new chests are drawn per copy (stage 3, below), with the hard loot where `core/Access` says so (stage 4); the
    barrels' chests do not count;
  - floors, rooms and places for a chest of the buildings (asked by the user on 15 Nov 2026, stage 2 of the procedural
    chests; `core/Rooms`, `Def.rooms`): worked out once per building when its template loads (after the overgrowth and
    the chests taken out); stage 2 placed nothing (the world did not change), stage 3 draws the chests on these spots. On the walk of `core/Access` (exposed as
    `Access.walk`, with the steps from the street; the reach rule `Walk.reaches` and the where-it-stands rule
    `Access.place` are the same code the hard containers use): floors = heights with 60+ cells where the feet stand on
    something solid (`FLOOR_MIN_CELLS`), no busier one within 3 (`FLOOR_GAP`), the other heights (stair treads,
    furniture tops, slanted floors) joining the floor up to 4 under them (`FRAGMENT_BELOW`); rooms = the plan of a
    floor cut at the doors and the passages 2 wide or less (`NARROW`); a room whose median width (pillars and tall
    furniture of 9 cells or less, `PILLAR_MAX`, do not count as walls) is 4 or less and that is 4+ times as long as
    wide is a corridor (`CORRIDOR_WIDTH`, `CORRIDOR_LONG`); rooms over 180 cells split in zones (`ZONE_MAX`). A spot:
    air in the game (cave_air under the ground) with air over it, on a full block (`Rooms.floorBlock`, a reviewed list
    that fails closed; `Palette.validate` refuses to start if one of them is not a full block in the game), against a
    wall (blocked to the head; a barrel never counts), a player standing beside it at its height in the walk with all
    the barrels and in the one without any, the cells around it left joined, no barrel within 1, no container beside
    it, no door, trapdoor, gate, ladder or scaffolding within 2 (`DOOR_CLEAR`), no stairs within 1 (`STAIRS_CLEAR`),
    not beside a passage or a door, not in a corridor, and indoors (a yard or a plaza inside the box gets none) unless
    4+ blocks up under the open sky (terraces and roofs), where a low parapet or a railing is a wall too (not leaves;
    the user's choice 1B, 15 Nov 2026); with its facing (toward where the player stands, the wall behind), weight (2 at
    a wall, 3 in a corner, +1 beside furniture) and flags: hard by `Access.place` (underground or terrace); open sky;
    far = the 20% of each floor's spots farthest from where the walk arrives on that floor (`FAR_SHARE`; the user's
    choice 4B: before it was from the street, so only the top floors); street = in a room at the street level with 24+
    cells on the edge of the box (`STREET_EDGE`: a lobby without a door joined to an arcade or a covered sidewalk; a
    doorway does not count), to get half the weight when the chests are drawn (the user's choice 2B). The skeleton's
    floor +17, a broken slab where only walkways 3 wide between holes and pillars are left, keeps 1 spot (the user's
    choice 3A). Since stage 3 a spot also needs air saved in the template (a cell with nothing saved keeps what the
    world has, so no chest could go there), and the cell in front of it and the feet one step up or down beside it must
    still be reached with the chest there (`Access.Walk.reachedAvoiding`, the same moves of the walk in a window of 8
    sideways and 4 up and down, `Rooms.CHECK_R`/`CHECK_RY`; in the walk with all the barrels and in the one without
    any): on slanted floors a chest could be the only way to the cell in front of it. Results (`RoomsTest`; floors,
    spots, room for chests 4 apart, `SPACING`): apartment 6, 234, 81; skeleton 8, 172, 59; small building 7, 389, 113;
    bluish 11, 2,150, 578; tower 14, 1,778, 538; apocalyptic 14, 3,301, 705; brutalist U 13, 2,373, 666; wide 14,
    2,395, 606; green tower 27, 1,447, 437; crash 18, 2,437, 725. Hard: wide 275 (the garage), crash 117, skeleton 14
    and green tower 9 (terrace); street: brutalist 251, bluish 217, tower 185, green tower 105, crash 78, skeleton 49.
    The roofs: wide +61 8 spots, brutalist +116 21, apocalyptic +79 none (only a tree beside it). Floor plans:
    `build/test-renders/rooms/`. With the drawing below it brought the loading of the structures from 11.5 to ~14.0 s (since 18 Nov 2026 ~5.0 s
    outside the game and 5.1 s in it: the `.nbt` read through a buffer, the questions of the overgrowth about the
    block names kept, the 52 templates read and weathered in parallel by `core/Preload`; then, review for 1.0.0, the
    walks of `Access` and `Rooms` in parallel too, from the shape of each template without the block state ids
    (`Structures.Shape`, `Preload.walks`): ~4.3 s outside the game, 4.2 s in it; the block state ids still in the order
    of the index; nothing produced changed, see `docs/TESTING.md`, section 5);
  - chests drawn in each copy of a building (asked by the user on 15 Nov 2026, stage 3 of the procedural chests;
    `core/ChestDraw`, `Def.chests(seed)`, placed by `Def.code(..., seed)` where the template has air, through
    `Lot.code` for the chunk builder, the neighbour repair, the audit, the fingerprint and `/amcs spawn`): a pure
    function of the copy's seed (the lot's) and of the template, not of the rotation, the position or the order of the
    chunks; each structure keeps the last 512 copies worked out (`Structures.DRAWN_KEPT`). How many: drawn evenly in
    the range of the height class, small 12–28, medium 25–47, large 38–65, immense 51–83 (`ChestDraw.range`; the chests
    of the barrels do not count). Per floor: in proportion to its weight, how many chests fit on it 4 apart with a
    street spot counting half (`Rooms.Layout.floorWeight`), largest remainders first (the user's choice "by capacity";
    not by raw spots, which favours the walls of the ground floor, nor equal, which gives a roof of 132 cells as much
    as a floor of 2,300). Which spots: a weighted draw without replacement (key `-ln(u) / weight`, u from the seed and
    the cell), the weight of a street spot halved (2B); never closer than 4 sideways within 2 heights to another
    chest (`Rooms.SPACING`), at most 1 per zone (`ChestDraw.ZONE_CAP`; a small room is one zone, a hall is cut in zones
    of 180 cells) until the floor has its share; when it does not fit, the zone cap is raised up to 4, then the floor
    shares are dropped (cap up to 8), then the cap goes (the spacing always holds). Exploration (far on its floor, open
    sky or hard): at least 15% of each copy (`ChestDraw.EXPLORATION_MIN`, the user's choice: a minimum, nothing
    reserved; a copy under it is drawn again with those spots first); the plain draw gives 25 to 43%. Chests
    `chest[type=single]` facing where the player stands (`Rooms.FACINGS`, turned with the structure); loot of the
    `building` theme, `chest` or, on a spot hard to get to, `chest_hard` (stage 4, 16 Nov 2026: `Def.hard` marks the
    cells of every hard spot, `Structures.hardSpots`, drawn or not, since the flag is only read where a container
    stands; by `Access.place`, underground or on the terrace, so only the wide building, 275 spots in its garage, the
    crash building 117, the skeleton 14 and the green tower 9 on their terraces; 5.6, 1.8, 2.1 and 0.2 hard chests a
    copy; no spot is a container of the template nor a barrel, so their flags do not change; the range of chests comes
    from the type, `buildings_<class>`). Since stage 5 (16 Nov 2026, the user's choice A) the world audit also checks
    the loot of the containers in the buried layers of a structure (the garage of the wide building, the basement of
    the police station), which it skipped before: all hard, none with a wrong table. The battery of the 4 seeds: 20261027 and
    3478477863671517581 pass; -3297019724594858492 (a vanilla village chest under the floor of a structure, finding
    75) and 1 (the leaves of a vanilla tree in a bridge hole, finding 76) failed the same on the code before stage 1;
    since 18 Nov 2026 both are fixed (below: the foundation of the lots and the holes of the bridges) and the 4 seeds
    pass. The 12 checks the user asked for
    and what proves each are listed in `docs/TESTING.md`, section 2.
    Measured (`ChestDrawTest`, 200 copies each): 20.0 chests on average in the small buildings, 51.5 in the large
    ones; on the street floor 3 to 20% (apartment 20, small 16, brutalist 11, wide and apocalyptic 9, tower 6, bluish
    and crash 4, green tower 3; skeleton 13), every floor whose share is one chest or more gets some; exploration 25 to
    43% (never under 15% in a copy); more than 1 per zone only in the apartment (2, 67 copies) and the skeleton (3,
    every copy, also in whole rooms of 94 to 170 cells; the apartment only in pieces of halls), which have few rooms
    with spots. Walked again, 200 copies of each (2,000): every chest reached, someone in front, no container cut off. 40 copies of each walked again with their barrels and chests: every
    chest reached with someone standing in front, no container cut off. In the battery the block entities and the
    loot containers of the structures went up by the same number in every target with buildings (t01 1,013 → 1,192),
    `structure_loot_hard` and every other count unchanged;
  - banners with the design saved in the template (`Template.banners`, applied when the banner is placed,
    `Palette.applyBanner`): the letters D R U G S T O R E of the pharmacies (9 on the pharmacy, 18 on the large
    pharmacy); against the sidewalk, forming a ring; drawn per file, no pattern;
  - limits per city (`Structures.LIMITS`): pharmacies 3 (both kinds), workshops 2 (both kinds), gas stations exactly
    2 in every city, never 2 large ones (asked by the user on 17 Nov 2026: 2 small or 1 small and 1 large, half the
    cities each, `CityRings.twoSmallGasStations`; until 16 Nov the rings drew up to 2, then up to 1 or 2 drawn per city, and
    3.7% of the cities had none). The rings never draw a station (the `gas_station` group starts full); before them
    `CityRings.gasStations` reserves the 2: the color drawn, 2 different blocks of the city, in each a row drawn of the 4 and
    its 1st or 2nd place (`Ring.Forced`); one that does not fit there (too deep or too wide for what is left of the row,
    the row closed before its place, or the block already has that model) goes on to the first place of the next row,
    then of the next block; one still due after the last block is moved to the first place of the first row of its
    block (then of the next block) and the city drawn again. Every city changed. In the test cities (1,022 by cell and
    level) all have 2, 55.5% two small, 51 with both in one block (a small and a large: never two of one model in a
    block); over 2,883 distinct cities of 50 world seeds (flat terrain) all have 2, 50.3% two small; special 1 (the ramp building), police station 1; the `special` category only in the cities that draw
    `CityRings.SPECIAL_CHANCE` = 0.40 (`CityRings.allowsSpecial`, from the city's seed; asked by the user after 13 Nov 2026:
    "around 1 in 10 cities"): the others start with the group full. The ramp building only fits some rows (a city that may
    have it gets it ~27% of the time): over 2,838 distinct cities of 50 world seeds, 10.5% have it (39.0% may);
    in the test cities (1,129, but only 170 distinct: the 7 terrains repeat the cities) 12.3% (11.9% with the gas
    stations drawn 1 or 2, 11.2% before that), before 22.7%. Only the cities that
    had it changed (they get other structures there); and each building (types `buildings_*` and `special`,
    `Def.onePerCity`) at most 1 time per city, in any block (13 Nov 2026, asked by the user: there were cities with 2
    or 3 bluish buildings; houses, pharmacies, workshops and gas stations may repeat within the limits); 6.35
    buildings per city in the tests (6.38 with the gas stations drawn 1 or 2, 6.30 before that); the rings of the whole city are drawn at once (`CityRings.rings`, blocks from north to
    south, west to east), and a group at its limit is drawn again;
  - never two of the same structure (the same model, in any color) in one block (13 Nov 2026, asked by the user): a
    structure already in some row of the block is drawn again (`Ring.row`); before, 80% of the blocks had some
    repeated one; in neighbouring blocks it may appear again;
  - box = the box of the solid blocks; the `ground` layer of the index lies at the level of the city ground and those
    below are buried: abandoned mansion, tower, bluish building and apartment building go 1 block down (asked by the
    user; buried air becomes dirt); an exception (12 Nov 2026, asked by the user for the underground parking of a
    building): the `cave_air` of the template becomes real air also below the ground, digging the terrain
    (`Structures.CARVE_STATE`); plain buried `air` still becomes dirt, so the existing structures do not change; the
    large house too (13 Nov 2026: its layer 0 is all dirt and the grass of the yard is on layer 1); the other new
    houses have the grass yard on layer 0, which lies at the ground level;
  - no gap to the sidewalk: on the ground layer, the edge rows without a real floor (less than 25% of floor, only
    vines, banners or loose slabs) get the floor block of the first row that has a floor, on all 4 sides
    (`StructureLoader.floorFill`); the decorations (pharmacy banners, workshop vines) stay;
  - trees of the structures with leaf decay: leaves up to 6 (through the leaves) from a trunk of the template become
    not persistent with the right distance; leaves with no trunk near (hedges, bushes) stay or become persistent (even
    those saved as not persistent);
  - crowns cut by the edge of the template are completed outside the structure (13 Nov 2026, asked by the user for the
    tree in front of the pool house; `StructureLoader.crowns`, `core/Crowns`, `gen/Overhangs`): each real leaf of a
    crown (up to 4 blocks horizontally from a trunk of 3+ natural logs, not stripped, and 2+ above its foot) is
    mirrored around the trunk, in x, in z and in both; a mirror that falls outside the box becomes a leaf not
    persistent, its distance counted through the leaves (up to 6). That is 189 leaves in 7 structures: pool house 11
    (over the sidewalk, 13–16 above the ground), garage house 23, suburban house 20, tower house 15, skeleton building
    105 (in the woods of the core), glass house and ramp building (15 the two). Over a street or sidewalk only more
    than `Params.CLEARANCE` (7) blocks above the surface; in the woods or a park where it plans nothing; never in
    another structure; only where there is air. The neighbour repair and the cleanup of orphan leaves keep them; the
    audit checks them (`crown_leaves`, `crown_leaf_missing`);
  - the trees of the cores never enter the structures (1 block of clearance) and the structures never overlap (the
    trees of each structure stay inside its box, except the crowns completed above);
  - gaps (12 Nov 2026, asked by the user): the structures never touch; between two neighbours of a row, and between
    the deepest one of a short-side row and the long-side rows, a gap of 4 to 17 blocks is drawn
    (`Ring.GAP_MIN`/`GAP_MAX`; it was 10 to 19, reduced by the user the same day); between the backs of opposite rows
    there is always the woods of the core, with a clearance drawn from 20 to 30 per pair of sides
    (`Ring.CORE_MIN`/`CORE_MAX`);
  - the rows of the short sides of the block run corner to corner and those of the long sides fill what is left
    between them (the other way round left the short sides nearly always empty); ~7.8 structures per block with the
    52 files (the 4 rows with ~25% each; in the cities, with the per-city limits, 7.3 in a block without a park; 8.2
    with the 21 structures of 12–13 Nov, 8.4 before the no-repeat rule), 52.5% of the free ground covered (51% with
    the 21). Block size: 180 × 120, recommended after simulating the gaps (140 × 90 fell to ~5 structures and the
    tower, the bluish building and the apocalyptic building became rare).
  - commands (12 Nov 2026, `command/StructureCommands`): `/amcs locate structure <category> [name]` (asked by the user
    after 13 Nov 2026: the categories of `spawn`; the category alone finds the nearest of any structure of it, with a
    name that structure; a name of another category is refused with the right list; the old form by name, type or
    group no longer exists) (the nearest one already planned; coordinates on the sidewalk in front of it) and `/amcs spawn <category> <name>` (13 Nov 2026,
    asked by the user: categories `house` 12, `pharmacy` 10, `workshop` 8, `gas_station` 10, `building_small` 3,
    `building_medium` 0, `building_large` 7, `building_immense` 0, `police_station` 1, `special` 1, by the start of the
    type; the buildings by height class (next item), `Structures.CATEGORIES`/`Def.category`; the game
    suggests only the names of the chosen category; a name of another category is refused with the right list; the
    old one-argument form no longer exists) (2 blocks in front of the player, centered, its front facing the player,
    its ground on the block the player stands on; the same rules as in the city, with loot);
  - height classes of the buildings (asked by the user on 15 Nov 2026): by the height of the box of the template with
    the buried layers (`Def.h`, the number of the table of `docs/DEVELOPMENT.md`): **small** up to 40 (`Structures.HEIGHT_SMALL`),
    **medium** up to 75, **large** up to 140, **immense** above 140, each limit with a margin of 3 blocks
    (`Structures.HEIGHT_MARGIN`, asked the same day: a building up to 3 over a limit stays in the lower class; the
    user chose this direction, not the one that moves up a building 3 under a limit): small up to 43, medium up to 78,
    large up to 143. The `type` of `index.txt` is `buildings_<class>` (`Structures.heightClass`) and the category of the
    commands `building_<class>`: small 3 (apartment building 37, small building 41, skeleton building 43), large 7 (wide
    building 80, 8 of them the buried garage, counted whole, the user's choice; crash building 90, bluish building 106,
    apocalyptic building 110, tower 118, brutalist U building 125, green tower 143), medium and immense none (their
    categories exist; the commands say "no structure in category ... yet"). Without the margin the skeleton building
    and the small building were medium and the green tower immense. Only the 10 buildings: the ramp building (`special`,
    134) and the police station (33) keep their categories. The class changes neither the loot (the theme stays
    `building`, every building) nor the barrels (`Structures.isBuildingType`) nor the drawing of the ring or the
    one-per-city rule (the world does not change; only the `structures` part of `FingerprintTest`, which hashes type and
    category); `RingTest.buildingsAreClassifiedByHeight` fails when a building has the type of another height.
- Overgrowth of the structures (13 Nov 2026, asked by the user: "nature taking over", modeled on the apocalyptic
  building; `core/Weathering`): applied to each template when it loads, before anything that reads it (real leaves,
  access of the containers, crowns), without touching the `.nbt` files; **switched off with
  `structureOvergrowth = false`** in the config (read at startup: restart the game; the log says "Structure
  overgrowth on (N blocks changed)" or "off"). It only adds what is missing to reach the measures of the apocalyptic
  building (whoever already has more stays as it is; in the stone and the floors the building itself, the skeleton
  building and the modern house do not change), above the ground layer and only inside the box of the structure (same
  size and place in the ring):
  - cracked stone: `stone_bricks`, `deepslate_bricks`, `deepslate_tiles`, `polished_blackstone_bricks` and
    `nether_bricks` turn into the cracked ones in patches (value noise) up to 31% of the family (in the building:
    9,863 of 31,332);
  - green on the floors: scattered spots on the full blocks with air above, up to 8.2% of those under a roof and
    35.8% of those under the open sky; a stone or concrete floor becomes `moss_block` (63%), `grass_block` (20%),
    `coarse_dirt` (7%), `rooted_dirt` (5%) or `dirt` (5%), with a moss carpet, short grass, a fern or a dead bush on
    top in the proportions of the building; wood and other full blocks only get a `moss_carpet`;
  - vines on the facades (13 Nov 2026, asked by the user after the first test: "longer, aggressive and spread on all
    sides"; before they were strands of 1 to 3 up to the building's measure, ~5% of the facade): on each of the 4
    sides, up to 25% of the outer facade (`Weathering.VINES`; cells of air next to a full block that see the outside
    of the box) covered, also on the apocalyptic building, the skeleton and the modern house (whoever already has
    more keeps it); strands of 3 to 12 blocks (`STRAND_MIN`/`STRAND_MAX`, ~6 on average) that hang down to a floor or
    the ground, starting where a value noise over the facade is highest (patches that cover stretches of wall and
    leave others bare); the cells below the first one hold on the vine above; 10% of the cells under eaves (outside)
    get a vine held by its top;
  - `cobblestone` and `stone_bricks` (and their stairs, slabs and walls) up to 2 blocks from the new green turn mossy
    (50%).
  It never touches leaves, logs, glass, block entities (containers, banners, beds...), doors, water or partial blocks;
  plants only on soil, carpets only on a full block, vines only held by a full block or by the vine above. In the 52
  files: 38,500 vines, 10,190 floors turned into soil, 5,088 carpets, 4,946 plants, 3,907 mossy, 2,451 cracked (65,082
  blocks; 65,086 before the chests of the medium workshop). The measures before and after of each structure are in `docs/TESTING.md`.
- Floating vegetation (18 Nov 2026, asked by the user: in cities built where there were mountains, pieces of trees,
  vines and snow floated well above the ground; `gen/Floating`, finding 78): a chunk decorated after its neighbour was
  built grows trees on the old, uncut ground whose branches reach into that neighbour; when the city cuts the trunk
  the rest stayed (the cleanup of crowns over roads and lots stops 48 blocks up, the slopes had none, a piece of branch
  kept its leaves "alive" for the cleanup of orphan leaves, snow and vines rested on removed leaves). After every chunk
  (`ChunkBuilder.removeFloating`), starting from the loose blocks next to what it opened (from 1 under to 1 over the
  opened range of each column, in the column and its 8 neighbours), around the orphan leaves it removed and around
  what the neighbour repair removed, every group of natural decoration (`ChunkBuilder.isDecoration`: leaves not
  persistent, logs, vines, snow layers, plants, huge mushrooms...) joined by a face, an edge or a corner that touches
  nothing holding it is removed, at any height: holding = any other block that is not air (ground, stone, water, a
  structure), a block the plan places there (`Floating.Planned`: lot codes other than keep and air, the leaves
  completing structure crowns, street light parts), or the edge of the 3 × 3 chunks it may write (the chunk built next
  to it looks again); a group of more than 20,000 blocks counts as standing (`Floating.MAX_GROUP`). The planned blocks
  do not change (in the battery every `mod blocks:` line stayed the same); removed in the battery: 3,517 blocks in
  seed 20261027, 4,020 in −3297…, 8,495 in 1, 8,780 in 3478…; the generation ~8% slower per chunk. The world audit
  checks it (`vegetation_floating`, with the loose blocks checked in `vegetation_checked`): before the fix 40, 54 and
  42 floating groups in three seeds, after 0 in all 4. Worlds generated before keep what floats in their chunks. Since the review for 1.0.0
  (findings 79 and 80: a vine hanging on nothing under jungle leaves and grass over a lava lake dug later, both
  Minecraft's own, also with the mod off) the audit fails a group only within 7 columns (`Floating.MOD_REACH`) of a
  road, lot or tunnel wall or of a slope whose ground the mod moved; the others are counted in
  `vegetation_floating_vanilla`.
- Planning on several cores (18 Nov 2026, asked by the user: a new world took ~13 s before its first chunks, 4,174
  terrain samples of ~2.9 ms): the cells of cities not decided yet in a `WorldPlan.citiesIn` (9 samples each; the
  highways near a chunk need the cities ~4,400 blocks around and their neighbours) are decided in parallel, and the
  samples of a highway's path are taken in parallel before its loop reads them (`Highway.computeDetail`); both are
  pure and cached, so the plan is the same (`FingerprintTest`); ~13 → ~4 s, and in the battery 2.9–3.4 → 1.8–2.1 ms
  per chunk. Not every block stayed the same: in seed 3478… t02, 2 of 1,369 chunks differ (each code repeats its
  own result), with vanilla ore and stone blobs that the mod never places and 3 leaves of a lot; hypothesis: the order
  in which the chunks finish changed, as it already could from one run to the next (seed 1 t01 differs between two
  runs of the old code; finding 65; `docs/TESTING.md`, section 6).
- Foundation of the lots (18 Nov 2026, asked by the user, finding 75: a vanilla village chest and beds, caves, water
  and the top of a dungeon were left right under the floor of the city): the 3 layers under a lot's floor
  (`ChunkBuilder.FOUNDATION`) never keep air, a liquid or a block entity where the plan keeps what the world has
  (`ChunkBuilder.foundationGap`): they become dirt, when the chunk is built (`lot()`) and in the neighbour repair
  (`repairLot`, which until then only filled air and liquids there, so the result depended on the order of the
  chunks; the fill under a lot only went down from the first solid block, and the walls and roof of a village house
  counted as solid). The cells a structure plans there (its buried layers, the garage of the wide building, the
  basement of the police station, the openings) are never touched: in the battery every `mod blocks:` line and every
  count of the structures (`building_mismatch`, block entities, loot containers, hard ones) stayed the same. What
  lies deeper (dungeons, mineshafts, ruins) does not change; a dungeon whose top reaches those 3 layers has that part
  filled (seed 20261027, t03: a chest and a spawner). The audit checks it (`lot_foundation`, the cells checked in
  `foundation_checked`): before the fix 279 failures in seed −3297… and 13 in 20261027, after 0. Holes of the bridges
  (finding 76): the neighbour repair, which left them as built, removes the crowns and plants a chunk decorated later
  grew into them, from the deck up (what is under the deck stays).
- Villages inside the cities (13 Nov 2026, asked by the user: villagers and an iron golem in a city generated where
  there was a village): the houses of the village are generated before the city, each with its creatures, which stayed
  when the city was built over them (in the test, a villager stuck inside the fill of the street). When it builds each
  chunk, the mod takes out of the chunk being generated the villagers, zombie villagers, iron golems and cats that
  stand in the city or up to 32 blocks beyond its border streets (`City.within`, `City.VILLAGE_MARGIN`,
  `ChunkBuilder.removeVillageMobs`); other animals stay, and houses of the village left standing outside the city
  stay empty in the margin; farther away, the village stays as it is. The battery reports how many were removed
  ("village creatures removed from cities").
- Hardness: any hardness is allowed (an old rule removed by the user); only unbreakable blocks are forbidden. The block
  entities allowed are the empty chest of the fountain and the containers of the structures (chests, barrels, brewing
  stands, decorated pots, lecterns, furnaces, beds, campfires, daylight detectors, jukeboxes, banners, skulls,
  dispensers, droppers and chiseled bookshelves, empty, since 13 Nov 2026 because of the large house;
  `Palette.exempt`). The banners get the design saved in the template; signs come out without text.
  **Loot tables** (13 Nov 2026, asked by the user; set when each container is placed, through the region, as in the
  vanilla structures; a seed per lot and position; `ChunkBuilder.loot`, `Palette.lootTable`): vanilla items only, in
  tables of its own written by `tools/gen_loot.py` in
  `data/abandonedmoderncity/loot_table/chests/<theme>/<tier>.json`. Theme by the type of the structure
  (`Structures.Def.lootTheme`): `house` (food, utensils, books, music discs), `pharmacy` (potions of healing,
  regeneration, night vision, fire resistance, potion ingredients), `workshop` (iron, copper, coal, worn iron tools,
  rails, pistons; rare: enchanted tools, anvil, hopper), `gas_station` (snacks, coal, lava buckets, maps, emeralds) and
  `building` (the buildings: paper, books, arrows, pencils, worn weapons; rare: enchanted iron armor, ender pearls,
  music discs; every building and the special one, whatever their number of containers: the theme `building_large`
  of the buildings with 100+ containers, made for the AMC TACZ Loot add-on after 13 Nov 2026, was taken out at the
  user's request on 14 Nov 2026, with its 6 tables; unopened containers of worlds generated in between point to
  `chests/building_large/...` and come out empty unless an add-on still has those tables); `police_station` (asked by the user after 13 Nov 2026, for the same add-on, so that it can give the police station, 63 containers, more weapons: its own theme, the same vanilla tables as `building`; `Structures.LOOT_THEMES`; `FingerprintTest` `structures` changed, the blocks did not). Tier by the container: chest `chest` (3 to 6 stacks of common and normal items and a 10% chance of a
  rare one), barrel, dispenser and dropper `barrel` (40% have anything, 1 to 3 stacks of common items only), decorated
  pot `pot` (40%, one common stack). A container hard to get to (`core/Access`) uses the `_hard` version, more
  generous: `chest_hard` 5 to 8 stacks and 1 to 2 rare items, `barrel_hard` 75% with 2 to 4 stacks of common and
  normal items, `pot_hard` 80% with one normal or rare stack. Hard to get to = out of reach of someone walking in
  from the street, or listed by hand in the `hard` column of `index.txt` (x,y,z of the `.nbt`, `Access.Result.withHard`;
  today the chest in the tree of the abandoned modern house, `5,12,17`, reached by jumping up the leaves), or by where
  it stands (asked by the user on 14 Nov 2026, added to the two rules before): underground (at or under the ground
  layer), on the terrace (open sky over it, `Access.ABOVE` = 4+ blocks over the ground layer and at most
  `Access.TERRACE_TOP` = 5 under the highest layer of the structure: its roof, not a yard or a lower wing) or, only in
  the houses, in the attic (one ceiling between it and the sky, 4+ over the ground layer; a ceiling is a run of blocks
  that close the way up: not air, plants, vines, cobwebs, torches, carpets; doors, trapdoors, gates, ladders and
  scaffolding close it; `Access.ceilings`). Measured before choosing: the attic in every structure would mark 311
  containers (the top floor of every wing of the buildings and their low annexes), the terrace at any height 43; the
  user chose the attic only in the houses and the terrace up to 5 under the top. The height rule (15+ blocks above the
  city ground, `Access.HIGH`) was removed at the user's request after 13 Nov 2026. The
  walk: the path jumps 1 block (never over a fence,
  wall or pane), falls any height, climbs ladders, vines, scaffolding and water, opens doors, trapdoors and gates (not
  the iron ones); plants, cobwebs, carpets and torches do not block; leaves do; the container must be 1 block to the
  side (diagonal counts) of where the player stands, from 1 below the feet to 1 above the head. Today 125 of 2,559
  containers of the 52 files with the overgrowth on (in the templates, with the barrels and without the chests of the
  buildings; the chests drawn in the buildings come on top, hard on the 415 hard spots, see above; 129 of 3,106 with those chests, 29 before the rules of where it stands): out of reach 24 (police station
  13, the closed rooms of its basement; apocalyptic building 7, its barrels; abandoned mansion 3, a room 2 blocks
  above the courtyard; abandoned house 1, the roof; the 3 chests of the apocalyptic building and the 1 of the bluish
  building that were out of reach went with the chests of the buildings), listed 1 (the
  chest in the tree, also in the attic), underground 23 (police station 18, its basement, the 13 closed ones among
  them; 1 in each of the 5 small workshops), terrace 5 (1 on the roof of each large pharmacy), attic 87 (modern house
  14, modern garage house 14, post-apocalyptic mansion 12, glass house 10, Victorian 9, pool house 6, abandoned
  mansion 5, suburban 5, large house 4, garage house 3, tower house 3, abandoned house 2); houses 90 of 407; the tall
  buildings 0 (their floors are reached by their stairs; the containers in the open on their roofs, 14 in the
  brutalist U, wide, green tower, ramp, small and crash buildings, lie 9 to 15 blocks under the highest block of the
  structure, a stair housing or a taller part, so they are not on the terrace; 5 chests in the open of the small,
  brutalist U, wide and crash buildings went with the chests of the buildings); the 5 underground ones of the small
  workshops are a barrel set in the ground layer at the edge, with vines; with the height rule there were 1,382. The vehicle parts
  of OAmP and the Lost Cities table were taken out (the Lost Cities one was rejected by Minecraft). The chest of the
  park fountain stays empty (50%) or `mossy_cobblestone` (50%).
- Palette (`gen/Palette`): every block the generator places, checked at startup (`Palette.validate`). The 8 material
  styles of the buildings of Abandoned Simple City (walls, windows, floors, trims, stairs, balconies, doors), their
  block codes and the plaza and empty lot types were removed in the code review (13 Nov 2026, asked by the user); they
  remain in the git history (commit `d960e99`).

## After 1.0.0

- **Zombie spawners in the buildings** (24 Nov 2026, asked by the user; `core/SpawnerDraw`, `gen/Spawners`, config
  `zombieSpawners`). Stage 1 (`cb7d05b`, `49ea765`) drew them in the tests only (the world did not change); stage 2
  placed them. The user's choices: only the plain zombie; on the floors over the ground one in two from the street
  floor (the 1st, 3rd, 5th...) and in the garage of the wide building; 1 a floor in a small building, 2 to 5 drawn per
  floor in a large one (the other reading, 2 to 5 in the whole building, was offered and not chosen); near the chests,
  "guarding" them, at most 5 blocks away, and when there is no room an exploration spot away from the chests; working
  in any light (the game's rule, block light 0 and a dark sky, was offered and not chosen, and so was "ignore the sun
  but stop at a torch"); a calmer pace than the game's (player within 12 instead of 16, 2 a wave instead of 4, at most
  4 around instead of 6); a config option, on by default. First the lobbies open onto the street were left out (the
  zombies would walk out into the sun): the street floors of the skeleton and bluish buildings got none, the tower and
  the green tower ~1.9 of 2 to 5; the user chose to take them only when the floor has no room elsewhere.
  What it replaced: the palette allowed no block entity but the containers and the decorative ones; now the spawners of
  the buildings too (`Palette.exempt`). The fingerprint and the battery references changed with stage 2: `lots` of
  `FingerprintTest` `ecf9d723...` → `537e49bd...` (with the option off it is still `ecf9d723...`); both reference
  reports of 20261027 (full, and quick with `structureOvergrowth = false`): the `mod blocks:` lines of the targets
  with buildings and `structure_block_entities` up by the new `structure_spawners`, nothing else.
- **The stairs of the apocalyptic building turned the right way** (24 Nov 2026, found by the user in the game: they
  could be climbed but looked wrong). The 146 stairs of its stairwell (two flights a floor, x 24–25 and 28–29) were
  saved facing the way down in the original `.nbt` (every flight climbed away from its facing; in the apartment, the
  skeleton, the bluish building and the tower the flights climb toward it). What it replaced: north and south swapped
  on those 146 stairs, nothing else, in `apocalyptic_building_57x45.nbt` and in its original
  `structures/Prédios/predio_apocaliptico_57x45.nbt` (the user chose to fix both; the former file is kept in
  `~/backups/amc-apocalyptic-stairs-2026-11-24/`). `FingerprintTest`: `lots` `537e49bd...` → `8acc1004...`,
  `structures` `eae75644...` → `cbe4e5de...`; both reference reports of 20261027: the `mod blocks:` digests of the
  targets with that building, the same positions and counts.
