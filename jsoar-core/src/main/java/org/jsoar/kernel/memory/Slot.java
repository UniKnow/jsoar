/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 15, 2008
 */
package org.jsoar.kernel.memory;

import com.google.common.collect.Iterators;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import org.jsoar.kernel.ImpasseType;
import org.jsoar.kernel.PredefinedSymbols;
import org.jsoar.kernel.symbols.IdentifierImpl;
import org.jsoar.kernel.symbols.StringSymbolImpl;
import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.SymbolImpl;
import org.jsoar.kernel.tracing.Trace;
import org.jsoar.kernel.tracing.Trace.Category;
import org.jsoar.util.adaptables.Adaptable;
import org.jsoar.util.adaptables.Adaptables;

/**
 * WARNING: This is an internal interface. Don't use it unless you know what you're doing.
 *
 * @author ray
 */
public class Slot {

  /*
   * used for a doubly-linked list of all slots for a certain identifier
   */
  public Slot next;
  public Slot prev;

  /** Identifier of slot */
  @NonNull public final IdentifierImpl id;

  /** Attributes of slot */
  @Getter public final SymbolImpl attr;

  private final List<WmeImpl> wmes = new ArrayList<>(); // dll of wmes in the slot

  /*
   * List of all acceptable preference wmes in the slot. (This is only used for context slots.)
   */
  private final List<WmeImpl> acceptablePreferenceWMEs = new ArrayList<>();

  /** header of a doubly-linked list of all preferences currently in the slot */
  private Preference all_preferences; // dll of all pref's in the slot

  /**
   * These store all the preferences, sorted into lists according to their types. Within each list,
   * the preferences are sorted according to their match goal, with the pref. supported by the
   * highest goal at the head of the list.
   */
  private EnumMap<PreferenceType, Preference> preferencesByType;

  /**
   * A list of preferences in the context-dependent preference set, which is the set of all
   * preferences that contributed to an operator's selection. This is used to allow Soar to
   * backtrace through evaluation rules in substates. The rules that determine which preferences are
   * in the CPSD are outlined in run_preference_semantics().
   */
  private final LinkedList<Preference> cdps = new LinkedList<>();

  /**
   * Points to the identifier of the attribute impasse object for this slot. (Null if slot is not
   * impassed.)
   */
  public IdentifierImpl impasse_id = null;

  /** TRUE if this is a context slot */
  @Getter private final boolean contextSlot;

  /**
   * indicates the type of the impasse for this slot. This is one of NONE_IMPASSE_TYPE,
   * CONSTRAINT_FAILURE_IMPASSE_TYPE, etc.
   */
  @Getter private ImpasseType impasseType = ImpasseType.NONE;

  /**
   * TRUE if this slot is on the list of slots that might be deallocated at the end of the current
   * top-level phases.
   */
  public boolean marked_for_possible_removal = false;

  /**
   * Indicates whether the preferences for this slot have changed. For non-context slots, this is
   * either Null or a pointer to the corresponding dl_cons in changed_slots; for context slots, it's
   * just a zero/nonzero flag.
   *
   * <p>TODO Sub-class instead of using this for two things
   */
  public Object changed;

  /**
   * For context slots only; this is zero if no acceptable or require preference in this slot has
   * changed; if one has changed, it points to a dl_cons.
   *
   * <p>TODO Sub-class instead of using this for two things
   */
  public Object acceptable_preference_changed;

  public Map<Symbol, Long> wma_val_references;

  /**
   * Find or create a new slot
   *
   * <p>tempmem.cpp:64:make_slot
   *
   * @param id the id of the slot
   * @param attr the attribute of the slot
   * @param operator_symbol the operator symbol from {@link PredefinedSymbols}
   * @return the slot. A new one is constructed if a slot for the given id/attr doesn't already
   *     exist.
   */
  public static Slot make_slot(
      IdentifierImpl id, SymbolImpl attr, StringSymbolImpl operator_symbol) {
    // Search for a slot first.  If it exists for the given symbol, then just return it
    final var s = find_slot(id, attr);
    if (s != null) {
      return s;
    }

    return new Slot(id, attr, operator_symbol);
  }

  /**
   * Construct a new slot
   *
   * <p>tempmem.cpp:64:make_slot
   *
   * @param id the slot identifier
   * @param attr the slot attribute
   * @param operator_symbol the operator symbol from {@link PredefinedSymbols}. May be null if you
   *     know that {@code attr} is not "operator". TODO get rid of this param
   */
  private Slot(IdentifierImpl id, SymbolImpl attr, StringSymbolImpl operator_symbol) {
    id.addSlot(this);

    /*
     * Context slots are goals and operators; operator slots get created
     * with a goal (see create_new_context).
     */
    this.contextSlot = (id.isGoal()) && (attr == operator_symbol);

    this.id = id;
    this.attr = attr;
  }

  /**
   * Find_slot() looks for an existing slot for a given id/attr pair, and returns it if found. If no
   * such slot exists, it returns `null`.
   *
   * <p>`tempmem.cpp:55:find_slot`
   *
   * @param id the slot identifier
   * @param attr the slot attribute
   * @return the slot, or `null` if not found
   */
  public static Slot find_slot(final IdentifierImpl id, final Symbol attr) {
    if (id != null) {
      for (Slot s = id.slots; s != null; s = s.next) {
        if (s.getAttr() == attr) {
          return s;
        }
      }
    }
    return null;
  }

  /**
   * Returns the head of the list of preferences with the given type. When iterating over this list,
   * you should use {@link Preference#next}.
   *
   * @param type The type of preference
   * @return The head of the list
   */
  public Preference getPreferencesByType(PreferenceType type) {
    if (preferencesByType == null) {
      return null;
    }
    return preferencesByType.get(type);
  }

  /** @return the head of the list of WMEs in this slot */
  @NonNull
  public List<WmeImpl> getWmes() {
    return List.copyOf(this.wmes);
  }

  /**
   * Add a WME to the head of the list of WMEs in this slot
   *
   * @param w the WME to add
   */
  public void addWme(WmeImpl w) {
    wmes.add(0, w);
  }

  /**
   * Remove a WME from the list of WMEs in this slot.
   *
   * @param w the WME to remove
   */
  public void removeWme(WmeImpl w) {
    wmes.remove(w);
  }

  /** Remove all WMEs from this slot */
  public void removeAllWmes() {
    wmes.clear();
  }

  /**
   * Returns an iterator over all the WMEs in this slot. Note that this should not be used for
   * performance critical code.
   *
   * @return An iterator over the wmes in this slot
   */
  public Iterator<Wme> getWmeIterator() {
    return Iterators.concat(acceptablePreferenceWMEs.iterator(), wmes.iterator());
  }

  public List<WmeImpl> getAcceptablePreferenceWmes() {
    return List.copyOf(acceptablePreferenceWMEs);
  }

  public void addAcceptablePreferenceWme(WmeImpl wme) {
    acceptablePreferenceWMEs.add(0, wme);
  }

  public void removeAcceptablePreferenceWme(WmeImpl w) {
    acceptablePreferenceWMEs.remove(w);
  }

  /** @return Head of list of all preferences. Iterate with {@link Preference#nextOfSlot}. */
  public Preference getAllPreferences() {
    return all_preferences;
  }

  public void addPreference(Preference pref) {
    pref.slot = this;

    pref.nextOfSlot = all_preferences;
    pref.previousOfSlot = null;
    if (all_preferences != null) {
      all_preferences.previousOfSlot = pref;
    }
    all_preferences = pref;

    addPreferenceToCorrectTypeList(pref);
  }

  public void removePreference(Preference pref) {
    pref.slot = null;

    removePreferenceByType(pref);

    if (pref.nextOfSlot != null) {
      pref.nextOfSlot.previousOfSlot = pref.previousOfSlot;
    }
    if (pref.previousOfSlot != null) {
      pref.previousOfSlot.nextOfSlot = pref.nextOfSlot;
    } else {
      all_preferences = pref.nextOfSlot;
    }
    pref.nextOfSlot = null;
    pref.previousOfSlot = null;
  }

  /**
   * Adds a new preference to the correct type list, in the correct position.
   *
   * <p>This method is extracted from prefmem.cpp:add_preference_to_tm
   */
  private void addPreferenceToCorrectTypeList(Preference pref) {
    // add preference to the list (in the right place, according to match
    // goal level of the instantiations) for the slot
    var s_prefs = this.getPreferencesByType(pref.type);
    if (s_prefs == null) {
      // this is the only pref. of its type, just put it at the head
      this.addPreferenceByType(pref, null);
    } else if (s_prefs.inst.match_goal_level >= pref.inst.match_goal_level) {
      // it belongs at the head of the list, so put it there
      this.addPreferenceByType(pref, null);
    } else {
      // scan through the pref. list, find the one to insert after
      Preference it = s_prefs;
      for (; it.next != null; it = it.next) {
        if (it.inst.match_goal_level >= pref.inst.match_goal_level) {
          break;
        }
      }

      // insert pref after it
      this.addPreferenceByType(pref, it);
    }
  }

  /**
   * Add a preference to this slot by type. This adds the preference to the appropriate list as
   * returned by {@link #getPreferencesByType(PreferenceType)}.
   *
   * @param pref The preference to add
   * @param after If non-null, the preference is added <b>after</b> this one.
   */
  private void addPreferenceByType(Preference pref, Preference after) {
    if (preferencesByType == null) {
      preferencesByType = new EnumMap<>(PreferenceType.class);
    }

    if (after == null) {
      final var head = preferencesByType.get(pref.type);
      pref.next = head;
      pref.previous = null;
      if (head != null) {
        head.previous = pref;
      }
      preferencesByType.put(pref.type, pref);
    } else {
      assert preferencesByType.get(pref.type) != null;

      pref.next = after.next;
      pref.previous = after;
      after.next = pref;
      if (pref.next != null) {
        pref.next.previous = pref;
      }
    }
  }

  private void removePreferenceByType(Preference pref) {
    if (preferencesByType == null) {
      return;
    }
    if (pref.next != null) {
      pref.next.previous = pref.previous;
    }
    if (pref.previous != null) {
      pref.previous.next = pref.next;
    } else {
      if (pref.next == null) {
        preferencesByType.remove(pref.type);
      } else {
        preferencesByType.put(pref.type, pref.next);
      }
    }
    pref.next = null;
    pref.previous = null;
  }

  /**
   * Clear out and deallocate the CDPS.
   *
   * <p>tempmem.cpp:166:clear_CDPS
   */
  public void clear_CDPS(final Adaptable context) {
    if (!hasContextDependentPreferenceSet()) {
      return;
    }

    /*
     * The CDPS should never exist on a top-level slot, so we do not need to
     * worry about checking for DO_TOP_LEVEL_REF_CTS.
     */

    final RecognitionMemory recMemory = Adaptables.adapt(context, RecognitionMemory.class);

    Iterator<Preference> it = cdps.iterator();
    while (it.hasNext()) {
      Preference p = it.next();
      p.preference_remove_ref(recMemory);
      it.remove();
    }
  }

  public boolean hasContextDependentPreferenceSet() {
    return !this.cdps.isEmpty();
  }

  LinkedList<Preference> getContextDependentPreferenceSet() {
    return this.cdps;
  }

  /**
   * Add a preference to a slot's CDPS This function adds a preference to a slots's context
   * dependent preference set, checking to first see whether the pref is already there. If an
   * operator The slot's CDPS is copied to conditions' bt structs in create_instatiation. Those
   * copies of the CDPS are used to backtrace through all relevant local evaluation rules that led
   * to the selection of the operator that produced a result.
   *
   * <p>decide.cpp:873:add_to_CDPS
   */
  public void add_to_CDPS(Adaptable context, Preference pref) {
    add_to_CDPS(context, pref, true);
  }

  public void add_to_CDPS(Adaptable context, Preference newPreference, boolean unique_value) {
    final var trace = Adaptables.adapt(context, Trace.class);
    final var printer = trace.getPrinter();

    final boolean traceBacktracing = trace.isEnabled(Category.BACKTRACING);
    if (traceBacktracing) {
      printer.print("--> Adding preference to CDPS: %s", newPreference);
    }

    var already_exists = false;
    for (Iterator<Preference> iterator = cdps.iterator(); iterator.hasNext() && !already_exists; ) {
      var existingPreference = iterator.next();

      if (existingPreference == newPreference) {
        already_exists = true;
      }

      if (unique_value) {
        already_exists = isUnique(existingPreference, newPreference);
      }
    }

    if (!already_exists) {
      this.cdps.push(newPreference);
      newPreference.preference_add_ref();
    } else if (traceBacktracing) {
      printer.print("--> equivalent pref already exists. Not adding.\n");
    }
  }

  private boolean isUnique(Preference existingPreference, Preference newPreference) {
    boolean alreadyExists;

    /*
     * Checking if a preference is unique differs depending on the preference type
     */
    if (((existingPreference.type == PreferenceType.BETTER)
            || (existingPreference.type == PreferenceType.WORSE))
        && ((newPreference.type == PreferenceType.BETTER)
            || (newPreference.type == PreferenceType.WORSE))) {
      if (existingPreference.type == newPreference.type) {
        alreadyExists =
            ((existingPreference.value == newPreference.value)
                && (existingPreference.referent == newPreference.referent));
      } else {
        alreadyExists =
            ((existingPreference.value == newPreference.referent)
                && (existingPreference.referent == newPreference.value));
      }
    } else if ((existingPreference.type == PreferenceType.BINARY_INDIFFERENT)
        && (newPreference.type == PreferenceType.BINARY_INDIFFERENT)) {
      /*
       * Binary preferences can be considered equivalent if they point to the same operators in the correct relative spots
       */
      alreadyExists =
          (((existingPreference.value == newPreference.value)
                  && (existingPreference.referent == newPreference.referent))
              || ((existingPreference.value == newPreference.referent)
                  && (existingPreference.referent == newPreference.value)));
    } else {
      /*
       * Otherwise they are equivalent if they have the same value and type
       */
      alreadyExists =
          (existingPreference.value == newPreference.value)
              && (existingPreference.type == newPreference.type);
    }

    return alreadyExists;
  }
}
