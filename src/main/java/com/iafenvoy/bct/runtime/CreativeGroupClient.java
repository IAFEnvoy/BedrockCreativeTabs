package com.iafenvoy.bct.runtime;

import com.iafenvoy.bct.data.CreativeGroup;
import com.iafenvoy.bct.mixin.CreativeModeInventoryScreenAccessor;
import com.iafenvoy.bct.registry.BctDataComponents;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of the groups: the click that opens one.
 *
 * <p>Nothing here has to stop the icon from being taken - the platform's slot lock already refuses that, in every
 * click path and in the quick-craft drag - so the click is only about the fold. Both halves are client only because a
 * tab list, and the screen showing it, exist nowhere else.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CreativeGroupClient {
    // The picker menu adds its own 45 slots first and the player's hotbar after them. Vanilla tells its own slots
    // apart by the (private) container they read; the index is what is left of that check.
    private static final int PICKER_SLOTS = 45;

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        Slot slot = screen.getHoveredSlot();
        if (slot == null || slot.index >= PICKER_SLOTS) return;
        ItemStack clicked = slot.getItem();
        CreativeGroup group = clicked.get(BctDataComponents.CREATIVE_GROUP);
        ResourceKey<CreativeModeTab> tabKey = clicked.get(BctDataComponents.CREATIVE_GROUP_TAB);
        if (group == null || tabKey == null) return;
        // The icon says which tab it was folded in, so the click opens that tab's list: a group a pack folded on
        // several tabs has one icon per tab, and each of them opens its own.
        if (BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tabKey) == null) return;
        // Expanded on the screen's own list, not on the tab's: the tab keeps the folded shape, so leaving and coming
        // back to a page shows every group folded again. The copy is what keeps the refresh below from clearing the
        // list it is handed.
        ItemPickerMenu menu = screen.getMenu();
        List<ItemStack> view = new ArrayList<>(menu.items);
        if (!CreativeGroupService.toggle(view, tabKey, group, CreativeGroupService.members(tabKey, group))) return;
        // The screen's own refresh rather than a plain refill: it remembers the row the player scrolled to and maps it
        // onto the new row count, so opening a group does not throw the list back to the top.
        ((CreativeModeInventoryScreenAccessor) screen).bct$refreshCurrentTabContents(view);
        event.setCanceled(true);
    }

    private CreativeGroupClient() {
    }
}
