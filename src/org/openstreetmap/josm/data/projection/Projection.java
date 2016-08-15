// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.projection;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * A projection, i.e.&nbsp;a class that supports conversion from lat/lon
 * to east/north and back.
 *
 * The conversion from east/north to the screen coordinates is simply a scale
 * factor and x/y offset.
 */
public interface Projection extends Projecting {
    /**
     * The default scale factor in east/north units per pixel
     * ({@link org.openstreetmap.josm.gui.NavigatableComponent#getState})).
     * FIXME: misnomer
     * @return the scale factor
     */
    double getDefaultZoomInPPD();

    /**
     * Convert from easting/norting to lat/lon.
     *
     * @param en the geographical point to convert (in projected coordinates)
     * @return the corresponding lat/lon (WGS84)
     */
    LatLon eastNorth2latlon(EastNorth en);

    /**
     * Describe the projection in one or two words.
     * @return the name / description
     */
    @Override
    String toString();

    /**
     * Return projection code.
     *
     * This should be a unique identifier.
     * If projection supports parameters, return a different code
     * for each set of parameters.
     *
     * The EPSG code can be used (if defined for the projection).
     *
     * @return the projection identifier
     */
    String toCode();

    /**
     * Get a filename compatible string (for the cache directory).
     * @return the cache directory name (base name)
     */
    String getCacheDirectoryName();

    /**
     * Get the bounds of the world.
     * @return the supported lat/lon rectangle for this projection
     */
    Bounds getWorldBoundsLatLon();

    /**
     * Get an approximate EastNorth box around the lat/lon world bounds.
     *
     * Note: The projection is only valid within the bounds returned by
     * {@link #getWorldBoundsLatLon()}. The lat/lon bounds need not be a
     * rectangular shape in east/north space. This method returns a box that
     * contains this shape.
     *
     * @return EastNorth box around the lat/lon world bounds
     */
    ProjectionBounds getWorldBoundsBoxEastNorth();

    /**
     * Find lat/lon-box containing all the area of a given rectangle in
     * east/north space.
     *
     * This is an approximate method. Points outside of the world should be ignored.
     *
     * @param pb the rectangle in projected space
     * @return minimum lat/lon box, that when projected, covers <code>pb</code>
     */
    Bounds getLatLonBoundsBox(ProjectionBounds pb);

    /**
     * Get the number of meters per unit of this projection. This more
     * defines the scale of the map, than real conversion of unit to meters
     * as this value is more less correct only along certain lines of true scale.
     *
     * Used by WMTS to properly scale tiles
     * @return meters per unit of projection
     */
    double getMetersPerUnit();

    /**
     * Does this projection natural order of coordinates is North East,
     * instead of East North
     *
     * @return true if natural order of coordinates is North East, false if East North
     */
    boolean switchXY();

    /**
     * Gets the object used as cache identifier when caching results of this projection.
     * @return The object to use as cache key
     */
    default Object getCacheKey() {
        return this;
    }
}
