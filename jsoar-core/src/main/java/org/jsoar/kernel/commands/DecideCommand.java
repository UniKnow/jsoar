package org.jsoar.kernel.commands;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.DecisionManipulation;
import org.jsoar.kernel.exploration.Exploration;
import org.jsoar.util.PrintHelper;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.commands.PicocliSoarCommand;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * This is the implementation of the "decide" command.
 *
 * @author austin.brehob
 */
public class DecideCommand extends PicocliSoarCommand {

  private static final int DISPLAY_COLUMNS = 55;

  public DecideCommand(Agent agent) {
    super(agent, new Decide(agent));
  }

  @Command(
      name = "decide",
      description =
          "Commands and settings related to "
              + "the selection of operators during the Soar decision process",
      subcommands = {
        HelpCommand.class,
        DecideCommand.IndifferentSelection.class,
        DecideCommand.NumericIndifferentMode.class,
        DecideCommand.Predict.class,
        DecideCommand.Select.class,
        DecideCommand.SetRandomSeed.class
      })
  public static class Decide implements Runnable {

    private final Agent agent;
    private final Exploration exploration;
    private final DecisionManipulation decisionManipulation;

    public Decide(Agent agent) {
      this.agent = agent;
      this.exploration = Adaptables.adapt(agent, Exploration.class);
      this.decisionManipulation = Adaptables.adapt(agent, DecisionManipulation.class);
    }

    @Override
    public void run() {
      printCurrentDecideSettings();
    }

    private void printCurrentDecideSettings() {
      final var sw = new StringWriter();
      final var pw = new PrintWriter(sw);

      pw.printf(PrintHelper.generateHeader("Decide Summary", DISPLAY_COLUMNS));
      pw.printf(currentNumericIndifferentMode(exploration));
      pw.printf(PrintHelper.generateSection("Discount", DISPLAY_COLUMNS));
      pw.printf(currentPolicy(exploration));
      pw.printf(currentAutoReduceSetting(exploration));
      pw.printf(currentParameterValue(exploration, "epsilon"));
      pw.printf(currentParameterReductionPolicy(exploration, "epsilon"));
      pw.printf(currentParameterReductionRate(exploration, "epsilon", "exponential"));
      pw.printf(currentParameterReductionRate(exploration, "epsilon", "linear"));
      pw.printf(currentParameterValue(exploration, "temperature"));
      pw.printf(currentParameterReductionPolicy(exploration, "temperature"));
      pw.printf(currentParameterReductionRate(exploration, "temperature", "exponential"));
      pw.printf(currentParameterReductionRate(exploration, "temperature", "linear"));
      pw.printf(PrintHelper.generateSection("Discount", DISPLAY_COLUMNS));

      pw.flush();
      agent.getPrinter().startNewLine().print(sw.toString());
    }
  }

  @Command(
      name = "indifferent-selection",
      description =
          "Allows the user to set options relating to "
              + "selection between operator proposals that are mutually indifferent in preference memory",
      subcommands = {HelpCommand.class})
  public static class IndifferentSelection implements Runnable {

    /*
     * Contains list of actions that can be performed by this command
     */
    private static final List<Action<IndifferentSelection>> ACTIONS =
        Stream.of(
                new SetExplorationPolicy(),
                new SetReductionPolicy(),
                new SetReductionRate(),
                new SetAutoUpdate(),
                new EpsilonValue(),
                new TemperatureValue(),
                new PrintStats(),
                new PrintPolicy())
            .collect(Collectors.toList());

    @ParentCommand Decide parent; // injected by picocli

    @ArgGroup(exclusive = true)
    Policy policy;

    private static class Policy {

      @Option(
          names = {"-b", "--boltzmann"},
          defaultValue = "false",
          description = "Sets the exploration policy to 'boltzmann'")
      boolean boltzmannPolicy;

      @Option(
          names = {"-E", "--epsilon-greedy"},
          defaultValue = "false",
          description = "Sets the exploration policy to 'epsilon-greedy'")
      boolean epsilonGreedyPolicy;

      @Option(
          names = {"-f", "--first"},
          defaultValue = "false",
          description = "Sets the exploration policy to 'first'")
      boolean firstPolicy;

      @Option(
          names = {"-l", "--last"},
          defaultValue = "false",
          description = "Sets the exploration policy to 'last'")
      boolean lastPolicy;

      @Option(
          names = {"-s", "--softmax"},
          defaultValue = "false",
          description = "Sets the exploration policy to 'softmax'")
      boolean softmaxPolicy;

      String getName() {
        String policyName = Exploration.Policy.USER_SELECT_BOLTZMANN.getPolicyName();
        if (epsilonGreedyPolicy) {
          policyName = Exploration.Policy.USER_SELECT_E_GREEDY.getPolicyName();
        } else if (firstPolicy) {
          policyName = Exploration.Policy.USER_SELECT_FIRST.getPolicyName();
        } else if (lastPolicy) {
          policyName = Exploration.Policy.USER_SELECT_LAST.getPolicyName();
        } else if (softmaxPolicy) {
          policyName = Exploration.Policy.USER_SELECT_SOFTMAX.getPolicyName();
        }
        return policyName;
      }
    }

    @Option(
        names = {"-e", "--epsilon"},
        defaultValue = "false",
        description = "Prints or updates the epsilon value")
    boolean epsilon;

    @Option(
        names = {"-t", "--temperature"},
        defaultValue = "false",
        description = "Prints or updates temperature value")
    boolean temperature;

    @Option(
        names = {"-p", "--reduction-policy"},
        description = "Prints or updates the reduction policy for the given parameter")
    String reductionPolicyParam;

    @Option(
        names = {"-r", "--reduction-rate"},
        description = "Prints or updates the reduction rate for the given parameter")
    String reductionRateParam;

    @Option(
        names = {"-a", "--auto-reduce"},
        defaultValue = "false",
        description = "Prints or toggles automatic policy parameter reduction")
    boolean autoReduce;

    @Option(
        names = {"-S", "--stats"},
        defaultValue = "false",
        description = "Prints summary of decision settings")
    boolean printStats;

    @Parameters(
        index = "0",
        arity = "0..1",
        description =
            "New epsilon/temperature value; or exploration  parameter reduction policy: 'linear' or 'exponential'; or toggles auto-reduce: 'on' or 'off'")
    private String param;

    @Parameters(
        index = "1",
        arity = "0..1",
        description = "New exploration parameter reduction rate")
    private Double reductionRate;

    @Override
    public void run() {

      // Traverse list of actions until successfully handled
      for (Action<IndifferentSelection> action : ACTIONS) {
        if (action.execute(this)) {
          return;
        }
      }
    }

    private interface SetParameterValue extends Action<IndifferentSelection> {

      default void printValue(final IndifferentSelection context, final String parameterName) {
        context
            .parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(currentParameterValue(context.parent.exploration, parameterName));
      }

      default void setValue(final IndifferentSelection context, final String parameterName) {
        try {
          double newValue = Double.parseDouble(context.param);

          if (context.parent.exploration.exploration_valid_parameter_value(
              parameterName, newValue)) {
            if (context.parent.exploration.exploration_set_parameter_value(
                parameterName, newValue)) {
              context
                  .parent
                  .agent
                  .getPrinter()
                  .startNewLine()
                  .print("Set " + parameterName + "parameter value to " + newValue);
            } else {
              context
                  .parent
                  .agent
                  .getPrinter()
                  .startNewLine()
                  .print("Unknown error trying to set " + parameterName + " parameter value");
            }
          } else {
            context
                .parent
                .agent
                .getPrinter()
                .startNewLine()
                .print("Illegal value for " + parameterName + " parameter value");
          }
        } catch (NumberFormatException e) {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print(String.format("%s is not a valid double: %s", context.param, e.getMessage()));
        }
      }
    }

    private static class EpsilonValue implements SetParameterValue {

      @Override
      public boolean execute(IndifferentSelection context) {
        var handled = false;
        if (context.epsilon) {

          if (context.param == null) {
            // decide indifferent-selection --epsilon
            printValue(context, "epsilon");
          } else {
            // decide indifferent-selection --epsilon <newValue>
            setValue(context, "epsilon");
          }

          handled = true;
        }
        return handled;
      }
    }

    private static class TemperatureValue implements SetParameterValue {

      @Override
      public boolean execute(IndifferentSelection context) {
        var handled = false;
        if (context.temperature) {

          if (context.param == null) {
            // decide indifferent-selection --temperature
            printValue(context, "temperature");
          } else {
            // decide indifferent-selection --temperature <newValue>
            setValue(context, "temperature");
          }

          handled = true;
        }
        return handled;
      }
    }

    private static class PrintPolicy implements Action<IndifferentSelection> {

      @Override
      public boolean execute(IndifferentSelection context) {
        // decide indifferent-selection
        context
            .parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(currentPolicy(context.parent.exploration));

        return true;
      }
    }

    private static class PrintStats implements Action<IndifferentSelection> {

      @Override
      public boolean execute(IndifferentSelection context) {
        var handled = false;

        if (context.printStats) {
          // decide indifferent-selection --stats
          final var sw = new StringWriter();
          final var pw = new PrintWriter(sw);
          final Decide parent = context.parent;

          pw.printf(currentPolicy(parent.exploration));
          pw.printf(currentAutoReduceSetting(parent.exploration));
          pw.printf(currentParameterValue(parent.exploration, "epsilon"));
          pw.printf(currentParameterReductionPolicy(parent.exploration, "epsilon"));
          pw.printf(currentParameterReductionRate(parent.exploration, "epsilon", "exponential"));
          pw.printf(currentParameterReductionRate(parent.exploration, "epsilon", "linear"));
          pw.printf(currentParameterValue(parent.exploration, "temperature"));
          pw.printf(currentParameterReductionPolicy(parent.exploration, "temperature"));
          pw.printf(
              currentParameterReductionRate(parent.exploration, "temperature", "exponential"));
          pw.printf(currentParameterReductionRate(parent.exploration, "temperature", "linear"));

          pw.flush();
          parent.agent.getPrinter().startNewLine().print(sw.toString());

          handled = true;
        }

        return handled;
      }
    }

    private static class SetAutoUpdate implements Action<IndifferentSelection> {

      private void printValue(final IndifferentSelection context) {
        context
            .parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(currentAutoReduceSetting(context.parent.exploration));
      }

      private void setValue(final IndifferentSelection context) {
        if (context.param.equals("on")) {
          context.parent.exploration.exploration_set_auto_update(true);
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Enabled decide indifferent-selection auto-update");
        } else if (context.param.equals("off")) {
          context.parent.exploration.exploration_set_auto_update(false);
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Disabled decide indifferent-selection auto-update");
        } else {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print(
                  "Illegal argument to decide indifferent-selection --auto-reduce: "
                      + context.param);
        }
      }

      @Override
      public boolean execute(IndifferentSelection context) {
        var handled = false;

        // decide indifferent-selection --auto-reduce ...
        if (context.autoReduce) {
          if (context.param == null) {
            printValue(context);
          } else {
            setValue(context);
          }
          handled = true;
        }

        return handled;
      }
    }

    private static class SetReductionRate implements Action<IndifferentSelection> {

      private void printValue(
          final IndifferentSelection context, final String parameterName, final String policy) {
        context
            .parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(
                currentParameterReductionRate(context.parent.exploration, parameterName, policy));
      }

      private void setValue(
          final IndifferentSelection context,
          final String parameterName,
          final String policy,
          Double reductionRate) {
        // decide indifferent-selection --reduction-rate
        // epsilon/temperature exponential/linear <newRate>
        if (context.parent.exploration.exploration_set_reduction_rate(
            parameterName, policy, reductionRate)) {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print(
                  "Set "
                      + parameterName
                      + " "
                      + policy
                      + " reduction "
                      + "rate to "
                      + reductionRate);
        } else {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print(
                  "Illegal value for "
                      + parameterName
                      + " "
                      + policy
                      + " reduction rate: "
                      + reductionRate);
        }
      }

      private boolean validParameter(
          final IndifferentSelection context, final String parameterName) {
        var validParameter = context.parent.exploration.exploration_valid_parameter(parameterName);
        if (!validParameter) {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Unknown parameter name: " + parameterName);
        }
        return validParameter;
      }

      private boolean validReductionPolicy(
          final IndifferentSelection context,
          final String parameterName,
          final String reductionPolicy) {

        var valid = false;

        if (reductionPolicy == null) {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Error: exploration parameter reduction policy must be specified");
        } else {
          if (context.parent.exploration.exploration_valid_reduction_policy(
              parameterName, reductionPolicy)) {
            valid = true;
          } else {
            context
                .parent
                .agent
                .getPrinter()
                .startNewLine()
                .print("Unknown reduction policy name: " + reductionPolicy);
          }
        }

        return valid;
      }

      @Override
      public boolean execute(final IndifferentSelection context) {
        var handled = false;

        // decide indifferent-selection --reduction-rate epsilon/temperature ...
        final String parameterName = context.reductionRateParam;
        if (parameterName != null) {
          final String reductionPolicy = context.param;
          if (validParameter(context, parameterName)
              && validReductionPolicy(context, parameterName, reductionPolicy)) {
            // decide indifferent-selection --reduction-rate epsilon/temperature exponential/linear
            Double reductionRate = context.reductionRate;
            if (reductionRate == null) {
              printValue(context, parameterName, reductionPolicy);
            } else {
              setValue(context, parameterName, reductionPolicy, reductionRate);
            }
          }

          handled = true;
        }

        return handled;
      }
    }

    private static class SetReductionPolicy implements Action<IndifferentSelection> {

      private void printValue(IndifferentSelection context) {
        context
            .parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(
                currentParameterReductionPolicy(
                    context.parent.exploration, context.reductionPolicyParam));
      }

      private void setValue(IndifferentSelection context) {
        final String reductionPolicy = context.reductionPolicyParam;
        if (context.parent.exploration.exploration_set_reduction_policy(
            reductionPolicy, context.param)) {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Set " + reductionPolicy + " reduction policy to " + context.param);
        } else {
          context
              .parent
              .agent
              .getPrinter()
              .startNewLine()
              .print(
                  "Illegal value for " + reductionPolicy + " reduction policy: " + context.param);
        }
      }

      @Override
      public boolean execute(IndifferentSelection context) {
        var handled = false;

        final String reductionPolicy = context.reductionPolicyParam;
        if (reductionPolicy != null) {
          if (context.parent.exploration.exploration_valid_parameter(reductionPolicy)) {
            // decide indifferent-selection --reduction-policy
            if (context.param == null) {
              printValue(context);
            } else {
              // decide indifferent-selection --reduction-policy <value>>
              setValue(context);
            }
          } else {
            context
                .parent
                .agent
                .getPrinter()
                .startNewLine()
                .print("Unknown " + "parameter name: " + reductionPolicy);
          }

          handled = true;
        }

        return handled;
      }
    }

    private static class SetExplorationPolicy implements Action<IndifferentSelection> {

      @Override
      public boolean execute(IndifferentSelection context) {
        var handled = false;

        if (context.policy != null) {
          // decide indifferent-selection --<policyName>
          String policyName = context.policy.getName();

          if (context.parent.exploration.exploration_set_policy(policyName)) {
            context
                .parent
                .agent
                .getPrinter()
                .startNewLine()
                .print("Set decide indifferent-selection policy to " + policyName);
          } else {
            context
                .parent
                .agent
                .getPrinter()
                .startNewLine()
                .print("Failed to set decide indifferent-selection policy to " + policyName);
          }

          handled = true;
        }

        return handled;
      }
    }
  }

  @Command(
      name = "numeric-indifferent-mode",
      description =
          "Sets how multiple numeric indifferent preference "
              + "values given to an operator are combined into a single value for use in random selection",
      subcommands = {HelpCommand.class})
  public static class NumericIndifferentMode implements Runnable {

    @ParentCommand Decide parent; // injected by picocli

    @Option(
        names = {"-a", "--avg"},
        defaultValue = "false",
        description = "Combines multiple preference values via an average")
    boolean average;

    @Option(
        names = {"-s", "--sum"},
        defaultValue = "false",
        description = "Combines multiple preference values via a sum")
    boolean sum;

    @Override
    public void run() {
      // decide numeric-indifferent-mode --avg
      if (average) {
        if (parent.exploration.exploration_set_numeric_indifferent_mode("avg")) {
          parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Set decide " + "numeric-indifferent-mode to avg");
        } else {
          parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Failed to set " + "numeric-indifferent-mode to avg");
        }
      }
      // decide numeric-indifferent-mode --sum
      else if (sum) {
        if (parent.exploration.exploration_set_numeric_indifferent_mode("sum")) {
          parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Set decide " + "numeric-indifferent-mode to sum");
        } else {
          parent
              .agent
              .getPrinter()
              .startNewLine()
              .print("Failed to set " + "numeric-indifferent-mode to sum");
        }
      }
      // decide numeric-indifferent-mode
      else {
        parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(currentNumericIndifferentMode(parent.exploration));
      }
    }
  }

  @Command(
      name = "predict",
      description =
          "Based upon current operator proposals, determines "
              + "which operator will be chosen during the next decision phase",
      subcommands = {HelpCommand.class})
  public static class Predict implements Runnable {

    @ParentCommand Decide parent; // injected by picocli

    @Override
    public void run() {
      parent.agent.getPrinter().startNewLine().print(parent.decisionManipulation.predict_get());
    }
  }

  @Command(
      name = "select",
      description =
          "Forces the selection of an operator whose ID "
              + "is supplied as an argument during the next decision phase",
      subcommands = {HelpCommand.class})
  public static class Select implements Runnable {

    @ParentCommand Decide parent; // injected by picocli

    @Parameters(index = "0", arity = "0..1", description = "The operator's identifier")
    private String operatorID;

    @Override
    public void run() {
      // decide select
      if (operatorID == null) {
        String my_selection = parent.decisionManipulation.select_get_operator();
        parent
            .agent
            .getPrinter()
            .startNewLine()
            .print(Objects.requireNonNullElse(my_selection, "No operator selected."));
      }
      // decide select <identifier>
      else {
        parent.decisionManipulation.select_next_operator(operatorID);
        parent
            .agent
            .getPrinter()
            .startNewLine()
            .print("Operator " + operatorID + " will be selected.");
      }
    }
  }

  @Command(
      name = "set-random-seed",
      aliases = {"srand"},
      description = "Seeds the random number generator with the passed seed",
      subcommands = {HelpCommand.class})
  public static class SetRandomSeed implements Runnable {

    @ParentCommand Decide parent; // injected by picocli

    @Parameters(
        index = "0",
        arity = "0..1",
        description = "The seed for the random number generator")
    private Long seed;

    @Override
    public void run() {
      if (seed == null) {
        seed = System.nanoTime();
      }

      parent.agent.getRandom().setSeed(seed);
      parent.agent.getPrinter().startNewLine().print("Random number generator seed set to " + seed);
    }
  }

  private static String currentNumericIndifferentMode(Exploration exploration) {
    return PrintHelper.generateItem(
        "Numeric indifference mode:",
        exploration.exploration_get_numeric_indifferent_mode().getModeName(),
        DISPLAY_COLUMNS);
  }

  private static String currentPolicy(Exploration exploration) {
    return PrintHelper.generateItem(
        "Exploration Policy:",
        exploration.exploration_get_policy().getPolicyName(),
        DISPLAY_COLUMNS);
  }

  private static String currentParameterValue(Exploration exploration, String name) {
    return PrintHelper.generateItem(
        name.substring(0, 1).toUpperCase() + name.substring(1) + ":",
        exploration.exploration_get_parameter_value(name),
        DISPLAY_COLUMNS);
  }

  private static String currentParameterReductionPolicy(Exploration exploration, String name) {
    return PrintHelper.generateItem(
        name.substring(0, 1).toUpperCase() + name.substring(1) + " Reduction Policy:",
        exploration.exploration_get_reduction_policy(name).getPolicyName(),
        DISPLAY_COLUMNS);
  }

  private static String currentParameterReductionRate(
      Exploration exploration, String name, String policy) {
    return PrintHelper.generateItem(
        name.substring(0, 1).toUpperCase()
            + name.substring(1)
            + " "
            + policy.substring(0, 1).toUpperCase()
            + policy.substring(1)
            + " Reduction Rate:",
        exploration.exploration_get_reduction_rate(name, policy),
        DISPLAY_COLUMNS);
  }

  private static String currentAutoReduceSetting(Exploration exploration) {
    return PrintHelper.generateItem(
        "Automatic Policy Parameter Reduction:",
        exploration.exploration_get_auto_update() ? "on" : "off",
        DISPLAY_COLUMNS);
  }
}
