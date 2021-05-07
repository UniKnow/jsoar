package org.jsoar.kernel.commands;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.parser.ParserException;
import org.jsoar.kernel.rhs.ReordererException;
import org.jsoar.util.commands.PicocliSoarCommand;
import org.jsoar.util.commands.SoarCommandContext;
import org.jsoar.util.commands.SoarExceptionsManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;

/**
 * This is the implementation of the "sp" command.
 *
 * @author austin.brehob
 */
public class SpCommand extends PicocliSoarCommand {
  public SpCommand(Agent agent) {
    super(agent, new Sp(agent));
  }

  @Override
  public String execute(SoarCommandContext context, String[] args) throws SoarException {
    final Sp sp = ((Sp) this.picocliCommand);
    sp.context = context;
    return super.execute(context, args);
  }

  @Command(
      name = "sp",
      description = "Define a Soar production",
      subcommands = {HelpCommand.class})
  public static class Sp implements Runnable {
    private final Agent agent;
    private SoarCommandContext context;

    public Sp(Agent agent) {
      this.agent = agent;
      this.context = null;
    }

    public Sp(Agent agent, SoarCommandContext context) {
      this.agent = agent;
      this.context = context;
    }

    @Parameters(description = "A Soar production")
    String production;

    @Override
    public void run() {
      if (production == null) {
        agent.getPrinter().startNewLine().print("Use this command to define a Soar production");
      } else {
        try {
          agent.getProductions().loadProduction(production, context.getSourceLocation());
          agent.getPrinter().print("*");
          SoarExceptionsManager exceptionsManager = agent.getInterpreter().getExceptionsManager();
          agent
              .getPrinter()
              .getWarningsAndClear()
              .forEach(warning -> exceptionsManager.addException(warning, context, production));
        } catch (ReordererException | ParserException e) {
          agent
              .getPrinter()
              .startNewLine()
              .print(context.getSourceLocation() + ":" + e.getMessage());
          agent.getInterpreter().getExceptionsManager().addException(e, context, production);
        }
      }
    }
  }

  @Override
  public Object getCommand() {
    return new Sp(agent, null);
  }
}
