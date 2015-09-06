// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.Main;

/**
 * This class handles the layer management.
 * <p>
 * This manager handles a list of layers with the first layer being the front layer.
 * <h1>Threading</h1>
 * Methods of this manager may be called from any thread in any order. Listeners are called while this layer manager is locked, so they should not block.
 *
 * @author Michael Zangl
 */
public class LayerManager {
    /**
     * Interface to notify listeners of a layer change.
     */
    public interface LayerChangeListener {
        /**
         * Notifies this listener that a layer has been added.
         * @param e The new added layer event
         */
        void layerAdded(LayerAddEvent e);

        /**
         * Notifies this listener that a layer is about to be removed.
         * @param e The layer to be removed (as event)
         */
        void layerRemoving(LayerRemoveEvent e);

        /**
         * Notifies this listener that the order of layers was changed.
         * @param e The order change event.
         */
        void layerOrderChanged(LayerOrderChangeEvent e);
    }

    protected static class LayerManagerEvent {
        private final LayerManager source;

        LayerManagerEvent(LayerManager source) {
            this.source = source;
        }

        public LayerManager getSource() {
            return source;
        }
    }

    public static class LayerAddEvent extends LayerManagerEvent {
        private final Layer addedLayer;

        LayerAddEvent(LayerManager source, Layer addedLayer) {
            super(source);
            this.addedLayer = addedLayer;
        }

        public Layer getAddedLayer() {
            return addedLayer;
        }
    }

    public static class LayerRemoveEvent extends LayerManagerEvent {
        private final Layer removedLayer;

        LayerRemoveEvent(LayerManager source, Layer removedLayer) {
            super(source);
            this.removedLayer = removedLayer;
        }

        public Layer getRemovedLayer() {
            return removedLayer;
        }
    }
    public static class LayerOrderChangeEvent extends LayerManagerEvent {
        LayerOrderChangeEvent(LayerManager source) {
            super(source);
        }

    }

    /**
     * This is the list of layers we manage.
     */
    private final List<Layer> layers = new ArrayList<>();

    private final List<LayerChangeListener> layerChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * Add a layer. The layer will be added at a given psoition.
     * @param layer The layer to add
     */
    public synchronized void addLayer(Layer layer) {
        if (containsLayer(layer)) {
            throw new IllegalArgumentException("Cannot add a layer twice.");
        }
        LayerPositionStrategy positionStrategy = layer.getPositionStrategy();
        int position = positionStrategy.getPosition(this);
        insertLayerAt(layer, position);
        fireLayerAdded(layer);
    }

    /**
     * Remove the layer from the mapview. If the layer was in the list before,
     * an LayerChange event is fired.
     * @param layer The layer to remove
     */
    public synchronized void removeLayer(Layer layer) {
        checkContainsLayer(layer);

        fireLayerRemoving(layer);
        layers.remove(layer);
    }

    public synchronized void moveLayer(Layer layer, int position) {
        checkContainsLayer(layer);

        int curLayerPos = layers.indexOf(layer);
        if (position == curLayerPos)
            return; // already in place.
        layers.remove(curLayerPos);
        insertLayerAt(layer, position);
        fireLayerOrderChanged();
    }

    private void insertLayerAt(Layer layer, int position) {
        if (position < 0 || position > layers.size()) {
            throw new IndexOutOfBoundsException("Position " + position + " out of range.");
        }
        if (position == layers.size()) {
            layers.add(layer);
        } else {
            layers.add(position, layer);
        }
    }

    /**
     * Gets an unmodifiable list of all layers that are currently in this manager. This list won't update once layers are added or removed.
     * @return The list of layers.
     */
    public List<Layer> getLayers() {
        return Collections.unmodifiableList(new ArrayList<>(layers));
    }

    /**
     * replies true if the list of layers managed by this map view contain layer
     *
     * @param layer the layer
     * @return true if the list of layers managed by this map view contain layer
     */
    public synchronized boolean containsLayer(Layer layer) {
        return layers.contains(layer);
    }

    protected void checkContainsLayer(Layer layer) {
        if (!containsLayer(layer)) {
            throw new IllegalArgumentException(layer + " is not managed by us.");
        }
    }

    /**
     * Adds a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public synchronized void addLayerChangeListener(LayerChangeListener listener) {
        addLayerChangeListener(listener, false);
    }

    public synchronized void addLayerChangeListener(LayerChangeListener listener, boolean fireAdd) {
        if (layerChangeListeners.contains(listener)) {
            throw new IllegalArgumentException("Listener already registered.");
        }
        layerChangeListeners.add(listener);
        if (fireAdd) {
            for (Layer l : getLayers()) {
                listener.layerAdded(new LayerAddEvent(this, l));
            }
        }
    }

    /**
     * Removes a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public synchronized void removeLayerChangeListener(LayerChangeListener listener) {
        removeLayerChangeListener(listener, false);
    }

    public synchronized void removeLayerChangeListener(LayerChangeListener listener, boolean fireRemove) {
        if (!layerChangeListeners.remove(listener)) {
            //throw new IllegalArgumentException("Listener was not registered before: " + listener);
            Main.error("Listener was not registered before: " + listener);
        } else {
            if (fireRemove) {
                for (Layer l : getLayers()) {
                    listener.layerRemoving(new LayerRemoveEvent(this, l));
                }
            }
        }
    }

    private void fireLayerAdded(Layer layer) {
        LayerAddEvent e = new LayerAddEvent(this, layer);
        for (LayerChangeListener l : layerChangeListeners) {
            l.layerAdded(e);
        }
    }

    private void fireLayerRemoving(Layer layer) {
        LayerRemoveEvent e = new LayerRemoveEvent(this, layer);
        for (LayerChangeListener l : layerChangeListeners) {
            l.layerRemoving(e);
        }
    }

    private void fireLayerOrderChanged() {
        LayerOrderChangeEvent e = new LayerOrderChangeEvent(this);
        for (LayerChangeListener l : layerChangeListeners) {
            l.layerOrderChanged(e);
        }
    }
}
