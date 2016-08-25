// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.util.ArrayList;

import javax.swing.JMenuBar;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.menu.MenuInsertionFinder.MenuInsertionPoint;
import org.openstreetmap.josm.gui.menu.search.SearchReceiver;
import org.openstreetmap.josm.gui.menu.search.Searchable;
import org.openstreetmap.josm.tools.Pair;

/**
 * The main menu bar.
 * @author Michael Zangl
 * @since xxx
 */
public class JosmMenuBar extends JMenuBar implements Searchable {
    private final ArrayList<JosmMenu> menus = new ArrayList<>();

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
        menus.add(menu);
        add(menu, pos);
    }

    /**
     * Add a menu item to any of the sub menus in this menu.
     * @param item The item to add
     * @param p The position to add it at. The first matching position is used.
     * @return The newly added menu item or <code>null</code> if the position was not found.
     */
    public JosmMenuReference add(JosmAction item, MenuInsertionFinder p) {
        Pair<JosmMenu, MenuInsertionPoint> i = p.findInsertionPoint(menus);
        if (i == null) {
            // TODO: Fall back to tools.
            Main.error("Menu insertion point not found for {0} ", item);
            return null;
        } else {
            return i.a.add(item, MenuInsertionFinder.NONE.at(i.b.getInsertPosition()));
        }
    }

    @Override
    public void search(SearchReceiver sr) {
        sr.autoRecurse(getComponents());
    }
}
