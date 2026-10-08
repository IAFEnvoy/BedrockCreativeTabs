package com.iafenvoy.bct.render;

import com.iafenvoy.bct.registry.BctItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Where the group icon's own drawing is hung: the decoration is registered against the group item, so it runs for
 * every slot holding one - in the creative screen and anywhere else a stack is drawn.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BctRenderers {
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(BctItems.GROUP.get(), GroupIconDecorator.INSTANCE);
    }

    private BctRenderers() {
    }
}
