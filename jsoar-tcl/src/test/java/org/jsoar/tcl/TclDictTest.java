/*
 * Copyright (c) 2012 Soar Technology, Inc
 *
 * Created on Oct 19, 2012
 */
package org.jsoar.tcl;

import static org.junit.Assert.assertEquals;

import org.jsoar.kernel.RunType;
import org.junit.Test;

/** @author charles.newton */
public class TclDictTest extends TclTestBase {
  @Test
  public void testExecute() throws Exception {
    sourceTestFile(getClass(), "testExecute.soar");

    agent.runFor(1, RunType.DECISIONS);

    assertEquals("alice", ifc.eval("set value1"));
    assertEquals("bob", ifc.eval("set value2"));
  }
}
