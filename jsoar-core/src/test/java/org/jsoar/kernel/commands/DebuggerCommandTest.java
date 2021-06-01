/*
 * Copyright (c) 2010 Dave Ray <daveray@gmail.com>
 *
 * Created on Jun 9, 2010
 */
package org.jsoar.kernel.commands;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.StringWriter;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.DebuggerProvider;
import org.jsoar.kernel.SoarException;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.Test;

public class DebuggerCommandTest {

  @Test
  public void testOpenDebuggerOnAgent() throws Exception {
    final Agent agent = new Agent("testDebuggerCommandCallsOpenDebuggerOnAgent");
    final DebuggerProvider provider = mock(DebuggerProvider.class);
    agent.setDebuggerProvider(provider);

    final DebuggerCommand command = new DebuggerCommand(agent);
    command.execute(DefaultSoarCommandContext.empty(), new String[] {"debugger"});

    verify(provider, times(1)).openDebugger(agent);
  }

  @Test
  public void testOpenDebuggerOnAgentFails() throws Exception {
    final String errorMessage = "TEST FAIL OPEN DEBUGGER";

    // Given a agent
    final StringWriter outputWriter = new StringWriter();
    final Agent agent = new Agent("testDebuggerCommandCallsOpenDebuggerOnAgent");
    agent.getPrinter().addPersistentWriter(outputWriter);

    final DebuggerProvider provider = mock(DebuggerProvider.class);
    doThrow(new SoarException(errorMessage)).when(provider).openDebugger(agent);
    agent.setDebuggerProvider(provider);

    // When opening debugger
    final DebuggerCommand command = new DebuggerCommand(agent);
    command.execute(DefaultSoarCommandContext.empty(), new String[] {"debugger"});
    // And opening fails

    // Then error that occurred is printed
    verify(provider, times(1)).openDebugger(agent);
    assertTrue(outputWriter.toString().contains(errorMessage));
  }
}
