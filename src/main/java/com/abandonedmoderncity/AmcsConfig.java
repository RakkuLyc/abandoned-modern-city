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
    public static final ModConfigSpec.IntValue MIN_RADIUS;
    public static final ModConfigSpec.IntValue MAX_RADIUS;
    public static final ModConfigSpec.IntValue MAX_CITY_LEVEL;
    public static final ModConfigSpec.BooleanValue HIGHWAYS;
    public static final ModConfigSpec.IntValue HIGHWAY_MAX_LENGTH;
    public static final ModConfigSpec.BooleanValue STRUCTURE_OVERGROWTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Params d = Params.DEFAULT;
        ENABLED = b.comment("Generate cities in the overworld").define("enabled", true);
        CELL_SIZE = b.comment("Side of the world cell that holds at most one city (blocks). Must be at least 2 x (city influence) + 64.")
                .defineInRange("cellSize", d.cellSize(), 512, 8192);
        CITY_CHANCE = b.comment("Probability that a cell has a city (cells on water or very rugged terrain never do)")
                .defineInRange("cityChance", d.cityChance(), 0.0, 1.0);
        MIN_RADIUS = b.comment("Smallest city radius (blocks)").defineInRange("minRadius", d.minRadius(), 64, 1024);
        MAX_RADIUS = b.comment("Largest city radius (blocks)").defineInRange("maxRadius", d.maxRadius(), 64, 1024);
        MAX_CITY_LEVEL = b.comment("Highest ground level a city may sit at").defineInRange("maxCityLevel", d.maxCityLevel(), 70, 250);
        HIGHWAYS = b.comment("Link neighboring cities with highways").define("highways", d.highways());
        HIGHWAY_MAX_LENGTH = b.comment("Longest highway (blocks of path)").defineInRange("highwayMaxLength", d.highwayMaxLength(), 256, 8192);
        STRUCTURE_OVERGROWTH = b.comment("Nature taking over the building structures: cracked stone, moss and plants on the floors, vines on the facades (modeled on predio_apocaliptico). Read at startup: restart the game after changing it.")
                .define("structureOvergrowth", true);
        SPEC = b.build();
    }

    private AmcsConfig() {
    }

    /** Current values; falls back to the defaults if the values are inconsistent. */
    public static Params params() {
        try {
            return new Params(CELL_SIZE.get(), CITY_CHANCE.get(), MIN_RADIUS.get(), MAX_RADIUS.get(), MAX_CITY_LEVEL.get(),
                    HIGHWAY_MAX_LENGTH.get(), HIGHWAYS.get());
        } catch (IllegalArgumentException e) {
            AbandonedModernCity.LOGGER.error("[AMCS] Invalid config ({}), using the defaults", e.getMessage());
            return Params.DEFAULT;
        }
    }
}
