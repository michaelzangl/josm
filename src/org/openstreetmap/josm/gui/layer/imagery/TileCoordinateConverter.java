// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapViewState;
import org.openstreetmap.josm.gui.MapViewState.MapViewPoint;
import org.openstreetmap.josm.gui.MapViewState.MapViewRectangle;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.tools.Utils;

/**
 * This class handles tile coordinate management and computes their position in the map view.
 * @author Michael Zangl
 * @since 10651
 */
public class TileCoordinateConverter {
    private MapViewState displacedState;
    private TileSourceDisplaySettings settings;
    private TileSource tileSource;

    /**
     * Create a new coordinate converter for the map view.
     * @param mapView The map view.
     * @param tileSource The tile source to use when converting coordinates.
     * @param settings displacement settings.
     */
    public TileCoordinateConverter(MapViewState mapView, TileSource tileSource, TileSourceDisplaySettings settings) {
        this.displacedState = mapView.shifted(settings.getDisplacement());
        this.tileSource = tileSource;
        this.settings = settings;
    }

    private MapViewPoint pos(ICoordinate ll) {
        return displacedState.getPointFor(new LatLon(ll));
    }

//    /**
//     * Gets the projecting instance to use to convert between latlon and eastnorth coordinates.
//     * @return The {@link Projecting} instance.
//     */
//    public Projecting getProjecting() {
//        return mapView.getProjection(), settings.getDisplacement());
//    }

    /**
     * Gets the top left position of the tile inside the map view.
     * @param tile The tile
     * @return The positon.
     */
    public Point2D getPixelForTile(Tile tile) {
        ICoordinate coord = tile.getTileSource().tileXYToLatLon(tile);
        return pos(coord).getInView();
    }

    /**
     * Gets the position of the tile inside the map view.
     * @param tile The tile
     * @return The positon.
     */
    public MapViewRectangle getRectangleForTile(TilePosition tile) {
        MapViewPoint p1 = tileUV(tile, 0, 0);
        MapViewPoint p2 = tileUV(tile, 1, 1);

        return p1.rectTo(p2);
    }

    /**
     * Gets an affine transform that maps image u/v (0..1) space to east/north space.
     * <p>
     * You need to scale it by the image size to draw the buffered image.
     * @param tile
     * @param u1
     * @param v1
     * @param u2
     * @param v2
     * @param u3
     * @param v3
     * @return the transform
     */
    public AffineTransform getTransformForTile(TilePosition tile, double u1, double v1, double u2, double v2, double u3, double v3) {
        MapViewPoint p1 = tileUV(tile, u1, v1);
        MapViewPoint p2 = tileUV(tile, u2, v2);
        MapViewPoint p3 = tileUV(tile, u3, v3);

        // We compute the matrix in a way that p_i.inView is mapped to the corresponding image position.
        // ( u1 )   ( m00 m01 m02  )   (p1.viewX )
        // ( v1 ) * ( m10 m11 m12  ) = (p1.viewY )
        // ( 1  )   (  0   0   1   )   (1        )
        // ( u2 )   ( m00 m01 m02  )   (p2.viewX )
        // ( v2 ) * ( m10 m11 m12  ) = (p2.viewY )
        // ( 1  )   (  0   0   1   )   (1        )
        // ( u3 )   ( m00 m01 m02  )   (p3.viewX )
        // ( v3 ) * ( m10 m11 m12  ) = (p3.viewY )
        // ( 1  )   (  0   0   1   )   (1        )

        // u1 * m00 + v1 * m01 + m02 = p1.viewX
        // u2 * m00 + v2 * m01 + m02 = p2.viewX
        // u3 * m00 + v3 * m01 + m02 = p3.viewX
        // u1 * m10 + v1 * m11 + m12 = p1.viewY
        // u2 * m10 + v2 * m11 + m12 = p2.viewY
        // u3 * m10 + v3 * m11 + m12 = p3.viewY

        // u1        * m00 + v1        * m01 + m02 = p1.viewX
        // (u2 - u1) * m00 + (v2 - v1) * m01       = p2.viewX - p1.viewX
        // (u3 - u1) * m00 + (v3 - v1) * m01       = p3.viewX - p1.viewX

        // if v2 != v1 and v3 != v1
        // (u2 - u1) / (v2 - v1) * m00 + m01       = (p2.viewX - p1.viewX) / (v2 - v1)
        // (u3 - u1) / (v3 - v1) * m00 + m01       = (p3.viewX - p1.viewX) / (v3 - v1)

        // m00 = ((p2.viewX - p1.viewX) / (v2 - v1) - (p3.viewX - p1.viewX) / (v3 - v1)) / ((u2 - u1) / (v2 - v1) - (u3 - u1) / (v3 - v1))
        // m01 = (p3.viewX - p1.viewX) / (v3 - v1) - (u3 - u1) / (v3 - v1) * m00
        // m02 = p1.viewX - u1 * m00 + v1 * m01

        // if v2 == v1:
        // u1        * m00 + v1        * m01 + m02 = p1.viewX
        // (u2 - u1) * m00 +                       = p2.viewX - p1.viewX
        // (u3 - u1) * m00 + (v3 - v1) * m01       = p3.viewX - p1.viewX

        // if v3 == v1
        // u1        * m00 + v1        * m01 + m02 = p1.viewX
        // (u2 - u1) * m00 + (v2 - v1) * m01       = p2.viewX - p1.viewX
        // (u3 - u1) * m00 +                       = p3.viewX - p1.viewX


        double du2 = u2 - u1;
        double du3 = u3 - u1;
        double dv2 = v2 - v1;
        double dv3 = v3 - v1;
        double p1x = p1.getInView().getX();
        double p2x = p2.getInView().getX();
        double p3x = p3.getInView().getX();
        double p1y = p1.getInView().getY();
        double p2y = p2.getInView().getY();
        double p3y = p3.getInView().getY();

        double m00;
        double m01;
        if (Utils.equalsEpsilon(0, dv2)) {
            if (Utils.equalsEpsilon(0, du2) || Utils.equalsEpsilon(0, dv3)) {
                // unsolveable
                return new AffineTransform();
            }
            m00 = (p2x - p1x) / du2;
            m01 = (p3x - p1x) / dv3 - du3 / dv3 * m00;
       } else if (Utils.equalsEpsilon(0, dv3)) {
            if (Utils.equalsEpsilon(0, du3)) {
                // unsolveable
                return new AffineTransform();
            }
            m00 = (p3x - p1x) / du3;
            m01 = (p2x - p1x) / dv2 - du2 / dv2 * m00;
        } else {
            m00 = ((p2x - p1x) / dv2 - (p3x - p1x) / dv3) / (du2 / dv2 - du3 / dv3);
            m01 = (p3x - p1x) / dv3 - du3 / dv3 * m00;
        }
        double m02 = p1x - u1 * m00 + v1 * m01;

        double m10;
        double m11;
        if (Utils.equalsEpsilon(0, dv2)) {
            m10 = (p2y - p1y) / du2;
            m11 = (p3y - p1y) / dv3 - du3 / dv3 * m10;
       } else if (Utils.equalsEpsilon(0, dv3)) {
            m10 = (p3y - p1y) / du3;
            m11 = (p2y - p1y) / dv2 - du2 / dv2 * m10;
        } else {
            m10 = ((p2y - p1y) / dv2 - (p3y - p1y) / dv3) / (du2 / dv2 - du3 / dv3);
            m11 = (p3y - p1y) / dv3 - du3 / dv3 * m10;
        }
        double m12 = p1y - u1 * m10 + v1 * m11;

        return new AffineTransform(new double[] {
                m00, m10, m01, m11, m02, m12
        });
    }

    private MapViewPoint tileUV(TilePosition tile, double u, double v) {
        ICoordinate tileLatLon = tileSource.tileXYToLatLon(tile.getX(), tile.getY(), tile.getZoom());
        if (Utils.equalsEpsilon(0, u) && Utils.equalsEpsilon(0, v)) {
            return pos(tileLatLon);
        } else {
            ICoordinate nextTile = tileSource.tileXYToLatLon(tile.getX() + 1, tile.getY() + 1, tile.getZoom());
            return displacedState.getPointFor(new LatLon(
                    (1 - v) * tileLatLon.getLat() + v * nextTile.getLat(),
                    (1 - u) * tileLatLon.getLon() + u * nextTile.getLon()));
        }
    }

    /**
     * Returns average number of screen pixels per tile pixel for current mapview
     * @param zoom zoom level
     * @return average number of screen pixels per tile pixel
     */
    public double getScaleFactor(int zoom) {
        Bounds area = displacedState.getViewArea().getCornerBounds();
        TileXY t1 = tileSource.latLonToTileXY(area.getMin().toCoordinate(), zoom);
        TileXY t2 = tileSource.latLonToTileXY(area.getMax().toCoordinate(), zoom);

        double screenPixels = displacedState.getViewWidth() * displacedState.getViewHeight();
        int tileSize = tileSource.getTileSize();
        double tilePixels = Math.abs((t2.getY() - t1.getY()) * (t2.getX() - t1.getX()) * tileSize * tileSize);
        if (screenPixels < 1e-10 || tilePixels < 1e-10) {
            return 1;
        } else {
            return screenPixels / tilePixels;
        }
    }

    /**
     * Get the tiles in view at the given zoom level.
     * @param zoom The zoom level
     * @return The tiles that are in the view.
     */
    public TileRange getViewAtZoom(int zoom) {
        Bounds view = displacedState.getViewArea().getLatLonBoundsBox();
        view = view.intersect(displacedState.getProjection().getWorldBoundsLatLon());
        if (view == null) {
            return new TileRange();
        } else {
            TileXY t1 = tileSource.latLonToTileXY(view.getMin().toCoordinate(), zoom);
            TileXY t2 = tileSource.latLonToTileXY(view.getMax().toCoordinate(), zoom);
            return new TileRange(t1, t2, zoom);
        }
    }

    /**
     * Gets the mathematically best zoom. May be out of range.
     * @return The zoom
     */
    public int getBestZoom() {
        double factor = getScaleFactor(1); // check the ratio between area of tilesize at zoom 1 to current view
        double result = Math.log(factor) / Math.log(2) / 2;
        /*
         * Math.log(factor)/Math.log(2) - gives log base 2 of factor
         * We divide result by 2, as factor contains ratio between areas. We could do Math.sqrt before log, or just divide log by 2
         *
         * ZOOM_OFFSET controls, whether we work with overzoomed or underzoomed tiles. Positive ZOOM_OFFSET
         * is for working with underzoomed tiles (higher quality when working with aerial imagery), negative ZOOM_OFFSET
         * is for working with overzoomed tiles (big, pixelated), which is good when working with high-dpi screens and/or
         * maps as a imagery layer
         */

        return (int) Math.round(result + 1 + AbstractTileSourceLayer.ZOOM_OFFSET.get() / 1.9);
    }

    /**
     * Gets the clip to use to only paint inside the projection
     * @return The clip.
     */
    public Shape getProjectionClip() {
        return displacedState.getArea(displacedState.getProjection().getWorldBoundsLatLon());
    }
}
