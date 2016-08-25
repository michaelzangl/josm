// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import java.awt.event.ActionEvent;

import javax.swing.JCheckBoxMenuItem;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.preferences.BooleanProperty;

public class PreferenceToggleAction extends JosmAction implements PreferenceChangedListener {

    private final JCheckBoxMenuItem checkbox;
    private final BooleanProperty pref;

    public PreferenceToggleAction(String name, String tooltip, String prefKey, boolean prefDefault) {
        super(name, null, tooltip, null, false);
        putValue("toolbar", "toggle-" + prefKey);
        this.pref = new BooleanProperty(prefKey, prefDefault);
        checkbox = new JCheckBoxMenuItem(this);
        checkbox.setSelected(pref.get());
        Main.pref.addWeakKeyPreferenceChangeListener(prefKey, this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pref.put(checkbox.isSelected());
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
    public void preferenceChanged(Preferences.PreferenceChangeEvent e) {
        checkbox.setSelected(pref.get());
    }
}
