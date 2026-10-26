package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.data.BctGroupStore;
import com.iafenvoy.bct.data.CreativeGroupLoader;
import com.iafenvoy.bct.mixin.AbstractContainerScreenAccessor;
import com.iafenvoy.bct.mixin.CreativeModeInventoryScreenAccessor;
import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * The picker's overlay layer, drawn and routed on top of the vanilla creative screen:
 *
 * <ul>
 * <li><b>搜索框</b> (top left) - matches ids and display names; the row under it selects everything the current tab
 * shows that fits, the BCTtools one-click-similar move. EditBox, so IME input just works.</li>
 * <li><b>标签面板</b> (middle-click an item) - that item's item tags; clicking one selects every universe item in
 * it, which is the "group a whole tag from one item" path.</li>
 * <li><b>重命名面板</b> (R on a group icon of your own) - the group's zh/en names, written straight into the
 * writable directory's language files.</li>
 * </ul>
 *
 * <p>Widgets are attached through the init event's listener list (input), but rendered by this class (the screen's
 * renderable pass is closed to outsiders); hidden panels keep their widgets out of sight with the public
 * {@code visible} flag, which also makes the widget refuse clicks. Hit tests run <em>before</em> the mode's own
 * handlers and cancel what they consume; regions never overlap the picker's slots, so the two can coexist without
 * ordering contracts.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BctPickerOverlay {
    private static final int COL_X = 6;
    private static final int COL_W = 106;
    private static final int SEARCH_Y = 6;
    private static final int SEARCH_H = 14;
    private static final int MATCH_ROW_Y = 24;
    private static final int ROW_H = 12;
    private static final int PANEL_W = 170;

    private static EditBox searchBox;
    private static EditBox renameZh;
    private static EditBox renameEn;
    private static CreativeModeInventoryScreen overlayScreen;
    private static boolean renameOpen;
    private static Identifier renamingId;
    private static boolean tagPanelOpen;
    private static List<TagKey<Item>> tagPanel = List.of();
    // Tag list scroll: the panel may hold hundreds of tags, so it shows a window of them with a scrollbar.
    private static int tagScroll;
    private static int tagPanelX;
    private static int tagPanelY;
    private static int tagPanelRows;
    private static int renamePanelX;
    private static int renamePanelY;

    private BctPickerOverlay() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        reset();
        if (!BctEditorState.PICKER_MODE) return;
        overlayScreen = screen;
        Minecraft minecraft = Minecraft.getInstance();
        // Only the side column needs a wide window. Rename popups and item tags remain usable at smaller sizes.
        if (screen.width >= 340) {
            searchBox = new EditBox(minecraft.font, COL_X, SEARCH_Y, COL_W, SEARCH_H,
                    Component.translatable("editor.bedrock_creative_tabs.search"));
            searchBox.setMaxLength(64);
            event.addListener(searchBox);
        }
        renameZh = new EditBox(minecraft.font, 0, 0, PANEL_W - 12, 12,
                Component.translatable("editor.bedrock_creative_tabs.name_zh"));
        renameEn = new EditBox(minecraft.font, 0, 0, PANEL_W - 12, 12,
                Component.translatable("editor.bedrock_creative_tabs.name_en"));
        for (EditBox box : new EditBox[]{renameZh, renameEn}) {
            box.setMaxLength(48);
            box.setVisible(false);
            event.addListener(box);
        }
    }

    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing event) {
        if (event.getScreen() == overlayScreen) reset();
    }

    static void reset() {
        for (EditBox box : new EditBox[]{searchBox, renameZh, renameEn}) {
            if (box == null) continue;
            box.setVisible(false);
            box.setFocused(false);
            if (overlayScreen != null && overlayScreen.getFocused() == box) overlayScreen.setFocused(null);
        }
        searchBox = null;
        renameZh = null;
        renameEn = null;
        overlayScreen = null;
        renameOpen = false;
        renamingId = null;
        tagPanelOpen = false;
        tagPanel = List.of();
        tagScroll = 0;
        tagPanelX = tagPanelY = tagPanelRows = 0;
        renamePanelX = renamePanelY = 0;
    }

    /**
     * Rename panel: bottom-left, clear of the creative GUI which sits centred.
     */
    private static final int RENAME_H = 84;

    private static int renameX(Screen screen) {
        return 6;
    }

    private static int renameY(Screen screen) {
        return screen.height - RENAME_H - 6;
    }

    /**
     * The tag panel: anchored to the creative GUI, spanning its width, sitting in the empty band directly under the
     * slot grid (the area the user marked off). Rows are whatever that band holds, so it never grows past the GUI.
     */
    private static int tagRows(CreativeModeInventoryScreen screen) {
        AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) screen;
        int guiBottom = gui.bct$getTopPos() + gui.bct$getImageHeight();
        int band = screen.height - guiBottom - 6;
        return Math.max(3, Math.min(14, band / ROW_H));
    }

    private static int tagX(CreativeModeInventoryScreen screen) {
        return ((AbstractContainerScreenAccessor) screen).bct$getLeftPos();
    }

    private static int tagY(CreativeModeInventoryScreen screen) {
        AbstractContainerScreenAccessor gui = (AbstractContainerScreenAccessor) screen;
        return gui.bct$getTopPos() + gui.bct$getImageHeight() + 2;
    }

    private static int tagWidth(CreativeModeInventoryScreen screen) {
        return ((AbstractContainerScreenAccessor) screen).bct$getImageWidth();
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!BctEditorState.PICKER_MODE) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (screen != overlayScreen) return;
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft().font;
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (searchBox != null && searchBox.visible) {
            searchBox.extractRenderState(graphics, mouseX, mouseY, event.getPartialTick());
            boolean hover = inRect(mouseX, mouseY, COL_X, MATCH_ROW_Y, COL_W, ROW_H);
            graphics.fill(RenderPipelines.GUI, COL_X, MATCH_ROW_Y, COL_X + COL_W, MATCH_ROW_Y + ROW_H,
                    hover ? 0xff3c8527 : 0xff313233);
            graphics.text(font, Component.translatable("editor.bedrock_creative_tabs.select_matches"),
                    COL_X + 4, MATCH_ROW_Y + 2, 0xffffffff);
        } else if (searchBox == null) {
            int y = SEARCH_Y;
            for (FormattedCharSequence line : font.split(Component.translatable("editor.bedrock_creative_tabs.narrow_window"), screen.width - 12)) {
                graphics.text(font, line, COL_X, y, 0xffffcc55);
                y += font.lineHeight;
            }
        }
        if (renameOpen) drawRename(screen, graphics, mouseX, mouseY);
        if (tagPanelOpen) drawTags(screen, graphics, mouseX, mouseY);
    }

    private static void drawRename(CreativeModeInventoryScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = renamePanelX = renameX(screen);
        int y = renamePanelY = renameY(screen);
        graphics.fill(RenderPipelines.GUI, x, y, x + PANEL_W, y + RENAME_H, 0xE0131314);
        graphics.fill(RenderPipelines.GUI, x, y, x + PANEL_W, y + 1, 0xff7345e5);
        graphics.text(minecraft().font, minecraft().font.plainSubstrByWidth(
                        Component.translatable("editor.bedrock_creative_tabs.rename", renamingId.toString()).getString(), PANEL_W - 10),
                x + 5, y + 3, 0xffe4e5e8);
        renameZh.setX(x + 6);
        renameZh.setY(y + 18);
        renameEn.setX(x + 6);
        renameEn.setY(y + 34);
        renameZh.extractRenderState(graphics, mouseX, mouseY, 0.0F);
        renameEn.extractRenderState(graphics, mouseX, mouseY, 0.0F);
        row(graphics, x + 6, y + 50, PANEL_W - 12, Component.translatable("editor.bedrock_creative_tabs.save"), mouseX, mouseY);
        row(graphics, x + 6, y + 64, PANEL_W - 12, Component.translatable("editor.bedrock_creative_tabs.close"), mouseX, mouseY);
    }

    private static void drawTags(CreativeModeInventoryScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = tagPanelX = tagX(screen);
        int y = tagPanelY = tagY(screen);
        int width = tagWidth(screen);
        int rows = tagPanelRows = tagRows(screen);
        int total = tagPanel.size();
        int maxScroll = Math.max(0, total - rows);
        tagScroll = Math.max(0, Math.min(tagScroll, maxScroll));
        int height = 14 + rows * ROW_H + 4;
        graphics.fill(RenderPipelines.GUI, x, y, x + width, y + height, 0xE0131314);
        graphics.fill(RenderPipelines.GUI, x, y, x + width, y + 1, 0xff3c8527);
        graphics.text(minecraft().font, Component.translatable("editor.bedrock_creative_tabs.tags"),
                x + 5, y + 3, 0xff6cc349);
        for (int index = 0; index < rows && tagScroll + index < total; index++) {
            int rowY = y + 14 + index * ROW_H;
            boolean hover = inRect(mouseX, mouseY, x + 2, rowY, width - 12, ROW_H);
            if (hover) graphics.fill(RenderPipelines.GUI, x + 2, rowY, x + width - 10, rowY + ROW_H, 0x403c8527);
            String label = "#" + tagPanel.get(tagScroll + index).location();
            // Clip long tag ids to the panel so nothing spills off the edge.
            graphics.text(minecraft().font,
                    Component.literal(minecraft().font.plainSubstrByWidth(label, width - 22)),
                    x + 5, rowY + 2, 0xffd0d1d4);
        }
        if (maxScroll > 0) drawScrollbar(graphics, x + width - 8, y + 14, rows * ROW_H, tagScroll, rows, total);
    }

    /**
     * A thin scrollbar on the panel's right edge; thumb size reflects the visible fraction.
     */
    private static void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y, int trackHeight,
                                      int scroll, int rows, int total) {
        graphics.fill(RenderPipelines.GUI, x, y, x + 4, y + trackHeight, 0x30ffffff);
        int thumb = Math.max(8, trackHeight * rows / total);
        int offset = (trackHeight - thumb) * scroll / Math.max(1, total - rows);
        graphics.fill(RenderPipelines.GUI, x, y + offset, x + 4, y + offset + thumb, 0xff9a9da3);
    }

    private static void row(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        boolean hover = inRect(mouseX, mouseY, x, y, width, ROW_H);
        graphics.fill(RenderPipelines.GUI, x, y, x + width, y + ROW_H, hover ? 0xff3c8527 : 0xff313233);
        graphics.text(minecraft().font, label, x + 4, y + 2, 0xffffffff);
    }

    @SubscribeEvent
    public static void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!BctEditorState.PICKER_MODE) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (screen != overlayScreen) return;
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();

        // A click anywhere but the search box or its match button ends the typing session, so the mode's shortcut
        // keys are live again. The click still falls through to the picker/vanilla, which is what selects items.
        if (searchBox != null && searchBox.isFocused()
                && !inRect(mouseX, mouseY, COL_X, SEARCH_Y, COL_W, SEARCH_H)
                && !inRect(mouseX, mouseY, COL_X, MATCH_ROW_Y, COL_W, ROW_H)) {
            blur(searchBox);
        }

        if (renameOpen) {
            int x = renamePanelX;
            int y = renamePanelY;
            if (inRect(mouseX, mouseY, x + 6, y + 50, PANEL_W - 12, ROW_H)) {
                saveRename();
                event.setCanceled(true);
                return;
            }
            if (inRect(mouseX, mouseY, x + 6, y + 64, PANEL_W - 12, ROW_H)) {
                closeRename();
                event.setCanceled(true);
                return;
            }
            if (inRect(mouseX, mouseY, x, y, PANEL_W, RENAME_H)) return; // inside the panel: the boxes take it
            closeRename();
        }

        if (tagPanelOpen) {
            int x = tagPanelX;
            int y = tagPanelY;
            int width = tagWidth(screen);
            int height = 14 + tagPanelRows * ROW_H + 4;
            if (inRect(mouseX, mouseY, x, y, width, height)) {
                // Ignore clicks on the scrollbar gutter; map the rest onto the visible window.
                int row = (mouseY - (y + 14)) / ROW_H;
                if (mouseX < x + width - 8 && row >= 0 && row < tagPanelRows) {
                    int index = tagScroll + row;
                    if (index >= 0 && index < tagPanel.size()) selectTag(tagPanel.get(index), minecraft());
                }
                event.setCanceled(true);
                return;
            }
            tagPanelOpen = false; // a click outside the tag panel dismisses it
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            Slot slot = screen.getHoveredSlot();
            ItemStack hovered = slot == null || slot.index >= 45 ? ItemStack.EMPTY : slot.getItem();
            if (hovered.isEmpty() || hovered.has(BctDataComponents.CREATIVE_GROUP_ID)) return;
            Set<TagKey<Item>> tags = new TreeSet<>(Comparator.comparing(a -> a.location().toString()));
            BuiltInRegistries.ITEM.wrapAsHolder(hovered.getItem()).tags().forEach(tags::add);
            tagPanel = List.copyOf(tags);
            tagPanelOpen = true;
            tagScroll = 0;
            minecraft().player.sendOverlayMessage(tags.isEmpty()
                    ? Component.translatable("editor.bedrock_creative_tabs.no_tags")
                    : Component.translatable("editor.bedrock_creative_tabs.tag_count", tags.size()));
            event.setCanceled(true);
            return;
        }

        if (searchBox != null && searchBox.visible && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inRect(mouseX, mouseY, COL_X, MATCH_ROW_Y, COL_W, ROW_H)) {
            selectMatching(screen);
            event.setCanceled(true);
        }
    }

    /**
     * Scroll wheel pages the tag list when the cursor is over it.
     */
    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!BctEditorState.PICKER_MODE || !tagPanelOpen) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (screen != overlayScreen) return;
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();
        int height = 14 + tagPanelRows * ROW_H + 4;
        if (!inRect(mouseX, mouseY, tagPanelX, tagPanelY, tagWidth(screen), height)) return;
        int maxScroll = Math.max(0, tagPanel.size() - tagPanelRows);
        if (maxScroll == 0) return;
        tagScroll = Math.max(0, Math.min(maxScroll, tagScroll - (int) Math.signum(event.getScrollDeltaY())));
        event.setCanceled(true);
    }

    /**
     * Keys on the overlay's own widgets. Typing guards live in BctEditorMode for the mode's keys.
     */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!BctEditorState.PICKER_MODE) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (screen != overlayScreen) return;
        if (!(screen.getFocused() instanceof EditBox focused)) return;
        if (!isOverlayBox(focused)) return;
        if (event.getKeyCode() != GLFW.GLFW_KEY_ENTER && event.getKeyCode() != GLFW.GLFW_KEY_KP_ENTER) return;
        if (focused == searchBox) selectMatching(screen);
        else if (renameOpen) saveRename();
        event.setCanceled(true);
    }

    /**
     * R on one of your own group icons opens the rename panel.
     */
    @SubscribeEvent
    public static void onRenameKey(ScreenEvent.KeyPressed.Pre event) {
        if (!BctEditorState.PICKER_MODE || renameOpen) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        if (screen != overlayScreen || renameZh == null || renameEn == null) return;
        if (event.getKeyCode() != GLFW.GLFW_KEY_R) return;
        if (screen.getFocused() instanceof EditBox) return;
        Minecraft minecraft = Minecraft.getInstance();
        Slot slot = screen.getHoveredSlot();
        ItemStack hovered = slot == null || slot.index >= 45 ? ItemStack.EMPTY : slot.getItem();
        Identifier id = hovered.get(BctDataComponents.CREATIVE_GROUP_ID);
        if (id == null || !CreativeGroupLoader.isUser(id)) {
            minecraft.player.sendOverlayMessage(Component.translatable("editor.bedrock_creative_tabs.rename_hover_own"));
            return;
        }
        renamingId = id;
        renameZh.setValue(BctGroupStore.nameIn(id, "zh_cn") == null ? "" : BctGroupStore.nameIn(id, "zh_cn"));
        renameEn.setValue(BctGroupStore.nameIn(id, "en_us") == null ? "" : BctGroupStore.nameIn(id, "en_us"));
        renameZh.setVisible(true);
        renameEn.setVisible(true);
        renameOpen = true;
        int x = renamePanelX = renameX(screen);
        int y = renamePanelY = renameY(screen);
        renameZh.setX(x + 6);
        renameZh.setY(y + 18);
        renameEn.setX(x + 6);
        renameEn.setY(y + 34);
        screen.setFocused(renameZh);
        renameZh.setHighlightPos(renameZh.getValue().length());
    }

    private static void saveRename() {
        if (!renameOpen || renamingId == null || renameZh == null || renameEn == null) return;
        Identifier id = renamingId;
        BctGroupStore.rename(id, renameZh.getValue().isBlank() ? null : renameZh.getValue(),
                renameEn.getValue().isBlank() ? null : renameEn.getValue());
        CreativeGroupService.onGroupsReloaded();
        closeRename();
        minecraft().player.sendOverlayMessage(Component.translatable("editor.bedrock_creative_tabs.name_saved", id.toString()));
    }

    private static void closeRename() {
        renameOpen = false;
        renamingId = null;
        if (renameZh != null) renameZh.setVisible(false);
        if (renameEn != null) renameEn.setVisible(false);
        blur(renameZh);
        blur(renameEn);
    }

    private static void selectMatching(CreativeModeInventoryScreen screen) {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            minecraft().player.sendOverlayMessage(Component.translatable("editor.bedrock_creative_tabs.search_empty"));
            return;
        }
        CreativeModeTab tab = CreativeModeInventoryScreenAccessor.bct$getSelectedTab();
        ResourceKey<CreativeModeTab> tabKey = null;
        if (tab != null) {
            for (Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> entry
                    : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
                if (entry.getValue() == tab) {
                    tabKey = entry.getKey();
                    break;
                }
            }
        }
        List<ItemStack> scope = tabKey == null ? CreativeGroupService.universe()
                : CreativeGroupService.universe(tabKey);
        int added = 0;
        for (ItemStack stack : scope) {
            if (stack.isEmpty() || stack.has(BctDataComponents.CREATIVE_GROUP_ID)) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) continue;
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (id.toString().contains(query) || name.contains(query)) {
                if (BctEditorState.CHOSEN.add(id.toString())) added++;
            }
        }
        // End the typing session: release the box so the keyboard goes back to the mode's shortcuts. Without this
        // the search box kept focus after selecting, and the next G/C/R a player pressed was typed into it (the mode
        // keys stand down while a box is focused) instead of folding a group.
        blur(searchBox);
        minecraft().player.sendOverlayMessage(Component.translatable(
                "editor.bedrock_creative_tabs.matches_selected", query, added, BctEditorState.CHOSEN.size()));
    }

    private static void selectTag(TagKey<Item> tag, Minecraft minecraft) {
        int added = 0;
        for (ItemStack stack : CreativeGroupService.universe()) {
            if (stack.isEmpty() || !stack.is(tag)) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (BctEditorState.CHOSEN.add(id.toString())) added++;
        }
        minecraft.player.sendOverlayMessage(Component.translatable(
                "editor.bedrock_creative_tabs.tag_selected", tag.location().toString(), added, BctEditorState.CHOSEN.size()));
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Drops focus from a box, so the mode's shortcut keys answer again.
     */
    private static void blur(EditBox box) {
        if (box == null) return;
        box.setFocused(false);
        Screen screen = minecraft().screen;
        if (screen != null && screen.getFocused() == box) screen.setFocused(null);
    }

    /**
     * Forwards a typed character to the focused overlay box, called from the screen's {@code charTyped} mixin.
     *
     * <p>This is the fix for "paste works, typing does not": the creative screen overrides {@code charTyped} and
     * does <b>not</b> call super - on any tab other than the search tab it returns false without ever offering the
     * event to the screen's widgets, so a character never reaches our EditBox and NeoForge's screen event (posted
     * from {@code Screen.charTyped}) never fires. Paste uses {@code keyPressed}, which the screen does forward,
     * which is why only paste worked.
     */
    public static boolean forwardChar(CharacterEvent event) {
        if (!BctEditorState.PICKER_MODE) return false;
        if (!(minecraft().screen instanceof CreativeModeInventoryScreen screen)) return false;
        if (screen != overlayScreen) return false;
        if (!(screen.getFocused() instanceof EditBox box)) return false;
        if (!isOverlayBox(box) || !box.visible) return false;
        return box.charTyped(event);
    }

    /**
     * IME preedit text (e.g. composing Chinese) is dropped by the same vanilla override - forward it too.
     */
    public static boolean forwardPreedit(PreeditEvent event) {
        if (!BctEditorState.PICKER_MODE) return false;
        if (!(minecraft().screen instanceof CreativeModeInventoryScreen screen)) return false;
        if (screen != overlayScreen) return false;
        if (!(screen.getFocused() instanceof EditBox box)) return false;
        if (!isOverlayBox(box) || !box.visible) return false;
        return box.preeditUpdated(event);
    }

    private static boolean isOverlayBox(EditBox box) {
        return box == searchBox || box == renameZh || box == renameEn;
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
