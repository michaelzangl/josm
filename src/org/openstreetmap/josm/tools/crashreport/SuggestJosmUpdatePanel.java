// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * @author michael
 *
 */
public class SuggestJosmUpdatePanel extends JPanel {
    public SuggestJosmUpdatePanel() {
        setBorder(BorderFactory.createLineBorder(Color.RED));
        add(new JLabel("TODO: Display a hint if JOSM needs to be updated."));
    }
}
