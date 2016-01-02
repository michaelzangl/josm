// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.CombineWayAction.NodeGraph;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;

/**
 * Unit tests for class CombineWayAction.
 */
public class CombineWayActionTest {

    /**
     * Setup test.
     */
    @BeforeClass
    public static void setUp() {
        JOSMFixture.createUnitTestFixture().init(false);
    }

    /**
     * Non-regression test for bug #11957.
     * @throws IOException if any I/O error occurs
     * @throws IllegalDataException if OSM parsing fails
     */
    @Test
    public void testTicket11957() throws IOException, IllegalDataException {
        try (InputStream is = new FileInputStream(TestUtils.getRegressionDataFile(11957, "data.osm"))) {
            DataSet ds = OsmReader.parseDataSet(is, null);
            NodeGraph graph = NodeGraph.createNearlyUndirectedGraphFromNodeWays(ds.getWays());
            List<Node> path = graph.buildSpanningPath();
            assertEquals(10, path.size());
            Set<Long> firstAndLastObtained = new HashSet<>();
            firstAndLastObtained.add(path.get(0).getId());
            firstAndLastObtained.add(path.get(path.size()-1).getId());
            Set<Long> firstAndLastExpected = new HashSet<>();
            firstAndLastExpected.add(1618969016L);
            firstAndLastExpected.add(35213705L);
            assertEquals(firstAndLastExpected, firstAndLastObtained);
        }
    }
}
