// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu.search;

import java.awt.event.ActionListener;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.Shortcut;

public class SearchResult {
    private final SearchCategory category;
    private final String commandName;
    private final ActionListener action;

    private String commandHint;
    private Shortcut shortcutHint;
    private Icon icon;
    private boolean enabled = true;

    /**
     * Create a new search result
     * @param category The category this result is of
     * @param commandName The name of the command (human readable)
     * @param action The action to take when the user wants to issue this command.
     */
    public SearchResult(SearchCategory category, String commandName, ActionListener action) {
        this.category = category;
        this.commandName = commandName;
        this.action = action;
    }

    public SearchResult(SearchCategory category, JosmAction action) {
        this(category, (String) action.getValue(Action.NAME), action);
        enabled = action.isEnabled();
        Object res = action.getValue("ImageResource");
        if (res != null) {
            icon = ((ImageResource) res).getImageIcon();
        }
    }

    public SearchCategory getCategory() {
        return category;
    }

    public String getCommandName() {
        return commandName;
    }

    public Icon getIcon() {
        return icon;
    }

    public ActionListener getAction() {
        return action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return enabled ? 1 : 0;
    }

    @Override
    public String toString() {
        return "SearchResult [category=" + category + ", commandName=" + commandName + "]";
    }


}
