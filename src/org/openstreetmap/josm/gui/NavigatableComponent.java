// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

import javax.swing.JComponent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.SystemOfMeasurement;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.coor.CachedLatLon;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.help.Helpful;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.navigate.GlobalSoM;
import org.openstreetmap.josm.gui.navigate.NavigationModel;
import org.openstreetmap.josm.gui.navigate.NavigationModel.ScrollMode;
import org.openstreetmap.josm.gui.navigate.NavigationModel.WeakZoomChangeListener;
import org.openstreetmap.josm.gui.navigate.NavigationModel.ZoomData;
import org.openstreetmap.josm.tools.Predicate;
import org.openstreetmap.josm.tools.Utils;

/**
 * A component that can be navigated by a {@link MapMover}. Used as map view and for the
 * zoomer in the download dialog.
 *
 * @author imi
 * @since 41
 */
public class NavigatableComponent extends JComponent implements Helpful, NavigationModel.ZoomChangeListener {

    /**
     * Interface to notify listeners of the change of the zoom area.
     */
    public interface ZoomChangeListener {
        /**
         * Method called when the zoom area has changed.
         */
        void zoomChanged();
    }

    private static final class ZoomChangeAdapter implements NavigationModel.ZoomChangeListener {

        private ZoomChangeListener listener;

        public  ZoomChangeAdapter(ZoomChangeListener listener) {
            this.listener = listener;
        }

        @Override
        public void zoomChanged(NavigationModel navigationModel, ZoomData oldZoom, ZoomData newZoom) {
            listener.zoomChanged();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((listener == null) ? 0 : listener.hashCode());
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
            ZoomChangeAdapter other = (ZoomChangeAdapter) obj;
            if (listener == null) {
                if (other.listener != null)
                    return false;
            } else if (!listener.equals(other.listener))
                return false;
            return true;
        }
    }

    /**
     * Interface to notify listeners of the change of the system of measurement.
     * @since 6056
     */
    public interface SoMChangeListener extends GlobalSoM.SoMChangeListener {
    }

    // XXX: Why is this here?
    public transient Predicate<OsmPrimitive> isSelectablePredicate = new Predicate<OsmPrimitive>() {
        @Override
        public boolean evaluate(OsmPrimitive prim) {
            if (!prim.isSelectable()) return false;
            // if it isn't displayed on screen, you cannot click on it
            MapCSSStyleSource.STYLE_SOURCE_LOCK.readLock().lock();
            try {
                return !MapPaintStyles.getStyles().get(prim, getDist100Pixel(), NavigatableComponent.this).isEmpty();
            } finally {
                MapCSSStyleSource.STYLE_SOURCE_LOCK.readLock().unlock();
            }
        }
    };

    public static final IntegerProperty PROP_SNAP_DISTANCE = new IntegerProperty("mappaint.node.snap-distance", 10);

    public static final String PROPNAME_CENTER = "center";
    public static final String PROPNAME_SCALE  = "scale";

    /**
     * This is the navigation model for the one single map view.
     * Due to backwards compatibility (zoom change listeners, ...), we use a static field here.
     */
    private static final NavigationModel defaultNavigationModel = new NavigationModel();

    /**
     * Removes a zoom change listener
     *
     * @param listener the listener. Ignored if null or already absent
     */
    public static void removeZoomChangeListener(NavigatableComponent.ZoomChangeListener listener) {
        defaultNavigationModel.removeZoomChangeListener(new ZoomChangeAdapter(listener));
    }

    /**
     * Adds a zoom change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public static void addZoomChangeListener(NavigatableComponent.ZoomChangeListener listener) {
        defaultNavigationModel.addZoomChangeListener(new ZoomChangeAdapter(listener));
    }

    /**
     * Removes a SoM change listener
     *
     * @param listener the listener. Ignored if null or already absent
     * @since 6056
     */
    public static void removeSoMChangeListener(NavigatableComponent.SoMChangeListener listener) {
        GlobalSoM.removeSoMChangeListener(listener);
    }

    /**
     * Adds a SoM change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @since 6056
     */
    public static void addSoMChangeListener(NavigatableComponent.SoMChangeListener listener) {
        GlobalSoM.addSoMChangeListener(listener);
    }

    /**
     * Returns the current system of measurement.
     * @return The current system of measurement (metric system by default).
     * @since 3490
     */
    public static SystemOfMeasurement getSystemOfMeasurement() {
        return GlobalSoM.getSystemOfMeasurement();
    }

    /**
     * Sets the current system of measurement.
     * @param somKey The system of measurement key. Must be defined in {@link SystemOfMeasurement#ALL_SYSTEMS}.
     * @throws IllegalArgumentException if {@code somKey} is not known
     * @since 6056
     */
    public static void setSystemOfMeasurement(String somKey) {
        GlobalSoM.setSystemOfMeasurement(somKey);
    }

    private final transient Object paintRequestLock = new Object();
    private Rectangle paintRect = null;
    private Polygon paintPoly = null;

    protected transient ViewportData initialViewport;

    private final NavigationModel navigationModel;

    private transient final NavigationModel.ZoomChangeListener weakZoomListener = new WeakZoomChangeListener(this);

    /**
     * Constructs a new {@code NavigatableComponent} using the static default {@link NavigationModel} and zooming to the current bounds,
     */
    public NavigatableComponent() {
        this(defaultNavigationModel);
        defaultNavigationModel.zoomTo(
                calculateDefaultCenter(),
                Main.getProjection().getDefaultZoomInPPD()
                );
    }

    /**
     * Constructs a new {@code NavigatableComponent}
     * @param navigationModel The navigation model to use.
     */
    public NavigatableComponent(NavigationModel navigationModel) {
        this.navigationModel = navigationModel;
        setLayout(null);
        navigationModel.addZoomChangeListener(weakZoomListener);
        navigationModel.trackComponentSize(this);
    }

    protected DataSet getCurrentDataSet() {
        return Main.main.getCurrentDataSet();
    }

    private EastNorth calculateDefaultCenter() {
        Bounds b = DownloadDialog.getSavedDownloadBounds();
        if (b == null) {
            b = Main.getProjection().getWorldBoundsLatLon();
        }
        return Main.getProjection().latlon2eastNorth(b.getCenter());
    }

    /**
     * Returns the text describing the given distance in the current system of measurement.
     * @param dist The distance in metres.
     * @return the text describing the given distance in the current system of measurement.
     * @since 3406
     */
    public static String getDistText(double dist) {
        return getSystemOfMeasurement().getDistText(dist);
    }

    /**
     * Returns the text describing the given distance in the current system of measurement.
     * @param dist The distance in metres
     * @param format A {@link NumberFormat} to format the area value
     * @param threshold Values lower than this {@code threshold} are displayed as {@code "< [threshold]"}
     * @return the text describing the given distance in the current system of measurement.
     * @since 7135
     */
    public static String getDistText(final double dist, final NumberFormat format, final double threshold) {
        return getSystemOfMeasurement().getDistText(dist, format, threshold);
    }

    /**
     * Returns the text describing the given area in the current system of measurement.
     * @param area The distance in square metres.
     * @return the text describing the given area in the current system of measurement.
     * @since 5560
     */
    public static String getAreaText(double area) {
        return getSystemOfMeasurement().getAreaText(area);
    }

    /**
     * Returns the text describing the given area in the current system of measurement.
     * @param area The area in square metres
     * @param format A {@link NumberFormat} to format the area value
     * @param threshold Values lower than this {@code threshold} are displayed as {@code "< [threshold]"}
     * @return the text describing the given area in the current system of measurement.
     * @since 7135
     */
    public static String getAreaText(final double area, final NumberFormat format, final double threshold) {
        return getSystemOfMeasurement().getAreaText(area, format, threshold);
    }

    public String getDist100PixelText() {
        return getDistText(getDist100Pixel());
    }

    public double getDist100Pixel() {
        int w = getWidth()/2;
        int h = getHeight()/2;
        LatLon ll1 = getLatLon(w-50,h);
        LatLon ll2 = getLatLon(w+50,h);
        return ll1.greatCircleDistance(ll2);
    }

    /**
     * @return Returns the center point. A copy is returned, so users cannot
     *      change the center by accessing the return value. Use zoomTo instead.
     */
    public EastNorth getCenter() {
        return navigationModel.getCenter();
    }

    /**
     * Get the current scale factor. This is [delta in eastnorth]/[pixels].
     * @return The scale.
     */
    public double getScale() {
        return navigationModel.getScale();
    }

    /**
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic coordinates from a specific pixel coordination on the screen.
     */
    public EastNorth getEastNorth(int x, int y) {
        return navigationModel.getEastNorth(x, y);
    }

    public ProjectionBounds getProjectionBounds() {
        return new ProjectionBounds(getEastNorth(0, getHeight()), getEastNorth(getWidth(), 0));
    }

    /* FIXME: replace with better method - used by MapSlider */
    public ProjectionBounds getMaxProjectionBounds() {
        Bounds b = getProjection().getWorldBoundsLatLon();
        return new ProjectionBounds(getProjection().latlon2eastNorth(b.getMin()),
                getProjection().latlon2eastNorth(b.getMax()));
    }

    /* FIXME: replace with better method - used by Main to reset Bounds when projection changes, don't use otherwise */
    public Bounds getRealBounds() {
        return new Bounds(
                getProjection().eastNorth2latlon(getEastNorth(0, getHeight())),
                        getProjection().eastNorth2latlon(getEastNorth(getWidth(), 0)));
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

    public LatLon getLatLon(double x, double y) {
        return getLatLon((int)x, (int)y);
    }

    /**
     * @param r
     * @return Minimum bounds that will cover rectangle
     */
    public Bounds getLatLonBounds(Rectangle r) {
        // TODO Maybe this should be (optional) method of Projection implementation
        EastNorth p1 = getEastNorth(r.x, r.y);
        EastNorth p2 = getEastNorth(r.x + r.width, r.y + r.height);

        Bounds result = new Bounds(Main.getProjection().eastNorth2latlon(p1));

        double eastMin = Math.min(p1.east(), p2.east());
        double eastMax = Math.max(p1.east(), p2.east());
        double northMin = Math.min(p1.north(), p2.north());
        double northMax = Math.max(p1.north(), p2.north());
        double deltaEast = (eastMax - eastMin) / 10;
        double deltaNorth = (northMax - northMin) / 10;

        for (int i=0; i < 10; i++) {
            result.extend(Main.getProjection().eastNorth2latlon(new EastNorth(eastMin + i * deltaEast, northMin)));
            result.extend(Main.getProjection().eastNorth2latlon(new EastNorth(eastMin + i * deltaEast, northMax)));
            result.extend(Main.getProjection().eastNorth2latlon(new EastNorth(eastMin, northMin  + i * deltaNorth)));
            result.extend(Main.getProjection().eastNorth2latlon(new EastNorth(eastMax, northMin  + i * deltaNorth)));
        }

        return result;
    }

    /**
     * Gets the affine transform that converts the east/north coordinates to pixel coordinates.
     * @return The current affine transform.
     */
    public AffineTransform getAffineTransform() {
        return navigationModel.getAffineTransform();
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
        Point2D p2d = new Point2D.Double(p.east(), p.north());
        return getAffineTransform().transform(p2d, null);
    }

    public Point2D getPoint2D(LatLon latlon) {
        if (latlon == null)
            return new Point();
        else if (latlon instanceof CachedLatLon)
            return getPoint2D(((CachedLatLon)latlon).getEastNorth());
        else
            return getPoint2D(getProjection().latlon2eastNorth(latlon));
    }

    public Point2D getPoint2D(Node n) {
        return getPoint2D(n.getEastNorth());
    }

    // looses precision, may overflow (depends on p and current scale)
    //@Deprecated
    public Point getPoint(EastNorth p) {
        Point2D d = getPoint2D(p);
        return new Point((int) d.getX(), (int) d.getY());
    }

    // looses precision, may overflow (depends on p and current scale)
    //@Deprecated
    public Point getPoint(LatLon latlon) {
        Point2D d = getPoint2D(latlon);
        return new Point((int) d.getX(), (int) d.getY());
    }

    // looses precision, may overflow (depends on p and current scale)
    //@Deprecated
    public Point getPoint(Node n) {
        Point2D d = getPoint2D(n);
        return new Point((int) d.getX(), (int) d.getY());
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
        navigationModel.zoomTo(newCenter, newScale, initial ? ScrollMode.INITIAL : ScrollMode.DEFAULT);
    }

    public void zoomTo(EastNorth newCenter) {
        zoomTo(newCenter, navigationModel.getScale());
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
    }

    public void zoomToFactor(double x, double y, double factor) {
        navigationModel.zoomToFactorAround(new Point2D.Double(x, y), factor);
    }

    public void zoomToFactor(EastNorth newCenter, double factor) {
        zoomTo(newCenter, getScale()*factor);
    }

    public void zoomToFactor(double factor) {
        zoomTo(getCenter(), getScale()*factor);
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

    public void zoomPrevious() {
        navigationModel.zoomPrevious();
    }

    public void zoomNext() {
        navigationModel.zoomNext();
    }

    public boolean hasZoomUndoEntries() {
        return navigationModel.hasZoomUndoEntries();
    }

    public boolean hasZoomRedoEntries() {
        return navigationModel.hasZoomRedoEntries();
    }

    private BBox getBBox(Point p, int snapDistance) {
        return new BBox(getLatLon(p.x - snapDistance, p.y - snapDistance),
                getLatLon(p.x + snapDistance, p.y + snapDistance));
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @return a sorted map with the keys representing the distance of
     *      their associated nodes to point p.
     */
    private Map<Double, List<Node>> getNearestNodesImpl(Point p,
            Predicate<OsmPrimitive> predicate) {
        Map<Double, List<Node>> nearestMap = new TreeMap<>();
        DataSet ds = getCurrentDataSet();

        if (ds != null) {
            double dist, snapDistanceSq = PROP_SNAP_DISTANCE.get();
            snapDistanceSq *= snapDistanceSq;

            for (Node n : ds.searchNodes(getBBox(p, PROP_SNAP_DISTANCE.get()))) {
                if (predicate.evaluate(n)
                        && (dist = getPoint2D(n).distanceSq(p)) < snapDistanceSq) {
                    List<Node> nlist;
                    if (nearestMap.containsKey(dist)) {
                        nlist = nearestMap.get(dist);
                    } else {
                        nlist = new LinkedList<>();
                        nearestMap.put(dist, nlist);
                    }
                    nlist.add(n);
                }
            }
        }

        return nearestMap;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @param p the point for which to search the nearest segment.
     * @param ignore a collection of nodes which are not to be returned.
     * @param predicate the returned objects have to fulfill certain properties.
     *
     * @return All nodes nearest to point p that are in a belt from
     *      dist(nearest) to dist(nearest)+4px around p and
     *      that are not in ignore.
     */
    public final List<Node> getNearestNodes(Point p,
            Collection<Node> ignore, Predicate<OsmPrimitive> predicate) {
        List<Node> nearestList = Collections.emptyList();

        if (ignore == null) {
            ignore = Collections.emptySet();
        }

        Map<Double, List<Node>> nlists = getNearestNodesImpl(p, predicate);
        if (!nlists.isEmpty()) {
            Double minDistSq = null;
            for (Entry<Double, List<Node>> entry : nlists.entrySet()) {
                Double distSq = entry.getKey();
                List<Node> nlist = entry.getValue();

                // filter nodes to be ignored before determining minDistSq..
                nlist.removeAll(ignore);
                if (minDistSq == null) {
                    if (!nlist.isEmpty()) {
                        minDistSq = distSq;
                        nearestList = new ArrayList<>();
                        nearestList.addAll(nlist);
                    }
                } else {
                    if (distSq-minDistSq < (4)*(4)) {
                        nearestList.addAll(nlist);
                    }
                }
            }
        }

        return nearestList;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @param p the point for which to search the nearest segment.
     * @param predicate the returned objects have to fulfill certain properties.
     *
     * @return All nodes nearest to point p that are in a belt from
     *      dist(nearest) to dist(nearest)+4px around p.
     * @see #getNearestNodes(Point, Collection, Predicate)
     */
    public final List<Node> getNearestNodes(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestNodes(p, null, predicate);
    }

    /**
     * The *result* depends on the current map selection state IF use_selected is true.
     *
     * If more than one node within node.snap-distance pixels is found,
     * the nearest node selected is returned IF use_selected is true.
     *
     * Else the nearest new/id=0 node within about the same distance
     * as the true nearest node is returned.
     *
     * If no such node is found either, the true nearest
     * node to p is returned.
     *
     * Finally, if a node is not found at all, null is returned.
     *
     * @param p the screen point
     * @param predicate this parameter imposes a condition on the returned object, e.g.
     *        give the nearest node that is tagged.
     *
     * @return A node within snap-distance to point p,
     *      that is chosen by the algorithm described.
     */
    public final Node getNearestNode(Point p, Predicate<OsmPrimitive> predicate, boolean useSelected) {
        return getNearestNode(p, predicate, useSelected, null);
    }

    /**
     * The *result* depends on the current map selection state IF use_selected is true
     *
     * If more than one node within node.snap-distance pixels is found,
     * the nearest node selected is returned IF use_selected is true.
     *
     * If there are no selected nodes near that point, the node that is related to some of the preferredRefs
     *
     * Else the nearest new/id=0 node within about the same distance
     * as the true nearest node is returned.
     *
     * If no such node is found either, the true nearest
     * node to p is returned.
     *
     * Finally, if a node is not found at all, null is returned.
     * @since 6065
     * @return A node within snap-distance to point p,
     *      that is chosen by the algorithm described.
     *
     * @param p the screen point
     * @param predicate this parameter imposes a condition on the returned object, e.g.
     *        give the nearest node that is tagged.
     * @param preferredRefs primitives, whose nodes we prefer
     */
    public final Node getNearestNode(Point p, Predicate<OsmPrimitive> predicate,
            boolean useSelected, Collection<OsmPrimitive> preferredRefs) {

        Map<Double, List<Node>> nlists = getNearestNodesImpl(p, predicate);
        if (nlists.isEmpty()) return null;

        if (preferredRefs != null && preferredRefs.isEmpty()) preferredRefs = null;
        Node ntsel = null, ntnew = null, ntref = null;
        boolean useNtsel = useSelected;
        double minDistSq = nlists.keySet().iterator().next();

        for (Entry<Double, List<Node>> entry : nlists.entrySet()) {
            Double distSq = entry.getKey();
            for (Node nd : entry.getValue()) {
                // find the nearest selected node
                if (ntsel == null && nd.isSelected()) {
                    ntsel = nd;
                    // if there are multiple nearest nodes, prefer the one
                    // that is selected. This is required in order to drag
                    // the selected node if multiple nodes have the same
                    // coordinates (e.g. after unglue)
                    useNtsel |= Utils.equalsEpsilon(distSq, minDistSq);
                }
                if (ntref == null && preferredRefs != null && Utils.equalsEpsilon(distSq, minDistSq)) {
                    List<OsmPrimitive> ndRefs = nd.getReferrers();
                    for (OsmPrimitive ref: preferredRefs) {
                        if (ndRefs.contains(ref)) {
                            ntref = nd;
                            break;
                        }
                    }
                }
                // find the nearest newest node that is within about the same
                // distance as the true nearest node
                if (ntnew == null && nd.isNew() && (distSq-minDistSq < 1)) {
                    ntnew = nd;
                }
            }
        }

        // take nearest selected, nearest new or true nearest node to p, in that order
        if (ntsel != null && useNtsel)
            return ntsel;
        if (ntref != null)
            return ntref;
        if (ntnew != null)
            return ntnew;
        return nlists.values().iterator().next().get(0);
    }

    /**
     * Convenience method to {@link #getNearestNode(Point, Predicate, boolean)}.
     * @param p the screen point
     * @param predicate this parameter imposes a condition on the returned object, e.g.
     *        give the nearest node that is tagged.
     *
     * @return The nearest node to point p.
     */
    public final Node getNearestNode(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestNode(p, predicate, true);
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @return a sorted map with the keys representing the perpendicular
     *      distance of their associated way segments to point p.
     */
    private Map<Double, List<WaySegment>> getNearestWaySegmentsImpl(Point p,
            Predicate<OsmPrimitive> predicate) {
        Map<Double, List<WaySegment>> nearestMap = new TreeMap<>();
        DataSet ds = getCurrentDataSet();

        if (ds != null) {
            double snapDistanceSq = Main.pref.getInteger("mappaint.segment.snap-distance", 10);
            snapDistanceSq *= snapDistanceSq;

            for (Way w : ds.searchWays(getBBox(p, Main.pref.getInteger("mappaint.segment.snap-distance", 10)))) {
                if (!predicate.evaluate(w)) {
                    continue;
                }
                Node lastN = null;
                int i = -2;
                for (Node n : w.getNodes()) {
                    i++;
                    if (n.isDeleted() || n.isIncomplete()) { //FIXME: This shouldn't happen, raise exception?
                        continue;
                    }
                    if (lastN == null) {
                        lastN = n;
                        continue;
                    }

                    Point2D A = getPoint2D(lastN);
                    Point2D B = getPoint2D(n);
                    double c = A.distanceSq(B);
                    double a = p.distanceSq(B);
                    double b = p.distanceSq(A);

                    /* perpendicular distance squared
                     * loose some precision to account for possible deviations in the calculation above
                     * e.g. if identical (A and B) come about reversed in another way, values may differ
                     * -- zero out least significant 32 dual digits of mantissa..
                     */
                    double perDistSq = Double.longBitsToDouble(
                            Double.doubleToLongBits( a - (a - b + c) * (a - b + c) / 4 / c )
                            >> 32 << 32); // resolution in numbers with large exponent not needed here..

                    if (perDistSq < snapDistanceSq && a < c + snapDistanceSq && b < c + snapDistanceSq) {
                        List<WaySegment> wslist;
                        if (nearestMap.containsKey(perDistSq)) {
                            wslist = nearestMap.get(perDistSq);
                        } else {
                            wslist = new LinkedList<>();
                            nearestMap.put(perDistSq, wslist);
                        }
                        wslist.add(new WaySegment(w, i));
                    }

                    lastN = n;
                }
            }
        }

        return nearestMap;
    }

    /**
     * The result *order* depends on the current map selection state.
     * Segments within 10px of p are searched and sorted by their distance to @param p,
     * then, within groups of equally distant segments, prefer those that are selected.
     *
     * @param p the point for which to search the nearest segments.
     * @param ignore a collection of segments which are not to be returned.
     * @param predicate the returned objects have to fulfill certain properties.
     *
     * @return all segments within 10px of p that are not in ignore,
     *          sorted by their perpendicular distance.
     */
    public final List<WaySegment> getNearestWaySegments(Point p,
            Collection<WaySegment> ignore, Predicate<OsmPrimitive> predicate) {
        List<WaySegment> nearestList = new ArrayList<>();
        List<WaySegment> unselected = new LinkedList<>();

        for (List<WaySegment> wss : getNearestWaySegmentsImpl(p, predicate).values()) {
            // put selected waysegs within each distance group first
            // makes the order of nearestList dependent on current selection state
            for (WaySegment ws : wss) {
                (ws.way.isSelected() ? nearestList : unselected).add(ws);
            }
            nearestList.addAll(unselected);
            unselected.clear();
        }
        if (ignore != null) {
            nearestList.removeAll(ignore);
        }

        return nearestList;
    }

    /**
     * The result *order* depends on the current map selection state.
     *
     * @param p the point for which to search the nearest segments.
     * @param predicate the returned objects have to fulfill certain properties.
     *
     * @return all segments within 10px of p, sorted by their perpendicular distance.
     * @see #getNearestWaySegments(Point, Collection, Predicate)
     */
    public final List<WaySegment> getNearestWaySegments(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestWaySegments(p, null, predicate);
    }

    /**
     * The *result* depends on the current map selection state IF use_selected is true.
     *
     * @return The nearest way segment to point p,
     *      and, depending on use_selected, prefers a selected way segment, if found.
     * @see #getNearestWaySegments(Point, Collection, Predicate)
     *
     * @param p the point for which to search the nearest segment.
     * @param predicate the returned object has to fulfill certain properties.
     * @param useSelected whether selected way segments should be preferred.
     */
    public final WaySegment getNearestWaySegment(Point p, Predicate<OsmPrimitive> predicate, boolean useSelected) {
        WaySegment wayseg = null, ntsel = null;

        for (List<WaySegment> wslist : getNearestWaySegmentsImpl(p, predicate).values()) {
            if (wayseg != null && ntsel != null) {
                break;
            }
            for (WaySegment ws : wslist) {
                if (wayseg == null) {
                    wayseg = ws;
                }
                if (ntsel == null && ws.way.isSelected()) {
                    ntsel = ws;
                }
            }
        }

        return (ntsel != null && useSelected) ? ntsel : wayseg;
    }

     /**
     * The *result* depends on the current map selection state IF use_selected is true.
     *
     * @return The nearest way segment to point p,
     *      and, depending on use_selected, prefers a selected way segment, if found.
     * Also prefers segments of ways that are related to one of preferredRefs primitives
     * @see #getNearestWaySegments(Point, Collection, Predicate)
     * @since 6065
     * @param p the point for which to search the nearest segment.
     * @param predicate the returned object has to fulfill certain properties.
     * @param use_selected whether selected way segments should be preferred.
     * @param preferredRefs - prefer segments related to these primitives, may be null
     */
    public final WaySegment getNearestWaySegment(Point p, Predicate<OsmPrimitive> predicate,
            boolean use_selected,  Collection<OsmPrimitive> preferredRefs) {
        WaySegment wayseg = null, ntsel = null, ntref = null;
        if (preferredRefs != null && preferredRefs.isEmpty()) preferredRefs = null;

        searchLoop: for (List<WaySegment> wslist : getNearestWaySegmentsImpl(p, predicate).values()) {
            for (WaySegment ws : wslist) {
                if (wayseg == null) {
                    wayseg = ws;
                }
                if (ntsel == null && ws.way.isSelected()) {
                    ntsel = ws;
                    break searchLoop;
                }
                if (ntref == null && preferredRefs != null) {
                    // prefer ways containing given nodes
                    for (Node nd: ws.way.getNodes()) {
                        if (preferredRefs.contains(nd)) {
                            ntref = ws;
                            break searchLoop;
                        }
                    }
                    Collection<OsmPrimitive> wayRefs = ws.way.getReferrers();
                    // prefer member of the given relations
                    for (OsmPrimitive ref: preferredRefs) {
                        if (ref instanceof Relation && wayRefs.contains(ref)) {
                            ntref = ws;
                            break searchLoop;
                        }
                    }
                }
            }
        }
        if (ntsel != null && use_selected)
            return ntsel;
        if (ntref != null)
            return ntref;
        return wayseg;
    }

    /**
     * Convenience method to {@link #getNearestWaySegment(Point, Predicate, boolean)}.
     * @param p the point for which to search the nearest segment.
     * @param predicate the returned object has to fulfill certain properties.
     *
     * @return The nearest way segment to point p.
     */
    public final WaySegment getNearestWaySegment(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestWaySegment(p, predicate, true);
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the perpendicular distance to point p.
     *
     * @param p the point for which to search the nearest ways.
     * @param ignore a collection of ways which are not to be returned.
     * @param predicate the returned object has to fulfill certain properties.
     *
     * @return all nearest ways to the screen point given that are not in ignore.
     * @see #getNearestWaySegments(Point, Collection, Predicate)
     */
    public final List<Way> getNearestWays(Point p,
            Collection<Way> ignore, Predicate<OsmPrimitive> predicate) {
        List<Way> nearestList = new ArrayList<>();
        Set<Way> wset = new HashSet<>();

        for (List<WaySegment> wss : getNearestWaySegmentsImpl(p, predicate).values()) {
            for (WaySegment ws : wss) {
                if (wset.add(ws.way)) {
                    nearestList.add(ws.way);
                }
            }
        }
        if (ignore != null) {
            nearestList.removeAll(ignore);
        }

        return nearestList;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the perpendicular distance to point p.
     *
     * @param p the point for which to search the nearest ways.
     * @param predicate the returned object has to fulfill certain properties.
     *
     * @return all nearest ways to the screen point given.
     * @see #getNearestWays(Point, Collection, Predicate)
     */
    public final List<Way> getNearestWays(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestWays(p, null, predicate);
    }

    /**
     * The *result* depends on the current map selection state.
     *
     * @return The nearest way to point p,
     *      prefer a selected way if there are multiple nearest.
     * @see #getNearestWaySegment(Point, Predicate)
     *
     * @param p the point for which to search the nearest segment.
     * @param predicate the returned object has to fulfill certain properties.
     */
    public final Way getNearestWay(Point p, Predicate<OsmPrimitive> predicate) {
        WaySegment nearestWaySeg = getNearestWaySegment(p, predicate);
        return (nearestWaySeg == null) ? null : nearestWaySeg.way;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * First, nodes will be searched. If there are nodes within BBox found,
     * return a collection of those nodes only.
     *
     * If no nodes are found, search for nearest ways. If there are ways
     * within BBox found, return a collection of those ways only.
     *
     * If nothing is found, return an empty collection.
     *
     * @param p The point on screen.
     * @param ignore a collection of ways which are not to be returned.
     * @param predicate the returned object has to fulfill certain properties.
     *
     * @return Primitives nearest to the given screen point that are not in ignore.
     * @see #getNearestNodes(Point, Collection, Predicate)
     * @see #getNearestWays(Point, Collection, Predicate)
     */
    public final List<OsmPrimitive> getNearestNodesOrWays(Point p,
            Collection<OsmPrimitive> ignore, Predicate<OsmPrimitive> predicate) {
        List<OsmPrimitive> nearestList = Collections.emptyList();
        OsmPrimitive osm = getNearestNodeOrWay(p, predicate, false);

        if (osm != null) {
            if (osm instanceof Node) {
                nearestList = new ArrayList<OsmPrimitive>(getNearestNodes(p, predicate));
            } else if (osm instanceof Way) {
                nearestList = new ArrayList<OsmPrimitive>(getNearestWays(p, predicate));
            }
            if (ignore != null) {
                nearestList.removeAll(ignore);
            }
        }

        return nearestList;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @param p The point on screen.
     * @param predicate the returned object has to fulfill certain properties.
     * @return Primitives nearest to the given screen point.
     * @see #getNearestNodesOrWays(Point, Collection, Predicate)
     */
    public final List<OsmPrimitive> getNearestNodesOrWays(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestNodesOrWays(p, null, predicate);
    }

    /**
     * This is used as a helper routine to {@link #getNearestNodeOrWay(Point, Predicate, boolean)}
     * It decides, whether to yield the node to be tested or look for further (way) candidates.
     *
     * @param osm node to check
     * @param p point clicked
     * @param use_selected whether to prefer selected nodes
     * @return true, if the node fulfills the properties of the function body
     */
    private boolean isPrecedenceNode(Node osm, Point p, boolean use_selected) {
        if (osm != null) {
            if (!(p.distanceSq(getPoint2D(osm)) > (4)*(4))) return true;
            if (osm.isTagged()) return true;
            if (use_selected && osm.isSelected()) return true;
        }
        return false;
    }

    /**
     * The *result* depends on the current map selection state IF use_selected is true.
     *
     * IF use_selected is true, use {@link #getNearestNode(Point, Predicate)} to find
     * the nearest, selected node.  If not found, try {@link #getNearestWaySegment(Point, Predicate)}
     * to find the nearest selected way.
     *
     * IF use_selected is false, or if no selected primitive was found, do the following.
     *
     * If the nearest node found is within 4px of p, simply take it.
     * Else, find the nearest way segment. Then, if p is closer to its
     * middle than to the node, take the way segment, else take the node.
     *
     * Finally, if no nearest primitive is found at all, return null.
     *
     * @return A primitive within snap-distance to point p,
     *      that is chosen by the algorithm described.
     * @see #getNearestNode(Point, Predicate)
     * @see #getNearestWay(Point, Predicate)
     *
     * @param p The point on screen.
     * @param predicate the returned object has to fulfill certain properties.
     * @param use_selected whether to prefer primitives that are currently selected or referred by selected primitives
     */
    public final OsmPrimitive getNearestNodeOrWay(Point p, Predicate<OsmPrimitive> predicate, boolean use_selected) {
        Collection<OsmPrimitive> sel;
        DataSet ds = getCurrentDataSet();
        if (use_selected && ds!=null) {
            sel = ds.getSelected();
        } else {
            sel = null;
        }
        OsmPrimitive osm = getNearestNode(p, predicate, use_selected, sel);

        if (isPrecedenceNode((Node)osm, p, use_selected)) return osm;
        WaySegment ws;
        if (use_selected) {
            ws = getNearestWaySegment(p, predicate, use_selected, sel);
        } else {
            ws = getNearestWaySegment(p, predicate, use_selected);
        }
        if (ws == null) return osm;

        if ((ws.way.isSelected() && use_selected) || osm == null) {
            // either (no _selected_ nearest node found, if desired) or no nearest node was found
            osm = ws.way;
        } else {
            int maxWaySegLenSq = 3*PROP_SNAP_DISTANCE.get();
            maxWaySegLenSq *= maxWaySegLenSq;

            Point2D wp1 = getPoint2D(ws.way.getNode(ws.lowerIndex));
            Point2D wp2 = getPoint2D(ws.way.getNode(ws.lowerIndex+1));

            // is wayseg shorter than maxWaySegLenSq and
            // is p closer to the middle of wayseg  than  to the nearest node?
            if (wp1.distanceSq(wp2) < maxWaySegLenSq &&
                    p.distanceSq(project(0.5, wp1, wp2)) < p.distanceSq(getPoint2D((Node)osm))) {
                osm = ws.way;
            }
        }
        return osm;
    }

    public static double perDist(Point2D pt, Point2D a, Point2D b) {
        if (pt != null && a != null && b != null) {
            double pd =
                    (a.getX()-pt.getX())*(b.getX()-a.getX()) -
                    (a.getY()-pt.getY())*(b.getY()-a.getY());
            return Math.abs(pd) / a.distance(b);
        }
        return 0d;
    }

    /**
     *
     * @param pt point to project onto (ab)
     * @param a root of vector
     * @param b vector
     * @return point of intersection of line given by (ab)
     *      with its orthogonal line running through pt
     */
    public static Point2D project(Point2D pt, Point2D a, Point2D b) {
        if (pt != null && a != null && b != null) {
            double r = (
                    (pt.getX()-a.getX())*(b.getX()-a.getX()) +
                    (pt.getY()-a.getY())*(b.getY()-a.getY()) )
                    / a.distanceSq(b);
            return project(r, a, b);
        }
        return null;
    }

    /**
     * if r = 0 returns a, if r=1 returns b,
     * if r = 0.5 returns center between a and b, etc..
     *
     * @param r scale value
     * @param a root of vector
     * @param b vector
     * @return new point at a + r*(ab)
     */
    public static Point2D project(double r, Point2D a, Point2D b) {
        Point2D ret = null;

        if (a != null && b != null) {
            ret = new Point2D.Double(a.getX() + r*(b.getX()-a.getX()),
                    a.getY() + r*(b.getY()-a.getY()));
        }
        return ret;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @return a list of all objects that are nearest to point p and
     *          not in ignore or an empty list if nothing was found.
     *
     * @param p The point on screen.
     * @param ignore a collection of ways which are not to be returned.
     * @param predicate the returned object has to fulfill certain properties.
     */
    public final List<OsmPrimitive> getAllNearest(Point p,
            Collection<OsmPrimitive> ignore, Predicate<OsmPrimitive> predicate) {
        List<OsmPrimitive> nearestList = new ArrayList<>();
        Set<Way> wset = new HashSet<>();

        // add nearby ways
        for (List<WaySegment> wss : getNearestWaySegmentsImpl(p, predicate).values()) {
            for (WaySegment ws : wss) {
                if (wset.add(ws.way)) {
                    nearestList.add(ws.way);
                }
            }
        }

        // add nearby nodes
        for (List<Node> nlist : getNearestNodesImpl(p, predicate).values()) {
            nearestList.addAll(nlist);
        }

        // add parent relations of nearby nodes and ways
        Set<OsmPrimitive> parentRelations = new HashSet<>();
        for (OsmPrimitive o : nearestList) {
            for (OsmPrimitive r : o.getReferrers()) {
                if (r instanceof Relation && predicate.evaluate(r)) {
                    parentRelations.add(r);
                }
            }
        }
        nearestList.addAll(parentRelations);

        if (ignore != null) {
            nearestList.removeAll(ignore);
        }

        return nearestList;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @return a list of all objects that are nearest to point p
     *          or an empty list if nothing was found.
     * @see #getAllNearest(Point, Collection, Predicate)
     *
     * @param p The point on screen.
     * @param predicate the returned object has to fulfill certain properties.
     */
    public final List<OsmPrimitive> getAllNearest(Point p, Predicate<OsmPrimitive> predicate) {
        return getAllNearest(p, null, predicate);
    }

    /**
     * @return The projection to be used in calculating stuff.
     */
    public Projection getProjection() {
        return Main.getProjection();
    }

    @Override
    public String helpTopic() {
        String n = getClass().getName();
        return n.substring(n.lastIndexOf('.')+1);
    }

    /**
     * Return a ID which is unique as long as viewport dimensions are the same
     * @return A unique ID, as long as viewport dimensions are the same
     */
    public int getViewID() {
        String x = getCenter().east() + "_" + getCenter().north() + "_" + getScale() + "_" +
                getWidth() + "_" + getHeight() + "_" + getProjection().toString();
        CRC32 id = new CRC32();
        id.update(x.getBytes(StandardCharsets.UTF_8));
        return (int)id.getValue();
    }

    private static class CursorInfo {
        private final Cursor cursor;
        private final Object object;
        public CursorInfo(Cursor c, Object o) {
            cursor = c;
            object = o;
        }
    }

    private LinkedList<CursorInfo> cursors = new LinkedList<>();

    /**
     * Set new cursor.
     */
    public void setNewCursor(Cursor cursor, Object reference) {
        if (!cursors.isEmpty()) {
            CursorInfo l = cursors.getLast();
            if(l != null && l.cursor == cursor && l.object == reference)
                return;
            stripCursors(reference);
        }
        cursors.add(new CursorInfo(cursor, reference));
        setCursor(cursor);
    }

    public void setNewCursor(int cursor, Object reference) {
        setNewCursor(Cursor.getPredefinedCursor(cursor), reference);
    }

    /**
     * Remove the new cursor and reset to previous
     */
    public void resetCursor(Object reference) {
        if (cursors.isEmpty()) {
            setCursor(null);
            return;
        }
        CursorInfo l = cursors.getLast();
        stripCursors(reference);
        if (l != null && l.object == reference) {
            if (cursors.isEmpty()) {
                setCursor(null);
            } else {
                setCursor(cursors.getLast().cursor);
            }
        }
    }

    private void stripCursors(Object reference) {
        LinkedList<CursorInfo> c = new LinkedList<>();
        for(CursorInfo i : cursors) {
            if(i.object != reference) {
                c.add(i);
            }
        }
        cursors = c;
    }

    @Override
    public void zoomChanged(NavigationModel navigationModel, ZoomData oldZoom, ZoomData newZoom) {
        if (oldZoom == null) {
            // initial.
        }
        EastNorth oldCenter = oldZoom.getCenterEastNorth(getProjection());
        EastNorth newCenter = newZoom.getCenterEastNorth(getProjection());
        if (!newCenter.equals(oldCenter)) {
            firePropertyChange(PROPNAME_CENTER, oldCenter, newCenter);
        }
        double oldScale = oldZoom.getScale();
        double newScale = newZoom.getScale();
        if (!Utils.equalsEpsilon(oldScale, newScale)) {
            firePropertyChange(PROPNAME_SCALE, oldScale, newScale);
        }
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        synchronized (paintRequestLock) {
            if (paintRect != null) {
                Graphics g2 = g.create();
                g2.setColor(Utils.complement(PaintColors.getBackgroundColor()));
                g2.drawRect(paintRect.x, paintRect.y, paintRect.width, paintRect.height);
                g2.dispose();
            }
            if (paintPoly != null) {
                Graphics g2 = g.create();
                g2.setColor(Utils.complement(PaintColors.getBackgroundColor()));
                g2.drawPolyline(paintPoly.xpoints, paintPoly.ypoints, paintPoly.npoints);
                g2.dispose();
            }
        }
        super.paint(g);
    }

    /**
     * Requests to paint the given {@code Rectangle}.
     * @param r The Rectangle to draw
     * @see #requestClearRect
     * @since 5500
     */
    public void requestPaintRect(Rectangle r) {
        if (r != null) {
            synchronized (paintRequestLock) {
                paintRect = r;
            }
            repaint();
        }
    }

    /**
     * Requests to paint the given {@code Polygon} as a polyline (unclosed polygon).
     * @param p The Polygon to draw
     * @see #requestClearPoly
     * @since 5500
     */
    public void requestPaintPoly(Polygon p) {
        if (p != null) {
            synchronized (paintRequestLock) {
                paintPoly = p;
            }
            repaint();
        }
    }

    /**
     * Requests to clear the rectangled previously drawn.
     * @see #requestPaintRect
     * @since 5500
     */
    public void requestClearRect() {
        synchronized (paintRequestLock) {
            paintRect = null;
        }
        repaint();
    }

    /**
     * Requests to clear the polyline previously drawn.
     * @see #requestPaintPoly
     * @since 5500
     */
    public void requestClearPoly() {
        synchronized (paintRequestLock) {
            paintPoly = null;
        }
        repaint();
    }
}
