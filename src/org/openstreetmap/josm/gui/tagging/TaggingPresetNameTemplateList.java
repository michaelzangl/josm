// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * List of tagging presets with name templates, allows to find appropriate template based on existing primitive
 */
public final class TaggingPresetNameTemplateList implements TaggingPresetListener {

    private static TaggingPresetNameTemplateList instance;

    /**
     * Replies the unique instance.
     * @return the unique instance
     */
    public static synchronized TaggingPresetNameTemplateList getInstance() {
        if (instance == null) {
            instance = new TaggingPresetNameTemplateList();
            TaggingPresets.addListener(instance);
        }
        return instance;
    }

    private final List<TaggingPreset> presetsWithPattern = new LinkedList<>();

    private TaggingPresetNameTemplateList() {
        buildPresetsWithPattern();
    }

    private void buildPresetsWithPattern() {
        synchronized (this) {
            Main.debug("Building list of presets with name template");
            presetsWithPattern.clear();
            for (TaggingPreset tp : TaggingPresets.getTaggingPresets()) {
                if (tp.nameTemplate != null) {
                    presetsWithPattern.add(tp);
                }
            }
        }
    }

    /**
     * Finds and returns the first occurence of preset with template name matching the given primitive
     * @param primitive The primitive to match
     * @return the first occurence of preset with template name matching the primitive
     */
    public TaggingPreset findPresetTemplate(OsmPrimitive primitive) {
        synchronized (this) {
            for (TaggingPreset t : presetsWithPattern) {
                Collection<TaggingPresetType> type = Collections.singleton(TaggingPresetType.forPrimitive(primitive));
                if (t.typeMatches(type)) {
                    if (t.nameTemplateFilter != null) {
                        if (t.nameTemplateFilter.match(primitive))
                            return t;
                        else {
                            continue;
                        }
                    } else if (t.matches(type, primitive.getKeys(), false)) {
                        return t;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void taggingPresetsModified() {
        buildPresetsWithPattern();
    }
}
