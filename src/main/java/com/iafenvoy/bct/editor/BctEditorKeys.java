package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The picker mode's keybind. Keybinds only fire while no screen is open; the keys that matter <em>inside</em> the
 * creative screen (B/G/C/Delete) are read by {@link BctEditorMode} from the screen event instead.
 */
@EventBusSubscriber(modid = BedrockCreativeTabs.MOD_ID, value = Dist.CLIENT)
public final class BctEditorKeys {
    /** Label key: {@code key.category.bedrock_creative_tabs.editor}. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "editor"));
    public static final KeyMapping TOGGLE_PICKER = new KeyMapping(
            "key.bedrock_creative_tabs.picker",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    private BctEditorKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_PICKER);
    }
}
