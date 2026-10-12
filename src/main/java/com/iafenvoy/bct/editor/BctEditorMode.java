package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.data.BctGroupStore;
import com.iafenvoy.bct.data.CreativeGroupLoader;
import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The picker mode: the <b>vanilla creative screen</b> is the item surface, not AUI. The vanilla screen renders
 * every item - modded ones included - through the game's own, already-warm pipeline (the AUI grid's per-icon first
 * draw was what froze tab switches for seconds), and its tabs and search come for free.
 *
 * <p>The whole flow is native now:
 * <ul>
 * <li><b>click</b> - toggle the hovered item's selection, painted by {@code BctRenderers.SelectedMarker};</li>
 * <li><b>right-drag</b> - sweep-select: whatever the cursor passes over joins the selection, in the direction set
 * by the first item touched (sweeping adds, sweeping over selected ones removes);</li>
 * <li><b>Enter</b> - batch-select everything the screen is currently showing (the search tab's filtered list, or a
 * whole tab), by id or display name - whatever vanilla already filtered for;</li>
 * <li><b>G</b> - fold the selection into a group ({@link BctGroupCreator}), visible before the key pops back;</li>
 * <li><b>C</b> - clear the selection; <b>Delete</b> - delete the hovered group if it is the player's own;</li>
 * <li><b>H</b> - toggle the keybind panel (top left); <b>B</b> - leave the mode.</li>
 * </ul>
 *
 * <p>Keybinds do not fire while a screen is open, so the in-screen keys are read from the screen key event - with
 * one guard: while a text box (the search field) has focus, none of them apply, or typing a query would fold groups.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BctEditorMode {
    // The picker menu adds its own 45 slots before the player's hotbar - clicking the hotbar must not select.
    private static final int PICKER_SLOTS = 45;
    // The keybind panel's geometry, top-left of the screen.
    private static final int PANEL_X = 4;
    private static final int PANEL_Y = 40;
    private static final int PANEL_W = 132;
    private static final int LINE = 12;

    private static boolean helpVisible = true;
    // Right-drag sweep state: the first item decides whether the sweep adds or removes.
    private static boolean sweeping;
    private static int sweepDirection;
    private static Identifier lastSwept;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (BctEditorKeys.TOGGLE_PICKER.consumeClick()) toggle(minecraft);
        // The screen died some other way (Esc, a menu): the green marks must not linger on nothing.
        if (BctEditorState.PICKER_MODE && !(minecraft.screen instanceof CreativeModeInventoryScreen)) {
            BctEditorState.PICKER_MODE = false;
        }
    }

    private static void toggle(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;
        if (BctEditorState.PICKER_MODE && minecraft.screen instanceof CreativeModeInventoryScreen) {
            BctEditorState.PICKER_MODE = false;
            minecraft.player.sendOverlayMessage(Component.literal("BCT 选择模式已关闭"));
            return;
        }
        enterPicker(minecraft);
    }

    /** Also the entry the command and the dev auto-open use. Call on the client thread. */
    public static void enterPicker(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;
        BctEditorState.PICKER_MODE = true;
        BedrockCreativeTabs.LOGGER.info("BCT picker mode on (screen was {})",
                minecraft.screen == null ? "<none>" : minecraft.screen.getClass().getSimpleName());
        minecraft.setScreen(new CreativeModeInventoryScreen(minecraft.player,
                minecraft.level.enabledFeatures(), minecraft.options.operatorItemsTab().get()));
        minecraft.player.sendOverlayMessage(Component.literal(
                "BCT 选择模式：点击/右键拖选 · 回车批量 · G 创建 · H 快捷键表 · B 退出"));
    }

    /** In-screen keys. Text boxes (the search field) swallow these first - never fold a group mid-typing. */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!BctEditorState.PICKER_MODE) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (screen.getFocused() instanceof EditBox) return;
        Minecraft minecraft = Minecraft.getInstance();
        int key = event.getKeyCode();
        if (key == GLFW.GLFW_KEY_B) {
            BctEditorState.PICKER_MODE = false;
            minecraft.player.sendOverlayMessage(Component.literal("BCT 选择模式已关闭"));
        } else if (key == GLFW.GLFW_KEY_G) {
            minecraft.player.sendOverlayMessage(Component.literal(BctGroupCreator.createFromSelection()));
        } else if (key == GLFW.GLFW_KEY_C) {
            BctEditorState.CHOSEN.clear();
            minecraft.player.sendOverlayMessage(Component.literal("已清空选择"));
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            deleteHovered(minecraft, screen);
        } else if (key == GLFW.GLFW_KEY_H) {
            helpVisible = !helpVisible;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            batchSelectShown(minecraft, screen);
        }
    }

    /**
     * Selects everything the screen is showing right now: on the search tab that is exactly what the query filtered
     * to (vanilla matches ids and names for us), on a plain tab it is that tab's whole content.
     */
    private static void batchSelectShown(Minecraft minecraft, CreativeModeInventoryScreen screen) {
        int added = 0;
        for (ItemStack stack : screen.getMenu().items) {
            if (stack.isEmpty() || stack.has(BctDataComponents.CREATIVE_GROUP_ID)) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && BctEditorState.CHOSEN.add(id.toString())) added++;
        }
        minecraft.player.sendOverlayMessage(Component.literal(
                "批量选中 " + added + " 个物品（共 " + BctEditorState.CHOSEN.size() + " 个）"));
    }

    private static void deleteHovered(Minecraft minecraft, CreativeModeInventoryScreen screen) {
        Slot slot = screen.getHoveredSlot();
        ItemStack hovered = slot == null ? ItemStack.EMPTY : slot.getItem();
        Identifier id = hovered.get(BctDataComponents.CREATIVE_GROUP_ID);
        if (id == null || !CreativeGroupLoader.isUser(id)) {
            minecraft.player.sendOverlayMessage(Component.literal("悬停的不是自己的分组图标，无法删除"));
            return;
        }
        BctGroupStore.delete(id);
        CreativeGroupLoader.removeUser(id);
        CreativeGroupService.onGroupsReloaded();
        minecraft.player.sendOverlayMessage(Component.literal("已删除 " + id));
    }

    /**
     * Click = toggle the hovered item's selection; right-press starts a sweep. Group icon clicks stay with the fold.
     * Middle clicks belong to the overlay's tag panel - this handler must neither act on nor cancel them, or the
     * panel never opens (a canceled event stops the later listeners too).
     */
    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!BctEditorState.PICKER_MODE) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        Minecraft minecraft = Minecraft.getInstance();
        Slot slot = screen.getHoveredSlot();
        if (slot == null || slot.index >= PICKER_SLOTS) return;
        ItemStack clicked = slot.getItem();
        if (clicked.isEmpty() || clicked.has(BctDataComponents.CREATIVE_GROUP_ID)) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(clicked.getItem());
        if (id == null) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // The first item under the sweep decides its direction: sweeping adds, sweeping over selected removes.
            sweeping = true;
            sweepDirection = BctEditorState.CHOSEN.contains(id.toString()) ? -1 : 1;
            lastSwept = null;
            sweepTo(id, minecraft);
        } else {
            toggleOne(id, minecraft);
        }
        event.setCanceled(true);
    }

    /** Sweep-select: every slot the cursor crosses while the right button is held joins the sweep. */
    @SubscribeEvent
    public static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!BctEditorState.PICKER_MODE || !sweeping) return;
        if (event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        Slot slot = screen.getHoveredSlot();
        ItemStack hovered = slot == null ? ItemStack.EMPTY : slot.getItem();
        if (hovered.isEmpty() || hovered.has(BctDataComponents.CREATIVE_GROUP_ID)) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(hovered.getItem());
        if (id != null) sweepTo(id, minecraft());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) sweeping = false;
    }

    private static void sweepTo(Identifier id, Minecraft minecraft) {
        if (id.equals(lastSwept)) return;
        lastSwept = id;
        if (sweepDirection > 0) BctEditorState.CHOSEN.add(id.toString());
        else BctEditorState.CHOSEN.remove(id.toString());
        minecraft.player.sendOverlayMessage(Component.literal("已选 " + BctEditorState.CHOSEN.size() + " 个物品"));
    }

    private static void toggleOne(Identifier id, Minecraft minecraft) {
        if (!BctEditorState.CHOSEN.remove(id.toString())) BctEditorState.CHOSEN.add(id.toString());
        minecraft.player.sendOverlayMessage(Component.literal("已选 " + BctEditorState.CHOSEN.size() + " 个物品"));
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    /** The keybind panel, top left: every key the mode answers, toggled with H. */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!BctEditorState.PICKER_MODE || !helpVisible) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft().font;
        String[] lines = {
                "BCT 选择模式 (H 收起)",
                "点击 选中 / 取消",
                "右键拖动 批量框选",
                "回车 批量选中当前列表",
                "中键 物品标签列表",
                "R 重命名悬停的组",
                "G 创建分组",
                "C 清空选择",
                "Delete 删除悬停的组",
                "B 退出"
        };
        int height = 8 + lines.length * LINE;
        graphics.fill(RenderPipelines.GUI, PANEL_X, PANEL_Y, PANEL_X + PANEL_W, PANEL_Y + height, 0xB0131314);
        graphics.fill(RenderPipelines.GUI, PANEL_X, PANEL_Y, PANEL_X + 1, PANEL_Y + height, 0xff3c8527);
        for (int index = 0; index < lines.length; index++) {
            int color = index == 0 ? 0xff6cc349 : 0xffe4e5e8;
            graphics.text(font, Component.literal(lines[index]), PANEL_X + 6, PANEL_Y + 4 + index * LINE, color);
        }
    }

    private BctEditorMode() {
    }
}
