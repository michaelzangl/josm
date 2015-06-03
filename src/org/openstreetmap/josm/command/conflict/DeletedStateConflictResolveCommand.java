// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command.conflict;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;
import java.util.Set;

import javax.swing.Icon;

import org.openstreetmap.josm.data.conflict.Conflict;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.conflict.pair.MergeDecisionType;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Represents the resolution of a conflict between the deleted flag of two {@link OsmPrimitive}s.
 * @since 1654
 */
public class DeletedStateConflictResolveCommand extends ConflictResolveCommand {

    /** the conflict to resolve */
    private Conflict<? extends OsmPrimitive> conflict;

    /** the merge decision */
    private final MergeDecisionType decision;

    /**
     * Constructs a new {@code DeletedStateConflictResolveCommand}.
     *
     * @param conflict the conflict data set
     * @param decision the merge decision
     */
    public DeletedStateConflictResolveCommand(Conflict<? extends OsmPrimitive> conflict, MergeDecisionType decision) {
        this.conflict = conflict;
        this.decision = decision;
    }

    @Override
    public String getDescriptionText() {
        return tr("Resolve conflicts in deleted state in {0}", conflict.getMy().getId());
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("data", "object");
    }

    @Override
    public boolean executeCommand() {
        // remember the current state of modified primitives, i.e. of OSM primitive 'my'
        super.executeCommand();

        if (decision.equals(MergeDecisionType.KEEP_MINE)) {
            if (conflict.getMy().isDeleted() || conflict.isMyDeleted()) {
                // because my was involved in a conflict it my still be referred
                // to from a way or a relation. Fix this now.
                deleteMy();
            }
        } else if (decision.equals(MergeDecisionType.KEEP_THEIR)) {
            if (conflict.getTheir().isDeleted()) {
                deleteMy();
            } else {
                conflict.getMy().setDeleted(false);
            }
        } else
            // should not happen
            throw new IllegalStateException(tr("Cannot resolve undecided conflict."));

        rememberConflict(conflict);
        return true;
    }

    private void deleteMy() {
        Set<OsmPrimitive> referrers = getLayer().data.unlinkReferencesToPrimitive(conflict.getMy());
        for (OsmPrimitive p : referrers) {
            if (!p.isNew() && !p.isDeleted()) {
                p.setModified(true);
            }
        }
        conflict.getMy().setDeleted(true);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.add(conflict.getMy());
        modified.addAll(conflict.getMy().getReferrers());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((conflict == null) ? 0 : conflict.hashCode());
        result = prime * result + ((decision == null) ? 0 : decision.hashCode());
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
        DeletedStateConflictResolveCommand other = (DeletedStateConflictResolveCommand) obj;
        if (conflict == null) {
            if (other.conflict != null)
                return false;
        } else if (!conflict.equals(other.conflict))
            return false;
        if (decision != other.decision)
            return false;
        return true;
    }
}
