// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.navigate;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.Utils;

/**
 * This class manages the current position on the map and provides utility methods to convert between view and NorthEast coordinates. There are convenience methods to directly convert to LatLon.
 *
 * @author Michael Zangl
 *
 */
public class NavigationModel {
    /**
     * Interface to notify listeners of the change of the zoom area.
     */
    public interface ZoomChangeListener {
        /**
         * Method called when the zoom area has changed.
         * @param navigationModel The model fireing the change.
         * @param oldZoom The old zoom. Might be null on initial zoom.
         * @param newZoom The new zoom.
         */
        void zoomChanged(NavigationModel navigationModel, ZoomData oldZoom, ZoomData newZoom);
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
        public void zoomChanged(NavigationModel navigationModel, ZoomData oldZoom, ZoomData newZoom) {
            ZoomChangeListener listener = l.get();
            if (listener != null) {
                listener.zoomChanged(navigationModel, oldZoom, newZoom);
            } else {
                navigationModel.removeZoomChangeListener(listener);
            }
        }
    }

    /**
     * The mode that is used to zoom to a given position on the map. Modes influence how the zoom undo stack is handled and if smooth zooming is used.
     * @author Michael Zangl
     */
    public enum ScrollMode {
        /**
         * An initial zoom. This resets the zoom history and zooms immediately.
         */
        INITIAL,
        /**
         * Use the default scroll mode.
         */
        DEFAULT,
        /**
         * Immeadiately zoom to the position.
         */
        IMMEDIATE,
        /**
         * Animate a move to the position.
         */
        ANIMATE;

        // Replace this with better methods?
        private boolean resetHistory() {
            return this == INITIAL;
        }

        private int animationTime() {
            if (this == ANIMATE) {
                return 1500;
            } else if (this == DEFAULT) {
                return 300;
            } else {
                return 0;
            }
        }
    }

    /**
     * This stores a position on the screen (relative to one projection).
     * @author michael
     *
     */
    public static class ZoomData {
        /**
         * Center n/e coordinate of the desired screen center using the projection when this object was created.
         */
        private final EastNorth center;
        /**
         * The scale factor in x or y-units per pixel. This means, if scale = 10,
         * every physical pixel on screen are 10 x or 10 y units in the
         * northing/easting space of the projection.
         */
        private final double scale;
        /**
         * The projection used to compute this center.
         */
        private final Projection usedProjection;

        /**
         * Create a new {@link ZoomData} with any content.
         */
        public ZoomData() {
            this(new EastNorth(0, 0), 1);
        }

        /**
         * Interpolates between two zoom data instances.
         * @param otherZoom The other zoom
         * @param proportion How much the other zoom object influences the result (0..1)
         * @param projection The projection to used. Currently, we interpolate in EastNorth coordinates, but this could change (180° problem, ...).
         * @return
         */
        public ZoomData interpolate(ZoomData otherZoom, double proportion, Projection projection) {
            EastNorth from = getCenterEastNorth(projection);
            EastNorth to = otherZoom.getCenterEastNorth(projection);
            EastNorth currentCenter = from.interpolate(to, proportion);
            double currentScale = (1 - proportion) * getScale() + proportion * otherZoom.getScale();
            return new ZoomData(currentCenter, currentScale, projection);
        }

        /**
         * Create a new {@link ZoomData} using no specified projection.
         * @param center The center to store.
         * @param scale The scale to store.
         */
        public ZoomData(EastNorth center, double scale) {
            this(center, scale, null);
        }

        /**
         * Create a new {@link ZoomData} specified using the given projection.
         * @param center The center to store.
         * @param scale The scale to store.
         * @param usedProjection The projection in which the center is.
         */
        public ZoomData(EastNorth center, double scale, Projection usedProjection) {
            this.center = center;
            this.scale = scale;
            this.usedProjection = usedProjection;
        }

        /**
         * Gets the center position.
         * @param projection The projection to use to get the center. If this is not the projection this object was constructed with, the EastNorth position of the center in the new projection is returned.
         * @return The center.
         */
        public EastNorth getCenterEastNorth(Projection projection) {
            if (usedProjection == null || projection == null || usedProjection == projection) {
                return center;
            } else {
                // we need to project the coordinates using the new projection.
                LatLon latlon = usedProjection.eastNorth2latlon(center);
                return projection.latlon2eastNorth(latlon);
            }
        }

        /**
         * Gets the scale.
         * @return The scale.
         */
        public double getScale() {
            return scale;
        }

        public boolean isWithinTolerance(ZoomData otherData) {
            return otherData.center.equals(this.center) && Utils.equalsEpsilon(otherData.scale, scale)
                    && otherData.usedProjection == usedProjection;
        }

        /**
         * Creates a new {@link ZoomData} that uses the new projection as base. This improves performance but has no other impacts on the behavior of the object.
         * @param projection The projection
         * @return A new, optimized {@link ZoomData}
         */
        public ZoomData usingProjection(Projection projection) {
            return new ZoomData(getCenterEastNorth(projection), getScale(), projection);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((center == null) ? 0 : center.hashCode());
            long temp;
            temp = Double.doubleToLongBits(scale);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            result = prime * result + ((usedProjection == null) ? 0 : usedProjection.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ZoomData other = (ZoomData) obj;
            if (center == null) {
                if (other.center != null)
                    return false;
            } else if (!center.equals(other.center))
                return false;
            if (Double.doubleToLongBits(scale) != Double.doubleToLongBits(other.scale))
                return false;
            if (usedProjection == null) {
                if (other.usedProjection != null)
                    return false;
            } else if (!usedProjection.equals(other.usedProjection))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "ZoomData [center=" + center + ", scale=" + scale + ", usedProjection=" + usedProjection + "]";
        }

        /**
         * Gets the affine transform that converts the east/north coordinates to pixel coordinates with no view offset. The center of the view would be at (0,0)
         * @return The current affine transform.
         */
        public AffineTransform getAffineTransform() {
            return new AffineTransform(1.0 / scale, 0.0, 0.0, -1.0 / scale, -center.east() / scale, center.north()
                    / scale);
        }

    }

    private static class ZoomHistoryStack extends Stack<ZoomData> {
        @Override
        public ZoomData push(ZoomData item) {
            ZoomData pushResult = super.push(item);
            if (size() > Main.pref.getInteger("zoom.undo.max", 50)) {
                remove(0);
            }
            return pushResult;
        }
    }

    /**
     * A {@link TimerTask} that is used for zoom to animations.
     * @author Michael Zangl
     *
     */
    private final class AnimateZoomToTimerTask extends TimerTask {
        private final int animationTime;
        private int time = 0;
        private final ZoomData currentZoom;
        private final ZoomData newZoom;

        private AnimateZoomToTimerTask(int animationTime, ZoomData currentZoom, ZoomData newZoom) {
            this.animationTime = animationTime;
            this.currentZoom = currentZoom;
            this.newZoom = newZoom;
        }

        @Override
        public void run() {
            double progress = Math.min((double) time / animationTime, 1);

            // Make animation smooth
            progress = (1 - Math.cos(progress * Math.PI)) / 2;
            final ZoomData position = currentZoom.interpolate(newZoom, progress, getProjection());

            GuiHelper.runInEDT(new Runnable() {
                @Override
                public void run() {
                    realZoomToNoUndo(position, true);
                }
            });

            if (time >= animationTime) {
                cancel();
            } else {
                time += TIMER_PERIOD;
            }
        }
    }

    // 20 FPS should be enough.
    private static final long TIMER_PERIOD = 50;

    /**
     * The current center/scale that is used.
     */
    private ZoomData currentZoom = new ZoomData();

    /**
     * The size of the navigation view. It is used to translate pixel coordinates.
     */
    private Dimension viewDimension = new Dimension(1, 1);

    private final ZoomHistoryStack zoomUndoBuffer = new ZoomHistoryStack();
    private final ZoomHistoryStack zoomRedoBuffer = new ZoomHistoryStack();
    private Date zoomTimestamp = new Date();

    /**
     * the zoom listeners
     */
    private final CopyOnWriteArrayList<ZoomChangeListener> zoomChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * A weak reference to the component of which we are tracking the size. That way, that component can get garbage collected while this model is still active.
     */
    private WeakReference<Component> trackedComponent = new WeakReference<Component>(null);

    private final ComponentAdapter resizeAdapter = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            setViewportSize(e.getComponent().getSize());
        }
    };

    private Timer zoomToTimer;

    /**
     * The zoomTo animation that is currently running.
     */
    private TimerTask currentZoomToAnimation;

    /**
     * Removes a zoom change listener
     *
     * @param listener the listener. Ignored if null or already absent
     */
    public void removeZoomChangeListener(ZoomChangeListener listener) {
        zoomChangeListeners.remove(listener);
    }

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

    protected void fireZoomChanged(ZoomData oldZoom, ZoomData currentZoom) {
        for (ZoomChangeListener l : zoomChangeListeners) {
            l.zoomChanged(this, oldZoom, currentZoom);
        }
    }

    private void pushZoomUndo(ZoomData zoomData) {
        Date now = new Date();
        if ((now.getTime() - zoomTimestamp.getTime()) > (Main.pref.getDouble("zoom.undo.delay", 1.0) * 1000)) {
            zoomUndoBuffer.push(zoomData);
            zoomRedoBuffer.clear();
        }
        zoomTimestamp = now;
    }

    public void zoomPrevious() {
        zoomInBuffer(zoomUndoBuffer, zoomRedoBuffer);
    }

    public void zoomNext() {
        zoomInBuffer(zoomRedoBuffer, zoomUndoBuffer);
    }

    private void zoomInBuffer(ZoomHistoryStack takeFrom, ZoomHistoryStack pushTo) {
        if (!takeFrom.isEmpty()) {
            ZoomData zoom = takeFrom.pop();
            pushTo.push(currentZoom);
            realZoomToNoUndo(zoom.usingProjection(getProjection()), ScrollMode.DEFAULT);
        }
    }

    public boolean hasZoomUndoEntries() {
        return !zoomUndoBuffer.isEmpty();
    }

    public boolean hasZoomRedoEntries() {
        return !zoomRedoBuffer.isEmpty();
    }

    /**
     * @return Returns the center point. A copy is returned, so users cannot
     *      change the center by accessing the return value. Use zoomTo instead.
     */
    public EastNorth getCenter() {
        return currentZoom.getCenterEastNorth(getProjection());
    }

    /**
     * Get the current scale factor. This is [delta in eastnorth]/[pixels].
     * @return The scale.
     */
    public double getScale() {
        return currentZoom.getScale();
    }

    /**
     * Zoom to the given coordinate while preserving the current scale.
     *
     * @param newCenter The center to zoom to.
     * @param mode The animation mode to use for zooming.
     */
    public void zoomTo(EastNorth newCenter, ScrollMode mode) {
        zoomTo(newCenter, getScale(), mode);
    }

    /**
     * Zoom to the given coordinate and scale.
     *
     * @param newCenter The center to zoom to.
     * @param newScale The scale to use.
     */
    public void zoomTo(EastNorth newCenter, double newScale) {
        zoomTo(newCenter, newScale, ScrollMode.DEFAULT);
    }

    /**
     * Zoom to the given coordinate and scale.
     *
     * @param newCenter The center to zoom to.
     * @param newScale The scale to use.
     * @param mode The animation mode to use for zooming.
     */
    public void zoomTo(EastNorth newCenter, double newScale, ScrollMode mode) {
        Bounds b = getProjection().getWorldBoundsLatLon();
        LatLon cl = Projections.inverseProject(newCenter);
        boolean changed = false;
        double lat = cl.lat();
        double lon = cl.lon();
        if (lat < b.getMinLat()) {
            changed = true;
            lat = b.getMinLat();
        } else if (lat > b.getMaxLat()) {
            changed = true;
            lat = b.getMaxLat();
        }
        if (lon < b.getMinLon()) {
            changed = true;
            lon = b.getMinLon();
        } else if (lon > b.getMaxLon()) {
            changed = true;
            lon = b.getMaxLon();
        }
        if (changed) {
            newCenter = Projections.project(new LatLon(lat, lon));
        }
        int centerX = viewDimension.width / 2;
        int centerY = viewDimension.height / 2;
        LatLon l1 = new LatLon(b.getMinLat(), lon);
        LatLon l2 = new LatLon(b.getMaxLat(), lon);
        EastNorth e1 = getProjection().latlon2eastNorth(l1);
        EastNorth e2 = getProjection().latlon2eastNorth(l2);
        double d = e2.north() - e1.north();
        if (centerY > 0 && d < centerY * newScale) {
            double newScaleH = d / centerY;
            e1 = getProjection().latlon2eastNorth(new LatLon(lat, b.getMinLon()));
            e2 = getProjection().latlon2eastNorth(new LatLon(lat, b.getMaxLon()));
            d = e2.east() - e1.east();
            if (centerX > 0 && d < centerX * newScale) {
                newScale = Math.max(newScaleH, d / centerX);
            }
        } else if (centerY > 0) {
            d = d / (l1.greatCircleDistance(l2) * centerY * 10);
            if (newScale < d) {
                newScale = d;
            }
        }

        ZoomData newZoom = new ZoomData(newCenter, newScale, getProjection());
        if (!newZoom.isWithinTolerance(currentZoom)) {
            realZoomTo(newZoom, mode);
        }
    }

    /**
     * Zoom to a position without checking it.
     * @param newZoom The new zoom.
     * @param mode The zoom mode
     */
    private void realZoomTo(ZoomData newZoom, ScrollMode mode) {
        if (mode.resetHistory()) {
            zoomRedoBuffer.clear();
            zoomUndoBuffer.clear();
        } else {
            pushZoomUndo(newZoom);
        }
        realZoomToNoUndo(newZoom, mode);
    }

    private void realZoomToNoUndo(ZoomData newZoom, ScrollMode mode) {
        final int animationTime = mode.animationTime();
        if (animationTime > 0) {
            if (currentZoomToAnimation != null) {
                currentZoomToAnimation.cancel();
            }
            currentZoomToAnimation = new AnimateZoomToTimerTask(animationTime, currentZoom, newZoom);
            if (zoomToTimer == null) {
                zoomToTimer = new Timer("Zoom animation.");
            }
            zoomToTimer.schedule(currentZoomToAnimation, 0, TIMER_PERIOD);
        } else {
            realZoomToNoUndo(newZoom, mode != ScrollMode.INITIAL);
        }
    }

    private void realZoomToNoUndo(ZoomData newZoom, boolean passOldZoomToListeners) {
        if (!newZoom.equals(currentZoom)) {
            ZoomData oldZoom = currentZoom;
            currentZoom = newZoom;
            // XXX: Do not fire if mode is initial ?
            fireZoomChanged(passOldZoomToListeners ? oldZoom : null, currentZoom);
        }
    }

    /**
     * Starts to listen to size change events for that component and adjusts our reference size whenever that component size changed.
     * @param component The component to track.
     */
    public void trackComponentSize(Component component) {
        Component trackedComponent = this.trackedComponent.get();
        if (trackedComponent != null) {
            trackedComponent.removeComponentListener(resizeAdapter);
        }
        component.addComponentListener(resizeAdapter);
        this.trackedComponent = new WeakReference<Component>(component);
        setViewportSize(component.getSize());
    }

    protected void setViewportSize(Dimension size) {
        this.viewDimension = size;
    }

    /**
     * Get the NorthEast coordinate for a given screen position.
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic coordinates from a specific pixel coordination on the screen.
     */
    public EastNorth getEastNorth(double x, double y) {
        return new EastNorth(getCenter().east() + (x - viewDimension.width / 2.0) * getScale(), getCenter().north()
                - (y - viewDimension.height / 2.0) * getScale());
    }

    /**
     * Get the NorthEast coordinate for a given screen position.
     * @param point The screen position
     *
     * @return Geographic coordinates from a specific pixel coordination on the screen.
     */
    public EastNorth getEastNorth(Point2D point) {
        return getEastNorth(point.getX(), point.getY());
    }

    /**
     * Gets an EastNorth position using relative screen coordinates.
     * @param relativeX The x-positon, where the interval [0,1] is the screen width
     * @param relativeY The x-positon, where the interval [0,1] is the screen height
     * @return The geographic coordinates for that pixel.
     */
    public EastNorth getEastNorthRelative(double relativeX, double relativeY) {
        return getEastNorth(relativeX * viewDimension.width, relativeY * viewDimension.height);
    }

    /**
     * Get the lat/lon coordinate for a given screen position.
     * @param point The screen position
     *
     * @return Geographic coordinates from a specific pixel coordination on the screen.
     */
    public LatLon getLatLon(Point2D point) {
        return getProjection().eastNorth2latlon(getEastNorth(point));
    }

    /**
     * Gets the affine transform that converts the east/north coordinates to pixel coordinates.
     * @return The current affine transform. Do not modify it.
     */
    public AffineTransform getAffineTransform() {
        AffineTransform transform = AffineTransform.getTranslateInstance(viewDimension.width / 2,
                viewDimension.height / 2);
        transform.concatenate(currentZoom.getAffineTransform());
        return transform;
    }

    //    /**
    //     * Create a thread that moves the viewport to the given center in an
    //     * animated fashion.
    //     */
    //    public void smoothScrollTo(EastNorth newCenter) {
    //        // FIXME make these configurable.
    //        final int fps = 20;     // animation frames per second
    //        final int speed = 1500; // milliseconds for full-screen-width pan
    //        if (!newCenter.equals(center)) {
    //            final EastNorth oldCenter = center;
    //            final double distance = newCenter.distance(oldCenter) / scale;
    //            final double milliseconds = distance / getWidth() * speed;
    //            final double frames = milliseconds * fps / 1000;
    //            final EastNorth finalNewCenter = newCenter;
    //
    //            new Thread(){
    //                @Override
    //                public void run() {
    //                    for (int i=0; i<frames; i++) {
    //                        // FIXME - not use zoom history here
    //                        zoomTo(oldCenter.interpolate(finalNewCenter, (i+1) / frames));
    //                        try {
    //                            Thread.sleep(1000 / fps);
    //                        } catch (InterruptedException ex) {
    //                            Main.warn("InterruptedException in "+NavigatableComponent.class.getSimpleName()+" during smooth scrolling");
    //                        }
    //                    }
    //                }
    //            }.start();
    //        }
    //    }

    public void zoomToFactorAround(Point2D screenPosition, double factor) {
        double newScale = getScale() * factor;
        // New center position so that point under the mouse pointer stays the same place as it was before zooming
        // You will get the formula by simplifying this expression: newCenter = oldCenter + mouseCoordinatesInNewZoom - mouseCoordinatesInOldZoom
        zoomTo(new EastNorth(getCenter().east() - (screenPosition.getX() - viewDimension.width / 2.0)
                * (newScale - getScale()), getCenter().north() + (screenPosition.getY() - viewDimension.height / 2.0)
                * (newScale - getScale())), newScale);
    }

    /**
     * @return The projection to be used in calculating stuff.
     */
    private Projection getProjection() {
        return Main.getProjection();
    }

    /**
     * Gets the horizontal distance in meters that a line of the length of n pixels would cover in the center of our view.
     * @param pixel The number of pixels the line should have.
     * @return The length in meters.
     */
    public double getPixelDistance(int pixel) {
        double centerX = viewDimension.getWidth() / 2;
        double centerY = viewDimension.getHeight() / 2;
        LatLon ll1 = getLatLon(new Point2D.Double(centerX - pixel / 2.0, centerY));
        LatLon ll2 = getLatLon(new Point2D.Double(centerX + pixel / 2.0, centerY));
        return ll1.greatCircleDistance(ll2);
    }
}
