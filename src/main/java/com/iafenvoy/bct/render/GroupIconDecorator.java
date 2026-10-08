package com.iafenvoy.bct.render;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.data.CreativeGroup;
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
 * What a folded group's icon shows: the stack the group stands for, and the mark that says whether it is open. The
 * platform's own decoration pass runs this right after the item of every slot, and it is handed the slot's own
 * position, so the icon is drawn by the same item pass a slot uses and the mark lands in the same place as the item's
 * other decorations. The backing an opened group sits on is not here: see {@link GroupBackingRenderer}.
 *
 * <p>The group's own item therefore stays blank: its model is a transparent texture, because this draws the member or
 * the definition's icon on top of it. A stack that carries a group but has no members on this side - one handed out by
 * {@code /give}, or a group a tab this fold never ran for holds - has nothing to show and gets neither.
 */
public enum GroupIconDecorator implements IItemDecorator {
    INSTANCE;

    private static final Identifier COLLAPSED = Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "textures/gui/creative_group_collapsed.png");
    private static final Identifier EXPANDED = Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "textures/gui/creative_group_expanded.png");

    @Override
    public boolean render(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, ItemStack stack, int x, int y) {
        CreativeGroup group = stack.get(BctDataComponents.CREATIVE_GROUP);
        ResourceKey<CreativeModeTab> tab = stack.get(BctDataComponents.CREATIVE_GROUP_TAB);
        if (group == null || tab == null || CreativeGroupService.members(tab, group).isEmpty()) return false;
        ItemStack icon = CreativeGroupService.displayIcon(stack);
        if (!icon.isEmpty()) graphics.item(icon, x, y);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CreativeGroupService.isOpen(shown(), stack) ? EXPANDED : COLLAPSED,
                x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        // Neither draw touches the render state, so nothing has to be reset for the next decorator.
        return false;
    }

    // The list the creative screen is showing, which is where a group is expanded. An icon drawn anywhere else - or
    // with no creative screen open - is folded by definition.
    private static List<ItemStack> shown() {
        return Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen
                ? screen.getMenu().items : List.of();
    }
}
