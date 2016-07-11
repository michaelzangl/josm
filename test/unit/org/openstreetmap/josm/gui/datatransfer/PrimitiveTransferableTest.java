// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.NodeData;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.gui.datatransfer.data.TagTransferData;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link PrimitiveTransferable} class.
 */
public class PrimitiveTransferableTest {
    /**
     * Prefs to use OSM primitives
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences();

    /**
     * Test of {@link PrimitiveTransferable#getTransferDataFlavors()} method response order
     */
    @Test
    public void testGetTransferDataFlavors() {
        List<DataFlavor> flavors = Arrays.asList(new PrimitiveTransferable(null).getTransferDataFlavors());
        int ptd = flavors.indexOf(PrimitiveTransferData.DATA_FLAVOR);
        int tags = flavors.indexOf(TagTransferData.FLAVOR);
        int string = flavors.indexOf(DataFlavor.stringFlavor);

        assertTrue(ptd >= 0);
        assertTrue(tags >= 0);
        assertTrue(string >= 0);

        assertTrue(ptd < tags);
        assertTrue(tags < string);
    }

    /**
     * Test of {@link PrimitiveTransferable#isDataFlavorSupported} method.
     */
    @Test
    public void testIsDataFlavorSupported() {
        assertTrue(new PrimitiveTransferable(null).isDataFlavorSupported(PrimitiveTransferData.DATA_FLAVOR));
        assertFalse(new PrimitiveTransferable(null).isDataFlavorSupported(DataFlavor.allHtmlFlavor));
    }

    /**
     * Test of {@link PrimitiveTransferable#getTransferData} method - nominal case.
     * @throws UnsupportedFlavorException never
     */
    @Test
    public void testGetTransferDataNominal() throws UnsupportedFlavorException {
        PrimitiveTransferData data = PrimitiveTransferData.getData(Collections.singleton(new Node(1)));
        PrimitiveTransferable pt = new PrimitiveTransferable(data);
        assertEquals("node 1", pt.getTransferData(DataFlavor.stringFlavor));
        Collection<PrimitiveData> td = ((PrimitiveTransferData) pt.getTransferData(PrimitiveTransferData.DATA_FLAVOR)).getAll();
        assertEquals(1, td.size());
        assertTrue(td.iterator().next() instanceof NodeData);


        data = PrimitiveTransferData.getData(Arrays.asList(new Node(1), new Node(2)));
        pt = new PrimitiveTransferable(data);
        assertEquals("node 1\nnode 2", pt.getTransferData(DataFlavor.stringFlavor));
    }

    /**
     * Test of {@link PrimitiveTransferable#getTransferData} method - error case.
     * @throws UnsupportedFlavorException always
     */
    @Test(expected = UnsupportedFlavorException.class)
    public void testGetTransferDataError() throws UnsupportedFlavorException {
        PrimitiveTransferData data = PrimitiveTransferData.getData(Collections.singleton(new Node(1)));
        new PrimitiveTransferable(data).getTransferData(DataFlavor.allHtmlFlavor);
    }
}
