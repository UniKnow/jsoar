package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoadCommandTest {

  private Agent agent;
  private final StringWriter outputWriter = new StringWriter();
  private LoadCommand loadCommand;

  @Before
  public void setUp() {
    agent = new Agent();

    agent.getPrinter().addPersistentWriter(outputWriter);

    loadCommand = new LoadCommand(mock(SourceCommand.class), mock(SpCommand.class), agent);
  }

  @After
  public void tearDown() {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  @Test
  public void testRestoreReteNetWhenFileDoesNotExist() throws SoarException {
    Path nonExistingFile = Paths.get("NON-EXISTING");
    loadCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"load", "rete-net", "--load", nonExistingFile.toAbsolutePath().toString()});

    assertEquals(
        "\nFile not found: " + nonExistingFile.toAbsolutePath() + "\nError: Load file failed.",
        outputWriter.toString());
  }

  @Test
  public void testRestoreReteNetWhenEmptyFile() throws SoarException, IOException {
    File file = File.createTempFile("invalid", null);
    loadCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"load", "rete-net", "--load", file.getAbsolutePath()});

    assertEquals(
        """

                 0: ==>S: S1\s
            Error: Load file failed.""",
        outputWriter.toString());
  }
}
