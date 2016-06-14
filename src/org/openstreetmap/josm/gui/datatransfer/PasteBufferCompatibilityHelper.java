// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer;

import org.openstreetmap.josm.data.osm.PrimitiveDeepCopy;
import org.openstreetmap.josm.data.osm.PrimitiveDeepCopy.PasteBufferChangedListener;

/**
 * Temporary helper class that allows the paste buffer and Swing CCP to coexist.
 * @author Michael Zangl
 * @since xxx
 */
public class PasteBufferCompatibilityHelper implements PasteBufferChangedListener {
    private OsmTransferHandler transferHandler = new OsmTransferHandler();
    @Override
    public void pasteBufferChanged(PrimitiveDeepCopy pasteBuffer) {
        PrimitiveTransferData data = new PrimitiveTransferData(pasteBuffer);
        OsmTransferHandler.copy(data);
    }
}
