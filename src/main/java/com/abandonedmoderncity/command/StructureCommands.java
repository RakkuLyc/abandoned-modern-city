package com.abandonedmoderncity.command;

import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.City;
import com.abandonedmoderncity.core.Hash;
import com.abandonedmoderncity.core.Lot;
import com.abandonedmoderncity.core.Structures;
import com.abandonedmoderncity.core.WorldPlan;
import com.abandonedmoderncity.gen.Palette;
import com.abandonedmoderncity.gen.Plans;
import com.abandonedmoderncity.gen.Spawners;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Commands for the building structures ({@link Structures}):
 * <ul>
 *   <li>{@code /amcs locate structure <category> [name]} (the categories of spawn): the nearest one the plan put in a
 *   city, of the category (any structure of it) or with that name; the clickable coordinates are on the sidewalk in
 *   front of it.</li>
 *   <li>{@code /amcs spawn <category> <name>} (house, pharmacy, workshop, gas_station, building_small, building_medium, building_large, building_immense, police_station, special): places
 *   the structure 2 blocks in front of you, centered on where you look, its
 *   front turned to you, its ground layer level with the block you stand on. Same rules as in the cities: buried
 *   layers, floor filled to the edge, real leaves, loot tables.</li>
 * </ul>
 */
public final class StructureCommands {
    private StructureCommands() {
    }

    /** A structure of the category, and with that name when {@code name} is not null. */
    static boolean matches(Structures.Def d, String category, String name) {
        return d.category().equals(category) && (name == null || d.name().equals(name));
    }

    /**
     * {@code structure <category> [name]} under {@code /amcs locate}: the nearest structure of the category (e.g.
     * {@code /amcs locate structure pharmacy}, any pharmacy of any color) or the nearest with that name (e.g.
     * {@code /amcs locate structure building_large abandoned_wide_building}); the names suggested are those of the category.
     */
    static ArgumentBuilder<CommandSourceStack, ?> locateNode() {
        return Commands.literal("structure").then(Commands.argument("category", StringArgumentType.word())
                .suggests((c, b) -> {
                    suggestCategories(b);
                    return b.buildFuture();
                })
                .executes(c -> locate(c, StringArgumentType.getString(c, "category"), null))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            namesOf(StringArgumentType.getString(c, "category")).forEach(b::suggest);
                            return b.buildFuture();
                        })
                        .executes(c -> locate(c, StringArgumentType.getString(c, "category"),
                                StringArgumentType.getString(c, "name")))));
    }

    /** The categories that have structures. */
    static void suggestCategories(SuggestionsBuilder b) {
        for (String k : Structures.CATEGORIES) {
            if (!namesOf(k).isEmpty()) {
                b.suggest(k);
            }
        }
    }

    /** The names of the structures of a category ({@link Structures.Def#category}). */
    static TreeSet<String> namesOf(String category) {
        TreeSet<String> out = new TreeSet<>();
        for (Structures.Def d : Structures.ALL) {
            if (d.category().equals(category)) {
                out.add(d.name());
            }
        }
        return out;
    }

    /**
     * {@code spawn <category> <name>} under {@code /amcs}: the structures by category (e.g.
     * {@code /amcs spawn building_large abandoned_wide_building}); the names suggested are those of the category.
     */
    static ArgumentBuilder<CommandSourceStack, ?> spawnNode() {
        return Commands.literal("spawn").then(Commands.argument("category", StringArgumentType.word())
                .suggests((c, b) -> {
                    suggestCategories(b);
                    return b.buildFuture();
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            namesOf(StringArgumentType.getString(c, "category")).forEach(b::suggest);
                            return b.buildFuture();
                        })
                        .executes(c -> spawn(c, StringArgumentType.getString(c, "category"),
                                StringArgumentType.getString(c, "name")))));
    }

    /** The category is unknown or the name is not one of it: says so with the accepted list and returns false. */
    private static boolean checkCategory(CommandContext<CommandSourceStack> c, String category, String name) {
        TreeSet<String> inCategory = namesOf(category);
        if (inCategory.isEmpty() && Arrays.asList(Structures.CATEGORIES).contains(category)) {
            c.getSource().sendFailure(Component.literal("[AMCS] no structure in category '" + category + "' yet"));
            return false;
        }
        if (inCategory.isEmpty()) {
            c.getSource().sendFailure(Component.literal("[AMCS] unknown category '" + category + "' ("
                    + String.join(", ", Structures.CATEGORIES) + ")"));
            return false;
        }
        if (name != null && !inCategory.contains(name)) {
            c.getSource().sendFailure(Component.literal("[AMCS] no " + category + " named '" + name + "' ("
                    + String.join(", ", inCategory) + ")"));
            return false;
        }
        return true;
    }

    private static int locate(CommandContext<CommandSourceStack> c, String category, String name) {
        if (!checkCategory(c, category, name)) {
            return 0;
        }
        String q = name != null ? name : category;
        WorldPlan plan = Plans.get(c.getSource().getLevel());
        if (plan == null) {
            c.getSource().sendFailure(Component.literal("No cities in this dimension (or disabled in the config)"));
            return 0;
        }
        double px = c.getSource().getPosition().x;
        double pz = c.getSource().getPosition().z;
        double best = Double.MAX_VALUE;
        Lot found = null;
        City foundCity = null;
        int[] radii = {2, 4, 8, 16};
        for (int cells : radii) {
            double r = cells * plan.params.cellSize();
            for (City city : plan.citiesIn(px - r, pz - r, px + r, pz + r)) {
                int n = city.gridHalf();
                for (int bj = -n; bj <= n; bj++) {
                    for (int bi = -n; bi <= n; bi++) {
                        for (Lot lot : city.lots(bi, bj)) {
                            if (lot.building == null || !matches(lot.building, category, name)) {
                                continue;
                            }
                            double d = Math.hypot((lot.x0 + lot.x1) / 2.0 - px, (lot.z0 + lot.z1) / 2.0 - pz);
                            if (d < best) {
                                best = d;
                                found = lot;
                                foundCity = city;
                            }
                        }
                    }
                }
            }
            if (found != null && best <= r) {
                break;
            }
        }
        if (found == null) {
            c.getSource().sendSuccess(() -> Component.literal("[AMCS] no " + q + " found within "
                    + radii[radii.length - 1] * plan.params.cellSize() + " blocks"), false);
            return 0;
        }
        // on the sidewalk, 2 blocks in front of the middle of its front
        int cx = (found.x0 + found.x1) / 2;
        int cz = (found.z0 + found.z1) / 2;
        int[] at = switch (found.rot) {
            case Structures.NORTH -> new int[]{cx, found.z0 - 2};
            case Structures.EAST -> new int[]{found.x1 + 2, cz};
            case Structures.SOUTH -> new int[]{cx, found.z1 + 2};
            default -> new int[]{found.x0 - 2, cz};
        };
        String coords = at[0] + " " + foundCity.level + " " + at[1];
        Component link = AmcsCommands.tpLink(coords);
        String what = found.building.name() + " (" + found.building.type() + ")";
        final double dist = best;
        Component msg = Component.literal("[AMCS] " + what + " at ").append(link)
                .append(Component.literal(" (" + Math.round(dist) + " blocks; box " + found.x0 + "," + found.z0 + " .. "
                        + found.x1 + "," + found.z1 + ")"));
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String category, String name) {
        if (!checkCategory(c, category, name)) {
            return 0;
        }
        Structures.Def def = Structures.ALL.stream().filter(d -> matches(d, category, name)).findFirst().orElseThrow();
        ServerLevel level = c.getSource().getLevel();
        Vec3 p = c.getSource().getPosition();
        // the direction you look at; the structure's front (its -Z side) faces back toward you
        Direction look = Direction.fromYRot(c.getSource().getRotation().y);
        int rot = switch (look.getOpposite()) {
            case NORTH -> Structures.NORTH;
            case EAST -> Structures.EAST;
            case SOUTH -> Structures.SOUTH;
            default -> Structures.WEST;
        };
        int sx = def.sizeX(rot);
        int sz = def.sizeZ(rot);
        int px = (int) Math.floor(p.x);
        int pz = (int) Math.floor(p.z);
        int x0;
        int z0;
        switch (look) {
            case NORTH -> {
                x0 = px - sx / 2;
                z0 = pz - 2 - sz + 1;
            }
            case SOUTH -> {
                x0 = px - sx / 2;
                z0 = pz + 2;
            }
            case EAST -> {
                x0 = px + 2;
                z0 = pz - sz / 2;
            }
            default -> {
                x0 = px - 2 - sx + 1;
                z0 = pz - sz / 2;
            }
        }
        // the ground layer replaces the block you stand on
        int base = (int) Math.floor(p.y) - 1;
        long seed = Hash.of(System.nanoTime(), 0x5350574EL, x0, z0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        int containers = 0;
        int hardOnes = 0;
        for (int ry = -def.ground(); ry < def.above(); ry++) {
            for (int rz = 0; rz < sz; rz++) {
                for (int rx = 0; rx < sx; rx++) {
                    int code = def.code(rx, ry, rz, rot, seed);
                    // nothing in the template, or its air at the ground: the world stays as it is; under the ground only
                    // cave_air comes as AIR (an underground garage, dug out), and at the ground the mouth of its ramp
                    // or stairwell stays open
                    if (code == BlockCode.KEEP || code == BlockCode.AIR && ry == 0 && !def.opening(rx, rz, rot)) {
                        continue;
                    }
                    BlockState s = Palette.of(code, null);
                    if (s == null) {
                        continue;
                    }
                    pos.set(x0 + rx, base + ry, z0 + rz);
                    // no shape updates while placing: a vine or a pane must not react to a neighbour not placed yet
                    level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    placed++;
                    if (s.hasBlockEntity()) {
                        Palette.applyBanner(level.getBlockEntity(pos), def.banner(rx, ry, rz, rot), level.registryAccess());
                        Spawners.configure(level.getBlockEntity(pos), pos);
                    }
                    if (s.hasBlockEntity() && level.getBlockEntity(pos) instanceof RandomizableContainer rc) {
                        boolean hard = def.hard(rx, ry, rz, rot);
                        rc.setLootTable(Palette.lootTable(def, s, hard), Hash.of(seed, 0x4C4F4F54L, pos.getX(), pos.getY() * 31L + pos.getZ()));
                        containers++;
                        hardOnes += hard ? 1 : 0;
                    }
                }
            }
        }
        // the leaves completing the crowns its template cut, where there is air
        for (int[] o : def.overhangs(rot)) {
            pos.set(x0 + o[0], base + o[1], z0 + o[2]);
            BlockState s = Palette.of(o[3], null);
            if (s != null && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                placed++;
            }
        }
        final int n = placed;
        final int k = containers;
        final int kh = hardOnes;
        final int fx = x0;
        final int fz = z0;
        c.getSource().sendSuccess(() -> Component.literal("[AMCS] " + def.name() + " placed: box " + fx + "," + fz + " .. "
                + (fx + sx - 1) + "," + (fz + sz - 1) + ", ground y " + base + ", " + n + " blocks, " + k
                + " containers with loot (" + kh + " hard to get to)"), true);
        return 1;
    }
}
