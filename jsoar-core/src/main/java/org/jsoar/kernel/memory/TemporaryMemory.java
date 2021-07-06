/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 7, 2008
 */
package org.jsoar.kernel.memory;

import java.util.LinkedList;
import org.jsoar.kernel.learning.Chunker;
import org.jsoar.kernel.symbols.IdentifierImpl;
import org.jsoar.util.ListHead;
import org.jsoar.util.ListItem;
import org.jsoar.util.adaptables.Adaptable;
import org.jsoar.util.adaptables.Adaptables;

/**
 * <em>This is an internal interface. Don't use it unless you know what you're doing.</em>
 *
 * <p>Slot Garbage Collection
 *
 * <p>Old slots are garbage collected as follows: whenever we notice that the last preference has
 * been removed from a slot, we call mark_slot_for_possible_removal(). We don't deallocate the slot
 * right away, because there might still be wmes in it, or we might be about to add a new preference
 * to it (through some later action of the same production firing, for example).
 *
 * <p>At the end of the phases, we call remove_garbage_slots(), which scans through each marked slot
 * and garbage collects it if it has no wmes or preferences.
 *
 * <p>tempmem.cpp
 *
 * @author ray
 */
public class TemporaryMemory {
  /**
   * agent.h:601:highest_goal_whose_context_changed
   *
   * <p>TODO Move to Decider?
   */
  public IdentifierImpl highest_goal_whose_context_changed;

  /** agent.h:602:changed_slots */
  public final ListHead<Slot> changed_slots = ListHead.newInstance();

  /**
   * Contains the slots which are marked for possible removal at the end of the current top-level
   * phases
   */
  private final LinkedList<Slot> slotsForPossibleRemoval = new LinkedList<>();

  /**
   * Mark_slot_as_changed() is called by the preference manager whenever the preferences for a slot
   * change. This updates the list of changed_slots and highest_goal_whose_context_changed for use
   * by the decider.
   *
   * <p>tempmem.cpp:116:mark_slot_as_changed
   */
  public void mark_slot_as_changed(Slot s) {
    if (s.isContextSlot()) {
      if ((this.highest_goal_whose_context_changed == null)
          || (s.id.getLevel() < this.highest_goal_whose_context_changed.getLevel())) {
        this.highest_goal_whose_context_changed = s.id;
      }
      s.changed = s; // just make it nonzero
    } else {
      if (s.changed == null) {
        ListItem<Slot> dc = new ListItem<>(s);
        s.changed = dc;
        dc.insertAtHead(changed_slots);
      }
    }
  }

  /**
   * tempmem.cpp:153:mark_slot_for_possible_removal
   *
   * @param s
   */
  public void mark_slot_for_possible_removal(Slot s) {
    if (s.marked_for_possible_removal) {
      return;
    }
    s.marked_for_possible_removal = true;
    slotsForPossibleRemoval.push(s);
  }

  /** tempmem.cpp:159:remove_garbage_slots */
  @SuppressWarnings("unchecked")
  public void remove_garbage_slots(final Adaptable context) {
    while (!slotsForPossibleRemoval.isEmpty()) {
      final var s = slotsForPossibleRemoval.pop();

      if (!s.getWmes().isEmpty() || s.getAllPreferences() != null) {
        // don't deallocate it if it still has any wmes or preferences
        s.marked_for_possible_removal = false;
        continue;
      }

      /* --- deallocate the slot --- */
      // #ifdef DEBUG_SLOTS
      // print_with_symbols (thisAgent, "\nDeallocate slot %y ^%y", s->id,
      // s->attr);
      // #endif

      final var chunker = Adaptables.adapt(context, Chunker.class);
      if (s.hasContextDependentPreferenceSet() && chunker.chunkThroughEvaluationRules) {
        s.clear_CDPS(context);
      }

      if (s.changed != null && !s.isContextSlot()) {
        final ListItem<Slot> changed = (ListItem<Slot>) s.changed;
        changed.remove(changed_slots);
      }
      s.id.removeSlot(s);

      if (s.wma_val_references != null) {
        s.wma_val_references = null;
      }
    }
  }
}
