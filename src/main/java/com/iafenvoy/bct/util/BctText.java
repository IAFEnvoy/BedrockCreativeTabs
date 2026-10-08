package com.iafenvoy.bct.util;

import com.iafenvoy.bct.data.CreativeGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * A group's name, derived from the definition's id rather than written in it: a group named {@code <name>.json} reads
 * as {@code creative_group.bedrock_creative_tabs.<name>}. Nothing else builds that key.
 *
 * <p>The id is the one the definition's file is named after, so a pack that renames a file renames the key it has to
 * translate with it. Only the id's path goes into the key - not its namespace - so a definition keeps its key wherever
 * it is filed, and every group reads as one flat family under {@code bedrock_creative_tabs}.
 */
public final class BctText {
    private static final String CATEGORY = "creative_group";

    public static String key(Identifier groupId) {
        return groupId.toLanguageKey(CATEGORY);
    }

    public static MutableComponent name(CreativeGroup group) {
        return Component.translatable(key(group.id()));
    }

    private BctText() {
    }
}
