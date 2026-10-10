package com.iafenvoy.bct.render;

import com.iafenvoy.bct.registry.BctItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the group icon's own drawing is hung: the decoration is registered against the group item, so it runs for
 * every slot holding one - in the creative screen and anywhere else a stack is drawn.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BctRenderers {
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(BctItems.GROUP.get(), GroupIconDecorator.INSTANCE);
    }

    private BctRenderers() {
    }
}
