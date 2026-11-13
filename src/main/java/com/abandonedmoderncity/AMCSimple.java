package com.abandonedmoderncity;

import com.abandonedmoderncity.command.SelfTest;
import com.abandonedmoderncity.command.SimpleCommands;
import com.abandonedmoderncity.core.Structures;
import com.abandonedmoderncity.core.Weathering;
import com.abandonedmoderncity.gen.CityFeature;
import com.abandonedmoderncity.gen.Palette;
import com.abandonedmoderncity.gen.Plans;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Abandoned Modern City: procedural abandoned modern cities linked by highways.
 * <ul>
 *   <li>{@code core}: the pure planner (cities, streets, highways, lots, buildings). No Minecraft classes, unit tested.</li>
 *   <li>{@code gen}: the world generation feature that turns the plan of a chunk into blocks.</li>
 *   <li>{@code command}: {@code /amcs} diagnostics, in-world audits and the automated self-test.</li>
 * </ul>
 */
@Mod(AMCSimple.MODID)
public final class AMCSimple {
    public static final String MODID = "abandonedmoderncity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);
    /** Placed once per chunk (top_layer_modification step) by the biome modifier in data/. */
    public static final DeferredHolder<Feature<?>, CityFeature> CITY = FEATURES.register("city", CityFeature::new);

    public AMCSimple(IEventBus modBus, ModContainer container) {
        FEATURES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, SimpleConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading e) -> Plans.clear());
        modBus.addListener((ModConfigEvent.Reloading e) -> Plans.clear());
        // fail fast: every block the generator can place must be breakable
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            // read before the structures load (Palette.validate loads them): the overgrowth is applied while loading
            Weathering.enabled = SimpleConfig.STRUCTURE_OVERGROWTH.get();
            Palette.validate();
            LOGGER.info("[AMCS] Structure overgrowth {}", Weathering.enabled
                    ? "on (" + Structures.WEATHERING.values().stream()
                    .mapToInt(Weathering.Report::total).sum() + " blocks changed)" : "off");
        }));
        NeoForge.EVENT_BUS.addListener(SimpleCommands::register);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> Plans.clear());
        SelfTest.init();
    }
}
