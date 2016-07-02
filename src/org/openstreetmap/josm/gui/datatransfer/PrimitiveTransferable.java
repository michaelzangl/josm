// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveData;

/**
 * Transferable objects for {@link PrimitiveTransferData} objects
 * @since 9369
 * @since xxx Complete rework
 */
public class PrimitiveTransferable implements Transferable {

    private final PrimitiveTransferData primitives;

    /**
     * Constructs a new {@code PrimitiveTransferable}.
     * @param primitives collection of OSM primitives
     */
    public PrimitiveTransferable(PrimitiveTransferData primitives) {
        this.primitives = primitives;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { PrimitiveTransferData.DATA_FLAVOR, TagTransferData.DATA_FLAVOR, DataFlavor.stringFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        DataFlavor[] flavors = getTransferDataFlavors();
        for (DataFlavor f : flavors) {
            if (flavor.equals(f)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return getStringData();
        } else if (PrimitiveTransferData.DATA_FLAVOR.equals(flavor)) {
            return primitives;
        } else if (TagTransferData.DATA_FLAVOR.equals(flavor)) {
            return new TagTransferData(primitives.getDirectlyAdded());
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }

    protected String getStringData() {
        final StringBuilder sb = new StringBuilder();
        for (PrimitiveData primitive : primitives.getAll()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(OsmPrimitiveType.from(primitive).getAPIName()).append(' ').append(primitive.getId());
        }
        return sb.toString().replace("\u200E", "").replace("\u200F", "");
    }
}
