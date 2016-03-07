// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.actions.ShowStatusReportAction;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.PluginException;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.PluginProxy;
import org.openstreetmap.josm.tools.crashreport.CrashReportInfo.CrashReportInfoMap;

/**
 * This class collects data on a crash that happened.
 * <p>
 * The crash data always includes an exception that occurred and description of the place the crash happened. It may contain additional values.
 * <p>
 * This class is throwable. Instances can simply be re-thrown and will be catched and handled by the global exception handler automatically.
 * <p>
 * This class is used in exception handling. Here is an example code on how to use:
 * <pre>
 * try {
 *      ... code ...
 * } catch (Throwable t) {
 *     throw CrashReportData.createCrashReport(t, "What we did").put("primitive", osm).put("name", name);
 * }
 * </pre>
 * You can re-throw the crash report if you want to abort and let JOSM handle the exception.
 * If you want to display a message to the user and continue afterwards, you can use the {@link #display()} method.
 *
 * @author Michael Zangl
 */
public final class CrashReportData extends RuntimeException {

    private static final CrashReportQueue DISPLAY_QUEUE = new CrashReportQueue();

    private final Throwable exception;
    /**
     * The description tags, the innermost is first.
     */
    private LinkedList<CrashReportSection> sections = new LinkedList<>();

    private CrashReportData(Throwable exception, String description) {
        super(exception);
        if (exception != null) {
            this.exception = exception;
        } else {
            // Do not throw, we handle this any way.
            this.exception = new NullPointerException("Reported exception is null.");
        }
        addSection(description);
    }

    private void addSection(String description) {
        this.sections.add(new CrashReportSection(description));
    }

    private CrashReportInfoMap getInfo() {
        assert !this.sections.isEmpty();
        return this.sections.getLast().getInfo();
    }

    /**
     * Writes information about this exception to a writer.
     * @param out The {@link PrintWriter} to write to.
     */
    public void writeTo(PrintWriter out) {
        out.println("----- Debug data -----");
        for (CrashReportSection section : sections) {
            out.println(section.getDescription() + ":");
            CrashReportInfoMap info = section.getInfo();
            if (!info.isEmpty()) {
                info.write(out, 0);
            } else {
                out.println("No data has been collected.");
            }
            out.println();
        }
        out.println("----- System Status -----");
        out.println(ShowStatusReportAction.getReportHeader());
        out.println();
        out.println("----- Active Plugins -----");
        printActivePlugins(out);
        out.println();
        out.println("----- Stacktrace -----");
        getCause().printStackTrace(out);
    }

    /**
     * @see PluginHandler#getBugReportText()
     * @param out
     */
    private void printActivePlugins(PrintWriter out) {
        List<PluginProxy> badPlugins = getPluginsCausingException();
        for (PluginProxy plugin : PluginHandler.pluginList) {
            out.print(" - ");
            out.print(plugin.getPluginInformation().name);
            out.print(" by ");
            out.print(plugin.getPluginInformation().author);
            out.print(", version: ");
            out.print(plugin.getPluginInformation().version);

            int badPluginIndex = badPlugins.indexOf(plugin);
            if (badPluginIndex >= 0) {
                // Indicate that this plugin might be a cause for the error.
                out.print(" (cause #");
                out.print(badPluginIndex + 1);
                out.print(")");
            }
            out.println();
        }
    }

    /**
     * Add some information about the crash
     * @param key Any descriptive key.
     * @param value Any string value.
     * @return This crash report data instance for easy chaining.
     */
    public CrashReportData put(String key, Object value) {
        String string;
        try {
            string = value.toString();
        } catch (Throwable t) {
            string = "[" + t.getClass().getSimpleName() + " while converting to String]";
        }
        getInfo().put(key, string);
        return this;
    }

    public CrashReportData put(String key, OsmPrimitive osm) {
        getInfo().put(key, "TODO... " + osm.getId());
        //TODO
        return this;
    }

    /**
     * Displays this message to the user.
     */
    public void display() {
        DISPLAY_QUEUE.enqueue(this);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        // This makes debugging clearer.
        s.println("Wrapped by CrashReportData:");
        exception.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        // This makes debugging clearer.v
        s.println("Wrapped by CrashReportData:");
        exception.printStackTrace(s);
    }

    /**
     * Replies the plugin which most likely threw the exception <code>ex</code>.
     * <p>
     * Note: Moved from {@link PluginHandler}.
     *
     * @return the plugin; null, if the exception probably wasn't thrown from a plugin
     */
    public PluginProxy getPluginCausingException() {
        List<PluginProxy> plugins = getPluginsCausingException();
        if (!plugins.isEmpty()) {
            return plugins.get(0);
        } else {
            return null;
        }
    }

    /**
     * Gets all plugins involved in this exception
     * @return The plugins.
     */
    public List<PluginProxy> getPluginsCausingException() {
        // Check for an explicit problem when calling a plugin function
        ArrayList<PluginProxy> plugins = new ArrayList<>();

        if (exception instanceof PluginException) {
            PluginProxy plugin = ((PluginException) exception).plugin;
            if (plugin != null) {
                plugins.add(plugin);
            }
        }

        Throwable ex = exception;
        while (ex != null) {
            for (StackTraceElement stackEntry : ex.getStackTrace()) {
                PluginProxy plugin = PluginHandler.getPluginForClass(stackEntry.getClassName());
                if (plugin != null && !plugins.contains(plugin)) {
                    plugins.add(plugin);
                }
            }
            ex = ex.getCause();
        }
        return plugins;
    }

    /**
     * Checks if the exception is considered to be the same. This allows us to ignore a specific exception.
     * <p>
     * For now, two exceptions are the same if their stack traces and their message are equal.
     * @param e The data to compare to.
     * @return <code>true</code> if they are the same.
     */
    public boolean isSame(CrashReportData e) {
        if (!getMessage().equals(e.getMessage())) {
            return false;
        }

        Set<Throwable> dejaVu = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        return hasSameStackTrace(dejaVu, this.exception, e.exception);
    }

    private boolean hasSameStackTrace(Set<Throwable> dejaVu, Throwable e1, Throwable e2) {
        if (dejaVu.contains(e1)) {
            // cycle. If it was the same until here, we hope both have that cycle.
            return true;
        }
        dejaVu.add(e1);

        StackTraceElement[] t1 = e1.getStackTrace();
        StackTraceElement[] t2 = e2.getStackTrace();

        if (!Arrays.equals(t1, t2)) {
            return false;
        }

        Throwable c1 = e1.getCause();
        Throwable c2 = e2.getCause();
        if ((c1 == null) != (c2 == null)) {
            return false;
        } else if (c1 != null) {
            return hasSameStackTrace(dejaVu, c1, c2);
        } else {
            return true;
        }
    }

    /**
     * Creates a new crash report
     * @param exception The exception to report
     * @param description The description of where it occurred.
     * @return The crash data to re-throw or display.
     */
    public static CrashReportData create(Throwable exception, String description) {
        CrashReportData reportData;
        if (exception instanceof CrashReportData) {
            reportData = (CrashReportData) exception;
            reportData.addSection(description);
        } else {
            reportData = new CrashReportData(exception, description);
        }
        return reportData;
    }

    private static class CrashReportSection {
        private final String description;
        private final CrashReportInfoMap info = new CrashReportInfoMap();

        CrashReportSection(String description) {
            super();
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public CrashReportInfoMap getInfo() {
            return info;
        }

        @Override
        public String toString() {
            return "CrashReportSection [description=" + description + ", info=" + info + "]";
        }
    }
}
