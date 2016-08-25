// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu.search;

/**
 * Something that can be searched.
 * @author Michael Zangl
 * @since xxx
 */
public interface Searchable {
    /**
     * Searches for the given search.
     * @param sr The search and the object to send results to.
     */
    void search(SearchReceiver sr);
}
