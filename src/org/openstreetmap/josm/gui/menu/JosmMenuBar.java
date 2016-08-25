// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import javax.swing.JMenuBar;

import org.openstreetmap.josm.gui.menu.search.SearchReceiver;
import org.openstreetmap.josm.gui.menu.search.Searchable;

/**
 * The main menu bar.
 * @author Michael Zangl
 * @since xxx
 */
public class JosmMenuBar extends JMenuBar implements Searchable {
    private int subMenuCount;

    /**
     * Create a new menu bar for JOSM
     */
    public JosmMenuBar() {
    }

    /**
     * Add a sub menu to JOSM
     * @param menu The menu
     * @param p The position to add it at.
     */
    public void add(JosmMenu menu, MenuInsertionFinder p) {
        int pos = p.findInsertionPoint(this).getInsertPosition();
        add(menu, pos);
    }

    @Override
    public void search(SearchReceiver sr) {
        sr.autoRecurse(getComponents());
    }
}
