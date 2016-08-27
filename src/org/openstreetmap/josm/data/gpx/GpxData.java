// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.gpx;

import java.awt.geom.Area;
import java.io.File;
import java.text.MessageFormat;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.DoubleSummaryStatistics;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Data;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.gpx.GpxTrack.GpxTrackChangeListener;
import org.openstreetmap.josm.tools.ListenerList;

/**
 * Objects of this class represent a gpx file with tracks, waypoints and routes.
 * It uses GPX v1.1, see <a href="http://www.topografix.com/GPX/1/1/">the spec</a>
 * for details.
 *
 * @author Raphael Mack &lt;ramack@raphael-mack.de&gt;
 */
public class GpxData extends WithAttributes implements Data {

    public File storageFile;
    public boolean fromServer;

    /** Creator (usually software) */
    public String creator;

    private ArrayList<GpxTrack> privateTracks = new ArrayList<>();
    private ArrayList<GpxRoute> privateRoutes = new ArrayList<>();
    private ArrayList<WayPoint> privateWaypoints = new ArrayList<>();
    private final GpxTrackChangeListener proxy = e -> fireInvalidate();

    /**
     * Tracks. Access is discouraged, use {@link #getTracks()} to read.
     * @see #getTracks()
     */
    public final Collection<GpxTrack> tracks = new ListeningCollection<GpxTrack>(privateTracks, this::fireInvalidate) {

        @Override
        protected void removed(GpxTrack cursor) {
            cursor.removeListener(proxy);
            super.removed(cursor);
        }

        @Override
        protected void added(GpxTrack cursor) {
            super.added(cursor);
            cursor.addListener(proxy);
        }
    };

    /**
     * Routes. Access is discouraged, use {@link #getTracks()} to read.
     * @see #getRoutes()
     */
    public final Collection<GpxRoute> routes = new ListeningCollection<>(privateRoutes, this::fireInvalidate);

    /**
     * Waypoints. Access is discouraged, use {@link #getTracks()} to read.
     * @see #getWaypoints()
     */
    public final Collection<WayPoint> waypoints = new ListeningCollection<>(privateWaypoints, this::fireInvalidate);

    /**
     * All data sources (bounds of downloaded bounds) of this GpxData.<br>
     * Not part of GPX standard but rather a JOSM extension, needed by the fact that
     * OSM API does not provide {@code <bounds>} element in its GPX reply.
     * @since 7575
     */
    public final Set<DataSource> dataSources = new HashSet<>();

    private final ListenerList<GpxDataChangeListener> listeners = ListenerList.create();

    /**
     * Merges data from another object.
     * @param other existing GPX data
     */
    public void mergeFrom(GpxData other) {
        if (storageFile == null && other.storageFile != null) {
            storageFile = other.storageFile;
        }
        fromServer = fromServer && other.fromServer;

        for (Map.Entry<String, Object> ent : other.attr.entrySet()) {
            // TODO: Detect conflicts.
            String k = ent.getKey();
            if (META_LINKS.equals(k) && attr.containsKey(META_LINKS)) {
                Collection<GpxLink> my = super.<GpxLink>getCollection(META_LINKS);
                @SuppressWarnings("unchecked")
                Collection<GpxLink> their = (Collection<GpxLink>) ent.getValue();
                my.addAll(their);
            } else {
                put(k, ent.getValue());
            }
        }
        privateTracks.addAll(other.getTracks());
        privateRoutes.addAll(other.getRoutes());
        privateWaypoints.addAll(other.getWaypoints());
        dataSources.addAll(other.dataSources);
        fireInvalidate();
    }

    /**
     * Get all tracks contained in this data set.
     * @return The tracks.
     */
    public Collection<GpxTrack> getTracks() {
        return Collections.unmodifiableCollection(privateTracks);
    }

    /**
     * Add a new track
     * @param track The new track
     */
    public void addTrack(GpxTrack track) {
        if (privateTracks.contains(track)) {
            throw new IllegalArgumentException(MessageFormat.format("The track was already added to this data: {0}", track));
        }
        privateTracks.add(track);
        track.addListener(proxy);
        fireInvalidate();
    }

    /**
     * Remove a track
     * @param track The old track
     */
    public void removeTrack(GpxTrack track) {
        if (!privateTracks.remove(track)) {
            throw new IllegalArgumentException(MessageFormat.format("The track was not in this data: {0}", track));
        }
        track.removeListener(proxy);
        fireInvalidate();
    }

    /**
     * Gets the list of all routes defined in this data set.
     * @return The routes
     */
    public Collection<GpxRoute> getRoutes() {
        return Collections.unmodifiableCollection(privateRoutes);
    }

    /**
     * Add a new route
     * @param route The new route
     */
    public void addRoute(GpxRoute route) {
        if (privateRoutes.contains(route)) {
            throw new IllegalArgumentException(MessageFormat.format("The route was already added to this data: {0}", route));
        }
        privateRoutes.add(route);
        fireInvalidate();
    }

    /**
     * Remove a route
     * @param route The old route
     */
    public void removeRoute(GpxRoute route) {
        if (!privateRoutes.remove(route)) {
            throw new IllegalArgumentException(MessageFormat.format("The route was not in this data: {0}", route));
        }
        fireInvalidate();
    }

    /**
     * Gets a list of all way points in this data set.
     * @return The way points.
     */
    public Collection<WayPoint> getWaypoints() {
        return Collections.unmodifiableCollection(privateWaypoints);
    }

    /**
     * Add a new waypoint
     * @param waypoint The new waypoint
     */
    public void addWaypoint(WayPoint waypoint) {
        if (privateWaypoints.contains(waypoint)) {
            throw new IllegalArgumentException(MessageFormat.format("The route was already added to this data: {0}", waypoint));
        }
        privateWaypoints.add(waypoint);
        fireInvalidate();
    }

    /**
     * Remove a waypoint
     * @param waypoint The old waypoint
     */
    public void removeWaypoint(WayPoint waypoint) {
        if (!privateWaypoints.remove(waypoint)) {
            throw new IllegalArgumentException(MessageFormat.format("The route was not in this data: {0}", waypoint));
        }
        fireInvalidate();
    }

    /**
     * Determines if this GPX data has one or more track points
     * @return {@code true} if this GPX data has track points, {@code false} otherwise
     */
    public boolean hasTrackPoints() {
        return getTrackPoints().findAny().isPresent();
    }

    /**
     * Gets a stream of all track points in the segments of the tracks of this data.
     * @return The stream
     * @see #getTracks()
     * @see GpxTrack#getSegments()
     * @see GpxTrackSegment#getWayPoints()
     */
    public Stream<WayPoint> getTrackPoints() {
        return getTracks().stream().flatMap(trk -> trk.getSegments().stream()).flatMap(trkseg -> trkseg.getWayPoints().stream());
    }

    /**
     * Determines if this GPX data has one or more route points
     * @return {@code true} if this GPX data has route points, {@code false} otherwise
     */
    public boolean hasRoutePoints() {
        return getRoutes().stream().anyMatch(rte -> !rte.routePoints.isEmpty());
    }

    /**
     * Determines if this GPX data is empty (i.e. does not contain any point)
     * @return {@code true} if this GPX data is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return !hasRoutePoints() && !hasTrackPoints() && waypoints.isEmpty();
    }

    /**
     * Returns the bounds defining the extend of this data, as read in metadata, if any.
     * If no bounds is defined in metadata, {@code null} is returned. There is no guarantee
     * that data entirely fit in this bounds, as it is not recalculated. To get recalculated bounds,
     * see {@link #recalculateBounds()}. To get downloaded areas, see {@link #dataSources}.
     * @return the bounds defining the extend of this data, or {@code null}.
     * @see #recalculateBounds()
     * @see #dataSources
     * @since 7575
     */
    public Bounds getMetaBounds() {
        Object value = get(META_BOUNDS);
        if (value instanceof Bounds) {
            return (Bounds) value;
        }
        return null;
    }

    /**
     * Calculates the bounding box of available data and returns it.
     * The bounds are not stored internally, but recalculated every time
     * this function is called.<br>
     * To get bounds as read from metadata, see {@link #getMetaBounds()}.<br>
     * To get downloaded areas, see {@link #dataSources}.<br>
     *
     * FIXME might perhaps use visitor pattern?
     * @return the bounds
     * @see #getMetaBounds()
     * @see #dataSources
     */
    public Bounds recalculateBounds() {
        Bounds bounds = null;
        for (WayPoint wpt : getWaypoints()) {
            if (bounds == null) {
                bounds = new Bounds(wpt.getCoor());
            } else {
                bounds.extend(wpt.getCoor());
            }
        }
        for (GpxRoute rte : getRoutes()) {
            for (WayPoint wpt : rte.routePoints) {
                if (bounds == null) {
                    bounds = new Bounds(wpt.getCoor());
                } else {
                    bounds.extend(wpt.getCoor());
                }
            }
        }
        for (GpxTrack trk : getTracks()) {
            Bounds trkBounds = trk.getBounds();
            if (trkBounds != null) {
                if (bounds == null) {
                    bounds = new Bounds(trkBounds);
                } else {
                    bounds.extend(trkBounds);
                }
            }
        }
        return bounds;
    }

    /**
     * calculates the sum of the lengths of all track segments
     * @return the length in meters
     */
    public double length() {
        return getTracks().stream().mapToDouble(GpxTrack::length).sum();
    }

    /**
     * returns minimum and maximum timestamps in the track
     * @param trk track to analyze
     * @return  minimum and maximum dates in array of 2 elements
     */
    public static Date[] getMinMaxTimeForTrack(GpxTrack trk) {
        return getMinMaxTimeFor(trk.getSegments().stream()
            .flatMap(seg -> seg.getWayPoints().stream()), false);
    }

    /**
    * Returns minimum and maximum timestamps for all tracks
    * Warning: there are lot of track with broken timestamps,
    * so we just ingore points from future and from year before 1970 in this method
    * works correctly @since 5815
     * @return minimum and maximum dates in array of 2 elements
    */
    public Date[] getMinMaxTimeForAllTracks() {
        return getMinMaxTimeFor(getTrackPoints(), true);
    }

    private static Date[] getMinMaxTimeFor(Stream<WayPoint> points, boolean restrictToNow) {
        DoubleStream stream = points.mapToDouble(pnt -> pnt.time);
        if (restrictToNow) {
            double now = System.currentTimeMillis()/1000.0;
            stream = stream.filter(t -> t > 0 && t <= now);
        }
        DoubleSummaryStatistics stats = stream.summaryStatistics();
        if  (stats.getCount() == 0) {
            return new Date[0];
        } else {
            return new Date[] {
                    new Date((long) (stats.getMin() * 1000)),
                    new Date((long) (stats.getMax() * 1000))
            };
        }
    }

    /**
     * Makes a WayPoint at the projection of point p onto the track providing p is less than
     * tolerance away from the track
     *
     * @param p : the point to determine the projection for
     * @param tolerance : must be no further than this from the track
     * @return the closest point on the track to p, which may be the first or last point if off the
     * end of a segment, or may be null if nothing close enough
     */
    public WayPoint nearestPointOnTrack(EastNorth p, double tolerance) {
        /*
         * assume the coordinates of P are xp,yp, and those of a section of track between two
         * trackpoints are R=xr,yr and S=xs,ys. Let N be the projected point.
         *
         * The equation of RS is Ax + By + C = 0 where A = ys - yr B = xr - xs C = - Axr - Byr
         *
         * Also, note that the distance RS^2 is A^2 + B^2
         *
         * If RS^2 == 0.0 ignore the degenerate section of track
         *
         * PN^2 = (Axp + Byp + C)^2 / RS^2 that is the distance from P to the line
         *
         * so if PN^2 is less than PNmin^2 (initialized to tolerance) we can reject the line
         * otherwise... determine if the projected poijnt lies within the bounds of the line: PR^2 -
         * PN^2 <= RS^2 and PS^2 - PN^2 <= RS^2
         *
         * where PR^2 = (xp - xr)^2 + (yp-yr)^2 and PS^2 = (xp - xs)^2 + (yp-ys)^2
         *
         * If so, calculate N as xn = xr + (RN/RS) B yn = y1 + (RN/RS) A
         *
         * where RN = sqrt(PR^2 - PN^2)
         */

        double pnminsq = tolerance * tolerance;
        EastNorth bestEN = null;
        double bestTime = 0.0;
        double px = p.east();
        double py = p.north();
        double rx = 0.0, ry = 0.0, sx, sy, x, y;
        for (GpxTrack track : getTracks()) {
            for (GpxTrackSegment seg : track.getSegments()) {
                WayPoint r = null;
                for (WayPoint S : seg.getWayPoints()) {
                    EastNorth en = S.getEastNorth();
                    if (r == null) {
                        r = S;
                        rx = en.east();
                        ry = en.north();
                        x = px - rx;
                        y = py - ry;
                        double pRsq = x * x + y * y;
                        if (pRsq < pnminsq) {
                            pnminsq = pRsq;
                            bestEN = en;
                            bestTime = r.time;
                        }
                    } else {
                        sx = en.east();
                        sy = en.north();
                        double a = sy - ry;
                        double b = rx - sx;
                        double c = -a * rx - b * ry;
                        double rssq = a * a + b * b;
                        if (rssq == 0) {
                            continue;
                        }
                        double pnsq = a * px + b * py + c;
                        pnsq = pnsq * pnsq / rssq;
                        if (pnsq < pnminsq) {
                            x = px - rx;
                            y = py - ry;
                            double prsq = x * x + y * y;
                            x = px - sx;
                            y = py - sy;
                            double pssq = x * x + y * y;
                            if (prsq - pnsq <= rssq && pssq - pnsq <= rssq) {
                                double rnoverRS = Math.sqrt((prsq - pnsq) / rssq);
                                double nx = rx - rnoverRS * b;
                                double ny = ry + rnoverRS * a;
                                bestEN = new EastNorth(nx, ny);
                                bestTime = r.time + rnoverRS * (S.time - r.time);
                                pnminsq = pnsq;
                            }
                        }
                        r = S;
                        rx = sx;
                        ry = sy;
                    }
                }
                if (r != null) {
                    EastNorth c = r.getEastNorth();
                    /* if there is only one point in the seg, it will do this twice, but no matter */
                    rx = c.east();
                    ry = c.north();
                    x = px - rx;
                    y = py - ry;
                    double prsq = x * x + y * y;
                    if (prsq < pnminsq) {
                        pnminsq = prsq;
                        bestEN = c;
                        bestTime = r.time;
                    }
                }
            }
        }
        if (bestEN == null)
            return null;
        WayPoint best = new WayPoint(Main.getProjection().eastNorth2latlon(bestEN));
        best.time = bestTime;
        return best;
    }

    /**
     * Iterate over all track segments and over all routes.
     *
     * @param trackVisibility An array indicating which tracks should be
     * included in the iteration. Can be null, then all tracks are included.
     * @return an Iterable object, which iterates over all track segments and
     * over all routes
     */
    public Iterable<Collection<WayPoint>> getLinesIterable(final boolean ... trackVisibility) {
        return () -> new LinesIterator(this, trackVisibility);
    }

    /**
     * Resets the internal caches of east/north coordinates.
     */
    public void resetEastNorthCache() {
        getWaypoints().forEach(WayPoint::invalidateEastNorthCache);
        getTrackPoints().forEach(WayPoint::invalidateEastNorthCache);
        for (GpxRoute route: getRoutes()) {
            if (route.routePoints == null) {
                continue;
            }
            for (WayPoint wp: route.routePoints) {
                wp.invalidateEastNorthCache();
            }
        }
    }

    /**
     * Iterates over all track segments and then over all routes.
     */
    public static class LinesIterator implements Iterator<Collection<WayPoint>> {

        private Iterator<GpxTrack> itTracks;
        private int idxTracks;
        private Iterator<GpxTrackSegment> itTrackSegments;
        private final Iterator<GpxRoute> itRoutes;

        private Collection<WayPoint> next;
        private final boolean[] trackVisibility;

        /**
         * Constructs a new {@code LinesIterator}.
         * @param data GPX data
         * @param trackVisibility An array indicating which tracks should be
         * included in the iteration. Can be null, then all tracks are included.
         */
        public LinesIterator(GpxData data, boolean ... trackVisibility) {
            itTracks = data.getTracks().iterator();
            idxTracks = -1;
            itRoutes = data.getRoutes().iterator();
            this.trackVisibility = trackVisibility;
            next = getNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Collection<WayPoint> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Collection<WayPoint> current = next;
            next = getNext();
            return current;
        }

        private Collection<WayPoint> getNext() {
            if (itTracks != null) {
                if (itTrackSegments != null && itTrackSegments.hasNext()) {
                    return itTrackSegments.next().getWayPoints();
                } else {
                    while (itTracks.hasNext()) {
                        GpxTrack nxtTrack = itTracks.next();
                        idxTracks++;
                        if (trackVisibility != null && !trackVisibility[idxTracks])
                            continue;
                        itTrackSegments = nxtTrack.getSegments().iterator();
                        if (itTrackSegments.hasNext()) {
                            return itTrackSegments.next().getWayPoints();
                        }
                    }
                    // if we get here, all the Tracks are finished; Continue with Routes
                    itTracks = null;
                }
            }
            if (itRoutes.hasNext()) {
                return itRoutes.next().routePoints;
            }
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Collection<DataSource> getDataSources() {
        return dataSources;
    }

    @Override
    public Area getDataSourceArea() {
        return DataSource.getDataSourceArea(dataSources);
    }

    @Override
    public List<Bounds> getDataSourceBounds() {
        return DataSource.getDataSourceBounds(dataSources);
    }

    /**
     * Adds a listener that gets called whenever the data changed.
     * @param listener The listener
     */
    public void addChangeListener(GpxDataChangeListener listener) {
        listeners.addListener(listener);
    }

    /**
     * Adds a listener that gets called whenever the data changed. It is added with a weak link
     * @param listener The listener
     */
    public void addWeakChangeListener(GpxDataChangeListener listener) {
        listeners.addWeakListener(listener);
    }

    /**
     * Removes a listener that gets called whenever the data changed.
     * @param listener The listener
     */
    public void removeChangeListener(GpxDataChangeListener listener) {
        listeners.removeListener(listener);
    }

    private void fireInvalidate() {
        GpxDataChangeEvent e = new GpxDataChangeEvent(this);
        listeners.fireEvent(l -> l.gpxDataChanged(e));
    }

    private static class ListeningCollection<T> extends AbstractCollection<T> {
        private final ArrayList<T> base;
        private final Runnable runOnModification;

        ListeningCollection(ArrayList<T> base, Runnable runOnModification) {
            this.base = base;
            this.runOnModification = runOnModification;
        }

        @Override
        public Iterator<T> iterator() {
            Iterator<T> it = base.iterator();
            return new Iterator<T>() {
                private T cursor;

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public T next() {
                    cursor = it.next();
                    return cursor;
                }

                @Override
                public void remove() {
                    if (cursor != null) {
                        removed(cursor);
                        cursor = null;
                    }
                    it.remove();
                }
            };
        }

        @Override
        public int size() {
            return base.size();
        }

        @Override
        public boolean remove(Object o) {
            boolean remove = super.remove(o);
            if (remove) {
                removed((T) o);
            }
            return remove;
        }

        @Override
        public boolean add(T e) {
            boolean add = super.add(e);
            added(e);
            return add;
        }

        protected void removed(T cursor) {
            runOnModification.run();
        }

        protected void added(T cursor) {
            runOnModification.run();
        }
    }

    /**
     * A listener that listens to GPX data changes.
     * @author Michael Zangl
     * @since xxx
     */
    @FunctionalInterface
    public interface GpxDataChangeListener {
        /**
         * Called when the gpx data changed.
         * @param e The event
         */
        void gpxDataChanged(GpxDataChangeEvent e);
    }

    /**
     * A data change event in any of the gpx data.
     * @author Michael Zangl
     * @since xxx
     */
    public static class GpxDataChangeEvent {
        private final GpxData source;

        GpxDataChangeEvent(GpxData source) {
            super();
            this.source = source;
        }

        /**
         * Get the data that was changed.
         * @return The data.
         */
        public GpxData getSource() {
            return source;
        }
    }
}
