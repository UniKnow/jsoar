package org.jsoar.kernel.commands;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.commands.ToggleConverter.Toggle;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;

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
        Stream.of(new SetLearning(), new PrintLearning()).collect(Collectors.toList());

    private final Agent agent;

    public Chunk(Agent agent) {
      this.agent = agent;
    }

    @Parameters(
        arity = "0..1",
        converter = ToggleConverter.class,
        description = "Enables/disables learning")
    private Toggle learning;

    @Override
    public void run() {
      // Traverse list of actions until successfully handled
      for (Action<Chunk> action : ACTIONS) {
        if (action.execute(this)) {
          return;
        }
      }
    }

    private static class SetLearning implements Action<Chunk> {

      @Override
      public boolean execute(Chunk context) {
        var handled = false;

        if (context.learning != null) {
          context
              .agent
              .getProperties()
              .set(SoarProperties.LEARNING_ON, context.learning.asBoolean());
          handled = true;
        }

        return handled;
      }
    }

    private static class PrintLearning implements Action<Chunk> {

      @Override
      public boolean execute(Chunk context) {
        var handled = false;

        if (context.learning == null) {
          context
              .agent
              .getPrinter()
              .startNewLine()
              .print(
                  "The current chunk setting is: "
                      + (Boolean.TRUE.equals(
                              context.agent.getProperties().get(SoarProperties.LEARNING_ON))
                          ? "enabled"
                          : "disabled"));
          handled = true;
        }
        return handled;
      }
    }
  }
}
