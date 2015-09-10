// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.junit.Assert.assertEquals;
import static org.openstreetmap.josm.TestUtils.assertEastNorthEquals;
import static org.openstreetmap.josm.TestUtils.assertLatLonEquals;
import static org.openstreetmap.josm.TestUtils.assertPointEquals;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.util.GuiHelper;

/**
 * Some tests for the {@link NavigatableComponent} class.
 * @author Michael Zangl
 *
 */
public class NavigatableComponentTest {

    private static final int HEIGHT = 200;
    private static final int WIDTH = 300;
    private NavigatableComponent component;

    /**
     * Setup test.
     */
    @BeforeClass
    public static void setUp() {
        JOSMFixture.createUnitTestFixture().init();
    }

    /**
     * Create a new, fresh {@link NavigatableComponent}
     */
    @Before
    public void setup() {
        component = new NavigatableComponent();
        component.setBounds(new Rectangle(WIDTH, HEIGHT));
        // wait for the event to be propagated.
        GuiHelper.runInEDTAndWait(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    /**
     * Test if the default scale was set correctly.
     */
    @Test
    public void testDefaultScale() {
        assertEquals(Main.getProjection().getDefaultZoomInPPD(), component.getScale(), 0.00001);
    }

    /**
     * Tests {@link NavigatableComponent#getPoint2D(EastNorth)}
     */
    @Test
    public void testPoint2DEastNorth() {
        assertPointEquals(new Point2D.Double(), component.getPoint2D((EastNorth) null));
        Point2D shouldBeCenter = component.getPoint2D(component.getCenter());
        assertPointEquals(new Point2D.Double(WIDTH / 2, HEIGHT / 2), shouldBeCenter);

        EastNorth testPoint = component.getCenter().add(300 * component.getScale(), 200 * component.getScale());
        Point2D testPointConverted = component.getPoint2D(testPoint);
        assertPointEquals(new Point2D.Double(WIDTH / 2 + 300, HEIGHT / 2 - 200), testPointConverted);
    }

    /**
     * TODO: Implement this test.
     */
    @Test
    public void testPoint2DLatLon() {
        assertPointEquals(new Point2D.Double(), component.getPoint2D((LatLon) null));
        // TODO: Really test this.
    }

    /**
     * Tests {@link NavigatableComponent#zoomTo(LatLon)}
     */
    @Test
    public void testZoomToLatLon() {
        component.zoomTo(new LatLon(10, 10));
        Point2D shouldBeCenter = component.getPoint2D(new LatLon(10, 10));
        assertPointEquals(new Point2D.Double(WIDTH / 2, HEIGHT / 2), shouldBeCenter);
    }

    /**
     * Tests {@link NavigatableComponent#zoomToFactor(double)} and {@link NavigatableComponent#zoomToFactor(EastNorth, double)}
     */
    @Test
    public void testZoomToFactor() {
        EastNorth center = component.getCenter();
        double initialScale = component.getScale();

        // zoomToFactor(double)
        component.zoomToFactor(0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertEastNorthEquals(center, component.getCenter());
        component.zoomToFactor(2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertEastNorthEquals(center, component.getCenter());

        // zoomToFactor(EastNorth, double)
        EastNorth newCenter = new EastNorth(10, 20);
        component.zoomToFactor(newCenter, 0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertEastNorthEquals(newCenter, component.getCenter());
        component.zoomToFactor(newCenter, 2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertEastNorthEquals(newCenter, component.getCenter());
    }

    /**
     * Tests {@link NavigatableComponent#getEastNorth(int, int)}
     */
    @Test
    public void testGetEastNorth() {
        EastNorth center = component.getCenter();
        assertEastNorthEquals(center, component.getEastNorth(WIDTH / 2, HEIGHT / 2));

        EastNorth testPoint = component.getCenter().add(WIDTH * component.getScale(), HEIGHT * component.getScale());
        assertEastNorthEquals(testPoint, component.getEastNorth(3 * WIDTH / 2, -HEIGHT / 2));
    }

    /**
     * Tests {@link NavigatableComponent#zoomToFactor(double, double, double)}
     */
    @Test
    public void testZoomToFactorCenter() {
        // zoomToFactor(double, double, double)
        // assumes getEastNorth works as expected
        EastNorth testPoint1 = component.getEastNorth(0, 0);
        EastNorth testPoint2 = component.getEastNorth(200, 150);
        double initialScale = component.getScale();

        component.zoomToFactor(0, 0, 0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertEastNorthEquals(testPoint1, component.getEastNorth(0, 0));
        component.zoomToFactor(0, 0, 2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertEastNorthEquals(testPoint1, component.getEastNorth(0, 0));

        component.zoomToFactor(200, 150, 0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertEastNorthEquals(testPoint2, component.getEastNorth(200, 150));
        component.zoomToFactor(200, 150, 2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertEastNorthEquals(testPoint2, component.getEastNorth(200, 150));

    }

    /**
     * Tests {@link NavigatableComponent#getProjectionBounds()}
     */
    @Test
    public void testGetProjectionBounds() {
        ProjectionBounds bounds = component.getProjectionBounds();
        assertEastNorthEquals(component.getCenter(), bounds.getCenter());

        assertEastNorthEquals(component.getEastNorth(0, HEIGHT), bounds.getMin());
        assertEastNorthEquals(component.getEastNorth(WIDTH, 0), bounds.getMax());
    }

    /**
     * Tests {@link NavigatableComponent#getRealBounds()}
     */
    @Test
    public void testGetRealBounds() {
        Bounds bounds = component.getRealBounds();
        assertLatLonEquals(component.getLatLon(WIDTH / 2, HEIGHT / 2), bounds.getCenter());

        assertLatLonEquals(component.getLatLon(0, HEIGHT), bounds.getMin());
        assertLatLonEquals(component.getLatLon(WIDTH, 0), bounds.getMax());
    }

}
