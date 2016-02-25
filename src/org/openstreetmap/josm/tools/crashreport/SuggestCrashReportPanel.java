// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class SuggestCrashReportPanel extends JPanel {
    public SuggestCrashReportPanel(CrashReportData data) {
        // Do not translate. We send this to Trac.
        StringWriter debugString = new StringWriter();
        PrintWriter out = new PrintWriter(debugString);
        data.writeTo(out);

        setBorder(BorderFactory.createTitledBorder("Send crash report."));
        add(new JTextArea(debugString.getBuffer().toString()));
    }
}
