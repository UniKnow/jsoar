package org.jsoar.kernel;

import com.google.common.base.Joiner;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogManager {

  private final Agent agent;

  private EchoMode echoMode = EchoMode.on;

  private boolean active = true;
  private boolean strict = false;
  private boolean abbreviate = true;
  private SourceLocationMethod sourceLocationMethod = SourceLocationMethod.disk;
  private LogLevel currentLogLevel = LogLevel.info;
  private final Map<String, Logger> loggers = new HashMap<>();
  private final Set<String> disabledLoggers = new HashSet<>();

  // private RhsFunctionHandler handler = null;

  private static final SimpleDateFormat timestampFormatter =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  public class LoggerException extends Exception {

    private static final long serialVersionUID = 1L;

    public LoggerException(String message) {
      super(message);
    }
  }

  public enum SourceLocationMethod {
    none,
    disk,
    stack;

    public static SourceLocationMethod fromString(@NonNull String sourceLocationMethod) {
      return SourceLocationMethod.valueOf(sourceLocationMethod.toLowerCase());    }

    @Override
    public String toString() {
      return name();
    }
  }

  public enum LogLevel {
    trace(1),
    debug(2),
    info(3),
    warn(4),
    error(5);

    private final int numericValue;

    LogLevel(int numericValue) {
      this.numericValue = numericValue;
    }

    public static LogLevel fromString(@NonNull String logLevel) {
      return LogLevel.valueOf(logLevel.toLowerCase());
    }

    @Override
    public String toString() {
      return name();
    }

    public boolean wouldAcceptLogLevel(LogLevel logLevel) {
      return logLevel.numericValue >= this.numericValue;
    }
  }

  public enum EchoMode {
    off,
    simple,
    on;

    public static EchoMode fromString(@NonNull String echoMode) {
      return EchoMode.valueOf(echoMode.toLowerCase());
    }

    @Override
    public String toString() {
      return name();
    }
  }

  public LogManager(Agent agent) {
    this.agent = agent;
    init();
  }

  public void init() {
    loggers.clear();
    disabledLoggers.clear();
    loggers.put("default", LoggerFactory.getLogger("default"));
  }

  public Logger getLogger(@NonNull String loggerName) throws LoggerException {
    var logger = loggers.get(loggerName);
    if (logger == null) {
      if (strict) {
        throw new LoggerException(
            "Logger [" + loggerName + "] does not exist (strict mode enabled).");
      }
      logger = LoggerFactory.getLogger(loggerName);
      loggers.put(loggerName, logger);
    }
    return logger;
  }

  public Set<String> getLoggerNames() {
    return new HashSet<>(loggers.keySet());
  }

  public int getLoggerCount() {
    return loggers.size();
  }

  public Logger addLogger(String loggerName) throws LoggerException {
    var logger = loggers.get(loggerName);
    if (logger != null) {
      if (strict) {
        throw new LoggerException(
            "Logger [" + loggerName + "] already exists (strict mode enabled).");
      }
    } else {
      logger = LoggerFactory.getLogger(loggerName);
      loggers.put(loggerName, logger);
    }
    return logger;
  }

  public boolean hasLogger(String loggerName) {
    return loggers.containsKey(loggerName);
  }

  public String getLoggerStatus() {
    var result = new StringBuilder();
    result.append("      Log Settings     \n");
    result.append("=======================\n");
    result.append("logging:           " + (isActive() ? "on" : "off") + "\n");
    result.append("strict:            " + (isStrict() ? "on" : "off") + "\n");
    result.append("echo mode:         " + getEchoMode().toString().toLowerCase() + "\n");
    result.append("log level:         " + getLogLevel().toString().toLowerCase() + "\n");
    result.append(
        "source location:   " + getSourceLocationMethod().toString().toLowerCase() + "\n");
    result.append("abbreviate:        " + (getAbbreviate() ? "yes" : "no") + "\n");
    result.append("number of loggers: " + loggers.size() + "\n");
    result.append("------- Loggers -------\n");

    List<String> loggerList = new ArrayList<>(getLoggerNames());
    Collections.sort(loggerList);
    for (String loggerName : loggerList) {
      result.append((disabledLoggers.contains(loggerName) ? "*" : " ") + " " + loggerName + "\n");
    }

    return result.toString();
  }

  public void log(String loggerName, LogLevel logLevel, List<String> args, boolean collapse)
      throws LoggerException {
    if (!isActive()) {
      return;
    }

    var logger = getLogger(loggerName);

    String result = formatArguments(args, collapse);

    switch (logLevel) {
      case trace:
        logger.trace(result);
        break;
      case debug:
        logger.debug(result);
        break;
      case info:
        logger.info(result);
        break;
      case warn:
        logger.warn(result);
        break;
      default:
        logger.error(result);
    }

    if (echoMode != EchoMode.off
        && currentLogLevel.wouldAcceptLogLevel(logLevel)
        && !disabledLoggers.contains(loggerName)) {
      agent.getPrinter().startNewLine();

      if (echoMode == EchoMode.simple) {
        agent.getPrinter().print(result);
      } else {
        agent
            .getPrinter()
            .print("[" + logLevel + " " + getTimestamp() + "] " + loggerName + ": " + result);
      }

      agent.getPrinter().flush();
    }
  }

  private String formatArguments(List<String> args, boolean collapse) {
    if (args.size() > 1) {
      var formatString = args.get(0);
      if (formatString.contains("{}")) {
        int numFields = (formatString.length() - formatString.replace("{}", "").length()) / 2;
        if (numFields == args.size() - 1) {
          return String.format(
              formatString.replace("{}", "%s"),
              args.subList(1, args.size()).toArray(new Object[args.size() - 1]));
        }
      }
    }

    return Joiner.on(collapse ? "" : " ").join(args);
  }

  public static String getTimestamp() {
    return timestampFormatter.format(new Date(System.currentTimeMillis()));
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public EchoMode getEchoMode() {
    return echoMode;
  }

  public void setEchoMode(EchoMode echoMode) {
    this.echoMode = echoMode;
  }

  public void setLogLevel(LogLevel logLevel) {
    currentLogLevel = logLevel;
  }

  public LogLevel getLogLevel() {
    return currentLogLevel;
  }

  public void setSourceLocationMethod(SourceLocationMethod sourceLocationMethod) {
    this.sourceLocationMethod = sourceLocationMethod;
  }

  public SourceLocationMethod getSourceLocationMethod() {
    return sourceLocationMethod;
  }

  public boolean isDisabledLogger(String name) {
    return disabledLoggers.contains(name);
  }

  public void enableLogger(String name) throws LoggerException {
    getLogger(name);
    if (isStrict() && !disabledLoggers.contains(name)) {
      throw new LoggerException("Logger is not currently disabled (strict mode enabled).");
    }
    disabledLoggers.remove(name);
  }

  public void disableLogger(String name) throws LoggerException {
    getLogger(name);
    if (isStrict() && disabledLoggers.contains(name)) {
      throw new LoggerException("Logger is already disabled (strict mode enabled).");
    }
    disabledLoggers.add(name);
  }

  public void setAbbreviate(boolean abbreviate) {
    this.abbreviate = abbreviate;
  }

  public boolean getAbbreviate() {
    return abbreviate;
  }
}
