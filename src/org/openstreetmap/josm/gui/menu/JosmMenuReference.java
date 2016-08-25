// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;

import org.openstreetmap.josm.actions.ExpertToggleAction;

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

    Component getMenuComponent();

}
