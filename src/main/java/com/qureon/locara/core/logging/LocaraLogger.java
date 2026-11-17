package com.qureon.locara.core.logging;

import java.util.Locale;

/**
 * Lightweight zero-dependency logger configurable via {@code locara.log.level}
 * or {@code LOCARA_LOG_LEVEL} ({@code SILENT}, {@code ERROR}, {@code WARN}, {@code INFO}, {@code DEBUG}).
 */
public final class LocaraLogger {

    public enum Level {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3),
        SILENT(4);

        private final int priority;

        Level(int priority) {
            this.priority = priority;
        }
    }

    private static volatile Level activeLevel = resolveInitialLevel();

    private LocaraLogger() {}

    public static void setLevel(Level level) {
        if (level != null) {
            activeLevel = level;
        }
    }

    public static Level getLevel() {
        return activeLevel;
    }

    public static void debug(String prefix, String message) {
        log(Level.DEBUG, prefix, message, false);
    }

    public static void info(String prefix, String message) {
        log(Level.INFO, prefix, message, false);
    }

    public static void warn(String prefix, String message) {
        log(Level.WARN, prefix, message, false);
    }

    public static void error(String prefix, String message) {
        log(Level.ERROR, prefix, message, true);
    }

    private static void log(Level level, String prefix, String message, boolean stderr) {
        if (level.priority < activeLevel.priority || activeLevel == Level.SILENT) {
            return;
        }
        String tag = (prefix != null && !prefix.isEmpty()) ? prefix : "[Locara]";
        String line = tag + " " + (message != null ? message : "");
        if (stderr) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
    }

    private static Level resolveInitialLevel() {
        String configured = System.getProperty("locara.log.level", System.getenv("LOCARA_LOG_LEVEL"));
        if (configured == null || configured.trim().isEmpty()) {
            return Level.INFO;
        }
        try {
            return Level.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Level.INFO;
        }
    }
}
