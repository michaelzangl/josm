// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * This is a listener that listens to selection change events in the data set.
 * @author Michael Zangl
 * @since xxx
 */
@FunctionalInterface
public interface DataSelectionListener {

    /**
     * Called whenever the selection is changed.
     * @param e The selection change event.
     */
    void selectionChanged(SelectionChangeEvent e);

    /**
     * The event that is fired when the selection changed.
     * @author Michael Zangl
     * @since xxx
     */
    public static interface SelectionChangeEvent {
        /**
         * Gets the previous selection
         * <p>
         * This collection cannot be modified and will not change.
         * @return The old selection
         */
        public Set<OsmPrimitive> getOldSelection();

        /**
         * Gets the new selection
         * <p>
         * This collection cannot be modified and will not change.
         * @return The new selection
         */
        public Set<OsmPrimitive> getSelection();

        /**
         * Gets the primitives that have been removed from the selection.
         * <p>
         * Those are the primitives contained in {@link #getOldSelection()} but not in {@link #getSelection()}
         * <p>
         * This collection cannot be modified and will not change.
         * @return The primitives
         */
        public Set<OsmPrimitive> getRemoved();

        /**
         * Gets the primitives that have been added to the selection.
         * <p>
         * Those are the primitives contained in {@link #getSelection()} but not in {@link #getOldSelection()}
         * <p>
         * This collection cannot be modified and will not change.
         * @return The primitives
         */
        public Set<OsmPrimitive> getAdded();

        /**
         * Gets the data set that triggered this selection event.
         * @return The data set.
         */
        public DataSet getSource();

        /**
         * Test if this event did not change anything.
         * <p>
         * Should return true for all events that are fired.
         * @return <code>true</code> if this did not change the selection.
         */
        default boolean isNop() {
            return getAdded().isEmpty() && getRemoved().isEmpty();
        }
    }

    /**
     * The base class for selection events
     * @author Michael Zangl
     * @since xxx
     */
    abstract static class AbstractSelectionEvent implements SelectionChangeEvent {
        private final DataSet source;
        private final Set<OsmPrimitive> old;

        public AbstractSelectionEvent(DataSet source, Set<OsmPrimitive> old) {
            CheckParameterUtil.ensureParameterNotNull(source, "source");
            CheckParameterUtil.ensureParameterNotNull(old, "old");
            this.source = source;
            this.old = Collections.unmodifiableSet(old);
        }

        @Override
        public Set<OsmPrimitive> getOldSelection() {
            return old;
        }

        @Override
        public DataSet getSource() {
            return source;
        }
    }


    /**
     * The selection is replaced by a new selection
     * @author Michael Zangl
     * @since xxx
     */
    public static class SelectionReplaceEvent extends AbstractSelectionEvent {
        private final Set<OsmPrimitive> current;
        private Set<OsmPrimitive> removed;
        private Set<OsmPrimitive> added;

        /**
         * Create a {@link SelectionReplaceEvent}
         * @param source The source dataset
         * @param old The old primitves that were previously selected. The caller needs to ensure that this set is not modifed.
         * @param newSelection The primitives of the new selection.
         */
        public SelectionReplaceEvent(DataSet source, Set<OsmPrimitive> old, Stream<OsmPrimitive> newSelection) {
            super(source, old);
            this.current = newSelection.collect(Collectors.toSet());

        }

        @Override
        public Set<OsmPrimitive> getSelection() {
            return current;
        }

        @Override
        public synchronized Set<OsmPrimitive> getRemoved() {
            if (removed == null) {
                removed = getOldSelection().stream().filter(p -> !current.contains(p)).collect(Collectors.toSet());
            }
            return removed;
        }

        @Override
        public synchronized Set<OsmPrimitive> getAdded() {
            if (added == null) {
                added = current.stream().filter(p -> !getOldSelection().contains(p)).collect(Collectors.toSet());
            }
            return added;
        }
    }

    /**
     * Primitives are added to the selection
     * @author Michael Zangl
     * @since xxx
     */
    public static class SelectionAddEvent extends AbstractSelectionEvent {
        private final Set<OsmPrimitive> add;
        private final Set<OsmPrimitive> current;

        /**
         * Create a {@link SelectionAddEvent}
         * @param source The source dataset
         * @param old The old primitves that were previously selected. The caller needs to ensure that this set is not modifed.
         * @param toAdd The primitives to add.
         */
        public SelectionAddEvent(DataSet source, Set<OsmPrimitive> old, Stream<OsmPrimitive> toAdd) {
            super(source, old);
            this.add = toAdd.filter(p -> !old.contains(p)).collect(Collectors.toSet());
            if (this.add.isEmpty()) {
                this.current = this.getOldSelection();
            } else {
                this.current = new HashSet<>(old);
                this.current.addAll(add);
            }
        }

        @Override
        public Set<OsmPrimitive> getSelection() {
            return current;
        }

        @Override
        public Set<OsmPrimitive> getRemoved() {
            return Collections.emptySet();
        }

        @Override
        public Set<OsmPrimitive> getAdded() {
            return add;
        }
    }

    /**
     * Primitives are removed from the selection
     * @author Michael Zangl
     * @since xxx
     */
    public static class SelectionRemoveEvent extends AbstractSelectionEvent {
        private final Set<OsmPrimitive> remove;
        private final Set<OsmPrimitive> current;

        /**
         * Create a {@link SelectionRemoveEvent}
         * @param source The source dataset
         * @param old The old primitves that were previously selected. The caller needs to ensure that this set is not modifed.
         * @param toRemove The primitives to remove.
         */
        public SelectionRemoveEvent(DataSet source, Set<OsmPrimitive> old, Stream<OsmPrimitive> toRemove) {
            super(source, old);
            this.remove = toRemove.filter(old::contains).collect(Collectors.toSet());
            if (this.remove.isEmpty()) {
                this.current = this.getOldSelection();
            } else {
                HashSet<OsmPrimitive> currentSet = new HashSet<>(old);
                currentSet.removeAll(remove);
                current = Collections.unmodifiableSet(currentSet);
            }
        }

        @Override
        public Set<OsmPrimitive> getSelection() {
            return current;
        }

        @Override
        public Set<OsmPrimitive> getRemoved() {
            return remove;
        }

        @Override
        public Set<OsmPrimitive> getAdded() {
            return Collections.emptySet();
        }
    }

    /**
     * Toggle the selected state of a primitive
     * @author Michael Zangl
     * @since xxx
     */
    public static class SelectionToggleEvent extends AbstractSelectionEvent {
        private final Set<OsmPrimitive> current;
        private final Set<OsmPrimitive> remove;
        private final Set<OsmPrimitive> add;

        /**
         * Create a {@link SelectionToggleEvent}
         * @param source The source dataset
         * @param old The old primitves that were previously selected. The caller needs to ensure that this set is not modifed.
         * @param toToggle The primitives to toggle.
         */
        public SelectionToggleEvent(DataSet source, Set<OsmPrimitive> old, Stream<OsmPrimitive> toToggle) {
            super(source, old);
            HashSet<OsmPrimitive> currentSet = new HashSet<>(old);
            HashSet<OsmPrimitive> removeSet = new HashSet<>();
            HashSet<OsmPrimitive> addSet = new HashSet<>();
            toToggle.forEach(p -> {
                if (currentSet.remove(p)) {
                    removeSet.add(p);
                } else {
                    addSet.add(p);
                    currentSet.add(p);
                }
            });
            this.current = Collections.unmodifiableSet(currentSet);
            this.remove = Collections.unmodifiableSet(removeSet);
            this.add = Collections.unmodifiableSet(addSet);
        }

        @Override
        public Set<OsmPrimitive> getSelection() {
            return current;
        }

        @Override
        public Set<OsmPrimitive> getRemoved() {
            return remove;
        }

        @Override
        public Set<OsmPrimitive> getAdded() {
            return add;
        }
    }
}
