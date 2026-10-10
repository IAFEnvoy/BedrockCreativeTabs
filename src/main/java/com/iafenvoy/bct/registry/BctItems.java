package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.function.Function;

/**
 * This mod's only item: the blank the fold writes a group's icon into. It is never offered as a plain creative entry,
 * and its model is fully transparent - what a slot shows is the member or the definition's icon drawn on top.
 */
public final class BctItems {
    public static final Items REGISTRY = DeferredRegister.createItems(BedrockCreativeTabs.MOD_ID);

    public static final DeferredItem<Item> GROUP = register("group", properties -> new Item(properties.stacksTo(1)));

    // 1.21.1 builds the Properties for us, so the item id comes off the registry rather than being written in.
    public static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        return REGISTRY.registerItem(path, factory);
    }

    private BctItems() {
    }
}
