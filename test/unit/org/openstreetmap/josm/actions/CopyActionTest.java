// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferData;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests for class {@link CopyAction}.
 */
public class CopyActionTest {
    private final class CapturingCopyAction extends CopyAction {
        private boolean warningShown;

        @Override
        protected void showEmptySelectionWarning() {
            warningShown = true;
        }
    }

    /**
     * We need prefs for this.
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().platform().fakeAPI();

    /**
     * Test that copy action copies the selected primitive
     * @throws IOException
     * @throws UnsupportedFlavorException
     */
    @Test
    public void testWarnOnEmpty() throws UnsupportedFlavorException, IOException {
        Clipboard clippboard = OsmTransferHandler.getClippboard();
        clippboard.setContents(new StringSelection("test"), null);

        CapturingCopyAction action = new CapturingCopyAction();

        action.updateEnabledState();
        assertFalse(action.isEnabled());
        action.actionPerformed(null);
        assertTrue(action.warningShown);

        Main.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "test", null));
        action.warningShown = false;

        action.updateEnabledState();
        assertFalse(action.isEnabled());
        action.actionPerformed(null);
        assertTrue(action.warningShown);

        assertEquals("test", clippboard.getContents(null).getTransferData(DataFlavor.stringFlavor));
    }
    /**
     * Test that copy action copies the selected primitive
     * @throws IOException
     * @throws UnsupportedFlavorException
     */
    @Test
    public void testCopySinglePrimitive() throws UnsupportedFlavorException, IOException {
        DataSet data = new DataSet();

        Node node1 = new Node();
        node1.setCoor(LatLon.ZERO);
        data.addPrimitive(node1);

        Node node2 = new Node();
        node2.setCoor(LatLon.ZERO);
        data.addPrimitive(node2);
        Way way = new Way();
        way.setNodes(Arrays.asList(node1, node2));
        data.addPrimitive(way);
        data.setSelected(way);

        Main.getLayerManager().addLayer(new OsmDataLayer(data, "test", null));

        CopyAction action = new CopyAction() {
            @Override
            protected void showEmptySelectionWarning() {
                fail("Selection is not empty.");
            }
        };
        action.updateEnabledState();
        assertTrue(action.isEnabled());
        action.actionPerformed(null);

        Object copied = OsmTransferHandler.getClippboard().getContents(null).getTransferData(PrimitiveTransferData.DATA_FLAVOR);
        assertNotNull(copied);
        assertTrue(copied instanceof PrimitiveTransferData);
        PrimitiveTransferData ptd = (PrimitiveTransferData) copied;
        Object[] direct = ptd.getDirectlyAdded().toArray();
        assertEquals(1, direct.length);
        Object[] referenced = ptd.getReferenced().toArray();
        assertEquals(2, referenced.length);
    }
}
