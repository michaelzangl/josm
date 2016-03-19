// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.bugreport.BugReportSender;

/**
 * Reports a ticket to JOSM bugtracker.
 * @since 7624
 */
public class ReportBugAction extends JosmAction {

    /**
     * Constructs a new {@code ReportBugAction}.
     */
    public ReportBugAction() {
        super(tr("Report bug"), "bug", tr("Report a ticket to JOSM bugtracker"),
                Shortcut.registerShortcut("reportbug", tr("Report a ticket to JOSM bugtracker"),
                        KeyEvent.CHAR_UNDEFINED, Shortcut.NONE), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        startBugReport();
    }

    /**
     * Reports a ticket to JOSM bugtracker.
     * <p>
     * Replaced by {@link BugReportSender#reportBug(String)}
     */
    @Deprecated
    public static void reportBug() {
        startBugReport();
    }

    private static void startBugReport() {
        BugReportSender.reportBug(ShowStatusReportAction.getReportHeader());
    }

    /**
     * Reports a ticket to JOSM bugtracker with given status report.
     * Replaced by {@link BugReportSender#reportBug(String)}
     * @param report Status report header containing technical, non-personal information
     */
    @Deprecated
    public static void reportBug(String report) {
        BugReportSender.reportBug(report);
    }
}
