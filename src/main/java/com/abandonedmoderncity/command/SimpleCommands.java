package com.abandonedmoderncity.command;

import com.abandonedmoderncity.core.ChunkPlan;
import com.abandonedmoderncity.core.City;
import com.abandonedmoderncity.core.Highway;
import com.abandonedmoderncity.core.PlanAudit;
import com.abandonedmoderncity.core.PlanRender;
import com.abandonedmoderncity.core.Profile;
import com.abandonedmoderncity.core.WorldPlan;
import com.abandonedmoderncity.gen.Plans;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.File;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /amcs} (op level 2):
 * <ul>
 *   <li>{@code info}: plan of the column under you (kind, height, structure, city, highway).</li>
 *   <li>{@code locate city|highway|tunnel|bridge}: nearest one.</li>
 *   <li>{@code plan [radius]}: plan audit of the chunks around you (nothing is generated) + PNG.</li>
 *   <li>{@code audit [radius]}: generates the chunks around you, audits plan and blocks, writes PNGs.</li>
 *   <li>{@code network [radius]}: PNG of cities and highways (blocks, 8 per pixel).</li>
 * </ul>
 * Files go to {@code amcs_renders/} in the server folder.
 */
public final class SimpleCommands {
    private SimpleCommands() {
    }

    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("amcs").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(SimpleCommands::info))
                .then(Commands.literal("locate").then(Commands.argument("what", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String s : List.of("city", "highway", "tunnel", "bridge")) {
                                b.suggest(s);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> locate(c, StringArgumentType.getString(c, "what")))))
                .then(Commands.literal("plan").executes(c -> plan(c, 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64)).executes(c -> plan(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("audit").executes(c -> audit(c, 6))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32)).executes(c -> audit(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("column").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("y0", IntegerArgumentType.integer())
                                        .then(Commands.argument("y1", IntegerArgumentType.integer())
                                                .executes(c -> column(c, IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "z"), IntegerArgumentType.getInteger(c, "y0"),
                                                        IntegerArgumentType.getInteger(c, "y1"))))))))
                .then(Commands.literal("network").executes(c -> network(c, 4000))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(500, 20000)).executes(c -> network(c, IntegerArgumentType.getInteger(c, "radius"))))));
    }

    static File outDir(ServerLevel level) {
        return level.getServer().getServerDirectory().resolve("amcs_renders").toFile();
    }

    private static WorldPlan plan(CommandContext<CommandSourceStack> c) {
        WorldPlan plan = Plans.get(c.getSource().getLevel());
        if (plan == null) {
            c.getSource().sendFailure(Component.literal("No cities in this dimension (or disabled in the config)"));
        }
        return plan;
    }

    private static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        WorldPlan plan = plan(c);
        if (plan == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        ChunkPlan cp = plan.chunk(pos.getX() >> 4, pos.getZ() >> 4);
        int i = ChunkPlan.idx(pos.getX() & 15, pos.getZ() & 15);
        String kind = switch (cp.kind[i]) {
            case ChunkPlan.ROAD -> "ROAD";
            case ChunkPlan.LOT -> "LOT";
            case ChunkPlan.SKIRT -> "SLOPE";
            case ChunkPlan.TUNNEL_WALL -> "TUNNEL_WALL";
            default -> "NONE";
        };
        StringBuilder sb = new StringBuilder("[AMCS] ").append(pos.toShortString()).append(": ").append(kind);
        if (cp.kind[i] == ChunkPlan.ROAD || cp.kind[i] == ChunkPlan.TUNNEL_WALL) {
            sb.append(" walk=").append(cp.h2[i] / 2.0)
                    .append(" struct=").append(switch (cp.struct[i]) {
                        case Profile.BRIDGE -> "bridge";
                        case Profile.TUNNEL -> "tunnel";
                        default -> "ground";
                    })
                    .append((cp.flags[i] & ChunkPlan.F_CITY) != 0 ? " city street" : "")
                    .append(cp.highway[i] != null ? " " + cp.highway[i] : "");
        } else if (cp.kind[i] == ChunkPlan.LOT) {
            sb.append(" level=").append(cp.h2[i] / 2).append(cp.lot[i] == null ? " yard" : " " + cp.lot[i].type);
        } else if (cp.kind[i] == ChunkPlan.SKIRT) {
            sb.append(" ground in [").append(cp.lo[i]).append(", ").append(cp.hi[i]).append(']');
        }
        if (cp.city != null) {
            sb.append(" | ").append(cp.city).append(" blocks=").append(cp.city.blockCount())
                    .append(" highways=").append(plan.highways(cp.city).size());
        }
        say(c, sb.toString());
        return 1;
    }

    /**
     * Nearest city, or the nearest highway / tunnel / bridge column. The coordinates are clickable (they suggest a /tp), like /locate.
     */
    private static int locate(CommandContext<CommandSourceStack> c, String what) {
        WorldPlan plan = plan(c);
        if (plan == null) {
            return 0;
        }
        double px = c.getSource().getPosition().x;
        double pz = c.getSource().getPosition().z;
        boolean cityKind = what.equals("city");
        boolean known = cityKind || what.equals("highway") || what.equals("tunnel") || what.equals("bridge");
        if (!known) {
            c.getSource().sendFailure(Component.literal("[AMCS] unknown target '" + what
                    + "' (city, highway, tunnel, bridge)"));
            return 0;
        }
        double best = Double.MAX_VALUE;
        String found = null;
        int[] at = null;
        // cities are cheap to plan: look far; highways only around the nearer cells
        int[] radii = cityKind ? new int[]{4, 8, 16} : new int[]{3, 6};
        for (int cells : radii) {
            double r = cells * plan.params.cellSize();
            List<City> cities = plan.citiesIn(px - r, pz - r, px + r, pz + r);
            if (cityKind) {
                for (City ct : cities) {
                    double d = Math.hypot(ct.cx - px, ct.cz - pz);
                    if (d < best) {
                        best = d;
                        found = ct.toString();
                        at = new int[]{ct.cx, ct.level + 1, ct.cz};
                    }
                }
            } else {
                for (Highway h : plan.highwaysOf(cities)) {
                    Highway.Detail det = h.detail(plan.terrain);
                    for (int k = 0; k <= h.length; k += 2) {
                        boolean match = what.equals("highway") && k > Profile.JUNCTION && k < h.length - Profile.JUNCTION
                                || what.equals("tunnel") && det.type[k] == Profile.TUNNEL
                                || what.equals("bridge") && det.type[k] == Profile.BRIDGE;
                        if (!match) {
                            continue;
                        }
                        double d = Math.hypot(h.x(k) - px, h.z(k) - pz);
                        if (d < best) {
                            best = d;
                            found = what + " of " + h;
                            at = new int[]{(int) Math.floor(h.x(k)), det.he[k] / 8 + 1, (int) Math.floor(h.z(k))};
                        }
                    }
                }
            }
            if (found != null && best <= r) {
                break;
            }
        }
        if (found == null) {
            say(c, "[AMCS] no " + what + " found within " + radii[radii.length - 1] * plan.params.cellSize() + " blocks");
            return 0;
        }
        String coords = at[0] + " " + at[1] + " " + at[2];
        Component link = Component.literal("[" + coords + "]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + coords))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport"))));
        Component msg = Component.literal("[AMCS] " + found + " at ").append(link)
                .append(Component.literal(" (" + Math.round(best) + " blocks)"));
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int plan(CommandContext<CommandSourceStack> c, int radius) {
        WorldPlan plan = plan(c);
        if (plan == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        PlanAudit.Report rep = PlanAudit.audit(plan, cx - radius, cz - radius, cx + radius, cz + radius);
        try {
            File f = new File(outDir(c.getSource().getLevel()), "plan_" + cx + "_" + cz + ".png");
            PlanRender.write(PlanRender.detail(plan, cx - radius, cz - radius, cx + radius, cz + radius), f);
            say(c, "[AMCS] " + rep.summary() + "-> " + f.getName());
        } catch (Exception e) {
            say(c, "[AMCS] " + rep.summary() + " (render failed: " + e + ")");
        }
        return rep.ok() ? 1 : 0;
    }

    private static int audit(CommandContext<CommandSourceStack> c, int radius) {
        WorldPlan plan = plan(c);
        if (plan == null) {
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int z = cz - radius - 1; z <= cz + radius + 1; z++) {
            for (int x = cx - radius - 1; x <= cx + radius + 1; x++) {
                level.getChunk(x, z);
            }
        }
        PlanAudit.Report pr = PlanAudit.audit(plan, cx - radius, cz - radius, cx + radius, cz + radius);
        PlanAudit.Report wr = WorldAudit.run(level, plan, cx - radius, cz - radius, cx + radius, cz + radius);
        try {
            PlanRender.write(WorldRender.topDown(level, cx - radius, cz - radius, cx + radius, cz + radius),
                    new File(outDir(level), "world_" + cx + "_" + cz + ".png"));
        } catch (Exception e) {
            say(c, "[AMCS] render failed: " + e);
        }
        say(c, "[AMCS] " + pr.summary());
        say(c, "[AMCS] " + wr.summary());
        return pr.ok() && wr.ok() ? 1 : 0;
    }

    /** Blocks of one column next to what the plan wants there (diagnostics). */
    private static int column(CommandContext<CommandSourceStack> c, int x, int z, int y0, int y1) {
        WorldPlan plan = plan(c);
        if (plan == null) {
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        ChunkPlan cp = plan.chunk(x >> 4, z >> 4);
        int i = ChunkPlan.idx(x & 15, z & 15);
        StringBuilder sb = new StringBuilder("[AMCS] column ").append(x).append(',').append(z).append(" kind ").append(cp.kind[i])
                .append(" h2 ").append(cp.h2[i]).append(" struct ").append(cp.struct[i]).append(" zone ").append(cp.zone[i]);
        com.abandonedmoderncity.core.Lot lot = cp.lot[i];
        int base = cp.h2[i] / 2 - 1;
        if (lot != null) {
            sb.append(" lot ").append(lot.type).append(' ').append(lot.x0).append(',').append(lot.z0).append("..")
                    .append(lot.x1).append(',').append(lot.z1);
        }
        var chunk = level.getChunk(x >> 4, z >> 4);
        for (int y = y1; y >= y0; y--) {
            var s = chunk.getBlockState(new BlockPos(x, y, z));
            sb.append("\n  ").append(y).append(' ').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
            if (lot != null) {
                sb.append(" (code ").append(lot.code(x, y, z, base)).append(')');
            }
        }
        say(c, sb.toString());
        return 1;
    }

    private static int network(CommandContext<CommandSourceStack> c, int radius) {
        WorldPlan plan = plan(c);
        if (plan == null) {
            return 0;
        }
        int x = (int) c.getSource().getPosition().x;
        int z = (int) c.getSource().getPosition().z;
        try {
            File f = new File(outDir(c.getSource().getLevel()), "network_" + x + "_" + z + ".png");
            PlanRender.write(PlanRender.network(plan, x - radius, z - radius, x + radius, z + radius, 8), f);
            say(c, "[AMCS] network -> " + f.getName());
        } catch (Exception e) {
            say(c, "[AMCS] render failed: " + e);
            return 0;
        }
        return 1;
    }
}
