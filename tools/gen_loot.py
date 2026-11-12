#!/usr/bin/env python3
"""Writes the loot tables of the structures' containers (asked by the user, 13 nov 2026): vanilla items only, one set
per kind of structure (casa, farmacia, oficina, posto, predio) and six tables per kind:

  barril          barrels, dispensers, droppers: 40% have anything, 1 to 3 stacks of common items
  barril_dificil  the same, hard to get to (Access): 75%, 2 to 4 stacks of common and normal items
  bau             chests: 3 to 6 stacks of common and normal items, and a 10% chance of one rare item
  bau_dificil     chests hard to get to: 5 to 8 stacks of common and normal items and 1 to 2 rare items
  vaso            decorated pots (one slot): 40% have one stack of a common item
  vaso_dificil    pots hard to get to: 80% have one stack of a normal or rare item

Run from the repository root: python3 tools/gen_loot.py
"""
import json
import os

OUT = "src/main/resources/data/abandonedmoderncity/loot_table/chests"


def it(name, weight, lo=1, hi=1, *fns):
    e = {"type": "minecraft:item", "name": "minecraft:" + name, "weight": weight}
    f = list(fns)
    if hi > 1:
        f.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if f:
        e["functions"] = f
    return e


def potion(pid):
    return {"function": "minecraft:set_potion", "id": "minecraft:" + pid}


WORN = {"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.25, "max": 0.8}}
BOOK = {"function": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}


def ench(lo, hi):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi},
            "options": "#minecraft:on_random_loot"}


THEMES = {
    "casa": {
        "common": [it("bread", 10, 1, 3), it("potato", 8, 1, 4), it("carrot", 6, 1, 4), it("apple", 8, 1, 3),
                   it("rotten_flesh", 8, 1, 4), it("string", 8, 1, 4), it("stick", 8, 1, 6), it("paper", 8, 1, 5),
                   it("bowl", 5, 1, 2), it("glass_bottle", 5, 1, 3), it("candle", 4, 1, 2), it("white_wool", 4, 1, 3),
                   it("leather", 4, 1, 2), it("bone", 5, 1, 3), it("cookie", 5, 1, 4), it("torch", 5, 1, 6)],
        "normal": [it("book", 6, 1, 3), it("cooked_beef", 5, 1, 3), it("cooked_chicken", 5, 1, 3),
                   it("baked_potato", 5, 1, 3), it("iron_nugget", 6, 2, 8), it("gold_nugget", 4, 1, 6),
                   it("shears", 3, 1, 1, WORN), it("flint_and_steel", 3, 1, 1, WORN), it("clock", 2), it("compass", 2),
                   it("lead", 3), it("name_tag", 2), it("emerald", 3, 1, 3), it("iron_ingot", 4, 1, 3), it("bucket", 3),
                   it("writable_book", 2)],
        "rare": [it("diamond", 3, 1, 2), it("golden_apple", 4), it("book", 5, 1, 1, BOOK), it("music_disc_cat", 1),
                 it("music_disc_13", 1), it("music_disc_far", 1), it("music_disc_mall", 1), it("emerald", 4, 3, 6),
                 it("gold_ingot", 4, 2, 4), it("spyglass", 2)],
    },
    "farmacia": {
        "common": [it("glass_bottle", 10, 1, 4), it("paper", 8, 1, 4), it("sugar", 8, 1, 4), it("spider_eye", 5, 1, 2),
                   it("apple", 6, 1, 3), it("honey_bottle", 4), it("milk_bucket", 3), it("bowl", 4),
                   it("string", 4, 1, 3), it("white_wool", 4, 1, 2), it("rotten_flesh", 4, 1, 3),
                   it("potion", 5, 1, 1, potion("water"))],
        "normal": [it("potion", 6, 1, 1, potion("healing")), it("potion", 4, 1, 1, potion("regeneration")),
                   it("splash_potion", 3, 1, 1, potion("healing")), it("potion", 2, 1, 1, potion("night_vision")),
                   it("potion", 2, 1, 1, potion("fire_resistance")), it("golden_carrot", 4, 1, 3),
                   it("glistering_melon_slice", 3, 1, 2), it("redstone", 4, 1, 4), it("glowstone_dust", 4, 1, 4),
                   it("fermented_spider_eye", 3), it("nether_wart", 3, 1, 3), it("book", 3), it("honey_bottle", 3, 1, 2)],
        "rare": [it("golden_apple", 6), it("potion", 5, 1, 1, potion("strong_healing")),
                 it("potion", 4, 1, 1, potion("long_regeneration")),
                 it("splash_potion", 3, 1, 1, potion("strong_healing")), it("experience_bottle", 5, 2, 6),
                 it("blaze_powder", 3, 1, 3), it("ghast_tear", 2), it("book", 3, 1, 1, BOOK)],
    },
    "oficina": {
        "common": [it("iron_nugget", 10, 2, 9), it("stick", 6, 1, 4), it("coal", 8, 1, 6), it("copper_ingot", 7, 1, 4),
                   it("string", 4, 1, 3), it("leather", 4, 1, 3), it("chain", 5, 1, 3), it("redstone", 5, 1, 4),
                   it("iron_bars", 3, 1, 4), it("torch", 4, 1, 4), it("black_dye", 3, 1, 2), it("slime_ball", 2)],
        "normal": [it("iron_ingot", 8, 1, 4), it("bucket", 4), it("shears", 3, 1, 1, WORN),
                   it("flint_and_steel", 3, 1, 1, WORN), it("iron_pickaxe", 3, 1, 1, WORN), it("iron_axe", 3, 1, 1, WORN),
                   it("iron_shovel", 3, 1, 1, WORN), it("lava_bucket", 2), it("rail", 3, 2, 6), it("minecart", 2),
                   it("piston", 2, 1, 2), it("lever", 3), it("tripwire_hook", 2), it("gold_ingot", 2, 1, 2),
                   it("lantern", 3)],
        "rare": [it("diamond", 3, 1, 2), it("iron_block", 3), it("iron_pickaxe", 3, 1, 1, ench(10, 25)),
                 it("iron_axe", 2, 1, 1, ench(10, 25)), it("hopper", 3), it("anvil", 2), it("book", 2, 1, 1, BOOK),
                 it("emerald", 2, 2, 5), it("redstone_block", 2)],
    },
    "posto": {
        "common": [it("cookie", 8, 1, 4), it("bread", 6, 1, 2), it("dried_kelp", 6, 2, 6), it("melon_slice", 5, 1, 4),
                   it("sweet_berries", 5, 1, 4), it("paper", 8, 1, 5), it("coal", 8, 1, 5), it("charcoal", 6, 1, 4),
                   it("glass_bottle", 5, 1, 2), it("torch", 5, 1, 4), it("string", 3, 1, 2), it("rotten_flesh", 4, 1, 3)],
        "normal": [it("honey_bottle", 4), it("pumpkin_pie", 4, 1, 2), it("cooked_porkchop", 4, 1, 2), it("map", 4),
                   it("compass", 3), it("bucket", 4), it("lava_bucket", 3), it("flint_and_steel", 3, 1, 1, WORN),
                   it("lantern", 3), it("emerald", 4, 1, 3), it("gold_nugget", 4, 1, 6), it("iron_ingot", 3, 1, 2),
                   it("coal_block", 2)],
        "rare": [it("emerald", 5, 3, 7), it("gold_ingot", 4, 1, 3), it("golden_apple", 3), it("book", 3, 1, 1, BOOK),
                 it("spyglass", 2), it("diamond", 2), it("golden_carrot", 3, 2, 5)],
    },
    "predio": {
        "common": [it("paper", 10, 1, 6), it("book", 5, 1, 2), it("glass_bottle", 5, 1, 3), it("candle", 4, 1, 2),
                   it("bread", 6, 1, 2), it("string", 5, 1, 3), it("rotten_flesh", 7, 1, 4), it("bone", 6, 1, 3),
                   it("arrow", 5, 2, 8), it("iron_nugget", 5, 1, 6), it("stick", 5, 1, 4), it("torch", 4, 1, 4),
                   it("white_wool", 3, 1, 2)],
        "normal": [it("iron_ingot", 5, 1, 3), it("gold_nugget", 4, 2, 8), it("emerald", 4, 1, 3), it("redstone", 4, 1, 5),
                   it("lapis_lazuli", 4, 1, 5), it("clock", 2), it("compass", 2), it("name_tag", 2),
                   it("writable_book", 3), it("cooked_beef", 4, 1, 3), it("bucket", 2), it("bow", 2, 1, 1, WORN),
                   it("iron_sword", 2, 1, 1, WORN), it("crossbow", 1, 1, 1, WORN), it("ender_pearl", 2), it("lead", 2)],
        "rare": [it("diamond", 4, 1, 3), it("golden_apple", 4), it("book", 5, 1, 1, BOOK),
                 it("iron_chestplate", 2, 1, 1, ench(10, 25)), it("iron_helmet", 2, 1, 1, ench(10, 25)),
                 it("spyglass", 2), it("ender_pearl", 3, 2, 4), it("music_disc_stal", 1), it("music_disc_chirp", 1),
                 it("music_disc_wait", 1), it("emerald", 4, 4, 8), it("gold_ingot", 3, 2, 5),
                 it("experience_bottle", 3, 3, 8)],
    },
}


def pool(entries, lo, hi, chance=None):
    p = {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi} if hi > lo else lo, "bonus_rolls": 0.0,
         "entries": entries}
    if chance is not None:
        p["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return p


def tables(t):
    c, n, r = t["common"], t["normal"], t["rare"]
    return {
        "barril": [pool(c, 1, 3, 0.40)],
        "barril_dificil": [pool(c + n, 2, 4, 0.75)],
        "bau": [pool(c + n, 3, 6), pool(r, 1, 1, 0.10)],
        "bau_dificil": [pool(c + n, 5, 8), pool(r, 1, 2)],
        "vaso": [pool(c, 1, 1, 0.40)],
        "vaso_dificil": [pool(n + r, 1, 1, 0.80)],
    }


def main():
    count = 0
    for theme, t in THEMES.items():
        os.makedirs(f"{OUT}/{theme}", exist_ok=True)
        for tier, pools in tables(t).items():
            table = {"type": "minecraft:chest", "pools": pools,
                     "random_sequence": f"abandonedmoderncity:chests/{theme}/{tier}"}
            with open(f"{OUT}/{theme}/{tier}.json", "w", encoding="utf-8") as f:
                json.dump(table, f, indent=2)
                f.write("\n")
            count += 1
    print(f"{count} loot tables in {OUT}")


if __name__ == "__main__":
    main()
