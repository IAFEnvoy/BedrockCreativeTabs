package com.iafenvoy.bct.render;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * What a folded group's icon shows: the stack the group stands for, plus the open/closed mark. Runs in the platform's
 * own item decoration pass, so it draws exactly where a slot's other decorations do.
 *
 * <p>The group's own item stays blank - its model is a transparent texture. A stack carrying a group that has no
 * members on this side (one from {@code /give}, say) has nothing to show and draws nothing.
 */
public enum GroupIconDecorator implements IItemDecorator {
    INSTANCE;
    private static final ResourceLocation COLLAPSED = ResourceLocation.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "textures/gui/creative_group_collapsed.png");
    private static final ResourceLocation EXPANDED = ResourceLocation.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "textures/gui/creative_group_expanded.png");
    // Past the z=150 an item is drawn at, so the mark sits on the icon rather than under it.
    private static final int MARK_Z = 200;

    @Override
    public boolean render(@NotNull GuiGraphics graphics, @NotNull Font font, ItemStack stack, int x, int y) {
        ResourceLocation groupId = stack.get(BctDataComponents.CREATIVE_GROUP_ID);
        ResourceKey<CreativeModeTab> tab = stack.get(BctDataComponents.CREATIVE_GROUP_TAB);
        if (groupId == null || tab == null || CreativeGroupService.members(tab, groupId).isEmpty()) return false;
        ItemStack icon = CreativeGroupService.displayIcon(stack);
        if (!icon.isEmpty()) graphics.renderItem(icon, x, y);
        // renderItem only batches the item, while blit draws immediately - so without this the mark is submitted
        // before the item ever reaches the screen and ends up underneath it.
        graphics.flush();
        ResourceLocation mark = CreativeGroupService.isOpen(shown(), stack) ? EXPANDED : COLLAPSED;
        graphics.blit(mark, x, y, MARK_Z, 0.0F, 0.0F, 16, 16, 16, 16);
        // Neither draw touches the render state, so nothing has to be reset for the next decorator.
        return false;
    }

    // Where a group is expanded. An icon drawn anywhere else, or with no creative screen open, is folded.
    private static List<ItemStack> shown() {
        return Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen ? screen.getMenu().items : List.of();
    }
}
