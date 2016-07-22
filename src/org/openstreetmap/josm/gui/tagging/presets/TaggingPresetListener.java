// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

/**
 * Notification of tagging presets events.
 * @since 7100
 * @see TaggingPresets#addListener(TaggingPresetListener)
 */
@FunctionalInterface
public interface TaggingPresetListener {

    /**
     * Called after list of tagging presets has been modified.
     */
    void taggingPresetsModified();
}
