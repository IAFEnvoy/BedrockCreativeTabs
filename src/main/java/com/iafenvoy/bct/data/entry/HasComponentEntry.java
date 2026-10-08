package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Claims every stack that <b>carries a given component</b>:
 * {@code {"type": "has_component", "component": "minecraft:enchantments"}}.
 *
 * <p>The other types all read the stack's item, so they claim a stack because of what it is. This one reads the stack
 * itself, so it claims one because of what it <em>has</em> - which is the only way to say "every enchanted book",
 * "every potion with a custom effect", "anything with a written book's content" or "any item a pack has dyed",
 * because those are all the same item and differ only in the components they carry. It is deliberately
 * item-agnostic: a pack that wants one item may write this alongside an {@code item} entry, but the entry on its
 * own means the whole component, across every item that can hold it.
 *
 * <p>The test is presence, not value. A stack has the component when the stack's component map contains the key,
 * whether the component came from the item's own prototype or was set on this stack, and whatever value it holds -
 * so {@code minecraft:damage} claims an undamaged stack too. A type that also pins the value would be a different
 * type, since a value is a codec of its own and cannot be read without knowing which component it belongs to; this
 * one stays value-free so it works for every component alike.
 *
 * <p>The component is named by its registry id, so it can only name a component that actually exists: an id nobody
 * registered decodes to the same log line as any other unknown id, and the definition is dropped rather than
 * matching nothing. Not every component is worth naming - some are set on every stack of an item and so claim
 * everything of it, which is what an {@code item} entry says more directly.
 */
public record HasComponentEntry(DataComponentType<?> component) implements GroupEntry {
    public static final MapCodec<HasComponentEntry> CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec()
            .fieldOf("component")
            .xmap(HasComponentEntry::new, HasComponentEntry::component);

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
