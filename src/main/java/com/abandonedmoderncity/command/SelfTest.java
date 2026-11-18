package com.abandonedmoderncity.command;

import com.abandonedmoderncity.AbandonedModernCity;
import com.abandonedmoderncity.core.City;
import com.abandonedmoderncity.core.Highway;
import com.abandonedmoderncity.core.NetworkStats;
import com.abandonedmoderncity.core.PlanAudit;
import com.abandonedmoderncity.core.PlanRender;
import com.abandonedmoderncity.core.Profile;
import com.abandonedmoderncity.core.WorldPlan;
import com.abandonedmoderncity.gen.CityFeature;
import com.abandonedmoderncity.gen.McTerrain;
import com.abandonedmoderncity.gen.Palette;
import com.abandonedmoderncity.gen.Plans;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Automated in-world test battery: {@code ./gradlew runServer -PselfTest=<report.txt>} (system property
 * {@code amcs.selftest}). On a fresh world it picks varied places from the plan itself (cities on flat, high, coastal
 * and rugged ground; the longest tunnel, a bridge over water, a dry viaduct, the longest ramp, a whole highway), has the
 * server generate them, runs the plan audit and the block audit on each, writes PNGs next to the report and stops the
 * server. The report ends with {@code RESULT: PASS} only if every audit found 0 problems and generation raised no error.
 */
public final class SelfTest {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("amcs_selftest", Comparator.comparingLong(ChunkPos::toLong));
    private static final int MAX_RADIUS = 18;
    private static final int IN_FLIGHT = 500;

    private record Target(String name, int cx, int cz, int r, Highway highway, int k0, int k1) {
    }

    private static String output;
    private static List<Target> targets;
    private static int index;
    private static List<ChunkPos> pending;
    private static List<ChunkPos> requested;
    private static StringBuilder report;
    private static long started;
    private static long targetStarted;
    private static int failures;
    private static boolean done;

    private SelfTest() {
    }

    public static void init() {
        output = System.getProperty("amcs.selftest");
        if (output == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> start(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    private static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        WorldPlan plan = Plans.get(level);
        report = new StringBuilder();
        started = System.currentTimeMillis();
        report.append("AMCS self-test, seed ").append(level.getSeed()).append(", ").append(plan == null ? "no plan" : plan.params)
                .append('\n');
        try {
            Palette.validate();
            report.append("palette: ").append(Palette.all().size()).append(" block states, all breakable\n");
        } catch (RuntimeException e) {
            report.append("palette: FAIL ").append(e.getMessage()).append('\n');
            failures++;
        }
        if (plan == null) {
            failures++;
            finish(server);
            return;
        }
        String scope = System.getProperty("amcs.selftest.scope", "full");
        targets = pickTargets(plan, scope);
        report.append("planning the targets took ").append(System.currentTimeMillis() - started).append(" ms, ")
                .append(McTerrain.SAMPLES.get()).append(" terrain samples\n");
        report.append("targets: ").append(targets.size()).append(" (scope ").append(scope).append(")\n");
        for (Target t : targets) {
            report.append("  ").append(t.name).append(" chunk ").append(t.cx).append(',').append(t.cz).append(" r=").append(t.r).append('\n');
        }
        try {
            int r = 3 * plan.params.cellSize();
            PlanRender.write(PlanRender.network(plan, -r, -r, r, r, 8, false), new File(outDir(), "network.png"));
        } catch (Exception e) {
            report.append("network render failed: ").append(e).append('\n');
        }
        // how well the cities around spawn are linked (6 x 6 cells)
        NetworkStats ns = NetworkStats.of(plan, -3, -3, 2, 2);
        report.append("network around spawn: ").append(ns).append('\n');
        index = -1;
        nextTarget();
    }

    private static File outDir() {
        return new File(new File(output).getAbsoluteFile().getParentFile(), "renders");
    }

    // ------------------------------------------------------------------ target selection

    private static int radiusFor(double halfBlocks) {
        return Math.max(3, Math.min(MAX_RADIUS, (int) Math.ceil(halfBlocks / 16) + 1));
    }

    private static List<Target> pickTargets(WorldPlan plan, String scope) {
        List<Target> out = new ArrayList<>();
        double R = 3.0 * plan.params.cellSize();
        List<City> cities = new ArrayList<>(plan.citiesIn(-R, -R, R, R));
        cities.sort(Comparator.comparingDouble(c -> (double) c.cx * c.cx + (double) c.cz * c.cz));
        if (cities.isEmpty()) {
            return out;
        }
        List<City> chosen = new ArrayList<>();
        addCity(out, chosen, "city nearest to spawn", cities.get(0));
        if (!scope.equals("quick")) {
            City high = cities.stream().limit(16).max(Comparator.comparingInt(c -> c.level)).orElse(null);
            addCity(out, chosen, "highest city (level " + (high == null ? 0 : high.level) + ")", high);
            City coastal = cities.stream().limit(16).filter(c -> nearWater(plan, c)).findFirst().orElse(null);
            addCity(out, chosen, "coastal city", coastal);
            City rugged = cities.stream().limit(16).max(Comparator.comparingInt(c -> ruggedness(plan, c))).orElse(null);
            addCity(out, chosen, "most rugged city (" + (rugged == null ? 0 : ruggedness(plan, rugged)) + ")", rugged);
        }
        List<Highway> hs = plan.highwaysOf(cities.subList(0, Math.min(cities.size(), 16)));
        hs.sort(Comparator.comparingLong(h -> h.key));
        // the longest run of each kind
        addRun(out, plan, hs, "longest tunnel", Profile.TUNNEL, null);
        addRun(out, plan, hs, "longest bridge over water", Profile.BRIDGE, Boolean.TRUE);
        if (!scope.equals("quick")) {
            addRun(out, plan, hs, "longest dry viaduct", Profile.BRIDGE, Boolean.FALSE);
            addRamp(out, plan, hs);
            // a whole highway that fits
            hs.stream().filter(h -> Math.max(h.maxX - h.minX, h.maxZ - h.minZ) / 2 + 40 <= MAX_RADIUS * 16)
                    .min(Comparator.comparingInt(h -> h.length))
                    .ifPresent(h -> out.add(region("whole highway " + h, h, 0, h.length, 40)));
        }
        return out;
    }

    private static void addCity(List<Target> out, List<City> chosen, String name, City c) {
        if (c == null || chosen.contains(c)) {
            return;
        }
        chosen.add(c);
        out.add(new Target(name + " " + c, Math.floorDiv(c.cx, 16), Math.floorDiv(c.cz, 16), radiusFor(c.influence), null, 0, 0));
    }

    private static boolean nearWater(WorldPlan plan, City c) {
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12;
            int x = c.cx + (int) Math.round(Math.cos(a) * (c.influence + 96));
            int z = c.cz + (int) Math.round(Math.sin(a) * (c.influence + 96));
            if (plan.terrain.water(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static int ruggedness(WorldPlan plan, City c) {
        int max = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int g = plan.terrain.ground(c.cx + dx * c.radius / 2, c.cz + dz * c.radius / 2);
                max = Math.max(max, Math.abs(g - c.level));
            }
        }
        return max;
    }

    private static Target region(String name, Highway h, int k0, int k1, int pad) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int k = k0; k <= k1; k++) {
            minX = Math.min(minX, h.x(k));
            maxX = Math.max(maxX, h.x(k));
            minZ = Math.min(minZ, h.z(k));
            maxZ = Math.max(maxZ, h.z(k));
        }
        int cx = Math.floorDiv((int) Math.floor((minX + maxX) / 2), 16);
        int cz = Math.floorDiv((int) Math.floor((minZ + maxZ) / 2), 16);
        double half = Math.max(maxX - minX, maxZ - minZ) / 2 + pad;
        return new Target(name, cx, cz, radiusFor(half), h, k0, k1);
    }

    private static void addRun(List<Target> out, WorldPlan plan, List<Highway> hs, String name, byte type, Boolean wet) {
        Highway best = null;
        int bk0 = 0;
        int bk1 = -1;
        for (Highway h : hs) {
            Highway.Detail d = h.detail(plan.terrain);
            int k = 0;
            while (k <= h.length) {
                if (d.type[k] != type) {
                    k++;
                    continue;
                }
                int e = k;
                boolean anyWater = false;
                while (e <= h.length && d.type[e] == type) {
                    anyWater |= d.water[e];
                    e++;
                }
                if ((wet == null || wet == anyWater) && e - 1 - k > bk1 - bk0) {
                    best = h;
                    bk0 = k;
                    bk1 = e - 1;
                }
                k = e;
            }
        }
        if (best != null) {
            // the run plus its approaches, limited to what fits in the region
            int k0 = Math.max(0, bk0 - 48);
            int k1 = Math.min(best.length, bk1 + 48);
            int maxSpan = 2 * (MAX_RADIUS - 2) * 16;
            if (k1 - k0 > maxSpan) {
                k1 = k0 + maxSpan;
            }
            out.add(region(name + " (" + (bk1 - bk0 + 1) + " blocks) of " + best, best, k0, k1, 24));
        }
    }

    private static void addRamp(List<Target> out, WorldPlan plan, List<Highway> hs) {
        Highway best = null;
        int bk0 = 0;
        int bk1 = -1;
        for (Highway h : hs) {
            Highway.Detail d = h.detail(plan.terrain);
            int k = 1;
            while (k <= h.length) {
                if (d.he[k] == d.he[k - 1]) {
                    k++;
                    continue;
                }
                int e = k;
                while (e <= h.length && d.he[e] != d.he[e - 1]) {
                    e++;
                }
                if (e - k > bk1 - bk0) {
                    best = h;
                    bk0 = k - 1;
                    bk1 = e - 1;
                }
                k = e;
            }
        }
        if (best != null) {
            int k0 = Math.max(0, bk0 - 32);
            int k1 = Math.min(best.length, Math.min(bk1 + 32, k0 + 2 * (MAX_RADIUS - 2) * 16));
            out.add(region("longest ramp (" + (bk1 - bk0) + " blocks, "
                    + Math.abs(best.detail(plan.terrain).he[bk1] - best.detail(plan.terrain).he[bk0]) / 8.0 + " up/down) of " + best,
                    best, k0, k1, 24));
        }
    }

    // ------------------------------------------------------------------ running

    private static void nextTarget() {
        index++;
        if (index >= targets.size()) {
            done = true;
            return;
        }
        Target t = targets.get(index);
        pending = new ArrayList<>();
        int g = t.r + 1;
        for (int dz = -g; dz <= g; dz++) {
            for (int dx = -g; dx <= g; dx++) {
                pending.add(new ChunkPos(t.cx + dx, t.cz + dz));
            }
        }
        requested = new ArrayList<>();
        targetStarted = System.currentTimeMillis();
        AbandonedModernCity.LOGGER.info("[AMCS self-test] target {}/{}: {} ({} chunks)", index + 1, targets.size(), t.name, pending.size());
    }

    private static void tick(MinecraftServer server) {
        if (targets == null || report == null) {
            return;
        }
        if (done) {
            finish(server);
            return;
        }
        ServerLevel level = server.overworld();
        var source = level.getChunkSource();
        // keep up to IN_FLIGHT chunks requested
        int ready = 0;
        for (ChunkPos p : requested) {
            if (source.getChunkNow(p.x, p.z) != null) {
                ready++;
            }
        }
        while (!pending.isEmpty() && requested.size() - ready < IN_FLIGHT) {
            ChunkPos p = pending.remove(pending.size() - 1);
            source.addRegionTicket(TICKET, p, 0, p);
            requested.add(p);
        }
        if (!pending.isEmpty() || ready < requested.size()) {
            return;
        }
        Target t = targets.get(index);
        WorldPlan plan = Plans.get(level);
        long genMs = System.currentTimeMillis() - targetStarted;
        PlanAudit.Report pr = PlanAudit.audit(plan, t.cx - t.r, t.cz - t.r, t.cx + t.r, t.cz + t.r);
        PlanAudit.Report wr = WorldAudit.run(level, plan, t.cx - t.r, t.cz - t.r, t.cx + t.r, t.cz + t.r);
        String base = String.format("t%02d", index + 1);
        WorldFingerprint.Result fp = WorldFingerprint.of(level, plan, t.cx - t.r, t.cz - t.r, t.cx + t.r, t.cz + t.r,
                new File(outDir(), base + "_blocks.txt"));
        try {
            PlanRender.write(WorldRender.topDown(level, t.cx - t.r, t.cz - t.r, t.cx + t.r, t.cz + t.r), new File(outDir(), base + "_world.png"));
            PlanRender.write(PlanRender.detail(plan, t.cx - t.r, t.cz - t.r, t.cx + t.r, t.cz + t.r), new File(outDir(), base + "_plan.png"));
            if (t.highway != null) {
                PlanRender.write(WorldRender.alongHighway(level, plan, t.highway, t.k0, t.k1, 24, 24), new File(outDir(), base + "_side.png"));
            }
        } catch (Exception e) {
            report.append("render failed: ").append(e).append('\n');
        }
        if (!pr.ok()) {
            failures++;
        }
        if (!wr.ok()) {
            failures++;
        }
        report.append("\n== ").append(base).append(": ").append(t.name).append("\n   chunks ").append(t.cx - t.r).append(',')
                .append(t.cz - t.r).append(" .. ").append(t.cx + t.r).append(',').append(t.cz + t.r)
                .append(" (blocks ").append((t.cx - t.r) * 16).append(',').append((t.cz - t.r) * 16).append(" .. ")
                .append((t.cx + t.r) * 16 + 15).append(',').append((t.cz + t.r) * 16 + 15).append("), generated in ")
                .append(genMs).append(" ms\n   ").append(pr.summary().replace("\n", "\n   ")).append(wr.summary().replace("\n", "\n   "))
                .append("mod blocks: ").append(fp).append('\n');
        AbandonedModernCity.LOGGER.info("[AMCS self-test] {}: plan {}, world {}", t.name, pr.ok() ? "OK" : pr.failures() + " failures",
                wr.ok() ? "OK" : wr.failures() + " failures");
        for (ChunkPos p : requested) {
            source.removeRegionTicket(TICKET, p, 0, p);
        }
        nextTarget();
    }

    private static void finish(MinecraftServer server) {
        int errors = CityFeature.ERRORS.get();
        long chunks = CityFeature.CHUNKS.get();
        report.append("\ngeneration errors: ").append(errors).append('\n');
        report.append("village creatures removed from cities: ").append(Plans.villageMobsRemoved()).append('\n');
        report.append("floating vegetation removed: ").append(Plans.floatingRemoved()).append(" blocks\n");
        report.append("chunks through the feature: ").append(chunks).append(", mean ")
                .append(chunks == 0 ? 0 : String.format("%.2f", CityFeature.NANOS.get() / 1e6 / chunks)).append(" ms per chunk\n");
        long samples = McTerrain.SAMPLES.get();
        report.append("terrain noise samples for planning: ").append(samples).append(", ")
                .append(String.format("%.1f", McTerrain.NANOS.get() / 1e9)).append(" s in total\n");
        report.append("total time: ").append((System.currentTimeMillis() - started) / 1000).append(" s\n");
        if (errors > 0) {
            failures++;
        }
        report.append("RESULT: ").append(failures == 0 ? "PASS" : "FAIL (" + failures + ")").append('\n');
        try {
            File f = new File(output).getAbsoluteFile();
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), report.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AbandonedModernCity.LOGGER.error("[AMCS self-test] cannot write the report", e);
        }
        AbandonedModernCity.LOGGER.info("[AMCS self-test] finished: {}\n{}", failures == 0 ? "PASS" : "FAIL", report);
        targets = null;
        report = null;
        server.halt(false);
    }
}
