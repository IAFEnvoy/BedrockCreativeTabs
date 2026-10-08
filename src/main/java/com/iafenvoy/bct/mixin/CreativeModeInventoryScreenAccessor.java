package com.iafenvoy.bct.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

/**
 * The two things the creative screen keeps to itself that this mod has to reach.
 *
 * <p>The refresh has to go through the screen's own method rather than writing its display list, because that is what
 * keeps the row the player scrolled to.
 *
 * <p>The selected tab is a static field rather than anything the menu answers, since the tab being viewed outlives any
 * one screen. The permission flag is deliberately not reached here: it is only the constructor argument stored at
 * {@code :125}, which is the {@code operatorItemsTab} option, so a rebuild with no screen reads that option instead.
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
