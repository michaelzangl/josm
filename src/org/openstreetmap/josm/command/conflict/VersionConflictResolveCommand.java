// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command.conflict;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;

import javax.swing.Icon;

import org.openstreetmap.josm.data.conflict.Conflict;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Represents the resolution of a version conflict between two {@link OsmPrimitive}s.
 *
 *
 */
public class VersionConflictResolveCommand extends ConflictResolveCommand {

    /** the conflict to resolve */
    private Conflict<? extends OsmPrimitive> conflict;

    /**
     * constructor
     * @param conflict the conflict data set
     */
    public VersionConflictResolveCommand(Conflict<? extends OsmPrimitive> conflict) {
        this.conflict = conflict;
    }

    @Override
    public String getDescriptionText() {
        String msg = "";
        switch(OsmPrimitiveType.from(conflict.getMy())) {
        case NODE: msg = marktr("Resolve version conflict for node {0}"); break;
        case WAY: msg = marktr("Resolve version conflict for way {0}"); break;
        case RELATION: msg = marktr("Resolve version conflict for relation {0}"); break;
        }
        return tr(msg, conflict.getMy().getId());
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("data", "object");
    }

    @Override
    public boolean executeCommand() {
        super.executeCommand();
        if (!conflict.getMy().isNew()) {
            long myVersion = conflict.getMy().getVersion();
            long theirVersion = conflict.getTheir().getVersion();
            conflict.getMy().setOsmId(
                    conflict.getMy().getId(),
                    (int)Math.max(myVersion, theirVersion)
            );
            // update visiblity state
            if (theirVersion >= myVersion) {
                conflict.getMy().setVisible(conflict.getTheir().isVisible());
            }
        }
        getLayer().getConflicts().remove(conflict);
        rememberConflict(conflict);
        return true;
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.add(conflict.getMy());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((conflict == null) ? 0 : conflict.hashCode());
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
        VersionConflictResolveCommand other = (VersionConflictResolveCommand) obj;
        if (conflict == null) {
            if (other.conflict != null)
                return false;
        } else if (!conflict.equals(other.conflict))
            return false;
        return true;
    }
}
