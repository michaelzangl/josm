// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mapview;

import java.awt.Dimension;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;

/**
 * This is the state of a map display. It stores position and all information needed to translate between coordinates.
 * @author Michael Zangl
 *
 */
public class MapDisplayState {

    /**
     * This defines a point on the map display. It's position can be queried in many coordinate systems.
     * @author Michael Zangl
     */
    public class MapDisplayPoint {
        /**
         * Local coordinates in map display state.
         */
        private final double x, y;

        public MapDisplayPoint(double x, double y) {
            super();
            this.x = x;
            this.y = y;
        }

        /**
         * Gets the point that is the closest real pixel to this screen.
         * @return The close point.
         */
        public MapDisplayPoint roundToScreen() {
            return new MapDisplayPoint(Math.round(x), Math.round(y));
        }

        public MapDisplayPoint addPixel(double dx, double dy) {
            return new MapDisplayPoint(x + dx, y + dy);
        }

        public LatLon getLatLon() {
            return projection.eastNorth2latlon(getEastNorth());
        }

        public EastNorth getEastNorth() {
            return new EastNorth(bottomLeft.east() + getRelativeEast(), bottomLeft.north() + getRelativeNorth());
        }

        private double getRelativeEast() {
            return x * scale;
        }

        private double getRelativeNorth() {
            return position.getHeight() - y * scale;
        }

        public Point2D getOnMapView() {
            return new Point2D.Double(x, y);
        }

        public Point2D getInWindow() {
            return new Point2D.Double(x + position.getX(), y + position.getY());
        }

        public Point2D getOnScreen() {
            return new Point2D.Double(x + position.getX() + position.getWindowX(), y + position.getY()
                    + position.getWindowY());
        }

        public MapDisplayPoint interpolateOnScreen(MapDisplayPoint other, float proportion) {
            return new MapDisplayPoint(x * (1 - proportion) + other.x * proportion, y * (1 - proportion) + other.y
                    * proportion);
        }

        public MapDisplayPoint interpolateOnWorld(MapDisplayPoint c2, float proportion) {
            LatLon latlon = getLatLon().interpolate(c2.getLatLon(), proportion);
            return get(latlon);
        }
    }

    private final Projection projection;

    private final MapDisplayPosition position;

    private final double scale;

    private final EastNorth bottomLeft;

    public MapDisplayState(Projection projection, MapDisplayPosition position, EastNorth bottomLeft, double scale) {
        super();
        this.projection = projection;
        this.position = position;
        this.bottomLeft = bottomLeft;
        this.scale = scale;
    }

    public Projection getProjection() {
        return projection;
    }

    public double getScale() {
        return scale;
    }

    public MapDisplayPoint getPoint(Point2D viewPosition) {
        return new MapDisplayPoint(viewPosition.getX(), viewPosition.getY());
    }

    public MapDisplayPoint getPointFromWindow(Point2D relative) {
        return new MapDisplayPoint(relative.getX() - position.getX(), relative.getY() - position.getY());
    }

    public MapDisplayPoint getCenter() {
        return getPoint(position.getWidth() / 2.0, position.getHeight() / 2.0);
    }

    private MapDisplayPoint getPoint(double relativeX, double relativeY) {
        return new MapDisplayPoint(relativeX, relativeY);
    }

    public MapDisplayPoint get(LatLon latlon) {
        return get(getProjection().latlon2eastNorth(latlon));
    }

    public MapDisplayPoint get(EastNorth eastNorth) {
        double lEast = eastNorth.east() - bottomLeft.east();
        double lNorth = eastNorth.north() - bottomLeft.north();
        return new MapDisplayPoint(lEast / scale, position.getHeight() - lNorth / scale);
    }

    public Area getArea(Bounds bounds) {
        // TODO: If projection is default, we don't need all this.
        MapDisplayPoint c1 = get(bounds.getMin());
        MapDisplayPoint c2 = get(new LatLon(bounds.getMinLat(), bounds.getMaxLon()));
        MapDisplayPoint c3 = get(bounds.getMax());
        MapDisplayPoint c4 = get(new LatLon(bounds.getMaxLat(), bounds.getMinLon()));
        Polygon s = new Polygon();
        addLine(s, c1, c2);
        addLine(s, c2, c3);
        addLine(s, c3, c4);
        addLine(s, c4, c1);
        return new Area(s);
    }

    private void addLine(Polygon s, MapDisplayPoint c1, MapDisplayPoint c2) {
        int steps = 10; // <- TODO: dynamic?
        for (int i = 0; i < steps; i++) {
            float progress = (float) i / steps;
            Point2D point = c1.interpolateOnWorld(c2, progress).getOnMapView();
            s.addPoint((int) point.getX(), (int) point.getY());
        }
        // don't add last.
    }

    MapDisplayState usingCenter(EastNorth center) {
        return moveTo(getCenter(), center);
    }

    MapDisplayState movedToOnMapView(Point2D relative, EastNorth newEastNorth) {
        return moveTo(getPoint(relative), newEastNorth);
    }

    MapDisplayState movedToInWindow(Point2D relative, EastNorth newEastNorth) {
        return moveTo(getPointFromWindow(relative), newEastNorth);
    }

    private MapDisplayState moveTo(MapDisplayPoint point, EastNorth newEastNorth) {
        double east = newEastNorth.east() - point.getRelativeEast();
        double north = newEastNorth.north() - point.getRelativeNorth();
        return new MapDisplayState(getProjection(), position, new EastNorth(east, north), getScale());
    }

    MapDisplayState usingPosition(MapDisplayPosition newPosition) {
        // preserve window coordinates.
        MapDisplayPoint referencePoint = getPoint(new Point2D.Double());
        Point2D inWindow = referencePoint.getInWindow();
        return new MapDisplayState(getProjection(), newPosition, bottomLeft, scale).movedToInWindow(inWindow,
                referencePoint.getEastNorth());
    }

    MapDisplayState usingScale(MapDisplayPoint fixedPoint, double newScale) {
        return new MapDisplayState(projection, position, bottomLeft, newScale).movedToOnMapView(
                fixedPoint.getOnMapView(), fixedPoint.getEastNorth());
    }

    public Dimension getMapViewSize() {
        return new Dimension(position.getWidth(), position.getHeight());
    }

    public Bounds getBoundsOnWorld() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Gets the horizontal distance in meters that a line of the length of n pixels would cover in the center of our view.
     * @param pixel The number of pixels the line should have.
     * @return The length in meters.
     */
    public double getPixelDistance(int pixel) {
        Point2D center = getCenter().getOnMapView();
        LatLon ll1 = getPoint(center.getX() - pixel / 2, center.getY()).getLatLon();
        LatLon ll2 = getPoint(center.getX() + pixel / 2, center.getY()).getLatLon();
        return ll1.greatCircleDistance(ll2);
    }

    /**
    * Gets the affine transform that converts the east/north coordinates to pixel coordinates.
    * @return The current affine transform. Do not modify it.
    */
    public AffineTransform getEastNorthToPixelTransoform() {
        return new AffineTransform(1.0 / scale, 0.0, 0.0, -1.0 / scale, -bottomLeft.east() / scale, bottomLeft.north()
                / scale - position.getHeight());
    }

}
