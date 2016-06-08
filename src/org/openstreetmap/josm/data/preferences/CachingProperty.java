// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;

public class CachingProperty<T> extends AbstractProperty<T> implements PreferenceChangedListener {

    private T cache;
    private boolean cacheActive;
    private AbstractProperty<T> toCache;

    public CachingProperty(AbstractProperty<T> toCache) {
        super(toCache.getKey(), toCache.getDefaultValue());
        this.toCache = toCache;
        //TODO: Weak
        Main.pref.addKeyPreferenceChangeListener(getKey(), this);
    }

    @Override
    public synchronized T get() {
        if  (!cacheActive) {
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
    public synchronized void preferenceChanged(PreferenceChangeEvent e) {
        cacheActive = false;
    }


    public static AbstractProperty<Boolean> getBoolean(String key, Boolean defaultValue) {
        return new CachingProperty<>(new BooleanProperty(key, defaultValue));
    }
}
