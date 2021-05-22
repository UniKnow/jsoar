/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Sep 12, 2008
 */
package org.jsoar.kernel.learning;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.Decider;
import org.jsoar.kernel.DecisionCycle;
import org.jsoar.kernel.ImpasseType;
import org.jsoar.kernel.PredefinedSymbols;
import org.jsoar.kernel.Production;
import org.jsoar.kernel.ProductionType;
import org.jsoar.kernel.Productions;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.events.ProductionAddedEvent;
import org.jsoar.kernel.lhs.Condition;
import org.jsoar.kernel.lhs.Conditions;
import org.jsoar.kernel.lhs.ConjunctiveNegationCondition;
import org.jsoar.kernel.lhs.ConjunctiveTest;
import org.jsoar.kernel.lhs.EqualityTest;
import org.jsoar.kernel.lhs.GoalIdTest;
import org.jsoar.kernel.lhs.ImpasseIdTest;
import org.jsoar.kernel.lhs.PositiveCondition;
import org.jsoar.kernel.lhs.RelationalTest;
import org.jsoar.kernel.lhs.Test;
import org.jsoar.kernel.lhs.Tests;
import org.jsoar.kernel.lhs.ThreeFieldCondition;
import org.jsoar.kernel.memory.Instantiation;
import org.jsoar.kernel.memory.Preference;
import org.jsoar.kernel.memory.RecognitionMemory;
import org.jsoar.kernel.memory.Slot;
import org.jsoar.kernel.memory.WmeImpl;
import org.jsoar.kernel.rete.NotStruct;
import org.jsoar.kernel.rete.ProductionAddResult;
import org.jsoar.kernel.rete.Rete;
import org.jsoar.kernel.rhs.Action;
import org.jsoar.kernel.rhs.MakeAction;
import org.jsoar.kernel.rhs.ReordererException;
import org.jsoar.kernel.smem.DefaultSemanticMemory;
import org.jsoar.kernel.symbols.IdentifierImpl;
import org.jsoar.kernel.symbols.SymbolFactory;
import org.jsoar.kernel.symbols.SymbolFactoryImpl;
import org.jsoar.kernel.symbols.SymbolImpl;
import org.jsoar.kernel.symbols.Variable;
import org.jsoar.kernel.tracing.Printer;
import org.jsoar.kernel.tracing.Trace;
import org.jsoar.kernel.tracing.Trace.Category;
import org.jsoar.util.ByRef;
import org.jsoar.util.DefaultSourceLocation;
import org.jsoar.util.ListHead;
import org.jsoar.util.ListItem;
import org.jsoar.util.SourceLocation;
import org.jsoar.util.adaptables.Adaptables;
import org.jsoar.util.markers.DefaultMarker;
import org.jsoar.util.markers.Marker;
import org.jsoar.util.properties.BooleanPropertyProvider;

/**
 * <em>This is an internal interface. Don't use it unless you know what you're doing.</em>
 *
 * <p>chunking.cpp
 *
 * @author ray
 */
public class Chunker {
  private static final SourceLocation NEW_PRODUCTION_SOURCE =
      DefaultSourceLocation.newBuilder().file("*chunker*").build();
  private final Agent context;
  private Decider decider;
  private Backtracer backtrace;
  private PredefinedSymbols predefinedSyms;
  Explain explain;
  private DecisionCycle decisionCycle;
  private Rete rete;
  private RecognitionMemory recMemory;

  public int chunks_this_d_cycle;
  /**
   * gsysparam.h:118:MAX_CHUNKS_SYSPARAM
   *
   * <p>Defaults to 50 in init_soar()
   */
  private int maxChunks = 50;
  /** agent.h:336:max_chunks_reached */
  private boolean maxChunksReached = false;

  private int results_match_goal_level;
  private Marker results_tc_number;
  private Preference results;
  private Preference extra_result_prefs_from_instantiation;
  public Marker variablization_tc;
  final ChunkConditionSet negated_set = new ChunkConditionSet();

  /**
   * gsysparam.h:179:CHUNK_THROUGH_LOCAL_NEGATIONS_SYSPARAM
   *
   * <p>Defaults to true in init_soar()
   */
  private boolean chunkThroughLocalNegations = true;
  /**
   * gsysparam.h:195:CHUNK_THROUGH_EVALUATION_RULES_SYSPARAM
   *
   * <p>MMA: Chunk through evaluation rules off by default
   */
  public boolean chunkThroughEvaluationRules = false;
  /** agent.h:534:quiescence_t_flag */
  boolean quiescence_t_flag = false;

  /**
   * gsysparam.h:143:USE_LONG_CHUNK_NAMES
   *
   * <p>Defaults to true in init_soar()
   */
  private boolean useLongChunkNames = true;

  /**
   * agent.h:535:chunk_name_prefix
   *
   * <p>Defautls to "chunk" in init_soar()
   */
  private String chunk_name_prefix = "chunk";
  /**
   * agent.h:516:chunk_count
   *
   * <p>Defautls to 1 in create_soar_agent()
   */
  private ByRef<Integer> chunk_count = ByRef.create(Integer.valueOf(1));

  /**
   * agent.h:517:justification_count
   *
   * <p>Defaults to 1 in create_soar_agent()
   */
  private ByRef<Integer> justification_count = ByRef.create(Integer.valueOf(1));
  /**
   * gsysparam.h:123:LEARNING_ON_SYSPARAM
   *
   * <p>Defaults to false in init_soar()
   */
  private BooleanPropertyProvider learningOn =
      new BooleanPropertyProvider(SoarProperties.LEARNING_ON);

  /**
   * gsysparam.h:126:LEARNING_ALL_GOALS_SYSPARAM
   *
   * <p>Defaults to true in init_soar()
   */
  private boolean learningAllGoals = true;
  /**
   * gsysparam.h:125:LEARNING_EXCEPT_SYSPARAM
   *
   * <p>Defaults to false in init_soar
   */
  private boolean learningExcept = false;

  /**
   * gsysparam.h:124:LEARNING_ONLY_SYSPARAM
   *
   * <p>Defaults to false in init_soar
   */
  private boolean learningOnly = false;

  /**
   * lists of symbols (PS names) declared chunk-free
   *
   * <p>agent.h:312:chunk_free_problem_spaces
   */
  private final LinkedList<IdentifierImpl> chunk_free_problem_spaces =
      new LinkedList<IdentifierImpl>();

  /**
   * lists of symbols (PS names) declared chunky
   *
   * <p>agent.h:313:chunky_problem_spaces
   */
  private final LinkedList<IdentifierImpl> chunky_problem_spaces = new LinkedList<IdentifierImpl>();

  /** agent.h:522:instantiations_with_nots */
  final LinkedList<Instantiation> instantiations_with_nots = new LinkedList<Instantiation>();

  /** @param context */
  public Chunker(Agent context) {
    this.context = context;

    this.context.getProperties().setProvider(SoarProperties.LEARNING_ON, learningOn);
  }

  public void initialize() {
    this.predefinedSyms = Adaptables.adapt(context, PredefinedSymbols.class);

    this.decider = Adaptables.adapt(context, Decider.class);
    this.explain = context.getExplain();

    this.backtrace = new Backtracer(context);
    this.backtrace.initialize();

    this.decisionCycle = Adaptables.adapt(context, DecisionCycle.class);
    this.rete = Adaptables.adapt(context, Rete.class);
    this.recMemory = Adaptables.adapt(context, RecognitionMemory.class);
  }

  public void reset() {
    this.chunk_free_problem_spaces.clear();
    this.chunky_problem_spaces.clear();
  }

  /**
   * Client code should use {@link SoarProperties#LEARNING_ON} rather than this method.
   *
   * @return true if learning (chunking) is currently enabled. False otherwise.
   */
  public boolean isLearningOn() {
    return learningOn.value.get();
  }

  /** @return the maxChunksReached */
  public boolean isMaxChunksReached() {
    return maxChunksReached;
  }

  public void removeGoalFromChunkyProblemSpaces(IdentifierImpl goal) {
    final Iterator<IdentifierImpl> it = chunky_problem_spaces.iterator();
    while (it.hasNext()) {
      final IdentifierImpl id = it.next();
      if (id == goal) {
        it.remove();
      }
    }
  }

  public void removeGoalFromChunkFreeProblemSpaces(IdentifierImpl goal) {
    final Iterator<IdentifierImpl> it = chunk_free_problem_spaces.iterator();
    while (it.hasNext()) {
      final IdentifierImpl id = it.next();
      if (id == goal) {
        it.remove();
      }
    }
  }

  /**
   * chunk.cpp:77:add_results_if_needed
   *
   * @param sym
   */
  private void add_results_if_needed(SymbolImpl sym) {
    IdentifierImpl id = sym.asIdentifier();
    if (id != null)
      if ((id.getLevel() >= results_match_goal_level) && (id.tc_number != results_tc_number))
        add_results_for_id(id);
  }

  /**
   * chunk.cpp:86:add_pref_to_results
   *
   * @param pref
   */
  private void add_pref_to_results(Preference pref) {
    // if an equivalent pref is already a result, don't add this one
    for (Preference p = this.results; p != null; p = p.next_result) {
      if (p.id != pref.id) continue;
      if (p.attr != pref.attr) continue;
      if (p.value != pref.value) continue;
      if (p.type != pref.type) continue;
      if (pref.type.isUnary()) return;
      if (p.referent != pref.referent) continue;
      return;
    }

    // if pref isn't at the right level, find a clone that is
    if (pref.inst.match_goal_level != this.results_match_goal_level) {
      Preference p = null;
      for (p = pref.next_clone; p != null; p = p.next_clone)
        if (p.inst.match_goal_level == this.results_match_goal_level) break;
      if (p == null)
        for (p = pref.prev_clone; p != null; p = p.prev_clone)
          if (p.inst.match_goal_level == this.results_match_goal_level) break;
      if (p == null) return; /* if can't find one, it isn't a result */
      pref = p;
    }

    // add this preference to the result list
    pref.next_result = this.results;
    this.results = pref;

    // follow transitive closuse through value, referent links
    add_results_if_needed(pref.value);
    if (pref.type.isBinary()) add_results_if_needed(pref.referent);
  }

  /**
   * chunk.cpp:121:add_results_for_id
   *
   * @param id
   */
  private void add_results_for_id(IdentifierImpl id) {
    id.tc_number = this.results_tc_number;

    // scan through all preferences and wmes for all slots for this id
    for (WmeImpl w = id.getInputWmes(); w != null; w = w.next) add_results_if_needed(w.value);

    for (Slot s = id.slots; s != null; s = s.next) {
      for (Preference pref = s.getAllPreferences(); pref != null; pref = pref.nextOfSlot)
        add_pref_to_results(pref);

      for (WmeImpl w = s.getWmes(); w != null; w = w.next) add_results_if_needed(w.value);
    }

    // now scan through extra prefs and look for any with this id
    for (Preference pref = this.extra_result_prefs_from_instantiation;
        pref != null;
        pref = pref.inst_next) {
      if (pref.id == id) add_pref_to_results(pref);
    }
  }

  /** chunk.cpp:144:get_results_for_instantiation */
  private Preference get_results_for_instantiation(Instantiation inst) {
    this.results = null;
    this.results_match_goal_level = inst.match_goal_level;
    this.results_tc_number = DefaultMarker.create();
    this.extra_result_prefs_from_instantiation = inst.preferences_generated;
    for (Preference pref = inst.preferences_generated; pref != null; pref = pref.inst_next) {
      if ((pref.id.getLevel() < this.results_match_goal_level)
          && (pref.id.tc_number != this.results_tc_number)) {
        add_pref_to_results(pref);
      }
    }
    return this.results;
  }

  /**
   * chunk.cpp:181:variablize_symbol
   *
   * <p>Note: In jsoar, modified to return new variable rather than replace byref argument
   *
   * @param sym the symbol to variablize. This object is not modified.
   * @return a new variablized version of the symbol
   */
  public SymbolImpl variablize_symbol(final SymbolImpl sym) {
    // only variablize identifiers
    final IdentifierImpl id = sym.asIdentifier();
    if (id == null) return sym;

    // don't variablize lti (long term identifiers)
    if (id.smem_lti != 0) {
      id.tc_number = variablization_tc;
      id.variablization = sym;
      return sym;
    }

    if (id.tc_number == this.variablization_tc) {
      // it's already been variablized, so use the existing variable
      return id.variablization;
    }

    // need to create a new variable
    id.tc_number = this.variablization_tc;
    Variable var =
        ((SymbolFactoryImpl) this.context.getSymbols())
            .getVariableGenerator()
            .generate_new_variable(Character.toString(id.getNameLetter()));
    id.variablization = var;
    return var;
  }

  /**
   * chunk.cpp:207:variablize_test
   *
   * @param t
   */
  private Test variablize_test(Test t) {
    if (Tests.isBlank(t)) return t;

    final EqualityTest eq = t.asEqualityTest();
    if (eq != null) {
      // eq.sym = variablize_symbol(eq.sym);
      /* Warning: this relies on the representation of tests */
      return SymbolImpl.makeEqualityTest(variablize_symbol(eq.getReferent()));
    }

    if (t.asGoalIdTest() != null || t.asImpasseIdTest() != null || t.asDisjunctionTest() != null) {
      return t;
    }

    final ConjunctiveTest ct = t.asConjunctiveTest();
    if (ct != null) {
      for (ListIterator<Test> it = ct.conjunct_list.listIterator(); it.hasNext(); ) {
        final Test c = it.next();
        it.set(variablize_test(c));
      }
      return ct;
    }
    // relational tests other than equality
    final RelationalTest rt = t.asRelationalTest();
    return rt.withNewReferent(variablize_symbol(rt.referent));
  }

  /**
   * chunk.cpp:235:variablize_condition_list
   *
   * @param cond
   */
  public void variablize_condition_list(Condition cond) {
    for (; cond != null; cond = cond.next) {
      ThreeFieldCondition tfc = cond.asThreeFieldCondition();
      if (tfc != null) {
        tfc.id_test = variablize_test(tfc.id_test);
        tfc.attr_test = variablize_test(tfc.attr_test);
        tfc.value_test = variablize_test(tfc.value_test);
      }

      ConjunctiveNegationCondition ncc = cond.asConjunctiveNegationCondition();
      if (ncc != null) {
        variablize_condition_list(ncc.top);
      }
    }
  }

  /** chunk.cpp:251:copy_and_variablize_result_list */
  private MakeAction copy_and_variablize_result_list(Preference pref, boolean variablize) {
    if (pref == null) return null;

    MakeAction a = new MakeAction();

    SymbolImpl id = pref.id;
    SymbolImpl attr = pref.attr;
    SymbolImpl val = pref.value;
    SymbolImpl ref = pref.referent;

    if (variablize) {
      id = variablize_symbol(id);
      attr = variablize_symbol(attr);
      val = variablize_symbol(val);
    }

    a.id = id.toRhsValue();
    a.attr = attr.toRhsValue();
    a.value = val.toRhsValue();

    a.preference_type = pref.type;

    if (pref.type.isBinary()) {
      if (variablize) {
        ref = variablize_symbol(ref);
      }
      a.referent = variablize_symbol(ref).toRhsValue();
    }

    a.next = copy_and_variablize_result_list(pref.next_result, variablize);
    return a;
  }

  /**
   * This routine is called once backtracing is finished. It goes through the ground conditions and
   * builds a chunk_cond (see above) for each one. The chunk_cond includes two new copies of the
   * condition: one to be used for the initial instantiation of the chunk, and one to be
   * (variablized and) used for the chunk itself.
   *
   * <p>This routine also goes through the negated conditions and adds to the ground set (again
   * building chunk_cond's) any negated conditions that are connected to the grounds.
   *
   * <p>At exit, the "dest_top" and "dest_bottom" arguments are set to point to the first and last
   * chunk_cond in the ground set. The "tc_to_use" argument is the tc number that this routine will
   * use to mark the TC of the ground set. At exit, this TC indicates the set of identifiers in the
   * grounds. (This is used immediately afterwards to figure out which Nots must be added to the
   * chunk.)
   *
   * <p>chunk.cpp:409:build_chunk_conds_for_grounds_and_add_negateds
   *
   * @param dest_top
   * @param dest_bottom
   * @param tc_to_use
   */
  private void build_chunk_conds_for_grounds_and_add_negateds(
      ByRef<ChunkCondition> dest_top,
      ByRef<ChunkCondition> dest_bottom,
      Marker tc_to_use,
      ByRef<Boolean> reliable) {
    ListItem<ChunkCondition> first_cc = null;

    // build instantiated conds for grounds and setup their TC
    ListItem<ChunkCondition> prev_cc = null;
    while (!backtrace.grounds.isEmpty()) {
      Condition ground = backtrace.grounds.pop();
      // make the instantiated condition
      ChunkCondition cc = new ChunkCondition(ground);
      cc.instantiated_cond = Condition.copy_condition(cc.cond);
      cc.variablized_cond = Condition.copy_condition(cc.cond);
      if (prev_cc != null) {
        prev_cc.next = cc.next_prev;
        cc.next_prev.previous = prev_cc;
        cc.variablized_cond.prev = prev_cc.item.variablized_cond;
        prev_cc.item.variablized_cond.next = cc.variablized_cond;
        cc.instantiated_cond.prev = prev_cc.item.instantiated_cond;
        prev_cc.item.instantiated_cond.next = cc.instantiated_cond;
      } else {
        first_cc = cc.next_prev;
        cc.next_prev.previous = null;
        cc.variablized_cond.prev = null;
        cc.instantiated_cond.prev = null;
      }
      prev_cc = cc.next_prev;
      // add this in to the TC
      ground.add_cond_to_tc(tc_to_use, null, null);
    }

    // scan through negated conditions and check which ones are connected
    // to the grounds
    final Trace trace = context.getTrace();
    trace.print(Category.BACKTRACING, "\n\n*** Adding Grounded Negated Conditions ***\n");

    while (!negated_set.all.isEmpty()) {
      final ChunkCondition cc = negated_set.all.getFirstItem();
      negated_set.remove_from_chunk_cond_set(cc);

      if (cc.cond.cond_is_in_tc(tc_to_use)) {
        // negated cond is in the TC, so add it to the grounds

        trace.print(Category.BACKTRACING, "\n-.Moving to grounds: %s", cc.cond);

        cc.instantiated_cond = Condition.copy_condition(cc.cond);
        cc.variablized_cond = Condition.copy_condition(cc.cond);
        if (prev_cc != null) {
          prev_cc.next = cc.next_prev;
          cc.next_prev.previous = prev_cc;
          cc.variablized_cond.prev = prev_cc.item.variablized_cond;
          prev_cc.item.variablized_cond.next = cc.variablized_cond;
          cc.instantiated_cond.prev = prev_cc.item.instantiated_cond;
          prev_cc.item.instantiated_cond.next = cc.instantiated_cond;
        } else {
          first_cc = cc.next_prev;
          cc.next_prev.previous = null;
          cc.variablized_cond.prev = null;
          cc.instantiated_cond.prev = null;
        }
        prev_cc = cc.next_prev;
      } else {
        // not in TC, so discard the condition
        if (!chunkThroughLocalNegations) {
          // this chunk will be overgeneral! don't create it

          // SBW 5/07
          // report what local negations are preventing the chunk,
          // and set flags like we saw a ^quiescence t so it won't be
          // created
          backtrace.report_local_negation(cc.cond);
          reliable.value = false;
        }

        // free_with_pool (&thisAgent.chunk_cond_pool, cc);
      }
    }

    if (prev_cc != null) {
      prev_cc.next = null;
      prev_cc.item.variablized_cond.next = null;
      prev_cc.item.instantiated_cond.next = null;
    } else {
      first_cc = null;
    }

    dest_top.value = first_cc.item;
    dest_bottom.value = prev_cc.item;
  }

  /**
   * This routine looks through all the Nots in the instantiations in instantiations_with_nots, and
   * returns copies of the ones involving pairs of identifiers in the grounds. Before this routine
   * is called, the ids in the grounds must be marked with "tc_of_grounds."
   *
   * <p>chunk.cpp:512:get_nots_for_instantiated_conditions
   */
  private NotStruct get_nots_for_instantiated_conditions(
      LinkedList<Instantiation> instantiations_with_nots, Marker tc_of_grounds) {
    // collect nots for which both id's are marked
    NotStruct collected_nots = null;
    while (!instantiations_with_nots.isEmpty()) {
      Instantiation inst = instantiations_with_nots.pop();

      for (NotStruct n1 = inst.nots; n1 != null; n1 = n1.next) {
        // Are both id's marked? If no, goto next loop iteration
        if (n1.s1.tc_number != tc_of_grounds) continue;
        if (n1.s2.tc_number != tc_of_grounds) continue;

        // If the pair already in collected_nots, goto next iteration
        NotStruct n2;
        for (n2 = collected_nots; n2 != null; n2 = n2.next) {
          if ((n2.s1 == n1.s1) && (n2.s2 == n1.s2)) break;
          if ((n2.s1 == n1.s2) && (n2.s2 == n1.s1)) break;
        }
        if (n2 != null) continue;

        // Add the pair to collected_nots
        NotStruct new_not = new NotStruct(n1.s1, n1.s2);
        new_not.next = collected_nots;
        collected_nots = new_not;
      }
    }

    return collected_nots;
  }

  /**
   * This routine goes through the given list of Nots and, for each one, inserts a variablized copy
   * of it into the given condition list at the earliest possible location. (The given condition
   * list should be the previously-variablized condition list that will become the chunk's LHS.) The
   * given condition list is destructively modified; the given Not list is unchanged.
   *
   * <p>chunk.cpp:561:variablize_nots_and_insert_into_conditions
   *
   * @param nots
   * @param conds
   */
  public void variablize_nots_and_insert_into_conditions(NotStruct nots, Condition conds) {
    for (NotStruct n = nots; n != null; n = n.next) {
      SymbolImpl var1 = n.s1.variablization;
      SymbolImpl var2 = n.s2.variablization;
      // find where var1 is bound, and add "<> var2" to that test
      RelationalTest t = new RelationalTest(RelationalTest.NOT_EQUAL_TEST, var2);
      boolean added_it = false;
      for (Condition c = conds; c != null; c = c.next) {
        PositiveCondition pc = c.asPositiveCondition();
        if (pc == null) continue;

        if (Tests.test_includes_equality_test_for_symbol(pc.id_test, var1)) {
          pc.id_test = Tests.add_new_test_to_test(pc.id_test, t);
          added_it = true;
          break;
        }
        if (Tests.test_includes_equality_test_for_symbol(pc.attr_test, var1)) {
          pc.attr_test = Tests.add_new_test_to_test(pc.attr_test, t);
          added_it = true;
          break;
        }
        if (Tests.test_includes_equality_test_for_symbol(pc.value_test, var1)) {
          pc.value_test = Tests.add_new_test_to_test(pc.value_test, t);
          added_it = true;
          break;
        }
      }
      if (!added_it) {
        throw new IllegalStateException("Internal error: couldn't add Not test to chunk");
      }
    }
  }

  /**
   * This routine adds goal id or impasse id tests to the variablized conditions. For each id in the
   * grounds that happens to be the identifier of a goal or impasse, we add a goal/impasse id test
   * to the variablized conditions, to make sure that in the resulting chunk, the variablization of
   * that id is constrained to match against a goal/impasse. (Note: actually, in the current
   * implementation of chunking, it's impossible for an impasse id to end up in the ground set. So
   * part of this code is unnecessary.)
   *
   * <p>chunk.cpp:628:add_goal_or_impasse_tests
   *
   * @param all_ccs
   */
  private void add_goal_or_impasse_tests(ListItem<ChunkCondition> all_ccs) {
    Marker tc = DefaultMarker.create();
    for (ListItem<ChunkCondition> ccIter = all_ccs; ccIter != null; ccIter = ccIter.next) {
      ChunkCondition cc = ccIter.item;
      PositiveCondition pc = cc.instantiated_cond.asPositiveCondition();
      if (pc == null) continue;

      // TODO Assumes id_test is equality test of identifier
      IdentifierImpl id = pc.id_test.asEqualityTest().getReferent().asIdentifier();

      if ((id.isGoal()) && (id.tc_number != tc)) {
        Test t = id.isGoal() ? GoalIdTest.INSTANCE : ImpasseIdTest.INSTANCE;

        // TODO Assumes variablized_cond is three-field (put this assumption in class?)
        ThreeFieldCondition tfc = cc.variablized_cond.asThreeFieldCondition();
        tfc.id_test = Tests.add_new_test_to_test(tfc.id_test, t);

        id.tc_number = tc;
      }
    }
  }

  /**
   * The Rete routines require the instantiated conditions (on the instantiation structure) to be in
   * the same order as the original conditions from which the Rete was built. This means that the
   * initial instantiation of the chunk must have its conditions in the same order as the
   * variablized conditions. The trouble is, the variablized conditions get rearranged by the
   * reorderer. So, after reordering, we have to rearrange the instantiated conditions to put them
   * in the same order as the now-scrambled variablized ones. This routine does this.
   *
   * <p>Okay, so the obvious way is to have each variablized condition (VCond) point to the
   * corresponding instantiated condition (ICond). Then after reordering the VConds, we'd scan
   * through the VConds and say {@code VCond->Icond->next = VCond->next->Icond VCond->Icond->prev =
   * VCond->prev->Icond} (with some extra checks for the first and last VCond in the list).
   *
   * <p>The problem with this is that it takes an extra 4 bytes per condition, for the "ICond"
   * field. Conditions were taking up a lot of memory in my test cases, so I wanted to shrink them.
   * This routine avoids needing the 4 extra bytes by using the following trick: first "swap out" 4
   * bytes from each VCond; then use that 4 bytes for the "ICond" field. Now run the above
   * algorithm. Finally, swap those original 4 bytes back in.
   *
   * <p>chunk.cpp:680:reorder_instantiated_conditions
   *
   * @param top_cc
   * @param dest_inst_top
   * @param dest_inst_bottom
   */
  private void reorder_instantiated_conditions(
      ListHead<ChunkCondition> top_cc,
      ByRef<Condition> dest_inst_top,
      ByRef<Condition> dest_inst_bottom) {
    // Step 1: swap prev pointers out of variablized conds into chunk_conds,
    // and swap pointer to the corresponding instantiated conds into the
    // variablized conds' prev pointers
    for (ListItem<ChunkCondition> it = top_cc.first; it != null; it = it.next) {
      final ChunkCondition cc = it.item;
      cc.saved_prev_pointer_of_variablized_cond = cc.variablized_cond.prev;
      cc.variablized_cond.prev = cc.instantiated_cond;
    }

    // Step 2: do the reordering of the instantiated conds
    for (ListItem<ChunkCondition> it = top_cc.first; it != null; it = it.next) {
      final ChunkCondition cc = it.item;
      if (cc.variablized_cond.next != null) {
        cc.instantiated_cond.next = cc.variablized_cond.next.prev;
      } else {
        cc.instantiated_cond.next = null;
        dest_inst_bottom.value = cc.instantiated_cond;
      }

      if (cc.saved_prev_pointer_of_variablized_cond != null) {
        cc.instantiated_cond.prev = cc.saved_prev_pointer_of_variablized_cond.prev;
      } else {
        cc.instantiated_cond.prev = null;
        dest_inst_top.value = cc.instantiated_cond;
      }
    }

    // Step 3:  restore the prev pointers on variablized conds
    for (ListItem<ChunkCondition> it = top_cc.first; it != null; it = it.next) {
      final ChunkCondition cc = it.item;
      cc.variablized_cond.prev = cc.saved_prev_pointer_of_variablized_cond;
    }
  }

  /**
   * When we build the initial instantiation of the new chunk, we have to fill in
   * preferences_generated with *copies* of all the result preferences. These copies are clones of
   * the results. This routine makes these clones and fills in {@code
   * chunk_inst->preferences_generated}.
   *
   * <p>chunk.cpp:726:make_clones_of_results
   *
   * @param results
   * @param chunk_inst
   */
  private void make_clones_of_results(Preference results, Instantiation chunk_inst) {
    chunk_inst.preferences_generated = null;
    for (Preference result_p = results; result_p != null; result_p = result_p.next_result) {
      // copy the preference
      Preference p =
          new Preference(
              result_p.type, result_p.id, result_p.attr, result_p.value, result_p.referent);

      // put it onto the list for chunk_inst
      p.inst = chunk_inst;
      chunk_inst.insertGeneratedPreference(p);

      // insert it into the list of clones for this preference
      p.next_clone = result_p;
      p.prev_clone = result_p.prev_clone;
      result_p.prev_clone = p;
      if (p.prev_clone != null) p.prev_clone.next_clone = p;
    }
  }

  /** chunk.cpp:762:find_impasse_wme_value */
  private static SymbolImpl find_impasse_wme_value(IdentifierImpl id, SymbolImpl attr) {
    for (WmeImpl w = id.goalInfo.getImpasseWmes(); w != null; w = w.next)
      if (w.attr == attr) return w.value;
    return null;
  }

  /** chunk.cpp:770:generate_chunk_name_sym_constant */
  private String generate_chunk_name_sym_constant(Instantiation inst) {
    if (!this.useLongChunkNames)
      return Productions.generateUniqueName(
          context.getProductions(), chunk_name_prefix, chunk_count);

    int lowest_result_level = decider.topGoal().getLevel();
    for (Preference p = inst.preferences_generated; p != null; p = p.inst_next)
      if (p.id.getLevel() > lowest_result_level) lowest_result_level = p.id.getLevel();

    IdentifierImpl goal = decider.find_goal_at_goal_stack_level(lowest_result_level);

    String impass_name = null;
    if (goal != null) {
      ImpasseType impasse_type = decider.type_of_existing_impasse(goal);
      // TODO: make this a method of ImpasseType
      switch (impasse_type) {
        case NONE:
          // #ifdef DEBUG_CHUNK_NAMES
          // print ("Warning: impasse_type is NONE_IMPASSE_TYPE during
          // chunk creation.\n");
          // xml_generate_warning(thisAgent, "Warning: impasse_type is
          // NONE_IMPASSE_TYPE during chunk creation.");
          // #endif
          impass_name = "unknownimpasse";
          break;
        case CONSTRAINT_FAILURE:
          impass_name = "cfailure";
          break;
        case CONFLICT:
          impass_name = "conflict";
          break;
        case TIE:
          impass_name = "tie";
          break;
        case NO_CHANGE:
          {
            SymbolImpl sym =
                find_impasse_wme_value(goal.goalInfo.lower_goal, predefinedSyms.attribute_symbol);

            if (sym == null) {
              // #ifdef DEBUG_CHUNK_NAMES
              // // TODO: generate warning XML: I think we need to get a
              // string for "do_print_for_identifier" and append it
              // // but this seems low priority since it's not even
              // included in a normal build
              // print ("Warning: Failed to find ^attribute impasse
              // wme.\n");
              // do_print_for_identifier(goal->id.lower_goal, 1, 0, 0);
              // #endif
              impass_name = "unknownimpasse";
            } else if (sym == predefinedSyms.operator_symbol) {
              impass_name = "opnochange";
            } else if (sym == predefinedSyms.state_symbol) {
              impass_name = "snochange";
            } else {
              // #ifdef DEBUG_CHUNK_NAMES
              // print ("Warning: ^attribute impasse wme has unexpected
              // value.\n");
              // xml_generate_warning(thisAgent, "Warning: ^attribute
              // impasse wme has unexpected value.");
              // #endif
              impass_name = "unknownimpasse";
            }
          }
          break;
        default:
          // #ifdef DEBUG_CHUNK_NAMES
          // // TODO: generate warning XML: I think we need to create a
          // buffer and SNPRINTF the impasse_type into it (since it's a
          // byte)
          // // but this seems low priority since it's not even included
          // in a normal build
          // print ("Warning: encountered unknown impasse_type: %d.\n",
          // impasse_type);
          //
          // #endif
          impass_name = "unknownimpasse";
          break;
      }
    } else {
      // #ifdef DEBUG_CHUNK_NAMES
      // print ("Warning: Failed to determine impasse type.\n");
      // xml_generate_warning(thisAgent, "Warning: Failed to determine
      // impasse type.");
      // #endif
      impass_name = "unknownimpasse";
    }

    String name =
        chunk_name_prefix
            + "-"
            + chunk_count.value
            + "*d"
            + this.decisionCycle.d_cycle_count
            + "*"
            + impass_name
            + "*"
            + chunks_this_d_cycle;
    chunk_count.value = chunk_count.value + 1;

    // Any user who named a production like this deserves to be burned, but we'll have mercy:
    final SymbolFactory syms = context.getSymbols();
    if (syms.findString(name) != null) {
      int collision_count = 1;

      context
          .getPrinter()
          .warn(
              "Warning: generated chunk name (%s) already exists.  Will find unique name.\n", name);
      do {
        name =
            chunk_name_prefix
                + "-"
                + chunk_count
                + "*d"
                + this.decisionCycle.d_cycle_count
                + "*"
                + impass_name
                + "*"
                + chunks_this_d_cycle
                + "*"
                + collision_count++;

      } while (context.getProductions().getProduction(name) != null);
    }

    return name;
  }

  /** chunk.cpp:903:chunk_instantiation */
  boolean should_variablize(Instantiation inst) {

    if (!isLearningOn()) {
      return false;
    }

    if (learningExcept && chunk_free_problem_spaces.contains(inst.match_goal)) {
      // TODO verbose
      // if (thisAgent->soar_verbose_flag ||
      // thisAgent->sysparams[TRACE_CHUNKS_SYSPARAM])
      // {
      // char buf[64];
      // std::ostringstream message;
      // message << "\nnot chunking due to chunk-free state " <<
      // symbol_to_string(thisAgent, inst->match_goal, false, buf, 64);
      // print(thisAgent, message.str().c_str());
      // xml_generate_verbose(thisAgent, message.str().c_str());
      // }
      return false;
    }

    if (learningOnly && !chunky_problem_spaces.contains(inst.match_goal)) {
      // TODO verbose
      // if (thisAgent->soar_verbose_flag ||
      // thisAgent->sysparams[TRACE_CHUNKS_SYSPARAM])
      // {
      // char buf[64];
      // std::ostringstream message;
      // message << "\nnot chunking due to non-chunky state " <<
      // symbol_to_string(thisAgent, inst->match_goal, false, buf, 64);
      // print(thisAgent, message.str().c_str());
      // xml_generate_verbose(thisAgent, message.str().c_str());
      // }
      return false;
    }

    /*
     * allow_bottom_up_chunks will be false if a chunk was already learned
     * in a lower goal
     */
    if (!learningAllGoals && !inst.match_goal.goalInfo.allow_bottom_up_chunks) {
      return false;
    }

    /*
     * if a result is created in a state higher than the immediate
     * superstate, don't make chunks for intermediate justifications.
     */
    for (Preference p = inst.preferences_generated; p != null; p = p.inst_next) {
      if (p.id.getLevel() < inst.match_goal_level - 1) {
        return false;
      }
    }

    return true;
  }

  /**
   * This the main chunking routine. It takes an instantiation, and a flag "variablize"--if FALSE,
   * the chunk will not be variablized. (If TRUE, it may still not be variablized, due to
   * chunk-free-problem-spaces, ^quiescence t, etc.)
   *
   * <p>chunk.cpp:973:chunk_instantiation
   *
   * @param inst
   * @param dont_variablize
   * @param custom_inst_list
   */
  public void chunk_instantiation(
      Instantiation inst, boolean dont_variablize, ByRef<Instantiation> custom_inst_list) {
    // #ifndef NO_TIMING_STUFF
    // #ifdef DETAILED_TIMING_STATS
    // struct timeval saved_start_tv;
    // #endif
    // #endif

    // if it only matched an attribute impasse, don't chunk
    if (inst.match_goal == null) return;

    // if no preference is above the match goal level, exit
    Preference pref = null;
    for (Preference i = inst.preferences_generated; i != null; i = i.inst_next) {
      if (i.id.getLevel() < inst.match_goal_level) {
        pref = i;
        break;
      }
    }
    if (pref == null) return;

    // #ifndef NO_TIMING_STUFF
    // #ifdef DETAILED_TIMING_STATS
    // start_timer (thisAgent, &saved_start_tv);
    // #endif
    // #endif

    Preference results = get_results_for_instantiation(inst);
    if (results == null) {
      // RPM 2/2013: this goto to support reuse of timer code
      //             timer code hasn't been ported yet, so ignoring
      // goto chunking_done;
      return;
    }

    // set allow_bottom_up_chunks to false for all higher goals to prevent chunking
    for (IdentifierImpl g = inst.match_goal.goalInfo.higher_goal;
        g != null && g.goalInfo.allow_bottom_up_chunks;
        g = g.goalInfo.higher_goal) g.goalInfo.allow_bottom_up_chunks = false;

    int grounds_level = inst.match_goal_level - 1;

    // TODO All these ops should be in Backtracer
    backtrace.backtrace_number++;
    if (backtrace.backtrace_number == 0) backtrace.backtrace_number = 1;
    backtrace.grounds_tc++;
    if (backtrace.grounds_tc == 0) backtrace.grounds_tc = 1;
    backtrace.potentials_tc++;
    if (backtrace.potentials_tc == 0) backtrace.potentials_tc = 1;
    backtrace.locals_tc++;
    if (backtrace.locals_tc == 0) backtrace.locals_tc = 1;
    backtrace.grounds.clear();
    backtrace.positive_potentials.clear();
    backtrace.locals.clear();
    this.instantiations_with_nots.clear();

    // Start a new structure for this potential chunk
    ExplainChunk temp_explain_chunk = null;
    if (this.explain.isEnabled()) {
      temp_explain_chunk = new ExplainChunk();
      this.explain.reset_backtrace_list();
    }

    final Trace trace = context.getTrace();
    ByRef<Boolean> reliable = new ByRef<Boolean>(true);

    /* --- backtrace through the instantiation that produced each result --- */
    for (pref = results; pref != null; pref = pref.next_result) {
      trace.print(Category.BACKTRACING, "\nFor result preference %s ", pref);
      backtrace.backtrace_through_instantiation(pref.inst, grounds_level, null, reliable, 0);
    }

    while (true) {
      backtrace.trace_locals(grounds_level, reliable);
      backtrace.trace_grounded_potentials();
      if (!backtrace.trace_ungrounded_potentials(grounds_level, reliable)) break;
    }

    backtrace.positive_potentials.clear();

    // backtracing done; collect the grounds into the chunk
    ByRef<ChunkCondition> top_cc = ByRef.create(null);
    ByRef<ChunkCondition> bottom_cc = ByRef.create(null);
    NotStruct nots = null;
    {
      final Marker tc_for_grounds = DefaultMarker.create();
      build_chunk_conds_for_grounds_and_add_negateds(top_cc, bottom_cc, tc_for_grounds, reliable);
      nots = get_nots_for_instantiated_conditions(instantiations_with_nots, tc_for_grounds);
    }

    boolean variablize = !dont_variablize && reliable.value && should_variablize(inst);

    // SMEM Check for LTI validity
    if (variablize) {
      if (top_cc.value != null) {
        // need a temporary copy of the actions
        variablization_tc = DefaultMarker.create();
        final Action rhs = copy_and_variablize_result_list(results, true);

        if (!DefaultSemanticMemory.smem_valid_production(top_cc.value.variablized_cond, rhs)) {
          variablize = false;

          trace.print(
              Category.BACKTRACING,
              "\nWarning: LTI validation failed, creating justification instead.");
        }

        // remove temporary copy
        // deallocate_action_list (thisAgent, rhs);
      }
    }

    // get symbol for name of new chunk or justification
    String prod_name = null;
    ProductionType prod_type = null;
    boolean print_name = false;
    boolean print_prod = false;

    if (variablize) {
      this.chunks_this_d_cycle++;
      prod_name = generate_chunk_name_sym_constant(inst);

      /*   old way of generating chunk names ...
      prod_name = generate_new_sym_constant ("chunk-",&thisAgent->chunk_count);
      thisAgent->chunks_this_d_cycle)++;
      */

      prod_type = ProductionType.CHUNK;
      // TODO startNewLine()?
      print_name = trace.isEnabled(Category.CHUNK_NAMES);
      trace.print(Category.CHUNK_NAMES, "Building %s", prod_name);
      print_prod = trace.isEnabled(Category.CHUNKS);
    } else {
      prod_name =
          Productions.generateUniqueName(
              context.getProductions(), "justification-", justification_count);
      prod_type = ProductionType.JUSTIFICATION;
      // TODO startNewLine()?
      print_name = trace.isEnabled(Category.JUSTIFICATION_NAMES);
      trace.print(Category.JUSTIFICATION_NAMES, "Building %s", prod_name);
      print_prod = trace.isEnabled(Category.JUSTIFICATIONS);
    }

    // if there aren't any grounds, exit
    if (top_cc.value == null) {
      context.getPrinter().print("Warning: chunk has no grounds, ignoring it.");
      // goto chunking_done;
      return;
    }

    if (this.chunks_this_d_cycle > maxChunks) {
      context.getPrinter().warn("\nWarning: reached max-chunks! Halting system.");
      this.maxChunksReached = true;
      // goto chunking_done
      return;
    }

    Condition lhs_top = top_cc.value.variablized_cond;
    Condition lhs_bottom = bottom_cc.value.variablized_cond;
    if (variablize) {
      this.predefinedSyms.getSyms().getVariableGenerator().reset(lhs_top, null);
      this.variablization_tc = DefaultMarker.create();
      variablize_condition_list(lhs_top);
      variablize_nots_and_insert_into_conditions(nots, lhs_top);
    }

    Action rhs = copy_and_variablize_result_list(results, variablize);

    // add goal/impasse tests to it
    add_goal_or_impasse_tests(top_cc.value.next_prev);

    // make the production
    final Production prod =
        Production.newBuilder()
            .type(prod_type)
            .location(NEW_PRODUCTION_SOURCE)
            .name(prod_name)
            .conditions(lhs_top, lhs_bottom)
            .actions(rhs)
            .build();

    // Reorder the production
    try {
      context.getProductions().addChunk(prod);
    } catch (ReordererException e) {
      final Printer p = context.getPrinter();
      p.print("\nUnable to reorder this chunk:\n ");
      Conditions.print_condition_list(p, lhs_top, 2, false);
      p.print("\n -->\n ");
      Action.print_action_list(p, rhs, 3, false);
      p.print("\n\nThis error is likely caused by the reasons outlined section 4 of the Soar\n");
      p.print("manual, subsection \"revising the substructure of a previous result\".\n");
      p.print("\n");
      p.print("Check that the rules are not revising substructure of a result matched only\n");
      p.print("through the local state.\n");

      // a bunch of memory stuff goes here, unnecessary in java

      // We cannot proceed, the GDS will crash in decide.cpp:decide_non_context_slot
      this.decisionCycle.halt("Bad chunk");
      // goto chunking_done
      return;
    }

    Instantiation chunk_inst = null;

    {
      ByRef<Condition> inst_lhs_top = ByRef.create(null);
      ByRef<Condition> inst_lhs_bottom = ByRef.create(null);

      reorder_instantiated_conditions(
          top_cc.value.next_prev.toListHead(), inst_lhs_top, inst_lhs_bottom);

      // Record the list of grounds in the order they will appear in the
      // chunk.
      if (this.explain.isEnabled())
        temp_explain_chunk.all_grounds = inst_lhs_top.value; /* Not a copy yet */

      chunk_inst = new Instantiation(prod, null, null);
      chunk_inst.top_of_instantiated_conditions = inst_lhs_top.value;
      chunk_inst.bottom_of_instantiated_conditions = inst_lhs_bottom.value;
      chunk_inst.nots = nots;

      chunk_inst.GDS_evaluated_already = false; /* REW:  09.15.96 */

      chunk_inst.reliable = reliable.value;

      chunk_inst.in_ms = true; /* set TRUE for now, we'll find out later... */
      make_clones_of_results(results, chunk_inst);
      recMemory.fill_in_new_instantiation_stuff(chunk_inst, true);
    } /* matches { condition *inst_lhs_top, *inst_lhs_bottom ...  */

    /* RBD 4/6/95 Need to copy cond's and actions for the production here,
    otherwise some of the variables might get deallocated by the call to
    add_production_to_rete() when it throws away chunk variable names. */
    if (this.explain.isEnabled()) {
      final ByRef<Condition> new_top = ByRef.create(null);
      final ByRef<Condition> new_bottom = ByRef.create(null);
      Condition.copy_condition_list(prod.getFirstCondition(), new_top, new_bottom);
      temp_explain_chunk.conds = new_top.value;
      temp_explain_chunk.actions = copy_and_variablize_result_list(results, variablize);
    }

    final ProductionAddResult rete_addition_result =
        this.rete.add_production_to_rete(prod, chunk_inst, print_name, false);

    // If didn't immediately excise the chunk from the rete net
    // then record the temporary structure in the list of explained chunks.

    if (this.explain.isEnabled()) {
      if ((rete_addition_result != ProductionAddResult.DUPLICATE_PRODUCTION)
          && ((prod_type != ProductionType.JUSTIFICATION)
              || (rete_addition_result != ProductionAddResult.REFRACTED_INST_DID_NOT_MATCH))) {
        temp_explain_chunk.name = prod_name;
        this.explain.explain_add_temp_to_chunk_list(temp_explain_chunk);
      } else {
        // RBD 4/6/95 if excised the chunk, discard previously-copied stuff
        // Not much to do here in Java
      }
    }

    // deallocate chunks conds and variablized conditions
    // Not much to do in Java...

    if (print_prod && (rete_addition_result != ProductionAddResult.DUPLICATE_PRODUCTION)) {
      context.getPrinter().print("\n");
      prod.print(context.getPrinter(), false);
    }

    if (rete_addition_result == ProductionAddResult.DUPLICATE_PRODUCTION) {
      context.getProductions().exciseProduction(prod, false);
    } else if ((prod_type == ProductionType.JUSTIFICATION)
        && (rete_addition_result == ProductionAddResult.REFRACTED_INST_DID_NOT_MATCH)) {
      context.getProductions().exciseProduction(prod, false);
    }

    if (rete_addition_result != ProductionAddResult.REFRACTED_INST_MATCHED) {
      // it didn't match, or it was a duplicate production
      // tell the firer it didn't match, so it'll only assert the o-supported preferences
      chunk_inst.in_ms = false;
    }

    if (rete_addition_result != ProductionAddResult.DUPLICATE_PRODUCTION) {
      context.getEvents().fireEvent(new ProductionAddedEvent(context, prod));
    }

    // assert the preferences
    custom_inst_list.value = chunk_inst.insertAtHeadOfProdList(custom_inst_list.value);

    if (!maxChunksReached) chunk_instantiation(chunk_inst, dont_variablize, custom_inst_list);

    // #ifndef NO_TIMING_STUFF
    // #ifdef DETAILED_TIMING_STATS
    //               stop_timer (thisAgent, &saved_start_tv,
    // &thisAgent->chunking_cpu_time[thisAgent->current_phase]);
    // #endif
    // #endif
    return;

    // RPM 2/2013: commented out gotos above go here
    // chunking_done:
    // #ifndef NO_TIMING_STUFF
    // #ifdef DETAILED_TIMING_STATS
    // local_timer.stop();
    // thisAgent->timers_chunking_cpu_time[thisAgent->current_phase].update(local_timer);
    // #endif
    // #endif

  }
}
