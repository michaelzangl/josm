// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.awt.Component;
import java.awt.Frame;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;

/**
 * This defines the main crash report dialog.
 * <p>
 * The dialog has multiple sections:
 * <ul>
 * <li> A header presenting the user with a short information on what happened.
 * <li> Plugin action buttons if the exception was caused by a plugin.
 * <li> JOSM action buttons (hidden on default for plugins)
 * <li> A detailed information text area. This information area is hidden by default.
 * </ul>
 * <p>
 * The user is presented with a link that allows it to directly file a bug report. The report is automatically sent to the JOSM server.
 *
 * @author Michael Zangl
 */
public class CrashReportDialog extends JDialog {

    private final CrashReportData data;

    /**
     * Creates a new dialog.
     * @param data The data to display.
     */
    public CrashReportDialog(CrashReportData data) {
        super(toFrame(Main.parent), "Something went wrong");
        this.data = data;

        JPanel content = new JPanel();
        content.add(new SuggestPluginDeactivationPanel(data));

        content.add(new SuggestJosmUpdatePanel());


        content.add(new SuggestCrashReportPanel(data));
        setContentPane(content);
        pack();
    }

    private static Frame toFrame(Component parent) {
        if (parent instanceof Frame) {
            return (Frame) parent;
        } else {
            return null;
        }
    }

    /**
     * Displays a crash report dialog. May only be called in the UI thread.
     * @param data The data to display.
     * @return The dialog.
     */
    public static CrashReportDialog displayForData(CrashReportData data) {
        CrashReportDialog dialog = new CrashReportDialog(data);
        dialog.setVisible(true);
        return dialog;
    }

    /**
     * Displays a crash report dialog.
     * @param data The data to display.
     */
    public static void displayForDataAsync(CrashReportData data) {
        SwingUtilities.invokeLater(new CrashReportDialogDisplayer(data));
    }

    private static class CrashReportDialogDisplayer implements Runnable {

        private final CrashReportData data;

        public CrashReportDialogDisplayer(CrashReportData data) {
            this.data = data;
        }

        @Override
        public void run() {
            displayForData(data);
        }
    }
}
