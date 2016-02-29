// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.plugins.PluginProxy;

/**
 * This panel allows the user to deactivate a plugin that caused an exception.
 * <p>
 * If the user agrees, the plugin is deactivated on the next restart. If the user wants to, JOSM can be restarted immediately.
 *
 * @author Michael Zangl
 */
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
            buttons.add(new JButton("Disable Plugin, manual restart"));
            buttons.add(new JButton("Disable and restart (loses changes!)"));
        }
    }

}
