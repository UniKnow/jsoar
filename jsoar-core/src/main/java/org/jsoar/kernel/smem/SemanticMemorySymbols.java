/*
 * Copyright (c) 2010 Dave Ray <daveray@gmail.com>
 *
 * Created on Jun 23, 2010
 */
package org.jsoar.kernel.smem;

import org.jsoar.kernel.symbols.Symbol;
import org.jsoar.kernel.symbols.SymbolFactoryImpl;
import org.jsoar.kernel.symbols.SymbolImpl;

/**
 * Standard symbols used by smem.
 *
 * <p>agent.h
 *
 * @author ray
 */
class SemanticMemorySymbols {
  public final SymbolImpl smem_sym;
  public final SymbolImpl smem_sym_cmd;
  public final SymbolImpl smem_sym_result;

  public final SymbolImpl smem_sym_retrieved;
  public final Symbol smem_sym_status;
  public final SymbolImpl smem_sym_success;
  public final SymbolImpl smem_sym_failure;
  public final SymbolImpl smem_sym_bad_cmd;

  public final Symbol smem_sym_retrieve;
  public final Symbol smem_sym_query;
  public final Symbol smem_sym_negquery;
  public final Symbol smem_sym_prohibit;
  public final Symbol smem_sym_store;
  public final Symbol smem_sym_math_query;

  public final Symbol smem_sym_max;
  public final Symbol smem_sym_min;
  public final Symbol smem_sym_less;
  public final Symbol smem_sym_greater;
  public final Symbol smem_sym_less_or_equal;
  public final Symbol smem_sym_greater_or_equal;

  public SemanticMemorySymbols(SymbolFactoryImpl syms) {
    // symtab.cpp:create_predefined_symbols
    smem_sym = syms.createString("smem");
    smem_sym_cmd = syms.createString("command");
    smem_sym_result = syms.createString("result");

    smem_sym_retrieved = syms.createString("retrieved");
    smem_sym_status = syms.createString("status");
    smem_sym_success = syms.createString("success");
    smem_sym_failure = syms.createString("failure");
    smem_sym_bad_cmd = syms.createString("bad-cmd");

    smem_sym_retrieve = syms.createString("retrieve");
    smem_sym_query = syms.createString("query");
    smem_sym_negquery = syms.createString("neg-query");
    smem_sym_prohibit = syms.createString("prohibit");
    smem_sym_store = syms.createString("store");
    smem_sym_math_query = syms.createString("math-query");

    smem_sym_max = syms.createString("max");
    smem_sym_min = syms.createString("min");
    smem_sym_less = syms.createString("less");
    smem_sym_greater = syms.createString("greater");
    smem_sym_less_or_equal = syms.createString("less-or-equal");
    smem_sym_greater_or_equal = syms.createString("greater-or-equal");
  }
}
