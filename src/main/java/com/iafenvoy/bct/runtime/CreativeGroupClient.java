package com.iafenvoy.bct.runtime;

import com.iafenvoy.bct.mixin.CreativeModeInventoryScreenAccessor;
import com.iafenvoy.bct.util.BctIconTags;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The click that opens a group. Nothing here blocks the icon from being taken - the platform's own slot lock already
 * refuses that - so this is only about the fold.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreativeGroupClient {
    // The picker menu adds its own 45 slots before the player's hotbar.
    private static final int PICKER_SLOTS = 45;

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || slot.index >= PICKER_SLOTS) return;
        ItemStack clicked = slot.getItem();
        ResourceLocation groupId = BctIconTags.groupId(clicked);
        ResourceKey<CreativeModeTab> tabKey = BctIconTags.tab(clicked);
        if (groupId == null || tabKey == null) return;
        if (BuiltInRegistries.CREATIVE_MODE_TAB.get(tabKey) == null) return;
        // Toggled on a copy of the screen's own list, not on the tab's: the tab keeps the folded shape, so a page
        // always opens folded, and the copy is what the refresh below can safely consume.
        ItemPickerMenu menu = screen.getMenu();
        List<ItemStack> view = new ArrayList<>(menu.items);
        if (!CreativeGroupService.toggle(view, tabKey, groupId, CreativeGroupService.members(tabKey, groupId))) return;
        // The screen's own refresh, not a plain refill: it keeps the row the player scrolled to.
        ((CreativeModeInventoryScreenAccessor) screen).bct$refreshCurrentTabContents(view);
        event.setCanceled(true);
    }

    private CreativeGroupClient() {
    }
}
