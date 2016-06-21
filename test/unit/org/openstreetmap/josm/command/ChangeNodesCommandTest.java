// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.command.CommandTest.CommandTestData;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.User;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit tests of {@link ChangeNodesCommand} class.
 */
public class ChangeNodesCommandTest {

    /**
     * We need prefs for nodes.
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().i18n();
    private CommandTestData testData;

    /**
     * Set up the test data.
     */
    @Before
    public void createTestData() {
        testData = new CommandTestData();
    }

    /**
     * Test that empty ways are prevented.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testPreventEmptyWays() {
        new ChangeNodesCommand(testData.existingWay, Collections.<Node>emptyList());
    }

    /**
     * Test {@link ChangeNodesCommand#executeCommand()}
     */
    @Test
    public void testChange() {
        List<Node> newNodes = testData.existingWay.getNodes();
        Collections.reverse(newNodes);

        new ChangeNodesCommand(testData.existingWay, newNodes).executeCommand();
        assertArrayEquals(newNodes.toArray(), testData.existingWay.getNodes().toArray());

        // tags are unchanged
        assertEquals("existing", testData.existingWay.get("existing"));
        assertTrue(testData.existingWay.isModified());
    }
    /**
     * Test {@link ChangeCommand#undoCommand()}
     */
    @Test
    public void testUndo() {
        List<Node> newNodes = testData.existingWay.getNodes();
        Collections.reverse(newNodes);

        ChangeNodesCommand command = new ChangeNodesCommand(testData.existingWay, newNodes);
        command.executeCommand();
        command.undoCommand();
        Collections.reverse(newNodes);
        assertArrayEquals(newNodes.toArray(), testData.existingWay.getNodes().toArray());
        assertFalse(testData.existingWay.isModified());
    }

    /**
     * Tests {@link ChangeNodesCommand#fillModifiedData(java.util.Collection, java.util.Collection, java.util.Collection)}
     * @since xxx
     */
    @Test
    public void testFillModifiedData() {
        ArrayList<OsmPrimitive> modified = new ArrayList<>();
        ArrayList<OsmPrimitive> deleted = new ArrayList<>();
        ArrayList<OsmPrimitive> added = new ArrayList<>();
        new ChangeNodesCommand(testData.existingWay, Arrays.asList(testData.existingNode)).fillModifiedData(modified, deleted, added);
        assertArrayEquals(new Object[] {testData.existingWay}, modified.toArray());
        assertArrayEquals(new Object[] {}, deleted.toArray());
        assertArrayEquals(new Object[] {}, added.toArray());
   }

    /**
     * Test {@link ChangeNodesCommand#getDescriptionText()}
     * @since xxx
     */
    @Test
    public void testDescription() {
        Node node = new Node(LatLon.ZERO);
        node.put("name", "xy");
        Way way = new Way();
        way.addNode(node);
        way.put("name", "xy");

        assertTrue(new ChangeNodesCommand(way, Arrays.asList(node)).getDescriptionText().matches("Change nodes of.*xy.*"));
    }


    /**
     * Unit test of methods {@link ChangeNodesCommand#equals} and {@link ChangeNodesCommand#hashCode}.
     */
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(ChangeNodesCommand.class).usingGetClass()
            .withPrefabValues(Way.class,
                new Way(1), new Way(2))
            .withPrefabValues(DataSet.class,
                    new DataSet(), new DataSet())
            .withPrefabValues(User.class,
                    User.createOsmUser(1, "foo"), User.createOsmUser(2, "bar"))
            .withPrefabValues(OsmDataLayer.class,
                    new OsmDataLayer(new DataSet(), "1", null), new OsmDataLayer(new DataSet(), "2", null))
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }
}
