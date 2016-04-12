// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.io.IOException;
import java.util.LinkedList;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.tools.WikiReader;

/**
 * This class tests if a newer JOSM version exists.
 * @author Michael Zangl
 *
 */
public class JosmVersionTester {
    public interface JosmVersionTesterListener {
        // FIXME: Currently fired in any thread. Fire only in UI Thread?
        void josmVersionChanged();
    }

    public enum UpToDate {
        UP_TO_DATE, HAVE_UPDATE_LATEST, HAVE_UPDATE_TESTED, UNKNOWN, ERROR, PENDING
    }

    /**
     * The current up to date state of JOSM.
     * @author Michael Zangl
     */
    public static class UpToDateState {
        /**
         * The latest JOSM version
         */
        private final int lastKnownJosmLatest;
        /**
         * The last tested version of JOSM.
         */
        private final int lastKnownJosmTested;

        private final UpToDate stateOverride;

        public UpToDateState() {
            this(UpToDate.PENDING);
        }

        public UpToDateState(int lastKnownJosmLatest, int lastKnownJosmTested) {
            this(lastKnownJosmLatest, lastKnownJosmTested, null);
        }

        public UpToDateState(UpToDate stateOverride) {
            this(Version.JOSM_UNKNOWN_VERSION, Version.JOSM_UNKNOWN_VERSION, stateOverride);
        }

        private UpToDateState(int lastKnownJosmLatest, int lastKnownJosmTested, UpToDate stateOverride) {
            super();
            this.lastKnownJosmLatest = lastKnownJosmLatest;
            this.lastKnownJosmTested = lastKnownJosmTested;
            this.stateOverride = stateOverride;
        }

        protected UpToDate isUpToDate() {
            if (stateOverride != null) {
                return stateOverride;
            }

            int josmVersion = Version.getInstance().getVersion();
            if (josmVersion == Version.JOSM_UNKNOWN_VERSION) {
                return UpToDate.UNKNOWN;
            } else if (lastKnownJosmTested == Version.JOSM_UNKNOWN_VERSION) {
                return UpToDate.UNKNOWN;
            }
            if (lastKnownJosmTested > josmVersion) {
                return UpToDate.HAVE_UPDATE_TESTED;
            } else if (lastKnownJosmTested != josmVersion) {
                // User is using a latest version.
                if (lastKnownJosmLatest == Version.JOSM_UNKNOWN_VERSION) {
                    return UpToDate.UNKNOWN;
                }
                if (lastKnownJosmLatest > josmVersion) {
                    return UpToDate.HAVE_UPDATE_LATEST;
                }
            }
            return UpToDate.UP_TO_DATE;
        }

        @Override
        public String toString() {
            return "UpToDateState [lastKnownJosmLatest=" + lastKnownJosmLatest + ", lastKnownJosmTested="
                    + lastKnownJosmTested + ", stateOverride=" + stateOverride + "]";
        }

    }

    /**
     * We re-request the version every 1h. For people like me running JOSM in background for days.
     */
    private static final long JOSM_REQUEST_PERIOD = 60 * 60 * 1000;

    private static long lastKnownJosmRequestTime = 0;

    private static UpToDateState state = new UpToDateState();

    private static LinkedList<JosmVersionTesterListener> listeners = new LinkedList<>();

    private static void requestJosmVersion() {
        if (doRequestStart()) {
            try {
                // Tested version is kept manually by updating this wiki page.
                String version = new WikiReader().read(Main.getJOSMWebsite() + "/wiki/TestedVersion?format=txt").trim();
                int lastKnownJosmTested = Integer.parseInt(version);
                // we might want to increase this.
                int lastKnownJosmLatest = lastKnownJosmTested;
                setState(new UpToDateState(lastKnownJosmLatest, lastKnownJosmTested));
            } catch (IOException | NumberFormatException e) {
                setState(new UpToDateState(UpToDate.ERROR));
            }
        }
    }

    private static synchronized void setState(UpToDateState upToDateState) {
        state = upToDateState;
        for (JosmVersionTesterListener l : listeners) {
            l.josmVersionChanged();
        }
    }

    /**
     * Gets the current update status that was queried from the server.
     * @return The update state. Never <code>null</code>.
     */
    public static synchronized UpToDateState getState() {
        if (shouldRequestVersion(System.currentTimeMillis())) {
            startRequestJosmVersion();
        }
        return state;
    }

    private static void startRequestJosmVersion() {
        Thread thread = new Thread("JOSM Version request.") {
            @Override
            public void run() {
                requestJosmVersion();
            }
        };
        thread.start();
    }

    /**
     * Updates the request time counter.
     * @return <code>true</code> if we should do a request.
     */
    private static synchronized boolean doRequestStart() {
        long timeMillis = System.currentTimeMillis();
        if (shouldRequestVersion(timeMillis)) {
            lastKnownJosmRequestTime = timeMillis;
            return true;
        }
        return false;
    }

    private static synchronized boolean shouldRequestVersion(long timeMillis) {
        return timeMillis > lastKnownJosmRequestTime + JOSM_REQUEST_PERIOD;
    }

    public static synchronized void addListener(JosmVersionTesterListener listener) {
        listeners.add(listener);
    }

    public static synchronized void removeListener(JosmVersionTesterListener listener) {
        listeners.remove(listener);
    }

    private JosmVersionTester() {
    }
}
