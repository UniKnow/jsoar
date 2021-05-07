/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 28, 2008
 */
package org.jsoar.kernel.learning;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.lhs.Condition;
import org.jsoar.kernel.lhs.Conditions;
import org.jsoar.kernel.rhs.Action;
import org.jsoar.kernel.tracing.Printer;
import org.jsoar.util.properties.BooleanPropertyProvider;

/**
 * <em>This is an internal interface. Don't use it unless you know what you're doing.</em>
 *
 * <p>agent.h:564:explain_chunk_name - Eliminated. Not used in CSoar
 *
 * @author ray
 */
public class Explain {
  private final Agent context;

  /** agent.h:563:explain_chunk_list */
  private ExplainChunk explain_chunk_list;

  /** agent.h:562:explain_backtrace_list */
  private Backtrace explain_backtrace_list;

  /**
   * gsysparam.h:140:EXPLAIN_SYSPARAM
   *
   * <p>Defaults to false in init_soar()
   *
   * <p>TODO There's probably more to do here (EXPLAIN_SYSPARAM on)
   */
  private BooleanPropertyProvider enabled = new BooleanPropertyProvider(SoarProperties.EXPLAIN);

  public Explain(Agent agent) {
    this.context = agent;
    this.context.getProperties().setProvider(SoarProperties.EXPLAIN, enabled);
  }

  /** @return the enabled */
  boolean isEnabled() {
    return enabled.value.get();
  }

  /** explain.cpp:105:reset_backtrace_list */
  void reset_backtrace_list() {
    explain_backtrace_list = null;
  }

  /** explain.cpp:155:explain_add_temp_to_backtrace_list */
  public void explain_add_temp_to_backtrace_list(
      Backtrace temp,
      LinkedList<Condition> grounds,
      LinkedList<Condition> pots,
      LinkedList<Condition> locals,
      LinkedList<Condition> negateds) {
    Backtrace back = new Backtrace();
    back.result = temp.result;
    back.trace_cond = Condition.copy_condition(temp.trace_cond);
    if (back.trace_cond != null) back.trace_cond.next = null;
    back.prod_name = temp.prod_name;

    back.grounds = Condition.copy_conds_from_list(grounds);
    back.potentials = Condition.copy_conds_from_list(pots);
    back.locals = Condition.copy_conds_from_list(locals);
    back.negated = Condition.copy_conds_from_list(negateds);

    back.next_backtrace = explain_backtrace_list;
    explain_backtrace_list = back;
  }

  /**
   * Allocate a new chunk structure and copy the information in the temp structure to it. Also copy
   * in the current "explain_backtrace_list" and reset that list. We want to copy all the
   * information in the chunk/justification in case it is excised or retracted later on and you
   * still want an explanation. Therefore each item used is carefully copied, rather than just
   * keeping a pointer.
   *
   * <p>explain.cpp:190:explain_add_temp_to_chunk_list
   */
  public void explain_add_temp_to_chunk_list(ExplainChunk temp) {
    ExplainChunk chunk = new ExplainChunk();
    chunk.conds = temp.conds;
    chunk.actions = temp.actions;
    chunk.name = temp.name;
    chunk.backtrace = explain_backtrace_list;
    explain_backtrace_list = null;

    chunk.all_grounds = Condition.copy_cond_list(temp.all_grounds);

    chunk.next_chunk = explain_chunk_list;
    explain_chunk_list = chunk;
  }

  /**
   * Called by init_soar()
   *
   * <p>explain.cpp:237:reset_explain
   */
  public void reset_explain() {
    explain_chunk_list = null;
    reset_backtrace_list();
  }

  /**
   * Find the data structure associated with an explain chunk by searching for its name.
   *
   * <p>explain.cpp:263:find_chunk
   */
  private ExplainChunk find_chunk(ExplainChunk chunk, String name) {
    while (chunk != null) {
      if (name.equals(chunk.name)) return chunk;
      chunk = chunk.next_chunk;
    }

    context
        .getPrinter()
        .print("Could not find the chunk.  Maybe explain was not on when it was created.");
    /* BUGBUG: this doesn't belong here!  changed for bug 608 */
    context
        .getPrinter()
        .print("\nTo turn on explain: save-backtraces --enable before the chunk is created.\n");

    return null;
  }

  /** explain.cpp:337:explain_trace_named_chunk */
  public void explain_trace_named_chunk(String chunk_name) {
    ExplainChunk chunk = find_chunk(explain_chunk_list, chunk_name);
    if (chunk != null) {
      chunk.explain_trace_chunk(context.getPrinter());
    }
  }

  /**
   * Search the backtrace structures to explain why the given condition appeared in the chunk.
   *
   * <p>explain.cpp:371:explain_trace
   */
  public void explain_trace(String chunk_name, Backtrace prod_list, Condition ground) {
    /* Find which prod. inst. tested the ground originally to get
    it included in the chunk.
    Need to check potentials too, in case they got included
    later on.                                                  */

    Backtrace prod = prod_list;
    Condition match = null;
    while (prod != null && match == null) {
      match = Condition.explain_find_cond(ground, prod.potentials);
      if (match == null) match = Condition.explain_find_cond(ground, prod.grounds);
      if (match == null) match = Condition.explain_find_cond(ground, prod.negated);
      if (match == null) prod = prod.next_backtrace;
    }

    final Printer printer = context.getPrinter();
    if (match == null) {
      printer.print("EXPLAIN: Error, couldn't find the ground condition\n");
      return;
    }

    printer.print("Explanation of why condition %s was included in %s\n\n", ground, chunk_name);
    printer.print("Production %s matched\n   %s which caused\n", prod.prod_name, match);

    /* Trace back the series of productions to find which one
    caused the matched condition to be created.
    Build in a safety limit of tracing 50 productions before cancelling.
    This is in case there is a loop in the search procedure somehow or
    a really long sequence of production firings.  Either way you probably
    don't want to see more than 50 lines of junk....                       */

    Condition target = prod.trace_cond;
    int count = 0;

    while (prod.result == false && count < 50 && match != null) {
      prod = prod_list;
      match = null;
      count++;
      while (prod != null && match == null) {
        match = Condition.explain_find_cond(target, prod.locals);
        /* Going to check all the other lists too just to be sure */
        if (match == null) match = Condition.explain_find_cond(target, prod.negated);
        if (match == null) match = Condition.explain_find_cond(target, prod.potentials);
        if (match == null) match = Condition.explain_find_cond(target, prod.grounds);
        if (match == null) prod = prod.next_backtrace;
      }

      if (match == null) {
        printer.print("EXPLAIN : Unable to find which production matched condition %s\n", target);
        printer.print(
            "To help understand what happened here and help debug this\n"
                + "here is all of the backtracing information stored for this chunk.\n"
                + "\n");
        explain_trace_named_chunk(chunk_name);
      } else {
        printer.print("production %s to match\n   %s which caused\n", prod.prod_name, match);
        target = prod.trace_cond;
      }
    }

    if (prod.result) printer.print("A result to be generated.\n");
    if (count >= 50)
      printer.print("EXPLAIN: Exceeded 50 productions traced through, so terminating now.\n");
  }

  /**
   * Explain why the numbered condition appears in the given chunk.
   *
   * <p>explain.cpp:455:Explain why the numbered condition appears in the given chunk.
   */
  public void explain_chunk(String chunk_name, int cond_number) {
    ExplainChunk chunk = find_chunk(explain_chunk_list, chunk_name);

    if (chunk == null) return;

    Condition ground = chunk.find_ground(context.getPrinter(), cond_number);
    if (ground == null) return;

    explain_trace(chunk_name, chunk.backtrace, ground);
  }

  /**
   * List all of the conditions and number them for a named chunk.
   *
   * <p>explain.cpp:476:explain_cond_list
   */
  public void explain_cond_list(String chunk_name) {
    ExplainChunk chunk = find_chunk(explain_chunk_list, chunk_name);
    if (chunk == null) return;

    // First print out the production in "normal" form
    final Printer printer = context.getPrinter();
    printer.print("(sp %s\n  ", chunk.name);
    Conditions.print_condition_list(printer, chunk.conds, 2, false);
    printer.print("\n-->\n   ");
    Action.print_action_list(printer, chunk.actions, 3, false);
    printer.print(")\n\n");

    /* Then list each condition and the associated "ground" WME */

    int i = 0;
    Condition ground = chunk.all_grounds;

    for (Condition cond = chunk.conds; cond != null; cond = cond.next) {
      i++;
      printer.print(" %2d : %s", i, cond);
      printer.print(" Ground :%s\n", ground);
      ground = ground.next;
    }
  }
  /**
   * New for JSoar to support testing and structured uses of explanations
   *
   * @return List of explanations for chunks.
   */
  public List<ExplainChunk> getChunkExplanations() {
    final List<ExplainChunk> result = new ArrayList<ExplainChunk>();
    ExplainChunk c = explain_chunk_list;
    while (c != null) {
      result.add(c);
      c = c.next_chunk;
    }
    return result;
  }

  /** explain.cpp:518:explain_list_chunks */
  public void explain_list_chunks() {
    ExplainChunk chunk = explain_chunk_list;

    if (chunk == null) context.getPrinter().print("No chunks/justifications built yet!\n");
    else {
      context.getPrinter().print("List of all explained chunks/justifications:\n");
      while (chunk != null) {
        context.getPrinter().print("Have explanation for %s\n", chunk.name);
        chunk = chunk.next_chunk;
      }
    }
  }

  /** explain.cpp:540:explain_full_trace */
  public void explain_full_trace() {
    ExplainChunk chunk = explain_chunk_list;

    while (chunk != null) {
      chunk.explain_trace_chunk(context.getPrinter());
      chunk = chunk.next_chunk;
    }
  }
}
