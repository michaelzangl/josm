// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import java.util.List;

/**
 * This class defines a position to insert a given layer in the list of layers.
 * @author Michael Zangl
 */
public abstract class LayerPositionStrategy {

    /**
     * always inserts at the front of the stack.
     */
    public static final LayerPositionStrategy IN_FRONT = new LayerPositionStrategy() {
        @Override
        public int getPosition(LayerManager manager) {
            return 0;
        }
    };

    /**
     * A GPX layer is added below the lowest data layer.
     */
    public static final LayerPositionStrategy AFTER_LAST_DATA_LAYER = new LayerPositionStrategy() {
        @Override
        public int getPosition(LayerManager manager) {
            List<Layer> layers = manager.getLayers();
            for (int i = layers.size() - 1; i >= 0; i--) {
                if (layers.get(i) instanceof OsmDataLayer) {
                    return i + 1;
                }
            }
            return 0;
        }
    };

    /**
     * The default for background layers: They are added before the first background layer in the list. If there is n one, they are added at the end of the list.
     */
    public static final LayerPositionStrategy BEFORE_FIRST_BACKGROUND_LAYER = new LayerPositionStrategy() {
        @Override
        public int getPosition(LayerManager manager) {
            List<Layer> layers = manager.getLayers();
            for (int i = 0; i < layers.size(); i++) {
                if (layers.get(i).isBackgroundLayer()) {
                    return i;
                }
            }
            return layers.size();
        }
    };

    /**
     * Gets the position where the layer should be inserted
     * @param manager The layer manager to insert the layer in.
     * @return The position in the range 0...layers.size
     */
    public abstract int getPosition(LayerManager manager);
}
