package org.jsoar.kernel.commands;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChunkCommandTest {

  private Agent agent;
  private final StringWriter outputWriter = new StringWriter();
  private ChunkCommand chunkCommand;

  @Before
  public void setUp() {
    agent = new Agent();
    agent.getPrinter().addPersistentWriter(outputWriter);
    chunkCommand = new ChunkCommand(agent);
  }

  @After
  public void tearDown() {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  @Test
  public void testEnableChunking() throws SoarException {
    chunkCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"chunk", "--enable"});

    assertTrue(agent.getProperties().get(SoarProperties.LEARNING_ON));
  }

  @Test
  public void testDisableChunking() throws SoarException {
    chunkCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"chunk", "--disable"});

    assertFalse(agent.getProperties().get(SoarProperties.LEARNING_ON));
  }

  @Test
  public void testPrintValueChunkSetting() throws SoarException {
    chunkCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"chunk"});

    String printedMessage = outputWriter.toString();
    assertNotNull(printedMessage);
    assertTrue(printedMessage.matches("\nThe current chunk setting is: .*"));
  }
}
