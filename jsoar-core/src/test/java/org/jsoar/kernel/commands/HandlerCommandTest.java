package org.jsoar.kernel.commands;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.regex.Pattern;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.LogManager;
import org.jsoar.kernel.LogManager.EchoMode;
import org.jsoar.kernel.LogManager.LogLevel;
import org.jsoar.kernel.RunType;
import org.jsoar.kernel.rhs.functions.RhsFunctionManager;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HandlerCommandTest {
  private Agent agent;
  private StringWriter outputWriter = new StringWriter();

  @Before
  public void setUp() throws Exception {
    agent = new Agent();

    agent.getPrinter().addPersistentWriter(outputWriter);
  }

  @After
  public void tearDown() throws Exception {
    if (agent != null) {
      agent.dispose();
      agent = null;
    }
  }

  private void clearBuffer() {
    outputWriter.getBuffer().setLength(0);
  }

  @Test
  public void testHandlerEnableDisable() throws Exception {
    RhsFunctionManager rhsFunctionManager = agent.getRhsFunctions();

    rhsFunctionManager.enableHandler("log");
    assertFalse(rhsFunctionManager.isDisabled("log"));

    rhsFunctionManager.disableHandler("log");
    assertTrue(rhsFunctionManager.isDisabled("log"));

    rhsFunctionManager.enableHandler("log");
    assertFalse(rhsFunctionManager.isDisabled("log"));
  }

  @Test
  public void testHandlerDisabledFunction() throws Exception {
    // Variables
    Pattern regex = Pattern.compile("^Simple test$", Pattern.MULTILINE);
    LogManager logManager = agent.getLogManager();
    HandlerCommand handlerCommand = new HandlerCommand(agent);

    // Configure the logger
    logManager.setEchoMode(EchoMode.simple);
    logManager.setLogLevel(LogLevel.trace);
    logManager.setActive(true);

    // Test enabled.
    handlerCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"handler", "--enable", "log"});
    agent
        .getProductions()
        .loadProduction("test (state <s> ^superstate nil) --> (log info |Simple test|)");
    clearBuffer();
    agent.runFor(1, RunType.DECISIONS);
    assertTrue(regex.matcher(outputWriter.toString()).find());

    // Test disabled.
    handlerCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"handler", "--disable", "log"});
    agent
        .getProductions()
        .loadProduction("test2 (state <s> ^superstate nil) --> (log info |Simple test|)");
    clearBuffer();
    agent.runFor(1, RunType.DECISIONS);
    assertFalse(regex.matcher(outputWriter.toString()).find());

    // Test enabled.
    handlerCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"handler", "--enable", "log"});
    agent
        .getProductions()
        .loadProduction("test3 (state <s> ^superstate nil) --> (log info |Simple test|)");
    clearBuffer();
    agent.runFor(1, RunType.DECISIONS);
    assertTrue(regex.matcher(outputWriter.toString()).find());
  }

  @Test
  public void testHandlerList() throws Exception {
    Pattern regex = Pattern.compile("^\\s*log\\s*$", Pattern.MULTILINE);
    HandlerCommand handlerCommand = new HandlerCommand(agent);
    String result;

    handlerCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"handler", "--enable", "log"});
    result = handlerCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"handler"});
    assertFalse(regex.matcher(result).find());

    handlerCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"handler", "--disable", "log"});
    result = handlerCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"handler"});
    assertTrue(regex.matcher(result).find());

    handlerCommand.execute(
        DefaultSoarCommandContext.empty(), new String[] {"handler", "--enable", "log"});
    result = handlerCommand.execute(DefaultSoarCommandContext.empty(), new String[] {"handler"});
    assertFalse(regex.matcher(result).find());
  }
}
