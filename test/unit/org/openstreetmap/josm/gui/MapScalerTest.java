// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.awt.Color;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MapScaler.AccessibleMapScaler;

/**
 * Unit tests of {@link MapScaler} class.
 */
public class MapScalerTest {

    /**
     * Setup tests
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        JOSMFixture.createUnitTestFixture().init(true);
    }

    /**
     * Unit test of {@link MapScaler#MapScaler}.
     */
    @Test
    public void testMapScaler() {
        assertEquals(Color.WHITE, MapScaler.getColor());
        Main.map.mapView.zoomTo(new EastNorth(0, 0), 10);
        assertEquals(10, Main.map.mapView.getScale(), 1e-5);
        assertEquals(1000, Main.map.mapView.getDist100Pixel(), 1e-3);

        MapScaler ms = new MapScaler(Main.map.mapView);
        assertEquals("/MapView/Scaler", ms.helpTopic());
        assertEquals(1000, Main.map.mapView.getDist100Pixel(), 1e-3);

        ms.paint(TestUtils.newGraphics());
        // This should not have changed scale
        assertEquals(1000, Main.map.mapView.getDist100Pixel(), 1e-3);


        AccessibleMapScaler ams = (AccessibleMapScaler) ms.getAccessibleContext();
        assertEquals(1000.0, ams.getCurrentAccessibleValue().doubleValue(), 1e-3);
        assertFalse(ams.setCurrentAccessibleValue(500));
        assertNull(ams.getMinimumAccessibleValue());
        assertNull(ams.getMaximumAccessibleValue());
    }
}
