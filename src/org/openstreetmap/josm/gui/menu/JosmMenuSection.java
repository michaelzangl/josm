// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;

/**
 * This is the section header of a new Menu.
 * @author Michael Zangl
 * @since xxx
 */
public class JosmMenuSection extends JLabel {

    private String id;

    /**
     * Create a new section
     * @param id The section id
     * @param label The label.
     */
    public JosmMenuSection(String id, String label) {
        super();
        this.id = id;
        UIDefaults defaults = UIManager.getDefaults();
        TitledBorder title = BorderFactory.createTitledBorder(label);
        title.setBorder(new MatteBorder(3, 0, 0, 0, defaults.getColor("Separator.foreground")));
        setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(5, 1, 2, 1), title));
    }

    public String getSectionId() {
        return id;
    }
}
