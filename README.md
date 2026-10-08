# Bedrock Creative Tabs

Configurable creative tabs for NeoForge: a **resource pack** names a set of item stacks and the tabs they belong to, and
each of those tabs shows them as **one icon that opens where it stands** — the way Bedrock Edition groups its creative
inventory. Nothing about which stacks fold, or where, is written in code.

**This is a client-side mod.** It loads on the client only and needs nothing from the server: a creative tab is built by
the client's creative screen and by nothing else, so a server has no tab list to fold. The definitions therefore ship in
a resource pack and reload with **F3+T**, not with `/reload` — see [Resource pack](#resource-pack).

- **Folded by default.** A group takes its members out of the tab's list and leaves an icon in their place.
- **Click to open in place.** Left click the icon and the members appear right after it, in the order the tab had
  them; click again and they fold back. The icon carries a `+` / `-` mark in its bottom-right corner.
- **Per tab.** A definition says which tabs it folds in, so the same stacks can be folded on one page and left alone on
  another.
- **Search still finds everything.** Members are removed from the *parent* list only, so the search page keeps
  collecting them; the icon itself stays out of search.
- **The icon cannot be taken.** It carries the platform's own slot lock, so no click path or quick-craft drag can pick
  it up. It is also not offered as a plain entry anywhere.
- **Opening does not lose your place.** Expanding goes through the creative screen's own refresh, so the row you
  scrolled to is kept.
- **Every page starts folded.** Expanding belongs to the visit, not to the tab: leaving and coming back shows the
  groups closed again.
- **Opened groups are backed.** While a group is open, a half-transparent black square is painted under the icon and
  under every member you can see, so it reads as one block.

## Resource pack

One JSON file per group, at
`assets/<your_pack>/bedrock_creative_tabs/creative_group/<name>.json`:

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

The directory is under `assets/`, because the client reads it from its own resource packs. A definition is reloaded with
**F3+T** (or by switching packs), not with `/reload`: `/reload` reloads data packs on the server, which is not where
this
reads from. Reloading also rebuilds the tabs, so a change shows up as soon as the reload finishes.

| Field           | Type                | Notes                                                                                                                                       |
|-----------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `entries`       | list                | **Required, at least one.** The stacks the group claims; any one entry matching is a match. A single entry may be written without the list. |
| `icon`          | item stack template | Optional. What the closed icon draws. Defaults to the first member the fold found on that tab.                                              |
| `creative_tabs` | list of tab ids     | **Required, at least one.** The tabs this group folds in. `minecraft:search` is refused.                                                    |
| `priority`      | int                 | Optional, defaults to `0`. When two groups claim the same stack, the higher priority wins; a tie falls back to load order.                  |

### The name

A definition carries no text. The icon's name is translated under the key

```
creative_group.<namespace>.<path>
```

so `dyes.json` needs `creative_group.bedrock_creative_tabs.dyes` in your language file. The key comes from the file name
rather than anything inside the file, which is why a definition has no `name` field: text written in the definition is
text no pack could translate, and the file name is the one thing a group is already guaranteed to have.

Only the id's *path* goes into the key, not its namespace, so a group keeps its key wherever it is filed and every group
reads as one flat family under `bedrock_creative_tabs`. An untranslated group still works — its icon just shows the raw
key.

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

A tag is read when the tab is built, so another pack adding to it changes the group without touching the definition.

**The two tag types are not interchangeable.** `item_tag` asks the question directly and is what almost every definition
wants. `block_tag` bridges through the stack's item — the stack matches when its item places a block and that block is
in
the tag — so a stick or a sword matches no block tag at all, however the tag is written. Reach for it only where the tag
exists on the block side alone (`#minecraft:mineable/pickaxe`, `#minecraft:needs_stone_tool`); where both sides have it,
prefer `item_tag`, which cannot be thrown off by an item whose block form is not what you meant.

**`has_component` is the one entry that reads the stack rather than the item.** The others claim a stack because of what
it *is*; this one claims it because of what it *has*, which is the only way to say "every enchanted book" or "every
potion with a custom effect" — the same item, differing only in components. It tests presence, not value, so
`minecraft:damage` claims an undamaged stack too. It is item-agnostic on purpose: an entry naming
`minecraft:enchantments` claims every enchanted thing there is.

**Prefer tags, in this order.** Several tags exist for the same thing and they are worth choosing between deliberately:

1. **`#c:`** — the cross-mod convention (NeoForge ships these under `data/c/tags/item/`, 268 of them for items). This is
   the most compatible choice of all, because another mod's items join them without either side doing anything:
   `#c:glass_blocks`, `#c:glass_panes`, `#c:concretes`, `#c:concrete_powders`, `#c:glazed_terracottas`, `#c:ores`,
   `#c:stones`, `#c:cobblestones`, `#c:sandstone/blocks`, `#c:chains`, `#c:bars`, `#c:dyes`, `#c:shulker_boxes`,
   `#c:music_discs`, `#c:natural_logs`, `#c:natural_woods`, `#c:stripped_logs`, `#c:stripped_woods`,
   `#c:cobblestones/infested`, `#c:armors/horse`, `#c:armors/nautilus`, `#c:drinks/ominous`, `#c:potions/bottle`,
   `#c:tools/shield`.
2. **`#minecraft:`** — what vanilla and other packs extend. Use it where there is no `c:` equivalent, which is more
   often than the names suggest: `#minecraft:planks`, `#minecraft:wool`, `#minecraft:wool_carpets`, `#minecraft:stairs`,
   `#minecraft:slabs`, `#minecraft:walls`, `#minecraft:doors`, `#minecraft:trapdoors`, `#minecraft:terracotta`,
   `#minecraft:beds`, `#minecraft:candles`, `#minecraft:lanterns`, `#minecraft:rails`, `#minecraft:bundles`,
   `#minecraft:arrows`, `#minecraft:skulls`, `#minecraft:decorated_pot_sherds`, `#minecraft:head_armor`,
   `#minecraft:chest_armor`, `#minecraft:leg_armor`, `#minecraft:foot_armor`, `#minecraft:swords`, `#minecraft:axes`,
   `#minecraft:pickaxes`, `#minecraft:shovels`, `#minecraft:hoes`.
3. **The regex form** — the fallback for what no tag covers: coral, coral fans, coral blocks, smithing templates,
   pressure plates, minecarts, fireworks, firework stars, splash and lingering potions, and tipped arrows. It matches
   the item's **full id** — namespace included, as `minecraft:oak_planks` — and must match the whole of it, so
   `minecraft:.*_concrete` does not also claim `minecraft:.*_concrete_powder` and `.*_concrete` on its own matches
   nothing at all. Naming the namespace is part of writing a pattern, and it is also what lets one pattern reach a
   single namespace deliberately. No other mod can extend it, and a set the game later grows is only covered if the
   pattern happens to describe it.

**Do not guess a tag name.** A tag that does not exist is not an error, so a wrong guess fails silently as a group
that matches nothing — the only symptom is an icon that never appears. `c:planks`, `c:wools`, `c:stairs`, `c:slabs`,
`c:walls`, `c:doors`, `c:beds`, `c:candles`, `c:rails`, `c:armors/helmet` and `c:tools/sword` are all plausible and
all absent. List the jar's `data/c/tags/item/` to see what is really there.

You do not have to catch that by eye: **every entry that matches no item at all is logged**, naming the group and the
entry.

```
WARN  Creative group bedrock_creative_tabs:planks: entry #c:planks matches no item
```

An entry is silent as soon as it claims a single item, so a group with one working entry and one broken one still says
so — this catches exactly the mistake that is otherwise invisible. It is a warning rather than an error on purpose: a
definition shipped ahead of the mod that fills its tag is legitimate, and the group still folds whatever else works.

The check runs the first time the definitions are actually read — when a creative tab is built — and not when the files
are loaded. That is deliberate: the initial resource reload happens before the client has a level, so the log line for a
bad definition arrives when you open the creative menu rather than while the game is starting.

The `type` value is an id, so it must be **fully qualified**: write `"bedrock_creative_tabs:item_tag"`, not
`"item_tag"`. A bare name resolves against `minecraft`, so the lookup asks for `minecraft:item_tag` and fails.

If an entry fails to parse — most often because it names a type that no installed mod provides, or because the type was
written without its namespace — that entry is dropped with a warning in the log and the rest of the definition still
loads; a group left with no entries at all is refused while it loads, so a broken group is never silently an empty one.

A tag that no pack provides is **empty, not an error**: the group loads and simply folds nothing. That is the same
bargain `"required": false` strikes inside a tag, one level up — and it is the case the warning above is there to
surface.

## Adding an entry type

An entry type is a `MapCodec` registered under `bedrock_creative_tabs:group_entry_type`. The id it is registered as is
what a pack writes in the `type` field, and the registry is first-wins, so two mods cannot claim one id:

```java
public final class MyEntryTypes {
    public static final DeferredRegister<MapCodec<? extends GroupEntry>> REGISTRY =
            DeferredRegister.create(BctRegistries.GROUP_ENTRY_TYPE, "mymod");

    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<RegexEntry>> REGEX =
            REGISTRY.register("regex", () -> RegexEntry.CODEC);
}

public record RegexEntry(Pattern pattern) implements GroupEntry {
    public static final MapCodec<RegexEntry> CODEC =
            Codec.STRING.comapFlatMap(RegexEntry::parse, entry -> entry.pattern.pattern()).fieldOf("pattern");

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

Register that `DeferredRegister` on your mod bus and `{"type": "mymod:regex", "pattern": "mymod:.*_dust"}` works
everywhere the built-in forms do. Compile the parts that can be validated while loading (a regex, a reference) in the
codec: `DataResult.error(...)` there fails the file with a message instead of surprising the first tab build.

The type registry is a code registry rather than a resource one — every side builds it from the mods it has loaded.
Definitions are only ever decoded now that they come from the client's own pack, but a codec still has to implement
`encode`: `Codec` requires both directions.
