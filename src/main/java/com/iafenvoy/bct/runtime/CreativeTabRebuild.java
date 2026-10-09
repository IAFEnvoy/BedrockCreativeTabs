package com.iafenvoy.bct.runtime;

import com.iafenvoy.bct.mixin.CreativeModeInventoryScreenAccessor;
import com.iafenvoy.bct.mixin.CreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;

/**
 * Rebuilds the creative tabs after a resource reload changed the definitions.
 *
 * <p>Order matters and neither half works alone: vanilla skips the build while its cached parameters compare equal,
 * and a reload changes none of the three things they compare, so the cache has to be dropped first (see
 * {@link CreativeModeTabsAccessor}). Then the tabs are rebuilt through vanilla's own entry point, so the fold still
 * runs as a listener on the build event rather than being special-cased.
 */
public final class CreativeTabRebuild {
    public static void rebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        CreativeModeTabsAccessor.bct$setCachedParameters(null);
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), minecraft.options.operatorItemsTab().get(), minecraft.level.registryAccess());
        if (minecraft.screen instanceof CreativeModeInventoryScreen screen) {
            // Read from the screen's own field: the selected tab outlives any one screen.
            CreativeModeTab selected = CreativeModeInventoryScreenAccessor.bct$getSelectedTab();
            ((CreativeModeInventoryScreenAccessor) screen).bct$refreshCurrentTabContents(List.copyOf(selected.getDisplayItems()));
        }
    }

    private CreativeTabRebuild() {
    }
}
