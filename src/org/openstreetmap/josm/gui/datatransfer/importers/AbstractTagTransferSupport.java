// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer.importers;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.swing.TransferHandler.TransferSupport;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * This transfer support allows us to transfer tags.
 * @author Michael Zangl
 * @since xxx
 */
public abstract class AbstractTagTransferSupport extends AbstractDataFlavorSupport {

    AbstractTagTransferSupport(DataFlavor df) {
        super(df);
    }

    @Override
    public boolean importData(TransferSupport support, OsmDataLayer layer, EastNorth pasteAt)
            throws UnsupportedFlavorException, IOException {
        Collection<OsmPrimitive> selection = layer.data.getSelected();
        if (selection.isEmpty()) {
            return false;
        }

        return importTagsOn(support, selection);
    }

    @Override
    public boolean importTagsOn(TransferSupport support, Collection<? extends OsmPrimitive> selection)
            throws UnsupportedFlavorException, IOException {
        ChangePropertyCommand command = new ChangePropertyCommand(selection, getTags(support));
        Main.main.undoRedo.add(command);
        return true;
    }

    /**
     * Gets the tags that should be pasted.
     * @param support The TransferSupport to get the tags from.
     * @return The tags
     * @throws UnsupportedFlavorException
     * @throws IOException
     */
    protected abstract Map<String, String> getTags(TransferSupport support) throws UnsupportedFlavorException, IOException;
}
