// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.command.CommandTest.CommandTestDataWithRelation;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.User;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit tests of {@link DeleteCommand} class.
 */
public class DeleteCommandTest {

    /**
     * We need prefs for nodes.
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().i18n();
    private CommandTestDataWithRelation testData;

    /**
     * Set up the test data.
     */
    @Before
    public void createTestData() {
        testData = new CommandTestDataWithRelation();
    }

    /**
     * A simple deletion test with no references
     */
    @Test
    public void testSimpleDelete() {
        Node node = testData.createNode(15);
        assertTrue(testData.layer.data.allPrimitives().contains(node));

        new DeleteCommand(node).executeCommand();

        assertTrue(node.isDeleted());
        assertTrue(node.isModified());
        assertFalse(testData.layer.data.allNonDeletedPrimitives().contains(node));
    }

    /**
     * A delete should not delete refered objects but should should remove the reference.
     */
    @Test
    public void testDeleteIgnoresReferences() {
        assertTrue(testData.existingNode.getReferrers().contains(testData.existingRelation));
        new DeleteCommand(testData.existingRelation).executeCommand();

        assertTrue(testData.existingRelation.isDeleted());
        assertEquals(0, testData.existingRelation.getMembersCount());
        assertFalse(testData.existingNode.isDeleted());
        assertFalse(testData.existingWay.isDeleted());
        assertFalse(testData.existingNode.getReferrers().contains(testData.existingRelation));

        // same for the way
        assertTrue(testData.existingNode.getReferrers().contains(testData.existingWay));
        new DeleteCommand(testData.existingWay).executeCommand();
        assertEquals(0, testData.existingWay.getNodesCount());
        assertFalse(testData.existingNode.getReferrers().contains(testData.existingWay));
    }

    /**
     * A delete should delete all objects with references to the deleted one
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteFailsOnDelted() {
        new DeleteCommand(testData.existingRelation).executeCommand();

        new DeleteCommand(testData.existingRelation).executeCommand();
    }

    /**
     * A delete should delete all objects with references to the deleted one
     */
    @Test
    public void testReferedDelete() {
        DeleteCommand.deleteWithReferences(testData.layer, Arrays.asList(testData.existingNode), true).executeCommand();

        assertTrue(testData.existingNode.isDeleted());
        assertEquals(0, testData.existingWay.getNodesCount());
        assertTrue(testData.existingWay.isDeleted());
    }

    /**
     * Delete nodes that would be without reference afterwards.
     */
    @Test
    public void testDelteNodesInWay() {
        testData.existingNode.removeAll();
        // That untagged node should be deleted.
        testData.existingNode2.removeAll();
        DeleteCommand.delete(testData.layer, Arrays.asList(testData.existingWay), true, true).executeCommand();

        assertTrue(testData.existingWay.isDeleted());
        assertTrue(testData.existingNode2.isDeleted());
        assertFalse(testData.existingNode.isDeleted());
        assertFalse(testData.existingRelation.isDeleted());

        // Same test, now with tagged nodes
        Node node1 = testData.createNode(15);
        Node node2 = testData.createNode(16);
        Node node3 = testData.createNode(17);
        Node node4 = testData.createNode(18);
        node2.removeAll();
        node4.removeAll();
        Way way1 = new Way(25, 1);
        way1.setNodes(Arrays.asList(node1, node2, node3));
        testData.layer.data.addPrimitive(way1);
        Way way2 = new Way(26, 1);
        way2.setNodes(Arrays.asList(node2, node3, node4));
        testData.layer.data.addPrimitive(way2);
        DeleteCommand.delete(testData.layer, Arrays.asList(way1, way2), true, true).executeCommand();

        assertTrue(way1.isDeleted());
        assertTrue(way2.isDeleted());
        assertFalse(node1.isDeleted());
        assertTrue(node2.isDeleted());
        assertFalse(node3.isDeleted());
        assertTrue(node4.isDeleted());
    }

    /**
     * Unit test of methods {@link DeleteCommand#equals} and {@link DeleteCommand#hashCode}.
     */
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(DeleteCommand.class).usingGetClass()
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
