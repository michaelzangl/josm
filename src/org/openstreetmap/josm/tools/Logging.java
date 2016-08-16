// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * This class contains utility methods to log errors and warnings.
 * <p>
 * There are multiple log levels supported.
 * @author Michael Zangl
 * @since xxx
 */
public final class Logging {
    /**
     * The josm internal log level indicating a severe error in the application that usually leads to a crash.
     */
    public static final Level LEVEL_ERROR = Level.SEVERE;
    /**
     * The josm internal log level to use when something that may lead to a crash or wrong behaviour has happened.
     */
    public static final Level LEVEL_WARN = Level.WARNING;
    /**
     * The josm internal log level to use for important events that will be useful when debugging problems
     */
    public static final Level LEVEL_INFO = Level.INFO;
    /**
     * The josm internal log level to print debug output
     */
    public static final Level LEVEL_DEBUG = Level.FINE;
    /**
     * The finest log level josm supports. This lets josm print a lot of debug output.
     */
    public static final Level LEVEL_TRACE = Level.FINEST;
    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static final RememberWarningHandler WARNINGS = new RememberWarningHandler();

    static {
        LOGGER.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(false);

        ConsoleHandler stderr = new ConsoleHandler();
        LOGGER.addHandler(stderr);
        stderr.setLevel(LEVEL_WARN);

        ConsoleHandler stdout = new ConsoleHandler() {
            @Override
            protected synchronized void setOutputStream(OutputStream out) {
                // overwrite output stream.
                super.setOutputStream(System.out);
            }

            @Override
            public void publish(LogRecord record) {
                if (!stderr.isLoggable(record)) {
                    super.publish(record);
                }
            }
        };
        LOGGER.addHandler(stdout);
        stdout.setLevel(Level.ALL);

        LOGGER.addHandler(WARNINGS);
    }

    private Logging() {
        // hide
    }

    /**
     * Set the global log level.
     * @param level The log level to use
     */
    public static void setLogLevel(Level level) {
        LOGGER.setLevel(level);
    }

    /**
     * Prints an error message if logging is on.
     * @param message The message to print.
     */
    public static void error(String message) {
        logPrivate(LEVEL_ERROR, message);
    }

    /**
     * Prints a formatted error message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param pattern The formatted message to print.
     * @param args The objects to insert into format string.
     */
    public static void error(String pattern, Object... args) {
        logPrivate(LEVEL_ERROR, pattern, args);
    }

    /**
     * Prints a warning message if logging is on.
     * @param message The message to print.
     */
    public static void warn(String message) {
        logPrivate(LEVEL_WARN, message);
    }

    /**
     * Prints a formatted warning message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param pattern The formatted message to print.
     * @param args The objects to insert into format string.
     */
    public static void warn(String pattern, Object... args) {
        logPrivate(LEVEL_WARN, pattern, args);
    }

    /**
     * Prints a info message if logging is on.
     * @param message The message to print.
     */
    public static void info(String message) {
        logPrivate(LEVEL_INFO, message);
    }

    /**
     * Prints a formatted info message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param pattern The formatted message to print.
     * @param args The objects to insert into format string.
     */
    public static void info(String pattern, Object... args) {
        logPrivate(LEVEL_INFO, pattern, args);
    }

    /**
     * Prints a debug message if logging is on.
     * @param message The message to print.
     */
    public static void debug(String message) {
        logPrivate(LEVEL_DEBUG, message);
    }

    /**
     * Prints a formatted debug message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param pattern The formatted message to print.
     * @param args The objects to insert into format string.
     */
    public static void debug(String pattern, Object... args) {
        logPrivate(LEVEL_DEBUG, pattern, args);
    }

    /**
     * Prints a trace message if logging is on.
     * @param message The message to print.
     */
    public static void trace(String message) {
        logPrivate(LEVEL_TRACE, message);
    }

    /**
     * Prints a formatted trace message if logging is on. Calls {@link MessageFormat#format}
     * function to format text.
     * @param pattern The formatted message to print.
     * @param args The objects to insert into format string.
     */
    public static void trace(String pattern, Object... args) {
        logPrivate(LEVEL_TRACE, pattern, args);
    }

    /**
     * Logs a throwable that happened.
     * @param level The level.
     * @param t The throwable that should be logged.
     */
    public static void log(Level level, Throwable t) {
        logPrivate(level, () -> getErrorLog(null, t));
    }

    /**
     * Logs a throwable that happened.
     * @param level The level.
     * @param message An additional error message
     * @param t The throwable that caused the message
     */
    public static void log(Level level, String message, Throwable t) {
        logPrivate(level, () -> getErrorLog(message, t));
    }

    /**
     * Logs a throwable that happened. Adds the stack trace to the log.
     * @param level The level.
     * @param t The throwable that should be logged.
     */
    public static void logWithStackTrace(Level level, Throwable t) {
        logPrivate(level, () -> getErrorLogWithStack(null, t));
    }

    /**
     * Logs a throwable that happened. Adds the stack trace to the log.
     * @param level The level.
     * @param message An additional error message
     * @param t The throwable that should be logged.
     */
    public static void logWithStackTrace(Level level, String message, Throwable t) {
        logPrivate(level, () -> getErrorLogWithStack(message, t));
    }

    private static void logPrivate(Level level, String pattern, Object... args) {
        logPrivate(level, () -> MessageFormat.format(pattern, args));
    }

    private static void logPrivate(Level level, String message) {
        logPrivate(level, () -> message);
    }

    private static void logPrivate(Level level, Supplier<String> supplier) {
        // all log methods immeadiately call one of the logPrivate methods.
        if (LOGGER.isLoggable(level)) {
            StackTraceElement callingMethod = BugReport.getCallingMethod(1, Logging.class.getName(), name -> !"logPrivate".equals(name));
            LOGGER.logp(level, callingMethod.getClassName(), callingMethod.getMethodName(), supplier);
        }
    }

    /**
     * Tests if a given log level is enabled. This can be used to avoid constructing debug data if required.
     *
     * For formatting text, you should use the {@link #debug(String, Object...)} message
     * @param level A lvele constant. You can e.g. use {@link Logging#LEVEL_ERROR}
     * @return <code>true</code> if debug is enabled.
     */
    public static boolean isLoggingEnabled(Level level) {
        return LOGGER.isLoggable(level);
    }

    private static String getErrorLog(String message, Throwable t) {
        StringBuilder sb = new StringBuilder();
        if (message != null) {
            sb.append(message).append(": ");
        }
        sb.append(getErrorMessage(t));
        return sb.toString();
    }

    private static String getErrorLogWithStack(String message, Throwable t) {
        StringWriter sb = new StringWriter();
        sb.append(getErrorLog(message, t));
        sb.append('\n');
        t.printStackTrace(new PrintWriter(sb));
        return sb.toString();
    }

    /**
     * Returns a human-readable message of error, also usable for developers.
     * @param t The error
     * @return The human-readable error message
     */
    public static String getErrorMessage(Throwable t) {
        if (t == null) {
            return "(no error)";
        }
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        String msg = t.getMessage();
        if (msg != null) {
            sb.append(": ").append(msg.trim());
        }
        Throwable cause = t.getCause();
        if (cause != null && !cause.equals(t)) {
            // this may cause infinite loops in the unlikely case that there is a loop in the causes.
            sb.append(". ").append(tr("Cause: ")).append(getErrorMessage(cause));
        }
        return sb.toString();
    }

    /**
     * Clear the list of last warnings
     */
    public static void clearLastErrorAndWarnings() {
        WARNINGS.clear();
    }

    /**
     * Get the last error and warning messages in the order in which they were received.
     * @return The last errors and warnings.
     */
    public static List<String> getLastErrorAndWarnings() {
        return WARNINGS.getMessages();
    }

    /**
     * Provides direct access to the logger used. Use of methods like {@link #warn(String)} is prefered.
     * @return The logger
     */
    public static Logger getLogger() {
        return LOGGER;
    }

    private static class RememberWarningHandler extends Handler {
        private final String[] log = new String[10];
        private int messagesLogged;

        RememberWarningHandler() {
            setLevel(LEVEL_WARN);
        }

        synchronized void clear() {
            messagesLogged = 0;
            Arrays.fill(log, null);
        }

        @Override
        public synchronized void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }

            String msg = getPrefix(record) + record.getMessage();

            // Only remember first line of message
            int idx = msg.indexOf('\n');
            if (idx > 0) {
                msg = msg.substring(0, idx);
            }
            log[messagesLogged % log.length] = msg;
            messagesLogged++;
        }

        private static String getPrefix(LogRecord record) {
            if (record.getLevel().equals(LEVEL_WARN)) {
                return "W: ";
            } else {
                // worse than warn
                return "E: ";
            }
        }

        synchronized List<String> getMessages() {
            List<String> logged = Arrays.asList(log);
            ArrayList<String> res = new ArrayList<>();
            int logOffset = messagesLogged % log.length;
            if (messagesLogged > logOffset) {
                res.addAll(logged.subList(logOffset, log.length));
            }
            res.addAll(logged.subList(0, logOffset));
            return res;
        }

        @Override
        public synchronized void flush() {
            // nothing to do
        }

        @Override
        public void close() {
            // nothing to do
        }
    }
}
