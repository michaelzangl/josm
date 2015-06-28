// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.navigate;

import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.data.SystemOfMeasurement;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;

/**
 * Josm uses one globally set system of measurement.
 *
 * @author Michael Zangl
 *
 */
public class GlobalSoM {

    /**
     * Interface to notify listeners of the change of the system of measurement.
     * @since 6056
     */
    public interface SoMChangeListener {
        /**
         * The current SoM has changed.
         * @param oldSoM The old system of measurement
         * @param newSoM The new (current) system of measurement
         */
        void systemOfMeasurementChanged(String oldSoM, String newSoM);
    }

    private static final CopyOnWriteArrayList<SoMChangeListener> somChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * Removes a SoM change listener
     *
     * @param listener the listener. Ignored if null or already absent
     * @since 6056
     */
    public static void removeSoMChangeListener(NavigatableComponent.SoMChangeListener listener) {
        somChangeListeners.remove(listener);
    }

    /**
     * Adds a SoM change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @since 6056
     */
    public static void addSoMChangeListener(NavigatableComponent.SoMChangeListener listener) {
        if (listener != null) {
            somChangeListeners.addIfAbsent(listener);
        }
    }

    protected static void fireSoMChanged(String oldSoM, String newSoM) {
        for (SoMChangeListener l : somChangeListeners) {
            l.systemOfMeasurementChanged(oldSoM, newSoM);
        }
    }

    /**
     * Returns the current system of measurement.
     * @return The current system of measurement (metric system by default).
     * @since 3490
     */
    public static SystemOfMeasurement getSystemOfMeasurement() {
        SystemOfMeasurement som = SystemOfMeasurement.ALL_SYSTEMS.get(ProjectionPreference.PROP_SYSTEM_OF_MEASUREMENT.get());
        if (som == null)
            return SystemOfMeasurement.METRIC;
        return som;
    }

    /**
     * Sets the current system of measurement.
     * @param somKey The system of measurement key. Must be defined in {@link SystemOfMeasurement#ALL_SYSTEMS}.
     * @throws IllegalArgumentException if {@code somKey} is not known
     * @since 6056
     */
    public static void setSystemOfMeasurement(String somKey) {
        if (!SystemOfMeasurement.ALL_SYSTEMS.containsKey(somKey)) {
            throw new IllegalArgumentException("Invalid system of measurement: "+somKey);
        }
        String oldKey = ProjectionPreference.PROP_SYSTEM_OF_MEASUREMENT.get();
        if (ProjectionPreference.PROP_SYSTEM_OF_MEASUREMENT.put(somKey)) {
            fireSoMChanged(oldKey, somKey);
        }
    }
}
