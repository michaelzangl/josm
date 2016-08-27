// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.xml.sax.SAXException;

/**
 * Tests the {@link GpxReader}.
 */
public class GpxReaderTest {

    /**
     * Parses a GPX file and returns the parsed data
     * @param filename the GPX file to parse
     * @return the parsed GPX data
     * @throws IOException if an error occurs during reading.
     * @throws SAXException if a SAX error occurs
     */
    public static GpxData parseGpxData(String filename) throws IOException, SAXException {
        final GpxData result;
        try (FileInputStream in = new FileInputStream(new File(filename))) {
            GpxReader reader = new GpxReader(in);
            assertTrue(reader.parse(false));
            result = reader.getGpxData();
        }
        return result;
    }

    /**
     * Tests the {@code munich.gpx} test file.
     * @throws Exception if something goes wrong
     */
    @Test
    public void testMunich() throws Exception {
        final GpxData result = parseGpxData("data_nodist/munich.gpx");
        assertEquals(2762, result.getTracks().size());
        assertEquals(0, result.getRoutes().size());
        assertEquals(903, result.getWaypoints().size());

        final WayPoint tenthWayPoint = ((List<WayPoint>) result.waypoints).get(10);
        assertEquals("128970", tenthWayPoint.get(GpxData.GPX_NAME));
        assertEquals(new LatLon(48.183956146240234, 11.43463134765625), tenthWayPoint.getCoor());
    }

    /**
     * Tests invalid data.
     * @throws Exception always SAXException
     */
    @Test(expected = SAXException.class)
    public void testException() throws Exception {
        new GpxReader(new ByteArrayInputStream("--foo--bar--".getBytes(StandardCharsets.UTF_8))).parse(true);
    }
}
