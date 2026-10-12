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
                            context.getSource().sendSuccess(() -> Component.literal("BCT 选择模式已开启。"), false);
                            return 1;
                        }))
                .then(Commands.literal("export")
                        .executes(context -> {
                            Path file = BctGroupStore.exportPack();
                            if (file == null) {
                                context.getSource().sendFailure(Component.literal("导出失败，见日志。"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "已导出 " + file + " · " + BctGroupStore.countGroups() + " 个分组 · F3+T 后在资源包列表启用。"), false);
                            return 1;
                        })));
    }

    private BctEditorCommands() {
    }
}
