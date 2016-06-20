// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * This class represents a property that can be represented as String.
 *
 * @author Michael Zangl
 *
 * @param <T> The property content type.
 */
public abstract class AbstractToStringProperty<T> extends AbstractProperty<T> {

    /**
     * This is a more specialized version of this property.
     *
     * @author Michael Zangl
     * @param <T> The type
     *
     */
    public static class SpecializedProperty<T> extends AbstractToStringProperty<T> {
        private AbstractToStringProperty<T> parent;

        SpecializedProperty(AbstractToStringProperty<T> parent, String key) {
            super(key, null);
            this.parent = parent;
        }

        @Override
        public T getDefaultValue() {
            return parent.get();
        }

        @Override
        protected T fromString(String string) {
            return parent.fromString(string);
        }

        @Override
        protected String toString(T t) {
            return parent.toString();
        }

        @Override
        public CachingProperty<T> cached() {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

    }

    /**
     * Create a new property and store the default value.
     * @param key The key
     * @param defaultValue The default value.
     * @see AbstractProperty#AbstractProperty(String, Object)
     */
    public AbstractToStringProperty(String key, T defaultValue) {
        super(key, defaultValue);
        storeDefaultValue();
    }

    @Override
    public T get() {
        String string = getAsString();
        if (!string.isEmpty()) {
            try {
                return fromString(string);
            } catch (InvalidPreferenceValueException e) {
                Main.warn(BugReport.intercept(e).put("key", key).put("value", string));
            }
        }
        return getDefaultValue();
    }

    /**
     * Converts the string to an object of the given type.
     * @param string The string
     * @return The object.
     * @throws InvalidPreferenceValueException If the value could not be converted.
     * @since xxx
     */
    protected abstract T fromString(String string);

    @Override
    public boolean put(T value) {
        String string = value == null ? null : toString(value);
        return getPreferences().put(getKey(), string);
    }

    /**
     * Converts the string to an object of the given type.
     * @param t The object.
     * @return The string representing the object
     * @throws InvalidPreferenceValueException If the value could not be converted.
     * @since xxx
     */
    protected abstract String toString(T t);

    /**
     * Gets the preference value as String.
     * @return The string preference value.
     */
    protected String getAsString() {
        T def = getDefaultValue();
        return getPreferences().get(key, def == null ? "" : toString(def));
    }

    /**
     * Gets a specialized setting value that has the current value as default
     * <p>
     * The key will be getKey().spec
     * @param spec The key specialization
     * @return The property
     */
    public AbstractProperty<T> getSpecialized(String spec) {
        return getDependingSetting(getKey() + "." + spec);
    }

    private SpecializedProperty<T> getDependingSetting(String key) {
        return new SpecializedProperty<>(this, key);
    }

    /**
     * Creates a new {@link CachingProperty} instance for this property.
     * @return The new caching property instance.
     */
    public CachingProperty<T> cached() {
        return new CachingProperty<>(this);
    }
}
