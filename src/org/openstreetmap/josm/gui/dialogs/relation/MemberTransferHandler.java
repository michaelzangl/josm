// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.relation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.RelationMemberData;
import org.openstreetmap.josm.gui.datatransfer.RelationMemberTransferable;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.tools.Utils.Function;

/**
 * A transfer handler that helps with importing / exporting members for relations.
 * @since xxx
 */
public class MemberTransferHandler extends TransferHandler {

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        final MemberTable source = (MemberTable) c;
        return new RelationMemberTransferable(source.getMemberTableModel().getSelectedMembers());
    }

    @Override
    public boolean canImport(TransferSupport support) {
        support.setShowDropLocation(true);
        return support.isDataFlavorSupported(RelationMemberTransferable.RELATION_MEMBER_DATA)
                || support.isDataFlavorSupported(PrimitiveTransferData.DATA_FLAVOR);
    }

    @Override
    public boolean importData(TransferSupport support) {
        final MemberTable destination = (MemberTable) support.getComponent();
        final int insertRow;
        if (support.isDrop()) {
            insertRow = ((JTable.DropLocation) support.getDropLocation()).getRow();
        } else {
            insertRow = destination.getSelectedRow();
        }

        try {
            if (support.isDataFlavorSupported(RelationMemberTransferable.RELATION_MEMBER_DATA)) {
                importRelationMemberData(support, destination, insertRow);
            } else if (support.isDataFlavorSupported(PrimitiveTransferData.DATA_FLAVOR)) {
                importPrimitiveData(support, destination, insertRow);
            }
        } catch (IOException | UnsupportedFlavorException e) {
            Main.warn(e);
            return false;
        }

        return true;
    }

    protected void importRelationMemberData(TransferSupport support, final MemberTable destination, int insertRow)
            throws UnsupportedFlavorException, IOException {
        final RelationMemberTransferable.Data memberData = (RelationMemberTransferable.Data)
                support.getTransferable().getTransferData(RelationMemberTransferable.RELATION_MEMBER_DATA);
        importData(destination, insertRow, memberData.getRelationMemberData(), new Function<RelationMemberData, RelationMember>() {
            @Override
            public RelationMember apply(RelationMemberData member) {
                final OsmPrimitive p = destination.getLayer().data.getPrimitiveById(member.getUniqueId(), member.getType());
                if (p == null) {
                    Main.warn(tr("Cannot add {0} since it is not part of dataset", member));
                    return null;
                } else {
                    return new RelationMember(member.getRole(), p);
                }
            }
        });
    }

    protected void importPrimitiveData(TransferSupport support, final MemberTable destination, int insertRow)
            throws UnsupportedFlavorException, IOException {
        final PrimitiveTransferData data = (PrimitiveTransferData)
                support.getTransferable().getTransferData(PrimitiveTransferData.DATA_FLAVOR);
        importData(destination, insertRow, data.getAll(), new Function<PrimitiveData, RelationMember>() {
            @Override
            public RelationMember apply(PrimitiveData data) {
                final OsmPrimitive p = destination.getLayer().data.getPrimitiveById(data);
                if (p == null) {
                    Main.warn(tr("Cannot add {0} since it is not part of dataset", data));
                    return null;
                } else {
                    return destination.getMemberTableModel().getRelationMemberForPrimitive(p);
                }
            }
        });
    }

    protected <T> void importData(MemberTable destination, int insertRow,
                                  Collection<T> memberData, Function<T, RelationMember> toMemberFunction) {
        final Collection<RelationMember> membersToAdd = new ArrayList<>(memberData.size());
        for (T i : memberData) {
            final RelationMember member = toMemberFunction.apply(i);
            if (member != null) {
                membersToAdd.add(member);
            }
        }
        destination.getMemberTableModel().addMembersAtIndexKeepingOldSelection(membersToAdd, insertRow);
    }

    @Override
    protected void exportDone(JComponent sourceComponent, Transferable data, int action) {
        if (action != MOVE) {
            return;
        }
        // Note: RelationMemberTransferable relationData = (RelationMemberTransferable) data;
        final MemberTable source = (MemberTable) sourceComponent;
        final MemberTableModel model = source.getMemberTableModel();
        model.remove(source.getSelectedRows());
        model.selectionChanged(null);
    }
}
