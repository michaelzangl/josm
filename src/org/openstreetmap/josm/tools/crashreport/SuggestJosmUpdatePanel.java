// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * This panel checks the current version of JOSM. If the user is not running the current stable/tested version, the user is prompted to update JOSM to the current version.
 *
 * @author Michael Zangl
 */
public class SuggestJosmUpdatePanel extends JPanel {
    public SuggestJosmUpdatePanel() {
        setBorder(BorderFactory.createLineBorder(Color.RED));
        add(new JLabel("TODO: Display a hint if JOSM needs to be updated."));
    }
}
