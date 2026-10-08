package com.iafenvoy.bct.util;

import com.iafenvoy.bct.data.CreativeGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

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
