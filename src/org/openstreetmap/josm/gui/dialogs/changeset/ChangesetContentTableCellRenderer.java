// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.changeset;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;

import javax.swing.JTable;

import org.openstreetmap.josm.data.osm.ChangesetDataSet.ChangesetModificationType;
import org.openstreetmap.josm.data.osm.history.HistoryOsmPrimitive;

/**
 * The table cell renderer used in the changeset content table, except for the "name"
 * column in which we use a {@link org.openstreetmap.josm.gui.OsmPrimitivRenderer}.
 */
public class ChangesetContentTableCellRenderer extends AbstractCellRenderer {

    protected void renderModificationType(ChangesetModificationType type) {
        switch(type) {
        case CREATED: setText(tr("Created")); break;
        case UPDATED: setText(tr("Updated")); break;
        case DELETED: setText(tr("Deleted")); break;
        }
        setToolTipText(null);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        if (value == null)
            return this;
        reset();
        renderColors(isSelected);
        switch(column) {
        case 0:
            ChangesetModificationType type = (ChangesetModificationType) value;
            renderModificationType(type);
            break;
        case 1:
            HistoryOsmPrimitive primitive = (HistoryOsmPrimitive) value;
            renderId(primitive.getId());
            break;
        default:
            /* do nothing */
        }
        return this;
    }
}
