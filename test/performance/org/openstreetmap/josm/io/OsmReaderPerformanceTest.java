// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.PerformanceTestUtils;
import org.openstreetmap.josm.PerformanceTestUtils.PerformanceTestTimer;
import org.openstreetmap.josm.data.osm.DataSet;

import sun.misc.IOUtils;

/**
 * This test tests how fast we are at reading an OSM file.
 * <p>
 * For this, we use the neubrandenburg-file, which is a good real world example of an OSM file. We ignore disk access times.
 *
 * @author Michael Zangl
 */
public class OsmReaderPerformanceTest {
    private static final int TIMES = 4;
    private static String DATA_FILE = "data_nodist/neubrandenburg.osm.bz2";

    @BeforeClass
    public static void createJOSMFixture() {
        JOSMFixture.createPerformanceTestFixture().init(true);
    }

    /**
     * Simulates a plain read of a .osm.bz2 file (from memory)
     * @throws IllegalDataException
     * @throws IOException
     */
    @Test
    public void testCompressed() throws IllegalDataException, IOException {
        runTest("compressed (.osm.bz2)", false);
    }

    /**
     * Simulates a plain read of a .osm file (from memory)
     * @throws IllegalDataException
     * @throws IOException
     */
    @Test
    public void test() throws IllegalDataException, IOException {
        runTest(".osm-file", true);
    }

    private void runTest(String what, boolean decompressBeforeRead) throws IllegalDataException, IOException {
        InputStream is = loadFile(decompressBeforeRead);
        PerformanceTestTimer timer = PerformanceTestUtils.startTimer("load " + what + " " + TIMES + " times");
        DataSet ds = null;
        for (int i = 0; i < TIMES; i++) {
            is.reset();

            ds = OsmReader.parseDataSet(decompressBeforeRead ? is : Compression.byExtension(DATA_FILE)
                    .getUncompressedInputStream(is), null);
        }
        timer.done();
        assertNotNull(ds);
    }

    private InputStream loadFile(boolean decompressBeforeRead) throws IOException {
        File file = new File(DATA_FILE);
        InputStream is;
        if (decompressBeforeRead) {
            is = Compression.getUncompressedFileInputStream(file);
        } else {
            is = new FileInputStream(file);
        }

        byte[] data = IOUtils.readFully(is, -1, false);
        is.close();
        ByteArrayInputStream memoryIs = new ByteArrayInputStream(data);

        return memoryIs;
    }

}
