package com.iafenvoy.bct.data;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the group definitions: one JSON per group at
 * {@code assets/<pack>/bedrock_creative_tabs/creative_group/<name>.json}, reloaded with the client's resource packs
 * (F3+T, not {@code /reload}).
 *
 * <p>Client resources rather than a data pack because a tab is only ever built on the client. The id a group is filed
 * under is the one it is named after, and a file that fails to parse is logged and skipped rather than taking the
 * rest down with it.
 *
 * <p>A reload only replaces this table; the tab lists are rebuilt separately, in
 * {@link CreativeGroupService#onGroupsReloaded()}.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CreativeGroupLoader extends SimpleJsonResourceReloadListener<CreativeGroup> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DIRECTORY = BedrockCreativeTabs.MOD_ID + "/creative_group";
    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    // In load order, keyed by the file name it came from. Swapped wholesale on reload, never mutated in place.
    private static volatile Map<Identifier, CreativeGroup> groups = Map.of();
    // Per entry, what the tabs built so far have said about it. Dropped with the table it describes.
    private static final Map<EntryKey, EntryCheck> entryChecks = new ConcurrentHashMap<>();
    // Set once every entry has been judged against every tab its group names.
    private static volatile boolean fullyChecked = true;

    public CreativeGroupLoader() {
        super(CreativeGroup.CODEC, LISTER);
    }

    /**
     * The groups as they stand, keyed by the id their file is named after. Empty before the first reload completes.
     */
    public static Map<Identifier, CreativeGroup> groups() {
        return groups;
    }

    /**
     * Highest priority first; a tie keeps the order the files were read in.
     */
    public static final Comparator<Map.Entry<Identifier, CreativeGroup>> ORDER = Map.Entry.comparingByValue(Comparator.comparingInt(CreativeGroup::priority).reversed());

    @SubscribeEvent
    public static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "creative_group"), new CreativeGroupLoader());
    }

    @Override
    protected void apply(Map<Identifier, CreativeGroup> prepared, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        // Replace and reset only. This runs before the client has a level, so nothing here may touch the item
        // registry; the definitions are judged later, against real tab contents.
        groups = Map.copyOf(prepared);
        entryChecks.clear();
        fullyChecked = false;
        LOGGER.info("Loaded {} bedrock creative group(s)", prepared.size());
        CreativeGroupService.onGroupsReloaded();
    }

    /**
     * Warns about entries that no tab ever claims, judged against the contents a tab really holds.
     *
     * <p>Run here rather than at load time because the initial reload happens before the client has a level, and
     * because the tab's own contents include component-carrying stacks - a bare item would make
     * {@code minecraft:tipped_arrow} look dead. An absent tag is simply empty and a pattern that matches nothing is a
     * legal pattern, so without this the only symptom is an icon that never appears.
     *
     * <p>Every tab contributes, since a group's items need not be on all the tabs it names. An entry is reported only
     * once all of them have been built and none claimed it.
     */
    public static void checkAgainst(ResourceKey<CreativeModeTab> tabKey, Collection<ItemStack> contents) {
        if (fullyChecked) return;
        boolean everyEntryJudged = true;
        for (Map.Entry<Identifier, CreativeGroup> group : groups.entrySet()) {
            CreativeGroup value = group.getValue();
            if (!value.creativeTabs().contains(tabKey)) continue;
            Set<ResourceKey<CreativeModeTab>> allTabs = Set.copyOf(value.creativeTabs());
            for (int i = 0; i < value.entries().size(); i++) {
                GroupEntry entry = value.entries().get(i);
                EntryCheck check = entryChecks.computeIfAbsent(new EntryKey(group.getKey(), i), key -> new EntryCheck());
                check.builtTabs.add(tabKey);
                if (!check.claimed) {
                    check.claimed = contents.stream().anyMatch(entry::matches);
                    if (!check.claimed && check.builtTabs.containsAll(allTabs)) {
                        LOGGER.warn("Creative group {}: entry {} matches no item in any of its tabs",
                                group.getKey(), entry.describe());
                        check.judged = true; // Reported; do not report it again on every later tab.
                    }
                }
                everyEntryJudged &= check.claimed || check.judged;
            }
        }
        fullyChecked = everyEntryJudged;
    }

    private record EntryKey(Identifier group, int index) {
    }

    private static final class EntryCheck {
        private final Set<ResourceKey<CreativeModeTab>> builtTabs = new HashSet<>();
        private boolean claimed;
        private boolean judged;
    }
}
