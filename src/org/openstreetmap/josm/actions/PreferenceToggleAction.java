// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import java.awt.event.ActionEvent;

import javax.swing.JCheckBoxMenuItem;

import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeEvent;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeListener;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.menu.JosmCheckboxMenuItem;

public class PreferenceToggleAction extends ToggleAction implements ValueChangeListener<Boolean> {

    private final JCheckBoxMenuItem checkbox;
    private final BooleanProperty pref;

    public PreferenceToggleAction(String name, String tooltip, String prefKey, boolean prefDefault) {
        this(name, tooltip, new BooleanProperty(prefKey, prefDefault));
    }

    public PreferenceToggleAction(String name, String tooltip, BooleanProperty pref) {
        super(name, null, tooltip, null, false);
        this.pref = pref;
        putValue("toolbar", "toggle-" + pref.getKey());
        pref.addWeakListener(this);

        checkbox = new JosmCheckboxMenuItem(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pref.put(!pref.get());
    }

    /**
     * Get the checkbox that can be used for this action. It can only be used at one place.
     * @return The checkbox.
     * @see #createMenuItem()
     */
    public JCheckBoxMenuItem getCheckbox() {
        return checkbox;
    }

    @Override
    public void valueChanged(ValueChangeEvent<? extends Boolean> e) {
        setSelected(pref.get());
    }
}
