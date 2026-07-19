package pt.isec.common.util;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logger with colored output according to module origin.
 * <p>
 * Colors for INFO messages:
 * <ul>
 *     <li>CLIENT    → Pink</li>
 *     <li>SERVER    → Blue</li>
 *     <li>DIRECTORY → Green</li>
 * </ul>
 * For WARN and ERROR messages the entire line is colored:
 * <ul>
 *     <li>WARN  → Yellow</li>
 *     <li>ERROR → Red</li>
 * </ul>
 *
 * Format:
 * <pre>
 *   [TIMESTAMP][LEVEL][ClassName] message
 * </pre>
 */
public final class Log {

    /* ===================== STATIC INITIALIZATION ===================== */

    // Initialize Jansi once (ignore if it fails)
    static {
        try {
            AnsiConsole.systemInstall();
        } catch (Exception ignored) {
        }
    }

    /**
     * Time formatter used for log timestamps.
     * Example output: 14:32:11
     */
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Utility class – no instances allowed.
     */
    private Log() {
        // static utility
    }

    /* ===================== INFO methods ===================== */

    /**
     * Logs an informational message.
     *
     * @param source  source class
     * @param message message text
     */
    public static void info(Class<?> source, String message) {
        log("INFO", source, message, null);
    }

    /**
     * Logs a formatted informational message.
     *
     * @param source source class
     * @param format message format compatible with {@link String#format(String, Object...)}
     * @param args   format arguments
     */
    public static void info(Class<?> source, String format, Object... args) {
        String msg = cleanFormat(format, args);
        log("INFO", source, msg, null);
    }

    /**
     * Logs a formatted informational message highlighting master/primary-related
     * events using a cyan color for the entire line.
     *
     * @param source source class
     * @param format message format compatible with {@link String#format(String, Object...)}
     * @param args   format arguments
     */
    public static void infoMaster(Class<?> source, String format, Object... args) {
        String msg = cleanFormat(format, args);
        logMaster("INFO", source, msg, null);
    }

    /* ===================== WARN methods ===================== */

    /**
     * Logs a warning message.
     *
     * @param source  source class
     * @param message message text
     */
    public static void warn(Class<?> source, String message) {
        log("WARN", source, message, null);
    }

    /**
     * Logs a formatted warning message.
     *
     * @param source source class
     * @param format message format compatible with {@link String#format(String, Object...)}
     * @param args   format arguments
     */
    public static void warn(Class<?> source, String format, Object... args) {
        String msg = cleanFormat(format, args);
        log("WARN", source, msg, null);
    }

    /* ===================== ERROR methods ===================== */

    /**
     * Logs an error message.
     *
     * @param source  source class
     * @param message message text
     */
    public static void error(Class<?> source, String message) {
        log("ERROR", source, message, null);
    }

    /**
     * Logs a formatted error message.
     *
     * @param source source class
     * @param format message format compatible with {@link String#format(String, Object...)}
     * @param args   format arguments
     */
    public static void error(Class<?> source, String format, Object... args) {
        String msg = cleanFormat(format, args);
        log("ERROR", source, msg, null);
    }

    /**
     * Logs an error message with a throwable.
     *
     * @param source  source class
     * @param message message text
     * @param t       throwable to log
     */
    public static void error(Class<?> source, String message, Throwable t) {
        log("ERROR", source, message, t);
    }

    /* ===================== Formatting helper ===================== */

    /**
     * Applies {@link String#format(String, Object...)} and trims a trailing
     * platform-specific line separator, if present.
     *
     * @param format format string
     * @param args   arguments referenced by the format specifiers
     * @return formatted string without a trailing line separator
     */
    private static String cleanFormat(String format, Object... args) {
        String msg = String.format(format, args);
        String nl = System.lineSeparator();
        if (msg.endsWith(nl)) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        return msg;
    }

    /* ===================== Core logging ===================== */

    /**
     * Core logging implementation used by all public helpers.
     * <p>
     * Decides the color based on the log level and source package and prints
     * the message to {@link System#out} or {@link System#err}.
     * <p>
     * The log line is prefixed with a timestamp in the format
     * {@code [yyyy-MM-dd HH:mm:ss]}.
     *
     * @param level   log level: {@code "INFO"}, {@code "WARN"} or {@code "ERROR"}
     * @param source  source class (may be {@code null})
     * @param message plain message text (no trailing newline)
     * @param t       optional throwable to print (may be {@code null})
     */
    private static void log(String level,
                            Class<?> source,
                            String message,
                            Throwable t) {

        String timestamp = LocalDateTime.now().format(DATE_TIME_FMT);
        String className = (source != null ? source.getSimpleName() : "UNKNOWN");
        String plainPrefix = "[" + timestamp + "][" + level + "][" + className + "] ";
        String plainLine = plainPrefix + message;

        PrintStream ps = "ERROR".equals(level) ? System.err : System.out;

        String toPrint;

        try {
            if ("ERROR".equals(level)) {
                toPrint = Ansi.ansi()
                        .fgBrightRed()
                        .a(plainLine)
                        .reset()
                        .toString();
            } else if ("WARN".equals(level)) {
                toPrint = Ansi.ansi()
                        .fgBrightYellow()
                        .a(plainLine)
                        .reset()
                        .toString();
            } else {
                String coloredPrefix = plainPrefix;
                int[] rgb = resolveColor(source);
                if (rgb != null) {
                    coloredPrefix = Ansi.ansi()
                            .fgRgb(rgb[0], rgb[1], rgb[2])
                            .a(plainPrefix)
                            .reset()
                            .toString();
                }
                toPrint = coloredPrefix + message;
            }
        } catch (Throwable ignored) {
            toPrint = plainLine;
        }

        ps.println(toPrint);

        if (t != null) {
            t.printStackTrace(ps);
        }
    }

    /**
     * Specialized logging implementation used to highlight master/primary-related
     * events. The entire line is printed in cyan.
     *
     * @param level   log level (normally {@code "INFO"})
     * @param source  source class (may be {@code null})
     * @param message plain message text
     * @param t       optional throwable
     */
    private static void logMaster(String level,
                                  Class<?> source,
                                  String message,
                                  Throwable t) {

        String timestamp = LocalDateTime.now().format(DATE_TIME_FMT);
        String className = (source != null ? source.getSimpleName() : "UNKNOWN");
        String plainPrefix = "[" + timestamp + "][" + level + "][" + className + "] ";
        String plainLine = plainPrefix + message;

        PrintStream ps = "ERROR".equals(level) ? System.err : System.out;

        String toPrint;
        try {
            toPrint = Ansi.ansi()
                    .fgBrightCyan()
                    .a(plainLine)
                    .reset()
                    .toString();
        } catch (Throwable ignored) {
            toPrint = plainLine;
        }

        ps.println(toPrint);

        if (t != null) {
            t.printStackTrace(ps);
        }
    }

    /* ===================== Color resolution ===================== */

    /**
     * Selects RGB color based on package for INFO messages.
     *
     * @param source source class
     * @return array {@code {r,g,b}} or {@code null} (no color)
     */
    private static int[] resolveColor(Class<?> source) {
        if (source == null) {
            return null;
        }

        String pkg = source.getPackageName();

        if (pkg.startsWith("pt.isec.client")) {
            // Pink (HotPink)
            return new int[]{255, 105, 180};
        }

        if (pkg.startsWith("pt.isec.server")) {
            // Blue (DodgerBlue)
            return new int[]{30, 144, 255};
        }

        if (pkg.startsWith("pt.isec.directory")) {
            // Green
            return new int[]{0, 255, 0};
        }

        // Other packages: no color
        return null;
    }
}
