// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.plugins.PluginProxy;

public class SuggestPluginDeactivationPanel extends JPanel {

    public SuggestPluginDeactivationPanel(CrashReportData data) {
        PluginProxy plugin = data.getPluginCausingException();
        if (plugin != null) {
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            setBorder(BorderFactory.createTitledBorder("Caused by plugin?"));
            add(new JLabel("Was this caused by " + plugin.getPluginInformation().name + "."));

            JPanel buttons = new JPanel();
            setLayout(new BoxLayout(buttons, BoxLayout.LINE_AXIS));
            buttons.add(new JButton("Update Plugin"));
        }
    }

}
