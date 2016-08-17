// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * This are the preferences of how to display a {@link TileSource}.
 * <p>
 * They have been extracted from the {@link AbstractTileSourceLayer}. Each layer has one set of such settings.
 * @author michael
 * @since 10568
 */
public class TileSourceDisplaySettings {
    /**
     * A string returned by {@link DisplaySettingsChangeEvent#getChangedSetting()} if auto load was changed.
     * @see TileSourceDisplaySettings#isAutoLoad()
     */
    public static final String AUTO_LOAD = "automatic-downloading";

    /**
     * A string returned by {@link DisplaySettingsChangeEvent#getChangedSetting()} if auto zoom was changed.
     * @see TileSourceDisplaySettings#isAutoZoom()
     */
    public static final String AUTO_ZOOM = "automatically-change-resolution";

    /**
     * A string returned by {@link DisplaySettingsChangeEvent#getChangedSetting()} if the sow errors property was changed.
     * @see TileSourceDisplaySettings#isShowErrors()
     */
    private static final String SHOW_ERRORS = "show-errors";

    private static final String DISPLACEMENT = "displacement";

    private static final String PREFERENCE_PREFIX = "imagery.generic";

    /**
     * The default auto load property
     */
    public static final BooleanProperty PROP_AUTO_LOAD = new BooleanProperty(PREFERENCE_PREFIX + ".default_autoload", true);

    /**
     * The default auto zoom property
     */
    public static final BooleanProperty PROP_AUTO_ZOOM = new BooleanProperty(PREFERENCE_PREFIX + ".default_autozoom", true);


    /** if layers changes automatically, when user zooms in */
    private boolean autoZoom;
    /** if layer automatically loads new tiles */
    private boolean autoLoad;
    /** if layer should show errors on tiles */
    private boolean showErrors;

    /**
     * The displacement
     */
    private EastNorth displacement = new EastNorth(0, 0);

    private final CopyOnWriteArrayList<DisplaySettingsChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Create a new {@link TileSourceDisplaySettings}
     */
    public TileSourceDisplaySettings() {
        this(new String[] {PREFERENCE_PREFIX});
    }

    /**
     * Create a new {@link TileSourceDisplaySettings}
     * @param preferencePrefix The additional prefix to scan for preferences.
     */
    public TileSourceDisplaySettings(String preferencePrefix) {
        this(PREFERENCE_PREFIX, preferencePrefix);
    }

    private TileSourceDisplaySettings(String... prefixes) {
        autoZoom = getProperty(prefixes, "default_autozoom");
        autoLoad = getProperty(prefixes, "default_autoload");
        showErrors = getProperty(prefixes, "default_showerrors");
    }

    private static boolean getProperty(String[] prefixes, String name) {
        // iterate through all values to force the preferences to receive the default value.
        // we only support a default value of true.
        boolean value = true;
        for (String p : prefixes) {
            String key = p + "." + name;
            boolean currentValue = Main.pref.getBoolean(key, true);
            if (!Main.pref.get(key).isEmpty()) {
                value = currentValue;
            }
        }
        return value;
    }

    /**
     * Let the layer zoom automatically if the user zooms in
     * @return auto zoom
     */
    public boolean isAutoZoom() {
        return autoZoom;
    }

    /**
     * Sets the auto zoom property
     * @param autoZoom {@code true} to let the layer zoom automatically if the user zooms in
     * @see #isAutoZoom()
     * @see #AUTO_ZOOM
     */
    public void setAutoZoom(boolean autoZoom) {
        this.autoZoom = autoZoom;
        fireSettingsChange(AUTO_ZOOM);
    }

    /**
     * Gets if the layer should automatically load new tiles.
     * @return <code>true</code> if it should
     */
    public boolean isAutoLoad() {
        return autoLoad;
    }

    /**
     * Sets the auto load property
     * @param autoLoad {@code true} if the layer should automatically load new tiles
     * @see #isAutoLoad()
     * @see #AUTO_LOAD
     */
    public void setAutoLoad(boolean autoLoad) {
        this.autoLoad = autoLoad;
        fireSettingsChange(AUTO_LOAD);
    }

    /**
     * If the layer should display the errors it encountered while loading the tiles.
     * @return <code>true</code> to show errors.
     */
    public boolean isShowErrors() {
        return showErrors;
    }

    /**
     * Sets the show errors property. Fires a change event.
     * @param showErrors {@code true} if the layer should display the errors it encountered while loading the tiles
     * @see #isShowErrors()
     * @see #SHOW_ERRORS
     */
    public void setShowErrors(boolean showErrors) {
        this.showErrors = showErrors;
        fireSettingsChange(SHOW_ERRORS);
    }

    /**
     * Gets the displacement in x (east) direction
     * @return The displacement.
     * @since 10571
     */
    public double getDx() {
        return displacement.east();
    }

    /**
     * Gets the displacement in y (north) direction
     * @return The displacement.
     * @since 10571
     */
    public double getDy() {
        return displacement.north();
    }

    /**
     * Gets the displacement of the image
     * @return The displacement.
     * @since 10571
     */
    public EastNorth getDisplacement() {
        return displacement;
    }

    /**
     * Set the displacement
     * @param displacement The new displacement
     * @since 10571
     */
    public void setDisplacement(EastNorth displacement) {
        CheckParameterUtil.ensureValidCoordinates(displacement, "displacement");
        this.displacement = displacement;
        fireSettingsChange(DISPLACEMENT);
    }

    /**
     * Adds the given value to the displacement.
     * @param displacement The value to add.
     * @since 10571
     */
    public void addDisplacement(EastNorth displacement) {
        CheckParameterUtil.ensureValidCoordinates(displacement, "displacement");
        setDisplacement(this.displacement.add(displacement));
    }

    /**
     * Notifies all listeners that the paint settings have changed
     * @param changedSetting The setting name
     */
    private void fireSettingsChange(String changedSetting) {
        DisplaySettingsChangeEvent e = new DisplaySettingsChangeEvent(this, changedSetting);
        for (DisplaySettingsChangeListener l : listeners) {
            l.displaySettingsChanged(e);
        }
    }

    /**
     * Add a listener that listens to display settings changes.
     * @param l The listener
     */
    public void addSettingsChangeListener(DisplaySettingsChangeListener l) {
        listeners.add(l);
    }

    /**
     * Remove a listener that listens to display settings changes.
     * @param l The listener
     */
    public void removeSettingsChangeListener(DisplaySettingsChangeListener l) {
        listeners.remove(l);
    }

    /**
     * Stores the current settings object to the given hashmap.
     * @param data The map to store the settings to.
     * @see #loadFrom(Map)
     */
    public void storeTo(Map<String, String> data) {
        data.put(AUTO_LOAD, Boolean.toString(autoLoad));
        data.put(AUTO_ZOOM, Boolean.toString(autoZoom));
        data.put(SHOW_ERRORS, Boolean.toString(showErrors));
        data.put("dx", String.valueOf(getDx()));
        data.put("dy", String.valueOf(getDy()));
    }

    /**
     * Load the settings from the given data instance.
     * @param data The data
     * @see #storeTo(Map)
     */
    public void loadFrom(Map<String, String> data) {
        try {
            String doAutoLoad = data.get(AUTO_LOAD);
            if (doAutoLoad != null) {
                setAutoLoad(Boolean.parseBoolean(doAutoLoad));
            }

            String doAutoZoom = data.get(AUTO_ZOOM);
            if (doAutoZoom != null) {
                setAutoZoom(Boolean.parseBoolean(doAutoZoom));
            }

            String doShowErrors = data.get(SHOW_ERRORS);
            if (doShowErrors != null) {
                setShowErrors(Boolean.parseBoolean(doShowErrors));
            }

            String dx = data.get("dx");
            String dy = data.get("dy");
            if (dx != null && dy != null) {
                setDisplacement(new EastNorth(Double.parseDouble(dx), Double.parseDouble(dy)));
            }
        } catch (RuntimeException e) {
            throw BugReport.intercept(e).put("data", data);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (autoLoad ? 1231 : 1237);
        result = prime * result + (autoZoom ? 1231 : 1237);
        result = prime * result + (showErrors ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TileSourceDisplaySettings other = (TileSourceDisplaySettings) obj;
        if (autoLoad != other.autoLoad)
            return false;
        if (autoZoom != other.autoZoom)
            return false;
        if (showErrors != other.showErrors)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "TileSourceDisplaySettings [autoZoom=" + autoZoom + ", autoLoad=" + autoLoad + ", showErrors="
                + showErrors + ']';
    }

    /**
     * A listener that listens to changes to the {@link TileSourceDisplaySettings} object.
     * @author Michael Zangl
     * @since 10600 (functional interface)
     */
    @FunctionalInterface
    public interface DisplaySettingsChangeListener {
        /**
         * Called whenever the display settings have changed.
         * @param e The change event.
         */
        void displaySettingsChanged(DisplaySettingsChangeEvent e);
    }

    /**
     * An event that is created whenever the display settings change.
     * @author Michael Zangl
     */
    public static final class DisplaySettingsChangeEvent {
        private final TileSourceDisplaySettings source;
        private final String changedSetting;

        DisplaySettingsChangeEvent(TileSourceDisplaySettings source, String changedSetting) {
            this.source = source;
            this.changedSetting = changedSetting;
        }

        /**
         * Gets the display settings that caused this event.
         * @return The settings.
         * @since xxx
         */
        public TileSourceDisplaySettings getSource() {
            return source;
        }

        /**
         * Gets the setting that was changed
         * @return The name of the changed setting.
         */
        public String getChangedSetting() {
            return changedSetting;
        }

        @Override
        public String toString() {
            return "DisplaySettingsChangeEvent [changedSetting=" + changedSetting + ']';
        }
    }
}
