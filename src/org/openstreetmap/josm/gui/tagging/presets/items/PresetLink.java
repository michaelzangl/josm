// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets.items;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetItem;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetLabel;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.tools.GBC;

/**
 * Adds a link to an other preset.
 * @since 8863
 */
public class PresetLink extends TaggingPresetItem {

    /** The exact name of the preset to link to. Required. */
    public String preset_name = ""; // NOSONAR

    @Override
    public boolean addToPanel(JPanel p, Collection<OsmPrimitive> sel, boolean presetInitiallyMatches) {
        final String presetName = preset_name;
        Optional<TaggingPreset> found = TaggingPresets.getTaggingPresets().stream().filter(preset -> presetName.equals(preset.name)).findFirst();
        if (!found.isPresent())
            return false;
        TaggingPreset t = found.get();
        JLabel lbl = new TaggingPresetLabel(t);
        lbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent arg0) {
                t.actionPerformed(null);
            }
        });
        p.add(lbl, GBC.eol().fill(GBC.HORIZONTAL));
        return false;
    }

    @Override
    public void addCommands(List<Tag> changedTags) {
        // Do nothing
    }
}
