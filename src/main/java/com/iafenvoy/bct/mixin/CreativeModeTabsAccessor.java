package com.iafenvoy.bct.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the build-parameter cache be cleared, which is the only way to make tabs rebuild when nothing they compare has
 * changed: {@code tryRebuildTabContents} skips the build while the cached features, permission flag and registry
 * provider still match, and a resource reload touches none of the three.
 */
@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void bct$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError();
    }
}
