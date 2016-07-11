// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;

/**
 * This class allows to transfer multiple layers.
 * @author Michael Zangl
 * @since xxx
 */
public class LayerTransferable implements Transferable {

    /**
     * A wrapper for a collection of {@link Layer}.
     */
    public static class Data {
        private final LayerManager manager;
        private final List<Layer> layers;

        /**
         * Create a new data object
         * @param manager The layer manager the layers are from.
         * @param layers The layers.
         */
        public Data(LayerManager manager, List<Layer> layers) {
            super();
            this.manager = manager;
            this.layers = new ArrayList<>(layers);
        }

        public LayerManager getManager() {
            return manager;
        }

        public List<Layer> getLayers() {
            return layers;
        }

        @Override
        public String toString() {
            return "Data [layers=" + layers + "]";
        }
    }

    /**
     * Data flavor for {@link Layer}s which are wrapped in {@link Data}.
     */
    public static final DataFlavor LAYER_DATA = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Data.class.getName(), "Layers");

    private final Data data;

    /**
     * Create a new data object
     * @param manager The layer manager the layers are from.
     * @param layers The layers.
     */
    public LayerTransferable(LayerManager manager, List<Layer> layers) {
        this.data = new Data(manager, layers);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { LAYER_DATA };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return LAYER_DATA.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        } else {
            return data;
        }
    }
}
