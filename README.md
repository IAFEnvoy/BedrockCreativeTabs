# Bedrock Creative Tabs

Configurable creative tabs for NeoForge: a **resource pack** names a set of item stacks and the tabs they belong to, and
each of those tabs shows them as **one icon that opens where it stands** — the way Bedrock Edition groups its creative
inventory. Nothing about which stacks fold, or where, is written in code.

This is a client-side mod. **But for stability, it's recommended to install this mod on dedicated servers.**

![](https://raw.githubusercontent.com/IAFEnvoy/BedrockCreativeTabs/refs/heads/master/img/1.webp)

## In-game group editor

In a world with creative mode enabled, press **B** (configurable in Controls) or run **`/bct edit`** to open
the group picker. It uses the normal creative inventory, including items added by other mods.

| Action | Control |
|--------|---------|
| Select or deselect an item | Left-click |
| Add or remove several items | Hold right-click and drag over items; the first item determines whether the sweep adds or removes |
| Select the current tab's list, or the vanilla search results | Enter while no text box is focused |
| Select matching names or IDs on the current tab | Type in the extra search box, then press Enter or click **Select matches** |
| Select all items in an item tag | Middle-click an item, then click a tag; scroll to browse the list |
| Create a group from the selection | G |
| Rename one of your own groups in Chinese and English | Hover over its icon and press R; save with Enter or **Save**; a blank field keeps its existing name |
| Delete one of your own groups | Hover over its icon and press Delete |
| Clear the selection | C |
| Show or hide the shortcut panel | H |
| Leave picker mode | B, or close the inventory |

Text boxes take priority over shortcuts while focused. After selecting matches, focus returns to the picker.
If the extra search box is unavailable in a narrow window, widen the window or lower GUI Scale; vanilla search,
renaming, and item tags remain available.

Creating a group automatically chooses its name, uses an exact matching item tag when possible (otherwise item IDs),
and infers its creative tabs and priority. Groups take effect immediately, and creating one clears the selection.
The editor follows the game's language, with English and Simplified Chinese translations included.

Your definitions are saved locally under **`config/bedrock_creative_tabs/groups/<namespace>/<path>.json`** and names
under **`config/bedrock_creative_tabs/lang/{zh_cn,en_us}.json`**, relative to the game directory. Local definitions
override resource-pack groups with the same ID. They are not synchronized to other players or the server.

Run **`/bct export`** to create **`resourcepacks/BCT_groups.zip`** containing your local groups and names. Each export
replaces that ZIP and uses the running game's resource-pack version. Share it with another BCT user, who can place
it in their `resourcepacks` directory and enable it in **Options → Resource Packs**. Use **F3+T** to reload changes
to an already enabled pack.

## Resource pack

One JSON file per group, at `assets/<namespace>/bedrock_creative_tabs/creative_group/<path>.json`:

```json
{
  "creative_tabs": [
    "minecraft:ingredients"
  ],
  "icon": {
    "id": "minecraft:red_dye"
  },
  "entries": [
    "#c:dyes"
  ],
  "priority": 0
}
```

A definition is reloaded with **F3+T** (or by switching packs). Reloading also rebuilds the tabs, so a change shows up
as soon as the reload finishes.

| Field           | Type            | Notes                                                                                                                                       |
|-----------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `entries`       | List<Entry>     | **Required, at least one.** The stacks the group claims; any one entry matching is a match. A single entry may be written without the list. |
| `icon`          | Item Stack      | Optional. What the closed icon draws. Defaults to the first member the fold found on that tab.                                              |
| `creative_tabs` | List of Tab IDs | **Required, at least one.** The tabs this group folds in. `minecraft:search` is refused.                                                    |
| `priority`      | Int             | Optional, defaults to `0`. When two groups claim the same stack, the higher priority wins; a tie falls back to load order.                  |

Also don't forget to translate your group's name with `creative_group.<namespace>.<path>` language keys.

### Entries

An entry says one thing: whether it claims a stack. Five types ship with the mod.

| Form                                                                                       | Meaning                                                     |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `"minecraft:oak_planks"`                                                                   | That item, by id.                                           |
| `"#minecraft:planks"`                                                                      | Every item in that tag.                                     |
| `{ "type": "bedrock_creative_tabs:item", "id": "minecraft:oak_planks" }`                   | The same, written out.                                      |
| `{ "type": "bedrock_creative_tabs:item_tag", "tag": "minecraft:planks" }`                  | The same, written out.                                      |
| `{ "type": "bedrock_creative_tabs:block_tag", "tag": "minecraft:mineable/pickaxe" }`       | Every item that places a block in that **block** tag.       |
| `{ "type": "bedrock_creative_tabs:has_component", "component": "minecraft:enchantments" }` | Every stack that carries that component, whatever its item. |
| `{ "type": "bedrock_creative_tabs:regex", "pattern": "minecraft:.*_concrete" }`            | Every item whose full id matches a regular expression.      |

You do not have to catch that by eye: **every entry that matches no item at all is logged**, naming the group and the
entry.

```
WARN  Creative group bedrock_creative_tabs:planks: entry #c:planks matches no item
```

If an entry fails to parse — most often because it names a type that no installed mod provides, or because the type was
written without its namespace — that entry is dropped with a warning in the log and the rest of the definition still
loads; a group left with no entries at all is refused while it loads, so a broken group is never silently an empty one.

## Adding an entry type

An entry type is a `MapCodec` registered under `bedrock_creative_tabs:group_entry_type`:

```java
public final class MyEntryTypes {
    public static final DeferredRegister<MapCodec<? extends GroupEntry>> REGISTRY = DeferredRegister.create(BctRegistries.GROUP_ENTRY_TYPE, "mymod");

    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<RegexEntry>> REGEX = REGISTRY.register("regex", () -> RegexEntry.CODEC);
}

public record RegexEntry(Pattern pattern) implements GroupEntry {
    public static final MapCodec<RegexEntry> CODEC = Codec.STRING.comapFlatMap(RegexEntry::parse, entry -> entry.pattern.pattern()).fieldOf("pattern");

    @Override
    public boolean matches(ItemStack stack) {
        // The full id, namespace included: writing that into the pattern is what a pack does to narrow it.
        return this.pattern.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).matches();
    }

    @Override
    public MapCodec<RegexEntry> codec() {
        return CODEC;
    }
}
```
