// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.menu.search.SearchCategory;
import org.openstreetmap.josm.gui.menu.search.SearchReceiver;
import org.openstreetmap.josm.gui.menu.search.SearchResult;
import org.openstreetmap.josm.gui.menu.search.Searchable;

/**
 * This is a menu item that can have a JosmAction. Visibility is adjusted to expert mode.
 * @author Michael Zangl
 * @since xxx
 */
public class JosmMenuItem extends JMenuItem implements Searchable, JosmMenuReference {

    private boolean expertOnly;

    /**
     * Creates a JosmMenuItem
     * @param action The action.
     */
    public JosmMenuItem(JosmAction action) {
        super(action);
        setHorizontalTextPosition(JButton.TRAILING);
        setVerticalTextPosition(JButton.CENTER);
        KeyStroke ks = action.getShortcut().getKeyStroke();
        if (ks != null) {
            setAccelerator(ks);
        }
    }

    @Override
    public Component getMenuComponent() {
        return this;
    }

    @Override
    public JosmAction getAction() {
        return (JosmAction) super.getAction();
    }

    @Override
    public void search(SearchReceiver sr) {
        if (isVisible() && sr.wantsMore(SearchCategory.MENU) && sr.containsSearchText(getText())) {
            SearchResult result = new SearchResult(SearchCategory.MENU, getAction());
            sr.receive(result);
        }
    }

}
