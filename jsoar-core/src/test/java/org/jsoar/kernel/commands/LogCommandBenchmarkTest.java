package org.jsoar.kernel.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.map.HashedMap;
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

public class LogCommandBenchmarkTest {

  private static DecimalFormat df = new DecimalFormat("0.000");

  private class ExpectedBenchmark {

  }

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
  public void launchBenchmarkLogMessage() throws Exception{
    launchBenchmark(this.getClass().getName() + ".logMessage", 200,0.10);
  }

  public void launchBenchmark(String benchmarkMethod, double referenceScore, double maxDeviation) throws Exception {

    Options opt = new OptionsBuilder()
        // Specify which benchmarks to run.
        // You can be more specific if you'd like to run only one benchmark per test.
        .include(this.getClass().getName() + ".logMessage")
        // Set the following options as needed
        .mode(Mode.AverageTime)
        //.timeUnit(TimeUnit.MICROSECONDS)
        .shouldFailOnError(true)
        .shouldDoGC(true)
        //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
        //.addProfiler(WinPerfAsmProfiler.class)
        .build();

    Collection<RunResult> results = new Runner(opt).run();

    assertEquals(1, results.size());
    assertResultBenchmark(results.iterator().next(), referenceScore, maxDeviation);
  }

  /**
   * This is just a performance test for when nothing should be logged.
   */
  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @Fork(value = 1)
  @Warmup(time = 10, iterations = 1)
  @Measurement(time = 30, iterations = 2)
  public void logMessage(BenchmarkState state) throws SoarException {
    state.logManager.setLogLevel(LogLevel.warn);

    state.logCommand.execute(
        DefaultSoarCommandContext.empty(),
        new String[]{"log", "trace", "This", "is", "a", "simple", "test", "case."});
  }

  @Benchmark
  public void otherBenchmark(BenchmarkState state) throws SoarException {
  }

  private void assertResultBenchmark(RunResult result, double referenceScore, double maxDeviation) {
    double score = result.getPrimaryResult().getScore();
    double deviation = score / referenceScore - 1;
    String deviationString = df.format(deviation * 100) + "%";
    String maxDeviationString = df.format(maxDeviation * 100) + "%";
    String benchmarkMethodName = result.getPrimaryResult().getLabel();
    String errorMessage =
        "Deviation " + deviationString + " of benchmark method " + benchmarkMethodName + " exceeds maximum allowed deviation " + maxDeviationString;
    assertFalse(errorMessage, deviation > 0 && deviation >= maxDeviation);
  }

//  /**
//   * Assert benchmark results that are interesting for us
//   * Asserting test mode and average test time
//   * @param results
//   */
//  private void assertOutputs(Collection<RunResult> results) {
//    for (RunResult r : results) {
//      for (BenchmarkResult rr : r.getBenchmarkResults()) {
//
//        Mode mode = rr.getParams().getMode();
//        double score = rr.getPrimaryResult().getScore();
//        String methodName = rr.getPrimaryResult().getLabel();
//
//        Assert.assertEquals("Test mode is not average mode. Method = " + methodName ,
//            Mode.AverageTime, mode);
//        Assert.assertTrue("Benchmark score = " + score + " is higher than " + AVERAGE_EXPECTED_TIME + " " + rr.getScoreUnit() + ". Too slow performance !",
//            score < AVERAGE_EXPECTED_TIME);
//      }
//    }
//  }
}
