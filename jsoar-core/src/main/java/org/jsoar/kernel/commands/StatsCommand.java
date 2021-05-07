package org.jsoar.kernel.commands;

import java.util.Calendar;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.JSoarVersion;
import org.jsoar.kernel.ProductionType;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.tracing.Printer;
import org.jsoar.util.commands.PicocliSoarCommand;
import org.jsoar.util.properties.PropertyManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

/**
 * This is the implementation of the "stats" command.
 *
 * @author austin.brehob
 */
public class StatsCommand extends PicocliSoarCommand {

  public StatsCommand(Agent agent) {
    super(agent, new Stats(agent));
  }

  @Command(
      name = "stats",
      description = "Prints information on Soar's runtime statistics",
      subcommands = {HelpCommand.class})
  public static class Stats implements Runnable {
    private final Agent agent;

    public Stats(Agent agent) {
      this.agent = agent;
    }

    @Option(
        names = {"-s", "--system"},
        defaultValue = "false",
        description = "Prints system statistics")
    boolean printSystemStats;

    @Override
    public void run() {
      final Printer p = agent.getPrinter();
      p.startNewLine();

      final JSoarVersion version = JSoarVersion.getInstance();
      p.print(
          "JSoar %s on %s at %s%n",
          version.getVersion(), System.getenv("HOSTNAME"), Calendar.getInstance().getTime());
      p.print("Built on %s by %s%n%n", version.getBuildDate(), version.getBuiltBy());
      p.print(
          "%d productions (%d default, %d user, %d chunks)%n   + %d justifications%n",
          agent.getProductions().getProductions(null).size(),
          agent.getProductions().getProductions(ProductionType.DEFAULT).size(),
          agent.getProductions().getProductions(ProductionType.USER).size(),
          agent.getProductions().getProductions(ProductionType.CHUNK).size(),
          agent.getProductions().getProductions(ProductionType.JUSTIFICATION).size());
      p.print("\n");
      p.print(
          "Values from single timers:%n"
              + " Kernel CPU Time: %f sec. %n"
              + " Total  CPU Time: %f sec. %n%n",
          agent.getTotalKernelTimer().getTotalSeconds(),
          agent.getTotalCpuTimer().getTotalSeconds());

      final PropertyManager props = agent.getProperties();
      final long decision_phases_count = props.get(SoarProperties.DECISION_PHASES_COUNT);
      final long e_cycle_count = props.get(SoarProperties.E_CYCLE_COUNT);
      final long pe_cycle_count = props.get(SoarProperties.PE_CYCLE_COUNT);
      final long inner_e_cycle_count = props.get(SoarProperties.INNER_E_CYCLE_COUNT);
      p.print(
          "%d decisions%n" + "%d elaboration cycles%n" + "%d inner elaboration cycles%n",
          decision_phases_count, e_cycle_count, inner_e_cycle_count);

      final double total_kernel_msec = agent.getTotalKernelTimer().getTotalSeconds() * 1000.0;
      p.print(
          "%d p-elaboration cycles (%f pe's per dc, %f msec/pe)%n",
          pe_cycle_count,
          decision_phases_count != 0 ? ((double) pe_cycle_count / decision_phases_count) : 0.0,
          pe_cycle_count != 0 ? total_kernel_msec / pe_cycle_count : 0.0);

      final long production_firing_count = props.get(SoarProperties.PRODUCTION_FIRING_COUNT);
      p.print(
          "%d production firings (%f pf's per ec, %f msec/pf)%n",
          production_firing_count,
          e_cycle_count != 0 ? ((double) production_firing_count / e_cycle_count) : 0.0,
          production_firing_count != 0 ? total_kernel_msec / production_firing_count : 0.0);

      final long wme_additions = props.get(SoarProperties.WME_ADDITION_COUNT);
      final long wme_removes = props.get(SoarProperties.WME_REMOVAL_COUNT);
      final long wme_changes = wme_additions + wme_removes;
      p.print(
          "%d wme changes (%d additions, %d removals)%n", wme_changes, wme_additions, wme_removes);

      final long num_wm_sizes_accumulated = props.get(SoarProperties.NUM_WM_SIZES_ACCUMULATED);
      p.print(
          "WM size: %d current, %f mean, %d maximum%n",
          agent.getNumWmesInRete(),
          num_wm_sizes_accumulated != 0
              ? ((double) props.get(SoarProperties.CUMULATIVE_WM_SIZE).intValue()
                  / num_wm_sizes_accumulated)
              : 0.0,
          props.get(SoarProperties.MAX_WM_SIZE));
    }
  }
}
