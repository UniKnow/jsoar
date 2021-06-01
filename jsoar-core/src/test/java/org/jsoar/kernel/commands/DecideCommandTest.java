package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
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

  @Test
  public void testMultiplePoliciesSpecified() throws SoarException {
    // When specifying multiple policies
    decideCommand.execute(DefaultSoarCommandContext.empty(),
        new String[]{"decide", "indifferent-selection", "--first", "--last"});

    // Then error message is printed
    String printedMessage = outputWriter.toString();
    assertEquals("\nindifferent-selection takes only one option at a time.", printedMessage);

  }
}
