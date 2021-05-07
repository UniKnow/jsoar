/*
 * Copyright (c) 2010 Dave Ray <daveray@gmail.com>
 *
 * Created on Jun 11, 2010
 */
package org.jsoar.kernel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.List;
import org.jsoar.kernel.rhs.functions.AbstractRhsFunctionHandler;
import org.jsoar.kernel.rhs.functions.RhsFunctionContext;
import org.jsoar.kernel.rhs.functions.RhsFunctionException;
import org.jsoar.kernel.rhs.functions.RhsFunctionHandler;
import org.jsoar.kernel.symbols.Symbol;
import org.junit.After;
import org.junit.Before;

public class FunctionalTestHarness {
  public Agent agent;

  protected boolean halted = false;
  protected boolean failed = false;

  /**
   * Sources rules
   *
   * @param testName the test to perform
   * @throws SoarException
   */
  public void runTestSetup(String testName) throws SoarException {
    String sourceName = getClass().getSimpleName() + "_" + testName + ".soar";
    URL sourceUrl = getClass().getResource(sourceName);
    assertNotNull("Could not find test file " + sourceName, sourceUrl);
    agent.getInterpreter().source(sourceUrl);
  }

  // this function assumes some other function has set up the agent (like runTestSetup)
  public void runTestExecute(String testName, int expectedDecisions) throws Exception {
    if (expectedDecisions >= 0) {
      agent.runFor(expectedDecisions + 1, RunType.DECISIONS);
    } else {
      agent.runForever();
    }

    assertTrue(testName + " functional test did not halt", halted);
    assertFalse(testName + " functional test failed", failed);
    if (expectedDecisions >= 0) {
      assertEquals(
          expectedDecisions,
          agent
              .getProperties()
              .get(SoarProperties.DECISION_PHASES_COUNT)
              .intValue()); // deterministic!
    }

    agent.getInterpreter().eval("stats");
  }

  protected void runTest(String testName, int expectedDecisions) throws Exception {
    runTestSetup(testName);
    runTestExecute(testName, expectedDecisions);
  }

  /** @throws java.lang.Exception */
  @Before
  public void setUp() throws Exception {
    halted = false;
    failed = false;
    agent = new Agent();

    installRHS(agent);
  }

  /** @throws java.lang.Exception */
  @After
  public void tearDown() throws Exception {
    agent.getPrinter().flush();
    agent.dispose();
  }

  /** Set up the agent with RHS functions common to these FunctionalTests. */
  public void installRHS(Agent agent) {
    // set up the agent with common RHS functions
    final RhsFunctionHandler oldHalt = agent.getRhsFunctions().getHandler("halt");
    assertNotNull(oldHalt);

    agent
        .getRhsFunctions()
        .registerHandler(
            new AbstractRhsFunctionHandler("halt") {

              @Override
              public Symbol execute(RhsFunctionContext rhsContext, List<Symbol> arguments)
                  throws RhsFunctionException {
                halted = true;
                return oldHalt.execute(rhsContext, arguments);
              }
            });
    agent
        .getRhsFunctions()
        .registerHandler(
            new AbstractRhsFunctionHandler("failed") {

              @Override
              public Symbol execute(RhsFunctionContext rhsContext, List<Symbol> arguments)
                  throws RhsFunctionException {
                halted = true;
                failed = true;
                return oldHalt.execute(rhsContext, arguments);
              }
            });
    agent
        .getRhsFunctions()
        .registerHandler(
            new AbstractRhsFunctionHandler("succeeded") {

              @Override
              public Symbol execute(RhsFunctionContext rhsContext, List<Symbol> arguments)
                  throws RhsFunctionException {
                halted = true;
                failed = false;
                return oldHalt.execute(rhsContext, arguments);
              }
            });
  }
}
