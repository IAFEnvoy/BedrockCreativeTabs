package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Claims every item whose id matches a regular expression, written as {@code bedrock_creative_tabs:regex}.
 *
 * <p>This exists because a large part of the creative tabs has no tag to name. Vanilla ships tags for the shapes that
 * a pack is expected to extend ({@code #minecraft:planks}, {@code #minecraft:wool}) but not for the ones that are only
 * ever a fixed set of colours - there is no {@code #minecraft:concrete}, {@code #minecraft:glass} or
 * {@code #minecraft:glazed_terracotta}. A definition that wants those would otherwise have to list all sixteen
 * colours of each, which is long, and which silently stops being complete the moment the game adds a seventeenth.
 *
 * <p>A pattern is the right tool for exactly that shape: {@code minecraft:.*_concrete} is the whole category, and it
 * keeps working when the set grows. It is the wrong tool where a tag exists, which is why both are offered and the
 * definitions use the tag wherever there is one - a tag is the thing another pack is meant to extend, and it matches
 * items from other namespaces too, which {@code minecraft:.*} by construction cannot.
 *
 * <p>The pattern is matched against the item's <b>full id</b> - namespace included, as {@code minecraft:oak_planks} -
 * and has to match the whole of it, so {@code .*_concrete} matches nothing at all while {@code minecraft:.*_concrete}
 * is the whole category. Naming the namespace is therefore part of writing a pattern, and it is also what lets one
 * pattern reach a single namespace deliberately.
 */
public record RegexEntry(Pattern pattern) implements GroupEntry {
    public static final MapCodec<RegexEntry> CODEC = Codec.STRING.comapFlatMap(RegexEntry::parse, entry -> entry.pattern.pattern())
            .fieldOf("pattern");

    private static DataResult<RegexEntry> parse(String value) {
        try {
            return DataResult.success(new RegexEntry(Pattern.compile(value)));
        } catch (PatternSyntaxException e) {
            return DataResult.error(() -> "Invalid pattern '" + value + "': " + e.getDescription());
        }
    }

    @Override
    public boolean matches(ItemStack stack) {
        return this.pattern.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).matches();
    }

    @Override
    public String describe() {
        return "regex{" + this.pattern.pattern() + "}";
    }

    @Override
    public MapCodec<RegexEntry> codec() {
        return CODEC;
    }
}
