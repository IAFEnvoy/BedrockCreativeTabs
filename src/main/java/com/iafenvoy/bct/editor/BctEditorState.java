package com.iafenvoy.bct.editor;

import java.util.LinkedHashSet;

/**
 * Selection state shared by every picker surface: the vanilla creative screen's click-to-select mode and the
 * (now secondary) AUI grid both read and write the same set, so a selection survives switching tabs, switching
 * surfaces, and reopening the game screen. No AUI imports - the picker path must not load them.
 */
public final class BctEditorState {
    /**
     * Chosen item ids, in pick order.
     */
    public static final LinkedHashSet<String> CHOSEN = new LinkedHashSet<>();
    /**
     * Whether clicks in the creative screen toggle selection and the green marker paints.
     */
    public static volatile boolean PICKER_MODE;

    private BctEditorState() {
    }
}
