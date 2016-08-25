// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * This is a search task that runs in the background.
 * @author Michael Zangl
 * @since xxx
 */
public class SearchTask extends Thread {

    /**
     * Cancel the ongoing search
     */
    private SearchTaskReceiver activeSearch;

    /**
     * Set the search to start next.
     */
    private SearchTaskReceiver startSearch;

    /**
     * A mutex this thread synchronizes on. We do not use the thread object to proevent the outside world to interfere with this.
     */
    private final Object syncMutex = new Object();

    /**
     * Stop the thread.
     */
    private boolean stopThread;

    private Consumer<List<SearchResult>> resultReceiver;

    public SearchTask(Consumer<List<SearchResult>> resultReceiver) {
        super("search");
        this.resultReceiver = resultReceiver;
    }

    /**
     * Stop and destroy the search thread.
     * @see #start()
     */
    public void requestStop() {
        synchronized (syncMutex) {
            if (activeSearch != null) {
                activeSearch.cancel();
            }
            startSearch = null;
            stopThread = true;
            syncMutex.notifyAll();
        }
    }

    public void search(String search) {
        synchronized(syncMutex) {
            if (!stopThread) {
                if (activeSearch != null) {
                    activeSearch.cancel();
                }
                startSearch = new SearchTaskReceiver(search);
                syncMutex.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        try {
            while (!stopThread) {
                SearchTaskReceiver task = waitForTask();
                if (stopThread) {
                    break;
                }
                SearchRegistry.searchInAll(task);

                ArrayList<SearchResult> results = task.getResults();
                results.sort(Comparator.comparing(SearchResult::getCategory).thenComparing(SearchResult::getPriority).reversed());
                resultReceiver.accept(results);
            }
        } catch (InterruptedException e) {
            BugReport.intercept(e).warn();
        }
    }

    private SearchTaskReceiver waitForTask() throws InterruptedException {
        synchronized (syncMutex) {
            while (startSearch == null && !stopThread) {
                syncMutex.wait();
            }
            activeSearch = startSearch;
            startSearch = null;
            return activeSearch;
        }
    }

    static class SearchTaskReceiver extends SearchReceiver {

        private boolean canceled;

        public SearchTaskReceiver(String search) {
            super(search);
        }

        void cancel() {
            canceled = true;
        }

        @Override
        public boolean wantsMore(SearchCategory category) {
            return !canceled && super.wantsMore(category);
        }

    }

    private class ResultComparator {

    }
}
