// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu.search;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This is the base registry where all {@link Searchable} modules may put their searchable instances.
 * @author Michael Zangl
 * @since xxx
 */
public class SearchRegistry {
    private static CopyOnWriteArrayList<Searchable> searchables = new CopyOnWriteArrayList<>();

    private SearchRegistry() {
        // hidden
    }

    public static void add(Searchable searchable) {
        searchables.add(searchable);
    }

    public static void searchInAll(SearchReceiver sr) {
        for (Searchable s : searchables) {
            s.search(sr);
        }
    }
}
