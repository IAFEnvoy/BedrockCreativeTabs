package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One item tag: {@code {"type": "item_tag", "tag": "minecraft:planks"}} or {@code "#minecraft:planks"} as shorthand.
 * Read when the tab is built, so another pack adding to the tag changes the group.
 */
public record ItemTagEntry(TagKey<Item> tag) implements GroupEntry {
    public static final MapCodec<ItemTagEntry> CODEC = TagKey.codec(Registries.ITEM).fieldOf("tag").xmap(ItemTagEntry::new, ItemTagEntry::tag);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(this.tag);
    }

    @Override
    public String describe() {
        return "#" + this.tag.location();
    }

    @Override
    public MapCodec<ItemTagEntry> codec() {
        return CODEC;
    }
}
