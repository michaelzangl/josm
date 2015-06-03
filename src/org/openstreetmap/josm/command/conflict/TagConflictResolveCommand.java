// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command.conflict;

import static org.openstreetmap.josm.tools.I18n.trn;

import java.util.Collection;
import java.util.List;

import javax.swing.Icon;

import org.openstreetmap.josm.data.conflict.Conflict;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.gui.conflict.pair.MergeDecisionType;
import org.openstreetmap.josm.gui.conflict.pair.tags.TagMergeItem;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Represents the resolution of a tag conflict in an {@link OsmPrimitive}.
 *
 */
public class TagConflictResolveCommand extends ConflictResolveCommand {
    /** the conflict to resolve */
    private Conflict<? extends OsmPrimitive> conflict;

    /** the list of merge decisions, represented as {@link TagMergeItem}s */
    private final List<TagMergeItem> mergeItems;

    /**
     * replies the number of decided conflicts
     *
     * @return the number of decided conflicts
     */
    public int getNumDecidedConflicts() {
        int n = 0;
        for (TagMergeItem item: mergeItems) {
            if (!item.getMergeDecision().equals(MergeDecisionType.UNDECIDED)) {
                n++;
            }
        }
        return n;
    }

    /**
     * constructor
     *
     * @param conflict the conflict data set
     * @param mergeItems the list of merge decisions, represented as {@link TagMergeItem}s
     */
    public TagConflictResolveCommand(Conflict<? extends OsmPrimitive> conflict, List<TagMergeItem> mergeItems) {
        this.conflict = conflict;
        this.mergeItems = mergeItems;
    }

    @Override
    public String getDescriptionText() {
        switch (OsmPrimitiveType.from(conflict.getMy())) {
            case NODE:
                /* for correct i18n of plural forms - see #9110 */
                return trn("Resolve {0} tag conflict in node {1}", "Resolve {0} tag conflicts in node {1}", getNumDecidedConflicts(), getNumDecidedConflicts(), conflict.getMy().getId());
            case WAY:
                /* for correct i18n of plural forms - see #9110 */
                return trn("Resolve {0} tag conflict in way {1}", "Resolve {0} tag conflicts in way {1}", getNumDecidedConflicts(), getNumDecidedConflicts(), conflict.getMy().getId());
            case RELATION:
                /* for correct i18n of plural forms - see #9110 */
                return trn("Resolve {0} tag conflict in relation {1}", "Resolve {0} tag conflicts in relation {1}", getNumDecidedConflicts(), getNumDecidedConflicts(), conflict.getMy().getId());
        }
        return "";
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("data", "object");
    }

    @Override
    public boolean executeCommand() {
        // remember the current state of modified primitives, i.e. of
        // OSM primitive 'my'
        //
        super.executeCommand();

        // apply the merge decisions to OSM primitive 'my'
        //
        for (TagMergeItem item: mergeItems) {
            if (!item.getMergeDecision().equals(MergeDecisionType.UNDECIDED)) {
                item.applyToMyPrimitive(conflict.getMy());
            }
        }
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
        result = prime * result + ((mergeItems == null) ? 0 : mergeItems.hashCode());
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
        TagConflictResolveCommand other = (TagConflictResolveCommand) obj;
        if (conflict == null) {
            if (other.conflict != null)
                return false;
        } else if (!conflict.equals(other.conflict))
            return false;
        if (mergeItems == null) {
            if (other.mergeItems != null)
                return false;
        } else if (!mergeItems.equals(other.mergeItems))
            return false;
        return true;
    }
}
