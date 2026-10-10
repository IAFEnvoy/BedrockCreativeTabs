package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Every item whose <b>full id</b> matches a pattern: {@code {"type": "regex", "pattern": "minecraft:.*_concrete"}}.
 * The whole id has to match, namespace included, so a pattern written without one matches nothing.
 *
 * <p>The fallback for categories no tag covers - {@code #minecraft:concrete} and friends do not exist. Prefer a tag
 * where there is one: a tag is what another pack extends, and a pattern cannot be extended at all.
 */
public record RegexEntry(Pattern pattern) implements GroupEntry {
    public static final Codec<RegexEntry> CODEC = Codec.STRING.comapFlatMap(RegexEntry::parse, entry -> entry.pattern.pattern()).fieldOf("pattern").codec();

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
    public Codec<RegexEntry> codec() {
        return CODEC;
    }
}
