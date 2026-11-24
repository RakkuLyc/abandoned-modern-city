package com.abandonedmoderncity.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The mixins of the mod are all for other mods: each is applied only when its mod is loaded (Outpost Zero:
 * {@link OutpostZeroSiteMixin}). Without them the mod changes nothing of the game's code.
 */
public final class AmcsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".OutpostZeroSiteMixin")) {
            // the literal, not OutpostZero.MOD_ID: no class of the game may load this early
            return loaded("outpostzero");
        }
        return false;
    }

    /** Whether the mod {@code id} is in the game (read while the mods load, before any of them runs). */
    static boolean loaded(String id) {
        try {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById(id) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
