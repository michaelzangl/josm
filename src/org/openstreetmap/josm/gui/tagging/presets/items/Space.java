// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets.items;

import java.util.Collection;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetItem;
import org.openstreetmap.josm.tools.GBC;

/**
 * Horizontal separator type.
 */
public class Space extends TaggingPresetItem {

    @Override
    public boolean addToPanel(JPanel p, Collection<OsmPrimitive> sel, boolean presetInitiallyMatches) {
        p.add(new JLabel(" "), GBC.eol()); // space
        return false;
    }

    @Override
    public void addCommands(List<Tag> changedTags) {
    }

    @Override
    public String toString() {
        return "Space";
    }
}
