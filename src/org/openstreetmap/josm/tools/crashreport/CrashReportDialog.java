// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
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

    private CrashReportCloseListener closeListener;

    private JCheckBox suppress;

    /**
     * Creates a new dialog.
     * @param data The data to display.
     */
    public CrashReportDialog(CrashReportData data) {
        super(toFrame(Main.parent), "Something went wrong");
        this.data = data;

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.PAGE_AXIS));
        content.add(new SuggestPluginDeactivationPanel(data));

        content.add(new SuggestJosmUpdatePanel());

        content.add(new SuggestCrashReportPanel(data));
        suppress = new JCheckBox("Suppress further reports of this type for this session.");
        content.add(suppress);
        setContentPane(content);
        pack();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog(CloseOp.DO_NOTHING);
            }
        });
    }

    protected void closeDialog(CloseOp doNothing) {
        setVisible(false);
        if (closeListener != null) {
            closeListener.crashReportDialogClosed(new CrashReportCloseResult(suppress.isSelected()));
        }
        dispose();
    }

    private static Frame toFrame(Component parent) {
        if (parent instanceof Frame) {
            return (Frame) parent;
        } else {
            return null;
        }
    }

    /**
     * Sets the listener that listens for dialog close events.
     * @param closeListener The listener.
     */
    public void setCloseListener(CrashReportCloseListener closeListener) {
        this.closeListener = closeListener;
    }

    /**
     * Displays a crash report dialog. May only be called in the UI thread.
     * @param data The data to display.
     * @param closeListener A listener that is notified when the dialog is closed.
     * @return The dialog.
     */
    private static CrashReportDialog displayForData(CrashReportData data, CrashReportCloseListener closeListener) {
        CrashReportDialog dialog = new CrashReportDialog(data);
        dialog.setCloseListener(closeListener);
        dialog.setVisible(true);
        return dialog;
    }

    /**
     * Displays a crash report dialog.
     * @param data The data to display.
     * @param closeListener A listener to call when the dialog is closed.
     */
    public static void displayForDataAsync(CrashReportData data, CrashReportCloseListener closeListener) {
        SwingUtilities.invokeLater(new CrashReportDialogDisplayer(data, closeListener));
    }

    private static class CrashReportDialogDisplayer implements Runnable {

        private final CrashReportData data;
        private final CrashReportCloseListener closeListener;

        CrashReportDialogDisplayer(CrashReportData data, CrashReportCloseListener closeListener) {
            this.data = data;
            this.closeListener = closeListener;
        }

        @Override
        public void run() {
            displayForData(data, closeListener);
        }
    }

    /**
     * This is the result of displaying a crash report dialog.
     *
     * @author Michael Zangl
     *
     */
    public static class CrashReportCloseResult {
        private final boolean suppressReportsOfSameType;

        CrashReportCloseResult(boolean suppressReportsOfSameType) {
            super();
            this.suppressReportsOfSameType = suppressReportsOfSameType;
        }

        /**
         * Checks if the user wants to suppress errors of this type.
         * @return <code>true</code> for yes.
         */
        public boolean isSuppressReportsOfSameType() {
            return suppressReportsOfSameType;
        }

        @Override
        public String toString() {
            return "CrashReportCloseResult [supressReportsOfSameType=" + suppressReportsOfSameType + "]";
        }

    }

    public static interface CrashReportCloseListener {
        void crashReportDialogClosed(CrashReportCloseResult closeResult);
    }

    private static enum CloseOp {
        DO_NOTHING
    }
}
