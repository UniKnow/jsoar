package org.jsoar.kernel.commands;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

/**
 * This is the implementation of the "chunk" command.
 *
 * @author austin.brehob
 */
public class ChunkCommand extends PicocliSoarCommand {

  public ChunkCommand(Agent agent) {
    super(agent, new Chunk(agent));
  }

  @Command(
      name = "chunk",
      description = "Prints or adjusts Soar's ability to learn new rules",
      subcommands = {HelpCommand.class})
  public static class Chunk implements Runnable {

    private Agent agent;

    public Chunk(Agent agent) {
      this.agent = agent;
    }

    @Option(
        names = {"on", "-e", "--on", "--enable"},
        defaultValue = "false",
        description = "Enables chunking")
    private boolean enable;

    @Option(
        names = {"off", "-d", "--off", "--disable"},
        defaultValue = "false",
        description = "Disables chunking")
    private boolean disable;

    @Override
    public void run() {
      if (enable) {
        agent.getProperties().set(SoarProperties.LEARNING_ON, true);
      } else if (disable) {
        agent.getProperties().set(SoarProperties.LEARNING_ON, false);
      } else {
        agent
            .getPrinter()
            .startNewLine()
            .print(
                "The current chunk setting is: "
                    + (Boolean.TRUE.equals(agent.getProperties().get(SoarProperties.LEARNING_ON))
                        ? "enabled"
                        : "disabled"));
      }
    }
  }
}
