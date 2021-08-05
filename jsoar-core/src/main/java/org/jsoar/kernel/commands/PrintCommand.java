package org.jsoar.kernel.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.Goal;
import org.jsoar.kernel.GoalDependencySet;
import org.jsoar.kernel.Production;
import org.jsoar.kernel.ProductionManager;
import org.jsoar.kernel.ProductionType;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.learning.rl.ReinforcementLearningParams;
import org.jsoar.kernel.memory.Wme;
import org.jsoar.kernel.memory.WorkingMemoryPrinter;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * This is the implementation of the "print" command.
 *
 * @author austin.brehob
 */
public class PrintCommand extends PicocliSoarCommand {
  public PrintCommand(Agent agent) {
    super(agent, new Print(agent));
  }

  @Override
  public Print getCommand() {
    return (Print) super.getCommand();
  }

  @Command(
      name = "print",
      description = "Prints data in the console window",
      subcommands = {HelpCommand.class})
  public static class Print implements Runnable {
    private Agent agent;
    private int defaultDepth = 1;
    private int depth = 1;
    private WorkingMemoryPrinter wmp = new WorkingMemoryPrinter();

    @Spec private CommandSpec spec;

    public Print(Agent agent) {
      this.agent = agent;
    }

    @Option(
        names = {"-a", "--all"},
        defaultValue = "false",
        description = "Print the names of all productions currently loaded")
    boolean printAll;

    @Option(
        names = {"-c", "--chunks"},
        defaultValue = "false",
        description = "Print the names of all chunks currently loaded")
    boolean printChunks;

    @Option(
        names = {"-D", "--defaults"},
        defaultValue = "false",
        description = "Print the names of " + "all default productions currently loaded")
    boolean printDefaults;

    @Option(
        names = {"-d", "--depth"},
        description = "This option overrides the default printing depth")
    Integer overrideDepth;

    @Option(
        names = {"-e", "--exact"},
        defaultValue = "false",
        description = "Print only the wmes that match the pattern")
    boolean printExactMatch;

    @Option(
        names = {"-F", "--filename"},
        defaultValue = "false",
        description = "Also prints the name " + "of the file that contains the production")
    boolean printFilename;

    @Option(
        names = {"-f", "--full"},
        defaultValue = "false",
        description =
            "When printing productions, print "
                + "the whole production (default behavior for individual productions)")
    boolean printFullProd;

    @Option(
        names = {"-g", "--gds"},
        defaultValue = "false",
        description = "Print the Goal Dependency Set")
    boolean printGDS;

    @Option(
        names = {"-i", "--internal"},
        defaultValue = "false",
        description = "Items will be printed in their internal form")
    boolean printInternalForm;

    @Option(
        names = {"-j", "--justifications"},
        defaultValue = "false",
        description = "Print the names of " + "all justifications currently loaded")
    boolean printJustify;

    @Option(
        names = {"-n", "--name"},
        defaultValue = "false",
        description =
            "When printing productions, print only "
                + "the name and not the whole production (default behavior for multiple productions")
    boolean printOnlyName;

    @Option(
        names = {"-o", "--operators"},
        defaultValue = "false",
        description = "When printing the stack, print only operators")
    boolean printOnlyOperators;

    @Option(
        names = {"-r", "--rl"},
        defaultValue = "false",
        description = "Print Soar-RL rules")
    boolean printRLRules;

    @Option(
        names = {"-s", "--stack"},
        defaultValue = "false",
        description = "Specifies that the Soar goal stack should be printed")
    boolean printStack;

    @Option(
        names = {"-S", "--states"},
        defaultValue = "false",
        description = "When printing the stack, print only states")
    boolean printOnlyStates;

    @Option(
        names = {"-T", "--template"},
        defaultValue = "false",
        description = "Print Soar-RL templates")
    boolean printTemplates;

    @Option(
        names = {"-t", "--tree"},
        defaultValue = "false",
        description = "wmes will be printed in a tree form (one wme per line)")
    boolean printInTreeForm;

    @Option(
        names = {"-u", "--user"},
        defaultValue = "false",
        description = "Print the names of all user productions currently loaded")
    boolean printUserProds;

    @Option(
        names = {"-v", "--varprint"},
        defaultValue = "false",
        description = "Print identifiers enclosed in angle brackets (not yet supported)")
    boolean printVars;

    @Parameters(description = "A symbol, pattern, timetag, or production name")
    private String[] params;

    @Override
    public void run() {
      if (printGDS) {
        StringBuilder result =
            new StringBuilder(
                """
                ********************* Current GDS **************************
                stepping thru all wmes in rete, looking for any that are in a gds...
                """);

        // the command outputs two lists of wmes:
        //  the wme-only list, which is in bottom-to-top order by goal
        //  the goal list, which is in top-to-bottom order by goal

        final List<Goal> goalsTopToBottom = agent.getGoalStack();
        final List<Goal> goalsBottomtoTop = new ArrayList<>(goalsTopToBottom);
        Collections.reverse(goalsBottomtoTop);

        // list wmes from goals in bottom-to-top order
        for (final Goal goal : goalsBottomtoTop) {
          final GoalDependencySet gds = Adaptables.adapt(goal, GoalDependencySet.class);
          if (gds == null) {
            continue;
          }

          final Iterator<Wme> itr = gds.getWmes();
          while (itr.hasNext()) {
            final Wme w = itr.next();
            result
                .append("  For Goal  ")
                .append(goal)
                .append("  ")
                .append(w.toString())
                .append("\n");
          }
        }

        result.append("************************************************************\n");

        // list goals with wmes in top-to-bottom order
        for (final Goal goal : goalsTopToBottom) {
          result.append("  For Goal  ").append(goal.toString());
          final GoalDependencySet gds = Adaptables.adapt(goal, GoalDependencySet.class);
          if (gds == null) {
            result.append("  : No GDS for this goal.\n");
            continue;
          }

          result.append("\n");

          final Iterator<Wme> itr = gds.getWmes();
          while (itr.hasNext()) {
            final Wme w = itr.next();
            result.append("                ").append(w.toString()).append("\n");
          }
        }

        result.append("************************************************************\n");

        agent.getPrinter().print(result.toString());
      }

      depth = defaultDepth;

      if (overrideDepth != null) {
        int newDepth = overrideDepth;
        if (checkDepth(newDepth)) {
          depth = newDepth;
        }
      }

      if (printVars) {
        throw new ParameterException(spec.commandLine(), "--varprint not implemented yet");
      }

      // New in Soar 8.6.3: if no args or options given, print all prods
      if (!printChunks
          && !printDefaults
          && !printJustify
          && !printRLRules
          && !printTemplates
          && !printUserProds) printAll = true;

      if (printStack) {
        agent.printStackTrace(printOnlyStates, printOnlyOperators);
        agent.getPrinter().print("\n").flush();
        return;
      } else if (printOnlyStates || printOnlyOperators) {
        agent
            .getPrinter()
            .print(
                "Options --operators (-o) and --states (-S) "
                    + "are only valid when printing the stack.");
        return;
      }

      if (params != null) {
        var argString = String.join(" ", params);

        // Test if the parameter(s) passed is a symbol or pattern
        Symbol arg = agent.readIdentifierOrContextVariable(argString);
        if (arg != null || argString.charAt(0) == '(') {
          wmp.setInternal(printInternalForm);
          wmp.setExact(printExactMatch);

          // These are ignored if the parameters form a pattern
          wmp.setDepth(depth);
          wmp.setTree(printInTreeForm);

          try {
            wmp.print(agent, agent.getPrinter(), arg, argString);
          } catch (RuntimeException e) {
            agent.getPrinter().print(e.toString());
          }

          agent.getPrinter().flush();
          return;
        }

        // Test if the parameter passed is a timetag
        try {
          var tt = Integer.parseInt(argString);
          for (Wme wme : agent.getAllWmesInRete()) {
            // TODO: Print full object, not just wme
            if (wme.getTimetag() == tt) {
              agent.getPrinter().print(wme.toString());
              agent.getPrinter().flush();
              return;
            }
          }
          agent.getPrinter().print("No wme " + tt + " in working memory.");
          return;
        } catch (NumberFormatException ignored) {
        }

        // Test if the parameter passed is a production name
        // Default is to print full production
        if (!printOnlyName) {
          printFullProd = true;
        }

        var p = agent.getProductions().getProduction(argString);
        if (p != null) {
          do_print_for_production(p);
        } else {
          agent.getPrinter().print("No production named " + argString);
        }
        agent.getPrinter().flush();
        return;
      }

      if (printAll) {
        printChunks = true;
        printDefaults = true;
        printJustify = true;
        printUserProds = true;
        printTemplates = true;
      }

      for (Production p : collectProductions()) {
        do_print_for_production(p);
      }

      if (printRLRules) {
        for (Production p : agent.getProductions().getProductions(null)) {
          if (p.rlRuleInfo != null) {
            do_print_for_production(p);
          }
        }
      }
      agent.getPrinter().flush();
    }

    private void do_print_for_production(Production prod) {
      final var p = agent.getPrinter();

      if (printFilename) {
        if (printFullProd) {
          p.print("# source file: ", prod.getLocation());
        }

        p.print("%s", prod.getLocation());

        if (printFullProd) p.print("\n");
        else p.print(": ");
      }

      if (printFullProd) {
        prod.print(p, printInternalForm);
      } else {
        p.print("%s ", prod.getName());

        if (prod.rlRuleInfo != null) {
          // Do extra logging if this agent is in delta bar delta mode.
          if (agent.getProperties().get(ReinforcementLearningParams.DECAY_MODE)
              == ReinforcementLearningParams.DecayMode.delta_bar_delta_decay) {
            p.print(
                " %y", agent.getSymbols().createDouble(prod.rlRuleInfo.rl_delta_bar_delta_beta));
            p.print(" %y", agent.getSymbols().createDouble(prod.rlRuleInfo.rl_delta_bar_delta_h));
          }
          p.print("%f  ", prod.rlRuleInfo.rl_update_count);
          p.print("%s", prod.getFirstAction().asMakeAction().referent);
        }
      }
      p.print("\n");
    }

    private List<Production> collectProductions() {
      final ProductionManager pm = agent.getProductions();
      final List<Production> result = new ArrayList<>();

      if (printChunks) result.addAll(pm.getProductions(ProductionType.CHUNK));
      if (printUserProds) result.addAll(pm.getProductions(ProductionType.USER));
      if (printDefaults) result.addAll(pm.getProductions(ProductionType.DEFAULT));
      if (printTemplates) result.addAll(pm.getProductions(ProductionType.TEMPLATE));
      if (printJustify) result.addAll(pm.getProductions(ProductionType.JUSTIFICATION));

      return result;
    }

    private boolean checkDepth(int depth) {
      return depth > 0;
    }

    public void setDefaultDepth(int depth) throws SoarException {
      if (checkDepth(depth)) {
        defaultDepth = depth;
      } else {
        throw new SoarException("depth must be greater than 0");
      }
    }

    public int getDefaultDepth() {
      return defaultDepth;
    }
  }
}
