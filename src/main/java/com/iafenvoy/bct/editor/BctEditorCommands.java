package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.data.BctGroupStore;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.Path;

/**
 * {@code /bct edit} - enters the picker mode (the vanilla creative screen with click-to-select), which is also what
 * makes the flow scriptable for verification: a command is an input path no IME fix can eat.
 * {@code /bct export} - zips the writable directory straight into resourcepacks.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BctEditorCommands {
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bct")
                .then(Commands.literal("edit")
                        .executes(context -> {
                            BctEditorMode.enterPicker(Minecraft.getInstance());
                            context.getSource().sendSuccess(() -> Component.translatable("editor.bedrock_creative_tabs.command_enabled"), false);
                            return 1;
                        }))
                .then(Commands.literal("export")
                        .executes(context -> {
                            Path file = BctGroupStore.exportPack();
                            if (file == null) {
                                context.getSource().sendFailure(Component.translatable("editor.bedrock_creative_tabs.export_failed"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "editor.bedrock_creative_tabs.exported", file.toString(), BctGroupStore.countGroups()), false);
                            return 1;
                        })));
    }

    private BctEditorCommands() {
    }
}
