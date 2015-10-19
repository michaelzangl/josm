// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mapview;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.Date;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.Utils;

/**
 * This class allows us to zoom the map display as required.
 * <p>
 * It provides:
 * <ul>
 * <li> Methods to zoom to a given position/bound/...
 * <li> A zoom undo/redo stack
 * <li> Methods to allow for smooth  (animated) scrolling.
 * </ul>
 * @author michael
 *
 */
public class MapDisplayZoomHelper {

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
         * Animate a smooth, slow move to the position.
         */
        ANIMATE,
        /**
         * Animate a relatively fast change (200ms).
         */
        ANIMATE_FAST;

        // Replace this with better methods?
        private boolean resetHistory() {
            return this == INITIAL;
        }

        private int animationTime() {
            if (this == ANIMATE) {
                return 1500;
            } else if (this == ANIMATE_FAST) {
                return 200;
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
         * Creates a new {@link ZoomData} for the given map display state.
         * @param state The state
         */
        public ZoomData(MapDisplayState state) {
            this(state.getCenter().getEastNorth(), state.getScale(), state.getProjection());
        }

        /**
         * Interpolates between two zoom data instances.
         * @param otherZoom The other zoom
         * @param proportion How much the other zoom object influences the result so that the values (0..1) form a straight line between the two centers.
         * @param projection The projection to used. Currently, we interpolate in EastNorth coordinates, but this could change (180° problem, ...).
         * @return A new, interpolated ZoomData.
         */
        public ZoomData interpolate(ZoomData otherZoom, double proportion, Projection projection) {
            EastNorth from = getCenterEastNorth(projection);
            EastNorth to = otherZoom.getCenterEastNorth(projection);
            EastNorth currentCenter = from.interpolate(to, proportion);
            double currentScale = (1 - proportion) * getScale() + proportion * otherZoom.getScale();
            return new ZoomData(currentCenter, currentScale, projection);
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

        /**
         * Checks if this ZoomData instance is almost the same as an other instance.
         * @param otherData THe other instance.
         * @return <code>true</code> if the centers are the same and the scale only differers a small amount.
         */
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
            // TODO: Sync
            double progress = Math.min((double) time / animationTime, 1);

            // Make animation smooth
            progress = (1 - Math.cos(progress * Math.PI)) / 2;
            final ZoomData position = currentZoom.interpolate(newZoom, progress, model.getState().getProjection());

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
    private Object zoomMutex;

    private Timer zoomToTimer;

    /**
     * The zoomTo animation that is currently running.
     */
    private TimerTask activeZoomToAnimation;

    private final ZoomHistoryStack zoomUndoBuffer = new ZoomHistoryStack();
    private final ZoomHistoryStack zoomRedoBuffer = new ZoomHistoryStack();
    private Date zoomTimestamp = new Date();
    private NavigationModel model;

    protected MapDisplayZoomHelper(NavigationModel model, Object zoomMutex) {
        this.model = model;
        this.zoomMutex = zoomMutex;
    }

    /**
     * Zoom to the given coordinate while preserving the current scale.
     *
     * @param newCenter The center to zoom to.
     * @param mode The animation mode to use for zooming.
     */
    public void zoomTo(EastNorth newCenter, ScrollMode mode) {
        GuiHelper.runInEDTAndWait(new ZoomToTask(Double.NaN, newCenter, mode));
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
        if (newScale <= 0) {
            throw new IllegalArgumentException("Scale (" + newScale + ") may not be negative.");
        }
        GuiHelper.runInEDTAndWait(new ZoomToTask(newScale, newCenter, mode));
    }

    private class ZoomToTask implements Runnable {
        private final double suggestedScale;
        private final EastNorth suggestedCenter;
        private final ScrollMode mode;

        public ZoomToTask(double newScale, EastNorth newCenter, ScrollMode mode) {
            super();
            this.suggestedScale = newScale;
            this.suggestedCenter = newCenter;
            this.mode = mode;
        }

        @Override
        public void run() {
            synchronized (zoomMutex) {
            MapDisplayState state = model.getState();
            double newScale = Double.isNaN(suggestedScale) ? state.getScale() : suggestedScale;
            Bounds b = state.getProjection().getWorldBoundsLatLon();
            EastNorth newCenter = suggestedCenter;
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
            Point2D center = model.getState().getCenter().getOnMapView();

            LatLon l1 = new LatLon(b.getMinLat(), lon);
            LatLon l2 = new LatLon(b.getMaxLat(), lon);
            EastNorth e1 = state.getProjection().latlon2eastNorth(l1);
            EastNorth e2 = state.getProjection().latlon2eastNorth(l2);
            double d = e2.north() - e1.north();
            if (center.getY() > 0 && d < center.getY() * newScale) {
                double newScaleH = d / center.getY();
                e1 = state.getProjection().latlon2eastNorth(new LatLon(lat, b.getMinLon()));
                e2 = state.getProjection().latlon2eastNorth(new LatLon(lat, b.getMaxLon()));
                d = e2.east() - e1.east();
                if (center.getX() > 0 && d < center.getX() * newScale) {
                    newScale = Math.max(newScaleH, d / center.getX());
                }
            } else if (center.getY() > 0) {
                d = d / (l1.greatCircleDistance(l2) * center.getY() * 10);
                if (newScale < d) {
                    newScale = d;
                }
            }

            ZoomData newZoom = new ZoomData(newCenter, suggestedScale, state.getProjection());
            if (!newZoom.isWithinTolerance(new ZoomData(state))) {
                realZoomTo(newZoom, mode);
            }
        }
        }
    }

    /**
     * Zooms around a given point on the screen by a given factor.
     * <p>
     * The EastNorth position below that point on the screen will stay the same.
     * @param viewPosition The position on the screen to zoom around.
     * @param factor The factor to zoom by.
     */
    public void zoomToFactorAround(Point2D viewPosition, double factor) {
        ZoomData state = getCurrentZoom();
        double newScale = state.getScale() * factor;
        Dimension viewDimension = model.getState().getMapViewSize();
        EastNorth center = model.getState().getCenter().getEastNorth();

        // New center position so that point under the mouse pointer stays the same place as it was before zooming
        // You will get the formula by simplifying this expression: newCenter = oldCenter + mouseCoordinatesInNewZoom - mouseCoordinatesInOldZoom
        zoomTo(new EastNorth( center.east() - (viewPosition.getX() - viewDimension.width / 2.0)
                * (newScale - state.getScale()), center.north() + (viewPosition.getY() - viewDimension.height / 2.0)
                * (newScale - state.getScale())), newScale);
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
            if (activeZoomToAnimation != null) {
                activeZoomToAnimation.cancel();
            }
            activeZoomToAnimation = new AnimateZoomToTimerTask(animationTime, getCurrentZoom(), newZoom);
            if (zoomToTimer == null) {
                zoomToTimer = new Timer("Zoom animation.");
            }
            zoomToTimer.schedule(activeZoomToAnimation, 0, TIMER_PERIOD);
        } else {
            realZoomToNoUndo(newZoom, mode != ScrollMode.INITIAL);
        }
    }

    private void realZoomToNoUndo(ZoomData newZoom, boolean passOldZoomToListeners) {
        if (!newZoom.equals(getCurrentZoom())) {
            model.setZoom(newZoom.center, newZoom.scale);
//            ZoomData oldZoom = currentZoom;
//            currentZoom = newZoom;
//            // XXX: Do not fire if mode is initial ?
//            fireZoomChanged(passOldZoomToListeners ? oldZoom : null, currentZoom);
        }
    }

    private ZoomData getCurrentZoom() {
        return new ZoomData(model.getState());
    }

    // ================ Zoom undo and redo ================

    private void pushZoomUndo(ZoomData zoomData) {
        Date now = new Date();
        if ((now.getTime() - zoomTimestamp.getTime()) > (Main.pref.getDouble("zoom.undo.delay", 1.0) * 1000)) {
            zoomUndoBuffer.push(zoomData);
            zoomRedoBuffer.clear();
        }
        zoomTimestamp = now;
    }

    /**
     * Zoom to the previous zoom position. This call is ignored if there is no previous position.
     */
    public void zoomPrevious() {
        zoomInBuffer(zoomUndoBuffer, zoomRedoBuffer);
    }

    /**
     * Zoom to the next zoom position. This call is ignored if there is no next position.
     */
    public void zoomNext() {
        zoomInBuffer(zoomRedoBuffer, zoomUndoBuffer);
    }

    private void zoomInBuffer(ZoomHistoryStack takeFrom, ZoomHistoryStack pushTo) {
        synchronized (zoomMutex) {
            if (!takeFrom.isEmpty()) {
                ZoomData zoom = takeFrom.pop();
                pushTo.push(getCurrentZoom());
                realZoomToNoUndo(zoom.usingProjection(model.getState().getProjection()), ScrollMode.DEFAULT);
            }
        }
    }

    /**
     * Check if there are previous zoom entries.
     * @return <code>true</code> if there are previous zoom entries and {@link #zoomPrevious()} can be used to zoom to them.
     */
    public boolean hasPreviousZoomEntries() {
        return !zoomUndoBuffer.isEmpty();
    }

    /**
     * Check if there are next zoom entries.
     * @return <code>true</code> if there are next zoom entries and {@link #zoomNext()} can be used to zoom to them.
     */
    public boolean hasNextZoomEntries() {
        return !zoomRedoBuffer.isEmpty();
    }
}
