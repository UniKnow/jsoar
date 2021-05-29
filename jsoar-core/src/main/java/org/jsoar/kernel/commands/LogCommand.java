package org.jsoar.kernel.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.Goal;
import org.jsoar.kernel.LogManager;
import org.jsoar.kernel.LogManager.EchoMode;
import org.jsoar.kernel.LogManager.LogLevel;
import org.jsoar.kernel.LogManager.LoggerException;
import org.jsoar.kernel.LogManager.SourceLocationMethod;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.util.DefaultSourceLocation;
import org.jsoar.util.commands.PicocliSoarCommand;
import org.jsoar.util.commands.SoarCommandContext;
import org.jsoar.util.commands.SoarCommandInterpreter;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * This is the implementation of the "log" command.
 *
 * @author austin.brehob
 */
public class LogCommand extends PicocliSoarCommand {

  public LogCommand(Agent agent, SoarCommandInterpreter interpreter) {
    super(agent, new Log(agent, interpreter, null));
  }

  @Override
  public String execute(SoarCommandContext context, String[] args) throws SoarException {
    final var log = ((Log) this.picocliCommand);
    log.context = context;
    return super.execute(context, args);
  }

  @Command(
      name = "log",
      description = "Adjusts logging settings",
      subcommands = {HelpCommand.class})
  public static class Log implements Runnable {

    private final Agent agent;
    private final LogManager logManager;
    private final SoarCommandInterpreter interpreter;
    private SoarCommandContext context;
    private static final String SOURCE_LOCATION_SEPARATOR = ".";

    /*
     * Contains list of actions that can be performed by this command
     */
    private static final List<Action<Log>> ACTIONS =
        Stream.of(
                new AddLogger(),
                new EnableLog(),
                new DisableLog(),
                new InitializeLogger(),
                new SetStrictMode(),
                new EnableAbbreviate(),
                new SetEchoMode(),
                new SetSourceLocationMethod(),
                new SetLogLevel(),
                new LogMessage())
            .collect(Collectors.toList());

    @Spec private CommandSpec spec; // injected by picocli

    public Log(Agent agent, SoarCommandInterpreter interpreter, SoarCommandContext context) {
      this.agent = agent;
      this.logManager = agent.getLogManager();
      this.interpreter = interpreter;
      this.context = context;

      //      // Initialize list of actions which could be performed by this command
      //      actions =
      //          Stream.of(
      //                  new AddLogger(),
      //                  new EnableLog(),
      //                  new DisableLog(),
      //                  new InitializeLogger(),
      //                  new SetStrictMode(),
      //                  new EnableAbbreviate(),
      //                  new SetEchoMode(),
      //                  new SetSourceLocationMethod(),
      //                  new SetLogLevel(),
      //                  new LogMessage())
      //              .collect(Collectors.toList());
    }

    @Option(
        names = {"-a", "--add"},
        description = "Adds a logger with the given name")
    String logToAdd;

    @Option(
        names = {"-e", "--on", "--enable", "--yes"},
        defaultValue = "false",
        description = "Enables logging")
    boolean enable;

    @Option(
        names = {"-d", "--off", "--disable", "--no"},
        defaultValue = "false",
        description = "Disables logging")
    boolean disable;

    @Option(
        names = {"-s", "--strict"},
        arity = "1",
        description = "Enables or disables logging strictness",
        converter = BooleanTypeConverter.class)
    Optional<Boolean> strict;

    @Option(
        names = {"-E", "--echo"},
        description = "Sets logger echo mode to on, simple, or off")
    Optional<EchoMode> echo;

    @Option(
        names = {"-i", "--init"},
        defaultValue = "false",
        description = "Re-initializes log manager")
    boolean init;

    @Option(
        names = {"-c", "--collapse"},
        defaultValue = "false",
        description = "Specifies collapsed logging")
    boolean collapse;

    @Option(
        names = {"-l", "--level"},
        description = "Sets the logging level to trace, debug, info, warn, or error")
    Optional<LogLevel> level;

    @Option(
        names = {"-S", "--source"},
        description = "Sets the logging source to disk, stack, or none")
    Optional<SourceLocationMethod> source;

    @Option(
        names = {"-A", "--abbreviate"},
        arity = "1",
        description = "Enables or disables logging abbreviation",
        converter = BooleanTypeConverter.class)
    Optional<Boolean> abbreviate;

    @Parameters(
        description =
            "The logger to enable/disable or send a message to, "
                + "the log level, and/or the message to log")
    String[] params;

    @Override
    public void run() {
      // Traverse list of actions until successfully handled
      for (Action<Log> action : ACTIONS) {
        if (action.execute(this)) {
          break;
        }
      }
    }

    public String getSourceLocation(
        SoarCommandContext context, boolean abbreviate, SourceLocationMethod sourceLocationMethod) {
      if (sourceLocationMethod.equals(SourceLocationMethod.stack)) {
        return getGoalStackLocation(abbreviate);
      } else if (sourceLocationMethod.equals(SourceLocationMethod.disk)) {
        return getSourceFileLocation(context, abbreviate);
      } else {
        return null;
      }
    }

    public String getGoalStackLocation(boolean abbreviate) {
      final var location = new StringBuilder();

      Iterator<Goal> it = agent.getGoalStack().iterator();
      if (it.hasNext()) {
        // location.append(getOperatorNameFromGoal(it.next()));
        String thisGoal = getOperatorNameFromGoal(it.next());
        if (!abbreviate || !it.hasNext()) {
          location.append(thisGoal);
        } else {
          location.append(thisGoal.charAt(0));
        }
        while (it.hasNext()) {
          location.append(SOURCE_LOCATION_SEPARATOR);
          // location.append(getOperatorNameFromGoal(it.next()));
          thisGoal = getOperatorNameFromGoal(it.next());
          if (!abbreviate || !it.hasNext()) {
            location.append(thisGoal);
          } else {
            location.append(thisGoal.charAt(0));
          }
        }
      }

      return location.toString();
    }

    public String getSourceFileLocation(SoarCommandContext context, boolean abbreviate) {
      var sourceLocation = context.getSourceLocation();
      if (sourceLocation != DefaultSourceLocation.UNKNOWN) {
        String fileName = sourceLocation.getFile();
        if (fileName != null && !fileName.isEmpty()) {
          return collapseFileName(fileName, interpreter.getWorkingDirectory(), abbreviate);
        }
      }
      return null;
    }

    private static String getOperatorNameFromGoal(Goal g) {
      Symbol opName = g.getOperatorName();
      return opName == null ? "?" : opName.toString();
    }

    public static List<String> uberSplit(String file) throws IOException {
      List<String> result = new ArrayList<>();

      var f = new File(file).getCanonicalFile();

      result.add(f.getName());
      f = f.getParentFile();
      while (f != null) {
        String n = f.getName();
        if (!n.isEmpty()) {
          result.add(f.getName());
        }
        f = f.getParentFile();
      }

      Collections.reverse(result);

      return result;
    }

    public static String collapseFileName(String file, String cwd, boolean abbreviate) {
      String[] cwdParts;
      String[] fileParts;

      try {
        cwdParts = uberSplit(cwd).toArray(new String[0]);
        fileParts = uberSplit(file).toArray(new String[0]);
      } catch (IOException e) {
        return null;
      }

      int minLength = Math.min(cwdParts.length, fileParts.length);

      int marker;
      for (marker = 0; marker < minLength; ++marker) {
        if (!cwdParts[marker].equals(fileParts[marker])) {
          break;
        }
      }

      var result = "";

      int diff = cwdParts.length - marker;
      if (diff > 0) {
        result += "^" + diff + SOURCE_LOCATION_SEPARATOR;
      }

      for (int i = marker; i < fileParts.length - 1; ++i) {
        if (abbreviate) {
          result += fileParts[i].charAt(0);
        } else {
          result += fileParts[i];
        }
        result += SOURCE_LOCATION_SEPARATOR;
      }
      result += fileParts[fileParts.length - 1];

      return result;
    }
  }

  /** Action to add specified logger. */
  private static class AddLogger implements Action<Log> {

    @Override
    public boolean execute(Log context) {
      if (context.logToAdd == null) {
        return false;
      }

      try {
        context.logManager.addLogger(context.logToAdd);
      } catch (LoggerException e) {
        throw new ParameterException(context.spec.commandLine(), e.getMessage(), e);
      }

      return true;
    }
  }

  /** Action to enable log. */
  private static class EnableLog implements Action<Log> {

    @Override
    public boolean execute(final Log context) {
      if (context.enable) {
        if (context.params == null) {
          if (context.logManager.isActive()) {
            context.agent.getPrinter().startNewLine().print("Logging already enabled.");
          } else {
            context.logManager.setActive(true);
            context.agent.getPrinter().startNewLine().print("Logging enabled.");
          }
        } else {
          try {
            String loggerName = context.params[0];
            context.logManager.enableLogger(loggerName);
            context.agent.getPrinter().startNewLine().print("Logger [{}] enabled.", loggerName);
          } catch (LoggerException e) {
            context.agent.getPrinter().startNewLine().print(e.getMessage());
          }
        }
        return true;
      }
      return false;
    }
  }

  /** Action to disable log. */
  private static class DisableLog implements Action<Log> {

    @Override
    public boolean execute(final Log context) {
      if (context.disable) {
        if (context.params == null) {
          if (!context.logManager.isActive()) {
            context.agent.getPrinter().startNewLine().print("Logging already disabled.");
          } else {
            context.logManager.setActive(false);
            context.agent.getPrinter().startNewLine().print("Logging disable.");
          }
        } else {
          try {
            String loggerName = context.params[0];
            context.logManager.disableLogger(loggerName);
            context.agent.getPrinter().startNewLine().print("Logger [{}] disabled.", loggerName);
          } catch (LoggerException e) {
            context.agent.getPrinter().startNewLine().print(e.getMessage());
          }
        }
        return true;
      }
      return false;
    }
  }

  /** Action to initialize logger. */
  private static class InitializeLogger implements Action<Log> {

    @Override
    public boolean execute(Log log) {
      var handled = false;
      if (log.init) {
        // log --init
        log.logManager.init();
        log.agent.getPrinter().startNewLine().print("Log manager re-initialized.");
        handled = true;
      }
      return handled;
    }
  }

  /** Action to set strict mode */
  private static class SetStrictMode implements Action<Log> {

    /**
     * Executes action
     *
     * @param context Context in which the action needs to be performed
     * @return true if action has executed; false otherwise
     */
    @Override
    public boolean execute(Log context) {
      var handled = false;
      if (context.strict.isPresent()) {
        boolean enabled = context.strict.get();
        String mode = enabled ? "strict" : "non-strict";

        if (context.logManager.isStrict() == enabled) {
          context.agent.getPrinter().startNewLine().print("Logger already in {} mode.", mode);
        } else {
          context.logManager.setStrict(enabled);
          context.agent.getPrinter().startNewLine().print("Logger set to {} mode.", mode);
        }

        handled = true;
      }
      return handled;
    }
  }

  /** Action to enable abbreviate */
  private static final class EnableAbbreviate implements Action<Log> {

    @Override
    public boolean execute(Log context) {
      var handled = false;

      if (context.abbreviate.isPresent()) {
        boolean enabled = context.abbreviate.get();
        context.logManager.setAbbreviate(enabled);
        context
            .agent
            .getPrinter()
            .startNewLine()
            .print("Logger using {} paths.", enabled ? "abbreviated" : "full");
        handled = true;
      }
      return handled;
    }
  }

  /** Action to set echo mode */
  private static final class SetEchoMode implements Action<Log> {

    @Override
    public boolean execute(Log context) {
      var handled = false;
      if (context.echo.isPresent()) {
        // log --echo on/simple/off
        var echoMode = context.echo.get();
        context.logManager.setEchoMode(echoMode);
        context.agent.getPrinter().startNewLine().print("Logger echo mode set to: " + echoMode);
        handled = true;
      }
      return handled;
    }
  }

  /** Action to set source location method */
  private static final class SetSourceLocationMethod implements Action<Log> {

    @Override
    public boolean execute(Log context) {
      var handled = false;
      if (context.source.isPresent()) {
        // log --source disk/stack/none
        var sourceLocationMethod = context.source.get();
        context.logManager.setSourceLocationMethod(sourceLocationMethod);
        context
            .agent
            .getPrinter()
            .startNewLine()
            .print("Logger source location " + "method set to: " + sourceLocationMethod);
        handled = true;
      }
      return handled;
    }
  }

  /** Action to set log level */
  private static final class SetLogLevel implements Action<Log> {

    @Override
    public boolean execute(Log context) {
      var handled = false;
      if (context.level.isPresent()) {
        // log --level trace/debug/info/warn/error
        var logLevel = context.level.get();
        context.logManager.setLogLevel(logLevel);
        context.agent.getPrinter().startNewLine().print("Logger level set to: " + logLevel);
        handled = true;
      }
      return handled;
    }
  }

  /** Action to log message */
  private static final class LogMessage implements Action<Log> {

    @Override
    public boolean execute(Log log) {
      if (log.params == null) {
        // log
        log.agent.getPrinter().startNewLine().print(log.logManager.getLoggerStatus());
      } else {
        // log <loggerName> <loggingLevel> <[message]>
        String loggerName;
        LogLevel logLevel;
        List<String> parameters;

        if (log.params.length == 1) {
          throw new ParameterException(log.spec.commandLine(), "Unknown command: " + log.params[0]);
        }

        try {
          // Did the user omit the LOGGER-NAME?
          // If so, the first argument will by the log level.
          // So let's try to cast the first argument to a log level.
          logLevel = LogManager.LogLevel.fromString(log.params[0]);

          // The user omitted LOGGER-NAME (we know because we just properly parsed the log level).
          loggerName =
              log.getSourceLocation(
                  log.context,
                  log.logManager.getAbbreviate(),
                  log.logManager.getSourceLocationMethod());
          if (loggerName != null) {
            // Prevent strict mode from biting us.
            if (!log.logManager.hasLogger(loggerName)) {
              try {
                log.logManager.addLogger(loggerName);
              } catch (LoggerException e) {
                throw new ParameterException(log.spec.commandLine(), e.getMessage(), e);
              }
            }
          }

          if (loggerName == null) {
            loggerName = "default";
          }

          parameters = Arrays.asList(Arrays.copyOfRange(log.params, 1, log.params.length));
        } catch (IllegalArgumentException e) {
          // The user specified LOGGER-NAME.
          loggerName = log.params[0];

          try {
            // Make sure that the log-level is valid.
            logLevel = LogManager.LogLevel.fromString(log.params[1]);
          } catch (IllegalArgumentException ee) {
            throw new ParameterException(
                log.spec.commandLine(), "Unknown log-level value: " + log.params[1], ee);
          }

          parameters = Arrays.asList(Arrays.copyOfRange(log.params, 2, log.params.length));
        }

        // Log the message.
        try {
          log.logManager.log(loggerName, logLevel, parameters, log.collapse);
        } catch (LoggerException e) {
          throw new ParameterException(log.spec.commandLine(), e.getMessage(), e);
        }
      }
      return true;
    }
  }
}
