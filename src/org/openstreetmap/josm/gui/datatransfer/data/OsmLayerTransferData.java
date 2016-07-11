// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer.data;

import java.awt.datatransfer.DataFlavor;
import java.util.Arrays;
import java.util.List;

import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * A special form of {@link LayerTransferData} that ensures you that the layer is an OSM data layer
 * @author michael
 *
 */
public class OsmLayerTransferData extends LayerTransferData {

    /**
     * This is a data flavor specific for OSM data layers.
     * <p>
     * @see LayerTransferData#FLAVOR
     * @see #FLAVORS
     */
    public static final DataFlavor FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + LayerTransferData.class.getCanonicalName(), "Layer");

    /**
     * The flavors that are supported by this data type.
     */
    public static final List<DataFlavor> FLAVORS = Arrays.asList(FLAVOR, LayerTransferData.FLAVOR);

    private final OsmDataLayer osmLayer;

    /**
     * Create a new {@link OsmLayerTransferData} object
     * @param layerManager The layer manager
     * @param layer The layer that is moved.
     */
    public OsmLayerTransferData(LayerManager layerManager, OsmDataLayer layer) {
        super(layerManager, layer);
        osmLayer = layer;
    }

    public OsmDataLayer getOsmLayer() {
        return osmLayer;
    }

    @Override
    public String toString() {
        return "OsmLayerTransferData [osmLayer=" + osmLayer + "]";
    }
}