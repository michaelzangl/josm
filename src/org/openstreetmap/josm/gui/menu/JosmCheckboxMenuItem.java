// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;

import javax.swing.JCheckBoxMenuItem;

import org.openstreetmap.josm.actions.ToggleAction;

/**
 * A checkbox to be displayed in the menu.
 * @author Michael Zangl
 * @since xxx
 */
public class JosmCheckboxMenuItem extends JCheckBoxMenuItem implements JosmMenuReference {

    /**
     * Create a new checkbox item for the given action
     * @param toggleAction The action.
     */
    public JosmCheckboxMenuItem(ToggleAction toggleAction) {
        super(toggleAction);
        setAccelerator(toggleAction.getShortcut().getKeyStroke());
        toggleAction.addButtonModel(getModel());
    }

    @Override
    public ToggleAction getAction() {
        return (ToggleAction) super.getAction();
    }

    @Override
    public Component getMenuComponent() {
        return this;
    }

}
