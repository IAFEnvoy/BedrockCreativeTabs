package com.iafenvoy.bct.runtime;

import com.iafenvoy.bct.mixin.CreativeModeInventoryScreenAccessor;
import com.iafenvoy.bct.mixin.CreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;

/**
 * Rebuilds the creative tabs after the definitions change, which a resource reload always does.
 *
 * <p>Two things have to happen and neither is enough alone. First the parameter cache has to be dropped, because
 * vanilla skips the build while the cached parameters still compare equal and a reload changes none of the three
 * things it compares (see {@link CreativeModeTabsAccessor}). Then the tabs are built again through vanilla's own
 * entry point, so the fold runs as a listener on the build event like it always does and nothing is special-cased.
 *
 * <p>An open creative screen is showing its own copy of a tab list built from the definitions that just went away, so
 * it is handed the rebuilt list through the screen's own contents method. That method already knows what to do with a
 * search tab, and it is also what keeps the row the player scrolled to.
 *
 * <p>With no creative screen open the tabs are still rebuilt: the rebuild is what makes the next open show the new
 * definitions, because the parameter cache alone would not notice the reload (see above, and the cache is cleared
 * before this decision so the rebuild cannot be skipped). Only the refresh at the end needs a screen to act on.
 */
public final class CreativeTabRebuild {
    /**
     * The tabs as the client has them, rebuilt from the definitions now loaded.
     */
    public static void rebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        // Nothing to rebuild onto before the client has a level: there is no tab list yet, and the one built later
        // will read the new definitions anyway.
        if (minecraft.level == null) return;

        // Nulling the cache is what makes the call below do anything: it compares against it, and a reload changes
        // none of the three things it holds. This has to happen even when no creative screen is open, because
        // otherwise the cache survives the reload and the player's next open compares equal and skips the rebuild,
        // leaving the old fold in place for the rest of the session.
        CreativeModeTabsAccessor.bct$setCachedParameters(null);

        // Vanilla ANDs two things into the flag the tabs are built with: the game-master permission, which only the
        // player knows, and a display flag, which is the option a creative screen is opened with - the screen stores
        // that argument and never revisits it. Reading the option therefore gives the same answer a screen would,
        // whether or not one is open. A player without the permission builds the operator tab empty, and an empty tab
        // does not display itself, so over-asking here cannot leak it.
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), minecraft.options.operatorItemsTab().get(), minecraft.level.registryAccess());

        // Only a screen that is showing the tabs needs to be told they changed; the list it holds is a copy built
        // from the definitions that just went away.
        if (minecraft.screen instanceof CreativeModeInventoryScreen screen) refresh(screen);
    }

    /**
     * Refills an open creative screen from the tabs that were just rebuilt, so the page it is showing matches the
     * definitions now loaded instead of the ones it was built from. The screen keeps its own list rather than reading
     * the tab every frame, which is exactly why it has to be told.
     */
    private static void refresh(CreativeModeInventoryScreen screen) {
        // The tab the player is looking at, which is the one the screen is showing a copy of. Reading it from the
        // screen's own field is the only way to get it: which tab is selected outlives any one screen.
        CreativeModeTab selected = CreativeModeInventoryScreenAccessor.bct$getSelectedTab();
        ((CreativeModeInventoryScreenAccessor) screen).bct$refreshCurrentTabContents(List.copyOf(selected.getDisplayItems()));
    }

    private CreativeTabRebuild() {
    }
}
