package org.jsoar.kernel.io.commands;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import org.jsoar.kernel.events.OutputEvent;
import org.jsoar.kernel.memory.Wme;
import org.jsoar.kernel.symbols.Identifier;
import org.jsoar.util.events.SoarEvent;
import org.jsoar.util.events.SoarEventListener;
import org.jsoar.util.events.SoarEventManager;

/**
 * Convenience class for working with string commands on the output link.
 *
 * @see OutputCommandHandler
 */
public class OutputCommandManager {
  private final Map<String, OutputCommandHandler> commandHandlers;
  private final SoarEventListener listener;
  private final SoarEventManager eventManager;

  /**
   * Construct a new {@link OutputCommandManager} and attach it to an {@link SoarEventManager}.
   *
   * @param eventManager the event manager to listen to.
   */
  public OutputCommandManager(SoarEventManager eventManager) {
    commandHandlers = Maps.newConcurrentMap();
    this.eventManager = eventManager;
    this.listener =
        new SoarEventListener() {
          @Override
          public void onEvent(SoarEvent soarEvent) {
            OutputEvent event = (OutputEvent) soarEvent;

            Collection<Wme> pendingCommands =
                Collections2.filter(
                    event.getInputOutput().getPendingCommands(), new ValidityPredicate());
            for (final Wme wme : pendingCommands) {
              String name = wme.getAttribute().asString().getValue();
              Identifier identifier = wme.getValue().asIdentifier();
              OutputCommandHandler handler = commandHandlers.get(name);
              if (handler == null) {
                continue;
              }
              handler.onCommandAdded(name, identifier);
            }

            Collection<Wme> removingCommands =
                Collections2.filter(
                    event.getInputOutput().getRemovingCommands(), new ValidityPredicate());
            for (final Wme wme : removingCommands) {
              String name = wme.getAttribute().asString().getValue();
              Identifier identifier = wme.getValue().asIdentifier();
              OutputCommandHandler handler = commandHandlers.get(name);
              if (handler == null) {
                continue;
              }
              handler.onCommandRemoved(name, identifier);
            }
          }
        };
    this.eventManager.addListener(OutputEvent.class, listener);
  }

  /**
   * Add a new {@link OutputCommandHandler} that will be fired when the specified command is added
   * to the output link.
   *
   * @param commandName name of the command
   * @param commandHandler handler for the command.
   */
  public void registerHandler(String commandName, OutputCommandHandler commandHandler) {
    commandHandlers.put(commandName, commandHandler);
  }

  /** Dispose this object, removing it from the event manager */
  public void dispose() {
    this.eventManager.removeListener(null, listener);
  }

  /**
   * Check if a Wme fits our assumptions about a command handled by {@link OutputCommandHandler}.
   */
  private static class ValidityPredicate implements Predicate<Wme> {
    @Override
    public boolean apply(Wme wme) {
      // Attribute of wme should be a string.
      if (wme.getAttribute().asString() == null) {
        return false;
      }
      // Value of wme should be an identifier.
      if (wme.getValue().asIdentifier() == null) {
        return false;
      }
      return true;
    }
  }
}
