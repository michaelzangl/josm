// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer.data;

import java.awt.datatransfer.DataFlavor;

import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;

/**
 * This transferable implements a layer transfer.
 * @author Michael Zangl
 *
 */
public class LayerTransferData {
    /**
     * This is a data flavor for all layer types
     */
    public static final DataFlavor FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + LayerTransferData.class.getCanonicalName(), "Layer");

    private final Layer layer;

    /**
     * Create a new transfer data for the given layer
     * @param layerManager The layer manager that the layer is moved in. May be <code>null</code>
     * @param layer The layer
     */
    public LayerTransferData(LayerManager layerManager, Layer layer) {
        this.layer = layer;
    }

    /**
     * Gets the layer to be transfered.
     * @return The layer
     */
    public Layer getLayer() {
        return layer;
    }

    @Override
    public String toString() {
        return "LayerTransferData [layer=" + layer + "]";
    }
}
