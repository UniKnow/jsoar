package org.jsoar.kernel.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.symbols.DoubleSymbol;
import org.jsoar.kernel.symbols.Identifier;
import org.jsoar.kernel.symbols.IntegerSymbol;
import org.jsoar.kernel.symbols.JavaSymbol;
import org.jsoar.kernel.symbols.StringSymbol;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.SymbolFactoryImpl;
import org.jsoar.kernel.symbols.Variable;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.commands.PicocliSoarCommand;
import org.jsoar.util.timing.DefaultExecutionTimer;
import org.jsoar.util.timing.WallclockExecutionTimeSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * This is the implementation of the "debug" command.
 *
 * @author austin.brehob
 */
public class DebugCommand extends PicocliSoarCommand {

  public DebugCommand(Agent agent) {
    super(agent, new Debug(agent));
  }

  @Command(
      name = "debug",
      description = "Contains low-level technical debugging commands",
      subcommands = {
          HelpCommand.class,
          DebugCommand.InternalSymbols.class,
          DebugCommand.Time.class
      })
  public static class Debug implements Runnable {

    private Agent agent;
    private SymbolFactoryImpl syms;

    public Debug(Agent agent) {
      this.agent = agent;
      this.syms = Adaptables.adapt(agent, SymbolFactoryImpl.class);
    }

    @Override
    public void run() {
      this.agent
          .getPrinter()
          .startNewLine()
          .print("The 'debug' command " + "contains low-level technical debugging commands.");
    }
  }

  @Command(
      name = "internal-symbols",
      description = "Prints symbol table",
      subcommands = {HelpCommand.class})
  public static class InternalSymbols implements Runnable {

    @ParentCommand
    Debug parent; // injected by picocli

    @Override
    public void run() {
      final List<Symbol> all = parent.syms.getAllSymbols();
      final var result = new StringBuilder();
      printSymbolsOfType(result, all, Identifier.class);
      printSymbolsOfType(result, all, StringSymbol.class);
      printSymbolsOfType(result, all, IntegerSymbol.class);
      printSymbolsOfType(result, all, DoubleSymbol.class);
      printSymbolsOfType(result, all, Variable.class);
      printSymbolsOfType(result, all, JavaSymbol.class);

      parent.agent.getPrinter().startNewLine().print(result.toString());
    }

    private <T extends Symbol> void printSymbolsOfType(
        StringBuilder result, List<Symbol> all, Class<T> klass) {
      final List<String> symbols = collectSymbolsOfType(all, klass);
      Collections.sort(symbols);

      result.append("--- ").append(klass).append(" (").append(symbols.size()).append(") ---\n")
          .append(String.join("\n", symbols))
          .append("\n");
    }

    private <T extends Symbol> List<String> collectSymbolsOfType(List<Symbol> in, Class<T> klass) {
      final List<String> result = new ArrayList<>();
      for (Symbol s : in) {
        if (klass.isInstance(s)) {
          result.add(s.toString());
        }
      }
      return result;
    }
  }

  @Command(
      name = "time",
      description = "Executes command and prints time spent",
      subcommands = {HelpCommand.class})
  public static class Time implements Runnable {

    @ParentCommand
    Debug parent; // injected by picocli

    @Parameters(description = "The Soar command")
    String[] command;

    @Override
    public void run() {
      if (command == null) {
        parent
            .agent
            .getPrinter()
            .startNewLine()
            .print("You must submit a command that you'd like timed.");
        return;
      }

      // JSoar can't easily have a Process Timer which does things exactly how
      // CSoar does things therefore I'm not including it in the output
      // - ALT

      var real_source = new WallclockExecutionTimeSource();
      var real = DefaultExecutionTimer.newInstance(real_source);

      var combined = String.join(" ", command);
      //      for (String s : command) {
      //        combined += s + " ";
      //      }
      //      combined = combined.substring(0, combined.length() - 1);

      // Run the command and record how long it takes to complete
      real.start();
      String result;
      try {
        result = parent.agent.getInterpreter().eval(combined);
      } catch (SoarException e) {
        parent.agent.getPrinter().startNewLine().print(e.getMessage());
        return;
      }
      real.pause();
      double seconds = real.getTotalSeconds();

      if (result == null) {
        result = "";
      }
      result += "(-1s) proc - Note JSoar does not support measuring CPU time at the moment.\n";
      result += "(" + seconds + "s) real\n";

      parent.agent.getPrinter().startNewLine().print(result);
    }
  }
}
