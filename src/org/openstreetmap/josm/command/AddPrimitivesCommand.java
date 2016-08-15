// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.command;

import static org.openstreetmap.josm.tools.I18n.trn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.swing.Icon;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.NodeData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * Add primitives to a data layer.
 * @since 2305
 */
public class AddPrimitivesCommand extends Command {

    private List<PrimitiveData> data = new ArrayList<>();
    private Collection<PrimitiveData> toSelect = new ArrayList<>();

    // only filled on undo
    private List<OsmPrimitive> createdPrimitives;
    private Collection<OsmPrimitive> createdPrimitivesToSelect;

    /**
     * Constructs a new {@code AddPrimitivesCommand} to add data to the current edit layer.
     * @param data The OSM primitives data to add. Must not be {@code null}
     */
    public AddPrimitivesCommand(List<PrimitiveData> data) {
        this(data, data);
    }

    /**
     * Constructs a new {@code AddPrimitivesCommand} to add data to the current edit layer.
     * @param data The OSM primitives to add. Must not be {@code null}
     * @param toSelect The OSM primitives to select at the end. Can be {@code null}
     * @since 5953
     */
    public AddPrimitivesCommand(List<PrimitiveData> data, List<PrimitiveData> toSelect) {
        init(data, toSelect);
    }

    /**
     * Constructs a new {@code AddPrimitivesCommand} to add data to the given layer.
     * @param data The OSM primitives data to add. Must not be {@code null}
     * @param toSelect The OSM primitives to select at the end. Can be {@code null}
     * @param layer The target data layer. Must not be {@code null}
     */
    public AddPrimitivesCommand(List<PrimitiveData> data, List<PrimitiveData> toSelect, OsmDataLayer layer) {
        super(layer);
        init(data, toSelect);
    }

    private void init(List<PrimitiveData> data, List<PrimitiveData> toSelect) {
        CheckParameterUtil.ensureParameterNotNull(data, "data");
        this.data.addAll(data);
        if (toSelect != null) {
            this.toSelect.addAll(toSelect);
        }
    }

    @Override
    public boolean executeCommand() {
        Collection<OsmPrimitive> primitivesToSelect;
        if (createdPrimitives == null) { // first time execution
            List<OsmPrimitive> newPrimitives = new ArrayList<>(data.size());
            primitivesToSelect = new ArrayList<>(toSelect.size());

            for (PrimitiveData pd : data) {
                OsmPrimitive primitive = getAffectedDataSet().getPrimitiveById(pd);
                boolean created = primitive == null;
                if (created) {
                    primitive = pd.getType().newInstance(pd.getUniqueId(), true);
                }
                if (pd instanceof NodeData) { // Load nodes immediately because they can't be added to dataset without coordinates
                    primitive.load(pd);
                }
                if (created) {
                    getAffectedDataSet().addPrimitive(primitive);
                }
                newPrimitives.add(primitive);
                primitive.setModified(true);
                if (toSelect.contains(pd)) {
                    primitivesToSelect.add(primitive);
                }
            }

            // Then load ways and relations
            for (int i = 0; i < newPrimitives.size(); i++) {
                if (!(newPrimitives.get(i) instanceof Node)) {
                    newPrimitives.get(i).load(data.get(i));
                }
            }
        } else { // redo
            // When redoing this command, we have to add the same objects, otherwise
            // a subsequent command (e.g. MoveCommand) cannot be redone.
            for (OsmPrimitive osm : createdPrimitives) {
                getLayer().data.addPrimitive(osm);
            }
            primitivesToSelect = createdPrimitivesToSelect;
        }

        getLayer().data.setSelected(primitivesToSelect);
        return true;
    }

    @Override public void undoCommand() {
        DataSet ds = getAffectedDataSet();

        if (createdPrimitives == null) {
            createdPrimitives = new ArrayList<>(data.size());
            createdPrimitivesToSelect = new ArrayList<>(toSelect.size());

            for (PrimitiveData pd : data) {
                OsmPrimitive p = ds.getPrimitiveById(pd);
                createdPrimitives.add(p);
                if (toSelect.contains(pd)) {
                    createdPrimitivesToSelect.add(p);
                }
            }
            createdPrimitives = PurgeCommand.topoSort(createdPrimitives);

            for (PrimitiveData p : data) {
                ds.removePrimitive(p);
            }
            data = null;
            toSelect = null;

        } else {
            for (OsmPrimitive osm : createdPrimitives) {
                ds.removePrimitive(osm);
            }
        }
    }

    @Override
    public String getDescriptionText() {
        int size = data != null ? data.size() : createdPrimitives.size();
        return trn("Added {0} object", "Added {0} objects", size, size);
    }

    @Override
    public Icon getDescriptionIcon() {
        return null;
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        // Does nothing because we don't want to create OsmPrimitives.
    }

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        if (createdPrimitives != null)
            return createdPrimitives;

        Collection<OsmPrimitive> prims = new HashSet<>();
        for (PrimitiveData d : data) {
            OsmPrimitive osm = getAffectedDataSet().getPrimitiveById(d);
            if (osm == null)
                throw new RuntimeException();
            prims.add(osm);
        }
        return prims;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data, toSelect, createdPrimitives, createdPrimitivesToSelect);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        AddPrimitivesCommand that = (AddPrimitivesCommand) obj;
        return Objects.equals(data, that.data) &&
                Objects.equals(toSelect, that.toSelect) &&
                Objects.equals(createdPrimitives, that.createdPrimitives) &&
                Objects.equals(createdPrimitivesToSelect, that.createdPrimitivesToSelect);
    }
}
