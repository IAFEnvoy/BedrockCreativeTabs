/**
 * The contract another mod implements to add a way of claiming stacks for a group, and the runtime that folds them.
 *
 * <p>{@link com.iafenvoy.bct.api.GroupEntry} is the extension point: an entry type is a codec registered under
 * {@code bedrock_creative_tabs:group_entry_type}, and the id it is registered as is what a data pack writes in the
 * {@code type} field. See that interface for the whole story.
 */
package com.iafenvoy.bct.api;
