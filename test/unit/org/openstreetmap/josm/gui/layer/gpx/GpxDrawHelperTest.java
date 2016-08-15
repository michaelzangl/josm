// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.gpx;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.preferences.ColorProperty;
import org.openstreetmap.josm.io.GpxReaderTest;
import org.openstreetmap.josm.tools.ColorHelper;
import org.xml.sax.SAXException;

/**
 * Unit tests of {@link GpxDrawHelper} class.
 */
public class GpxDrawHelperTest {

    /**
     * Setup test.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        JOSMFixture.createUnitTestFixture().init(false);
    }

    /**
     * Non-regression test for ticket <a href="https://josm.openstreetmap.de/ticket/12312">#12312</a>.
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    @Test
    public void testTicket12312() throws FileNotFoundException, IOException, SAXException {
        Main.pref.put("draw.rawgps.colors.dynamic.layer 12312", true);
        Main.pref.putInteger("draw.rawgps.colors.layer 12312", GpxDrawHelper.ColorMode.VELOCITY.toIndex());
        final List<String> colors = calculateColors(TestUtils.getRegressionDataFile(12312, "single_trackpoint.gpx"), "12312", 1);
        assertEquals("[#FF00FF]", colors.toString());
    }

    /**
     * Tests coloring of an example track using the default color.
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    @Test
    public void testNone() throws IOException, SAXException {
        final List<String> colors = calculateColors("data_nodist/2094047.gpx", "000", 10);
        assertEquals("[#FF00FF, #FF00FF, #FF00FF, #FF00FF, #FF00FF, #FF00FF, #FF00FF, #FF00FF, #FF00FF, #FF00FF]", colors.toString());
    }

    /**
     * Tests coloring of an example track using its velocity.
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    @Test
    public void testVelocity() throws IOException, SAXException {
        Main.pref.putInteger("draw.rawgps.colors.layer 001", GpxDrawHelper.ColorMode.VELOCITY.toIndex());
        final List<String> colors = calculateColors("data_nodist/2094047.gpx", "001", 10);
        assertEquals("[#FF00FF, #FFAD00, #FFA800, #FFA800, #FF9E00, #FF9400, #FF7000, #FF7000, #FF8000, #FF9400]", colors.toString());
    }

    /**
     * Tests coloring of an example track using its velocity with a dynamic scale
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    @Test
    public void testVelocityDynamic() throws IOException, SAXException {
        Main.pref.putInteger("draw.rawgps.colors.layer 002", GpxDrawHelper.ColorMode.VELOCITY.toIndex());
        Main.pref.put("draw.rawgps.colors.dynamic.layer 002", true);
        final List<String> colors = calculateColors("data_nodist/2094047.gpx", "002", 10);
        assertEquals("[#FF00FF, #00FFE0, #00FFC2, #00FFC2, #00FF75, #00FF3D, #99FF00, #94FF00, #38FF00, #00FF38]", colors.toString());
    }

    /**
     * Tests coloring of an example track using its direction.
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    @Test
    public void testDirection() throws IOException, SAXException {
        Main.pref.putInteger("draw.rawgps.colors.layer 003", GpxDrawHelper.ColorMode.DIRECTION.toIndex());
        final List<String> colors = calculateColors("data_nodist/2094047.gpx", "003", 10);
        assertEquals("[#FF00FF, #EAEC25, #EDEA26, #EDE525, #ECD322, #EBB81D, #E85A0D, #E73708, #E84D0B, #EA8A15]", colors.toString());
    }

    /**
     * Tests coloring of an example track using its direction.
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    @Test
    public void testTime() throws IOException, SAXException {
        Main.pref.putInteger("draw.rawgps.colors.layer 003", GpxDrawHelper.ColorMode.TIME.toIndex());
        final List<String> colors = calculateColors("data_nodist/2094047.gpx", "003", 10);
        assertEquals("[#FF00FF, #FF0000, #FF0000, #FF0500, #FF0500, #FF0A00, #FF0A00, #FF1F00, #FF2E00, #FF3300]", colors.toString());
    }

    /**
     *
     * @param fileName the GPX filename to parse
     * @param layerName the layer name used to fetch the color settings, see {@link GpxDrawHelper#readPreferences(java.lang.String)}
     * @param n the number of waypoints of the first track/segment to analyze
     * @return the HTML color codes for the first {@code n} points
     * @throws IOException if any I/O error occurs
     * @throws FileNotFoundException if the data file is not found
     * @throws SAXException if any SAX error occurs
     */
    static List<String> calculateColors(String fileName, String layerName, int n) throws IOException, SAXException {
        final GpxData data = GpxReaderTest.parseGpxData(fileName);
        final GpxDrawHelper gdh = new GpxDrawHelper(data, new ColorProperty("x", Color.MAGENTA));
        gdh.readPreferences(layerName);
        gdh.calculateColors();
        final Iterator<WayPoint> wayPointIterator = data.tracks.iterator().next().getSegments().iterator().next().getWayPoints().iterator();
        final List<String> colorCodes = new ArrayList<>(n);
        while (colorCodes.size() < n) {
            colorCodes.add(ColorHelper.color2html(wayPointIterator.next().customColoring));
        }
        return colorCodes;
    }
}
