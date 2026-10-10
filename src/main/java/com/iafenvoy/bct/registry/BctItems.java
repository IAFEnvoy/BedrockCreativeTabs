package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * This mod's only item: the blank the fold writes a group's icon into. It is never offered as a plain creative entry,
 * and its model is fully transparent - what a slot shows is the member or the definition's icon drawn on top.
 */
public final class BctItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, BedrockCreativeTabs.MOD_ID);

    public static final RegistryObject<Item> GROUP = REGISTRY.register("group", () -> new Item(new Item.Properties().stacksTo(1)));

    private BctItems() {
    }
}
