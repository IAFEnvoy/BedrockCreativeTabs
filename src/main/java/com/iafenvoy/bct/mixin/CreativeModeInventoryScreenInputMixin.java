package com.iafenvoy.bct.mixin;

import com.iafenvoy.bct.editor.BctPickerOverlay;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes typed characters into the picker overlay's text boxes.
 *
 * <p>Why a mixin and not an event: {@code CreativeModeInventoryScreen} overrides {@code charTyped} and
 * {@code preeditUpdated} <b>without calling super</b> - off the search tab they simply return false. NeoForge's
 * {@code ScreenEvent.CharacterTyped} is posted from {@code Screen.charTyped}, so on this screen it is never reached,
 * and a character never arrives at any other widget. That is exactly the reported "paste works, typing does not"
 * (paste goes through {@code keyPressed}, which the screen does forward to its children). Injecting at the head of
 * the override, before it can drop the event, is the only place that sees the character.
 *
 * <p>The mixin is a thin forwarder only: all the decision-making (mode on, which box holds focus) lives in
 * {@link BctPickerOverlay}, so nothing here touches the overlay's state.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenInputMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void bct$charTyped(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BctPickerOverlay.forwardChar(event)) cir.setReturnValue(true);
    }

    @Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
    private void bct$preedit(PreeditEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BctPickerOverlay.forwardPreedit(event)) cir.setReturnValue(true);
    }
}
