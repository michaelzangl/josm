// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.bugreport;

/**
 * This class contains utility methods to create and handle a bug report.
 * <p>
 * It allows you to configure the format and request to send the bug report.
 * <p>
 * You should use this after catching an exception. Use the {@link #intercept(Throwable)} method on it.
 * <h1> Catching Exceptions </h1>
 * In your code, you should add try...catch blocks for any runtime exceptions that might happen. It is fine to catch throwable there.
 * <p>
 * You should then add some debug information there. This can be the OSM ids that caused the error, information on the data you were working on or other local variables. Make sure that no excpetions may occur while computing the values. It is best to send plain local variables to put(...). Then simply throw the throwable you got from the bug report. The global exception handler will do the rest.
 * <pre>
 * int id = ...;
 * String tag = "...";
 * try {
 *   ... your code ...
 * } catch (Throwable t) {
 *   throw BugReport.intercept(t).put("id", id).put("tag", tag);
 * }
 * </pre>
 *
 * @author Michael Zangl
 * @since xxx
 */
public class BugReport {
    /**
     * Create a new bug report
     * @param e The {@link ReportingException} to use. No more data should be added after creating the report.
     */
    public BugReport(ReportingException e) {
        // TODO...
    }

    /**
     * This should be called whenever you want to add more information to a given exception.
     * @param t The throwable that was thrown.
     * @return A {@link ReportingException} to which you can add additional information.
     */
    public static ReportingException intercept(Throwable t) {
        ReportingException e;
        if (t instanceof ReportingException) {
            e = (ReportingException) t;
        } else {
            e = new ReportingException(t);
        }
        e.startSection(getCallingMethod(2));
        return e;
    }

    /**
     * Find the method that called us.
     *
     * @param offset
     *            How many methods to look back in the stack trace. 1 gives the method calling this method, 0 gives you getCallingMethod().
     * @return The method name.
     */
    static String getCallingMethod(int offset) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = BugReport.class.getName();
        for (int i = 0; i < stackTrace.length - offset; i++) {
            StackTraceElement element = stackTrace[i];
            if (className.equals(element.getClassName()) && "getCallingMethod".equals(element.getMethodName())) {
                StackTraceElement toReturn = stackTrace[i + offset];
                return toReturn.getClassName().replaceFirst(".*\\.", "") + "#" + toReturn.getMethodName();
            }
        }
        return "?";
    }

}
