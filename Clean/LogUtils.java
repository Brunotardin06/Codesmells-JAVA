package com.blankj.log;

import android.util.Log;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Facade for logging, delegates to console and file printers, with pluggable formatters.
 */
public final class LogUtils {
    private LogUtils() { /* no instances */ }

    private static final Config CONFIG = new Config.Builder().build();
    private static final BodyFormatter DEFAULT_FORMATTER = new DefaultFormatter();
    private static final BodyFormatter JSON_FORMATTER    = new JsonFormatter();
    private static final BodyFormatter XML_FORMATTER     = new XmlFormatter();
    private static final LogPrinter CONSOLE_PRINTER      = new ConsolePrinter();
    private static final LogPrinter FILE_PRINTER         = new FilePrinter(CONFIG);

    public static void v(@NonNull String tag, Object... msgs) { log(Level.VERBOSE, tag, msgs); }
    public static void d(@NonNull String tag, Object... msgs) { log(Level.DEBUG,   tag, msgs); }
    public static void i(@NonNull String tag, Object... msgs) { log(Level.INFO,    tag, msgs); }
    public static void w(@NonNull String tag, Object... msgs) { log(Level.WARN,    tag, msgs); }
    public static void e(@NonNull String tag, Object... msgs) { log(Level.ERROR,   tag, msgs); }
    public static void json(@NonNull String tag, Object data) { log(Level.JSON, tag, data); }
    public static void xml(@NonNull String tag, String xml) { log(Level.XML, tag, xml); }

    private static void log(Level level, String tag, Object... msgs) {
        if (!CONFIG.isEnabled() || level.ordinal() < CONFIG.getConsoleLevel().ordinal()) return;
        BodyFormatter fmt = selectFormatter(level);
        String content = fmt.format(msgs);
        // Console
        CONSOLE_PRINTER.print(new LogMessage(level, tag, content));
        // File
        if (CONFIG.isFileLoggingEnabled() && level.ordinal() >= CONFIG.getFileLevel().ordinal()) {
            FILE_PRINTER.print(new LogMessage(level, tag, content));
        }
    }

    private static BodyFormatter selectFormatter(Level level) {
        switch (level) {
            case JSON: return JSON_FORMATTER;
            case XML:  return XML_FORMATTER;
            default:   return DEFAULT_FORMATTER;
        }
    }

    // -- Supporting types --

    public enum Level { VERBOSE, DEBUG, INFO, WARN, ERROR, JSON, XML }

    public static final class LogMessage {
        public final Level level;
        public final String tag;
        public final String message;
        public final long timestamp = System.currentTimeMillis();

        public LogMessage(Level lvl, String t, String msg) {
            level = lvl; tag = t; message = msg;
        }
    }

    public interface BodyFormatter {
        String format(Object... parts);
    }

    public static class DefaultFormatter implements BodyFormatter {
        @Override public String format(Object... parts) {
            if (parts == null || parts.length == 0) return "";
            if (parts.length == 1) return String.valueOf(parts[0]);
            StringBuilder sb = new StringBuilder();
            Arrays.stream(parts).forEach(p -> sb.append(p).append(" "));
            return sb.toString().trim();
        }
    }

    public static class JsonFormatter implements BodyFormatter {
        @Override public String format(Object... parts) {
            // simplistic: single argument only
            Object obj = parts.length > 0 ? parts[0] : null;
            return obj == null ? "null" : UtilsBridge.getGson().toJson(obj);
        }
    }

    public static class XmlFormatter implements BodyFormatter {
        @Override public String format(Object... parts) {
            Object obj = parts.length > 0 ? parts[0] : null;
            return obj == null ? "null" : XmlUtils.prettyXml(obj.toString());
        }
    }

    public interface LogPrinter {
        void print(@NonNull LogMessage msg);
    }

    public static class ConsolePrinter implements LogPrinter {
        @Override
        public void print(@NonNull LogMessage msg) {
            Log.println(
                mapLevel(msg.level),
                msg.tag,
                msg.message
            );
        }
        private int mapLevel(Level lvl) {
            switch (lvl) {
                case VERBOSE: return Log.VERBOSE;
                case DEBUG:   return Log.DEBUG;
                case INFO:    return Log.INFO;
                case WARN:    return Log.WARN;
                case ERROR:   return Log.ERROR;
                default:      return Log.DEBUG;
            }
        }
    }

    public static class FilePrinter implements LogPrinter {
        private final Config config;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

        public FilePrinter(Config cfg) { this.config = cfg; }

        @Override
        public void print(@NonNull LogMessage msg) {
            executor.execute(() -> {
                String date = sdf.format(new Date(msg.timestamp)).substring(0,10);
                String filePath = config.getLogDir() + date + config.getFileExtension();
                try {
                    File dir = new File(config.getLogDir());
                    if (!dir.exists() && !dir.mkdirs()) return;
                    try (FileWriter fw = new FileWriter(filePath, true)) {
                        fw.write(sdf.format(new Date(msg.timestamp))
                            + " " + msg.level + "/" + msg.tag
                            + " " + msg.message + System.lineSeparator());
                    }
                } catch (IOException ignored) {}
            });
        }
    }

    public static final class Config {
        private final boolean enabled;
        private final boolean fileLogging;
        private final Level consoleLevel;
        private final Level fileLevel;
        private final String logDir;
        private final String fileExtension;

        private Config(Builder b) {
            this.enabled      = b.enabled;
            this.fileLogging  = b.fileLogging;
            this.consoleLevel = b.consoleLevel;
            this.fileLevel    = b.fileLevel;
            this.logDir       = b.logDir;
            this.fileExtension= b.fileExtension;
        }

        public boolean isEnabled()      { return enabled; }
        public boolean isFileLoggingEnabled() { return fileLogging; }
        public Level getConsoleLevel()  { return consoleLevel; }
        public Level getFileLevel()     { return fileLevel; }
        public String getLogDir()       { return logDir; }
        public String getFileExtension(){ return fileExtension; }

        public static class Builder {
            private boolean enabled = true;
            private boolean fileLogging = false;
            private Level consoleLevel = Level.VERBOSE;
            private Level fileLevel = Level.DEBUG;
            private String logDir = UtilsBridge.getApp().getFilesDir() + "/logs/";
            private String fileExtension = ".log";

            public Builder setEnabled(boolean en) { this.enabled = en; return this; }
            public Builder setFileLogging(boolean fl) { this.fileLogging = fl; return this; }
            public Builder setConsoleLevel(Level lvl) { this.consoleLevel = lvl; return this; }
            public Builder setFileLevel(Level lvl) { this.fileLevel = lvl; return this; }
            public Builder setLogDir(String dir) { this.logDir = dir.endsWith("/") ? dir : dir + "/"; return this; }
            public Builder setFileExtension(String ext) { this.fileExtension = ext.startsWith(".")? ext: "."+ext; return this; }
            public Config build() { return new Config(this); }
        }
    }
}
