// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeListener;

/**
 * This is a special wrapper of {@link AbstractProperty}. The current preference value is cached. The value is invalidated if the preference was
 * changed.
 * @author Michael Zangl
 *
 * @param <T>
 * @since xxx
 */
public class CachingProperty<T> extends AbstractProperty<T> implements ValueChangeListener<T> {

    private T cache;
    private boolean cacheActive;
    private final AbstractProperty<T> toCache;

    /**
     * Create a new caching property.
     * @param toCache The property to cache.
     */
    CachingProperty(AbstractProperty<T> toCache) {
        super(toCache.getKey(), toCache.getDefaultValue());
        this.toCache = toCache;
        addWeakListener(this);
    }

    @Override
    public synchronized T get() {
        if (!cacheActive) {
            cache = toCache.get();
            cacheActive = true;
        }
        return cache;
    }

    @Override
    public boolean put(T value) {
        return toCache.put(cache);
    }

    @Override
    public synchronized void valueChanged(org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeEvent<? extends T> e) {
        cacheActive = false;
    }
}
