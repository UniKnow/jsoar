package org.jsoar.kernel.commands;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.commands.ToggleConverter.Toggle;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;

/**
 * This is the implementation of the "save-backtraces" command.
 *
 * @author austin.brehob
 */
public class SaveBacktracesCommand extends PicocliSoarCommand {

  public SaveBacktracesCommand(Agent agent) {
    super(agent, new SaveBacktraces(agent));
  }

  @Command(
      name = "save-backtraces",
      description = "Toggles or prints backtrace saving",
      subcommands = {HelpCommand.class})
  public static class SaveBacktraces implements Runnable {

    private final Agent agent;

    public SaveBacktraces(Agent agent) {
      this.agent = agent;
    }

    @Parameters(
        arity = "0..1",
        converter = ToggleConverter.class,
        description = "Enables/disables backtrace saving")
    Toggle explain;

    @Override
    public void run() {
      if (explain == null) {
        agent
            .getPrinter()
            .startNewLine()
            .print(
                "The current save-backtraces setting is: "
                    + (Boolean.TRUE.equals(agent.getProperties().get(SoarProperties.EXPLAIN))
                        ? "enabled"
                        : "disabled"));
      } else {
        agent.getProperties().set(SoarProperties.EXPLAIN, explain.asBoolean());
      }
    }
  }
}
