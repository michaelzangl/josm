// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;

/**
 * Captures the common functionality of preference properties
 * @param <T> The type of object accessed by this property
 */
public abstract class AbstractProperty<T> {

    private final class PreferenceChangedListenerAdapter implements PreferenceChangedListener {
        private ValueChangeListener<? super T> listener;

        public PreferenceChangedListenerAdapter(ValueChangeListener<? super T> listener) {
            this.listener = listener;
        }

        @Override
        public void preferenceChanged(PreferenceChangeEvent e) {
            listener.valueChanged(new ValueChangeEvent<>(e, AbstractProperty.this));
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((listener == null) ? 0 : listener.hashCode());
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PreferenceChangedListenerAdapter other = (PreferenceChangedListenerAdapter) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (listener == null) {
                if (other.listener != null)
                    return false;
            } else if (!listener.equals(other.listener))
                return false;
            return true;
        }

        private AbstractProperty<T> getOuterType() {
            return AbstractProperty.this;
        }

        @Override
        public String toString() {
            return "PreferenceChangedListenerAdapter [listener=" + listener + "]";
        }
    }

    /**
     * A listener that listens to changes in the properties value.
     * @author michael
     *
     */
    public interface ValueChangeListener<T> {
        public void valueChanged(ValueChangeEvent<? extends T> e);
    }

    /**
     * An event that is triggered if the value of a property changes.
     * @author Michael Zangl
     * @param <T>
     * @since xxx
     */
    public static class ValueChangeEvent<T> {
        private final PreferenceChangeEvent base;

        private final AbstractProperty<T> source;

        ValueChangeEvent(PreferenceChangeEvent base, AbstractProperty<T> source) {
            this.base = base;
            this.source = source;
        }

        /**
         * Get the property that was changed
         * @return The property.
         */
        public AbstractProperty<T> getProperty() {
            return source;
        }
    }

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
    public void addListener(ValueChangeListener<? super T> listener) {
        addListenerImpl(new PreferenceChangedListenerAdapter(listener));
    }

    protected void addListenerImpl(PreferenceChangedListener adapter) {
        getPreferences().addKeyPreferenceChangeListener(getKey(), adapter);
    }

    /**
     * Adds a weak listener that listens only for changes to this preference key.
     * @param listener The listener to add.
     */
    public void addWeakListener(ValueChangeListener<? super T> listener) {
        addWeakListenerImpl(new PreferenceChangedListenerAdapter(listener));
    }

    protected void addWeakListenerImpl(PreferenceChangedListener adapter) {
        getPreferences().addWeakKeyPreferenceChangeListener(getKey(), adapter);
    }

    /**
     * Removes a listener that listens only for changes to this preference key.
     * @param listener The listener to add.
     */
    public void removeListener(ValueChangeListener<? super T> listener) {
        removeListenerImpl(new PreferenceChangedListenerAdapter(listener));
    }

    protected void removeListenerImpl(PreferenceChangedListener adapter) {
        getPreferences().removeKeyPreferenceChangeListener(getKey(), adapter);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((preferences == null) ? 0 : preferences.hashCode());
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
        AbstractProperty other = (AbstractProperty) obj;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (preferences == null) {
            if (other.preferences != null)
                return false;
        } else if (!preferences.equals(other.preferences))
            return false;
        return true;
    }
}
