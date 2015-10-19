// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mapview;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.util.GuiHelper;

/**
 * This class manages the size and position of a {@link NavigateablePanel}
 * <p>
 * This class is only able to track at most one component.
 * @author Michael Zangl
 */
public class NavigationModel {

    public static class ZoomChangeEvent {
        private final NavigationModel source;
        private final MapDisplayState oldState;
        private final MapDisplayState newState;

        private ZoomChangeEvent(NavigationModel source, MapDisplayState oldState, MapDisplayState newState) {
            super();
            this.source = source;
            this.oldState = oldState;
            this.newState = newState;
        }

        /**
         * The model firing the event,
         * @return The model.
         */
        public NavigationModel getSource() {
            return source;
        }

        /**
         * The view state the view used before this event was fired.
         * @return The old state.
         */
        public MapDisplayState getOldState() {
            return oldState;
        }

        /**
         * The new view state that is used now.
         * @return The new state.
         */
        public MapDisplayState getNewState() {
            return newState;
        }

        @Override
        public String toString() {
            return "ZoomChangeEvent [source=" + source + ", oldState=" + oldState + ", newState=" + newState + "]";
        }
    }

    /**
     * Interface to notify listeners of the change of the zoom area.
     */
    public interface ZoomChangeListener {
        /**
         * Method called when the zoom area, the map view size or the position on the screen was changed.
         * <p>
         * This method is always called in the UI thread.
         * @param e The event.
         */
        void zoomChanged(ZoomChangeEvent e);
    }

    /**
     * This is a weak reference to a zoom listener. The weak reference auto-removes itsef if the referenced zoom change listener is no longer used.
     * @author michael
     *
     */
    public static class WeakZoomChangeListener implements ZoomChangeListener {
        private WeakReference<ZoomChangeListener> l;

        /**
         * Creates a new, weak zoom listener.
         * @param l The listener.
         */
        public WeakZoomChangeListener(ZoomChangeListener l) {
            // Note: We might use reference queues to clear the reference earlier.
            this.l = new WeakReference<>(l);
        }

        @Override
        public void zoomChanged(ZoomChangeEvent e) {
            ZoomChangeListener listener = l.get();
            if (listener != null) {
                listener.zoomChanged(e);
            } else {
                e.getSource().removeZoomChangeListener(listener);
            }
        }
    }

    private final ComponentAdapter resizeAdapter = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            updateViewportSize();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            updateViewportSize();
        }
    };

    /**
     * the zoom listeners
     */
    private final CopyOnWriteArrayList<ZoomChangeListener> zoomChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * A weak reference to the component of which we are tracking the size. That way, that component can get garbage collected while this model is still active.
     */
    private WeakReference<NavigateablePanel> trackedComponent = new WeakReference<NavigateablePanel>(null);

    private MapDisplayPosition position = new MapDisplayPosition(null);

    private MapDisplayState state = new MapDisplayState(Main.getProjection(), position, new EastNorth(0, 0), 1);

    /**
     * The object we synchronize the state against.
     */
    private final Object stateMutex = new Object();

    private final MapDisplayZoomHelper zoomHelper = new MapDisplayZoomHelper(this, stateMutex);

    /**
     * Adds a zoom change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public void addZoomChangeListener(ZoomChangeListener listener) {
        if (listener != null) {
            zoomChangeListeners.addIfAbsent(listener);
        }
    }

    /**
     * Removes a zoom change listener
     *
     * @param listener the listener. Ignored if null or already absent
     */
    public void removeZoomChangeListener(ZoomChangeListener listener) {
        zoomChangeListeners.remove(listener);
    }

    protected void fireZoomChanged(ZoomChangeEvent e) {
        for (ZoomChangeListener l : zoomChangeListeners) {
            l.zoomChanged(e);
        }
    }

    public void setTrackedComponent(NavigateablePanel navigateablePanel) {
        this.trackedComponent = new WeakReference<NavigateablePanel>(navigateablePanel);
        navigateablePanel.addComponentListener(resizeAdapter);
        updateViewportSize();
    }

    private void updateViewportSize() {
        synchronized (stateMutex) {
            NavigateablePanel trackedComponent = this.trackedComponent.get();
            position = new MapDisplayPosition(trackedComponent);
            state = state.usingPosition(position);
        }
    }

    void setZoom(EastNorth center, double scale) {
        GuiHelper.requireEdtThread();
        synchronized (stateMutex) {
            state = state.usingScale(getState().getCenter(), scale).usingCenter(center);
        }
    }

    public MapDisplayState getState() {
        synchronized (stateMutex) {
            return state;
        }
    }

    /**
     * This method should always be used to set the state.
     */
    protected void setState(MapDisplayState state) {
        GuiHelper.requireEdtThread();
        // Be sure to have that lock. Most mehtods calling this should already have it.
        synchronized (stateMutex) {
            MapDisplayState oldState = getState();
            this.state = state;
            ZoomChangeEvent e = new ZoomChangeEvent(this, oldState, getState());
            fireZoomChanged(e);
        }
    }

    /**
     * Gets a class that allows zooming this navigation model.
     * @return A zoom helper.
     */
    public MapDisplayZoomHelper getZoomHelper() {
        return zoomHelper;
    }
}
