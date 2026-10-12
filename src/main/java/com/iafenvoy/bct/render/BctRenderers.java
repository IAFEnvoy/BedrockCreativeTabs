package com.iafenvoy.bct.render;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.editor.BctEditorState;
import com.iafenvoy.bct.registry.BctItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Where the group icon's own drawing is hung: the decoration is registered against the group item, so it runs for
 * every slot holding one - in the creative screen and anywhere else a stack is drawn.
 *
 * <p>The same pass carries {@link SelectedMarker}, the picker mode's green selection mark: it decorates every
 * registered item, does one set lookup while the mode is off, and paints only on the chosen ones - which is what
 * makes the vanilla creative screen work as the picker without touching its rendering pipeline.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BctRenderers {
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(BctItems.GROUP.get(), GroupIconDecorator.INSTANCE);
        IItemDecorator marker = SelectedMarker.INSTANCE;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == BctItems.GROUP.get()) continue; // the group icon keeps its own decorator
            event.register(item, marker);
        }
    }

    private BctRenderers() {
    }

    /** The picker mode's selection mark: a green wash plus edge on every chosen stack, in any screen. */
    public enum SelectedMarker implements IItemDecorator {
        INSTANCE;
        private static final int WASH = 0x583c8527;
        private static final int EDGE = 0xff6cc349;

        @Override
        public boolean render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
            if (!BctEditorState.PICKER_MODE) return false;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !BctEditorState.CHOSEN.contains(id.toString())) return false;
            graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, WASH);
            graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 1, EDGE);
            graphics.fill(RenderPipelines.GUI, x, y + 15, x + 16, y + 16, EDGE);
            graphics.fill(RenderPipelines.GUI, x, y, x + 1, y + 16, EDGE);
            graphics.fill(RenderPipelines.GUI, x + 15, y, x + 16, y + 16, EDGE);
            return false;
        }
    }
}
