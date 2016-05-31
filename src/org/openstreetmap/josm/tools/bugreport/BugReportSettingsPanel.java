// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.bugreport;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 * This panel displays the settings that can be changed before submitting a bug report to the web page.
 * @author Michael Zangl
 * @since xxx
 */
public class BugReportSettingsPanel extends JPanel {
    /**
     * Creates the new settings panel.
     * @param report The report this panel should influence.
     */
    public BugReportSettingsPanel(BugReport report) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JCheckBox statusReport = new JCheckBox(tr("Include the system status report."));
        statusReport.setSelected(report.getIncludeStatusReport());
        statusReport.addChangeListener(e -> report.setIncludeStatusReport(statusReport.isSelected()));
        add(statusReport);

        JCheckBox data = new JCheckBox(tr("Include information about the data that was worked on."));
        data.setSelected(report.getIncludeData());
        data.addChangeListener(e -> report.setIncludeData(data.isSelected()));
        add(data);

        JCheckBox allStackTraces = new JCheckBox(tr("Include all stack traces."));
        allStackTraces.setSelected(report.getIncludeAllStackTraces());
        allStackTraces.addChangeListener(e -> report.setIncludeAllStackTraces(allStackTraces.isSelected()));
        add(allStackTraces);
    }
}
