// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;

/**
 * Captures the common functionality of preference properties
 * @param <T> The type of object accessed by this property
 */
public abstract class AbstractProperty<T> {
    /**
     * An exception that is thrown if a preference value is invalid.
     * @author Michael Zangl
     */
    public static class InvalidPreferenceValueException extends RuntimeException {

        public InvalidPreferenceValueException() {
            super();
        }

        public InvalidPreferenceValueException(String message, Throwable cause) {
            super(message, cause);
        }

        public InvalidPreferenceValueException(String message) {
            super(message);
        }

        public InvalidPreferenceValueException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * The preferences object this property is for.
     */
    protected final Preferences preferences;
    protected final String key;
    protected final T defaultValue;

    /**
     * Constructs a new {@code AbstractProperty}.
     * @param key The property key
     * @param defaultValue The default value
     * @since 5464
     */
    public AbstractProperty(String key, T defaultValue) {
        // Main.pref should not change in production but may change during tests.
        preferences = Main.pref;
        this.key = key;
        this.defaultValue = defaultValue;
    }

    /**
     * Store the default value to {@link Preferences}.
     */
    protected void storeDefaultValue() {
        if (getPreferences() != null) {
            get();
        }
    }

    /**
     * Replies the property key.
     * @return The property key
     */
    public String getKey() {
        return key;
    }

    /**
     * Determines if this property is currently set in JOSM preferences.
     * @return true if {@code Main.pref} contains this property.
     */
    public boolean isSet() {
        return !getPreferences().get(key).isEmpty();
    }

    /**
     * Replies the default value of this property.
     * @return The default value of this property
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Removes this property from JOSM preferences (i.e replace it by its default value).
     */
    public void remove() {
        put(getDefaultValue());
    }

    /**
     * Replies the value of this property.
     * @return the value of this property
     * @since 5464
     */
    public abstract T get();

    /**
     * Sets this property to the specified value.
     * @param value The new value of this property
     * @return true if something has changed (i.e. value is different than before)
     * @since 5464
     */
    public abstract boolean put(T value);


    /**
     * Gets the preferences used for this property.
     * @return The preferences for this property.
     * @since xxx
     */
    protected Preferences getPreferences() {
        return preferences;
    }

    /**
     * Adds a listener that listens only for changes to this preference key.
     * @param listener The listener to add.
     */
    public void addListener(PreferenceChangedListener listener) {
        getPreferences().addKeyPreferenceChangeListener(getKey(), listener);
    }

    /**
     * Removes a listener that listens only for changes to this preference key.
     * @param listener The listener to add.
     */
    public void removeListener(PreferenceChangedListener listener) {
        getPreferences().removeKeyPreferenceChangeListener(getKey(), listener);
    }
}
