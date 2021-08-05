/*
 * Copyright (c) 2009 Dave Ray <daveray@gmail.com>
 *
 * Created on May 22, 2009
 */
package org.jsoar.kernel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import org.jsoar.JSoarTest;
import org.jsoar.kernel.parser.ParserException;
import org.jsoar.kernel.rhs.ReordererException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** @author ray */
public class DefaultProductionManagerTest extends JSoarTest {
  private Agent agent;
  private ProductionManager pm;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.agent = new Agent();
    this.pm = this.agent.getProductions();
  }

  @After
  public void tearDown() throws Exception {}

  /**
   * Test method for {@link
   * org.jsoar.kernel.DefaultProductionManager#getProduction(java.lang.String)}.
   */
  @Test
  public void testGetProduction() throws Exception {
    final Production p =
        pm.loadProduction("   testGetProduction (state <s> ^superstate nil) --> (<s> ^foo bar)");
    assertNotNull(p);
    assertSame(p, pm.getProduction("testGetProduction"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetParserThrowsExceptionIfParserIsNull() {
    DefaultProductionManager productionManager = new DefaultProductionManager(mock(Agent.class));
    productionManager.setParser(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testLoadProductionThrowsExceptionIfLocationIsNull()
      throws ParserException, ReordererException {
    DefaultProductionManager productionManager = new DefaultProductionManager(mock(Agent.class));
    productionManager.loadProduction("productionBody", null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAddProductionThrowsExceptionIfDuplicateProduction()
      throws ReordererException, ParserException {
    // Given a production manager
    // And a existing Production with name
    final Production p =
        pm.loadProduction(
            "   testAddProductionThrowsExceptionIfDuplicateProduction (state <s> ^superstate nil) --> (<s> ^foo bar)");

    // When adding similar production again
    // Then exception should be thrown
    pm.addProduction(p, false);
  }
}
