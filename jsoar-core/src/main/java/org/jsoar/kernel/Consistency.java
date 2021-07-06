/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 12, 2008
 */
package org.jsoar.kernel;

import java.util.EnumSet;
import org.jsoar.kernel.memory.Preference;
import org.jsoar.kernel.memory.RecognitionMemory;
import org.jsoar.kernel.memory.Slot;
import org.jsoar.kernel.memory.TemporaryMemory;
import org.jsoar.kernel.memory.WmeImpl;
import org.jsoar.kernel.rete.SoarReteListener;
import org.jsoar.kernel.symbols.IdentifierImpl;
import org.jsoar.kernel.tracing.Trace.Category;
import org.jsoar.util.ByRef;
import org.jsoar.util.adaptables.Adaptables;

/**
 * <em>This is an internal interface. Don't use it unless you know what you're doing.</em>
 *
 * <p>consistency.cpp
 *
 * @author ray
 */
public class Consistency {

  private static final boolean DEBUG_CONSISTENCY_CHECK = false;

  private enum LevelChangeType {
    NEW_DECISION,
    SAME_LEVEL,
    HIGHER_LEVEL,
    LOWER_LEVEL,
    NIL_GOAL_RETRACTIONS
  }

  private final Agent context;
  private Decider decider;
  private DecisionCycle decisionCycle;
  private TemporaryMemory tempMemory;
  private RecognitionMemory recMemory;
  private SoarReteListener soarReteListener;

  public Consistency(Agent context) {
    this.context = context;
  }

  public void initialize() {
    this.decider = Adaptables.adapt(context, Decider.class);
    this.decisionCycle = Adaptables.adapt(context, DecisionCycle.class);
    this.tempMemory = Adaptables.adapt(context, TemporaryMemory.class);
    this.recMemory = Adaptables.adapt(context, RecognitionMemory.class);
    this.soarReteListener = Adaptables.adapt(context, SoarReteListener.class);
  }

  /**
   * This code concerns the implementation of a 'consistency check' following each IE phases. The
   * basic idea is that we want context decisions to remain consistent with the current preferences,
   * even if the proposal for some operator is still acceptable
   *
   * <p>consistency.cpp:116:decision_consistent_with_current_preferences
   */
  private boolean decisionConsistentWithCurrentPreferences(IdentifierImpl goal, Slot s) {

    if (s.isa_context_slot) {
      printDebugMessage(
          "    slot (s)  isa context slot: " + "    Slot IdentifierImpl [%s] and attribute [%s]\n",
          s.id, s.attr);
    }
    printDebugMessage("    s->impasse_type: %s\n", s.getImpasseType());
    if (s.impasse_id != null) {
      printDebugMessage("    Impasse ID is set (non-NIL)\n");
    }

    /* Determine the current operator/impasse in the slot*/
    WmeImpl current_operator;
    boolean operator_in_slot;
    if (!goal.goalInfo.operator_slot.getWmes().isEmpty()) {
      /* There is an operator in the slot */
      current_operator = goal.goalInfo.operator_slot.getWmes().get(0);
      operator_in_slot = true;
    } else {
      /* There is not an operator in the slot */
      current_operator = null;
      operator_in_slot = false;
    }

    ImpasseType current_impasse_type;
    if (goal.goalInfo.lower_goal != null) {
      // the goal is impassed
      current_impasse_type = decider.type_of_existing_impasse(goal);
      printDebugMessage(
          "    Goal is impassed:  Impasse type: %d: Impasse attribute: [%s]\n",
          current_impasse_type, decider.attribute_of_existing_impasse(goal));

      /* Special case for an operator no-change */
      if ((operator_in_slot) && (current_impasse_type == ImpasseType.NO_CHANGE)) {
        /* Operator no-change impasse: run_preference_semantics will return 0
           and we only want to blow away this operator if another is better
        than it (checked in NONE_IMPASSE_TYPE switch) or if another kind
        of impasse would be generated (e.g., OPERATOR_TIE). So, we set
        the impasse type here to 0; that way we'll know that we should be
        comparing a previous decision for a unique operator against the
        current preference semantics. */
        printDebugMessage("    This is an operator no-change  impasse.\n");
        current_impasse_type = ImpasseType.NONE;
      }
    } else {
      current_impasse_type = ImpasseType.NONE;
      printDebugMessage("    Goal is not impassed: ");
    }

    /* Determine the new impasse type, based on the preferences that exist now */
    final ByRef<Preference> candidates = ByRef.create(null);
    final ImpasseType new_impasse_type = decider.run_preference_semantics(s, candidates, true);

    if (DEBUG_CONSISTENCY_CHECK) {
      context
          .getPrinter()
          .print("    Impasse Type returned by run preference semantics: %d\n", new_impasse_type);

      for (Preference cand = candidates.value; cand != null; cand = cand.next) {
        context.getPrinter().print("    Preference for slot: %s", cand);
      }

      for (Preference cand = candidates.value; cand != null; cand = cand.next_candidate) {
        context.getPrinter().print("\n    Candidate  for slot: %s", cand);
      }
    }

    if (current_impasse_type != new_impasse_type) {
      /* Then there is an inconsistency: no more work necessary */
      printDebugMessage(
          "    Impasse types are different: Returning FALSE, "
              + "preferences are not consistent with prior decision.\n");

      return false;
    }

    /* in these cases, we know that the new impasse and the old impasse *TYPES* are the same.  We
    just want to check and make the actual impasses/decisions are the same. */
    switch (new_impasse_type) {
      case NONE:
        /* There are four cases to consider when NONE_IMPASSE_TYPE is returned: */
        /* 1.  Previous operator and operator returned by run_pref_sem are the same.
        In this case, return TRUE (decision remains consistent) */

        /* This next if is meant to test that there actually is something in the slot but
        I'm nut quite certain that it will not always be true? */
        if (operator_in_slot) {
          printDebugMessage("    There is a WME in the operator slot:%s", current_operator);

          /* Because of indifferent preferences, we need to compare all possible candidates
          with the current decision */
          for (Preference cand = candidates.value; cand != null; cand = cand.next_candidate) {
            if (current_operator.value == cand.value) {
              printDebugMessage(
                  "       Operator slot ID [%s] and candidate ID [%s] are the same.\n",
                  current_operator.value, cand.value);

              return true;
            }
          }

          /* 2.  A different operator is indicated for the slot than the one that is
          currently installed.  In this case, we return FALSE (the decision is
          not consistent with the preferences). */

          /* Now we know that the decision is inconsistent */
          return false;

          /* 3.  A single operator is suggested when an impasse existed previously.
          In this case, return FALSE so that the impasse can be removed. */

        } else {
          /* There is no operator in the slot */
          if (goal.goalInfo.lower_goal != null) {
            /* But there is an impasse */
            context
                .getPrinter()
                .warn("      No Impasse Needed but Impasse exists: remove impasse now\n");
            context
                .getPrinter()
                .warn("\n\n   *************This should never be executed*******************\n\n");
            return false;
          }
        }

        /* 4.  This is the bottom goal in the stack and there is no operator or
        impasse for the operator slot created yet.  We shouldn't call this
        routine in this case (this condition is checked before
        decision_consistent_with_current_preferences is called) but, for
        completeness' sake, we check this condition and return TRUE
        (because no decision has been made at this level, there is no
        need to remove anything). */
        context
            .getPrinter()
            .warn("\n\n   *************This should never be executed*******************\n\n");
        return true;

      case CONSTRAINT_FAILURE:
        printDebugMessage("    Constraint Failure Impasse: Returning TRUE\n");
        return true;

      case CONFLICT:
        printDebugMessage("    Conflict Impasse: Returning TRUE\n");
        return true;

      case TIE:
        printDebugMessage("    Tie Impasse: Returning TRUE\n");
        return true;

      case NO_CHANGE:
        printDebugMessage("    No change Impasse: Returning TRUE\n");
        return true;

      default:
        // do nothing
        break;
    }

    context.getPrinter().warn("\n   After switch................");
    context
        .getPrinter()
        .warn("\n\n   *************This should never be executed*******************\n\n");
    return true;
  }

  /** consistency.cpp:299:remove_current_decision */
  private void removeCurrentDecision(final Slot s) {
    final var trace = context.getTrace();
    if (s.getWmes().isEmpty()) {
      trace.print(
          Category.OPERAND2_REMOVALS,
          "\n       REMOVING CONTEXT SLOT: Slot IdentifierImpl [%s] and attribute [%s]\n",
          s.id,
          s.attr);
    }

    trace.print(
        Category.OPERAND2_REMOVALS,
        "\n          Decision for goal [%s] is inconsistent.  Replacing it with....\n",
        s.id);

    /* If there is an operator in the slot, remove it */
    decider.remove_wmes_for_context_slot(s);

    /* If there are any subgoals, remove those */
    if (s.id.goalInfo.lower_goal != null) {
      decider.remove_existing_context_and_descendents(s.id.goalInfo.lower_goal);
    }

    decider.do_buffered_wm_and_ownership_changes();
  }

  /**
   * This scans down the goal stack and checks the consistency of the current decision versus the
   * current preferences for the slot, if the preferences have changed.
   *
   * <p>consistency.cpp:326:check_context_slot_decisions
   */
  private boolean checkContextSlotDecisions(int level) {
    if (tempMemory.highest_goal_whose_context_changed != null) {
      printDebugMessage(
          "    Highest goal with changed context: [%s]\n",
          tempMemory.highest_goal_whose_context_changed);
    }

    /* Check only those goals where preferences have changes that are at or above the level
    of the consistency check */
    for (IdentifierImpl goal = tempMemory.highest_goal_whose_context_changed;
        goal != null && goal.getLevel() <= level;
        goal = goal.goalInfo.lower_goal) {
      printDebugMessage("    Looking at goal [%s] to see if its preferences have changed\n", goal);

      Slot s = goal.goalInfo.operator_slot;

      if ((goal.goalInfo.lower_goal != null) || !s.getWmes().isEmpty()) {
        /* If we are not at the bottom goal or if there is an operator in the
        bottom goal's operator slot */
        printDebugMessage(
            "      This is a goal that either has subgoals or, if the bottom goal, has an operator in the slot\n");

        if (s.changed != null) {
          /* Only need to check a goal if its prefs have changed */
          printDebugMessage("      This goal's preferences have changed.\n");
          if (!decisionConsistentWithCurrentPreferences(goal, s)) {
            printDebugMessage(
                "   The current preferences indicate that the decision at [%s] needs to be removed.\n",
                goal);

            context
                .getTrace()
                .print(
                    EnumSet.of(Category.VERBOSE, Category.WM_CHANGES),
                    "Removing state %s because of a failed consistency check.\n",
                    goal);
            /* This doesn;t seem like it should be necessary but evidently it is: see 2.008 */
            removeCurrentDecision(s);
            return false; /* No need to continue once a decision is removed */
          }
        }
      }

      printDebugMessage("   This is a bottom goal with no operator in the slot\n");
    }

    return true;
  }

  /** consistency.cpp:378:i_activity_at_goal */
  private boolean i_activity_at_goal(IdentifierImpl goal) {
    /* print_with_symbols("\nLooking for I-activity at goal: %y\n", goal); */

    if (!goal.goalInfo.ms_i_assertions.isEmpty()) {
      return true;
    }

    return !goal.goalInfo.ms_retractions.isEmpty();

    /* printf("\nNo instantiation found.  Returning FALSE\n");  */
  }

  /**
   * This procedure returns TRUE if the current firing type is IE_PRODS and there are no
   * i-assertions (or any retractions) ready to fire in the current GOAL. Else it returns FALSE.
   *
   * <p>consistency.cpp:400:minor_quiescence_at_goal
   */
  private boolean minor_quiescence_at_goal(IdentifierImpl goal) {
    /* firing IEs but no more to fire == minor quiescence */
    return (recMemory.FIRING_TYPE == SavedFiringType.IE_PRODS) && (!i_activity_at_goal(goal));
  }

  /**
   * Find the highest goal of activity among the current assertions and retractions
   *
   * <p>We have to start at the top of the goal stack and go down because *any* goal in the goal
   * stack could be active (and we want to highest one). However, we terminate as soon as a goal
   * with assertions or retractions is found. Propose cares only about ms_i_assertions and
   * retractions *
   *
   * <p>consistency.cpp:420:highest_active_goal_propose
   *
   * @param start_goal The goal to start at
   * @param noneOk true if no active goal is ok, false if not
   * @return highest active goal in goal stack.
   */
  public IdentifierImpl highest_active_goal_propose(IdentifierImpl start_goal, boolean noneOk) {
    for (IdentifierImpl goal = start_goal; goal != null; goal = goal.goalInfo.lower_goal) {
      /*
      #ifdef DEBUG_DETERMINE_LEVEL_PHASE
      print(thisAgent, "In highest_active_goal_propose:\n");
      if (goal->id.ms_i_assertions) print_assertion(goal->id.ms_i_assertions);
      if (goal->id.ms_retractions)  print_retraction(goal->id.ms_retractions);
      #endif
       */
      /* If there are any active productions at this goal, return the goal */
      if ((!goal.goalInfo.ms_i_assertions.isEmpty()) || (!goal.goalInfo.ms_retractions.isEmpty())) {
        return goal;
      }
    }

    /* This routine should only be called when !quiescence.  However, there is
    still the possibility that the only active productions are retractions
    that matched in a NIL goal.  If so, then we just return the bottom goal.
    If not, then all possibilities have been exausted and we have encounted
    an unrecoverable error. */
    /*
    #ifdef DEBUG_DETERMINE_LEVEL_PHASE
       print(thisAgent, "WARNING: Returning NIL active goal because only NIL goal retractions are active.");
       xml_generate_warning(thisAgent, "WARNING: Returning NIL active goal because only NIL goal retractions are active.");
    #endif
    */
    if (!this.soarReteListener.nil_goal_retractions.isEmpty()) {
      return null;
    }

    if (!noneOk) {
      context.getTrace().flush();
      throw new IllegalStateException("Unable to find an active goal when not at quiescence.");
    }

    return null;
  }

  /**
   * consistency.cpp:457:highest_active_goal_apply
   *
   * <p>Preconditions: start_goal cannot be null, agent not at quiescence
   *
   * @param start_goal the goal to start at
   * @param noneOk true if no active goal is ok, false if not
   * @return highest active goal
   */
  public IdentifierImpl highest_active_goal_apply(IdentifierImpl start_goal, boolean noneOk) {
    for (IdentifierImpl goal = start_goal; goal != null; goal = goal.goalInfo.lower_goal) {
      /*
      #if 0 //DEBUG_DETERMINE_LEVEL_PHASE
           print(thisAgent, "In highest_active_goal_apply :\n");
           if (goal->id.ms_i_assertions) print_assertion(goal->id.ms_i_assertions);
           if (goal->id.ms_o_assertions) print_assertion(goal->id.ms_o_assertions);
           if (goal->id.ms_retractions)  print_retraction(goal->id.ms_retractions);
      #endif
      */

      /* If there are any active productions at this goal, return the goal */
      if ((!goal.goalInfo.ms_i_assertions.isEmpty())
          || (!goal.goalInfo.ms_o_assertions.isEmpty())
          || (!goal.goalInfo.ms_retractions.isEmpty())) {
        return goal;
      }
    }

    /* This routine should only be called when !quiescence.  However, there is
    still the possibility that the only active productions are retractions
    that matched in a NIL goal.  If so, then we just return the bottom goal.
    If not, then all possibilities have been exausted and we have encounted
    an unrecoverable error. */

    /*
    #ifdef DEBUG_DETERMINE_LEVEL_PHASE
    print(thisAgent, "WARNING: Returning NIL active goal because only NIL goal retractions are active.");
    xml_generate_warning(thisAgent, "WARNING: Returning NIL active goal because only NIL goal retractions are active.");
    #endif
    */
    if (!this.soarReteListener.nil_goal_retractions.isEmpty()) {
      return null;
    }

    if (!noneOk) {
      throw new IllegalStateException("Unable to find an active goal when not at quiescence.");
    }

    return null;
  }

  /**
   * Determines type of productions active at some active level. If IE PRODS are active, this value
   * is returned (regardless of whether there are PEs active or not). Note that this procedure will
   * return erroneous values if there is no activity at the current level. It should only be called
   * when activity at the active_level has been determined.
   *
   * <p>consistency.cpp::active_production_type_at_goal
   */
  private SavedFiringType active_production_type_at_goal(IdentifierImpl goal) {
    if (i_activity_at_goal(goal)) {
      return SavedFiringType.IE_PRODS;
    } else {
      return SavedFiringType.PE_PRODS;
    }
  }

  /** consistency.cpp:516:goal_stack_consistent_through_goal */
  private boolean goal_stack_consistent_through_goal(IdentifierImpl goal) {
    // #ifndef NO_TIMING_STUFF
    // #ifdef DETAILED_TIMING_STATS
    // start_timer(thisAgent, &thisAgent->start_gds_tv);
    // #endif
    // #endif

    if (DEBUG_CONSISTENCY_CHECK) {
      context.getPrinter().print("\nStart: CONSISTENCY CHECK at level %d\n", goal.getLevel());

      /* Just a bunch of debug stuff for now */
      if (tempMemory.highest_goal_whose_context_changed != null) {
        context
            .getPrinter()
            .print(
                "current_agent(highest_goal_whose_context_changed) = [%s]\n",
                tempMemory.highest_goal_whose_context_changed);
      } else {
        context.getPrinter().print("Evidently, nothing has changed: not checking slots\n");
      }
    }

    boolean test = checkContextSlotDecisions(goal.getLevel());

    printDebugMessage("\nEnd:   CONSISTENCY CHECK\n");

    // #ifndef NO_TIMING_STUFF
    // #ifdef DETAILED_TIMING_STATS
    //   stop_timer(thisAgent, &thisAgent->start_gds_tv,
    //      &thisAgent->gds_cpu_time[thisAgent->current_phase]);
    // #endif
    // #endif

    return test;
  }

  /** consistency.cpp:559:initialize_consistency_calculations_for_new_decision */
  public void initialize_consistency_calculations_for_new_decision() {
    /*
    if(DEBUG_DETERMINE_LEVEL_PHASE){
    context.getPrinter().print("\nInitialize consistency calculations for new decision.\n");
    }
    */

    /* No current activity level */
    decider.active_level = 0;
    decider.active_goal = null;

    /* Clear any interruption flags on the goals....*/
    for (IdentifierImpl goal = decider.topGoal(); goal != null; goal = goal.goalInfo.lower_goal) {
      goal.goalInfo.saved_firing_type = SavedFiringType.NO_SAVED_PRODS;
    }
  }

  /**
   * This routine is responsible for implementing the DETERMINE_LEVEL_PHASE. In the Waterfall
   * version of Soar, the DETERMINE_LEVEL_PHASE makes the determination of what goal level is active
   * in the stack. Activity proceeds from top goal to bottom goal so the active goal is the goal
   * highest in the stack with productions waiting to fire. This procedure also recognizes
   * quiescence (no productions active anywhere) and mini-quiescence (no more IE_PRODS are waiting
   * to fire in some goal for a goal that fired IE_PRODS in the previous elaboration).
   * Mini-quiescence is followed by a consistency check.
   *
   * <p>consistency.cpp:590:determine_highest_active_production_level_in_stack_apply
   */
  public void determine_highest_active_production_level_in_stack_apply() {
    if (!this.soarReteListener.any_assertions_or_retractions_ready()) {
      /* This is quiescence */
      /*

      /* Need to determine if this quiescence is also a minor quiescence,
      otherwise, an inconsistent decision could get retained here (because
      the consistency check was never run). (2.008).  Therefore, if
      in the previous preference phases, IE_PRODS fired, then force a
      consistency check over the entire stack (by checking at the
      bottom goal). */
      if (minor_quiescence_at_goal(decider.bottomGoal())) {
        goal_stack_consistent_through_goal(decider.bottomGoal());
      }

      // TODO why is this here?
      /* regardless of the outcome, we go to the output phases */
      this.decisionCycle.current_phase.set(Phase.OUTPUT);
      return;
    }

    /* Not Quiescence */

    /* Check for Max ELABORATIONS EXCEEDED */

    if (this.decisionCycle.checkForMaxElaborations(Phase.OUTPUT)) {
      return;
    }

    /* Save the old goal and level (must save level explicitly in case goal is NIL) */
    decider.previous_active_goal = decider.active_goal;
    decider.previous_active_level = decider.active_level;

    /* Determine the new highest level of activity */
    decider.active_goal = highest_active_goal_apply(decider.topGoal(), false);
    if (decider.active_goal != null) {
      decider.active_level = decider.active_goal.getLevel();
    } else {
      decider.active_level = 0; /* Necessary for get_next_retraction */
    }

    LevelChangeType level_change_type;
    if (decider.active_goal == null)
    /* Only NIL goal retractions */ {
      level_change_type = LevelChangeType.NIL_GOAL_RETRACTIONS;
    } else if (decider.previous_active_level == 0) {
      level_change_type = LevelChangeType.NEW_DECISION;
    } else {
      int diff = decider.active_level - decider.previous_active_level;
      if (diff == 0) {
        level_change_type = LevelChangeType.SAME_LEVEL;
      } else if (diff > 0) {
        level_change_type = LevelChangeType.LOWER_LEVEL;
      } else {
        level_change_type = LevelChangeType.HIGHER_LEVEL;
      }
    }

    switch (level_change_type) {
      case NIL_GOAL_RETRACTIONS:
        recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
        break;

      case NEW_DECISION:
        recMemory.FIRING_TYPE = active_production_type_at_goal(decider.active_goal);
        /* in APPLY phases, we can test for ONC here, check ms_o_assertions */
        break;

      case LOWER_LEVEL:
        /* Is there a minor quiescence at the previous level? */
        if (minor_quiescence_at_goal(decider.previous_active_goal)) {
          /*
          #ifdef DEBUG_DETERMINE_LEVEL_PHASE
          printf("\nMinor quiescence at level %d", thisAgent->previous_active_level);
          #endif
          */
          if (!goal_stack_consistent_through_goal(decider.previous_active_goal)) {
            this.decisionCycle.current_phase.set(Phase.OUTPUT);
            break;
          }
        }

        /* else: check if return to interrupted level */

        IdentifierImpl goal = decider.active_goal;

        if (goal.goalInfo.saved_firing_type != SavedFiringType.NO_SAVED_PRODS) {
          recMemory.FIRING_TYPE = goal.goalInfo.saved_firing_type;
          // KJC 04.05 commented the next line after reworking the phases
          // in init_soar.cpp
          // thisAgent->current_phase = DETERMINE_LEVEL_PHASE;
          // Reluctant to make this a recursive call, but somehow we need
          // to go thru
          // and determine which level we should start with now that we've
          // returned from a lower level (solved a subgoal or changed the
          // conditions).
          // We could return a flag instead and test it everytime thru
          // loop in APPLY.
          determine_highest_active_production_level_in_stack_apply();

          break;
        }

        /* else: just do a preference phases */
        recMemory.FIRING_TYPE = active_production_type_at_goal(decider.active_goal);
        break;

      case SAME_LEVEL:
        if (minor_quiescence_at_goal(decider.active_goal)) {
          if (!goal_stack_consistent_through_goal(decider.active_goal)) {
            this.decisionCycle.current_phase.set(Phase.OUTPUT);
            break;
          }
        }
        recMemory.FIRING_TYPE = active_production_type_at_goal(decider.active_goal);
        break;

      case HIGHER_LEVEL:
        goal = decider.previous_active_goal;
        goal.goalInfo.saved_firing_type = recMemory.FIRING_TYPE;

        /* run consistency check at new active level *before* firing any
        productions there */
        if (!goal_stack_consistent_through_goal(decider.active_goal)) {
          this.decisionCycle.current_phase.set(Phase.OUTPUT);
          break;
        }

        /* If the decision is consistent, then just start processing at this level */
        recMemory.FIRING_TYPE = active_production_type_at_goal(decider.active_goal);
        break;
    }
  }

  /**
   * This routine is called from the Propose Phase under the new reordering of the Decision Cycle.
   * In the Waterfall version of Soar, the this routine makes the determination of what goal level
   * is active in the stack. Activity proceeds from top goal to bottom goal so the active goal is
   * the goal highest in the stack with productions waiting to fire. This procedure also recognizes
   * quiescence (no productions active anywhere) and mini-quiescence (no more IE_PRODS are waiting
   * to fire in some goal for a goal that fired IE_PRODS in the previous elaboration).
   * Mini-quiescence is followed by a consistency check.
   *
   * <p>This routine could be further pruned, since with 8.6.0 we have a PROPOSE Phase, and don't
   * have to keep toggling IE_PRODS KJC april 2005
   *
   * <p>consistency.cpp:821:determine_highest_active_production_level_in_stack_propose
   */
  public void determine_highest_active_production_level_in_stack_propose() {
    /*
    #ifdef DEBUG_DETERMINE_LEVEL_PHASE
    printf("\n(Propose) Determining the highest active level in the stack....\n");
    #endif
    */

    // KJC 01.24.06 Changed logic for testing for IE prods. Was incorrectly
    // checking only the bottom goal. Need to check at all levels. A
    // previous
    // code change required #define, but it was never defined.
    /* We are only checking for i_assertions, not o_assertions, since we don't
     *  want operators to fire in the proposal phases
     */
    if (!(this.soarReteListener.ms_retractions != null
        || this.soarReteListener.ms_i_assertions != null)) {
      if (minor_quiescence_at_goal(decider.bottomGoal())) {
        /* This is minor quiescence */
        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        printf("\n Propose Phase Quiescence has been reached...going to decision\n");
        #endif
        */

        /* Force a consistency check over the entire stack (by checking at
        the bottom goal). */
        goal_stack_consistent_through_goal(decider.bottomGoal());

        /* Decision phases is always next */

        this.decisionCycle.current_phase.set(Phase.DECISION);
        return;
      }
    }

    /* Not Quiescence, there are elaborations ready to fire at some level. */

    /* Check for Max ELABORATIONS EXCEEDED */
    if (this.decisionCycle.checkForMaxElaborations(Phase.DECISION)) {
      return;
    }

    /* not Max Elaborations */

    /* Save the old goal and level (must save level explicitly in case
    goal is NIL) */
    decider.previous_active_goal = decider.active_goal;
    decider.previous_active_level = decider.active_level;

    /* Determine the new highest level of activity */
    decider.active_goal = highest_active_goal_propose(decider.topGoal(), false);
    if (decider.active_goal != null) {
      decider.active_level = decider.active_goal.getLevel();
    } else {
      decider.active_level = 0; /* Necessary for get_next_retraction */
    }
    /*
    #ifdef DEBUG_DETERMINE_LEVEL_PHASE
    printf("\nHighest level of activity is....%d", thisAgent->active_level);
    printf("\n   Previous level of activity is....%d", thisAgent->previous_active_level);
    #endif
    */

    LevelChangeType level_change_type;
    if (decider.active_goal == null)
    /* Only NIL goal retractions */ {
      level_change_type = LevelChangeType.NIL_GOAL_RETRACTIONS;
    } else if (decider.previous_active_level == 0) {
      level_change_type = LevelChangeType.NEW_DECISION;
    } else {
      int diff = decider.active_level - decider.previous_active_level;
      if (diff == 0) {
        level_change_type = LevelChangeType.SAME_LEVEL;
      } else if (diff > 0) {
        level_change_type = LevelChangeType.LOWER_LEVEL;
      } else {
        level_change_type = LevelChangeType.HIGHER_LEVEL;
      }
    }

    switch (level_change_type) {
      case NIL_GOAL_RETRACTIONS:
        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        print(thisAgent, "\nOnly NIL goal retractions are active");
        #endif
        */
        recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
        break;

      case NEW_DECISION:
        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        print(thisAgent, "\nThis is a new decision....");
        #endif
        */
        recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
        break;

      case LOWER_LEVEL:
        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        print(thisAgent, "\nThe level is lower than the previous level....");
        #endif
        */
        /* There is always a minor quiescence at the previous level
        in the propose phases, so check for consistency. */
        if (!goal_stack_consistent_through_goal(decider.previous_active_goal)) {
          this.decisionCycle.current_phase.set(Phase.DECISION);
          break;
        }
        /* else: just do a preference phases */
        recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
        break;

      case SAME_LEVEL:
        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        print(thisAgent, "\nThe level is the same as the previous level....");
        #endif
        */
        recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
        break;

      case HIGHER_LEVEL:
        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        print(thisAgent, "\nThe level is higher than the previous level....");
        #endif
        */

        IdentifierImpl goal = decider.previous_active_goal;
        goal.goalInfo.saved_firing_type = recMemory.FIRING_TYPE;

        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        if (goal->id.saved_firing_type == IE_PRODS)
           print(thisAgent, "\n Saving current firing type as IE_PRODS");
        else if (goal->id.saved_firing_type == PE_PRODS)
           print(thisAgent, "\n Saving current firing type as PE_PRODS");
        else if (goal->id.saved_firing_type == NO_SAVED_PRODS)
           print(thisAgent, "\n Saving current firing type as NO_SAVED_PRODS");
        else
           print(thisAgent, "\n Unknown SAVED firing type???????");
        #endif
        */

        /* run consistency check at new active level *before* firing any
        productions there */

        /*
        #ifdef DEBUG_DETERMINE_LEVEL_PHASE
        printf("\nMinor quiescence at level %d", thisAgent->active_level);
        #endif
        */
        if (!goal_stack_consistent_through_goal(decider.active_goal)) {
          this.decisionCycle.current_phase.set(Phase.DECISION);
          break;
        }

        /* If the decision is consistent, then just keep processing
        at this level */

        recMemory.FIRING_TYPE = SavedFiringType.IE_PRODS;
        break;
    }
  }

  private void printDebugMessage(final String message, Object... arguments) {
    if (DEBUG_CONSISTENCY_CHECK) {
      context.getPrinter().print(message, arguments);
    }
  }
}
