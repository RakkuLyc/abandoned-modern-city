# Abandoned Modern City — development notes

The player's page is the [README](../README.md); this file is for whoever builds, tests or changes the mod.

Procedurally generated abandoned modern cities: streets, highways, tunnels, bridges, city blocks and parks.
The buildings are hand-built `.nbt` structures (30 models, 52 files with the color variants), drawn at random in a
ring around each city block, with per-city limits for pharmacies, workshops and gas stations.

> It started on 7 Nov 2026 as a copy of Abandoned Simple City (repository `abandoned-simple-city`), without its
> buildings and without its commit history. The original Abandoned Modern City (folder
> `AbandonedModernCity-Deprecated`) is discontinued.

- modid `abandonedmoderncity` · package `com.abandonedmoderncity` · Java 21 · NeoForge 21.1.219 ·
  ModDevGradle 2.0.140
- Build: `./gradlew build` → `build/libs/abandonedmoderncity-1.0.0.jar` (also runs the unit tests, ~2 min); the jar
  only: `./gradlew build -x test`
- Unit tests: `./gradlew test` (renders in `build/test-renders/`)
- In-world test battery: `tools/selftest.sh [full|quick] [seed...]` (reports in `run/selftest/<seed>/`); details in
  `docs/TESTING.md`
- Checking that a change did not alter the world: `tools/verify/` (the battery fingerprint against the reference,
  bytecode listings method by method, the blocks of two saved worlds); details in `docs/TESTING.md`
- No optional dependency: no Immersive Vehicles, Zombie Extreme, Big Lost City or mixins.
- `libs/` is **not versioned** (it is in `.gitignore`) and **not needed to build**: `./gradlew build` works without
  it. The folder holds jars of third-party mods used only as reference material (the loot tables of Apocalypse Loot,
  the vehicle parts of OAmP, Immersive Vehicles and MTS Official Pack). Whoever clones the repository and wants to
  redo that survey copies the jars into `libs/` by hand: `ApocalypseLoot-1.1.1.jar`,
  `Immersive Vehicles-1.21.1-24.0.0.jar`, `MTS Official Pack-1.21.1-V29.jar` and `OAmP-1.21.1-V3.jar`.
- The complete, current specification is in `CLAUDE.md`; how it got there (dates, what changed and why) in
  `docs/HISTORY.md`.

## What is generated

### Cities

| Kind | Where | Shape |
|---|---|---|
| 2 × 2 city | one in the middle of each cell of 736 blocks (70% of the cells) | a rigid grid of 2 × 2 city blocks of 180 × 120 (street axes 213 and 153 apart, ~459 × ~339 blocks with the streets, 3 × 3 streets; the long side runs east-west or north-south, drawn per city), 4 highway entrances, one per side (its middle crossing or one of its two corners, drawn) |

There is only this kind of city: the octagonal cities and the 3 × 2 highway towns (with the rural_highway pattern and
the fields) were removed on 7 Nov 2026; the grid went from 4 × 4 blocks of 186 (cells of 2,048) to 2 × 2 blocks of
105 × 60 (cells of 512) and, on 8 Nov 2026, to 140 × 90 (cells of 576, to fit the structures of `structures/`); on
10 Nov 2026 streets and highways became 33 wide (cells of 640); on 11 Nov 2026 the cities moved to the middle of
cells of 648 and got 4 entrances; on 12 Nov 2026 the city blocks became 180 × 120 (cells of 736, highways of ~277 to
~397 blocks), with gaps between the structures.

- Each city stands at a single height: water is filled, hills are cut, and a 1:1 slope makes the transition.
- Every city block has its 4 streets (the random removal of street segments, which was switched off, was taken out
  on 13 Nov 2026).
- **City blocks:** the structures form a ring against the sidewalks, their front (the -Z side of the `.nbt`) facing
  the street; the rest (the core) is taken over by nature. About 1 block in 8 has a park of 110 × 64 in a corner,
  with the structures in an L along the two other streets.
- **Structures (30 models, 52 files, `structures/`):** drawn at random in the ring (every model with the same chance,
  then its color: the 12 houses make ~52% of the structures of the cities), with a per-city limit: pharmacies 3,
  workshops 2, gas stations always 2 (2 small or 1 small and 1 large, half the cities each; never 2 large), and each building (apartment, skeleton, bluish, tower, apocalyptic, small, brutalist,
  wide, green tower, crash, ramp) at most 1 per city; the ramp building (the `special` category) in only ~1 city in
  10 (`CityRings.SPECIAL_CHANCE`). Never two of the same structure (the same model, in any color)
  in one block.
  The buildings are classified by the height of their box (buried layers included; the `type` of `index.txt` and the
  `/amcs spawn` / `locate` category follow it): **small** up to 40 blocks, **medium** up to 75, **large** up to 140 and
  **immense** above 140, each limit with a margin of 3 blocks (a building up to 3 over a limit stays in the lower class:
  small up to 43, medium up to 78, large up to 143). Today: small 3 (apartment building 37, small building 41, skeleton
  building 43), medium 0, large 7 (wide building 80, crash building 90, bluish building 106, apocalyptic building 110,
  tower 118, brutalist U building 125, green tower 143), immense 0 (the categories exist for future buildings). The
  ramp building (`special`, 134) and the police station (own category, 33) stay out of it. The class changes neither the
  loot (every building uses the `building` theme) nor the barrels, nor the sorting of the ring.
  Color variants: pharmacy and large pharmacy (5 colors each, the D R U G S T O R E letters copied from the original
  in the variant's color, without the F A R M Á C I A sign in blocks the variants had: the wall stays white as on
  the original), medium workshop (3), small workshop (5), gas station and small gas station (5 each). The medium
  workshop has 3 chests added to its `.nbt` (17 Nov 2026): one in the work bay, one in the back room, one on the terrace.

  | Structure | Type | Box (front × depth × height) | Ground |
  |---|---|---|---|
  | Abandoned house | houses | 29 × 46 × 20 | layer 0 |
  | Abandoned modern house (front on the +Z side of the template: turned 180°) | houses | 30 × 29 × 15 | layer 0 |
  | Abandoned mansion | houses | 41 × 57 × 21 | layer 1 (1 down) |
  | Post-apocalyptic mansion (front on the +Z side of the template: turned 180°) | houses | 37 × 41 × 18 | layer 0 |
  | Abandoned garage house | houses | 32 × 28 × 17 | layer 0 |
  | Abandoned large house | houses | 41 × 47 × 23 | layer 1 (1 down) |
  | Abandoned pool house | houses | 34 × 45 × 23 | layer 0 |
  | Abandoned suburban house | houses | 51 × 43 × 20 | layer 0 |
  | Abandoned tower house | houses | 40 × 44 × 24 | layer 0 |
  | Abandoned Victorian house | houses | 42 × 47 × 23 | layer 0 |
  | Pharmacy (red, yellow, blue, purple, green) | pharmacy (limit 3 with the large one) | 19 × 28 × 16 | layer 0 |
  | Large pharmacy (yellow, blue, purple, green, red) | pharmacy | 26 × 40 × 15 | layer 0 |
  | Medium workshop (blue and black, yellow and blue, yellow and red) | workshop (limit 2 with the small one) | 35 × 26 × 17 | layer 0 |
  | Small workshop (green, yellow, blue, purple, red) | workshop | 22 × 33 × 16 | layer 0 |
  | Gas station (red, yellow, blue, purple, green) | gas station (with the small one, always 2 per city, never 2 large) | 45 × 37 × 18 | layer 0 |
  | Small gas station (red, yellow, blue, purple, green) | gas station | 25 × 20 × 18 | layer 0 |
  | Apartment building | building (small) | 31 × 29 × 37 | layer 1 (1 down) |
  | Skeleton building (`skeleton_building_31x29`) | building (small) | 31 × 29 × 43 | layer 0 |
  | Bluish building | building (large) | 66 × 46 × 106 | layer 1 (1 down) |
  | Tower | building (large) | 62 × 53 × 118 | layer 1 (1 down) |
  | Apocalyptic building | building (large) | 57 × 45 × 110 | layer 0 |
  | Modern garage house | houses | 42 × 36 × 15 | layer 0 |
  | Glass house | houses | 48 × 40 × 15 | layer 0 |
  | Small building | building (small) | 23 × 31 × 41 | layer 0 |
  | Brutalist U building | building (large) | 75 × 58 × 125 | layer 0 |
  | Wide building (underground parking dug into the terrain, whole ramp and stairwell open) | building (large) | 84 × 31 × 80 | layer 8 (8 buried) |
  | Green tower | building (large) | 31 × 31 × 143 | layer 0 |
  | Ramp building (folder Especial) | special (at most 1 per city, in ~1 city in 10) | 102 × 72 × 134 | layer 0 |
  | Crash building (two leaning towers) | building (large) | 91 × 44 × 90 | layer 0 |
  | Police station (half-buried basement dug into the terrain, letters on banners) | police station (at most 1 per city) | 54 × 38 × 33 | layer 3 (3 buried) |

  - Against the sidewalk without a gap: the edge rows without a floor get the floor of the first row that has one.
  - The trees of the structures decay like real trees (leaves linked to a trunk); hedges without a trunk stay
    persistent.
  - Crowns cut by the edge of the template are completed by mirroring the crown around its trunk: 189 leaves in 7
    structures (pool house, garage house, suburban house, tower house, glass house, skeleton building and ramp
    building), hanging over the woods or, more than 7 blocks up, over the sidewalk.
  - The structures never touch: a gap of 4 to 17 blocks drawn between neighbours, and always 20 to 30 blocks of
    woods between the backs of opposite sides. The trees of the cores stay out of the structures.
  - The banners keep the design of the template: the pharmacies have the letters DRUGSTORE.
  - Loose cobwebs (with nothing to hang on) are removed.
  - Nature taking over (`core/Weathering`, modeled on the apocalyptic building, switched off with
    `structureOvergrowth = false`): each structure gets what it lacks to reach that building's measures, without
    changing size: cracked stone in patches (31% of the bricks and tiles that crack), spots of moss, grass and dirt on
    the floors (8% under a roof, 36% under the open sky) with a moss carpet, grass, a fern or a dead bush, long vines
    on all 4 sides (25% of the outer facade of each side, strands of 3 to 12 blocks in patches, on every structure)
    and mossy cobblestone and stone bricks near the green. 65,082 blocks in the 52 files.
  - Lightning rods become lit redstone torches (5: on top of the tower house, the post-apocalyptic mansion, the
    apartment building, the apocalyptic building and the tower).
  - Barrels of the buildings (`core/Barrels`): in the 10 structures of the `building_*` categories, 70% of the barrels
    are taken out and the others become chests, drawn again in each copy (two towers are not the same). A barrel
    that holds something (a slab or a pot on top, a vine fixed to it) is never taken out and stays a barrel (16 Nov
    2026: the counters of the tower's ground floor were ~24 chests in every copy); in a stack the top ones go first.
    794 barrels, 26 that always stay, ~212 chests per set of the 10 buildings.
  - Chests of the buildings (15 Nov 2026, first step of the procedural chests): the 556 chests saved in the templates
    of the 10 buildings are taken out when they load, after the overgrowth (the `.nbt` files do not change, nor the
    overgrowth, the barrels or any other structure; the ramp building, the police station, the houses, the shops and
    the park fountain keep their chests). The new chests, spread by floors and rooms (12–28 per small building, 25–47
    medium, 38–65 large, 51–83 immense), come in the next stages. Stage 2 (`core/Rooms`) already finds, in each
    building, its floors, rooms, corridors and the places where a chest can stand (indoors, or on a terrace or a roof
    against a wall or a railing; on a full block, beside where a player stands, away from doors, stairs, barrels and
    corridors), the ones far from where each floor is reached and the ones of a lobby open onto the street; stage 3 places the chests on them. Floor plans of each building: `build/test-renders/rooms/` (after `./gradlew test`). Stage 3
    (`core/ChestDraw`) places them: each copy of a building draws 12–28 chests (small), 25–47 (medium), 38–65 (large)
    or 51–83 (immense), spread over the floors in proportion to the room on each, 4+ blocks apart, 1 per room or piece
    of a hall while there is room (the skeleton building, with few rooms with spots, gets up to 3 in rooms of 94 to 170
    cells), at least 15% in exploration places (far on their floor, under the open sky, underground), never blocking a
    passage; loot of the `building` theme, the more generous `chest_hard` on the spots underground (the garage of the
    wide building) or on a terrace (crash, skeleton, green tower).

  The containers (chests, barrels, dispensers, droppers, decorated pots) get their loot table when they are placed;
  the banners get the design of the template; the other block entities (furnaces, beds, skulls, chiseled bookshelves,
  etc.) come out empty. Loot with vanilla items only, in 36 tables of its own (`tools/gen_loot.py`): 6 themes × 6
  tiers.

  | Theme | Structures | Typical items | Rare |
  |---|---|---|---|
  | `house` | the 12 houses | food, string, paper, torches, books, nuggets, emeralds | diamond, golden apple, enchanted book, music discs |
  | `pharmacy` | the pharmacies | bottles, sugar, potions of healing, regeneration, night vision, fire resistance | healing II, long regeneration, bottles o' enchanting |
  | `workshop` | the workshops | iron, copper, coal, chains, worn iron tools, rails, pistons | diamond, iron block, enchanted tools, hopper, anvil |
  | `gas_station` | the gas stations | snacks, coal, lava bucket, map, compass, emeralds | emeralds, gold, golden apple, spyglass |
  | `building` | the buildings and the ramp building (whatever their number of containers) | paper, books, arrows, pencils, redstone, worn bow and sword | diamond, enchanted iron armor, ender pearls, music discs |
  | `police_station` | the police station (63 containers) | the same as `building` (its own theme so that an add-on such as AMC TACZ Loot can give it more weapons) | the same |

  | Container | Normal | Hard to get to (`_hard`) |
  |---|---|---|
  | Chest (`chest`) | always: 3 to 6 stacks (common and normal) and a 10% chance of 1 rare | 5 to 8 stacks and 1 to 2 rare |
  | Barrel, dispenser, dropper (`barrel`) | 40% have anything: 1 to 3 stacks of common items | 75%: 2 to 4 stacks of common and normal items |
  | Decorated pot (`pot`) | 40%: 1 common stack | 80%: 1 normal or rare stack |

  Hard to get to (`core/Access`): out of reach of someone walking in from the street (without breaking blocks: jumping
  1 block, falling, climbing ladders, vines and water, opening doors), listed by hand in the `hard` column of
  `index.txt` (the chest in the tree of the abandoned modern house), or by where it stands: underground (at or under
  the street floor), on the terrace (open sky, 4+ blocks over the ground and at most 5 under the top of the structure)
  or, only in the houses, in the attic (only the roof between it and the sky, 4+ blocks over the ground). The height
  alone does not count. That is 125 of the 2,559 containers: the attics of the houses (87), the police station (18:
  its basement), the apocalyptic building (7 barrels in closed rooms), the abandoned mansion (3, a closed room), the
  roof of each large pharmacy (1), a barrel set in the floor of each small workshop (1) and the chest in the tree.
  The chests drawn in the buildings follow the same rule by where they stand: hard in the garage of the wide building
  and on the terraces of the crash, skeleton and green tower buildings.
- **Nature taking the city back** (`core/Flora`, `core/Wild`):
  - the ground in patches: grass, moss, coarse dirt and remains of the concrete (`gray_concrete_powder`);
  - the mod's own trees, ~1 per 55 columns: oak, birch, dark oak and spruce, small and large, with vines hanging from
    the crowns; they are real trees: the leaves keep their distance to the trunk and decay once the trunk is cut;
  - tall grass, ferns, moss carpets, bushes (leaves on a buried trunk) and rubble (mossy cobblestone, cobblestone,
    cracked bricks, concrete and mossy slabs).
- **Parks:**
  - the same nature (trees ~1 per 60 columns), some flowers, and paths of `dirt_path` (a grid or a pinwheel) with
    30% of gaps, scaled to the size of the park;
  - the `waterfall.nbt` fountain in the middle, intact, with strands of vines of 2 to 6 blocks and an empty chest in
    50% of them; the ring is all mossy slabs (`mossy_stone_brick_slab` and `mossy_cobblestone_slab`), with 10% of
    gaps; the stone floor around the basin is mossy (`mossy_cobblestone` and `mossy_stone_bricks`).

### Streets and highways

| Road | Width | Cross-section |
|---|---|---|
| City street | 33 | `smooth_stone` 3 · `polished_deepslate` 27 (dashed `white_concrete` line on the 17th block) · `smooth_stone` 3 |
| Highway | 33 | `smooth_stone` 3 (the outer one with `stone_brick_wall`) · `polished_deepslate` 27 (`yellow_terracotta` line) · `smooth_stone` 3 (the outer one with a wall) |

- **Park benches:** 2 spruce stairs with open trapdoors as armrests, on the edge of the paths, facing them, ~1 per 19
  blocks of path (the target was 15; the spirals lack straight stretches); 20% broken.
- **Center line:** dashes of 11 with gaps of 6. Each crossing has a 3 × 3 square of `smooth_stone` at each corner.
- **Abandoned streets:** ~30% of the columns of streets, sidewalks, highways and bridges are taken over, in patches
  and cracks: grass with weeds, moss, coarse dirt, gravel or cracked asphalt, always at the same level (no steps) and
  only with plants without collision on top. The rest of the asphalt (`polished_deepslate`) and of the sidewalks has
  a worn texture, with patches of other stones, and 20% of the painted lines are faded. The tunnels stay clean; vines
  hang from the bridges down to the ground.
- **Old bridges:** deck and pillars of mossy and cracked stone, a wall broken in stretches and mossy, and irregular
  holes (5 to 7 × 4 to 5) through the deck, ~1 per 80 blocks of bridge.
- **Villages under the cities:** the villagers, zombie villagers, iron golems and cats of a village generated where
  the city now stands are removed (in the city and up to 32 blocks from it); ordinary animals stay.
- **Street lights:** every 24 blocks on both sides of every city street, facing each other (7 a side on the segments
  of 180, 5 on those of 120), shaped like the example structure `poste_exemplo` (a post of 13 on the sidewalk, an arm
  of 4 at a height of 12 over the lanes, an unlit lamp at its end), abandoned: 10% are gone and the rest are one of 5
  variants (mossy, dark, broken with half an arm, lamp fallen on the lane with a chain left hanging, overgrown with
  vines and leaves), with vines climbing the back of the post and hanging from the arm.
- **Crosswalks:** a `white_concrete` zebra (stripes of 1 with gaps of 1, 3 rows) 2 rows before each crossing, at both
  ends of every street segment.
- **Highway network:** a Gabriel graph between the city centers. It is planar and connected, and the highways never
  cross.
  - Each city has 4 entrances, one per side, drawn among the middle crossing and the two corners of that side. Each
    link is agreed by both cities and leaves on the side facing the other one. From a corner the highway leaves
    straight, continuing one of the border streets. Up to 4 highways per city.
  - The highway is as wide as the street and meets it without narrowing; the wall ends at the junction.
- **"Flat or ramp" profile** (`core/Profile`): flat stretches at whole heights and ramps of 1 block every 8, with a
  half slab every 4.
- **Tunnels:** only where there are 11+ blocks of terrain over the road. They have 7 blocks of headroom, a `stone`
  lining and `glowstone` in the ceiling every 12.
- **Bridges:** over water or 7+ blocks above the ground, with a 2-block deck, parapets and pillars every 24 down to
  the bottom.

## Guaranteed rules

| Rule | How it is guaranteed |
|---|---|
| Nothing unbreakable | Every block is in `gen/Palette`. `Palette.validate()` runs at startup and stops the game if one is unbreakable or has a block entity (except the fountain chest and the block entities of the structures). Any hardness is allowed. The in-world audit (`hard_block`) checks the generated blocks. |
| Loot only where meant | The containers of the structures **have their own vanilla loot tables** per theme (house, pharmacy, workshop, gas station, building) and per container (chest, barrel, pot), more generous when hard to get to. The park fountain chest stays empty (50%) or is `mossy_cobblestone` (50%). The audit checks: a structure container without a loot table is `structure_without_loot`, with the wrong table `structure_wrong_loot`; outside the structures, `loot_in_city` and `container_in_city`. |
| Flat, connected streets | Each city has a single height (`City.level`). The street grid has no dead end and stays connected (`PlanAudit`: `dead_end`, `city_split`). |
| Right surface | The in-world audit (`surface_block`) checks the road surface block by block. |
| Nothing floating | Natural vegetation that the city's cuts leave in the air (a crown whose trunk was cut, snow and vines on removed leaves) is removed after every chunk (`gen/Floating`), at any height; the in-world audit (`vegetation_floating`) fails on any group of leaves, logs, vines, snow or plants above the ground that touches nothing holding it, within 7 columns of what the mod builds or of a slope whose ground it moved (Minecraft leaves such groups by itself too, farther away: those are only counted). |
| Determinism | The plan (cities, streets, highways, lots, structures, chests, the decay of the roads) is a pure function of the seed and the natural terrain: two neighbouring chunks never disagree about it, in any order and thread (`PlanAuditTest.planIsIndependentOfQueryOrderAndThreads`), and the blocks the mod decides come out the same (the battery's `mod blocks:` digest). Minecraft's own decoration next to the city (trees, vines, plants, ore blobs, lakes of a chunk decorated before or after its neighbour) can differ with the order in which the chunks are generated, as in Minecraft itself (findings 65, 76, 79 in `docs/TESTING.md`). |
| Same world through a code review | `FingerprintTest` keeps a SHA-256 per part of what the planner produces (cities, highways, chunk plans, lot blocks, structures, overgrowth, benches), and the battery prints per target `mod blocks:`, the SHA-256 of the blocks the mod decides in the generated world (`command/WorldFingerprint`). A change of form only keeps both the same (details in `docs/TESTING.md`). |
| No Immersive Vehicles | No reference to IV in the code or the build. |

## Architecture

```
core/   the pure planner (no Minecraft classes), tested with JUnit
  Params          all the fixed geometry (widths, blocks, ramps, tunnels, bridges) + the config values
  Terrain         interface: natural ground height and water per column
  City            stage 1 (is there a city? center, height) and stage 2 (streets, crossings, highway exits, the
                  lots of each block, cached)
  CityRings       what stands in the blocks of a city: the rings of structures drawn for the whole city, the 2 gas
                  stations, the special structure (~1 city in 10), the parks in a corner, the nature of the rest
  StreetLights    the street lights: where they stand, the 5 abandoned variants and the block at each height
  Lot             lots: a park (in a corner of the block), a structure or the core taken over by nature (WILD)
  Flora           trees (4 species, vines), patchy ground, plants and rubble; a bucketed index of trees
  RoadDecay       abandoned roads: taken-over columns, worn texture, plants; stones and walls of the old bridges
  Wreck           holes in the bridges, by position along the highway
  Wild            the core of a block: ground, trees and plants, kept out of the structures
  Ring            the ring of structures around a block (short sides whole, long sides in what is left), gaps, limits
  Structures      the 52 files (30 models) of `amcs/buildings/index.txt`: loading in order, box, ground layer,
                  rotation, per-city limits, digging with `cave_air`, the chests of the buildings taken out
  StructureLoader the preparation of each template: floor out to the edge, real leaves, cut crowns completed, loose
                  cobwebs, openings of the garage
  Preload         reads and weathers the 52 templates in parallel at startup, then works out the walks of Access and
                  Rooms in parallel (~4 s in all; the block state ids are given out after, in the order of the
                  index, which they depend on)
  Access          the containers of the structures that are hard to get to (out of walking reach, listed by hand
                  in index.txt, underground, on the terrace, in the attic of a house): more generous loot
  Barrels         the barrels of the buildings: 70% taken out, the others chests, drawn per copy (lot seed)
  Rooms           floors, rooms, corridors and places for a chest of each building, on the walk of Access
  ChestDraw       the chests of each copy of a building: how many, per floor, which spots (pure function of the seed)
  Crowns          where the leaves completing the crowns cut by the edge of the structures go (gen/Overhangs places
                  them)
  Park            parks taken over by nature, with paths and the fountain
  Template        .nbt structures (and the waterfall.nbt fountain)
  Highway         the curve between two city entrances, points every block, column projection
  Profile         the "flat or ramp" height profile and the ground / bridge / tunnel classification
  WorldPlan       cities, highway network, the plan of each chunk (ChunkPlan); caches; the cities of an area and
                  the samples of a highway's path computed on several cores
  PlanAudit       the plan audit (used in the tests and in game)
  PlanRender      PNGs of the plan (detail, network, side profile)
gen/    the game layer
  CityFeature     the feature per chunk (top_layer_modification step, through a biome modifier)
  ChunkBuilder    writes the chunk plan in blocks; seals liquids; the bridge vines
  NeighbourRepair after every chunk, repairs the neighbours already built (trees, lakes and ores of chunks decorated
                  later)
  Floating        natural vegetation left hanging in the air (pieces of trees, vines, snow): found and removed after
                  every chunk, and checked by the audit (`vegetation_floating`)
  Palette         every block used (the structures included, turned by `BlockState.rotate`) + validation
  McTerrain       Terrain over ChunkGenerator.getBaseHeight (noise, the same in any thread)
  Plans           one WorldPlan per dimension (only the overworld)
command/  /amcs (StructureCommands: locate structure and spawn), the block audit (WorldAudit), renders, the battery
          (SelfTest), the fingerprint of the mod's blocks (WorldFingerprint)
```

### Stages

1. **Is there a city?**
   - The world is divided into cells of `cellSize` (736). Each cell has a city with the chance `cityChance` (70%), in
     the middle of the cell.
   - The city only exists if the terrain there is not mostly water and the height (median of 9 samples, at least sea
     level + 2) is not above `maxCityLevel`.
2. **Plan.**
   - Streets, blocks and lots come out of the city grid.
   - Highway links: a Gabriel graph. Each city reserves an entrance per neighbour, facing it.
   - The highway is validated: length up to `highwayMaxLength`, curve radius ≥ 60, distance from other cities,
     possible ramps and water. It is dropped if it comes too close to a shorter highway.
3. **Build.**
   - `WorldPlan.chunk(cx, cz)` says what each column must be: street, highway, bridge, tunnel, tunnel wall, lot or
     slope. The `ChunkBuilder` only carries it out.
   - Liquids touching open air become stone.
   - The 3 layers under a lot's floor never keep air, a liquid or a block entity where the plan keeps the world (what
     stood there before, a cave or a village house, becomes dirt); the cells a structure plans there never change.
   - Trees and plants that a neighbouring chunk decorated later puts on the road are removed again
     (`NeighbourRepair.repairNeighbours`).
   - Natural vegetation that what was cut or removed left in the air (a crown whose trunk the city cut, snow and
     vines that rested on removed leaves) is removed (`gen/Floating`): a group of leaves, logs, vines, snow or plants
     that touches nothing else, starting from the blocks next to what this chunk opened or removed.

## Configuration (`config/abandonedmoderncity-common.toml`)

`enabled`, `cellSize`, `cityChance`, `maxCityLevel`, `highways`, `highwayMaxLength`, `structureOvergrowth` (default
`true`: nature taking over the structures; `false` generates them as they were saved; read at startup, restart the game
after changing it).
The geometry (widths, blocks, ramps, tunnels, bridges) is in `core/Params` and the cities in `core/City`. Changes
apply to new chunks and change the plan: use a new world.
`cellSize` (default 736) is the distance between neighbouring cities: the highways get longer with it. It must be at
least 734; NeoForge raises a smaller value to 734 and rewrites the file (the log says "Configuration file ... is not
correct. Correcting"), the other values stay. The keys `minRadius` and `maxRadius` of old config files (they never
changed the size of the cities) are removed from the file the same way.

## Commands (op 2)

- `/amcs info` — what the plan wants at the column you stand on (kind, height, structure, city, highway, lot).
- `/amcs locate city|highway|tunnel|bridge` — the nearest one, with clickable coordinates (they suggest a `/tp`).
- `/amcs locate structure <category> [name]` — the nearest structure already planned in the cities, with the
  categories of `spawn`: the category alone finds the nearest of any structure of it (`/amcs locate structure
  pharmacy`: any pharmacy, of any size and color), with a name that structure (`/amcs locate structure building_large
  tower`); the game suggests the names of the chosen category, and a name of another category is refused with the
  right list. The clickable coordinates are on the sidewalk, 2 blocks in front of the middle of
  the facade.
- `/amcs spawn <category> <name>` — categories `house`, `pharmacy`, `workshop`, `gas_station`, `building_small`,
  `building_medium`, `building_large`, `building_immense` (the buildings by height: see below),
  `police_station` and `special` (the game suggests the names of the chosen category; e.g.
  `/amcs spawn building_large abandoned_wide_building`); places the structure 2 blocks in front of you, centered on the
  direction you look in, its front facing you and its ground at the level of the block you stand on. The same rules
  as in the city: buried layers, floor out to the edge, real leaves and loot tables in the containers. The air of the
  template clears the terrain inside the box (above the ground); below the ground only the template's `cave_air`
  digs the terrain (basements, such as underground garages).
- `/amcs plan [radius]` — audit of the plan around (generates nothing) + PNG; radius in chunks, 8 by default, at most
  16.
- `/amcs audit [radius]` — generates the chunks around, audits the plan and the blocks, PNG of the world; radius in
  chunks, 6 by default, at most 8.
- `/amcs network [radius]` — PNG of the network of cities and highways; radius in blocks, 500 to 1000 (default).
- `/amcs column <x> <z> <y0> <y1>` — the real blocks of a column next to what the plan wants there (diagnosis); the
  heights are kept inside the world (at most 384 lines).

`plan`, `audit` and `network` make the server wait while they run (seconds: network 1000 ~23 s, plan 16 ~19 s, audit 8 ~15 s,
measured on a development server in a region not planned yet); the limits keep them under the 60 s after which a server's watchdog shuts it down.

The files go to `amcs_renders/` in the server folder.
