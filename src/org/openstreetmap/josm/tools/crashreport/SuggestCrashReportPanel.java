// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * This panel displays allows the user to send in a crash report.
 * <p>
 * It displays some help on how to send in the crash report and a Button to open the web browser.
 * <p>
 * For experts, there is an expand button that allows you to see the stack trace.
 *
 * @author Michael Zangl
 */
public class SuggestCrashReportPanel extends JPanel {
    public SuggestCrashReportPanel(CrashReportData data) {
        // Do not translate. We send this to Trac.
        StringWriter debugString = new StringWriter();
        PrintWriter out = new PrintWriter(debugString);
        data.writeTo(out);

        setBorder(BorderFactory.createTitledBorder("Send crash report."));
        add(new JScrollPane(new JTextArea(debugString.getBuffer().toString())));
    }
}
