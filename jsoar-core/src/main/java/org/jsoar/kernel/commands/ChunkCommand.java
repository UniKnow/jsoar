package org.jsoar.kernel.commands;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    /*
     * Contains list of actions that can be performed by this command
     */
    private static final List<Action<Chunk>> ACTIONS =
        Stream.of(
            new EnableLearning(),
            new DisableLearning(),
            new PrintValueLearning()
        ).collect(Collectors.toList());

    private final Agent agent;

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
      // Traverse list of actions until successfully handled
      for (Action<Chunk> action : ACTIONS) {
        if (action.execute(this)) {
          break;
        }
      }
    }

    private static class EnableLearning implements Action<Chunk> {

      @Override
      public boolean execute(Chunk context) {
        var handled = false;

        if (context.enable) {
          context.agent.getProperties().set(SoarProperties.LEARNING_ON, true);
          handled = true;
        }

        return handled;
      }
    }

    private static class DisableLearning implements Action<Chunk> {

      @Override
      public boolean execute(Chunk context) {
        var handled =false;

        if (context.disable) {
          context.agent.getProperties().set(SoarProperties.LEARNING_ON, false);
          handled = true;
        }

        return handled;
      }
    }

    private static class PrintValueLearning implements Action<Chunk> {

      @Override
      public boolean execute(Chunk context) {
        context.agent
            .getPrinter()
            .startNewLine()
            .print(
                "The current chunk setting is: "
                    + (Boolean.TRUE.equals(context.agent.getProperties().get(SoarProperties.LEARNING_ON))
                    ? "enabled"
                    : "disabled"));

        return true;
      }
    }
  }
}
