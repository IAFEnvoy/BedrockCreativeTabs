package com.iafenvoy.bct.render;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import org.jspecify.annotations.NonNull;

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
    private static final Identifier COLLAPSED = Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "textures/gui/creative_group_collapsed.png");
    private static final Identifier EXPANDED = Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "textures/gui/creative_group_expanded.png");

    @Override
    public boolean render(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, ItemStack stack, int x, int y) {
        Identifier groupId = stack.get(BctDataComponents.CREATIVE_GROUP_ID);
        ResourceKey<CreativeModeTab> tab = stack.get(BctDataComponents.CREATIVE_GROUP_TAB);
        if (groupId == null || tab == null || CreativeGroupService.members(tab, groupId).isEmpty()) return false;
        ItemStack icon = CreativeGroupService.displayIcon(stack);
        if (!icon.isEmpty()) graphics.item(icon, x, y);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CreativeGroupService.isOpen(shown(), stack) ? EXPANDED : COLLAPSED, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        // Neither draw touches the render state, so nothing has to be reset for the next decorator.
        return false;
    }

    // Where a group is expanded. An icon drawn anywhere else, or with no creative screen open, is folded.
    private static List<ItemStack> shown() {
        return Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen ? screen.getMenu().items : List.of();
    }
}
