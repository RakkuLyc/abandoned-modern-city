package com.abandonedmoderncity.mixin;

import com.abandonedmoderncity.gen.OutpostZero;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Outpost Zero's check of a bunker site first asks {@link OutpostZero#refuse} (cities and highways). Applied only when
 * Outpost Zero is loaded ({@link AmcsMixinPlugin}); its classes are named by string, nothing of it is compiled in.
 * Both its callers (the starter bunker and the additional ones) go through this method.
 */
@Pseudo
@Mixin(targets = "dev.mostlyharmless.outpostzero.world.bunker.logic.BunkerSiteSuitabilityLogic", remap = false)
public abstract class OutpostZeroSiteMixin {
    @Inject(method = "checkBunkerSite(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)"
            + "Ldev/mostlyharmless/outpostzero/world/bunker/logic/BunkerSiteSuitabilityLogic$Result;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void amcs$awayFromCitiesAndHighways(ServerLevel level, BlockPos pos, int slope,
                                                       CallbackInfoReturnable<Object> cir) {
        Object refused = OutpostZero.refuse(level, pos);
        if (refused != null) {
            cir.setReturnValue(refused);
        }
    }
}
