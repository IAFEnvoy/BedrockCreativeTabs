package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * This mod's code registries - the ones a definition's {@code type} fields point into. They are built once at
 * startup, before any definition is read, and are the same on every side because every side loads the same mods.
 */
@EventBusSubscriber
public final class BctRegistries {
    private static final List<DefaultedRegistry<?>> REGISTRIES = new LinkedList<>();

    public static final DefaultedRegistry<MapCodec<? extends GroupEntry>> GROUP_ENTRY_TYPE = create(BedrockCreativeTabs.MOD_ID + ":item", BctResourceKeys.GROUP_ENTRY_TYPE);

    private static <T> DefaultedRegistry<T> create(String defaultKey, ResourceKey<? extends Registry<T>> key) {
        DefaultedRegistry<T> registry = new DefaultedMappedRegistry<>(defaultKey, key, Lifecycle.stable(), false);
        REGISTRIES.add(registry);
        return registry;
    }

    @SubscribeEvent
    public static void newRegistries(NewRegistryEvent event) {
        REGISTRIES.forEach(event::register);
    }

    private BctRegistries() {
    }
}
