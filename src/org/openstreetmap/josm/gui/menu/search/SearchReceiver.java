// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu.search;

import java.util.ArrayList;


public class SearchReceiver {

    private final String searchText;

    private final ArrayList<SearchResult> results = new ArrayList<>();

    public SearchReceiver(String search) {
        searchText = search;
    }

    public String getSearchText() {
        return searchText;
    }

    public boolean containsSearchText(String haystack) {
        return haystack.toLowerCase().contains(searchText.toLowerCase());
    }

    /**
     * Should be checked periodically to see if the search was cancelled.
     * @param category The category that we want to check for.
     * @return <code>true</code> if wants more.
     */
    public boolean wantsMore(SearchCategory category) {
        // We may limit this, e.g.:
        // return results.size() < 20 && results.stream().map(SearchResult::getCategory).filter(category::equals).count() < 6;
        return true;
    }

//    /**
//     * Get the minimum search priority this receiver is waiting for.
//     * @return The priority.
//     */
//    public int getMinSearchPriority(SearchCategory category) {
//
//    }

    /**
     * Receives the result of a search operation
     * @param result The search result.
     */
    public void receive(SearchResult result) {
        if (wantsMore(result.getCategory())) {
            results.add(result);
        }
    }

    /**
     * Search all searchable objects in the array using this search receiver.
     * @param <T> array type
     * @param array The array
     */
    public <T> void autoRecurse(T[] array) {
        for (T a : array) {
            if (a instanceof Searchable) {
                ((Searchable) a).search(this);
            }
        }
    }

    protected ArrayList<SearchResult> getResults() {
        return results;
    }
}