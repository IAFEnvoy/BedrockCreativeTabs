package com.iafenvoy.bct.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

/**
 * The two things the screen keeps to itself that this mod has to reach: its own contents refresh, and which tab it is
 * showing.
 *
 * <p>The refresh is the only thing that keeps the row the player scrolled to: the fold writes a tab's display list
 * itself, so the changed list has to go through the same method the screen uses for every other change (it remembers
 * the top row, refills, then maps that row onto the new row count).
 *
 * <p>The selected tab is read after a resource reload, to know which rebuilt tab to hand that refresh. It is a static
 * field on the screen rather than anything the menu answers, because the tab a player is looking at outlives any one
 * screen.
 *
 * <p>The screen's permission flag is deliberately <em>not</em> reached from here. It is only ever the constructor
 * argument stored at {@code :125}, which is the {@code operatorItemsTab} option the screen was opened with, so a
 * rebuild that has no screen to ask reads that option instead and gets the same answer without a third accessor.
 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Invoker("refreshCurrentTabContents")
    void bct$refreshCurrentTabContents(Collection<ItemStack> displayList);

    @Accessor("selectedTab")
    static CreativeModeTab bct$getSelectedTab() {
        throw new AssertionError();
    }
}
