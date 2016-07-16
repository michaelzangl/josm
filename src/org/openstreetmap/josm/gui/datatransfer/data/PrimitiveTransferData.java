// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.datatransfer.data;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.NodeData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;

/**
 * A list of primitives that are transfered. The list allows you to implicitly add primitives.
 * It distingluishes between primitives that were directly added and implicitly added ones.
 * @author Michael Zangl
 * @since xxx
 */
public final class PrimitiveTransferData implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The data flavor used to represent this class.
     */
    public static final DataFlavor DATA_FLAVOR = new DataFlavor(PrimitiveTransferData.class, "OSM Primitives");

    private static final class GetReferences implements ReferenceGetter {
        @Override
        public Collection<? extends OsmPrimitive> getReferedPrimitives(OsmPrimitive primitive) {
            if (primitive instanceof Way) {
                return ((Way) primitive).getNodes();
            } else if (primitive instanceof Relation) {
                return ((Relation) primitive).getMemberPrimitives();
            } else {
                return Collections.emptyList();
            }
        }
    }

    // TODO: Java8: Replace by Function.
    private interface ReferenceGetter {
        Collection<? extends OsmPrimitive> getReferedPrimitives(OsmPrimitive primitive);
    }

    private final ArrayList<PrimitiveData> direct;
    private final ArrayList<PrimitiveData> referenced;

    /**
     * Create the new transfer data.
     * @param primitives The primitives to transfer
     * @param referencedGetter A function that allows to get the primitives referenced by the primitives variable.
     * It will be queried recursively.
     */
    private PrimitiveTransferData(Collection<? extends OsmPrimitive> primitives, ReferenceGetter referencedGetter) {
        // convert to hash set first to remove dupplicates
        HashSet<OsmPrimitive> visited = new HashSet<OsmPrimitive>(primitives);
        this.direct = new ArrayList<>(visited.size());

        this.referenced = new ArrayList<>();
        Queue<OsmPrimitive> toCheck = new LinkedList<>();
        for (OsmPrimitive p : visited) {
            direct.add(p.save());
            toCheck.addAll(referencedGetter.getReferedPrimitives(p));
        }
        while (!toCheck.isEmpty()) {
            OsmPrimitive p = toCheck.poll();
            if (visited.add(p)) {
                referenced.add(p.save());
                toCheck.addAll(referencedGetter.getReferedPrimitives(p));
            }
        }
    }

    /**
     * Gets all primitives directly added.
     * @return The primitives
     */
    public Collection<PrimitiveData> getDirectlyAdded() {
        return Collections.unmodifiableList(direct);
    }

    /**
     * Gets all primitives that were added because they were referenced.
     * @return The primitives
     */
    public Collection<PrimitiveData> getReferenced() {
        return Collections.unmodifiableList(referenced);
    }

    /**
     * Gets a List of all primitives added to this set.
     * @return That list.
     */
    public Collection<PrimitiveData> getAll() {
        ArrayList<PrimitiveData> list = new ArrayList<>();
        list.addAll(direct);
        list.addAll(referenced);
        return list;
    }

    /**
     * Creates a new {@link PrimitiveTransferData} object that only contains the primitives.
     * @param primitives The primitives to contain.
     * @return That set.
     */
    public static PrimitiveTransferData getData(Collection<? extends OsmPrimitive> primitives) {
        return new PrimitiveTransferData(primitives, new ReferenceGetter() {
            @Override
            public Collection<? extends OsmPrimitive> getReferedPrimitives(OsmPrimitive primitive) {
                return Collections.emptyList();
            }
        });
    }


    /**
     * Creates a new {@link PrimitiveTransferData} object that contains the primitives and all references.
     * @param primitives The primitives to contain.
     * @return That set.
     */
    public static PrimitiveTransferData getDataWithReferences(Collection<? extends OsmPrimitive> primitives) {
        return new PrimitiveTransferData(primitives, new GetReferences());
    }

    /**
     * Compute the center of all nodes.
     * @return The center or null if this buffer has no location.
     */
    public EastNorth getCenter() {
        BoundingXYVisitor visitor = new BoundingXYVisitor();
        for (PrimitiveData pd : getAll()) {
            if (pd instanceof NodeData && !pd.isIncomplete()) {
                visitor.visit(((NodeData) pd).getEastNorth());
            }
        }
        ProjectionBounds bounds = visitor.getBounds();
        if (bounds == null) {
            return null;
        } else {
            return bounds.getCenter();
        }
    }

    /**
     * Tests wheter this set contains any primitives that have invalid data.
     * @return <code>true</code> if invalid data is contained in this set.
     */
    public boolean hasIncompleteData() {
        for (PrimitiveData pd : getAll()) {
            if (pd.isIncomplete()) {
                return true;
            }
        }
        return false;
    }
}
