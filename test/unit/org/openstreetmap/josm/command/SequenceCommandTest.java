// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.command.CommandTest.CommandTestDataWithRelation;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.User;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit tests of {@link SequenceCommand} class.
 */
public class SequenceCommandTest {

    /**
     * We need prefs for nodes.
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences();
    private CommandTestDataWithRelation testData;

    /**
     * Set up the test data.
     */
    @Before
    public void createTestData() {
        testData = new CommandTestDataWithRelation();
    }

    /**
     * Test {@link SequenceCommand#executeCommand()}
     * @since xxx
     */
    @Test
    public void testExecute() {
        final TestCommand command1 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode));
        TestCommand command2 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode2)) {
            @Override
            public boolean executeCommand() {
                assertTrue(command1.executed);
                return super.executeCommand();
            }
        };
        SequenceCommand command = new SequenceCommand("seq", Arrays.<Command> asList(command1, command2));

        command.executeCommand();

        assertTrue(command1.executed);
        assertTrue(command2.executed);
    }

    /**
     * Test {@link SequenceCommand#undoCommand()}
     * @since xxx
     */
    @Test
    public void testUndo() {
        final TestCommand command2 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode2));
        TestCommand command1 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode)) {
            @Override
            public void undoCommand() {
                assertFalse(command2.executed);
                super.undoCommand();
            }
        };
        SequenceCommand command = new SequenceCommand("seq", Arrays.<Command> asList(command1, command2));

        command.executeCommand();

        command.undoCommand();

        assertFalse(command1.executed);
        assertFalse(command2.executed);

        command.executeCommand();

        assertTrue(command1.executed);
        assertTrue(command2.executed);
    }

    /**
     * Test {@link SequenceCommand#undoCommand()}
     * @since xxx
     */
    @Test
    public void testGetLastCommand() {
        final TestCommand command1 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode));
        final TestCommand command2 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode2));

        assertEquals(command2, new SequenceCommand("seq", command1, command2).getLastCommand());
        assertNull(new SequenceCommand("seq").getLastCommand());
    }

    /**
     * Tests {@link SequenceCommand#fillModifiedData(java.util.Collection, java.util.Collection, java.util.Collection)}
     * @since xxx
     */
    @Test
    public void testFillModifiedData() {
        Command command1 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode));
        Command command2 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode2));
        Command command3 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingWay)) {
            @Override
            public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                    Collection<OsmPrimitive> added) {
                deleted.addAll(primitives);
            }
        };
        Command command4 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingRelation)) {
            @Override
            public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                    Collection<OsmPrimitive> added) {
                added.addAll(primitives);
            }
        };

        ArrayList<OsmPrimitive> modified = new ArrayList<>();
        ArrayList<OsmPrimitive> deleted = new ArrayList<>();
        ArrayList<OsmPrimitive> added = new ArrayList<>();
        SequenceCommand command = new SequenceCommand("seq", command1, command2, command3, command4);
        command.fillModifiedData(modified, deleted, added);
        assertArrayEquals(new Object[] { testData.existingNode, testData.existingNode2 }, modified.toArray());
        assertArrayEquals(new Object[] { testData.existingWay }, deleted.toArray());
        assertArrayEquals(new Object[] { testData.existingRelation }, added.toArray());
    }

    /**
     * Tests {@link SequenceCommand#getParticipatingPrimitives()}
     * @since xxx
     */
    @Test
    public void testGetParticipatingPrimitives() {
        Command command1 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode));
        Command command2 = new TestCommand(Arrays.<OsmPrimitive> asList(testData.existingNode2));

        SequenceCommand command = new SequenceCommand("seq", command1, command2);
        command.executeCommand();
        Collection<? extends OsmPrimitive> primitives = command.getParticipatingPrimitives();
        assertEquals(2, primitives.size());
        assertTrue(primitives.contains(testData.existingNode));
        assertTrue(primitives.contains(testData.existingNode2));
    }

    /**
     * Test {@link SequenceCommand#getDescriptionText()}
     * @since xxx
     */
    @Test
    public void testDescription() {
        assertTrue(new SequenceCommand("test").getDescriptionText().matches("Sequence: test"));
    }

    /**
     * Unit test of methods {@link SequenceCommand#equals} and {@link SequenceCommand#hashCode}.
     */
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SequenceCommand.class).usingGetClass()
            .withPrefabValues(Command.class,
                new AddCommand(new Node(1)), new AddCommand(new Node(2)))
            .withPrefabValues(DataSet.class,
                    new DataSet(), new DataSet())
            .withPrefabValues(User.class,
                    User.createOsmUser(1, "foo"), User.createOsmUser(2, "bar"))
            .withPrefabValues(OsmDataLayer.class,
                    new OsmDataLayer(new DataSet(), "1", null), new OsmDataLayer(new DataSet(), "2", null))
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }

    private static class TestCommand extends Command {
        protected final Collection<? extends OsmPrimitive> primitives;
        protected boolean executed;

        TestCommand(Collection<? extends OsmPrimitive> primitives) {
            super();
            this.primitives = primitives;
        }

        @Override
        public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                Collection<OsmPrimitive> added) {
            modified.addAll(primitives);
        }

        @Override
        public String getDescriptionText() {
            fail("Should not be called");
            return "";
        }

        @Override
        public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
            return primitives;
        }

        @Override
        public boolean executeCommand() {
            assertFalse("Cannot execute twice", executed);
            executed = true;
            return true;
        }

        @Override
        public void undoCommand() {
            assertTrue("Cannot undo without execute", executed);
            executed = false;
        }

        @Override
        public String toString() {
            return "TestCommand [primitives=" + primitives + "]";
        }

    }
}
