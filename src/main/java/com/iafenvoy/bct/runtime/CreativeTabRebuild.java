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
        // No tab list exists before the client has a level, and the one built later reads the new definitions anyway.
        if (minecraft.level == null) return;

        // Unconditional: with the cache left in place the next open would compare equal and skip the rebuild, showing
        // the old fold for the rest of the session.
        CreativeModeTabsAccessor.bct$setCachedParameters(null);

        // The option gives the same answer a screen would - vanilla ANDs it with the game-master permission, and an
        // empty operator tab does not display itself, so over-asking cannot leak it.
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), minecraft.options.operatorItemsTab().get(), minecraft.level.registryAccess());

        // A screen holds its own copy of the tab list, built from the definitions that just went away.
        if (minecraft.screen instanceof CreativeModeInventoryScreen screen) refresh(screen);
    }

    private static void refresh(CreativeModeInventoryScreen screen) {
        // Read from the screen's own field: the selected tab outlives any one screen.
        CreativeModeTab selected = CreativeModeInventoryScreenAccessor.bct$getSelectedTab();
        ((CreativeModeInventoryScreenAccessor) screen).bct$refreshCurrentTabContents(List.copyOf(selected.getDisplayItems()));
    }

    private CreativeTabRebuild() {
    }
}
