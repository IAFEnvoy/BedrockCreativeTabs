package com.iafenvoy.bct.util;

import com.iafenvoy.bct.BedrockCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The identity an icon carries. 1.20.1 has no data components, so it lives in the stack's tag - which is also what a
 * tab's entry set compares, so two icons of the same item stay apart as long as their tags differ.
 *
 * <p>The slot lock is the platform's own tag rather than one of ours: the creative screen already refuses to hand back
 * any stack carrying {@code CustomCreativeLock}, so the icon is unpickable without this mod drawing anything.
 */
public final class BctIconTags {
    public static final String ROOT = BedrockCreativeTabs.MOD_ID;
    public static final String GROUP_ID = "group";
    public static final String TAB = "tab";
    public static final String LOCK = "CustomCreativeLock";

    /**
     * Marks a stack as one group's icon on one tab. The group definition itself is deliberately not written: it lives
     * in the client's resource packs, so a stored copy could outlive the file that defined it.
     */
    public static void write(ItemStack stack, ResourceKey<CreativeModeTab> tab, ResourceLocation groupId) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT);
        root.putString(GROUP_ID, groupId.toString());
        root.putString(TAB, tab.location().toString());
        stack.getOrCreateTagElement(LOCK);
    }

    /**
     * The group this stack is an icon of, or null when it is not an icon at all.
     */
    public static ResourceLocation groupId(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT);
        if (root == null || !root.contains(GROUP_ID, Tag.TAG_STRING)) return null;
        return ResourceLocation.tryParse(root.getString(GROUP_ID));
    }

    /**
     * The tab this icon was folded in, or null when it is not an icon.
     */
    public static ResourceKey<CreativeModeTab> tab(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT);
        if (root == null || !root.contains(TAB, Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(root.getString(TAB));
        return id == null ? null : ResourceKey.create(Registries.CREATIVE_MODE_TAB, id);
    }

    private BctIconTags() {
    }
}
