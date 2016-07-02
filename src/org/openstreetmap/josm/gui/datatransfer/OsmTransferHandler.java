// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.TransferHandler;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.datatransfer.importers.AbstractDataFlavorSupport;
import org.openstreetmap.josm.gui.datatransfer.importers.FileSupport;
import org.openstreetmap.josm.gui.datatransfer.importers.PrimitiveDataSupport;
import org.openstreetmap.josm.gui.datatransfer.importers.TagTransferSupport;
import org.openstreetmap.josm.gui.datatransfer.importers.TextTagSupport;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * This transfer hanlder provides the ability to transfer OSM data. It allows you to receive files, primitives or tags.
 * @author Michael Zangl
 */
public class OsmTransferHandler extends TransferHandler {

    private static final Collection<AbstractDataFlavorSupport> SUPPORTED = Arrays.asList(
            new FileSupport(), new PrimitiveDataSupport(),
            new TagTransferSupport(), new TextTagSupport());
    private static Clipboard clippboard;

    @Override
    public boolean canImport(TransferSupport support) {
        // import everything for now, only support copy.
        for (AbstractDataFlavorSupport df : SUPPORTED) {
            if (df.supports(support)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
        return importData(support, Main.getLayerManager().getEditLayer(), null);
    }

    private boolean importData(TransferSupport support, OsmDataLayer layer, EastNorth center) {
        for (AbstractDataFlavorSupport df : SUPPORTED) {
            if (df.supports(support)) {
                try {
                    if (df.importData(support, layer, center)) {
                        return true;
                    }
                } catch (UnsupportedFlavorException | IOException e) {
                    Main.warn(e);
                }
            }
        }
        return super.importData(support);
    }

    private boolean importTags(TransferSupport support, Collection<? extends OsmPrimitive> primitives) {
        for (AbstractDataFlavorSupport df : SUPPORTED) {
            if (df.supports(support)) {
                try {
                    if (df.importTagsOn(support, primitives)) {
                        return true;
                    }
                } catch (UnsupportedFlavorException | IOException e) {
                    Main.warn(e);
                }
            }
        }
        return super.importData(support);
    }

    /**
     * Paste the current clippboard current at the given position
     * @param editLayer The layer to paste on.
     * @param mPosition The position to paste at.
     */
    public void pasteOn(OsmDataLayer editLayer, EastNorth mPosition) {
        Transferable transferable = getClippboard().getContents(null);
        pasteOn(editLayer, mPosition, transferable);
    }

    /**
     * Paste the given clippboard current at the given position
     * @param editLayer The layer to paste on.
     * @param mPosition The position to paste at.
     * @param transferable The transferable to use.
     */
    public void pasteOn(OsmDataLayer editLayer, EastNorth mPosition, Transferable transferable) {
        importData(new TransferSupport(Main.panel, transferable), editLayer, mPosition);
    }

    /**
     * Paste the given tags on the primitives.
     * @param primitives The primitives to paste on.
     */
    public void pasteTags(Collection<? extends OsmPrimitive> primitives) {
        Transferable transferable = getClippboard().getContents(null);
        importTags(new TransferSupport(Main.panel, transferable), primitives);
    }

    /**
     * Check if any primitive data or any other supported data is available in the clippboard.
     * @return <code>true</code> if any flavor is supported.
     */
    public boolean isDataAvailable() {
        Collection<DataFlavor> available = Arrays.asList(getClippboard().getAvailableDataFlavors());
        for (AbstractDataFlavorSupport s : SUPPORTED) {
            if (s.supports(available)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Invoke a copy for the given data.
     * @param data The data to copy.
     */
    public static void copyToClippboard(PrimitiveTransferData data) {
        getClippboard().setContents(new PrimitiveTransferable(data), null);
    }

    /**
     * This method should be used from all of JOSM to access the clippboard.
     * <p>
     * It will default to the system clippboard except for cases where that clippboard is not accessible.
     * @return A clippboard.
     */
    public static synchronized Clipboard getClippboard() {
        // Might be unsupported in some more cases, we need a fake clippboard then.
        if (clippboard == null) {
            try {
                clippboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            } catch (HeadlessException e) {
                Main.warn("Headless. Using fake clippboard.", e);
                clippboard = new Clipboard("fake");
            }
        }
        return clippboard;
    }
}
