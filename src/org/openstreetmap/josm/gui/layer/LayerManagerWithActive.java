// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.data.osm.DataSet;

/**
 * This class extends the layer manager by adding an active and an edit layer.
 * <p>
 * The active layer is the layer the user is currently working on.
 * <p>
 * The edit layer is an data layer that we currently work with.
 * @author Michael Zangl
 */
public class LayerManagerWithActive extends LayerManager {
    /**
     * This listener listens to changes of the active or the edit layer.
     * @author Michael Zangl
     *
     */
    public interface ActiveLayerChangeListener {
        /**
         * Called whenever the active or edit layer changed.
         * <p>
         * You can be sure that this layer is still contained in this set.
         * @param e The change event.
         */
        public void activeOrEditLayerChanged(ActiveLayerChangeEvent e);
    }

    /**
     * This event is fired whenever the active or the edit layer changes.
     * @author Michael Zangl
     */
    public class ActiveLayerChangeEvent extends LayerManagerEvent {

        private final OsmDataLayer previousEditLayer;

        private final Layer previousActiveLayer;

        /**
         * Create a new {@link ActiveLayerChangeEvent}
         * @param source The source
         * @param previousEditLayer the previous edit layer
         * @param previousActiveLayer the previous active layer
         */
        ActiveLayerChangeEvent(LayerManagerWithActive source, OsmDataLayer previousEditLayer,
                Layer previousActiveLayer) {
            super(source);
            this.previousEditLayer = previousEditLayer;
            this.previousActiveLayer = previousActiveLayer;
        }

        /**
         * Gets the edit layer that was previously used.
         * @return The old edit layer, <code>null</code> if there is none.
         */
        public OsmDataLayer getPreviousEditLayer() {
            return previousEditLayer;
        }

        /**
         * Gets the active layer that was previously used.
         * @return The old active layer, <code>null</code> if there is none.
         */
        public Layer getPreviousActiveLayer() {
            return previousActiveLayer;
        }

        @Override
        public LayerManagerWithActive getSource() {
            return (LayerManagerWithActive) super.getSource();
        }
    }

    /**
     * The layer from the layers list that is currently active.
     */
    private Layer activeLayer;

    /**
     * The edit layer is the current active data layer.
     */
    private OsmDataLayer editLayer;

    private final List<ActiveLayerChangeListener> activeLayerChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a active/edit layer change listener
     *
     * @param listener the listener.
     * @param initialFire fire a fake active-layer-changed-event right after adding
     * the listener. The previous layers will be null.
     */
    public void addActiveLayerChangeListener(ActiveLayerChangeListener listener, boolean initialFire) {
        activeLayerChangeListeners.add(listener);
        if (initialFire) {
            listener.activeOrEditLayerChanged(new ActiveLayerChangeEvent(this, null, null));
        }
    }

    /**
     * Removes an active/edit layer change listener.
     * @param listener the listener.
     */
    public void removeActiveLayerChangeListener(ActiveLayerChangeListener listener) {
        activeLayerChangeListeners.remove(listener);
    }

    /**
     * Set the active layer. If the layer is an OsmDataLayer, the edit layer is also changed.
     * @param layer The active layer.
     */
    public synchronized void setActiveLayer(Layer layer) {
        checkContainsLayer(layer);
        setActiveLayer(layer, false);
    }

    private void setActiveLayer(Layer layer, boolean forceEditLayerUpdate) {
        ActiveLayerChangeEvent event = new ActiveLayerChangeEvent(this, editLayer, activeLayer);
        activeLayer = layer;
        if (activeLayer instanceof OsmDataLayer) {
            editLayer = (OsmDataLayer) activeLayer;
        } else if (forceEditLayerUpdate) {
            editLayer = null;
        }
        fireActiveLayerChange(event);
    }

    private void fireActiveLayerChange(ActiveLayerChangeEvent event) {
        if (event.getPreviousActiveLayer() != activeLayer || event.getPreviousEditLayer() != editLayer) {
            for (ActiveLayerChangeListener l : activeLayerChangeListeners) {
                l.activeOrEditLayerChanged(event);
            }
        }
    }

    @Override
    public synchronized void addLayer(Layer layer) {
        super.addLayer(layer);

        // update the active layer automatically.
        if (layer instanceof OsmDataLayer || activeLayer == null) {
            setActiveLayer(layer);
        }
    }

    @Override
    public synchronized void removeLayer(Layer layer) {
        if (layer == activeLayer || layer == editLayer) {
            Layer nextActive = suggestNextActiveLayer(layer);
            setActiveLayer(nextActive, true);
        }

        super.removeLayer(layer);
    }

    /**
     * Determines the next active data layer according to the following
     * rules:
     * <ul>
     *   <li>if there is at least one {@link OsmDataLayer} the first one
     *     becomes active</li>
     *   <li>otherwise, the top most layer of any type becomes active</li>
     * </ul>
     *
     * @param except A layer to ignore.
     * @return the next active data layer
     */
    private Layer suggestNextActiveLayer(Layer except) {
        List<Layer> layersList = new ArrayList<>(getLayers());
        layersList.remove(except);
        // First look for data layer
        for (Layer layer : layersList) {
            if (layer instanceof OsmDataLayer) {
                return layer;
            }
        }

        // Then any layer
        if (!layersList.isEmpty())
            return layersList.get(0);

        // and then give up
        return null;
    }

    /**
     * Replies the currently active layer
     *
     * @return the currently active layer (may be null)
     */
    public synchronized Layer getActiveLayer() {
        return activeLayer;
    }

    /**
     * Replies the current edit layer, if any
     *
     * @return the current edit layer. May be null.
     */
    public synchronized OsmDataLayer getEditLayer() {
        return editLayer;
    }

    /**
     * Gets the data set of the active edit layer.
     * @return That data set, <code>null</code> if there is no edit layer.
     */
    public synchronized DataSet getEditDataSet() {
        return editLayer != null ? editLayer.data : null;
    }


    /**
     * Creates a list of the visible layers in Z-Order, the layer with the lowest Z-Order
     * first, layer with the highest Z-Order last.
     * <p>
     * The active data layer is pulled above all adjacent data layers.
     *
     * @return a list of the visible in Z-Order, the layer with the lowest Z-Order
     * first, layer with the highest Z-Order last.
     */
    public synchronized List<Layer> getVisibleLayersInZOrder() {
        List<Layer> ret = new ArrayList<>();
        // This is set while we delay the addition of the active layer.
        boolean activeLayerDelayed = false;
        List<Layer> layers = getLayers();
        for (ListIterator<Layer> iterator = layers.listIterator(layers.size()); iterator.hasPrevious();) {
            Layer l = iterator.previous();
            if (!l.isVisible()) {
                // ignored
            } else if (l == activeLayer && l instanceof OsmDataLayer) {
                // delay and add after the current block of OsmDataLayer
                activeLayerDelayed = true;
            } else {
                if (activeLayerDelayed && !(l instanceof OsmDataLayer)) {
                    // add active layer before the current one.
                    ret.add(activeLayer);
                    activeLayerDelayed = false;
                }
                // Add this layer now
                ret.add(l);
            }
        }
        if (activeLayerDelayed) {
            ret.add(activeLayer);
        }
        return ret;
    }
}
