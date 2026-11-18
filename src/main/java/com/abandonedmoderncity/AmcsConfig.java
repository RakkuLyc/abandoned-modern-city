package com.abandonedmoderncity;

import com.abandonedmoderncity.core.Params;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config ({@code config/abandonedmoderncity-common.toml}). Only the big knobs; the geometry lives in
 * {@link Params}. Changes apply to chunks generated afterwards (and change the plan: use a new world).
 */
public final class AmcsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue CELL_SIZE;
    public static final ModConfigSpec.DoubleValue CITY_CHANCE;
    public static final ModConfigSpec.IntValue MAX_CITY_LEVEL;
    public static final ModConfigSpec.BooleanValue HIGHWAYS;
    public static final ModConfigSpec.IntValue HIGHWAY_MAX_LENGTH;
    public static final ModConfigSpec.BooleanValue STRUCTURE_OVERGROWTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Params d = Params.DEFAULT;
        ENABLED = b.comment("Generate cities in the overworld").define("enabled", true);
        CELL_SIZE = b.comment("Side of the world cell that holds at most one city (blocks): neighbouring cities stand this far apart, and the highways between them get longer with it. At least " + Params.MIN_CELL_SIZE + " (a smaller value is raised to it).")
                .defineInRange("cellSize", d.cellSize(), Params.MIN_CELL_SIZE, 8192);
        CITY_CHANCE = b.comment("Probability that a cell has a city (cells on water or very rugged terrain never do)")
                .defineInRange("cityChance", d.cityChance(), 0.0, 1.0);
        MAX_CITY_LEVEL = b.comment("Highest ground level a city may sit at").defineInRange("maxCityLevel", d.maxCityLevel(), 70, 250);
        HIGHWAYS = b.comment("Link neighboring cities with highways").define("highways", d.highways());
        HIGHWAY_MAX_LENGTH = b.comment("Longest highway (blocks of path)").defineInRange("highwayMaxLength", d.highwayMaxLength(), 256, 8192);
        STRUCTURE_OVERGROWTH = b.comment("Nature taking over the building structures: cracked stone, moss and plants on the floors, vines on the facades (modeled on the apocalyptic building). Read at startup: restart the game after changing it.")
                .define("structureOvergrowth", true);
        SPEC = b.build();
    }

    private AmcsConfig() {
    }

    /**
     * Current values. NeoForge keeps each one inside its range when the file is read (a value out of range is moved to
     * the nearest limit and the file rewritten), so they always make valid {@link Params}.
     */
    public static Params params() {
        return new Params(CELL_SIZE.get(), CITY_CHANCE.get(), MAX_CITY_LEVEL.get(), HIGHWAY_MAX_LENGTH.get(),
                HIGHWAYS.get());
    }
}
