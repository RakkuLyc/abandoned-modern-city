package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.core.Crowns;
import com.abandonedmoderncity.core.WorldPlan;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;

/** The leaves completing the crowns cut by a structure's edge ({@link Crowns}), as block states. */
public final class Overhangs {
    private Overhangs() {
    }

    /** The planned crown leaves whose column is in chunk (cx, cz): {@code BlockPos.asLong} -> leaf state. */
    public static Map<Long, BlockState> forChunk(WorldPlan plan, int cx, int cz) {
        Map<Long, BlockState> out = new HashMap<>();
        for (Map.Entry<Long, Integer> e : Crowns.forChunk(plan, cx, cz).entrySet()) {
            BlockState s = Palette.of(e.getValue(), null);
            if (s != null) {
                out.put(e.getKey(), s);
            }
        }
        return out;
    }
}
