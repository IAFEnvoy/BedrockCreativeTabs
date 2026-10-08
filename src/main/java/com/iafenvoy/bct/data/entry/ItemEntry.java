package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One item, by id: {@code {"type": "item", "id": "minecraft:oak_planks"}}, or the bare id as shorthand. It reads the
 * item alone, so it claims every stack of it whatever components it carries.
 */
public record ItemEntry(Item item) implements GroupEntry {
    public static final MapCodec<ItemEntry> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("id")
            .xmap(ItemEntry::new, ItemEntry::item);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(this.item);
    }

    @Override
    public String describe() {
        return BuiltInRegistries.ITEM.getKey(this.item).toString();
    }

    @Override
    public MapCodec<ItemEntry> codec() {
        return CODEC;
    }
}
