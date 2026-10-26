package com.iafenvoy.bct.editor;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development-only hooks that live in {@code <gameDir>/apricity/bct_editor/}; ignored in production:
 *
 * <ul>
 * <li>{@code autopen} - enters the picker once, as soon as the player is in a world with nothing else on screen.</li>
 * <li>{@code selftest} - runs the naming self-check once and logs the results (a file trigger no input path can eat,
 * which is what makes the flow scriptable for verification).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(Dist.CLIENT)
public final class BctEditorDevAutoOpen {
    private static final String DIR = "apricity/bct_editor";
    private static boolean opened;
    private static boolean selfTested;

    /**
     * Forge 47 has no {@code ClientTickEvent.Post} - the client tick is one event with a phase, and this work belongs
     * after the tick so a screen opened here is not immediately overwritten by that tick's own screen handling.
     */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Both lines expose the flag as a field; the isProduction() method only exists in later loader versions.
        if (FMLEnvironment.production) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        Path dir = minecraft.gameDirectory.toPath().resolve(DIR);
        if (!selfTested && Files.exists(dir.resolve("selftest"))) {
            selfTested = true;
            BctNameSelfTest.run();
        }
        if (opened || minecraft.screen != null) return;
        if (!Files.exists(dir.resolve("autopen"))) return;
        opened = true;
        BctEditorMode.enterPicker(minecraft);
    }

    private BctEditorDevAutoOpen() {
    }
}