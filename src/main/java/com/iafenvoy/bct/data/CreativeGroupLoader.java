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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Where the group definitions come from: one JSON file per group, at
 * {@code assets/<your_pack>/bedrock_creative_tabs/creative_group/<name>.json}, read from whatever resource packs the
 * client has on and reloaded with them.
 *
 * <p>This is client resources rather than a data pack on purpose. The fold happens while the <em>client</em> builds a
 * tab - a server never builds one - so a definition only ever has to reach the client that folds with it, and the
 * client's own resource packs can carry it with no server involvement at all. The consequences are worth stating
 * plainly because they are what a pack author notices first: a definition lives under {@code assets/} and ships in a
 * <b>resource pack</b>, and it is reloaded with <b>F3+T</b> rather than {@code /reload}.
 *
 * <p>The listener keeps each group under the id its file is named after, which is the id that ends up on the icon and
 * is what makes two groups' icons differ. A file that fails to parse is logged and skipped, and says which file; the
 * rest of the definitions still load. That is the same bargain {@code BctCodecs.tolerantList} strikes for a single
 * entry inside a file, one level up.
 *
 * <p>Reloading is not only a data change: the tab lists have to be rebuilt for a new definition to be folded at all,
 * which is what {@link CreativeGroupService#onGroupsReloaded()} does.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CreativeGroupLoader extends SimpleJsonResourceReloadListener<CreativeGroup> {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * The directory every definition is read from, under the {@code assets} root of whichever pack provides it.
     */
    public static final String DIRECTORY = BedrockCreativeTabs.MOD_ID + "/creative_group";
    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    // The groups as they stand, in load order, keyed by the id their file is named after. Swapped wholesale on every
    // reload rather than mutated, so a reader never sees a half-loaded table.
    private static volatile Map<Identifier, CreativeGroup> groups = Map.of();
    // Whether every entry has been settled against every tab its group names. Cleared with the table it describes, so
    // a reload is checked again rather than remembered as already done.
    private static volatile boolean checked = true;
    // Per entry, what the tabs built so far have said about it. Filled in as tabs are built and dropped with the
    // table, so it never describes definitions that have gone away.
    private static final Map<EntryKey, Entry> pending = new ConcurrentHashMap<>();

    public CreativeGroupLoader() {
        super(CreativeGroup.CODEC, LISTER);
    }

    /**
     * The groups as they stand, which is what the fold walks. Empty before the first reload completes.
     */
    public static Stream<CreativeGroup> groups() {
        return groups.values().stream();
    }

    /**
     * The groups that claim a stack, best first: higher {@code priority} wins and a tie keeps load order, so the head
     * of this stream is the group that owns the stack.
     */
    public static Optional<CreativeGroup> groupOf(ItemStack stack) {
        return groups().filter(group -> group.matches(stack)).min(ORDER);
    }

    // Highest priority first; a tie keeps the order the files were read in.
    public static final Comparator<CreativeGroup> ORDER =
            Comparator.comparingInt(CreativeGroup::priority).reversed();

    @SubscribeEvent
    public static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, "creative_group"), new CreativeGroupLoader());
    }

    /**
     * Stamps the id a file is named after onto the group it held. A definition does not write its own id, so this is
     * the only place the two are joined; everything downstream - the icon's components, the text keys, the fold's own
     * bookkeeping - reads it back off the group.
     */
    @Override
    protected @NonNull Map<Identifier, CreativeGroup> prepare(@NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        Map<Identifier, CreativeGroup> read = new LinkedHashMap<>();
        for (Map.Entry<Identifier, CreativeGroup> entry : super.prepare(manager, profiler).entrySet()) {
            read.put(entry.getKey(), entry.getValue().withId(entry.getKey()));
        }
        return read;
    }

    @Override
    protected void apply(Map<Identifier, CreativeGroup> prepared, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        // Store and reset, and nothing else. A reload runs before the client has a level - the initial one runs while
        // the loading overlay is still up - so anything that reads the item registry here would run at the one moment
        // the game is least ready for it. The definitions are checked later, against real tab contents.
        groups = Map.copyOf(prepared);
        pending.clear();
        checked = false;
        LOGGER.info("Loaded {} bedrock creative group(s)", prepared.size());
        // A definition that has just changed is not folded into anything yet: the tab lists were built from the old
        // table and are cached, so they have to be thrown away and rebuilt before the new definitions mean anything.
        CreativeGroupService.onGroupsReloaded();
    }

    /**
     * Checks the definitions against one tab's real contents, the first time a tab is built from them.
     *
     * <p>This is the one moment the question can be answered properly. It is late enough that the client has a level
     * and the item registry is live, and the stacks being examined are the ones the game itself put on the tab -
     * including the component-carrying variants, so an entry naming {@code minecraft:tipped_arrow} or
     * {@code minecraft:firework_star} is judged against stacks that really exist rather than against a bare item that
     * never appears on a tab at all.
     *
     * <p>Run once per table, against the first tab built afterwards. Later tabs are the same definitions over
     * different stacks, and re-reporting the same broken entry once per tab would bury it. An entry is only reported
     * when no tab has ever seen it claim anything, which is the honest reading of "matches nothing".
     */
    public static void checkAgainst(Collection<ItemStack> contents) {
        if (checked) return;
        checked = true;
        for (Map.Entry<Identifier, CreativeGroup> group : groups.entrySet()) {
            for (GroupEntry entry : group.getValue().entries()) {
                if (contents.stream().noneMatch(entry::matches))
                    LOGGER.warn("Creative group {}: entry {} matches no item", group.getKey(), entry.describe());
            }
        }
    }

    /**
     * Checks the definitions against a tab's real contents, on the way to building it.
     *
     * <p>This is the one moment the question can be answered properly, and the reason the check is not done while the
     * files are read. It is late enough that the client has a level and the item registry is live, and the stacks
     * being examined are the ones the game itself put on the tab - including the component-carrying variants, so an
     * entry naming {@code minecraft:tipped_arrow} or {@code minecraft:firework_star} is judged against stacks that
     * really exist rather than against a bare item that never appears on a tab at all. A definition whose tag went
     * missing, whose pattern is misspelled or whose component id was never registered does not fail to load - an
     * absent tag is simply empty and a pattern that matches nothing is a legal pattern - so this warning is the only
     * sign a pack gets, and the symptom it replaces is an icon that never appears.
     *
     * <p><b>Every tab contributes, and none of them decides alone.</b> A group names several tabs and its items need
     * not be on all of them - glass is on {@code colored_blocks} and not on {@code building_blocks}, and a group
     * naming both would be misreported by whichever tab happened to be built first. So each entry is marked as soon
     * as any tab claims it, and is only reported once every tab the group names has been built and none of them
     * claimed it. That is the honest reading of "matches nothing": nowhere, not merely not here.
     *
     * <p>Warns rather than fails. An entry matching nothing is a real mistake but a survivable one: the group still
     * folds whatever else works, and a pack may legitimately ship a definition ahead of the mod that fills its tag.
     *
     * @param tabKey   the tab the contents belong to, so a group folding elsewhere is not judged by it
     * @param contents that tab's own entries, before the fold has touched them
     */
    public static void checkAgainst(ResourceKey<CreativeModeTab> tabKey, Collection<ItemStack> contents) {
        if (checked) return;
        boolean settled = true;
        for (Map.Entry<Identifier, CreativeGroup> group : groups.entrySet()) {
            CreativeGroup value = group.getValue();
            if (!value.creativeTabs().contains(tabKey)) continue;
            // One record per entry of this group, keyed by the group and the entry's position in it.
            for (int i = 0; i < value.entries().size(); i++) {
                Entry entry = pending.computeIfAbsent(new EntryKey(group.getKey(), i), key -> new Entry());
                entry.built.add(tabKey);
                if (entry.found && entry.built.containsAll(value.creativeTabs())) continue;
                if (contents.stream().anyMatch(value.entries().get(i)::matches)) entry.found = true;
                // Only speak once the group has had every chance: all of its tabs built, and none of them claimed it.
                else if (entry.built.containsAll(value.creativeTabs())) {
                    LOGGER.warn("Creative group {}: entry {} matches no item in any of its tabs",
                            group.getKey(), value.entries().get(i).describe());
                }
                if (!entry.found) settled = false;
            }
        }
        checked = settled;
    }

    // Which entry of which group a record is about: the group's id and the entry's position in its `entries` list.
    private record EntryKey(Identifier group, int index) {
    }

    // What is known about one entry so far: which of its group's tabs have been built, and whether any claimed it.
    private static final class Entry {
        private final Set<ResourceKey<CreativeModeTab>> built = new HashSet<>();
        private boolean found;
    }
}
