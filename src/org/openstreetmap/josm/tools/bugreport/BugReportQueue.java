// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.bugreport;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.function.BiFunction;

import org.openstreetmap.josm.Main;

/**
 * This class handles the display of the bug report dialog.
 * @author Michael Zangl
 * @since xxx
 */
public class BugReportQueue {

    private static final BugReportQueue INSTANCE = new BugReportQueue();

    private LinkedList<ReportedException> reportsToDisplay = new LinkedList<>();
    private boolean suppressAllMessages;
    private ArrayList<ReportedException> suppressFor = new ArrayList<>();
    private Thread displayThread;
    private final BiFunction<ReportedException, Integer, SuppressionMode> bugReportHandler = getBestHandler();
    private int displayedErrors;

    private boolean inReportDialog;


    /**
     * The suppression mode that should be used after the dialog was closed.
     */
    public enum SuppressionMode {
        /**
         * Suppress no dialogs.
         */
        NONE,
        /**
         * Suppress only the ones that are for the same error
         */
        SAME,
        /**
         * Suppress all report dialogs
         */
        ALL
    }

    /**
     * Submit a new error to be displayed
     * @param report The error to display
     */
    public synchronized void submit(ReportedException report) {
        if (suppressAllMessages || suppressFor.stream().anyMatch(report::isSame)) {
            Main.info("User requested to skip error " + report);
        } else if (reportsToDisplay.size() > 100 || reportsToDisplay.stream().filter(report::isSame).count() >= 10) {
            Main.warn("Too many errors. Dropping " + report);
        } else {
            reportsToDisplay.add(report);
            if (displayThread == null) {
                displayThread = new Thread(this::displayAll, "bug-report-display");
                displayThread.start();
            }
            notifyAll();
        }
    }

    private void displayAll() {
        try {
            while (true) {
                ReportedException e = getNext();
                SuppressionMode suppress = displayFor(e);
                handleDialogResult(e, suppress);
            }
        } catch (InterruptedException e) {
            displayFor(BugReport.intercept(e));
        }
    }

    private synchronized void handleDialogResult(ReportedException e, SuppressionMode suppress) {
        if (suppress == SuppressionMode.ALL) {
            suppressAllMessages = true;
            reportsToDisplay.clear();
        } else if (suppress == SuppressionMode.SAME) {
            suppressFor.add(e);
            reportsToDisplay.removeIf(e::isSame);
        }
        displayedErrors++;
        inReportDialog = false;
    }

    private synchronized ReportedException getNext() throws InterruptedException {
        while (reportsToDisplay.isEmpty()) {
            wait();
        }
        inReportDialog = true;
        return reportsToDisplay.removeFirst();
    }

    private SuppressionMode displayFor(ReportedException e) {
        return bugReportHandler.apply(e, getDisplayedErrors());
    }

    private synchronized int getDisplayedErrors() {
        return displayedErrors;
    }

    /**
     * Check if the dialog is shown. Should only be used for e.g. debugging.
     * @return <code>true</code> if the exception handler is still showing the exception to the user.
     */
    public synchronized boolean exceptionHandlingInProgress() {
        return !reportsToDisplay.isEmpty() || inReportDialog;
    }

    private static BiFunction<ReportedException, Integer, SuppressionMode> getBestHandler() {
        if (GraphicsEnvironment.isHeadless()) {
            return (e, index) -> { e.printStackTrace(); return SuppressionMode.NONE; };
        } else {
            return BugReportDialog::showFor;
        }
    }

    public static BugReportQueue getInstance() {
        return INSTANCE;
    }
}
