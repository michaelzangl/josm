// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferable;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;

/**
 * This class allows to create and keep a deep copy of primitives. Provides methods to access directly added
 * primitives and reference primitives
 * <p>
 * To be removed end of 2016
 * @since 2305
 * @deprecated This has been replaced by Swing Copy+Paste support. Use {@link OsmTransferHandler} instead.
 */
@Deprecated
public class PrimitiveDeepCopy {

    public interface PasteBufferChangedListener {
        void pasteBufferChanged(PrimitiveDeepCopy pasteBuffer);
    }

    /**
     * Constructs a new {@code PrimitiveDeepCopy} without data. Use {@link #makeCopy(Collection)} after that.
     */
    public PrimitiveDeepCopy() {
    }

    /**
     * Constructs a new {@code PrimitiveDeepCopy} of given OSM primitives.
     * @param primitives OSM primitives to copy
     * @since 7961
     */
    public PrimitiveDeepCopy(final Collection<? extends OsmPrimitive> primitives) {
        makeCopy(primitives);
    }

    /**
     * Replace content of the object with copy of provided primitives.
     * @param primitives OSM primitives to copy
     * @since 7961
     * @deprecated Call {@link OsmTransferHandler#copyToClippboard(PrimitiveTransferData)} yourself
     */
    @Deprecated
    public final void makeCopy(final Collection<? extends OsmPrimitive> primitives) {
        OsmTransferHandler.getClippboard().setContents(new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(primitives)), null);
    }

    /**
     * Gets the list of primitives that were explicitly added to this copy.
     * @return The added primitives
     */
    public List<PrimitiveData> getDirectlyAdded() {
        try {
            PrimitiveTransferData data = (PrimitiveTransferData) OsmTransferHandler.getClippboard().getData(PrimitiveTransferData.DATA_FLAVOR);
            return new ArrayList<>(data.getDirectlyAdded());
        } catch (UnsupportedFlavorException | IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gets the list of primitives that were implicitly added because they were referenced.
     * @return The primitives
     */
    public List<PrimitiveData> getReferenced() {
        try {
            PrimitiveTransferData data = (PrimitiveTransferData) OsmTransferHandler.getClippboard().getData(PrimitiveTransferData.DATA_FLAVOR);
            return new ArrayList<>(data.getReferenced());
        } catch (UnsupportedFlavorException | IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gets a list of all primitives in this copy.
     * @return The primitives
     * @see #getDirectlyAdded()
     * @see #getReferenced()
     */
    public List<PrimitiveData> getAll() {
        try {
            PrimitiveTransferData data = (PrimitiveTransferData) OsmTransferHandler.getClippboard().getData(PrimitiveTransferData.DATA_FLAVOR);
            return new ArrayList<>(data.getAll());
        } catch (UnsupportedFlavorException | IOException e) {
            return Collections.emptyList();
        }
    }

    public boolean isEmpty() {
        return !OsmTransferHandler.getClippboard().isDataFlavorAvailable(PrimitiveTransferData.DATA_FLAVOR);
    }

    /**
     * Deactivated. To be removed as soon as we think nobody uses it.
     * @param listener
     * @deprecated You can detect buffer changes by registering a listener on {@link OsmTransferHandler#getClippboard()}
     */
    @Deprecated
    public void addPasteBufferChangedListener(PasteBufferChangedListener listener) {
    }

    @Deprecated
    public void removePasteBufferChangedListener(PasteBufferChangedListener listener) {
    }
}
