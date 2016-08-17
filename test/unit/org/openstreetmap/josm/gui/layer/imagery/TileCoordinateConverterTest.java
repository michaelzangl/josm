// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import static org.junit.Assert.assertEquals;

import java.awt.Image;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapViewState.MapViewRectangle;
import org.openstreetmap.josm.gui.layer.LayerManagerTest;
import org.openstreetmap.josm.gui.util.GuiHelper;

/**
 * Test {@link TileCoordinateConverter}
 * @author Michael Zangl
 * @since xxx
 */
public class TileCoordinateConverterTest {
    private TileCoordinateConverter converter;

    /**
     * Setup test.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        JOSMFixture.createUnitTestFixture().init(true);
        Main.getLayerManager().addLayer(new LayerManagerTest.TestLayer());
        GuiHelper.runInEDTAndWait(() -> {});
    }

    @Before
    public void setUp() {
        MapFrame map = Main.map;
        map.mapView.zoomTo(new EastNorth(0, 0), 1);
        converter = new TileCoordinateConverter(map.mapView.getState(), new TileSource() {

            @Override
            public boolean requiresAttribution() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getTermsOfUseURL() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getTermsOfUseText() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAttributionText(int zoom, ICoordinate topLeft, ICoordinate botRight) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAttributionLinkURL() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getAttributionImageURL() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Image getAttributionImage() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Point latLonToXY(ICoordinate point, int zoom) {
                return latLonToXY(point.getLat(), point.getLon(), zoom);
            }

            @Override
            public ICoordinate xyToLatLon(Point point, int zoom) {
                return xyToLatLon(point.x, point.y, zoom);
            }

            @Override
            public TileXY latLonToTileXY(ICoordinate point, int zoom) {
                return latLonToTileXY(point.getLat(), point.getLon(), zoom);
            }

            @Override
            public ICoordinate tileXYToLatLon(TileXY xy, int zoom) {
                return tileXYToLatLon(xy.getXIndex(), xy.getYIndex(), zoom);
            }

            @Override
            public ICoordinate tileXYToLatLon(Tile tile) {
                return tileXYToLatLon(tile.getXtile(), tile.getYtile(), tile.getZoom());
            }

            @Override
            public boolean isNoTileAtZoom(Map<String, List<String>> headers, int statusCode, byte[] content) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getTileYMin(int zoom) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getTileYMax(int zoom) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getTileXMin(int zoom) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getTileXMax(int zoom) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getTileUrl(int zoom, int tilex, int tiley) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getTileSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getTileId(int zoom, int tilex, int tiley) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getMinZoom() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, String> getMetadata(Map<String, List<String>> headers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getMaxZoom() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public double getDistance(double la1, double lo1, double la2, double lo2) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getDefaultTileSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Point latLonToXY(double lat, double lon, int zoom) {
                return new Point((int) lat / 13, (int) lon - 4);
            }

            @Override
            public ICoordinate xyToLatLon(int x, int y, int zoom) {
                return new Coordinate(x * 13, y + 4);
            }

            @Override
            public TileXY latLonToTileXY(double lat, double lon, int zoom) {
                return new TileXY(lat / 13, lon - 4);
            }

            @Override
            public ICoordinate tileXYToLatLon(int x, int y, int zoom) {
                return new Coordinate(x * 13, y + 4);
            }
        }, new TileSourceDisplaySettings());
    }

    @Test
    public void testGetRectangleForTile() {
        EastNorth p1 = Main.getProjection().latlon2eastNorth(new LatLon(26, 7));
        MapViewRectangle rect = converter.getRectangleForTile(new TilePosition(2, 3, 1));
        assertEquals(p1.getX(), rect.getProjectionBounds().minEast, 1e-10);
        assertEquals(p1.getY(), rect.getProjectionBounds().minNorth, 1e-10);
    }

    @Test
    public void testGetTransformForTile() {
        AffineTransform transform = new AffineTransform();
        transform.setToTranslation(1, 10);
        assertEquals(1, transform.getTranslateX(), 1e-10);
        testGetTransform(transform);
    }

    private void testGetTransform(AffineTransform transform) {
        TilePosition tile = new TilePosition(2, 3, 1);
        Point2D p1 = Main.map.mapView.getState().getPointFor(new LatLon(26, 7)).getInView();
        Point2D p2 = Main.map.mapView.getState().getPointFor(new LatLon(39, 8)).getInView();

        transform = converter.getTransformForTile(tile, 0, 0, 0, 1, 1, 1);

        assertEquals(p1.getX(), transform.getTranslateX(), 1e-10);
        assertEquals(p1.getY(), transform.getTranslateY(), 1e-10);

        Point2D p1converted = transform.transform(new Point2D.Double(0, 0), null);
        Point2D p2converted = transform.transform(new Point2D.Double(1, 1), null);

        assertEquals(p1.getX(), p1converted.getX(), 1e-10);
        assertEquals(p1.getY(), p1converted.getY(), 1e-10);
        assertEquals(p2.getX(), p2converted.getX(), 1e-10);
        assertEquals(p2.getY(), p2converted.getY(), 1e-10);
    }
}
