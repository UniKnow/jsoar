/*
 * Copyright (c) 2010 Dave Ray <daveray@gmail.com>
 *
 * Created on Aug 21, 2010
 */
package org.jsoar.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.rhs.functions.RhsFunctionHandler;
import org.jsoar.kernel.rhs.functions.RhsFunctionManager;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.jsoar.util.events.SoarEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScriptCommandTest {
  private Agent agent;
  private ScriptCommand command;

  public static class TestEvent implements SoarEvent {}
  ;

  @Before
  public void setUp() throws Exception {
    agent = new Agent();
    command = new ScriptCommand(agent);
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = SoarException.class)
  public void testThrowsAnExceptionForUnknownScriptEngines() throws Exception {
    command.execute(
        DefaultSoarCommandContext.empty(), new String[] {"script", "unknown-script-engine"});
  }

  @Test
  public void testCanEvalScriptCode() throws Exception {
    final String result =
        command.execute(
            DefaultSoarCommandContext.empty(), new String[] {"script", "javascript", "'hi there'"});
    assertEquals("hi there", result);
  }

  @Test
  public void testInstallsRhsFunctionHandler() throws Exception {
    // Initialize javascript engine
    command.execute(DefaultSoarCommandContext.empty(), new String[] {"script", "javascript"});

    final RhsFunctionManager rhsFuncs = agent.getRhsFunctions();
    assertNotNull(rhsFuncs);

    final RhsFunctionHandler handler = rhsFuncs.getHandler("javascript");
    assertNotNull(handler);
  }

  @Test
  public void testCanCleanupRegisteredListenersWhenReset() throws Exception {
    final Agent agent = new Agent("testCanCleanupRegisteredListenersWhenReset");
    try {
      // Initialize javascript engine and register a handler for our test
      // event. It throws an exception.
      agent
          .getInterpreter()
          .eval(
              "script javascript { soar.onEvent('org.jsoar.script.ScriptCommandTest$TestEvent', function() { throw 'Failed'; }); }");
      // reset javascript engine
      agent.getInterpreter().eval("script --reset javascript");

      // Now if everything went right, firing the test event should have
      // no effect
      agent.getEvents().fireEvent(new TestEvent());
    } finally {
      agent.dispose();
    }
  }

  @Test
  public void testCanCleanupRegistersRhsFunctionsWhenReset() throws Exception {
    final Agent agent = new Agent("testCanCleanupRegistersRhsFunctionsWhenReset");
    try {
      // Initialize javascript engine and register a handler for our test
      // function. It throws an exception.
      agent
          .getInterpreter()
          .eval(
              "script javascript {\n"
                  + "soar.rhsFunction( { name: 'cleanup-test', \n"
                  + "   execute: function(context, args) { throw 'Failed'; } "
                  + "});\n"
                  + "\n}");

      // reset javascript engine
      agent.getInterpreter().eval("script --reset javascript");

      // Now if everything went right, firing the test event should have
      // no effect
      assertNull(agent.getRhsFunctions().getHandler("cleanup-test"));
    } finally {
      agent.dispose();
    }
  }
}
