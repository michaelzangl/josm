// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.RestartAction;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.tools.GBC;

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

    private JCheckBox suppressAll;

    /**
     * Creates a new dialog.
     * @param data The data to display.
     */
    public CrashReportDialog(CrashReportData data) {
        super(toFrame(Main.parent), "Something went wrong");
        this.data = data;

        JPanel content = new JPanel();
        content.setLayout(new GridBagLayout());
        // FIXME: String copied from old bug report. Why the manual <br>? We should remove that and fix dialog layout.
        String message = tr("An unexpected exception occurred.<br>"
                + "This is always a coding error. If you are running the latest<br>"
                + "version of JOSM, please consider being kind and file a bug report.");
        JMultilineLabel messageLabel = new JMultilineLabel(message);
        messageLabel.setBackground(new Color(0xffff7373));
        messageLabel.setOpaque(true);
        messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, new Color(0xffff9191), new Color(
                0xfffc4848)));
        content.add(messageLabel, GBC.eol().insets(20, 10, 20, 20).fill(GBC.HORIZONTAL).grid(0, 0));
        content.add(new SuggestPluginDeactivationPanel(data), GBC.eop().fill(GBC.HORIZONTAL).grid(0, 1));

        content.add(new SuggestJosmUpdatePanel(), GBC.eop().fill(GBC.HORIZONTAL).grid(0, 2));

        content.add(new SuggestCrashReportPanel(data), GBC.eop().fill().grid(0, 3));
        suppress = new JCheckBox("Suppress further reports of this type for this session.");
        content.add(suppress, GBC.std().grid(0, 4).fill(GBC.HORIZONTAL));
        suppressAll = new JCheckBox("Suppress all reports for this session.");
        content.add(suppressAll, GBC.std().grid(0, 5).fill(GBC.HORIZONTAL));

        content.add(new JButton(new RestartAction()), GBC.std().grid(1, 4).span(1, 2).insets(10, 0, 0, 0));
        content.add(new JButton(new JosmAction("Ignore", null, "", null, false) {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeDialog(CloseOp.DO_NOTHING);
            }
        }), GBC.std().grid(2, 4).span(1, 2).insets(10, 0, 0, 0));


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
            closeListener.crashReportDialogClosed(new CrashReportCloseResult(suppress.isSelected(), suppressAll
                    .isSelected()));
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
        try {
            CrashReportDialog dialog = new CrashReportDialog(data);
            dialog.setCloseListener(closeListener);
            dialog.setVisible(true);
            return dialog;
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(null, "Could not create crash report dialog. Please look at the console.");
            Main.error("Could not create crash report dialog.");
            Main.error(t, true);
            return null;
        }
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
        private final boolean suppressAllReports;

        CrashReportCloseResult(boolean suppressReportsOfSameType, boolean suppressAllReports) {
            super();
            this.suppressReportsOfSameType = suppressReportsOfSameType;
            this.suppressAllReports = suppressAllReports;
        }

        /**
         * Checks if the user wants to suppress errors of this type.
         * @return <code>true</code> for yes.
         */
        public boolean isSuppressReportsOfSameType() {
            return suppressReportsOfSameType;
        }

        /**
         * Checks if all error dialogs should be suppressed.
         * @return  <code>true</code> for yes.
         */
        public boolean isSuppressAllReports() {
            return suppressAllReports;
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
