// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.relation.actions;

import java.awt.event.ActionEvent;

import javax.swing.TransferHandler.TransferSupport;

import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.gui.dialogs.relation.IRelationEditor;
import org.openstreetmap.josm.gui.dialogs.relation.MemberTable;
import org.openstreetmap.josm.gui.dialogs.relation.MemberTransferHandler;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * Paste members.
 * @since 9496
 */
public class PasteMembersAction extends AddFromSelectionAction {

    /**
     * Constructs a new {@code PasteMembersAction}.
     * @param memberTable member table
     * @param layer OSM data layer
     * @param editor relation editor
     */
    public PasteMembersAction(MemberTable memberTable, OsmDataLayer layer, IRelationEditor editor) {
        super(memberTable, null, null, null, null, layer, editor);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MemberTransferHandler handler = new MemberTransferHandler();
        handler.importData(new TransferSupport(memberTable, OsmTransferHandler.getClippboard().getContents(null)));
    }

    @Override
    protected void updateEnabledState() {
        // Do nothing
    }
}
