package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.function.Function;

/**
 * This mod's only item: the blank the fold writes a group's icon into. It is never offered as a plain creative entry -
 * the fold writes it, and the slot lock keeps it from being taken - so it only has to exist for the item decoration
 * and the tooltip to hang off. Its own model is a fully transparent texture, because what a slot shows is the member
 * or the definition's icon drawn on top of it.
 */
public final class BctItems {
    public static final Items REGISTRY = DeferredRegister.createItems(BedrockCreativeTabs.MOD_ID);

    public static final DeferredItem<Item> GROUP = register("group", properties -> new Item(properties.stacksTo(1)));

    public static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, path));
        return REGISTRY.register(path, () -> factory.apply(new Properties().setId(key)));
    }

    private BctItems() {
    }
}
