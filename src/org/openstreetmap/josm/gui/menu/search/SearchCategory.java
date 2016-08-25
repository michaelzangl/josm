// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu.search;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * The category for the search result.
 * @author Michael Zangl
 * @since xxx
 */
public class SearchCategory implements Comparable<SearchCategory> {
    public static final SearchCategory PRESET = new SearchCategory(tr("Presets"), 1000);
    public static final SearchCategory MENU = new SearchCategory(tr("Commands"), 1100);
    public static final SearchCategory PRIMITIVE = new SearchCategory(tr("Primitives"), 500);
    private final int priority;
    private final String name;

    /**
     * Create a new search category
     * @param name The human readable name
     * @param priority The priority. Higher priorities are more up in the list.
     */
    public SearchCategory(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    @Override
    public int compareTo(SearchCategory o) {
        return Integer.compare(priority, o.priority);
    }
}