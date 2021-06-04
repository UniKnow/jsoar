package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.exploration.Exploration;
import org.jsoar.kernel.exploration.Exploration.Policy;
import org.jsoar.kernel.exploration.ExplorationParameter.ReductionPolicy;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DecideCommandTest {

  private Agent agent;
  private final StringWriter outputWriter = new StringWriter();
  private DecideCommand decideCommand;

  @Before
  public void setUp() {
    agent = new Agent();

    agent.getPrinter().addPersistentWriter(outputWriter);
    decideCommand = new DecideCommand(agent);
  }

  @After
  public void tearDown() {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  @Test(expected = SoarException.class)
  public void testExecuteThrowsExceptionIfMultiplePoliciesSpecified() throws SoarException {
    // When specifying multiple policies
    // Then exception occurs
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--first", "--last"});
  }

  @Test
  public void testSetExplorationPolicyToBoltzmann() throws SoarException {
    setExplorationPolicy("-b", Policy.USER_SELECT_BOLTZMANN);
    setExplorationPolicy("--boltzmann", Policy.USER_SELECT_BOLTZMANN);
  }

  @Test
  public void testSetExplorationPolicyToEpsilonGreedy() throws SoarException {
    setExplorationPolicy("-E", Policy.USER_SELECT_E_GREEDY);
    setExplorationPolicy("--epsilon-greedy", Policy.USER_SELECT_E_GREEDY);
  }

  @Test
  public void testSetExplorationPolicyToFirst() throws SoarException {
    setExplorationPolicy("-f", Policy.USER_SELECT_FIRST);
    setExplorationPolicy("--first", Policy.USER_SELECT_FIRST);
  }

  @Test
  public void testSetExplorationPolicyToLast() throws SoarException {
    setExplorationPolicy("-l", Policy.USER_SELECT_LAST);
    setExplorationPolicy("--last", Policy.USER_SELECT_LAST);
  }

  @Test
  public void testSetExplorationPolicyToSoftMax() throws SoarException {
    setExplorationPolicy("-s", Policy.USER_SELECT_SOFTMAX);
    setExplorationPolicy("--softmax", Policy.USER_SELECT_SOFTMAX);
  }

  @Test
  public void testPrintValueEpsilon() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--epsilon"});

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nEpsilon:.*\\n"));
  }

  @Test
  public void testSetValueTemperature() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--temperature", "0.5"});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertEquals(0.5, exploration.exploration_get_parameter_value("temperature"), 0);

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nSet .* to 0.5"));
  }

  @Test
  public void testSetValueTemperatureWithIllegalValue() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--temperature", "-1.0"});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertNotEquals(-1.0, exploration.exploration_get_parameter_value("temperature"), 0);

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nIllegal value for .*"));
  }

  @Test
  public void testSetValueTemperatureWithNoNumber() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--temperature", "INVALID"});

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nINVALID is not a valid double: .*"));
  }

  @Test
  public void testSetValueEpsilon() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--epsilon", "0.5"});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertEquals(0.5, exploration.exploration_get_parameter_value("epsilon"), 0);

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nSet .* to 0.5"));
  }

  @Test
  public void testSetValueEpsilonWithIllegalValue() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--epsilon", "2.0"});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertNotEquals(2.0, exploration.exploration_get_parameter_value("epsilon"), 0);

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nIllegal value for .*"));
  }

  @Test
  public void testSetValueEpsilonWithNoNumber() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--epsilon", "INVALID"});

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nINVALID is not a valid double: .*"));
  }

  private void setExplorationPolicy(String argument, Policy expectedPolicy) throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", argument});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertEquals(expectedPolicy, exploration.exploration_get_policy());

    assertEquals(
        "\nSet decide indifferent-selection policy to " + expectedPolicy.getPolicyName(),
        outputWriter.toString());

    // clear buffer
    outputWriter.getBuffer().setLength(0);
  }

  @Test
  public void testSetValueReductionPolicy() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-policy", "epsilon", "exponential"
        });

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertEquals(
        ReductionPolicy.EXPLORATION_REDUCTION_EXPONENTIAL,
        exploration.exploration_get_reduction_policy("epsilon"));

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nSet .* to exponential"));
  }

  @Test
  public void testSetValueReductionPolicyNonExistingProperty() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-policy", "non-existing", "exponential"
        });

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nUnknown parameter name: non-existing"));
  }

  @Test
  public void testSetValueReductionPolicyInvalidValue() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-policy", "epsilon", "invalid"
        });

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nIllegal value for epsilon reduction policy: invalid"));
  }

  @Test
  public void testPrintAutoUpdate() throws SoarException {
    // Turn auto update on
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--auto-reduce"});

    String printedMessage = outputWriter.toString();
    assertTrue(printedMessage.matches("\\nAutomatic Policy Parameter Reduction:.*\\n"));
  }

  @Test
  public void testSetAutoUpdateOn() throws SoarException {
    // Turn auto update on
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--auto-reduce", "on"});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertTrue(exploration.exploration_get_auto_update());
  }

  @Test
  public void testSetAutoUpdateOff() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--auto-reduce", "off"});

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertFalse(exploration.exploration_get_auto_update());
  }

  @Test
  public void testSetAutoUpdateInvalidValue() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--auto-reduce", "invalid"});

    assertEquals(
        "\nIllegal argument to decide indifferent-selection --auto-reduce: invalid",
        outputWriter.toString());
  }

  @Test
  public void testSetReductionRate() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-rate", "temperature", "linear", "10.5"
        });

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertEquals(10.5, exploration.exploration_get_reduction_rate("temperature", "linear"), 0);
  }

  @Test
  public void testSetReductionRateWithIllegalValue() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-rate", "temperature", "linear", "-1"
        });

    assertEquals(
        "\nIllegal value for temperature linear reduction rate: -1.0", outputWriter.toString());
  }

  @Test
  public void testSetReductionRateWithUnknownParameterName() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--reduction-rate", "unknown"});

    assertEquals("\nUnknown parameter name: unknown", outputWriter.toString());
  }

  @Test
  public void testSetReductionRateWithNoReductionPolicySpecified() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"decide", "indifferent-selection", "--reduction-rate", "temperature"});

    assertEquals(
        "\nError: exploration parameter reduction policy must be specified",
        outputWriter.toString());
  }

  @Test
  public void testSetReductionRateWithUnknownReductionPolicy() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-rate", "temperature", "unknown"
        });

    Exploration exploration = Adaptables.adapt(agent, Exploration.class);
    assertEquals("\nUnknown reduction policy name: unknown", outputWriter.toString());
  }

  @Test
  public void testPrintStats() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {
          "decide", "indifferent-selection", "--reduction-rate", "temperature", "unknown"
        });

    String printedMessage = outputWriter.toString();
    assertNotNull(printedMessage);
    assertFalse(printedMessage.isBlank());
    assertFalse(printedMessage.isEmpty());
  }

  @Test
  public void testPrintPolicy() throws SoarException {
    decideCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"decide", "indifferent-selection"});

    String printedMessage = outputWriter.toString();
    assertNotNull(printedMessage);
    assertFalse(printedMessage.isBlank());
    assertFalse(printedMessage.isEmpty());
  }
}
