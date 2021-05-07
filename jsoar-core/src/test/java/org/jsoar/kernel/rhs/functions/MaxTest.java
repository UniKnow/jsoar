/*
 * Copyright (c) 2009 Dave Ray <daveray@gmail.com>
 *
 * Created on Jun 28, 2009
 */
package org.jsoar.kernel.rhs.functions;

import static org.junit.Assert.*;

import org.jsoar.JSoarTest;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.Symbols;
import org.junit.Test;

/** @author ray */
public class MaxTest extends JSoarTest {

  @Test
  public void testMaxWithAllIntegers() throws Exception {
    final Max max = new Max();
    final Symbol result = max.execute(rhsFuncContext, Symbols.asList(syms, 2, 1, -40, 99));
    assertEquals(99, result.asInteger().getValue());
  }

  @Test
  public void testMaxWithLargeIntegers() throws Exception {
    final Max max = new Max();
    final Symbol result =
        max.execute(rhsFuncContext, Symbols.asList(syms, 2, 1000000000000L, -40, 99));
    assertEquals(1000000000000L, result.asInteger().getValue());
  }

  @Test
  public void testMaxWithAllDoubles() throws Exception {
    final Max max = new Max();
    final Symbol result = max.execute(rhsFuncContext, Symbols.asList(syms, 2., 1., -40., 99.));
    assertEquals(99., result.asDouble().getValue(), 0.00001);
  }

  @Test
  public void testMaxWithMixedTypesAndIntResult() throws Exception {
    final Max max = new Max();
    final Symbol result = max.execute(rhsFuncContext, Symbols.asList(syms, 2., 1., -40, 99));
    assertEquals(99, result.asInteger().getValue());
  }

  @Test
  public void testMaxWithMixedTypesAndDoubleResult() throws Exception {
    final Max max = new Max();
    final Symbol result = max.execute(rhsFuncContext, Symbols.asList(syms, 2, 1., -40., 99.));
    assertEquals(99., result.asDouble().getValue(), 0.0001);
  }
}
