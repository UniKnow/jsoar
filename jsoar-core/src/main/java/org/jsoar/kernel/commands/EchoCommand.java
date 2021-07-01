package org.jsoar.kernel.commands;

import org.jsoar.kernel.Agent;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * This is the implementation of the "echo" command.
 *
 * @author austin.brehob
 */
public class EchoCommand extends PicocliSoarCommand {

  public EchoCommand(Agent agent) {
    super(agent, new Echo(agent));
  }

  @Command(
      name = "echo",
      description = "Outputs the given string",
      subcommands = {HelpCommand.class})
  public static class Echo implements Runnable {
    private final Agent agent;

    public Echo(Agent agent) {
      this.agent = agent;
    }

    @Option(
        names = {"-n", "--no-newline"},
        defaultValue = "false",
        description = "Suppress printing of the newline character")
    private boolean noNewline;

    @Parameters(description = "The string to output")
    private String[] outputString = null;

    @Override
    public void run() {
      if (outputString != null) {
        agent.getPrinter().print(String.join(" ", outputString));
      }

      if (!noNewline) {
        agent.getPrinter().print("\n");
      }
      agent.getPrinter().flush();
    }
  }
}
