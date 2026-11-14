# Tests — cities, roads, bridges, tunnels and highways

This document records:
- how the mod is tested and what each test checks;
- the problems found and their fixes (section 3);
- the numbers of the latest runs (sections 5 and 6).

The specification the tests check is in `CLAUDE.md`.

## 1. Three layers of tests

| Layer | Where | What it uses | When to run it |
|---|---|---|---|
| Unit (JUnit) | `src/test/java/.../core` | the pure planner + 7 synthetic terrains × 3 seeds | `./gradlew test` (also in `build`; ~2 min) |
| Plan on real terrain | `core/PlanAudit`, in game | Minecraft's terrain noise (`getBaseHeight`) | `/amcs plan [radius]` and in the battery |
| Generated blocks | `command/WorldAudit`, in game | the chunks the server really generated | `/amcs audit [radius]` and in the battery |

To build only the jar, without the tests, use `./gradlew build -x test`.

### Fingerprint (changes that must not alter the world)

Two checks keep the generated world through a code review (since 12 Nov 2026):
- **`FingerprintTest` (unit):** a SHA-256 per part of what the planner hands to the game layer, on the 7 synthetic
  terrains with fixed seeds. The parts:
  - `layout`: the 1,129 cities of the 7 × 3 combinations and the lots of each block;
  - `highways`: the 2,013 highways (points, profile, bridges, tunnels, holes);
  - `village`: the area without village creatures around each city;
  - `chunks`: the plan of each column of 31,214 chunks, around 2 cities and along 1 highway per terrain;
  - `lots`: in those chunks, the block code of each lot column at every height, with the loot access and the banner
    designs (and the barrels of the buildings taken out or turned into chests, since 14 Nov 2026);
  - `roads`: worn surfaces, plants, stones and walls of the bridges, vines;
  - `lamps`: the parts of the street lights;
  - `crowns`: the leaves of the completed crowns;
  - `structures`: the 52 structures in the 4 rotations, cell by cell, with openings, access, banners and outside
    leaves;
  - `weathering`: what the overgrowth added to each structure;
  - `benches`: the park benches of the flat terrain.

  The expected values are in the test itself (code of commit `4dd94ae`; `layout`, `structures` and `weathering`, which
  hold the structure names, since the English names, finding 66). A structure block goes in by the text of its state
  and its rotation, not by its index in the list of states. The test also checks a minimum coverage, so it cannot
  pass with an empty plan. Sensitivity checked on 12 Nov: changing `StreetLights.LAMP_EVERY` (then
  `City.LAMP_EVERY`), `RoadDecay.PATCH`, `Park.BENCH_EVERY`, `Weathering.VINES`, `Ring.GAP_MIN`, `Highway.DEADBAND` or
  the village margin changes the right parts, and each of the 11 parts changes with one of these changes.
- **`mod blocks:` in the battery:** per target, a SHA-256 of the blocks the mod decides in the generated world
  (`command/WorldFingerprint`), at the positions `WorldAudit` compares with the plan:
  - on the roads, the surface and the block under it, the air or the plants above, the street lights, the ceiling of
    the tunnels and the pillars;
  - the walls of the tunnels;
  - in the lots, every block the plan defines, except the codes the audit also skips (nothing planned, grass, short
    grass and moss carpet);
  - the planned leaves of the completed crowns;
  - the block entities of the structures, buried layers included (loot table and seed, banner designs). The others
    are left out: the empty chest of the park fountain, for example, only shows up in the chunk after it has been
    saved and loaded again (missing in a freshly generated world and there after a reload).

  The rest of the world is left out, because vanilla Minecraft does not generate the same from one run to the next
  (finding 64). Minecraft recomputes some properties from the neighbours when it finishes a chunk, and the result
  depends on whether the neighbouring chunk exists yet; they go in with a fixed value: the distance of leaves, the
  shape of stairs, the connections of bars, panes and fences, the snow on grass (`snowy`), a wall side connected low
  or tall (`low`/`tall`; connected or not still counts) and the faces of vines in the columns at the edge of the
  chunk; `cave_air` counts as air. The file `renders/tNN_blocks.txt`, next to the report, holds a summary per chunk
  (roads, lots, rest) and per column, to find where two runs differ.

In a change that must not alter the world, the test passes, and the `mod blocks:` lines and the `stats:` counts of
the battery stay equal to the reference (section 6). The exception is `loot_containers_anywhere`, which also counts
the vanilla chests, which vary. A commit that changes the world on purpose updates the expected values of the test
and the battery reference, and says so.

With the structure overgrowth switched off (`structureOvergrowth = false`) the structures change, so the reference is
another one: that of the quick battery with this option (section 6). To take it, change the line in
`run/config/abandonedmoderncity-common.toml`, run `-PselfTestScope=quick` and put the line back afterwards.

What the battery fingerprint does not cover: the vines hanging from the bridges (finding 65: where they go depends on
a vanilla tree getting there first), the fill under the roads, the slopes and the liquid sealing. In a change that
only moves code (step 3 of the review), the proof for these parts is to compare the bytecode listing
(`javap -c -p`) of each moved method, before and after: the body must be the same.

The **automatic battery** (`tools/selftest.sh`) does, for each seed:
1. creates a new world on a dedicated development server (port 25591);
2. picks the targets from the planned network itself;
3. generates the chunks;
4. runs both audits on each target and writes PNGs;
5. stops the server.

Targets per seed:

| Target | `quick` | `full` |
|---|---|---|
| the city nearest to the spawn (whole, with the slopes) | ✓ | ✓ |
| the highest city among the 16 nearest | | ✓ |
| a coastal city (water up to 96 blocks from the area of influence) | | ✓ |
| the city on the roughest terrain | | ✓ |
| the longest tunnel (with its approaches) | ✓ | ✓ |
| the longest bridge over water | ✓ | ✓ |
| the longest dry viaduct | | ✓ |
| the longest continuous ramp | | ✓ |
| a whole highway (the shortest that fits the area) | | ✓ |

Repeated targets (the same city under two criteria) go in only once. Fixed seeds: `20261027`,
`-3297019724594858492`, `3478477863671517581` and `1`.

### Synthetic terrains (unit tests)

- `FLAT` (flat), `HILLS` (hills ±16) and `MOUNTAINS` (mountains up to ~220).
- `ISLANDS` (islands and sea) and `MIXED` (ridges + valleys + rivers).
- `CANYONS` (a plateau cut by narrow canyons below sea level).
- `RIVERS` (rolling land cut by rivers and dotted with lakes, like the real overworld near the battery seeds).

Each terrain runs with the seeds `20261104`, `-3297019724594858492` and `42`, on an area of 9 × 9 cells of 736
(~18,000 × 18,000 blocks).

## 2. What is checked

### Unit (48 tests)

| Test | Checks |
|---|---|
| `ProfileTest.profilesAreFlatOrRampAndJoinTheCities` | 4,000 random profiles (with cliffs): starts and ends at the level of each city, 48 flat blocks at the ends, a change of ≤ 1/8 of a block per block, flat stretches always at a whole height, never below the minimum, the surface (half block) never changes more than 0.5 between neighbours |
| `ProfileTest.halfBlockStepsAreEvenlySpaced` | on a climb, the half-slab steps are at least 4 blocks apart |
| `ProfileTest.classificationRunsAreClean` | 2,000 profiles: junctions always on the ground, tunnels ≥ 16, dry bridges ≥ 8, all the water outside the junctions is bridge |
| `WorldPlanTest.villageCreaturesAreRemovedInTheCityAndItsMargin` | in 30 cities: the center counts as city; walking out from the center, the area holds up to 32 blocks (±1) past the last street or lot column and not farther |
| `WorldPlanTest.parkBenchesSitOnTheEdgeOfThePathsFacingThem` | in the 30 parks of the flat terrain: every seat with the path in front and no path behind; every armrest with its panel against a seat; no bench on the path; nothing grows through a bench (only a high crown); benches 8+ apart; every park with a bench; 565 benches, 1,018 seats, 1 per 18.6 blocks of path |
| `WorldPlanTest.streetLightsStandOnTheSidewalksWithTheirArmsOverTheLanes` | in 20 cities, column by column: street lights only on street segments (never on a crossing or a crosswalk); a whole post of 13 (with a tuft of leaves only on the overgrown variant); the back vine always facing the post, 3+ up and with the post beside it at the same height; over the lanes nothing below 11 but the fallen lamp; a hanging vine always under a slab; a broken arm only on its first 2 columns; 129.9 street lights per city (of 144, 10% gone), variants 505 / 523 / 533 / 525 / 511; in the chunk plans the post and the fallen lamp are not drivable |
| `WorldPlanTest.cutCrownsHangOverSidewalksAndCores` | in the cities of the flat terrain (897 chunks with completed crowns): 7,267 leaves in the woods and 860 over sidewalks; 1,047 too low over the street and 13 where the woods already plan something are left out; none in another structure; each one in the chunk asked for |
| `WorldPlanTest.cityStreetsHaveNoDeadEndsAndLotsAreBuildable` | in every city of the 7 terrains (357 2 × 2 cities of 180 × 120 blocks, 11,500 lots; no building repeated in a city, 6.30 buildings per city — structures, parks and cores taken over by nature; no block with two of the same structure; in each city at most 3 pharmacies, 2 workshops and 2 gas stations; 204 park blocks, each with the park of 110 × 64 in a corner and 947 structures around, none less than 4 blocks from it; 9,868 structures in 1,428 blocks, 6.91 per block, 7.29 in the blocks without a park, 4.64 in those with one, 51.3% houses; writes `park_block.png`): no node of degree 1 (no dead ends), nodes of degree 2 only at the corners (with the arc of the corner); every lot column is a lot of the right block and does not touch a street |
| `WorldPlanTest.streetWidthIsConstantOnStraightStreets` | every straight city street has exactly 33 columns; the zebra is on rows 2 to 4 of the segment, with 14 stripes, and on no other row |
| `WorldPlanTest.highwayNetworkIsConsistentAndWellConnected` | 21 terrain × seed combinations: Gabriel symmetric, a highway starts and ends exactly at the entrance of each city, curve radius ≥ 60, points ~1 block apart, maximum length, an entrance crossing never used twice (a corner has two exits, but only one highway), at most 4 highways per city (`City.EXITS`), no pair of highways too close (no crossings); limits: flat/hills ≥ 85% of the links and ≤ 5% of cities without a highway, mountains/rivers/canyons ≤ 15%, and at least half of the cells with a city (except islands); writes `network_*.png` |
| `FloraTest.overgrownBlocksAreConsistent` | 6 cores of 140 × 90 (the test uses this fixed size): more than 120 trees each, crowns inside the block, every vine held by a leaf/log or under another vine, every leaf with a distance of 1 to 6 and a neighbour one step closer to the wood (vanilla's leaf decay rule), plants only 1 block above the ground and only on soil (grass, moss, coarse dirt), the top halves of tall plants right, rubble never floats; no tree enters a structure; writes `wild_block.png` |
| `FloraTest.parksAreOvergrownAndKeepTheirFountain` | 6 parks: the same rules for plants and vines, every block of the fountain in place (only the chest → mossy cobblestone swap, the mossy slabs of the ring and the stone floor turned mossy), strands of vine on the fountain; writes `wild_park.png` |
| `RoadDecayTest.aboutThreeColumnsInTenAreTakenInPatches` | 1.9 million columns in 3 seeds: 29.8% taken, deterministic, in patches (73% of the taken ones have a taken neighbour), the 6 kinds appear |
| `RoadDecayTest.tunnelsRampStepsAndWallsStayClean` | tunnels, half-slab steps and the wall column are never taken (bridges may be); sidewalks crack into cobblestone, never into asphalt |
| `RoadDecayTest.wornTextureAndMarkings` | 250,000 columns: the worn texture is always one of the variants of the lanes, the sidewalk or the slabs; `polished_deepslate` is the most common (54%) and every variant appears; 20% of the lines faded; the wall column does not change |
| `WreckTest.holesAreAboutOnePerEightyBlocksOfBridgeInTheMiddleOfTheLanes` | 2,000 bridge profiles of 60 to 459 blocks: 1 hole per ~85 blocks of bridge, always on the bridge, away from the ends, inside the 27 lanes, of the size asked for; none off the bridges |
| `RoadDecayTest.plantsOnlyOnGrassAndMossAndWithoutCollision` | plants only on grass and moss, always without collision (short grass, tall grass, fern), the top half of tall grass right |
| `RingTest.withoutStructuresBlocksStayEmpty` | (skipped with structures) with an empty `index.txt`: no structure state and the ring makes no lots |
| `RingTest.groundLayerAndBuriedLayers` | the structures with a buried layer have blocks below the ground and nothing deeper; buried air only in the wide building and the police station (the `cave_air` of the garage and of the basement, dug); abandoned mansion, large house, tower, bluish and apartment go 1 down, the wide building 8 |
| `RingTest.undergroundGarageKeepsItsRampAndStairsOpen` | in the wide building, the 99 air cells of the ground layer stay open (the whole ramp, with its top, and the stairwell), with dug air or the ramp itself below (never terrain), in the 4 rotations; the police station has 405 (the rooms of the basement crossing the street layer); no other structure has an opening; its 249 containers are reachable (0 out of reach) |
| `StructuresCarveTest.caveAirIsDugUnderTheGround` | a synthetic 3 × 3 × 3 template with ground 2: below the ground the `cave_air` becomes air (dug) and plain `air` stays terrain (`KEEP`); the buried stone and the one at ground level are placed |
| `RingTest.frontsMeetTheSidewalk` | the front row, at ground level, is all blocks in 51 of the 52 files (the apartment has 22 of 31: the gaps become the city ground, level); it prints the box (front × depth × height) and the ground layer of each file, the source of the structure table of the README |
| `RingTest.structureTreesHaveRealLeaves` | every non-persistent leaf of the structures has a distance of 1 to 6 and a log or leaf neighbour closer (leaf decay rule); more than 1,000 real leaves |
| `RingTest.housesWithTheirFrontOnPlusZFaceTheStreet` | the modern house and the post-apocalyptic mansion have a 180° turn in the index and, with the street to the north, the row against the street is the last row (+Z) of the template, block by block; the others have no turn |
| `RingTest.pharmacyVariantsMatchTheirOriginalButTheColor` | in the 8 pharmacy variants, position by position against the original: the same blocks in the same places, no neutral block (white concrete, smooth quartz, calcite, polished diorite) painted and the banners in the same positions; 76 blocks of the F A R M Á C I A sign put back on each small one, 152 on each large one |
| `RingTest.pharmacyBannersKeepTheirLetters` | in the 4 rotations, each of the 10 pharmacies (originals and variants) has 9 banners (small) or 18 (large) with a design (stripes and border), all on banner blocks; on the small ones, a white banner with letters in the variant's color; on the large ones, a banner in the variant's color with black letters |
| `AccessTest.closedRoomsDoorsAndLadders` | rooms built in the test: a chest in a sealed room is hard to get to; with a wooden door it is not; with an iron door or a fence in the doorway it is; a chest on a pillar of 4 is only reached with a ladder; steps of 1 block are climbed by jumping |
| `AccessTest.heightAloneDoesNotMakeAContainerHard` | a barrel 20 blocks above the ground, reachable by a ladder, is not hard (the height rule was removed); a chest on the ground neither |
| `AccessTest.theChestInTheTreeOfTheModernHouseIsHard` | the chest in the tree of the abandoned modern house (reached by jumping up the leaves) is hard, listed in the `hard` column of `index.txt` (`5,12,17`); the house has no other hard container |
| `AccessTest.structuresHardContainers` | in the 52 files, with the overgrowth: 29 of 3,106 containers hard to get to (out of reach 28, listed 1; at most 2%); hard = out of reach + listed in every structure; the same in the 4 rotations; the tower, the green tower and the brutalist U building have none (their floors are reached by their stairs); in the houses at most 5% (5 of 407) |
| `AccessTest.everyStructureHasItsLootTables` | the 6 tables of the theme of each structure exist; every building and the ramp building have the theme `building` (whatever their number of containers), no structure has `building_large` and its tables are gone, only the police station has `police_station`; barrel and pot with a 40% chance, `barrel_hard` 75%, `pot_hard` 80%, chests with no chance of coming empty; nothing from Lost Cities or OAmP |
| `BarrelsTest.onlyTheBuildingsAndEveryBarrelOfThem` | the 10 structures of the `building` category have an entry for each of their barrels (794); no other structure has any (the police station and the ramp building keep their barrels) |
| `BarrelsTest.seventyPercentTakenOutForcedNeverStacksFromTheTop` | over 200 copies (seeds) of each building: the same seed gives the same result; a barrel that holds something (26: tower 25, crash 1) is never taken out; in a stack, never a chest over a barrel taken out; 69.9% taken out in all (within 1 point of 70%), each building with 20+ barrels within 3 points (tower 70.0%, brutalist U 69.8%, crash 69.9%, wide 69.5%, green tower 69.8%, apocalyptic 70.5%, small building 71.1%); ~239 chests per set of the 10 buildings |
| `BarrelsTest.chestsOpenAndFaceAHorizontalSide` | every chest that replaces a barrel faces north, east, south or west (never up or down) and has no full block or double slab over it (it would not open) |
| `BarrelsTest.theCodeOfACopyHasNoBarrelLeft` | in the 4 rotations, the block codes of a copy have no barrel left: each barrel became air or a chest with the same rotation, and nothing else changed against the template |
| `BarrelsTest.everyCopyOfABuildingIsDrawnApart` | in the cities of the flat terrain, every copy of a building with 20+ barrels has its own seed and its own set of chests (205 copies: tower 30, green tower 37, small building 46, apocalyptic 34, brutalist U 22, wide 20, crash 16) |
| `BarrelsTest.barrelsByLayer` | no barrel of the 10 buildings is on the ground layer or under it (a barrel taken out there would become city ground or dug air) |
| `BarrelsTest.theHardContainersDoNotChange` | with every barrel taken out, or every barrel a chest, the containers hard to get to of each building are the same as on the template with its barrels (the hard flags are worked out once) |
| `WeatheringTest.onlyAllowedChangesAndEverythingHolds` | in the 52 files, with the seed of the `seed` column of the index, cell by cell against the saved template: the same report as the overgrowth the game applied when the structures loaded (`Structures.WEATHERING`); only allowed changes (cracked and mossy of the same stone, with the same properties; a stone or concrete floor with air above turned into soil; a carpet on a full block or soil; grass, fern and dead bush only on soil; a vine with one face, held by a full block or by the vine above with the same face, or under eaves); banners equal; the box of the structure equal; the same result in two loads; the report equal to the changes. Total: 38,501 vines, 10,192 soils, 5,088 carpets, 4,947 plants, 3,907 mossy, 2,451 cracked (65,086 blocks, the number of the game log) |
| `WeatheringTest.everyStructureReachesTheMeasuresOfTheApocalypticBuilding` | no measure drops (cracked and vines may drop up to 3 points when a floor turns into moss: the block leaves the family or the counted wall); cracked at 31% (−3) or more; the side least covered with vines at 19% or more (target 25%); on the apocalyptic building only the vines change |
| `WeatheringTest.wholeNamesAreNotTakenForParts` | `light_gray_concrete`, `smooth_sandstone` and `mud_bricks` are whole stone; planks are whole but not stone; glass, leaves, slab, chest, banner and the light block are not whole |
| `RingTest.cutCrownsAreCompletedOutsideTheBox` | 189 leaves complete cut crowns, only on the pool house, the garage house, the suburban house, the tower house, the glass house, the skeleton building and the ramp building; in the 4 rotations: all outside the box and up to 4 from it, not persistent, distance 1 to 6 with a neighbour one step closer to the trunk, no repeated position; those of the pool house are in front and above `CLEARANCE` |
| `WorldPlanTest.specialStructuresAreInAboutOneCityInTen` | in the cities of the 3 seeds × 7 terrains, the `special` category (the ramp building) only in the cities that draw `City.SPECIAL_CHANCE` (0.40) and in 7% to 13% of the cities: 127 of 1,129 (11.2%), 496 may have it (before the draw: 256, 22.7%); the 1,129 are only 170 distinct cities (the 7 terrains repeat them); a measurement apart over 2,838 distinct cities of 50 world seeds (flat terrain): 39.0% may have it, 10.5% have it |
| `RingTest.everyStructureHasASpawnCategory` | every structure has one of the 7 `/amcs spawn` categories: house 12, police_station 1, special 1, pharmacy 10, workshop 8, gas_station 10, building 10 (52) |
| `RingTest.lightningRodsBecomeRedstoneTorches` | no lightning rod state is left in the structures; 5 standing redstone torches coming from lightning rods (plus 15 saved as torches in the new structures, 3 of them in the police station: 20) (one on top of each structure that had a lightning rod); a sideways lightning rod becomes a wall torch with the same direction |
| `RingTest.noFloatingCobwebs` | no cobweb of the structures is left without a block to hang on (the ground counts); more than 2,000 cobwebs checked |
| `RingTest.spawnLimitsHoldOverACity` | 300 cities of 4 blocks drawn in sequence: at most 3 pharmacies, 2 workshops, 2 gas stations and 1 of each building; the city's counter matches what was placed |
| `RingTest.structuresHaveSolidBoxesWithAFloorLayer` | every structure has a non-empty box and blocks on its first layer |
| `RingTest.rotationKeepsEveryBlockAndTurnsTheFrontToTheStreet` | the 4 rotations have the same number of blocks in every structure; the rotation of 90, 180 and 270 degrees matches the formula of the transformation cell by cell |
| `RingTest.ringsFitTheirBlockTouchTheSidewalksAndDoNotOverlap` | 6,000 rings in blocks of 180 × 120 and 120 × 180: inside the block, no overlap, each structure touches the sidewalk its front points to, no structure repeated in the same block, deterministic, all 52 files appear and the 4 sides are used; 7.81 structures per block, ~25% on each side, 52.5% of the block covered, 39.1% houses |
| `RingTest.everyStructureFitsNextToTheShallowestInA120Block` | each structure fits in a block of 180 × 120 next to the shallowest one (20) and the widest woods (30), facing a long side or a short side (the ramp building, 102 × 72, only on the short side) |
| `RingTest.structuresNeverTouchAndLeaveTheForest` | 6,000 rings in 180 × 120 and 120 × 180 (164,125 pairs): any two structures are 4 or more blocks apart; between opposite sides, 20 or more of woods (smallest measured: 4 and 20) |
| `PlanAuditTest.citiesAndHighwaysPassThePlanAudit` | `PlanAudit` column by column in 3 whole cities and 2 whole highways per terrain × seed (105 audits); writes `city_*.png` |
| `PlanAuditTest.planIsIndependentOfQueryOrderAndThreads` | 3,721 chunks planned again in shuffled order by 8 threads: an identical result, field by field |
| `FingerprintTest.plannerOutputIsUnchanged` | the 11 parts of the planner fingerprint (section 1) equal to the expected ones (those of commit `4dd94ae`, with `layout`, `structures` and `weathering` of the English names); coverage: 1,129 cities, 2,013 highways, 31,214 chunks, 1.47 million road columns (44,129 of bridge, 26,783 of tunnel), 1.22 million lot columns (6.6 million structure blocks in the lots), 10,198 street light columns, 1,741 crown leaves, 16.8 million structure cells, 2,036 bench parts; 14 s on the latest run |

### `PlanAudit` (the plan, column by column, across chunks)

- **Roads:**
  - `step` (a step > 0.5 between neighbouring drivable columns) and `city_level`;
  - `wall_gap` (a highway wall that touches its neighbour only at the corner);
  - `highway_hole` (a hole in the highway band), `highway_height` and `highway_end` (a highway that does not land
    on a street of the city, at its level);
  - `short_tunnel` and `ramp` (a slope or a fractional landing in the profile).
- **Street network:**
  - `street_missing` and `dead_end`;
  - `orphan_road` (a stretch of road that reaches no city and does not leave the area);
  - `city_split`.
- **Lots:** `lot_overlap` (two lots of a block overlap).

### `WorldAudit` (generated blocks)

- **Road surface:**
  - `hole` (no surface), `surface_block` (a block other than the planned one) and `no_base`;
  - `height_mismatch` (a measured height other than the planned one);
  - `step` (measured on the blocks, also across chunks).
- **Clearance and decoration:**
  - `obstacle`, `cover` and `fluid_on_road` (4 free blocks above the lanes, 7 in tunnels);
  - `vegetation_over_road` (up to 48 above; the planned leaves that complete the crowns of structures do not count);
  - `street_light` (a planned street light part that is missing or changed: post, arm or glass; the ones checked
    count in `street_light_blocks`);
  - `crown_leaf_missing` (a planned leaf of a completed crown left without a block; the ones checked are counted in
    `crown_leaves`).
- **Structures:** `parapet`, `tunnel_ceiling` (`stone` or `glowstone`), `tunnel_wall` and `pillar_floating`.
- **Lots:**
  - `lot_hole` (water the structure puts at ground level, the pool of the mansion, does not count);
  - `yard_ground` (free city ground that is not `gray_concrete_powder`; since 11 Nov there is no free ground: the
    core is a `WILD` lot);
  - `building_mismatch` (an inherited name: a lot block — park, fountain, core taken over by nature with trees,
    vines, plants and rubble, structure — other than its definition; seams between chunks would show up here).
- **General rules:**
  - `hard_block` (an unbreakable block on a road, bridge, tunnel or lot; any hardness is allowed);
  - `hole_filled` (a bridge hole with a block at the surface or in the deck; counted in `bridge_hole_columns`),
    `parapet` (a bridge wall: `stone_brick_wall` or `mossy_stone_brick_wall`, or air in a fallen stretch;
    `wall_gap_columns`);
  - `road_plant` (a planned plant on a taken road column that is missing or changed; the taken columns are counted in
    `taken_road_columns`, the plants in `road_plants`; the taken surface is checked by `surface_block`);
  - `banner_without_design` (a structure banner without the design of the template; counted in `structure_banners`);
  - `structure_without_loot` (a structure container — chest, barrel, dispenser, dropper, decorated pot — without a
    loot table) and `structure_wrong_loot` (with a table other than the one of the structure's theme, the container
    kind and the access; the block entities of the structures are counted in `structure_block_entities`, the
    containers in `structure_loot_containers` and the hard to get to ones in `structure_loot_hard`), `loot_in_city`
    and `container_in_city` (outside the structures; the fountain chest empty, counted in `fountain_chests`);
  - `orphan_road` (connectivity measured on the blocks).

## 3. Problems found and fixed

History inherited from Abandoned Simple City (this mod started as a copy of it on 7 Nov 2026). The findings about
buildings (8, 13, 15, 17 to 26) no longer apply here: the buildings were removed. Findings 1 to 17 are from earlier
versions; some cite rules that changed later (blocks of 64, ladders in "shafts" against the walls). The fixes still
hold where the idea applies: the neighbour repair and the rule of ladder and support in the same chunk.

| # | Where it showed up | Problem | Cause | Fix |
|---|---|---|---|---|
| 1 | `ProfileTest`, `PlanAuditTest` (1 point in ~1 of every 6 highways) | a landing of 1 point at a fractional height | the allowed band (ramp cones of the two cities) has a plateau of 1 point at the top/bottom when the parity does not close; the profile stopped there | `Profile.build`: blocked at a fractional height, the profile turns back instead of stopping |
| 2 | Battery (startup) | the battery took minutes to start | the network PNG sampled the game noise in ~750 thousand columns (each `getBaseHeight` builds a `NoiseChunk`, ~3 ms) | network render without terrain in the battery; plan renders sample the terrain every 8 blocks |
| 3 | Battery (thread dump) | slow planning in areas with many highways | `tooClose` (the minimum distance between two highways) compared each point with ~800 points of the other | a spatial grid with a cell of 21 blocks: linear in the length |
| 4 | Battery, seed 20261027 | `surface_block`, `hard_block`, `tunnel_wall`, `parapet`: copper/coal ore, dirt, gravel, granite, diorite and andesite in place of the `stone` of sidewalks, parapets and tunnel walls | the ores and blobs of dirt/gravel/rock (`ore_*`) of a chunk decorated later reach ~15 blocks into the neighbour already built and replace `stone` (the `smooth_stone` of the lanes cannot be replaced) | the neighbour repair restores surface, base, parapet, ceiling and tunnel walls on every road column of the neighbour |
| 5 | Battery, seed 20261027 | `pillar_floating` (194 columns): a bridge over the sea without pillars | the neighbour repair repaved "eaten surface" also in neighbours **not built yet**; when that neighbour was built, the loose block was read as ground and the pillar did not go down | the repair only acts on neighbours that have passed the `FEATURES` stage (the others will be built from scratch) |
| 6 | Battery, seed 20261027 | `building_mismatch`: leaves and mushrooms inside buildings | trees and mushrooms of chunks decorated later | the repair removes decoration in the volume of the neighbour's buildings, only where the building wants air |
| 7 | Battery (audit) | `loot_in_city`: two chests at y 110 over a tunnel; `pillar_floating` under rock ledges | false positives: the audit looked at the whole column | containers only in the built volume; a "floating" pillar only if the air is above the natural ground of the noise |
| 8 | Battery, seed 20261027 | `building_mismatch` (1,724): the slab bands of a building style were missing | the building repair treated `stripped_oak_log` (the band of style 7) as a tree trunk | the repair never removes a block the building defines; style 7 uses planks |
| 9 | Battery, seed 20261027 (network PNG) | audits OK, but the real network was poor: 5 cities in ~7 × 7 km, nearly all without a highway | stage 1 drew **one** point per cell and refused the cell if it fell near water or on a hill; the real overworld has many more rivers and lakes than the synthetic terrains, so few cities and links too long | stage 1 tries up to 10 positions (the last ones with a smaller radius); candidate links up to 2,200 blocks (highway up to 3,000); a new synthetic terrain `RIVERS` and limits on isolated cities in the network test, so this class of problem is caught by `./gradlew test` |
| 10 | `WorldPlanTest` (network) | "no exit facing the other city" was the main reason for a lost link (up to 48 of 126 on mixed terrain) | irregular cities have few T nodes on their outline, and each serves only one highway | the middle of each street segment of the outline is also an exit (a T junction with the outer street), with at least 48 blocks between used exits and an exit up to ~75° off the axis; the links kept went up from 51–96% to 74–100% and isolated cities ended between 0 and 4% |
| 11 | Battery, seed 3478477863671517581 (rough city, level 114) | `lot_hole` (69) and `building_mismatch` (104): `cave_air` and `stone` in the floor of lots and buildings | a lava lake of a chunk decorated later digs the space above the liquid (`cave_air`) and puts a stone rim, inside the neighbour already built | the neighbour repair also restores lots: fill under the floor, floor, and the band up to 8 blocks above (building, park trees, plaza), removing liquids and sealing the neighbours |
| 12 | Battery, seed 1 | `building_mismatch`: gravel in place of granite in walls | granite (the "worn" variant of the brick style) is a target of the ore blobs, like stone | polished granite; `Palette.validate()` now refuses in buildings and lots any block of the tags the ores replace |
| 13 | Battery, seed 1 (city at a height of 139) | `building_mismatch`: `dripstone_block` on the high floors of a building | a dripstone cluster of a neighbouring chunk took the closed rooms for a cave | in the building columns near the edge, everything where the building wants air is removed, at every height |
| 14 | Network PNG (real terrain) | many cities of 1 block (64 × 64) | the block rule demanded 5 of 5 dry, level samples | the middle of the block must be firm; 1 of the 4 corners may not be (a pond or a hillock filled/cut). In the tests: 17–37 blocks per city, almost none of 1–2 |
| 15 | Final battery, seed 20261027 | `building_mismatch`: `mossy_cobblestone` in a wall, on a high floor | a forest rock of a neighbouring chunk, above the band the lot repair covers | near the edge, the building repair restores every block of the building (not only the air); the reach of the repair widened to the whole neighbour |
| 16 | Final battery, seed 3478477863671517581 | `building_mismatch`: `cave_air` where the building wants air | a false positive: `cave_air` is air | the audit accepts any kind of air |
| 17 | Final battery, seed −3297019724594858492 | `building_mismatch`: 2 ladder blocks turned into `air` 19 blocks above the ground floor | investigated in the generated world with the new `/amcs column` command over RCON: the supporting wall was intact, but in **another chunk**; vanilla re-evaluates positions marked for post-processing when the chunk is promoted, and the ladder, without support at that moment, breaks | each building picks, among 4 possible shafts (one per wall, next to a corner), one where ladder and support are in the same chunk; in the very rare case where all 4 cross the edge (0.015%), the building is 1 block narrower. New test `laddersAreHeldByWallsOfTheSameChunk`: 32,612 buildings, every ladder with a solid wall behind it and none supported in another chunk |
| 18 | `buildingsHaveTheirSizeRoomsAndLadders` (buildings with rooms) | an area of a floor unreachable from the ladder | the support pillar of the ladder fell right in the passage between two rooms and closed it | the passages do not use pillar cells or cells with a pillar on both sides; the test walks every floor from the ladder |
| 19 | `buildingsHaveTheirSizeRoomsAndLadders` (3D test of the zigzag stairs) | the landing of the 1st floor unreachable | the last step of the return flight was 1 block below the next landing, and the slab above prevented the jump | the last step is built into the landing (a stairs block in place of the floor) |
| 20 | In-game test by the user (jar of the zigzag stairs) | the return flight could not be climbed: the head hit | above the steps of the return flight there were only 2 free blocks (the slab of the upper flight came down with it); going up a step the player passes 1 block higher over the column they leave, so it needs 3. The 3D test only demanded 2 | the filling under the upper flight starts 1 block higher (3 free above each step); the 3D test now demands 3 free blocks over the column of origin on every climb |
| 21 | In-game test by the user (zigzag stairs) | the closed boxes were too big and left corridors of 1 block between the box and the walls of the rooms | — | the closed box was replaced by the open stairs of the user's blueprints (a straight flight per floor with `iron_bars` around the openings), 1 per medium building and 2 per large one |
| 22 | In-game test by the user (open stairs) | a stepped hole in the ceiling of the room, next to the lower part of the stairs | under the steps the opening was open also in the ceiling filling | the filling stays closed under the steps; it only opens over them, where the head needs it |
| 23 | `buildingsHaveTheirSizeRoomsAndLadders` (balconies) | a column taken by the balconies of two medium buildings facing each other with a gap of 3 blocks | the face only demanded 3 blocks free of other buildings, but both stick out 2 | the free band became 2 × reach + 1 = 5 blocks; in the alley of 5 the balconies of the two sides have 1 block between them |
| 24 | In-game test by the user (balconies and pillars) | flush pillars did not please: wanted relief | — | on the free faces the pillars stick out 1 block from the 1st floor to the roof (on the ground floor and on the sides against the neighbour they stay flush) and split the balconies that cross them |
| 25 | In-game test by the user (pillars in relief, approved) | asked for more variety: negative relief | — | three facades drawn in thirds: plain, relief and recess (the window bands 1 block inward from the 1st floor to the top) |
| 26 | `buildingsHaveTheirSizeRoomsAndLadders` (recessed facade) | a floor of a medium building with 7 rooms (maximum 5) | only the column behind each recessed band became wall; the columns behind the pillars and the corners stayed as niches of 1 block caught between the bands | on recessed floors the whole 1st column against the walls becomes wall |
| 27 | Battery, 8 Nov (structures, since removed) | `palette` brought the server down: beds, furnaces, campfires, daylight detector and jukebox in the structures | block entities outside the allowed list | `Palette.exempt` now accepts them (empty) |
| 28 | Battery, 8 Nov (structures, since removed) | `building_mismatch` (17): diorite and dripstone in place of the andesite of the structures, more than 8 blocks above the ground | ore blobs of chunks decorated later; the neighbour repair only covered 8 blocks above the ground | near the edge, the repair covers the whole height of the lot |
| 29 | `FloraTest` (nature, 11 Nov) | an unsupported vine where two crowns meet | the crown of one tree cuts the strand of vines of the other | the tree index drops the vine that is not held by a leaf/log nor under the same vine |
| 30 | `FloraTest` (nature, 11 Nov) | `ArrayIndexOutOfBounds` in `Park.code` | a query for a fountain vine above the height of the template (the park trees are taller than the fountain) | a height limit in the query |
| 31 | `FloraTest` (nature, 11 Nov) | grass on the fountain floor where a slab of the ring is missing | the slab gap put grass without looking at the block below | no grass in the gaps over the fountain |
| 32 | `FloraTest` (mossy floor, 11 Nov) | the stone floor of the fountain turned into grass | the swap table was built before the index of `mossy_cobblestone` existed (order of the static initialization: it was 0) | the table declared after the index |
| 33 | Tests, 12 Nov (structures) | every structure class failed to load (`NullPointerException`) | the list of blocks that are not floor and the minimum floor fraction were declared after the static block that loads the structures | the loading block moved to the end of the class |
| 34 | `RingTest`, 12 Nov | the short sides of the 140 × 90 blocks were nearly always empty (9% of the structures on each) | the rows of the long sides ran corner to corner and the deep structures (up to 57) left no room | the short sides run corner to corner; now ~25% per side |
| 35 | Review, 12 Nov | the structure loot (code from another session) swept the whole volume of the structure for every column and read the block entity straight from the chunk being generated, where it does not exist yet | — | the loot is set when each container is placed, through the region (like the vanilla structures); the audit now demands loot in the containers of the structures |
| 36 | Battery, 12 Nov | `lot_hole` (up to 200 per target): water and seagrass at ground level | the pool of the abandoned mansion, which went 1 block down, is at ground level | water planned by the structure does not count as a hole |
| 37 | In-game test by the user, 12 Nov | the abandoned modern house showed up with its back to the street | the front of its template is the +Z side (all the others: -Z) | a `front` field in `index.txt` (180) and a turn per structure in `Structures.Def` |
| 38 | In-game test by the user, 12 Nov | the post-apocalyptic mansion with its back to the street; cobwebs floating in it | front on the +Z of the template; unsupported cobwebs in the template | a 180° turn in the index; loose cobwebs become air in every structure (18) |
| 39 | In-game test by the user, 12 Nov | plain pharmacy banners | the template reader ignored the block entity data, where the designs are | `Template.banners` keeps the patterns, applied when the banner is placed (generation and `/amcs spawn`); audit `banner_without_design` |
| 40 | Battery, 12 Nov (180 × 120 blocks) | `building_mismatch` (3): planned birch leaves turned into air | the cleanup of orphan leaves (natural trees cut by the city) took every non-persistent leaf; those of the mod's real trees are like that, and with the trunk in a chunk not generated yet they looked orphaned | the cleanup never removes leaves the plan places (`ChunkBuilder.plannedLeaves`) |
| 41 | `PlanAuditTest`, 12 Nov (holes in the bridges and cave-ins in the tunnels, the latter later taken out) | `orphan_road` (18 of 105 audits): isolated lane columns | the irregular edge, drawn column by column, fenced in lane columns | an irregular but smooth outline around the center (waves of the angle) without islands; 105 of 105 pass |
| 42 | Battery, 12 Nov (old bridges) | the server froze on the viaduct and `Detected setBlock in a far chunk` warnings | in the fallen wall stretches, `wallTop` returned `Integer.MIN_VALUE + 1` and the clearance loop of the neighbour repair went through ~2 billion heights; the wall was also placed in the fallen stretches (height of the neighbour) | `wallTop` returns `Integer.MIN_VALUE` when the column has no wall and the clearance starts at the surface |
| 43 | Battery and RCON test, 12–13 Nov | the loot table `lostcitychest` (from Lost Cities) was rejected by Minecraft at load time | an extra escape in `custom_data` | replaced, at the user's request, by its own vanilla tables (`tools/gen_loot.py`); the battery log has no loot table error anymore |
| 44 | `WorldPlanTest`, 13 Nov (completed crowns) | no planned crown leaf | `WorldPlan.citiesIn` only returns cities whose center falls in the rectangle asked for, and the rectangle of a chunk almost never holds the center | `Crowns.forChunk` uses `cityNear` |
| 45 | Battery, 13 Nov (completed crowns) | `crown_leaf_missing` (all of them, up to 324 per target) and `Detected setBlock in a far chunk` warnings | the neighbour repair took the leaves away as loose decoration over the lot and the street and, when checking the 4 neighbours of the removed block, called `ensureCanWrite` 2 chunks away (which logs the warning) | the repair skips the planned leaves (`Overhangs.forChunk` of the neighbour) and only checks neighbours inside the writable area (`canWrite`) |
| 46 | Review, 13 Nov (overgrowth) | the list of blocks that are not full, by part of the name, caught `light_gray_concrete` (`light`), `sandstone` (`sand`), `mud_bricks` (`mud`), and planks and wool got no moss carpet | comparison by part on short names | these names are now compared whole (`NOT_FULL_EXACT`); `WeatheringTest.wholeNamesAreNotTakenForParts` |
| 47 | `WeatheringTest`, 13 Nov | a report with more changes than changed cells (medium workshop: 327 / 321) | a moss patch fell on a block that had already cracked | a cell already changed gets no other change |
| 48 | `WorldPlanTest`, 13 Nov | 11,812 lots instead of 11,818: the ring changed | vines and a carpet outside the box of the solid blocks made the box bigger (the structure moved away from the sidewalk) | nothing is placed outside the original box (`Editor.set` refuses); a test of the equal box |
| 49 | Measurement, 13 Nov | a cracked stone target of 23% | the first count included stones without a cracked version (`polished_deepslate`) | a target of 31% of the family that cracks, the measure of the building itself |
| 50 | `WeatheringTest`, 13 Nov (strong vines) | the abandoned mansion with 15% on its least covered side (target 25%) | the stretches of vine hanging below the end of the wall counted toward the target without covering facade | only the vines on the facade count |
| 51 | `WeatheringTest`, 13 Nov (strong vines) | the vine coverage of the abandoned house "dropped" | `moss_block` did not count as a full block: the vines next to a floor turned into moss left the count | `moss_block` counts as wall (the vine holds on it) |
| 52 | In-game test by the user, 13 Nov | the pharmacy variants were not equal to the original | an F A R M Á C I A sign in colored blocks on the white wall | the neutral blocks of the original come back where the variant painted them (`withNeutralFrom`) |
| 53 | Review, 13 Nov (same comparison) | the D R U G S T O R E banners of the small pharmacies placed 1 block to the left | the offset was measured by aligning the boxes of the solid blocks; in the template positions it is 0 | the index with `@0,0`; the test compares the banner positions with those of the original |
| 54 | Analysis of the 7 new ones | the mouth of the ramp and the stairwell of the wide building would be covered by the city ground (all air on the ground layer becomes ground) | the old rule of the ground layer | air or `cave_air` with `cave_air` below stays open (`Def.opening`); the audit accepts this planned air (`lot_hole`) |
| 55 | `RingTest`, 7 new ones | a leaf with distance 7 in the modern garage house | leaves saved as not persistent far from any trunk (they would fall in the world) | they become persistent at load time |
| 56 | Review, 7 new ones | `/amcs spawn` would not dig the garage nor open the ramp (it ignored all air on the ground and below) | the rule of the command | the command digs where the plan has air below the ground (it only comes from `cave_air`) and opens the openings; checked over RCON |
| 57 | In-game test by the user, 13 Nov | the stairs of the glass house started against the glass wall of the front: one could only climb by going in from the side | the template | template replaced (commit `7e0b2a9`): L-shaped stairs from the hall, a 2 × 2 landing in the corner and a continuous iron-bar railing from the foot of the stairs to the gallery |
| 58 | Battery, 13 Nov (street lights) | `parapet` (up to 134 per target) and then `height_mismatch` on the street light columns | the audit treated every non-drivable road column as a highway wall and measured the floor height on all of them | the street light column is out of these two checks and checked whole by `street_light` |
| 59 | `WorldPlanTest`, 13 Nov (benches) | a tree vine "hanging from nothing" over a bench | the bench column returned empty at every height, erasing the crown above | above the bench the tree goes on; only ground plants do not grow through it |
| 60 | `WorldPlanTest`, 13 Nov (benches) | 1 bench per 22.2 blocks of path (asked: 15) | a minimum distance of 12 between benches and few straight stretches of 4 blocks on the spirals | a minimum distance of 8: 1 per 18.6; the rest is the shape of the paths |
| 61 | In-game test by the user, 13 Nov | two rows of terrain just before the garage ramp of the wide building (also with `/amcs spawn`) | only the air of the ground layer with `cave_air` right below stayed open; at the top of the ramp, below is the ramp itself (and at the mouth of the stairs, the step) | the air of the ground layer joined to those openings also stays open (`StructureLoader.openings`, then `Structures.openings`): 99 cells instead of 87 |
| 62 | In-game test by the user, 13 Nov | villagers and an iron golem in a city generated where there was a village | the village creatures spawn with the houses, before the city, and stayed in the chunk when the city was built over them | the mod takes them out of the chunk being generated in the city and up to 32 blocks from it (villagers, zombie villagers, golems, cats) |
| 63 | Battery, 13 Nov (abandoned street lights) | `street_light` (up to 962 per target): street light vines missing | the neighbour repair put the street light parts back and right after it the cleanup of hanging plants over the road erased the vines | the cleanup spares the planned street light parts |
| 64 | Code review, 12 Nov (fingerprint) | the blocks of each target changed between two equal runs of the battery (same code, same seed), the number of block entities too (1,142 and 1,017 on the bridge) | Minecraft itself does not generate the same when the server generates the chunks in parallel: features of neighbouring chunks placed in another order change ores, sculk, trees, beehives and chests (1,073 of 1,369 chunks different in the spawn city, from y −64 up); at the mod's positions only properties that Minecraft recomputes from the neighbours when it finishes a chunk changed: the distance of leaves and the connections of bars and panes (at the edge of the chunk), the shape of a stairs block, the snow on grass, `cave_air` in place of air, a wall side low or tall, a vine face held by a block of the neighbouring chunk; and the empty chest of a fountain only existed as a block entity after reloading the world | the battery fingerprint (`mod blocks:`) covers only the positions the mod decides, with these properties at a fixed value and only the block entities of the structures; checked on worlds generated apart, freshly generated and reloaded from disk (section 6), and sensitive to one block swapped in the palette (`smooth_basalt` → `basalt` in the worn asphalt: the audit passes, the fingerprint changes on the 3 targets) |
| 65 | Code review, 13 Nov (fingerprint) | with the bridge vines in the battery fingerprint, 3 of the 8 targets differed between the reference world and a new world of the same code | in place of the vine (next to the deck, hanging through the air) one world had leaves of a vanilla tree (birch, spruce) and the other the vine; in one column there was air in one world and a vine in the other. Hypothesis (not proven): it depends on the order in which the neighbouring chunks are decorated. The two worlds came from different commits (`4dd94ae` and `254ca56`); what indicates it is not the code is that the compiled `ChunkBuilder` did not change between them (only the 7 calls without the fixed argument) and the planner fingerprint stayed the same | the bridge vines stay out of the fingerprint; in a step that only moves code, the proof is the identical bytecode listing of the moved method |
| 66 | Code review, 13 Nov (English names) | renaming the structures would change their overgrowth | the overgrowth seed of each structure was the `hashCode` of the file name | a `seed` column in `index.txt` with the value of the old name. Checked: a summary of the 52 structures without the names (cells in the 4 rotations, overgrowth, loot access, banners, openings, crowns) equal in the old and the new code (`7e3083fc…`); in the planner fingerprint only the 3 parts that hold names changed (`layout`, `structures`, `weathering`); in the battery, roads and lots equal to the reference in every chunk of the 8 targets and the differences only in the block entities, equal with the old loot table name swapped for the new one (2,133 of 2,139 in the spawn city; the 6 others are vanilla, underground); counts equal |
| 67 | Numbers audit, 13 Nov (step 4 of the review) | the overgrowth totals printed by `WeatheringTest` (64,896 blocks) did not match the game log and the docs (65,086) | since the English names (commit `8d07aea`) the test took the seed from the hash of the English file name, while the game uses the `seed` column of `index.txt`: the test checked another overgrowth than the one generated (the world did not change: the `weathering` fingerprint stayed the same) | the test reads the seed from the index as `Structures` does and checks that its report equals the one the game applied when the structures loaded (`Structures.WEATHERING`); with the old seed this check fails on the first structure |
| — | Battery (infrastructure) | servers failing with "Address already in use" | a server of an interrupted battery was still shutting down | the script uses port 25591 and refuses to start while a development server is alive |

## 4. How to reproduce

```bash
./gradlew test                                   # unit tests, ~2 min (renders in build/test-renders/)
./gradlew test --tests '*WorldPlanTest.streetWidthIsConstantOnStraightStreets'   # a single test
tools/selftest.sh quick 20261027                 # quick battery (3 targets), ~4 min
tools/selftest.sh full 20261027 -3297019724594858492 3478477863671517581 1
cat run/selftest/<seed>/report.txt               # ends with RESULT: PASS or FAIL
./gradlew test --tests '*FingerprintTest'        # planner fingerprint, ~15 s
# checks of a change that must not alter the world (tools/verify/):
python3 tools/verify/compare_battery.py run/selftest/<seed>/report.txt      # against the reference (section 6)
python3 tools/verify/compare_battery.py R.txt tools/verify/reference/battery-20261027-quick-overgrowth-off.txt
tools/verify/javap_dump.sh build/verify/before               # bytecode listing, before the change
tools/verify/javap_dump.sh build/verify/after                # and after (./gradlew compileJava between the two)
python3 tools/verify/compare_methods.py build/verify/before build/verify/after [--owners A,B] [--ignore-slots]
# if a mod blocks: line changes: where (per chunk and column) and which blocks
python3 tools/verify/compare_block_detail.py A/renders/t01_blocks.txt B/renders/t01_blocks.txt
python3 tools/verify/chunk_diff.py run/selftest_world_ref_english <other world> CX,CZ --ymin 60
```

The `run/config/abandonedmoderncity-common.toml` of the development server has old values (`cellSize = 512`,
`maxRadius = 131`) that the mod refuses as inconsistent: the log says "Invalid config ... using the defaults" and the
battery uses the defaults (736 and 230), as the first line of the report shows.

In game (op):
- `/amcs locate city` (or `highway`, `tunnel`, `bridge`);
- `/amcs info`, which shows what the plan wants at the column;
- `/amcs audit 6` and `/amcs plan 12`;
- `/amcs locate structure <category> [name]` and `/amcs spawn <category> <name>`.

Test of the structure commands (12 Nov 2026, development server over RCON, seed 20261027, from 0 70 0; with the
Portuguese names of the time, today `tower`, `pharmacy`, `pharmacy_red`, `houses` and `abandoned_house`):
`locate structure torre` → 1,707 blocks; `farmacia` (group) → `farmacia_vermelho` at 290; `casas` (type) →
`casa_abandonada` at 331; an invalid name lists the accepted names. In the generated world, the coordinate of the
pharmacy falls on the sidewalk (`smooth_stone`) and the column behind it is the lot of the structure (`/amcs info`:
BUILDING). `spawn torre` looking south: the box 2 blocks ahead, 62 wide and centered, its ground on the terrain block,
311,793 blocks and 178 containers with loot; `spawn farmacia_vermelho` looking west: the front facing east (toward
the player). After the banners: `spawn farmacia_vermelho` and `data get block` on the banners at the ends: E (left,
top, middle and bottom stripes, border) and D (stripes and wavy border) — whoever looks at the facade reads
DRUGSTORE.

Bridge vines (12 Nov 2026, RCON): around the longest dry viaduct of seed 20261027 (-1965 86 -1382) there are ~700
vine blocks hanging from the edges; a "bridge" of 12 blocks over a small drop has no vines (its edge columns are
road on the ground, without a bridge wall).

## 5. Measured performance

- **Terrain samples:** each `getBaseHeight` costs ~2.7 ms, because it builds a `NoiseChunk` of one column. Planning
  uses ~9 samples per cell to decide the city, ~5 per block and 1 every 4 blocks of highway. Everything is cached.
- **Generation per chunk in the feature** (planning and neighbour repair included):
  - 1.2–1.3 ms before the cities of 186;
  - in Abandoned Simple City, with buildings: 2.45 ms with the cities of 186 (seed 20261027: a 4 × 4 city with 950
    buildings), between 2.26 and 2.77 ms in the following versions (ladders, facades, elastic depth, rooms);
  - 2.25 ms without buildings (this mod, 7 Nov);
  - for comparison, Minecraft itself spends tens of ms per chunk.
- **Unit tests:** ~2 min in all (without the buildings test, which took ~8 min).

## 6. Current results (12 Nov 2026, blocks of 180 × 120 with a ring of structures with gaps and a core taken over by nature, streets and highways of 33, cities in the middle of cells of 736, 4 entrances)

- **Unit:** 54 pass and 1 is skipped (the test without structures), 14 Nov 2026, with the 7 tests of the barrels of
  the buildings (`BarrelsTest`); 47 + 1 with the tests of the hard containers without the height rule; 46 + 1 with the test of the special
  structures (`WorldPlanTest.specialStructuresAreInAboutOneCityInTen`); 45 + 1 after the cut streets (and their
  test) were taken out; `./gradlew build` in ~2 min (Linux, JDK 21:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build -Dorg.gradle.java.installations.paths=$JAVA_HOME`) (the
  test area shrank with the cell).
  - **Plan audits:** 105 audits column by column, no failures.
  - **Network (7 terrains × 3 seeds, 81 cells), cities in the middle of the cell of 736, 4 entrances (one per
    side):** 35 to 62 cities per combination, 76 to 121 candidate links. Highways kept: 99–119 on the flat, hills and
    canyons (93–98%); 97–112 on the mountains (91–93%); 85–96 on the rivers (83–86%); 91–110 on the mixed terrain
    (85–92%); 59–64 on the islands (76–78%). At most 2 cities without a highway per combination. With any 4 entrances
    of the 8 (rejected on 11 Nov): only 82 of 106 on the flat, 22 lost to a sharp curve, the network split into 4
    groups.
  - **Ring (6,000 blocks), gaps of 4 to 17:** with the 52 files (13 Nov, step 4 of the review) 7.81 structures per
    block, ~25% on each side, 52.5% of the free ground covered, 39.1% houses; in the cities of the 7 terrains, with
    the per-city limits, 6.91 per block (7.29 in the blocks without a park, 4.64 in those with one) and 51.3% houses
    (6.85, 7.22 and 50.7% before the special structures became ~1 city in 10).
    Before: ~8.2 structures per block, ~25% on each side, 51% of the free ground covered (with gaps of 10 to 19 it was
    7.7 and 45%); 13 Nov, with 21 structures: without repeats in the block 8.15
    per block and 50.9% covered; before the rule 8.41 and 51.1%, with 80% of the blocks having some repeated one; an
    earlier simulation of the sizes with gaps: 140 × 90 → 5.0 per block, 160 × 110 → 6.5, 180 × 120 → 7.2,
    200 × 130 → 8.2.
- **Structure overgrowth (13 Nov):** cracked of the family that cracks / green under a roof / green under the open
  sky / outer facade with vines / least covered side (sides of 40+ cells), before → after (targets 31% / 8.2% /
  35.8% / 25% per side; "-" = no candidates):

  | Structure | Before | After |
  |---|---|---|
  | `abandoned_house` | 48% / 0% / 4% / 38% / 28% | 60% / 8% / 35% / 38% / 28% |
  | `abandoned_modern_house` | 36% / 19% / 36% / 36% / 35% | 36% / 19% / 36% / 36% / 36% |
  | `abandoned_mansion` | 48% / 6% / 16% / 10% / 7% | 63% / 8% / 35% / 26% / 25% |
  | `post_apocalyptic_mansion` | 61% / 4% / 5% / 18% / 9% | 70% / 8% / 35% / 26% / 25% |
  | `abandoned_garage_house` | 13% / 6% / 38% / 4% / 1% | 31% / 8% / 40% / 26% / 25% |
  | `abandoned_large_house` | 30% / 9% / 5% / 29% / 22% | 32% / 9% / 36% / 31% / 26% |
  | `abandoned_pool_house` | 19% / 7% / 1% / 11% / 5% | 34% / 8% / 36% / 26% / 25% |
  | `abandoned_suburban_house` | 0% / 9% / 48% / 8% / 0% | 31% / 9% / 50% / 26% / 25% |
  | `abandoned_tower_house` | 31% / 7% / 17% / 18% / 5% | 34% / 8% / 35% / 26% / 25% |
  | `abandoned_victorian_house` | 27% / 8% / 1% / 10% / 8% | 35% / 8% / 36% / 26% / 25% |
  | `pharmacy_red` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 34% / 34% / 26% |
  | `pharmacy_yellow` | 100% / 5% / 12% / 20% / 0% | 100% / 7% / 34% / 34% / 26% |
  | `pharmacy_blue` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 34% / 34% / 25% |
  | `pharmacy_purple` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 35% / 34% / 25% |
  | `pharmacy_green` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 35% / 35% / 26% |
  | `pharmacy_large_yellow` | - / 5% / 15% / 0% / 0% | - / 8% / 34% / 27% / 26% |
  | `pharmacy_large_blue` | - / 5% / 15% / 0% / 0% | - / 8% / 35% / 26% / 26% |
  | `pharmacy_large_purple` | - / 5% / 15% / 0% / 0% | - / 8% / 34% / 26% / 25% |
  | `pharmacy_large_green` | - / 5% / 15% / 0% / 0% | - / 8% / 35% / 26% / 25% |
  | `pharmacy_large_red` | - / 5% / 15% / 0% / 0% | - / 8% / 35% / 26% / 26% |
  | `workshop_medium_blue_black` | 0% / 5% / 13% / 1% / 0% | 35% / 7% / 33% / 28% / 25% |
  | `workshop_medium_yellow_blue` | - / 5% / 13% / 1% / 0% | - / 8% / 35% / 28% / 25% |
  | `workshop_medium_yellow_red` | - / 5% / 13% / 1% / 0% | - / 8% / 35% / 29% / 26% |
  | `workshop_small_green` | 18% / 14% / 15% / 20% / 8% | 35% / 15% / 33% / 28% / 25% |
  | `workshop_small_yellow` | 18% / 14% / 15% / 21% / 4% | 36% / 15% / 33% / 29% / 26% |
  | `workshop_small_blue` | 18% / 14% / 15% / 20% / 8% | 35% / 14% / 34% / 29% / 25% |
  | `workshop_small_purple` | 18% / 14% / 15% / 20% / 8% | 33% / 16% / 34% / 29% / 25% |
  | `workshop_small_red` | 18% / 14% / 15% / 20% / 8% | 34% / 15% / 34% / 29% / 25% |
  | `gas_station_red` | 34% / 0% / 7% / 22% / 14% | 34% / 7% / 34% / 26% / 25% |
  | `gas_station_yellow` | 34% / 0% / 7% / 21% / 14% | 34% / 8% / 34% / 26% / 25% |
  | `gas_station_blue` | 34% / 0% / 7% / 22% / 14% | 33% / 7% / 35% / 26% / 25% |
  | `gas_station_purple` | 34% / 0% / 7% / 21% / 14% | 36% / 8% / 35% / 26% / 25% |
  | `gas_station_green` | 34% / 0% / 7% / 22% / 14% | 35% / 7% / 35% / 26% / 25% |
  | `gas_station_small_red` | 31% / 0% / 2% / 23% / 10% | 33% / 11% / 35% / 29% / 25% |
  | `gas_station_small_yellow` | 31% / 0% / 2% / 23% / 10% | 34% / 10% / 35% / 29% / 25% |
  | `gas_station_small_blue` | 31% / 0% / 2% / 23% / 10% | 32% / 11% / 33% / 29% / 26% |
  | `gas_station_small_purple` | 31% / 0% / 2% / 23% / 11% | 30% / 14% / 35% / 29% / 25% |
  | `gas_station_small_green` | 31% / 0% / 2% / 23% / 10% | 35% / 10% / 34% / 29% / 26% |
  | `apartment_building` | 40% / 6% / 0% / 18% / 15% | 45% / 8% / 35% / 26% / 25% |
  | `skeleton_building_31x29` | 39% / 22% / 41% / 51% / 48% | 39% / 22% / 41% / 51% / 48% |
  | `bluish_building` | 22% / 0% / 7% / 7% / 6% | 31% / 8% / 35% / 26% / 26% |
  | `tower` | 39% / 2% / 4% / 4% / 3% | 49% / 8% / 35% / 26% / 26% |
  | `apocalyptic_building_57x45` | 31% / 9% / 43% / 11% / 9% | 31% / 9% / 43% / 27% / 27% |
  | `abandoned_modern_garage_house` | 13% / 35% / 43% / 32% / 25% | 31% / 35% / 43% / 32% / 25% |
  | `abandoned_glass_house` | 7% / 40% / 44% / 33% / 15% | 31% / 40% / 44% / 37% / 25% |
  | `abandoned_small_building` | 100% / 31% / 14% / 27% / 14% | 100% / 31% / 35% / 29% / 25% |
  | `brutalist_u_building_75x58` | 70% / 6% / 13% / 4% / 3% | 71% / 8% / 35% / 25% / 25% |
  | `abandoned_wide_building` | 27% / 15% / 10% / 24% / 13% | 31% / 15% / 34% / 28% / 25% |
  | `abandoned_green_tower` | 24% / 23% / 21% / 24% / 11% | 33% / 23% / 35% / 28% / 25% |
  | `abandoned_ramp_building` | 32% / 4% / 2% / 2% / 0% | 40% / 8% / 35% / 26% / 25% |
  | `abandoned_crash_building` | 69% / 15% / 23% / 9% / 8% | 69% / 15% / 35% / 26% / 25% |
  | `abandoned_police_station` | 58% / 30% / 38% / 30% / 23% | 58% / 30% / 38% / 31% / 26% |

- **In-world battery** (each row keeps the names in use on its date: before the English names the
  structures, categories and loot tiers had Portuguese names, such as `predio_largo_abandonado` for
  `abandoned_wide_building`, `delegacia_abandonada` for `abandoned_police_station`, the categories `casa`, `predio` and
  `delegacia` for `house`, `building` and `police_station`, and `bau`, `barril` and `vaso` with `_dificil` for
  `chest`, `barrel` and `pot` with `_hard`):

  | Seed | Scope | Date | Version | Result |
  |---|---|---|---|---|
  | `20261027` | default (8 targets) and quick with `structureOvergrowth = false` | 14 Nov | loot theme `building_large` taken out (every building uses `building`) | PASS on both (8 of 8 and 3 of 3), 0 generation errors, log "Structure overgrowth off" on the second; every count equal to the reference; t01–t05 and t08 (and the 3 of the quick run) changed only in the loot table names of the big buildings' containers, t06 and t07 equal: the new references (section 6); 2.79 ms per chunk (4.59 ms with the overgrowth off) |
  | `20261027` | default (8 targets) and quick with `structureOvergrowth = false` | 14 Nov | barrels of the buildings: 70% taken out, the others chests, copy by copy (`core/Barrels`) | PASS on both (8 of 8 and 3 of 3), 0 generation errors, log "Structure overgrowth off" on the second; every target with containers changed only in the structures: block entities and containers down (t01 1,375 → 1,175, t02 2,087 → 1,615; with the overgrowth off the same counts), `structure_loot_hard` down where hard barrels were taken out; roads, bridges, lamps, crowns and positions equal: the new references (section 6); 2.74 ms per chunk, 260 s (4.54 ms with the overgrowth off) |
  | `20261027` | default (8 targets) | 13 Nov | review, step 4: code comments without dates, the docs in English (`docs/TESTES.md` → `docs/TESTING.md`), the numbers audit, `WeatheringTest` with the seed of the index (finding 67) and new prints in `RingTest` and `WorldPlanTest`; the code of the mod unchanged since the comments commit (`5eb5b74`), whose bytecode was equal to the commit before in all 1,322 members of the mod and 176 of the tests | PASS: 8 of 8, 0 generation errors; `tools/verify/compare_battery.py` equal to the reference (the 8 `mod blocks:` lines and the counts); 142 palette states, 16 village creatures removed; 2.62 ms per chunk, 240 s |
  | `20261027` | default (8 targets) and quick with `structureOvergrowth = false` | 13 Nov | height rule of the hard containers removed; the chest in the tree of the abandoned modern house listed as hard (`index.txt`, column `hard`) | PASS on both, 0 generation errors; loot containers and block entities equal, `structure_loot_hard` down (t01 291 → 16, t02 1,199 → 15, t03 469 → 18); compared on the saved worlds: only `_hard` → normal loot table names and the tree chests (5 and 2) normal → `chest_hard`, no container of the mod moved: the new references; 2.87 ms per chunk |
  | `20261027` | default (8 targets) | 13 Nov | loot theme `building_large` (buildings with 100+ containers) | PASS: 8 of 8, 0 generation errors; counts equal to the reference; t01–t05 and t08 changed only in the loot table names of the big buildings' containers (checked on the saved worlds); 2.72 ms per chunk |
  | `20261027` | quick with `structureOvergrowth = false` | 13 Nov | loot theme `building_large` | PASS: 3 of 3, 0 generation errors, log "Structure overgrowth off"; counts equal to the reference; the 3 `mod blocks:` lines changed only in loot table names (checked on the saved worlds): the new reference; 4.48 ms per chunk |
  | `20261027` | default (8 targets) | 13 Nov | special structures in ~1 city in 10 (`City.SPECIAL_CHANCE` = 0.40) | PASS: 8 of 8, 0 generation errors, 16 village creatures removed; t01, t02 and t04–t07 equal to the reference; t03 and t08 changed only in their structures (lots 30 → 33 and 66 → 68, roads and streets equal): the new reference (section 6); 2.81 ms per chunk (8,346 chunks). Not run: the quick reference with `structureOvergrowth = false`, whose 3 targets (t01, the longest tunnel and bridge) are equal in this run |
  | — | RCON | 13 Nov | `/amcs locate structure <category> [name]` (development server, a new world `cmdtest_world`, from 0 70 0) | `pharmacy` → `pharmacy_large_yellow` at 360 blocks; `house` → `abandoned_modern_garage_house` at 315; `special` → `abandoned_ramp_building` at 990; `building abandoned_wide_building` → at 1,241; `police_station abandoned_police_station` → at 465; `building pharmacy_red` refused with the 10 names of `building`; `shop` and `tower` (the old one-word form) refused with the 7 categories; `locate city` and `highway` unchanged; `spawn pharmacy pharmacy_red` placed it (22 containers with loot), `spawn building pharmacy_red` and `spawn shop x` refused; the server log only with the known warning of the old dev config |
  | — | RCON | 13 Nov | commands with the English names (development server, world `cmdtest_world`) | `/amcs locate structure abandoned_wide_building` (name), `pharmacy` (group) and `buildings_large` (type) found the nearest ones; `execute positioned 1000 200 1000 rotated 180 0 run amcs spawn house abandoned_house` placed the house (57 containers with loot, 1 hard to get to) and `... spawn building abandoned_wide_building` the wide building (249, 191 hard to get to); in the saved world the chests have the new tables (`chests/house/barrel` 46, `house/chest` 10, `house/chest_hard` 1; `building/barrel` 29, `barrel_hard` 87, `chest` 29, `chest_hard` 104); `spawn house abandoned_wide_building` refused with the list of the houses and the old category `casa` with the list of the new categories |
  | `20261027` | default (8 targets) and quick with `structureOvergrowth = false` | 13 Nov | structure names, categories, groups and loot tables in English (finding 66) | PASS on both, counts equal to the previous references; roads and lots equal in every chunk; only the names of the loot tables in the chests changed, and the `mod blocks:` lines became the new reference (above); 2.69 ms per chunk, 243 s |
  | `20261027` | default (8 targets) | 13 Nov | review: classes named after the old mod renamed (`AMCSimple` → `AbandonedModernCity`, `SimpleConfig` → `AmcsConfig`, `SimpleCommands` → `AmcsCommands`) | PASS: `tools/verify/compare_battery.py` equal to the reference (8 of 8); 2.65 ms per chunk, 244 s. Bytecode equal in all 1,322 members with the old names swapped for the new ones (`compare_methods.py`) |
  | `20261027` | default (8 targets) | 13 Nov | review, step 3c: the neighbour repair moves out of `ChunkBuilder` into `gen/NeighbourRepair` | PASS: 8 of 8, 0 generation errors; `mod blocks:` and counts equal to the reference; 2.69 ms per chunk, 250 s. Planner fingerprint equal; method by method, of the 61 members of `ChunkBuilder` 54 kept the same body (bridge vines, fill, slopes, liquid sealing included) and 7 went to `NeighbourRepair` with the same body; no other class changed |
  | `20261027` | default (8 targets) and quick with `structureOvergrowth = false` | 13 Nov | review, step 3b: the preparation of the templates moves out of `Structures` into `core/StructureLoader` | PASS on both: `mod blocks:` and counts equal to the reference (8 of 8) and to the reference with the overgrowth off (3 of 3); 2.71 ms per chunk, 253 s. Planner fingerprint equal (with `structures` and `weathering`); method by method: the 14 methods and constants moved with the same body (except for `blockOf`/`isLogName`/`withProps` now called through the class name and `ldc` in place of `ldc_w`) |
  | `20261027` | default (8 targets) | 13 Nov | review, step 3a: street lights move out of `City` into `core/StreetLights` | PASS: 8 of 8, 0 generation errors; `mod blocks:` and counts equal to the reference; 2.93 ms per chunk, 263 s. Planner fingerprint equal (with `lamps`); method by method: `lampAt`, `lampVine` and the 28 constants with the same body, `lampParts` only with another way of loading the same constant, `lampCandidate` only reading the seed from the argument instead of the city's field |
  | `20261027` | quick (3 targets) | 13 Nov | reference with `structureOvergrowth = false` (log "Structure overgrowth off") | PASS: 3 of 3, 0 generation errors; a new world and the reloaded one with the same 3 `mod blocks:` lines (the reference above); 4.59 ms per chunk, 149 s |
  | `20261027` | default (8 targets) | 13 Nov | review: 3 constants left unused (`Params.ARC_RADIUS`, `AVENUE_EVERY`, `LOT_SETBACK`) | PASS: 8 of 8, 0 generation errors; `mod blocks:` and counts equal to the reference; 142 palette states; 2.79 ms per chunk, 253 s. Planner fingerprint equal |
  | `20261027` | default (8 targets) | 13 Nov | review, step 2d: cut streets taken out (`City.MAX_CUTS`, the drawing of the cuts, `removed_street` and their test) | PASS: 8 of 8, 0 generation errors; the 8 `mod blocks:` lines equal to the reference and the counts equal (except `loot_containers_anywhere`); 142 palette states; 2.73 ms per chunk, 248 s. Planner fingerprint equal |
  | `20261027` | default (8 targets) | 13 Nov | review, step 2c: dead code (the Simple City building styles, the plaza and empty lot types, 26 block codes, the `style` argument of `Palette.of`, 11 unused declarations) | PASS: 8 of 8, 0 generation errors; the 8 `mod blocks:` lines equal to the reference and the counts equal (except `loot_containers_anywhere`); 142 palette states validated (there were 218: those of the styles left); 2.71 ms per chunk, 250 s. Planner fingerprint equal |
  | `20261027` | default (8 targets) | 13 Nov | review, step 2b: imports in place of full names (213 names, 26 files) | PASS: 8 of 8, 0 generation errors; `mod blocks:` and counts equal to the reference; bytecode equal instruction by instruction (`javap -c -p`); 2.74 ms per chunk, 253 s |
  | `20261027` | default (8 targets) | 13 Nov | review, step 2a: LF line endings (`.gitattributes`, 18 files) | PASS: 8 of 8, 0 generation errors; `mod blocks:` and counts equal to the reference; compiled classes identical byte for byte; 2.76 ms per chunk, 252 s |
  | `20261027` | default (8 targets) | 12 Nov | code review, step 1: fingerprint (`FingerprintTest` and `mod blocks:`); generation equal to that of commit `4dd94ae` | PASS: 8 of 8, 0 generation errors, 218 palette states validated, 16 village creatures removed; 2.73 ms per chunk, 252 s. A new world, the same world reloaded from disk and another world (generated earlier, reloaded): the 8 `mod blocks:` lines equal (the reference below); `stats:` counts equal to those of another run, except `loot_containers_anywhere` |
  | — | RCON | 13 Nov | `/amcs spawn <category> <name>` (no generation battery: only the command changed) | `predio predio_largo_abandonado` and `delegacia delegacia_abandonada` placed; `casa predio_largo_abandonado` refused with the list of the houses; the category `igreja` refused with the list of the categories; the old one-argument form refused by the game |
  | `20261027` | default (8 targets) | 13 Nov | police station (1 per city) and crash building (52 files) | PASS: 8 of 8, 0 generation errors, 1,408 structure states validated; 2.77 ms per chunk, 256 s. Over RCON, `/amcs spawn delegacia_abandonada` on stone: the rooms of the basement dug on layers 1, 2 and the street one, the stone outside them intact, a letter banner with its design; 63 containers with loot, 13 hard to get to |
  | `20261027` | default (8 targets) | 13 Nov | abandoned street lights in 5 variants | PASS: 8 of 8, 0 generation errors, ~3,460 street light blocks checked per city (with the vines), 218 palette states validated; 2.77 ms per chunk, 274 s |
  | `20261027` | default (8 targets) | 13 Nov | village creatures removed from the cities | PASS: 8 of 8, 0 generation errors, 16 village creatures removed; 2.77 ms per chunk, 259 s. Over RCON, seed 20261027, a snowy village at (−240, 144) on a street of the spawn city: before, 4 villagers, 1 golem and 1 cat in a square of 160 × 160 (a villager at y 64, under the street with its floor at 69); after, 1 villager, at (−235, 89), ~49 blocks past the street (outside the margin, from a house left standing outside the city) |
  | `20261027` | default (8 targets) | 13 Nov | the top of the ramp and the mouth of the stairs of the wide building open | PASS: 8 of 8, 0 generation errors; 2.85 ms per chunk, 251 s. Over RCON, `/amcs spawn predio_largo_abandonado` on stone: the two rows at the top of the ramp and the mouth of the stairs open, the slab and the concrete of the ramp in place (10 of 10 checked) |
  | `20261027` | default (8 targets) | 13 Nov | buildings at most 1 per city, street lights every 24, park benches, L-shaped stairs in the glass house | PASS: 8 of 8, 0 generation errors; ~2,300 street light blocks checked per city; 214 palette block states validated; 2.68 ms per chunk, 266 s |
  | `20261027` | default (8 targets) | 13 Nov | 7 new structures (50 files), the wide building with its garage dug and its ramp open, the ramp building 1 per city | PASS: 8 of 8, 0 generation errors, 1,385 structure states validated, 59,904 overgrowth blocks; 2.75 ms per chunk, 246 s. Over RCON: `/amcs spawn predio_largo_abandonado` on a volume of stone: the mouth of the ramp open, 4 points of the garage dug, a floor of `smooth_stone`, the stone outside the garage intact (7 of 7 checked); 249 containers with loot, 191 hard to get to |
  | `20261027` | default (8 targets) | 13 Nov | the F A R M Á C I A sign erased from the variants; the banners of the small pharmacies at the position of the original | PASS: 8 of 8, 0 generation errors, pharmacy banners checked; 2.68 ms per chunk, 257 s |
  | `20261027` | default (8 targets) | 13 Nov | long, aggressive vines on all 4 sides (25% of the facade, strands of 3 to 12; 34,609 overgrowth blocks) | PASS: 8 of 8, 0 generation errors, no `building_mismatch`; 2.63 ms per chunk, 253 s |
  | `20261027` | default (8 targets) | 13 Nov | nature taking over the structures (`structureOvergrowth` on, 18,341 blocks) | PASS: 8 of 8, 0 generation errors, no `building_mismatch` (plants and vines stayed in the world as in the plan); 2.72 ms per chunk, 266 s |
  | `20261027` | quick (3 targets) | 13 Nov | the same with `structureOvergrowth = false` | PASS: 3 of 3; log "Structure overgrowth off" |
  | `20261027` | default (8 targets) | 13 Nov | 22 color variants (drawn per model, banners copied on the pharmacies) | PASS: 8 of 8, 0 generation errors, 1,115 structure states validated, pharmacy banners checked with their design; 2.70 ms per chunk, 263 s |
  | `20261027` | default (8 targets) | 13 Nov | cut crowns completed outside the structures | PASS: 8 of 8, 0 generation errors, 0 `far chunk` warnings; 151 crown leaves checked in the spawn city, 324 in the highest one, 307 in the roughest one; 2.62 ms per chunk, 250 s |
  | `20261027` | default (8 targets) | 13 Nov | never two of the same structure in one block | PASS: 8 of 8, 0 generation errors; in the spawn city 906 containers with the right table, 156 hard to get to; 2.87 ms per chunk, 247 s |
  | `20261027` | default (8 targets) | 13 Nov | vanilla loot per theme, container and access | PASS: 8 of 8, 0 generation errors; in the spawn city 902 containers with the right table, 105 hard to get to; 2.68 ms per chunk, 256 s. Over RCON, 300 draws of each of the 30 tables: barrel empty in 58–62% (0.74–0.85 stacks), `barril_dificil` empty in 19–30% (2.1–2.4 stacks), chest 4.5–4.6 stacks, `bau_dificil` 7.9–8.1, pot empty in 58–65%, `vaso_dificil` in 19–22%; potions with effects, worn and enchanted tools and enchanted books checked; `/amcs spawn` of the apocalyptic building placed 73 containers, 56 hard to get to |
  | `20261027` | default (8 targets) | 13 Nov | lightning rods swapped for redstone torches | PASS: 8 of 8, 0 generation errors, 2.72 ms per chunk, 261 s |
  | `20261027` | default (8 targets) | 13 Nov | 7 new structures (6 houses and the skeleton building, 21 in all), the post-apocalyptic mansion updated, chiseled bookshelves allowed | PASS: 8 of 8, 0 generation errors, 2.71 ms per chunk, 253 s |
  | `20261027` | default (8 targets) | 13 Nov | a park of 110 × 64 in a corner, structures in an L | PASS: 8 of 8, 0 generation errors, 1 fountain checked, 2.69 ms per chunk, 252 s |
  | `20261027` | default (8 targets) | 12 Nov | worn asphalt (`polished_deepslate`), 30% taken, old bridges (mossy deck and pillars, broken and mossy wall, holes), no cave-ins in the tunnels | PASS: 8 of 8, 0 generation errors; on the longest bridge 87 hole columns, 53 of fallen wall and 5,976 taken; 2.74 ms per chunk, 268 s |
  | `20261027` | default (8 targets) | 12 Nov | abandoned streets and bridge vines | PASS: 8 of 8, 0 generation errors; in the spawn city 7,717 of 76,027 road columns on the ground taken (10.2%) and 2,413 plants checked; 2.86 ms per chunk, 279 s |
  | `20261027` | default (8 targets) | 12 Nov | gaps of 4 to 17 | PASS: 8 of 8, 0 generation errors, 2.82 ms per chunk |
  | `20261027` | default (8 targets) | 12 Nov | blocks of 180 × 120, gaps of 10 to 19 and woods of 20 to 30 | PASS: 8 of 8, 0 generation errors; in the roughest city 1,180 containers with loot and 45 banners checked; 8,346 chunks, 2.74 ms per chunk, 267 s |
  | `20261027` | default (8 targets) | 12 Nov | the post-apocalyptic mansion turned, loose cobwebs removed, banners with their design | PASS: 8 of 8, 0 generation errors, 126 pharmacy banners checked with their design, 3.06 ms per chunk |
  | `20261027` | default (8 targets) | 12 Nov | the modern house turned 180° | PASS: 8 of 8, 0 generation errors, 2.94 ms per chunk |
  | `20261027` | default (8 targets) | 12 Nov | 14 structures, limits per city, aligned ground, floor out to the sidewalk, real leaves in the structures | PASS: 8 of 8, 0 generation errors; in the spawn city alone 1,261 structure block entities and 744 containers with a loot table checked; 2.93 ms per chunk, 227 s |
  | `20261027` | default (8 targets) | 11 Nov | mossy fountain floor | PASS: 8 of 8, 0 generation errors, 1 fountain checked, 2.93 ms per chunk |
  | `20261027` | default (8 targets) | 11 Nov | 4 entrances, cells of 648, crosswalks, real trees, mossy ring, long vines | PASS: 8 of 8, 0 generation errors, 7,948 chunks, 2.91 ms per chunk, 231 s |
  | `20261027` | default (8 targets) | 11 Nov | cores and parks taken over by nature | PASS: 8 of 8, 0 generation errors, 952,433 lot blocks checked on the spawn target alone, 9,096 chunks, 9.28 ms per chunk, 352 s |
  | `20261027` | default (8 targets) | 10 Nov | streets and highways of 33, no structures | PASS: 8 of 8 (spawn city, highest, roughest, tunnel of 282, bridge of 645, viaduct of 172, ramp of 480, whole highway), 0 generation errors, 9,096 chunks, 8.49 ms per chunk, 324 s |
  | `20261027` | default (8 targets) | 8 Nov | blocks of 140 × 90 with the 33 structures | PASS: 8 of 8, 13.53 ms per chunk (after extending the neighbour repair to the whole height of the structures) |
  | `20261027` | `quick` | 7 Nov | copy without buildings | PASS: 4 targets, a 4 × 4 city with 2 lots (parks) and 37,765 columns of free ground checked, 18,953 road columns, 2.25 ms per chunk |

  The battery has not run yet after the removal of the octagonal and 3 × 2 cities (the target "nearest 3 × 2 city to
  the spawn" left) nor after the change to blocks of 105 × 60 (cells of 512) and 140 × 90 (cells of 576). On 8 Nov,
  on Linux (CachyOS, JDK 21), the `-PselfTest` battery passed 8 of 8 targets with the blocks of 105 × 60 on a new
  world (an old test world, generated before the changes, gives false failures on the spawn target: rename
  `run/selftest_world_*`). It is worth running the full battery when there are changes: `tools/selftest.sh full`.
- **Reference of the battery fingerprint** (`mod blocks:`, seed `20261027`, default scope, 14 Nov 2026, with the
  structure names in English, the special structures in ~1 city in 10, the barrels of the buildings and without the
  loot theme `building_large`; the blocks are those of commit `4dd94ae`, finding 66). In a change that must not alter
  the world, the 8 lines stay equal to these:

  | Target | SHA-256 | Positions | Block entities |
  |---|---|---|---|
  | t01 city nearest to the spawn | `f0f82a15aec4c20875a03c46ae5fa97c65347d826cebbcaa00cbfae118f4b08b` | 1,877,158 | 1,855 |
  | t02 highest city (level 103) | `7f4018583fa3a2b9f2906a2f28f8dc49ee5ba457f194819e4b0f7d101c3c64a5` | 3,290,072 | 2,915 |
  | t03 roughest city | `a5ee3c0ccfddbe6b76e0660e88d4acdee99ee92e9ec8cf1563dad5da7d0f87cf` | 2,225,236 | 1,998 |
  | t04 longest tunnel (352) | `36116c59174cd0a45ca9fd15f2e99ed064c731b77561a6a825d14bb814a66461` | 543,064 | 132 |
  | t05 longest bridge over water (349) | `45125b3abcf3c4d3f109650544e7cb31ff50cc1f25c83a7fd5c49da56d69aea4` | 452,586 | 512 |
  | t06 longest dry viaduct (239) | `3f0d0eb10108fd93b83762b06517db3ffd86ada834a63a8232b3d5b351bd1617` | 581,916 | 356 |
  | t07 longest ramp (360) | `f960ad531d4199d12ecf42bb72b87d9a199bc1fccb85235572528b07c3dcc118` | 191,450 | 0 |
  | t08 whole highway | `11480fcdff81109bb9cb3f08598cfef3ac4221c6fc69368b9ba38e557ad7b6e7` | 822,838 | 527 |

  Since the loot theme `building_large` was taken out (14 Nov 2026) t01–t05 and t08 changed only in the loot table
  names of the big buildings' containers (`building_large/...` → `building/...`): the same positions, block entities
  and counts (t06 and t07, without those buildings, the same; the planner's `lots` digest the same). Before, since
  the barrels of the buildings are taken out (70%) or turned into chests, copy by copy (14 Nov 2026,
  `core/Barrels`), every line but t07 (no container) changed: the same positions and the same roads, streets,
  bridges, lamps and crowns (all the other counts equal), fewer block entities and containers in the structures
  (`structure_loot_containers` t01 1,375 → 1,175, t02 2,087 → 1,615, t03 1,377 → 1,153, t04 127 → 95, t05 300 → 207,
  t06 217 → 192, t08 363 → 317) and fewer hard ones (the hard barrels taken out: t01 16 → 11, t02 15 → 9, t03 18 →
  13, t06 14 → 9); 0 generation errors and no `building_mismatch` (the world is the plan, which has no barrel left in
  the buildings). Before, since the height rule of the hard containers was removed (after 13 Nov 2026) every line
  but t07 (no container) changed only in the loot
  table names: the containers no longer out of reach use the normal tables (2,494 in the containers of the mod of
  this run, `_hard` → normal) and 5 chests in the trees of abandoned modern houses became `chest_hard`; positions,
  block entities and the other counts the same, `structure_loot_hard` down (t01 291 → 16, t02 1,199 → 15); the world
  of this run is in `run/selftest_world_ref_nohigh/` (with the overgrowth off: `run/selftest_world_ref_off_nohigh/`,
  623 renamed, 2 tree chests). Before, since the loot theme `building_large` (buildings with 100+ containers) t01–t05 and t08 changed only in the loot
  table names of those buildings' containers (`building/...` → `building_large/...`): the positions, block entities
  and counts are the same, and a comparison of the two saved worlds found no other difference in the containers of
  the mod (1,146 hard barrels, 886 hard chests, 436 barrels, 179 chests, 92 pots renamed; the block entities at
  other positions are vanilla ones: sculk, beehives, spawners, vanilla chests). The world of this run is in
  `run/selftest_world_ref_large/`. Since the special structures became ~1 city in 10 (`City.SPECIAL_CHANCE`, after 13 Nov 2026) t03 and t08 are
  new: their cities lost the ramp building (t03: 30 → 33 lots, 790 → 469 hard-to-get-to containers; t08: 66 → 68
  lots), the roads and streets the same (equal `road_columns`, `street_columns`); before, t03 was
  `2e2476a9…` (2,506,369 positions) and t08 `a4def9aa…` (1,280,207). The world of this run is (outside git) in
  `run/selftest_world_ref_special/`. Before the English names (generation of `4dd94ae`) there were 7 other lines (t07, without containers, is the
  same): only the names of the loot tables inside the chests changed. The full report is in git in
  `tools/verify/reference/battery-20261027-full.txt` (what `tools/verify/compare_battery.py` compares); the world of
  that run is (outside git) in `run/selftest_world_ref_english/`, and the one from before the names in
  `run/selftest_world_ref_4dd94ae/` (same blocks), to compare block by block if a line changes.
- **Reference with the overgrowth off** (`structureOvergrowth = false`, quick scope, 14 Nov 2026, without the loot
  theme `building_large`: the 3 lines changed only in the loot table names, counts equal; before, the same day, with
  the barrels of the buildings taken out or turned into chests: block entities and containers down as in the default
  scope, roads and positions equal; before, 13 Nov 2026, with the English names and the loot theme `building_large`, the 3 lines
  changed only in the loot table names of the containers, checked on the saved worlds, 0 containers of the mod at
  other positions; the world of that run is in `run/selftest_world_ref_off_large/`; the previous one, with the
  Portuguese names, was taken at commit `254ca56`, and the two have the same roads and lots in every chunk):

  | Target | SHA-256 | Positions | Block entities |
  |---|---|---|---|
  | t01 city nearest to the spawn | `b20a2a541e2644137382a277fa3f4dcfd684f8e6577d39ac377e039898ba3e10` | 1,874,811 | 1,855 |
  | t02 longest tunnel (352) | `4a0cee821e078057871930a7385e81cd1c343864afe4e6e6781d83acdf7cf2e7` | 541,806 | 132 |
  | t03 longest bridge over water (349) | `e05755eede6a4e0bda2b2d79b46d92c51d99f3eacbedc03ea76e5df4d3121679` | 452,586 | 512 |

  Report in git in `tools/verify/reference/battery-20261027-quick-overgrowth-off.txt`; world (with the Portuguese
  names, same blocks) in `run/selftest_world_ref_off/` (outside git).
- In no run has there been a step, hole, obstacle, floating pillar, unconnected highway or unbreakable block on the
  roads, bridges and tunnels after fixes 4 and 5. The containers of the structures have their own vanilla loot tables
  (13 Nov 2026: theme of the structure × kind of container × access), audited by `structure_without_loot`,
  `structure_wrong_loot`, `loot_in_city` and `container_in_city`.
