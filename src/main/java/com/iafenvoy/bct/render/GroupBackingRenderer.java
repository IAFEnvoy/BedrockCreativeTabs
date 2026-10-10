package com.iafenvoy.bct.render;

import com.iafenvoy.bct.util.BctIconTags;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The backing an opened group is painted on: a translucent black square under its icon and each of its members, so an
 * expanded group reads as one block. A folded group needs no rule of its own - its members are not on the list.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GroupBackingRenderer {
    private static final int BACKING = 0x40000000; // Black at a quarter alpha.

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        List<ItemStack> view = screen.getMenu().items;
        Set<ItemStack> backed = backed(view);
        if (backed.isEmpty()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        for (Slot slot : screen.getMenu().slots) {
            if (!backed.contains(slot.getItem())) continue;
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            graphics.fill(x, y, x + 16, y + 16, BACKING);
        }
    }

    // The icon and members of every open group. Looked up by identity: a screen holds the very stacks the fold took
    // off the tab, not copies.
    private static Set<ItemStack> backed(List<ItemStack> view) {
        Set<ItemStack> backed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack stack : view) {
            ResourceLocation groupId = BctIconTags.groupId(stack);
            if (groupId == null || !CreativeGroupService.isOpen(view, stack)) continue;
            ResourceKey<CreativeModeTab> tab = BctIconTags.tab(stack);
            backed.add(stack);
            backed.addAll(CreativeGroupService.members(tab, groupId));
        }
        return backed;
    }

    private GroupBackingRenderer() {
    }
}
