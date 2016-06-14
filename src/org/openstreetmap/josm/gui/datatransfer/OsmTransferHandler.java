// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.TransferHandler;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.OpenFileAction;
import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.NodeData;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.RelationData;
import org.openstreetmap.josm.data.osm.RelationMemberData;
import org.openstreetmap.josm.data.osm.WayData;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferable.Data;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * This transfer hanlder provides the ability to transfer OSM data. It allows you to receive files and {@link Data} objects.
 * @author Michael Zangl
 */
public class OsmTransferHandler extends TransferHandler {

    private abstract static class AbstractDataFlavorSupport {
        protected final DataFlavor df;

        AbstractDataFlavorSupport(DataFlavor df) {
            this.df = df;
        }

        protected boolean supports(TransferSupport support) {
            return support.isDataFlavorSupported(df) && isCopy(support);
        }

        private boolean isCopy(TransferSupport support) {
            return !support.isDrop() || (COPY & support.getSourceDropActions()) == COPY;
        }

        protected abstract boolean importData(TransferSupport support, OsmDataLayer layer, EastNorth pasteAt)
                throws UnsupportedFlavorException, IOException;
    }

    private static Collection<AbstractDataFlavorSupport> SUPPORTED = Arrays
            .asList(new AbstractDataFlavorSupport(DataFlavor.javaFileListFlavor) {
                @Override
                public boolean importData(TransferSupport support, OsmDataLayer layer, EastNorth pasteAt)
                        throws UnsupportedFlavorException, IOException {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(df);
                    OpenFileAction.OpenFileTask task = new OpenFileAction.OpenFileTask(files, null);
                    task.setRecordHistory(true);
                    Main.worker.submit(task);
                    return true;
                }
            }, new AbstractDataFlavorSupport(PrimitiveTransferData.DATA_FLAVOR) {
                @Override
                public boolean importData(TransferSupport support, final OsmDataLayer layer, EastNorth pasteAt)
                        throws UnsupportedFlavorException, IOException {
                    PrimitiveTransferData pasteBuffer = (PrimitiveTransferData) support.getTransferable()
                            .getTransferData(df);
                    // Allow to cancel paste if there are incomplete primitives
                    if (pasteBuffer.hasIncompleteData() && !confirmDeleteIncomplete()) {
                        return false;
                    }

                    EastNorth center = pasteBuffer.getCenter();
                    EastNorth offset = pasteAt.subtract(center);

                    // Make a copy of pasteBuffer and map from old id to copied data id
                    List<PrimitiveData> bufferCopy = new ArrayList<>();
                    List<PrimitiveData> toSelect = new ArrayList<>();
                    Map<Long, Long> newNodeIds = new HashMap<>();
                    Map<Long, Long> newWayIds = new HashMap<>();
                    Map<Long, Long> newRelationIds = new HashMap<>();
                    for (PrimitiveData data : pasteBuffer.getAll()) {
                        if (data.isIncomplete()) {
                            continue;
                        }
                        PrimitiveData copy = data.makeCopy();
                        copy.clearOsmMetadata();
                        if (data instanceof NodeData) {
                            newNodeIds.put(data.getUniqueId(), copy.getUniqueId());
                        } else if (data instanceof WayData) {
                            newWayIds.put(data.getUniqueId(), copy.getUniqueId());
                        } else if (data instanceof RelationData) {
                            newRelationIds.put(data.getUniqueId(), copy.getUniqueId());
                        }
                        bufferCopy.add(copy);
                        if (pasteBuffer.getDirectlyAdded().contains(data)) {
                            toSelect.add(copy);
                        }
                    }

                    // Update references in copied buffer
                    for (PrimitiveData data : bufferCopy) {
                        if (data instanceof NodeData) {
                            NodeData nodeData = (NodeData) data;
                            nodeData.setEastNorth(nodeData.getEastNorth().add(offset));
                        } else if (data instanceof WayData) {
                            List<Long> newNodes = new ArrayList<>();
                            for (Long oldNodeId : ((WayData) data).getNodes()) {
                                Long newNodeId = newNodeIds.get(oldNodeId);
                                if (newNodeId != null) {
                                    newNodes.add(newNodeId);
                                }
                            }
                            ((WayData) data).setNodes(newNodes);
                        } else if (data instanceof RelationData) {
                            List<RelationMemberData> newMembers = new ArrayList<>();
                            for (RelationMemberData member : ((RelationData) data).getMembers()) {
                                OsmPrimitiveType memberType = member.getMemberType();
                                Long newId;
                                switch (memberType) {
                                case NODE:
                                    newId = newNodeIds.get(member.getMemberId());
                                    break;
                                case WAY:
                                    newId = newWayIds.get(member.getMemberId());
                                    break;
                                case RELATION:
                                    newId = newRelationIds.get(member.getMemberId());
                                    break;
                                default:
                                    throw new AssertionError();
                                }
                                if (newId != null) {
                                    newMembers.add(new RelationMemberData(member.getRole(), memberType, newId));
                                }
                            }
                            ((RelationData) data).setMembers(newMembers);
                        }
                    }

                    /* Now execute the commands to add the duplicated contents of the paste buffer to the map */
                    Main.main.undoRedo.add(new AddPrimitivesCommand(bufferCopy, toSelect) {
                        @Override
                        protected OsmDataLayer getLayer() {
                            return layer;
                        }
                    });
                    Main.map.mapView.repaint();
                    return true;
                }
            });


    @Override
    public boolean canImport(TransferSupport support) {
        // import everything for now, only support copy.
        for (AbstractDataFlavorSupport df : SUPPORTED) {
            if (df.supports(support)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
        return importData(support, Main.getLayerManager().getEditLayer(), null);
    }

    private boolean importData(TransferSupport support, OsmDataLayer layer, EastNorth center) {
        for (AbstractDataFlavorSupport df : SUPPORTED) {
            if (df.supports(support)) {
                try {
                    df.importData(support, layer, center);
                    return true;
                } catch (UnsupportedFlavorException | IOException e) {
                    Main.warn(e);
                }
            }
        }
        return super.importData(support);
    }

    /**
     * Invoke a copy for the given data.
     * @param data The data to copy.
     */
    public static void copy(final PrimitiveTransferData data) {
        getClippboard().setContents(new Transferable() {
            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == PrimitiveTransferData.DATA_FLAVOR;
            }

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] { PrimitiveTransferData.DATA_FLAVOR };
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                if (flavor == PrimitiveTransferData.DATA_FLAVOR) {
                    return data;
                } else {
                    throw new UnsupportedFlavorException(flavor);
                }
            }
        }, null);
    }

    public void pasteOn(OsmDataLayer editLayer, EastNorth mPosition) {
        Transferable transferable = getClippboard().getContents(null);
        importData(new TransferSupport(Main.panel, transferable), editLayer, mPosition);
    }

    private static boolean confirmDeleteIncomplete() {
        ExtendedDialog ed = new ExtendedDialog(Main.parent, tr("Delete incomplete members?"),
                new String[] { tr("Paste without incomplete members"), tr("Cancel") });
        ed.setButtonIcons(new String[] { "dialogs/relation/deletemembers", "cancel" });
        ed.setContent(tr(
                "The copied data contains incomplete objects.  " + "When pasting the incomplete objects are removed.  "
                        + "Do you want to paste the data without the incomplete objects?"));
        ed.showDialog();
        return ed.getValue() == 1;
    }

    private static Clipboard getClippboard() {
        //TODO: Might be unsupported in some cases, we need a fake clippboard then.
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }
}
