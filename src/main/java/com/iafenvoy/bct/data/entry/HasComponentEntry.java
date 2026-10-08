package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Any stack carrying a given component: {@code {"type": "has_component", "component": "minecraft:enchantments"}}.
 * Unlike the other types this reads the stack rather than the item, which is the only way to say "every enchanted
 * book" - the same item differing only in components.
 *
 * <p>Tests presence, not value, so {@code minecraft:damage} claims an undamaged stack too. Item-agnostic on purpose.
 */
public record HasComponentEntry(DataComponentType<?> component) implements GroupEntry {
    public static final MapCodec<HasComponentEntry> CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().fieldOf("component").xmap(HasComponentEntry::new, HasComponentEntry::component);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.has(this.component);
    }

    @Override
    public String describe() {
        return "has_component{" + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(this.component) + "}";
    }

    @Override
    public MapCodec<HasComponentEntry> codec() {
        return CODEC;
    }
}
