package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.AbandonedModernCity;
import com.abandonedmoderncity.core.WorldPlan;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Compatibility with Outpost Zero (mod id {@value #MOD_ID}): its bunkers are placed after the world is generated, on a
 * tick of the server, wherever its only check of a site ({@code BunkerSiteSuitabilityLogic.checkBunkerSite}: no water,
 * no steep slope) passes, and the flat ground of a city or a highway always passes it. The mixin
 * {@code com.abandonedmoderncity.mixin.OutpostZeroSiteMixin}, applied only when that mod is loaded
 * ({@code AmcsMixinPlugin}), asks {@link #refuse} first: a site {@link #REACH} or less from a city or a highway with
 * their slopes ({@link WorldPlan#nearCityOrHighway}) is refused with a result of Outpost Zero's own, code
 * {@value #CODE}, and Outpost Zero looks for another one (the additional bunkers: another spot of the region, up to 16
 * times; the starter bunker: the other spots around the spawn, and its fallback takes no site refused for this reason).
 * Nothing of Outpost Zero is compiled in: its result is made through reflection.
 */
public final class OutpostZero {
    private OutpostZero() {
    }

    public static final String MOD_ID = "outpostzero";
    /** A bunker site this close (blocks, from the point Outpost Zero checks) to a city or a highway is refused. */
    public static final int REACH = 48;
    /** The code of the refusal, in Outpost Zero's result (its debug log prints it). */
    public static final String CODE = "amcs_city_or_highway";
    private static final String RESULT =
            "dev.mostlyharmless.outpostzero.world.bunker.logic.BunkerSiteSuitabilityLogic$Result";

    private static volatile MethodHandle fail;
    private static volatile boolean broken;
    private static final AtomicLong REFUSED = new AtomicLong();

    /** How many sites were refused since the game started. */
    public static long refused() {
        return REFUSED.get();
    }

    /**
     * The result that refuses a bunker at {@code pos} (an instance of Outpost Zero's {@code Result}), or null to leave
     * the site to Outpost Zero's own checks: away from the cities and highways, outside the overworld, with the mod
     * off, or when its result could not be made (logged once).
     */
    public static Object refuse(ServerLevel level, BlockPos pos) {
        if (broken || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        WorldPlan plan = Plans.get(level);
        if (plan == null || !plan.nearCityOrHighway(pos.getX() + 0.5, pos.getZ() + 0.5, REACH)) {
            return null;
        }
        try {
            MethodHandle f = fail;
            if (f == null) {
                Class<?> result = Class.forName(RESULT, true, OutpostZero.class.getClassLoader());
                f = MethodHandles.publicLookup().findStatic(result, "fail",
                        MethodType.methodType(result, String.class, String.class, int.class, boolean.class));
                fail = f;
            }
            Object r = f.invoke(CODE, "within " + REACH + " blocks of an Abandoned Modern City city or highway", -1,
                    true);
            long n = REFUSED.incrementAndGet();
            if (n == 1) {
                AbandonedModernCity.LOGGER.info("[AMCS] Outpost Zero: bunker sites within {} blocks of a city or a "
                        + "highway are refused (first at {}, {})", REACH, pos.getX(), pos.getZ());
            } else {
                AbandonedModernCity.LOGGER.debug("[AMCS] Outpost Zero: bunker site at {}, {} refused", pos.getX(),
                        pos.getZ());
            }
            return r;
        } catch (Throwable t) {
            broken = true;
            AbandonedModernCity.LOGGER.warn("[AMCS] Outpost Zero: could not refuse its bunker sites near the cities "
                    + "(its code changed?); its bunkers may stand in cities and on highways", t);
            return null;
        }
    }
}
