package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryManager;

import java.util.LinkedList;
import java.util.List;

/**
 * This mod's code registries - the ones a definition's {@code type} fields point into. They are built once at
 * startup, before any definition is read, and are the same on every side because every side loads the same mods.
 *
 * <p>Forge creates them here rather than the mod holding an instance, so anything needing the registry reads it back
 * through its {@link ResourceKey} - which is what {@code byNameCodec()} does.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BctRegistries {
    private static final List<RegistryBuilder<?>> BUILDERS = new LinkedList<>();

    public static final ResourceKey<Registry<Codec<? extends GroupEntry>>> GROUP_ENTRY_TYPE = create("group_entry_type", "item");

    private static <T> ResourceKey<Registry<T>> create(String path, String defaultKey) {
        ResourceKey<Registry<T>> key = ResourceKey.createRegistryKey(new ResourceLocation(BedrockCreativeTabs.MOD_ID, path));
        BUILDERS.add(RegistryBuilder.<T>of(key.location())
                .setDefaultKey(new ResourceLocation(BedrockCreativeTabs.MOD_ID, defaultKey))
                .disableSaving()
                .disableSync());
        return key;
    }

    @SubscribeEvent
    public static void newRegistries(NewRegistryEvent event) {
        BUILDERS.forEach(event::create);
    }

    /**
     * The entry type registry as it stands right now. Forge owns the instance, so it is looked up rather than held;
     * this is null before {@code NewRegistryEvent} has run and must not be cached across that point.
     */
    public static IForgeRegistry<Codec<? extends GroupEntry>> groupEntryTypes() {
        return RegistryManager.ACTIVE.getRegistry(GROUP_ENTRY_TYPE);
    }

    private BctRegistries() {
    }
}
