package org.jsoar.kernel.commands;


import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.LogManager;
import org.jsoar.kernel.LogManager.LogLevel;
import org.jsoar.kernel.SoarException;
import org.jsoar.util.commands.DefaultInterpreter;
import org.jsoar.util.commands.DefaultSoarCommandContext;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.uniknow.utils.junit.AbstractBenchmark;

public class LogCommandBenchmarkTest extends AbstractBenchmark {

  @State(Scope.Benchmark)
  public static class BenchmarkState {

    Agent agent;
    private StringWriter outputWriter = new StringWriter();
    LogManager logManager;
    LogCommand logCommand;

    @Setup(Level.Trial)
    public void initialize() {
      agent = new Agent();
      agent.getPrinter().addPersistentWriter(outputWriter);
      logManager = agent.getLogManager();
      logCommand = new LogCommand(agent, new DefaultInterpreter(agent));
    }
  }

  @Test
  public void launchBenchmarkLogMessage() throws Exception {
    launchBenchmark(this.getClass().getName() + ".logMessage", 200, 0.10);
  }

  /** This is just a performance test for when nothing should be logged. */
  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @Fork(value = 1)
  @Warmup(time = 10, iterations = 1)
  @Measurement(time = 30, iterations = 2)
  public void logMessage(BenchmarkState state) throws SoarException {
    state.logManager.setLogLevel(LogLevel.warn);

    state.logCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[] {"log", "trace", "This", "is", "a", "simple", "test", "case."});
  }

}
