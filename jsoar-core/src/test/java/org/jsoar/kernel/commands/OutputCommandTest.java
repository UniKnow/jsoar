package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.util.Arrays;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.commands.PrintCommand.Print;
import org.jsoar.kernel.tracing.Printer;
import org.jsoar.util.TeeWriter;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class OutputCommandTest {

  private Agent agent;
  private Printer printer;
  private OutputCommand outputCommand;
  private Print print;

  @Before
  public void setUp() throws Exception {
    agent = mock(Agent.class);
    printer = mock(Printer.class);

    when(agent.getPrinter()).thenReturn(printer);

    when(printer.asPrintWriter()).thenReturn(mock(PrintWriter.class));
    when(printer.startNewLine()).thenReturn(printer);
    when(printer.print(any(String.class))).thenReturn(printer);

    print = new Print(agent);
    outputCommand = new OutputCommand(agent, print);
  }

  @Test
  public void testCloseLogWithNoWriters() throws SoarException {
    // Given a log without any writers
    // When closing log
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "log", "--close"});

    // Then 'log is not open' message is printed
    verify(printer, times(1)).print("Log is not open.");
  }

  @Test
  public void testCloseLog() throws SoarException {
    // Given a log with writers
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "log", "stdout"});

    // When closing log
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "log", "--close"});

    // Then writer is removed from printer agent
    verify(printer, times(1)).popWriter();
    // And message that logger is closed is printer
    verify(printer, times(1)).print("Log file closed.");
  }

  @Test
  public void testSetLogToStdOut() throws SoarException {
    setLogTo("stdout", "Now writing to System.out");
  }

  @Test
  public void testSetLogToStdErr() throws SoarException {
    setLogTo("stderr", "Now writing to System.err");
  }

  @Test
  public void testSetLogToLogFile() throws SoarException {
    setLogTo("log.file", "Log file log.file open.");
  }

  @Test
  public void testPrintDepth() throws SoarException {
    // Given default depth is 4
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "print-depth", "4"});

    // When printing depth
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "print-depth"});

    // Then message showing depth is printed
    verify(printer, times(1)).print("print-depth is 4");
  }

  @Test
  public void testSetPrintDepth() throws SoarException {
    // When setting print depth
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "print-depth", "2"});

    // Then message that print depth has been set is printed
    verify(printer, times(1)).print("print-depth is now 2");
    // And default depth of print is set to 2
    assertEquals(2, print.getDefaultDepth());
  }

  @Test(expected = SoarException.class)
  public void testSetPrintDepthThrowsExceptionWhenInvalidValue() throws SoarException {
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "print-depth", "-1"});
  }

  @Test
  public void testPrintStateLog() throws SoarException {
    // Given a log without any writers
    // When printing state log
    outputCommand.execute(DefaultSoarCommandContext.empty(), new String[]{"output", "log"});

    // Then log is off
    verify(printer, times(1)).print("log is off");

    // When adding writer
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "log", "stdout"});
    // And printing state log
    outputCommand.execute(DefaultSoarCommandContext.empty(), new String[]{"output", "log"});

    // Then log is on
    verify(printer, times(1)).print("log is on");
  }

  @Test
  public void testEnableWarnings() throws SoarException {
    outputCommand
        .execute(DefaultSoarCommandContext.empty(), new String[]{"output", "warnings", "--enable"});

    verify(printer, times(1)).setPrintWarnings(true);
    verify(printer, times(1)).print("warnings is now on");
  }

  @Test
  public void testDisableWarnings() throws SoarException {
    outputCommand.execute(DefaultSoarCommandContext.empty(),
        new String[]{"output", "warnings", "--disable"});

    verify(printer, times(1)).setPrintWarnings(false);
    verify(printer, times(1)).print("warnings is now off");
  }

  @Test
  public void testPrintWarningsToggle() throws SoarException {
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

    // Given toggle warning is turned off
    outputCommand.execute(DefaultSoarCommandContext.empty(),
        new String[]{"output", "warnings", "--disable"});

    // When printing warning toggle
    outputCommand.execute(DefaultSoarCommandContext.empty(), new String[]{"output", "warnings"});

    // Then current state of warning toggle is printed
    verify(printer, times(2)).print(argumentCaptor.capture());
    assertEquals(Arrays.asList("warnings is now off", "warnings is off"),
        argumentCaptor.getAllValues());
  }

  private void setLogTo(final String destination, final String message) throws SoarException {
    // Given a log without any writers
    // When adding writer
    outputCommand.execute(
        DefaultSoarCommandContext.empty(), new String[]{"output", "log", destination});

    // Then new writer is pushed to printer agent
    verify(printer, times(1)).pushWriter(any(TeeWriter.class));
    // And message is printed that writing to stdout
    verify(printer, times(1)).print(message);
  }
}
