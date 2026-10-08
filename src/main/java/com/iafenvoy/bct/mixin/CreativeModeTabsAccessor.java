package com.iafenvoy.bct.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Clears the parameter cache vanilla keeps for the tab build, which is the only way to make a tab be built again
 * without the things it compares actually changing.
 *
 * <p>{@code tryRebuildTabContents} skips the build whenever the cached parameters still match, and "match" means the
 * same enabled features, the same permission flag and the same registry provider - none of which a resource reload
 * touches. So after a reload the tabs are still the ones built from the previous definitions, and writing
 * {@code null} here is what makes the next rebuild actually happen.
 */
@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void bct$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError();
    }
}
