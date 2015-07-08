// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.coor.CachedLatLon;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.tools.Utils;

public class NavigationModel {

    double scale = 1;
    /**
     * Center n/e coordinate of the desired screen center.
     */
    protected EastNorth center = new EastNorth(0, 0);

    /**
     * The size of the navigation view. It is used to translate pixel coordinates.
     */
    private Dimension viewDimension = new Dimension(0, 0);

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
        @Override
        public void componentShown(ComponentEvent e) {
            componentResized(e);
        }
    };

    /**
     * @return Returns the center point. A copy is returned, so users cannot
     *      change the center by accessing the return value. Use zoomTo instead.
     */
    public EastNorth getCenter() {
        return center;
    }

    public double getScale() {
        return scale;
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
        if (!size.equals(viewDimension)) {
            this.viewDimension = size;
            // XXX: fireZoomChanged(currentZoom, currentZoom);
        }
    }
    /**
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic coordinates from a specific pixel coordination on the screen.
     */
    public EastNorth getEastNorth(int x, int y) {
        return new EastNorth(
                center.east() + (x - getWidth()/2.0)*scale,
                center.north() - (y - getHeight()/2.0)*scale);
    }

    public ProjectionBounds getProjectionBounds() {
        return new ProjectionBounds(getEastNorth(0, getHeight()), getEastNorth(getWidth(), 0));
    }


    /* FIXME: replace with better method - used by Main to reset Bounds when projection changes, don't use otherwise */
    public Bounds getRealBounds() {
        return new Bounds(
                getProjection().eastNorth2latlon(new EastNorth(
                        center.east() - getWidth()/2.0*scale,
                        center.north() - getHeight()/2.0*scale)),
                        getProjection().eastNorth2latlon(new EastNorth(
                                center.east() + getWidth()/2.0*scale,
                                center.north() + getHeight()/2.0*scale)));
    }

    public AffineTransform getAffineTransform() {
        return new AffineTransform(
                1.0/scale, 0.0, 0.0, -1.0/scale, getWidth()/2.0 - center.east()/scale, getHeight()/2.0 + center.north()/scale);
    }

    /**
     * Return the point on the screen where this Coordinate would be.
     * @param p The point, where this geopoint would be drawn.
     * @return The point on screen where "point" would be drawn, relative
     *      to the own top/left.
     */
    public Point2D getPoint2D(EastNorth p) {
        if (null == p)
            return new Point();
        double x = (p.east()-center.east())/scale + getWidth()/2d;
        double y = (center.north()-p.north())/scale + getHeight()/2d;
        return new Point2D.Double(x, y);
    }

    public Point2D getPoint2D(LatLon latlon) {
        if (latlon == null)
            return new Point();
        else if (latlon instanceof CachedLatLon)
            return getPoint2D(((CachedLatLon) latlon).getEastNorth());
        else
            return getPoint2D(getProjection().latlon2eastNorth(latlon));
    }

    /**
     * Zoom to the given coordinate and scale.
     *
     * @param newCenter The center x-value (easting) to zoom to.
     * @param newScale The scale to use.
     */
    public void zoomTo(EastNorth newCenter, double newScale) {
        zoomTo(newCenter, newScale, false);
    }

    /**
     * Zoom to the given coordinate and scale.
     *
     * @param newCenter The center x-value (easting) to zoom to.
     * @param newScale The scale to use.
     * @param initial true if this call initializes the viewport.
     */
    public void zoomTo(EastNorth newCenter, double newScale, boolean initial) {
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
        int width = getWidth()/2;
        int height = getHeight()/2;
        LatLon l1 = new LatLon(b.getMinLat(), lon);
        LatLon l2 = new LatLon(b.getMaxLat(), lon);
        EastNorth e1 = getProjection().latlon2eastNorth(l1);
        EastNorth e2 = getProjection().latlon2eastNorth(l2);
        double d = e2.north() - e1.north();
        if (height > 0 && d < height*newScale) {
            double newScaleH = d/height;
            e1 = getProjection().latlon2eastNorth(new LatLon(lat, b.getMinLon()));
            e2 = getProjection().latlon2eastNorth(new LatLon(lat, b.getMaxLon()));
            d = e2.east() - e1.east();
            if (width > 0 && d < width*newScale) {
                newScale = Math.max(newScaleH, d/width);
            }
        } else if (height > 0) {
            d = d/(l1.greatCircleDistance(l2)*height*10);
            if (newScale < d) {
                newScale = d;
            }
        }

        if (!newCenter.equals(center) || !Utils.equalsEpsilon(scale, newScale)) {
            if (!initial) {
                pushZoomUndo(center, scale);
            }
            zoomNoUndoTo(newCenter, newScale, initial);
        }
    }

    /**
     * Zoom to the given coordinate without adding to the zoom undo buffer.
     *
     * @param newCenter The center x-value (easting) to zoom to.
     * @param newScale The scale to use.
     * @param initial true if this call initializes the viewport.
     */
    private void zoomNoUndoTo(EastNorth newCenter, double newScale, boolean initial) {
        EastNorth oldCenter = getCenter();
        double oldScale = getScale();
        if (!newCenter.equals(center)) {
            center = newCenter;
        }
        if (!Utils.equalsEpsilon(scale, newScale)) {
            scale = newScale;
        }
        ((NavigatableComponent) trackedComponent.get()).onZoomNoUndoTo(oldCenter, newCenter, oldScale, newScale, initial);

        if (!initial) {
            fireZoomChanged();
        }
    }

    public void zoomTo(EastNorth newCenter) {
        zoomTo(newCenter, scale);
    }

    public void zoomTo(LatLon newCenter) {
        zoomTo(Projections.project(newCenter));
    }

    public void smoothScrollTo(LatLon newCenter) {
        smoothScrollTo(Projections.project(newCenter));
    }

    /**
     * Create a thread that moves the viewport to the given center in an
     * animated fashion.
     */
    public void smoothScrollTo(EastNorth newCenter) {
        // FIXME make these configurable.
        final int fps = 20;     // animation frames per second
        final int speed = 1500; // milliseconds for full-screen-width pan
        if (!newCenter.equals(center)) {
            final EastNorth oldCenter = center;
            final double distance = newCenter.distance(oldCenter) / scale;
            final double milliseconds = distance / getWidth() * speed;
            final double frames = milliseconds * fps / 1000;
            final EastNorth finalNewCenter = newCenter;

            new Thread() {
                @Override
                public void run() {
                    for (int i = 0; i < frames; i++) {
                        // FIXME - not use zoom history here
                        zoomTo(oldCenter.interpolate(finalNewCenter, (i+1) / frames));
                        try {
                            Thread.sleep(1000 / fps);
                        } catch (InterruptedException ex) {
                            Main.warn("InterruptedException in "+NavigatableComponent.class.getSimpleName()+" during smooth scrolling");
                        }
                    }
                }
            }.start();
        }
    }

    public void zoomToFactor(double x, double y, double factor) {
        double newScale = scale*factor;
        // New center position so that point under the mouse pointer stays the same place as it was before zooming
        // You will get the formula by simplifying this expression: newCenter = oldCenter + mouseCoordinatesInNewZoom - mouseCoordinatesInOldZoom
        zoomTo(new EastNorth(
                center.east() - (x - getWidth()/2.0) * (newScale - scale),
                center.north() + (y - getHeight()/2.0) * (newScale - scale)),
                newScale);
    }

    public void zoomToFactor(EastNorth newCenter, double factor) {
        zoomTo(newCenter, scale*factor);
    }

    public void zoomToFactor(double factor) {
        zoomTo(center, scale*factor);
    }

    public void zoomTo(ProjectionBounds box) {
        // -20 to leave some border
        int w = getWidth()-20;
        if (w < 20) {
            w = 20;
        }
        int h = getHeight()-20;
        if (h < 20) {
            h = 20;
        }

        double scaleX = (box.maxEast-box.minEast)/w;
        double scaleY = (box.maxNorth-box.minNorth)/h;
        double newScale = Math.max(scaleX, scaleY);

        zoomTo(box.getCenter(), newScale);
    }

    public void zoomTo(Bounds box) {
        zoomTo(new ProjectionBounds(getProjection().latlon2eastNorth(box.getMin()),
                getProjection().latlon2eastNorth(box.getMax())));
    }

    public void zoomTo(ViewportData viewport) {
        if (viewport == null) return;
        if (viewport.getBounds() != null) {
            BoundingXYVisitor box = new BoundingXYVisitor();
            box.visit(viewport.getBounds());
            zoomTo(box);
        } else {
            zoomTo(viewport.getCenter(), viewport.getScale(), true);
        }
    }

    /**
     * Set the new dimension to the view.
     */
    public void zoomTo(BoundingXYVisitor box) {
        if (box == null) {
            box = new BoundingXYVisitor();
        }
        if (box.getBounds() == null) {
            box.visit(getProjection().getWorldBoundsLatLon());
        }
        if (!box.hasExtend()) {
            box.enlargeBoundingBox();
        }

        zoomTo(box.getBounds());
    }

    private class ZoomData {
        private final LatLon center;
        private final double scale;

        public ZoomData(EastNorth center, double scale) {
            this.center = Projections.inverseProject(center);
            this.scale = scale;
        }

        public EastNorth getCenterEastNorth() {
            return getProjection().latlon2eastNorth(center);
        }

        public double getScale() {
            return scale;
        }
    }

    private Stack<ZoomData> zoomUndoBuffer = new Stack<>();
    private Stack<ZoomData> zoomRedoBuffer = new Stack<>();
    private Date zoomTimestamp = new Date();

    private void pushZoomUndo(EastNorth center, double scale) {
        Date now = new Date();
        if ((now.getTime() - zoomTimestamp.getTime()) > (Main.pref.getDouble("zoom.undo.delay", 1.0) * 1000)) {
            zoomUndoBuffer.push(new ZoomData(center, scale));
            if (zoomUndoBuffer.size() > Main.pref.getInteger("zoom.undo.max", 50)) {
                zoomUndoBuffer.remove(0);
            }
            zoomRedoBuffer.clear();
        }
        zoomTimestamp = now;
    }

    public void zoomPrevious() {
        if (!zoomUndoBuffer.isEmpty()) {
            ZoomData zoom = zoomUndoBuffer.pop();
            zoomRedoBuffer.push(new ZoomData(center, scale));
            zoomNoUndoTo(zoom.getCenterEastNorth(), zoom.getScale(), false);
        }
    }

    public void zoomNext() {
        if (!zoomRedoBuffer.isEmpty()) {
            ZoomData zoom = zoomRedoBuffer.pop();
            zoomUndoBuffer.push(new ZoomData(center, scale));
            zoomNoUndoTo(zoom.getCenterEastNorth(), zoom.getScale(), false);
        }
    }

    public boolean hasZoomUndoEntries() {
        return !zoomUndoBuffer.isEmpty();
    }

    public boolean hasZoomRedoEntries() {
        return !zoomRedoBuffer.isEmpty();
    }

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

    protected void fireZoomChanged() {
        for (ZoomChangeListener l : zoomChangeListeners) {
            l.zoomChanged();
        }
    }

    /**
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic unprojected coordinates from a specific pixel coordination
     *      on the screen.
     */
    public LatLon getLatLon(int x, int y) {
        return getProjection().eastNorth2latlon(getEastNorth(x, y));
    }

    public double getDist100Pixel() {
        int w = getWidth()/2;
        int h = getHeight()/2;
        LatLon ll1 = getLatLon(w-50, h);
        LatLon ll2 = getLatLon(w+50, h);
        return ll1.greatCircleDistance(ll2);
    }

    private int getWidth() {
        return (int) viewDimension.getWidth();
    }

    private int getHeight() {
        return (int) viewDimension.getHeight();
    }

    private Projection getProjection() {
        return Main.getProjection();
    }

}
