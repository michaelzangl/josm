// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.JosmAction;

/**
 * This is the reference to a menu item that is returned after adding it to the main menu.
 * @author Michael Zangl
 * @since xxx
 */
public interface JosmMenuReference {

    /**
     * Set this item to only be displayed in expert mode.
     * @param expertOnly <code>true</code> to only display in expert mode.
     */
    default void setExpertOnly(boolean expertOnly) {
        if (expertOnly) {
            ExpertToggleAction.addVisibilitySwitcher(getMenuComponent());
        } else {
            ExpertToggleAction.removeVisibilitySwitcher(getMenuComponent());
        }
    }

    /**
     * Get the component that this reference is for.
     * @return The component.
     */
    Component getMenuComponent();

    /**
     * Removes this item from the JOSM menu.
     */
    default void remove() {
        Component comp = getMenuComponent();
        if (comp.getParent() == null) {
            throw new IllegalStateException("The item has not been added to a menu.");
        }
        comp.getParent().remove(comp);
    }

    JosmAction getAction();

}
