package org.jsoar.kernel.commands;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.symbols.DoubleSymbol;
import org.jsoar.kernel.symbols.Identifier;
import org.jsoar.kernel.symbols.IntegerSymbol;
import org.jsoar.kernel.symbols.JavaSymbol;
import org.jsoar.kernel.symbols.StringSymbol;
import org.jsoar.kernel.symbols.Variable;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DebugCommandTest {

  private Agent agent;
  private final StringWriter outputWriter = new StringWriter();
  private DebugCommand debugCommand;

  @Before
  public void setUp() {
    agent = new Agent();

    agent.getPrinter().addPersistentWriter(outputWriter);
    debugCommand = new DebugCommand(agent);
  }

  @After
  public void tearDown() {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  @Test
  public void testPrintInternalSymbols() throws SoarException {
    debugCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"debug", "internal-symbols"});

    String printedMessage = outputWriter.toString();
    assertNotNull(printedMessage);
    assertTrue(printedMessage.contains("\n--- " + Identifier.class + " ("));
    assertTrue(printedMessage.contains("\n--- " + StringSymbol.class + " ("));
    assertTrue(printedMessage.contains("\n--- " + IntegerSymbol.class + " ("));
    assertTrue(printedMessage.contains("\n--- " + DoubleSymbol.class + " ("));
    assertTrue(printedMessage.contains("\n--- " + Variable.class + " ("));
    assertTrue(printedMessage.contains("\n--- " + JavaSymbol.class + " ("));
  }
}
