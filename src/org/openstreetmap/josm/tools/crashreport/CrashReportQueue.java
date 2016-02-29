// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.tools.Predicate;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.crashreport.CrashReportDialog.CrashReportCloseListener;
import org.openstreetmap.josm.tools.crashreport.CrashReportDialog.CrashReportCloseResult;

/**
 * This is a queue of pending crash reports that should be displayed.
 *
 * @author Michael Zangl
 *
 */
public class CrashReportQueue {
    /**
     * Maximum number of pending crash reports. If there are more, they are discared.
     */
    private static final int MAX_PENDING = 100;

    /**
     * Maximum number of crash reports that are considered to be the same type.
     */
    private static final int MAX_PENDING_SAME_TYPE = 11;

    private final LinkedList<CrashReportData> pending = new LinkedList<>();

    private boolean dialogShown;

    /**
     * A list of crash reports that were suppressed by the user.
     */
    private final ArrayList<CrashReportData> suppressed = new ArrayList<>();

    /**
     * Enqueues a crash report that should be displayed.
     * @param data The data to display.
     */
    public synchronized void enqueue(CrashReportData data) {
        if (isSuppressed(data)) {
            Main.trace("Dropped pending crash report because it is Suppressed.");
        } else if (pending.size() >= MAX_PENDING) {
            Main.trace("Dropped pending crash report because we exceeded total limit.");
        } else if (countSame(data) >= MAX_PENDING_SAME_TYPE) {
            Main.trace("Dropped pending crash report because we exceeded same type limit.");
        } else {
            pending.add(data);
            recheckShowDialog();
        }
    }

    /**
     * Displays the crash report dialog if required.
     */
    private void recheckShowDialog() {
        if (!pending.isEmpty() && !dialogShown) {
            CrashReportData toShow = pending.poll();
            CrashReportDialog.displayForDataAsync(toShow, createCloseListener(toShow));
            dialogShown = true;
        }
    }

    protected synchronized void crashReportClosed(CrashReportData suppress) {
        if (suppress != null) {
            suppressOfType(suppress);
        }
        dialogShown = false;
        recheckShowDialog();
    }

    private CrashReportCloseListener createCloseListener(final CrashReportData toShow) {
        return new CrashReportCloseListener() {
            @Override
            public void crashReportDialogClosed(CrashReportCloseResult closeResult) {
                crashReportClosed(closeResult.isSuppressReportsOfSameType() ? toShow : null);
            }
        };
    }

    protected void suppressOfType(CrashReportData toShow) {
        suppressed.add(toShow);
        Collection<CrashReportData> toRemove = Utils.filter(pending, new IsSamePredicate(toShow));
        pending.removeAll(toRemove);
    }

    private boolean isSuppressed(CrashReportData data) {
        return Utils.exists(suppressed, new IsSamePredicate(data));
    }

    /**
     * Counts how many crash reports are considered the same as data.
     * @param data The crash report to compare to.
     * @return That number
     */
    private int countSame(CrashReportData data) {
        return Utils.filter(pending, new IsSamePredicate(data)).size();
    }

    private static class IsSamePredicate implements Predicate<CrashReportData> {
        private final CrashReportData sameTo;

        IsSamePredicate(CrashReportData sameTo) {
            super();
            this.sameTo = sameTo;
        }

        @Override
        public boolean evaluate(CrashReportData object) {
            return sameTo.isSame(object);
        }

        @Override
        public String toString() {
            return "IsSamePredicate [sameTo=" + sameTo + "]";
        }

    }
}
