package org.jsoar.kernel.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.commands.SourceCommand.FileInfo;
import org.jsoar.kernel.commands.SourceCommand.TopLevelState;
import org.jsoar.kernel.events.ProductionAddedEvent;
import org.jsoar.kernel.events.ProductionExcisedEvent;
import org.jsoar.kernel.rete.ReteSerializer;
import org.jsoar.util.FileTools;
import org.jsoar.util.commands.PicocliSoarCommand;
import org.jsoar.util.events.SoarEvent;
import org.jsoar.util.events.SoarEventListener;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * This is the implementation of the "load" command.
 *
 * @author austin.brehob
 */
public class LoadCommand extends PicocliSoarCommand {
  public LoadCommand(SourceCommand sourceCommand, SpCommand spCommand, Agent agent) {
    super(agent, new Load(sourceCommand, spCommand, agent));
  }

  @Command(
      name = "load",
      description = "Loads a file or rete-net",
      subcommands = {HelpCommand.class, LoadCommand.FileC.class, LoadCommand.ReteNet.class})
  public static class Load implements Runnable {
    private SourceCommand sourceCommand;
    private PicocliSoarCommand spCommand;
    private Agent agent;

    public Load(SourceCommand sourceCommand, SpCommand spCommand, Agent agent) {
      this.sourceCommand = sourceCommand;
      this.spCommand = spCommand;
      this.agent = agent;
    }

    @Override
    public void run() {
      agent.getPrinter().startNewLine().print("File type is required.");
    }
  }

  @Command(
      name = "file",
      description = "Loads and evaluates the contents of a file.",
      subcommands = {HelpCommand.class})
  public static class FileC implements Runnable {
    @ParentCommand Load parent; // injected by picocli

    @Option(
        names = {"-a", "--all"},
        defaultValue = "false",
        description = "Enables a summary for each file sourced")
    boolean loadSummary;

    @Option(
        names = {"-d", "--disable"},
        defaultValue = "false",
        description = "Disables all summaries")
    boolean disableSummaries;

    @Option(
        names = {"-v", "--verbose"},
        defaultValue = "false",
        description = "Prints all excised production names")
    boolean printExcised;

    @Parameters(arity = "0..*", description = "File names")
    String[] fileNames;

    @Override
    public void run() {
      // File name is required unless -r option is provided
      if (fileNames == null) {
        parent.agent.getPrinter().startNewLine().print("Error: file name(s) required");
        return;
      }

      final boolean topLevel = parent.sourceCommand.topLevelState == null;

      // If this is the top source command (user-initiated), set up the
      // state info and register for production events
      if (topLevel) {
        parent.sourceCommand.topLevelState = new TopLevelState();
        parent.spCommand.autoFlush(false);
        parent.sourceCommand.events.addListener(ProductionAddedEvent.class, eventListener);
        parent.sourceCommand.events.addListener(ProductionExcisedEvent.class, eventListener);
      }

      try {
        for (String file : fileNames) {
          try {
            parent.sourceCommand.source(file);
          } catch (SoarException e) {
            parent.agent.getPrinter().startNewLine().print("Error: " + e.getMessage());
            return;
          }
        }

        if (topLevel) {
          // Construct an array containing each word in the current
          // command and assign it to "lastTopLevelCommand"
          var lastCommand = new String[fileNames.length + 2];
          lastCommand[0] = "load";
          lastCommand[1] = "file";
          for (var i = 0; i < fileNames.length; i++) {
            lastCommand[i + 2] = fileNames[i];
          }
          parent.sourceCommand.lastTopLevelCommand = Arrays.copyOf(lastCommand, lastCommand.length);
        }

        // Generate a message depending on the files loaded/excised and the user-provided options
        final var result = new StringBuilder();
        if (topLevel) {
          if (loadSummary) {
            for (FileInfo file : parent.sourceCommand.topLevelState.files) {
              result.append(
                  String.format(
                      "%s: %d productions sourced.\n", file.name, file.productionsAdded.size()));
              if (printExcised && !file.productionsExcised.isEmpty()) {
                result.append("Excised productions:\n");
                for (String p : file.productionsExcised) {
                  result.append("        " + p + "\n");
                }
              }
            }
          }

          if (!disableSummaries) {
            result.append(
                String.format(
                    "Total: %d productions sourced. " + "%d productions excised.\n",
                    parent.sourceCommand.topLevelState.totalProductionsAdded,
                    parent.sourceCommand.topLevelState.totalProductionsExcised));
          }

          if (printExcised
              && !loadSummary
              && parent.sourceCommand.topLevelState.totalProductionsExcised != 0) {
            result.append("Excised productions:\n");
            for (FileInfo file : parent.sourceCommand.topLevelState.files) {
              for (String p : file.productionsExcised) {
                result.append("        " + p + "\n");
              }
            }
          }
          result.append("Source finished.");
        }

        parent.agent.getPrinter().startNewLine().print(result.toString());
      } finally {
        // Clean up top-level state
        if (topLevel) {
          parent.spCommand.autoFlush(true);
          parent.sourceCommand.topLevelState = null;
          parent.sourceCommand.events.removeListener(null, eventListener);
        }
      }
    }

    private final SoarEventListener eventListener =
        new SoarEventListener() {
          @Override
          public void onEvent(SoarEvent event) {
            if (event instanceof ProductionAddedEvent) {
              parent.sourceCommand.topLevelState.productionAdded(
                  ((ProductionAddedEvent) event).getProduction());
            } else if (event instanceof ProductionExcisedEvent) {
              parent.sourceCommand.topLevelState.productionExcised(
                  ((ProductionExcisedEvent) event).getProduction());
            }
          }
        };
  }

  @Command(
      name = "rete-net",
      description =
          "Restores an agent's productions from "
              + "a binary file. Loading productions from a rete-net file causes all "
              + "prior productions in memory to be excised.",
      subcommands = {HelpCommand.class})
  public static class ReteNet implements Runnable {
    @ParentCommand Load parent; // injected by picocli

    @Option(
        names = {"-l", "--load", "-r", "--restore"},
        arity = "1",
        description = "File name to load rete-net from")
    String fileName;

    @Override
    public void run() {

      try (InputStream is = uncompressIfNeeded(fileName, findFile(fileName))) {
        ReteSerializer.replaceRete(parent.agent, is);
        parent.agent.getPrinter().startNewLine().print("Rete loaded into agent");
      } catch (IOException e) {
        parent.agent.getPrinter().startNewLine().print("Error: Load file failed.");
      } catch (SoarException e) {
        parent.agent.getPrinter().startNewLine().print("Error: " + e.getMessage());
      }
    }

    /** Construct an InputStream from a file or URL. */
    private InputStream findFile(String fileString) throws IOException {
      final var url = FileTools.asUrl(fileString);
      var file = new File(fileString);
      if (url != null) {
        return url.openStream();
      } else if (file.isAbsolute()) {
        if (!file.exists()) {
          parent.agent.getPrinter().startNewLine().print("File not found: " + fileString);
        }
        return new FileInputStream(file);
      } else if (parent.sourceCommand.getWorkingDirectoryRaw().url != null) {
        final var childUrl =
            parent.sourceCommand.joinUrl(
                parent.sourceCommand.getWorkingDirectoryRaw().url, fileString);
        return childUrl.openStream();
      } else {
        file = new File(parent.sourceCommand.getWorkingDirectoryRaw().file, file.getPath());
        if (!file.exists()) {
          parent.agent.getPrinter().startNewLine().print("File not found: " + fileString);
        }
        return new FileInputStream(file);
      }
    }

    private InputStream uncompressIfNeeded(String filename, InputStream is) throws IOException {
      if (filename.endsWith(".Z")) {
        return new GZIPInputStream(is);
      }
      return is;
    }
  }
}
