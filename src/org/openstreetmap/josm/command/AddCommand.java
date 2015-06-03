// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;
import java.util.Collections;

import javax.swing.Icon;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.DefaultNameFormatter;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * A command that adds an osm primitive to a dataset. Keys cannot be added this way.
 *
 * See {@link ChangeCommand} for comments on relation back references.
 *
 * @author imi
 */
public class AddCommand extends Command {

    /**
     * The primitive to add to the dataset.
     */
    private final OsmPrimitive osm;

    /**
     * Creates the command and specify the element to add in the context of the current edit layer, if any.
     * @param osm The primitive to add
     */
    public AddCommand(OsmPrimitive osm) {
        this.osm = osm;
    }

    /**
     * Creates the command and specify the element to add in the context of the given data layer.
     * @param layer The data layer. Must not be {@code null}
     * @param osm The primitive to add
     */
    public AddCommand(OsmDataLayer layer, OsmPrimitive osm) {
        super(layer);
        this.osm = osm;
    }

    protected static final void checkNodeStyles(OsmPrimitive osm) {
        if (osm instanceof Way) {
            // Fix #10557 - node icon not updated after undoing/redoing addition of a way
            ((Way)osm).clearCachedNodeStyles();
        }
    }

    @Override
    public boolean executeCommand() {
        getLayer().data.addPrimitive(osm);
        osm.setModified(true);
        checkNodeStyles(osm);
        return true;
    }

    @Override
    public void undoCommand() {
        getLayer().data.removePrimitive(osm);
        checkNodeStyles(osm);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
        added.add(osm);
    }

    @Override
    public String getDescriptionText() {
        String msg;
        switch(OsmPrimitiveType.from(osm)) {
        case NODE: msg = marktr("Add node {0}"); break;
        case WAY: msg = marktr("Add way {0}"); break;
        case RELATION: msg = marktr("Add relation {0}"); break;
        default: /* should not happen */msg = ""; break;
        }
        return tr(msg, osm.getDisplayName(DefaultNameFormatter.getInstance()));
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get(osm.getDisplayType());
    }

    @Override
    public Collection<OsmPrimitive> getParticipatingPrimitives() {
        return Collections.singleton(osm);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((osm == null) ? 0 : osm.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        AddCommand other = (AddCommand) obj;
        if (osm == null) {
            if (other.osm != null)
                return false;
        } else if (!osm.equals(other.osm))
            return false;
        return true;
    }
}
