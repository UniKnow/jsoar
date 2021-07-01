package org.jsoar.kernel.commands;

@FunctionalInterface
public interface Action<T> {

  /**
   * Executes action
   *
   * @param context Context in which the action needs to be performed
   * @return true if action has executed; false otherwise
   */
  boolean execute(final T context);
}
