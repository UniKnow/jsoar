package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EchoCommandTest {

  private Agent agent;
  private final StringWriter outputWriter = new StringWriter();
  private EchoCommand echoCommand;

  @Before
  public void setUp() {
    agent = new Agent();

    agent.getPrinter().addPersistentWriter(outputWriter);
    echoCommand = new EchoCommand(agent);
  }

  @After
  public void tearDown() {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  @Test
  public void testEchoMessage() throws SoarException {
    echoCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"echo", "TEST", "ECHO", "COMMAND"});

    assertEquals("TEST ECHO COMMAND\n", outputWriter.toString());
  }

  @Test
  public void testEchoEmptyMessage() throws SoarException {
    echoCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"echo"});

    assertEquals("\n", outputWriter.toString());
  }

  @Test
  public void testEchoMessageWithNoNewLine() throws SoarException {
    echoCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"echo", "--no-newline", "TEST", "ECHO", "COMMAND"});

    assertEquals("TEST ECHO COMMAND", outputWriter.toString());
  }
}
