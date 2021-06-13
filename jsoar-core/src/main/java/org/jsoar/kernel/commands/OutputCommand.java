package org.jsoar.kernel.commands;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.commands.PrintCommand.Print;
import org.jsoar.util.TeeWriter;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * This is the implementation of the "output" command.
 *
 * @author austin.brehob
 */
public final class OutputCommand extends PicocliSoarCommand {

  public OutputCommand(Agent agent, Print printCommand) {
    super(agent, new Output(agent, printCommand, new LinkedList<>()));
  }

  @Command(
      name = "output",
      description = "Commands related to handling output",
      subcommands = {
        HelpCommand.class,
        OutputCommand.Log.class,
        OutputCommand.PrintDepth.class,
        OutputCommand.Warnings.class
      })
  public static class Output implements Runnable {

    private final Agent agent;
    private final Print printCommand;

    private final Deque<Writer> writerStack;

    public Output(Agent agent, Print printCommand, Deque<Writer> writerStack) {
      this.agent = agent;
      this.printCommand = printCommand;
      this.writerStack = writerStack;
    }

    // TODO Provide summary
    @Override
    public void run() {
      agent
          .getPrinter()
          .startNewLine()
          .print(
              """
                  =======================================================
                  -                    Output Status                    -
                  =======================================================
                  """);
    }
  }

  @Command(
      name = "log",
      description = "Changes output log settings",
      subcommands = {HelpCommand.class})
  public static class Log implements Runnable {

    /*
     * Contains list of actions that can be performed by this command
     */
    private static final List<Action<Log>> ACTIONS =
        Stream.of(new CloseLog(), new SetDestinationLog(), new PrintStateLog())
            .collect(Collectors.toList());

    @ParentCommand Output parent; // injected by picocli

    @Option(
        names = {"-c", "--close"},
        arity = "0..1",
        defaultValue = "false",
        description = "Closes the log file")
    boolean close;

    @Parameters(index = "0", arity = "0..1", description = "Destination log")
    String destination;

    @Override
    public void run() {
      for (Action<Log> action : ACTIONS) {
        if (action.execute(this)) {
          return;
        }
      }
    }

    private static class PrintStateLog implements Action<Log> {

      @Override
      public boolean execute(Log context) {
        context
            .parent
            .agent
            .getPrinter()
            .startNewLine()
            .print("log is " + (context.parent.writerStack.isEmpty() ? "off" : "on"));

        return true;
      }
    }

    private static class SetDestinationLog implements Action<Log> {

      @Override
      public boolean execute(Log context) {

        var handled = false;

        if (context.destination != null) {
          String destination = context.destination;
          Writer w;

          switch (destination) {
            case "stdout":
              w = new OutputStreamWriter(System.out);
              context.parent.writerStack.push(null);
              context
                  .parent
                  .agent
                  .getPrinter()
                  .pushWriter(new TeeWriter(context.parent.agent.getPrinter().getWriter(), w));
              context.parent.agent.getPrinter().startNewLine().print("Now writing to System.out");
              break;
            case "stderr":
              w = new OutputStreamWriter(System.err);
              context.parent.writerStack.push(null);
              context
                  .parent
                  .agent
                  .getPrinter()
                  .pushWriter(new TeeWriter(context.parent.agent.getPrinter().getWriter(), w));
              context.parent.agent.getPrinter().startNewLine().print("Now writing to System.err");
              break;
            default:
              try {
                w = new FileWriter(context.destination);
                context.parent.writerStack.push(w);
                context
                    .parent
                    .agent
                    .getPrinter()
                    .pushWriter(new TeeWriter(context.parent.agent.getPrinter().getWriter(), w));

                // adding a newline at the end because we don't want the next line (which could be a
                // command output) to start on the same line.
                // normally we would leave this up to the debugger or other display mechanism to
                // figure
                // out, but in this case it's going straight to a file.
                context
                    .parent
                    .agent
                    .getPrinter()
                    .startNewLine()
                    .print("Log file " + context.destination + " open.")
                    .startNewLine();
              } catch (IOException e) {
                context
                    .parent
                    .agent
                    .getPrinter()
                    .startNewLine()
                    .print("Failed to open file '" + context.destination + "': " + e.getMessage());
              }
          }
          handled = true;
        }

        return handled;
      }
    }

    private static class CloseLog implements Action<Log> {

      @Override
      public boolean execute(Log context) {
        var handled = false;

        if (context.close) {
          if (context.parent.writerStack.isEmpty()) {
            context.parent.agent.getPrinter().startNewLine().print("Log is not open.");
          } else {
            context.parent.writerStack.pop();
            context.parent.agent.getPrinter().popWriter();
            context.parent.agent.getPrinter().startNewLine().print("Log file closed.");
          }
          handled = true;
        }

        return handled;
      }
    }
  }

  @Command(
      name = "print-depth",
      description = "Adjusts or displays the print-depth",
      subcommands = {HelpCommand.class})
  public static class PrintDepth implements Runnable {

    @ParentCommand Output parent; // injected by picocli

    @Spec CommandSpec spec; // injected by picocli

    @Parameters(index = "0", arity = "0..1", description = "New print depth")
    Integer depth;

    @Override
    public void run() {
      if (depth == null) {
        parent
            .agent
            .getPrinter()
            .startNewLine()
            .print("print-depth is " + parent.printCommand.getDefaultDepth());
      } else {
        int depth = this.depth;
        try {
          parent.printCommand.setDefaultDepth(depth);
        } catch (SoarException e) {
          throw new ParameterException(spec.commandLine(), e.getMessage(), e);
        }
        parent.agent.getPrinter().startNewLine().print("print-depth is now " + depth);
      }
    }
  }

  @Command(
      name = "warnings",
      description = "Toggles output warnings",
      subcommands = {HelpCommand.class})
  public static class Warnings implements Runnable {

    @ParentCommand Output parent; // injected by picocli

    @Option(
        names = {"on", "-e", "--on", "--enable"},
        defaultValue = "false",
        description = "Enables output warnings")
    boolean enable;

    @Option(
        names = {"off", "-d", "--off", "--disable"},
        defaultValue = "false",
        description = "Disables output warnings")
    boolean disable;

    @Override
    public void run() {
      if (!enable && !disable) {
        parent
            .agent
            .getPrinter()
            .print("warnings is " + (parent.agent.getPrinter().isPrintWarnings() ? "on" : "off"));
      } else if (enable) {
        parent.agent.getPrinter().setPrintWarnings(true);
        parent.agent.getPrinter().print("warnings is now on");
      } else {
        parent.agent.getPrinter().setPrintWarnings(false);
        parent.agent.getPrinter().print("warnings is now off");
      }
    }
  }
}
