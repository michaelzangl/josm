// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This is a list of listeners. It does error checking and allows you to fire all listeners.
 *
 * @author Michael Zangl
 * @param <T> The type of listener contained in this list.
 */
public class ListenerList<T> {
    /**
     * This is a function that can be invoked for every listener.
     * @param <T> the listener type.
     */
    public interface EventFirerer<T> {
        /**
         * Should fire the event for the given listener.
         * @param listener The listener to fire the event for.
         */
        void fire(T listener);
    }

    private static final class WeakListener<T> {

        private WeakReference<T> listener;

        WeakListener(T listener) {
            this.listener = new WeakReference<>(listener);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != null && obj.getClass() == WeakListener.class) {
                return Objects.equals(listener.get(), ((WeakListener<?>) obj).listener.get());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            T l = listener.get();
            if (l == null) {
                return 0;
            } else {
                return l.hashCode();
            }
        }
    }

    private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<WeakListener<T>> weakListeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener. The listener will not prevent the object from being garbage collected.
     *
     * This should be used with care. It is better to add good cleanup code.
     * @param listener The listener.
     */
    public synchronized void addWeakListener(T listener) {
        if (containsListener(listener)) {
            throw new IllegalArgumentException("Listener " + listener + " was already registered.");
        }
        // clean the weak listeners, just to be sure...
        while (weakListeners.remove(new WeakListener<T>(null)));
        weakListeners.add(new WeakListener<>(listener));
    }

    /**
     * Adds a listener.
     * @param listener The listener to add.
     */
    public synchronized void addListener(T listener) {
        if (containsListener(listener)) {
            throw new IllegalArgumentException("Listener " + listener + " was already registered.");
        }
        listeners.add(listener);
    }

    private boolean containsListener(T listener) {
        return listeners.contains(listener) || weakListeners.contains(new WeakListener<>(listener));
    }

    /**
     * Removes a listener.
     * @param listener The listener to remove.
     */
    public synchronized void removeListener(T listener) {
        if (!listeners.remove(listener) && !weakListeners.remove(new WeakListener<>(listener))) {
            throw new IllegalArgumentException("Listener " + listener + " was already registered.");
        }
    }

    /**
     * Check if any listeners are registered.
     * @return <code>true</code> if any are registered.
     */
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Fires an event to every listener.
     * @param eventFirerer The firerer to invoke the event method of the listener.
     */
    public void fireEvent(EventFirerer<T> eventFirerer) {
        for (T l : listeners) {
            eventFirerer.fire(l);
        }
        for (Iterator<WeakListener<T>> iterator = weakListeners.iterator(); iterator.hasNext();) {
            WeakListener<T> weakLink = iterator.next();
            T l = weakLink.listener.get();
            if (l == null) {
                iterator.remove();
            } else {
                eventFirerer.fire(l);
            }
        }
    }
}
