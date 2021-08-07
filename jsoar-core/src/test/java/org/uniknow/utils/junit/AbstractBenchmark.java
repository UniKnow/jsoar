package org.uniknow.utils.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.text.DecimalFormat;
import java.util.Collection;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class AbstractBenchmark {

  private static DecimalFormat df = new DecimalFormat("0.000");

  protected void launchBenchmark(String benchmarkMethod, double referenceScore, double maxDeviation)
      throws Exception {

    Options opt =
        new OptionsBuilder()
            // Specify which benchmarks to run.
            // You can be more specific if you'd like to run only one benchmark per test.
            .include(this.getClass().getName() + ".logMessage")
            // Set the following options as needed
            .mode(Mode.AverageTime)
            // .timeUnit(TimeUnit.MICROSECONDS)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            // .jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
            // .addProfiler(WinPerfAsmProfiler.class)
            .build();

    Collection<RunResult> results = new Runner(opt).run();

    assertEquals("Benchmark didn't produce any results", 1, results.size());
    assertResultBenchmark(results.iterator().next(), referenceScore, maxDeviation);
  }

  private void assertResultBenchmark(RunResult result, double referenceScore, double maxDeviation) {
    double score = result.getPrimaryResult().getScore();
    double deviation = score / referenceScore - 1;
    String maxDeviationString = df.format(maxDeviation * 100) + "%";
    String benchmarkMethodName = result.getPrimaryResult().getLabel();
    String errorMessage =
        "Measured score "
            + score
            + " of benchmark method "
            + benchmarkMethodName
            + " exceeds reference score ("
            + referenceScore
            + "±"
            + maxDeviationString
            + ")";
    assertFalse(errorMessage, deviation > 0 && deviation >= maxDeviation);
  }
}
