// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Subclasses of this class are used to keep track of crash reports.
 *
 * @author Michael Zangl
 *
 */
public abstract class CrashReportInfo {

    public static class CrashReportInfoMap extends CrashReportInfo {
        private final HashMap<String, CrashReportInfo> items = new HashMap<>();

        @Override
        public void write(PrintWriter w, int indent) {
            ArrayList<String> keys = new ArrayList<>(items.keySet());
            Collections.sort(keys);
            for (String k : keys) {
                writeIndent(w, indent);
                w.append(k);
                w.append(": ");
                items.get(k).write(w, indent + 2);
                w.append("\n");
            }
        }

        public void put(String key, String value) {
            items.put(key, new CrashReportInfoString(value));
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    public static class CrashReportInfoString extends CrashReportInfo {
        private final String string;

        public CrashReportInfoString(String string) {
            super();
            this.string = string;
        }

        @Override
        public void write(PrintWriter w, int indent) {
            if (!string.contains("\n")) {
                w.append(string);
            } else {
                for (String line : string.split("\n")) {
                    w.append("\n");
                    writeIndent(w, indent);
                    w.append(line);
                }
            }
        }

        @Override
        public String toString() {
            return "CrashReportInfoString [string=" + string + "]";
        }

    }

    /**
     * Writes the crash report info.
     * @param w The writer to use.
     * @param indent How many spaces to indent each line.
     */
    public abstract void write(PrintWriter w, int indent);

    protected static void writeIndent(PrintWriter w, int indent) {
        for (int i = 0; i < indent; i++) {
            w.append(' ');
        }
    }
}
