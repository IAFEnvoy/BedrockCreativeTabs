package com.iafenvoy.bct.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The GUI's top-left corner and size, which live on {@code AbstractContainerScreen} and are {@code protected}.
 * The picker overlay anchors its panels to these so they sit under the real slot grid instead of guessing at screen
 * fractions - a separate accessor from the creative-screen one because mixin only finds fields declared on the
 * target class itself, not on its superclass.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int bct$getLeftPos();

    @Accessor("topPos")
    int bct$getTopPos();

    @Accessor("imageWidth")
    int bct$getImageWidth();

    @Accessor("imageHeight")
    int bct$getImageHeight();
}
