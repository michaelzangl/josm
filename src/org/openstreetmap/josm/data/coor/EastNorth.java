// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.coor;

/**
 * Northing, Easting of the projected coordinates.
 *
 * This class is immutable.
 *
 * @author Imi
 */
public class EastNorth extends Coordinate {

    private static final long serialVersionUID = 1L;

    public EastNorth(double east, double north) {
        super(east, north);
    }

    public double east() {
        return x;
    }

    public double north() {
        return y;
    }

    /**
     * Adds an offset to this {@link EastNorth} instance and returns the result.
     * @param dEast The offset to add in east direction.
     * @param dNorth The offset to add in north direction.
     * @return The result.
     */
    public EastNorth add(double dEast, double dNorth) {
        return new EastNorth(east()+dEast, north()+dNorth);
    }

    /**
     * Adds the coordinates of an other EastNorth instance to this one.
     * @param other The other instance.
     * @return The new EastNorth position.
     */
    public EastNorth add(EastNorth other) {
        return new EastNorth(x+other.x, y+other.y);
    }

    /**
     * Subtracts the coordinates of this EastNorth instance from the other one.
     * <p>
     * This produces result = en2 - this.
     * @param en2 The instance to subtract this one from.
     * @return The new EastNorth position.
     * @deprecated use {@code subtract} on the other EastNorth instead
     */
    @Deprecated
    public EastNorth sub(EastNorth en2) {
        return en2.subtract(this);
    }

    /**
     * Subtracts an east/north value from this point.
     * @param other The other value to subtract from this.
     * @return A point with the new coordinates.
     */
    public EastNorth subtract(EastNorth other) {
        return new EastNorth(x-other.x, y-other.y);
    }

    public EastNorth scale(double s) {
        return new EastNorth(s * x, s * y);
    }

    /**
     * Does a linear interpolation between two EastNorth instances.
     * @param en2 The other EstNort instance.
     * @param proportion The proportion the other instance influences the result.
     * @return The new {@link EastNorth} position.
     */
    public EastNorth interpolate(EastNorth en2, double proportion) {
        return new EastNorth(this.x + proportion * (en2.x - this.x),
                this.y + proportion * (en2.y - this.y));
    }

    /**
     * Gets the center between two {@link EastNorth} instances.
     * @param en2 The other instance.
     * @return The center between this and the other instance.
     */
    public EastNorth getCenter(EastNorth en2) {
        return new EastNorth((this.x + en2.x)/2.0, (this.y + en2.y)/2.0);
    }

    /**
     * Returns the euclidean distance from this {@code EastNorth} to a specified {@code EastNorth}.
     *
     * @param en the specified coordinate to be measured against this {@code EastNorth}
     * @return the euclidean distance from this {@code EastNorth} to a specified {@code EastNorth}
     * @since 6166
     */
    public double distance(final EastNorth en) {
        return super.distance(en);
    }

    /**
     * Returns the square of the euclidean distance from this {@code EastNorth} to a specified {@code EastNorth}.
     *
     * @param en the specified coordinate to be measured against this {@code EastNorth}
     * @return the square of the euclidean distance from this {@code EastNorth} to a specified {@code EastNorth}
     * @since 6166
     */
    public double distanceSq(final EastNorth en) {
        return super.distanceSq(en);
    }

    /**
     * Counts length (distance from [0,0]) of this.
     *
     * @return length of this
     */
    public double length() {
        return Math.sqrt(x*x + y*y);
    }

    /**
     * Returns the heading, in radians, that you have to use to get from
     * this EastNorth to another. Heading is mapped into [0, 2pi)
     *
     * @param other the "destination" position
     * @return heading
     */
    public double heading(EastNorth other) {
        double hd = Math.atan2(other.east() - east(), other.north() - north());
        if (hd < 0) {
            hd = 2 * Math.PI + hd;
        }
        return hd;
    }

    /**
     * Replies true if east and north are different from Double.NaN and not infinite
     *
     * @return true if east and north are different from Double.NaN and not infinite
     */
    public boolean isValid() {
        return !Double.isNaN(x) && !Double.isNaN(y) && !Double.isInfinite(x) && !Double.isInfinite(y);
    }

    /**
     * Returns an EastNorth representing the this EastNorth rotated around
     * a given EastNorth by a given angle
     * @param pivot the center of the rotation
     * @param angle the angle of the rotation
     * @return EastNorth rotated object
     */
    public EastNorth rotate(EastNorth pivot, double angle) {
        double cosPhi = Math.cos(angle);
        double sinPhi = Math.sin(angle);
        double x = east() - pivot.east();
        double y = north() - pivot.north();
        double nx =  cosPhi * x + sinPhi * y + pivot.east();
        double ny = -sinPhi * x + cosPhi * y + pivot.north();
        return new EastNorth(nx, ny);
    }

    @Override
    public String toString() {
        return "EastNorth[e="+x+", n="+y+']';
    }

    /**
     * Compares two EastNorth values
     *
     * @return true if "x" and "y" values are within 1E-6 of each other
     */
    public boolean equalsEpsilon(EastNorth other, double e) {
        return Math.abs(x - other.x) < e && Math.abs(y - other.y) < e;
    }
}
