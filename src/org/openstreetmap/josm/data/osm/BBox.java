// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Objects;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.coor.QuadTiling;
import org.openstreetmap.josm.tools.Utils;

public class BBox {

    protected double xmin = Double.POSITIVE_INFINITY;
    protected double xmax = Double.NEGATIVE_INFINITY;
    protected double ymin = Double.POSITIVE_INFINITY;
    protected double ymax = Double.NEGATIVE_INFINITY;

    /**
     * Constructs a new (invalid) BBox
     */
    public BBox() { }

    /**
     * Constructs a new {@code BBox} defined by a single point.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @since 6203
     */
    public BBox(final double x, final double y) {
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            xmin = x;
            ymin = y;
            xmax = x;
            ymax = y;
        }
    }

    /**
     * Constructs a new {@code BBox} defined by points <code>a</code> and <code>b</code>.
     * Result is minimal BBox containing both points if they are both valid, else undefined
     *
     * @param a first point
     * @param b second point
     */
    public BBox(LatLon a, LatLon b) {
        this(a.lon(), a.lat(), b.lon(), b.lat());
    }

    /**
     * Constructs a new {@code BBox} from another one.
     *
     * @param copy the BBox to copy
     */
    public BBox(BBox copy) {
        this.xmin = copy.xmin;
        this.xmax = copy.xmax;
        this.ymin = copy.ymin;
        this.ymax = copy.ymax;
    }

    /**
     * Create minimal  BBox so that {@code this.bounds(ax,ay)} and {@code this.bounds(bx,by)} will both return true
     * @param ax left or right X value (-180 .. 180)
     * @param ay top or bottom Y value (-90 .. 90)
     * @param bx left or right X value (-180 .. 180)
     * @param by top or bottom Y value (-90 .. 90)
     */
    public BBox(double ax, double ay, double bx, double by) {
        if (Double.isNaN(ax) || Double.isNaN(ay) || Double.isNaN(bx) || Double.isNaN(by)) {
            return; // use default which is an invalid BBox
        }

        if (ax > bx) {
            xmax = ax;
            xmin = bx;
        } else {
            xmax = bx;
            xmin = ax;
        }

        if (ay > by) {
            ymax = ay;
            ymin = by;
        } else {
            ymax = by;
            ymin = ay;
        }
    }

    /**
     * Create BBox for all nodes of the way with known coordinates.
     * If no node has a known coordinate, an invalid BBox is returned.
     * @param w the way
     */
    public BBox(Way w) {
        w.getNodes().forEach((n) -> add(n.getCoor()));
    }

    /**
     * Create BBox for a node. An invalid BBox is returned if the coordinates are not known.
     * @param n the node
     */
    public BBox(Node n) {
        if (n.isLatLonKnown())
            add(n.getCoor());
    }

    /**
     * Add a point to an existing BBox. Extends this bbox if necessary so that this.bounds(c) will return true
     * if c is a valid LatLon instance.
     * @param c a LatLon point
     */
    public final void add(LatLon c) {
        if (c != null && c.isValid())
            add(c.lon(), c.lat());
    }

    /**
     * Extends this bbox to include the point (x, y)
     * @param x X coordinate
     * @param y Y coordinate
     */
    public final void add(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y))
            return;
        xmin = Math.min(xmin, x);
        xmax = Math.max(xmax, x);
        ymin = Math.min(ymin, y);
        ymax = Math.max(ymax, y);
    }

    /**
     * Extends this bbox to include the bbox other. Does nothing if other is not valid.
     * @param other a bbox
     */
    public final void add(BBox other) {
        if (other.isValid()) {
            xmin = Math.min(xmin, other.xmin);
            xmax = Math.max(xmax, other.xmax);
            ymin = Math.min(ymin, other.ymin);
            ymax = Math.max(ymax, other.ymax);
        }
    }

    /**
     * Extends this bbox to include the bbox of the primitive extended by extraSpace.
     * @param primitive an OSM primitive
     * @param extraSpace the value to extend the primitives bbox. Unit is in LatLon degrees.
     */
    public void addPrimitive(OsmPrimitive primitive, double extraSpace) {
        BBox primBbox = primitive.getBBox();
        add(primBbox.xmin - extraSpace, primBbox.ymin - extraSpace);
        add(primBbox.xmax + extraSpace, primBbox.ymax + extraSpace);
    }

    public double height() {
        return ymax-ymin;
    }

    public double width() {
        return xmax-xmin;
    }

    /**
     * Tests, whether the bbox {@code b} lies completely inside this bbox.
     * @param b bounding box
     * @return {@code true} if {@code b} lies completely inside this bbox
     */
    public boolean bounds(BBox b) {
        return xmin <= b.xmin && xmax >= b.xmax
            && ymin <= b.ymin && ymax >= b.ymax;
    }

    /**
     * Tests, whether the Point {@code c} lies within the bbox.
     * @param c point
     * @return {@code true} if {@code c} lies within the bbox
     */
    public boolean bounds(LatLon c) {
        return xmin <= c.lon() && xmax >= c.lon()
            && ymin <= c.lat() && ymax >= c.lat();
    }

    /**
     * Tests, whether two BBoxes intersect as an area.
     * I.e. whether there exists a point that lies in both of them.
     * @param b other bounding box
     * @return {@code true} if this bbox intersects with the other
     */
    public boolean intersects(BBox b) {
        if (xmin > b.xmax)
            return false;
        if (xmax < b.xmin)
            return false;
        if (ymin > b.ymax)
            return false;
        if (ymax < b.ymin)
            return false;
        return true;
    }

    /**
     * Returns the top-left point.
     * @return The top-left point
     */
    public LatLon getTopLeft() {
        return new LatLon(ymax, xmin);
    }

    /**
     * Returns the latitude of top-left point.
     * @return The latitude of top-left point
     * @since 6203
     */
    public double getTopLeftLat() {
        return ymax;
    }

    /**
     * Returns the longitude of top-left point.
     * @return The longitude of top-left point
     * @since 6203
     */
    public double getTopLeftLon() {
        return xmin;
    }

    /**
     * Returns the bottom-right point.
     * @return The bottom-right point
     */
    public LatLon getBottomRight() {
        return new LatLon(ymin, xmax);
    }

    /**
     * Returns the latitude of bottom-right point.
     * @return The latitude of bottom-right point
     * @since 6203
     */
    public double getBottomRightLat() {
        return ymin;
    }

    /**
     * Returns the longitude of bottom-right point.
     * @return The longitude of bottom-right point
     * @since 6203
     */
    public double getBottomRightLon() {
        return xmax;
    }

    public LatLon getCenter() {
        return new LatLon(ymin + (ymax-ymin)/2.0, xmin + (xmax-xmin)/2.0);
    }

    byte getIndex(final int level) {

        byte idx1 = QuadTiling.index(ymin, xmin, level);

        final byte idx2 = QuadTiling.index(ymin, xmax, level);
        if (idx1 == -1) idx1 = idx2;
        else if (idx1 != idx2) return -1;

        final byte idx3 = QuadTiling.index(ymax, xmin, level);
        if (idx1 == -1) idx1 = idx3;
        else if (idx1 != idx3) return -1;

        final byte idx4 = QuadTiling.index(ymax, xmax, level);
        if (idx1 == -1) idx1 = idx4;
        else if (idx1 != idx4) return -1;

        return idx1;
    }

    public Rectangle2D toRectangle() {
        return new Rectangle2D.Double(xmin, ymin, xmax - xmin, ymax - ymin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xmin, xmax, ymin, ymax);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BBox b = (BBox) o;
        return Double.compare(b.xmax, xmax) == 0 && Double.compare(b.ymax, ymax) == 0
            && Double.compare(b.xmin, xmin) == 0 && Double.compare(b.ymin, ymin) == 0;
    }

    /**
     * @return true if the bbox covers a part of the planets surface
     * Height and width must be non-negative, but may (both) be 0.
     */
    public boolean isValid() {
        return (xmin <= xmax && ymin <= ymax);
    }

    /**
     * @return true if the bbox covers a part of the planets surface
     */
    public boolean isInWorld() {
        return !(xmin < -180.0 || xmax > 180.0 || ymin < -90.0 || ymax > 90.0);
    }

    @Override
    public String toString() {
        return "[ x: " + xmin + " -> " + xmax + ", y: " + ymin + " -> " + ymax + " ]";
    }

    public String toStringCSV(String separator) {
        return Utils.join(separator, Arrays.asList(
                LatLon.cDdFormatter.format(xmin),
                LatLon.cDdFormatter.format(ymin),
                LatLon.cDdFormatter.format(xmax),
                LatLon.cDdFormatter.format(ymax)));
    }
}
