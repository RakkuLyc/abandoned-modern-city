# Contributing structures to Abandoned Modern City

Abandoned Modern City is open to structures built by other players. This page says what a structure must be, how
to send it and what happens to it. The process is done by hand for now; automatic checks are planned for a later
version.

## 1. Legal rules

- **Your own work.** Send only structures you built yourself. Nothing copied from maps, servers, schematics or
  other mods, and no real brands, logos or trademarks on the facades.
- **License.** By sending a structure you agree that it is distributed with the mod under the GNU General Public
  License version 3 with the mod's additional terms (see [LICENSE](LICENSE)), and that it may be changed (rotated,
  weathered by the mod's overgrowth, its chests drawn anew, its colors varied).
- **Sign-off.** Add this line to your pull request or issue, with the name you want to be credited under:
  `Signed-off-by: Your Name or handle` — it certifies that the structure is your own and that you can license it
  as above (the [Developer Certificate of Origin](https://developercertificate.org/)).
- **Credit.** Every accepted structure is listed in [CREDITS.md](CREDITS.md) with its author.

## 2. Technical rules

The mod's loader and its tests check most of these; a structure that breaks them does not load.

- **Format.** A `.nbt` saved with a structure block in Minecraft **1.21.1**, with a single palette. Entities saved in
  it (mobs, item frames, armor stands, paintings) are not placed.
- **Blocks.** Vanilla blocks only. Nothing unbreakable (bedrock, barriers, command, structure and jigsaw blocks, end
  portal frames...). Blocks with data (block entities) only from this list: chests, barrels, brewing stands,
  decorated pots, lecterns, furnaces, beds, campfires, daylight detectors, jukeboxes, banners, skulls, dispensers,
  droppers, chiseled bookshelves (no spawners, no signs with text: signs come out blank).
- **Containers.** Leave them empty: the mod gives them its own loot tables by kind of structure, more generous where
  they are hard to reach. In a building (type `buildings_*`) the saved chests are taken out and new ones are drawn
  in every copy, and 70% of the barrels are taken out.
- **Front.** The front (the side facing the street, with the entrance) is the **−Z side** (north) of the `.nbt`, or
  say which side it is (the `front` field turns it).
- **Ground.** Say which layer, counted from the lowest one, lies at the street's level (`ground`); the layers below
  are buried. In buried layers plain `air` becomes dirt, `cave_air` digs the terrain (a basement, a garage).
- **Size.** It must fit a row of a city block (free ground of 180 × 120): the largest structure today is 102 × 72
  (front × depth) and only fits some rows; most are under 60 deep. The tests report where a structure fits.
- **Height.** Any. A building is classified by its height (small up to 43, medium up to 78, large up to 143, immense
  above) and each building stands at most once per city.
- **Name.** An English name in lowercase ASCII letters, digits and underscores (e.g. `abandoned_corner_shop`): the
  game's `/amcs spawn` command only takes such names.
- **Trees.** Trees made of natural logs and leaves become real trees (their leaves decay if the trunk is cut);
  hedges and bushes without a trunk stay.

## 3. Style

- Abandoned modern buildings: houses, shops, offices, public buildings, towers. Broken windows, missing walls,
  rubble, overgrown corners and furniture left behind are welcome.
- The mod adds its own overgrowth (cracked stone, moss and plants on the floors, vines on the facades), so a clean
  structure is fine; one that is already overgrown keeps what it has.
- Interiors matter: the chests of the buildings are drawn on floors with rooms a player can walk into.

## 4. How to send a structure

Open an issue or a pull request in the repository with:

1. the `.nbt` file;
2. its fields: `type` (`houses`, `pharmacy`, `workshop_*`, `gas_station*`, `buildings_<class>`...), `ground`, `front`;
3. two or three screenshots (the front and the inside);
4. the name you want in the credits and the `Signed-off-by` line.

## 5. What happens next

The author reviews the structure in the game (`/amcs spawn`), copies it into the mod with its line in
`src/main/resources/amcs/buildings/index.txt`, runs the tests and the in-world battery, and adds it to the credits.
A new structure changes the cities of new worlds (it joins the draw), so structures are added in new versions of
the mod, listed in its changelog (`CHANGELOG.md`). The author may ask for changes or decline a structure.
