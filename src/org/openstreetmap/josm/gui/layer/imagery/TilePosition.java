// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileXY;

/**
 * The position of a single tile. In contrast to {@link TileXY}, this stores the position of the whole tile.
 * @author Michael Zangl
 * @since xxx
 */
public class TilePosition {
    private final int x;
    private final int y;
    private final int zoom;

    /**
     * Create a new tile position object
     * @param x The x coordinate
     * @param y The y coordinate
     * @param zoom The zoom at which the tile is.
     */
    TilePosition(int x, int y, int zoom) {
        super();
        this.x = x;
        this.y = y;
        this.zoom = zoom;
    }

    /**
     * Create a new tile position object
     * @param tile The tile from wich the position should be copied.
     */
    public TilePosition(Tile tile) {
        this(tile.getXtile(), tile.getYtile(), tile.getZoom());
    }

    /**
     * @return the x position
     */
    public int getX() {
        return x;
    }

    /**
     * @return the y position
     */
    public int getY() {
        return y;
    }

    /**
     * @return the zoom
     */
    public int getZoom() {
        return zoom;
    }

    /**
     * Gets an x/y coordinate inside this tile
     * @param du x delta. Range should be 0..1
     * @param dv y delta. Range should be 0..1
     * @return The x/y coordinate
     */
    public TileXY uv(double du, double dv) {
        return new TileXY(getX() + du, getY() + dv);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        result = prime * result + zoom;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TilePosition other = (TilePosition) obj;
        if (x != other.x)
            return false;
        if (y != other.y)
            return false;
        if (zoom != other.zoom)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "TilePosition [x=" + x + ", y=" + y + ", zoom=" + zoom + "]";
    }
}