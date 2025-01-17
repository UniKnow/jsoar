package org.jsoar.runtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.Test;

public class SwingCompletionHandlerTest {

  @Test(expected = IllegalArgumentException.class)
  public void testNewInstanceThrowsExceptionWhenInnerCompletionHandlerIsNull() {
    SwingCompletionHandler.newInstance(null);
  }

  @Test
  public void testFinish() {
    // Given a Swing completion handler
    // And a nested inner completion handler
    CompletionHandler<String> innerCompletionHandler = mock(CompletionHandler.class);
    CompletionHandler<String> swingCompletionHandler =
        SwingCompletionHandler.newInstance(innerCompletionHandler);

    // When finishing with result
    String result = "RESULT";
    swingCompletionHandler.finish(result);

    // Then finish on inner completion handler is called
    // And result is passed
    // NOTE: Since finish invocation is put on queue and not immediately executed included timeout
    verify(innerCompletionHandler, timeout(10000).times(1)).finish(result);
  }
}
