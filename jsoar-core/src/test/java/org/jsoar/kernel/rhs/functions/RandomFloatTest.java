/*
 * Copyright (c) 2009 Dave Ray <daveray@gmail.com>
 *
 * Created on Jul 4, 2009
 */
package org.jsoar.kernel.rhs.functions;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Random;
import org.jsoar.JSoarTest;
import org.jsoar.kernel.symbols.Symbol;
import org.junit.Test;

/** @author ray */
public class RandomFloatTest extends JSoarTest {
  @Test
  public void testExpectedName() {
    final RandomFloat rf = new RandomFloat(new Random());
    assertEquals("random-float", rf.getName());
  }

  @Test
  public void testRandomFloat() throws Exception {
    final RandomFloat rf = new RandomFloat(new Random());
    for (int i = 0; i < 5000; ++i) {
      final Symbol result = rf.execute(rhsFuncContext, new ArrayList<Symbol>());
      assertNotNull(result);
      assertNotNull(result.asDouble());
      final double value = result.asDouble().getValue();
      assertTrue(value >= 0.0 && value < 1.0);
    }
  }
}
