/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Oct 19, 2008
 */
package org.jsoar.kernel.rhs.functions;

import static org.junit.Assert.assertEquals;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.io.CycleCountInput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** @author ray */
public class InterruptTest {
  private Agent agent;

  @Before
  public void setUp() throws Exception {
    this.agent = new Agent();
  }

  @After
  public void tearDown() throws Exception {}

  @Test(timeout = 3000)
  public void testInterrupt() throws Exception {
    new CycleCountInput(agent.getInputOutput());
    agent.getProperties().set(SoarProperties.WAITSNC, true);
    this.agent
        .getProductions()
        .loadProduction(
            "testInterrupt (state <s> ^superstate nil ^io.input-link.cycle-count 45) --> (interrupt)");

    this.agent.runForever();

    assertEquals("*** Interrupt from production testInterrupt ***", this.agent.getReasonForStop());
    assertEquals(45, agent.getProperties().get(SoarProperties.D_CYCLE_COUNT).intValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorThrowsExceptionIfDecisionCycleIsNull() {
    new Interrupt(null);
  }

}
