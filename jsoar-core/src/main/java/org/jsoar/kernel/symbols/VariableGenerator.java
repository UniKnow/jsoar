/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 18, 2008
 */
package org.jsoar.kernel.symbols;

import org.jsoar.kernel.lhs.Condition;
import org.jsoar.kernel.rhs.Action;
import org.jsoar.util.ListHead;
import org.jsoar.util.ListItem;
import org.jsoar.util.markers.DefaultMarker;
import org.jsoar.util.markers.Marker;

/**
 * <em>This is an internal interface. Don't use it unless you know what you're doing.</em>
 *
 * <p>These routines are used for generating new variables. The variables aren't necessarily
 * "completely" new--they might occur in some existing production. But we usually need to make sure
 * the new variables don't overlap with those already used in a *certain* production--for instance,
 * when variablizing a chunk, we don't want to introduce a new variable that coincides with the name
 * of a variable already in an NCC in the chunk.
 *
 * <p>To use these routines, first call reset_variable_generator(), giving it lists of conditions
 * and actions whose variables should not be used. Then call generate_new_variable() any number of
 * times; each time, you give it a string to use as the prefix for the new variable's name. The
 * prefix string should not include the opening "&lt;".
 *
 * @author ray
 */
public class VariableGenerator {
  private SymbolFactoryImpl syms;

  private int[] gensymed_variable_count = new int[26];

  private int current_variable_gensym_number = 0;

  /** @param syms */
  VariableGenerator(SymbolFactoryImpl syms) {
    this.syms = syms;
  }

  /** @return the syms */
  public SymbolFactoryImpl getSyms() {
    return syms;
  }

  /**
   * reset_variable_generator
   *
   * @param conds_with_vars_to_avoid
   * @param actions_with_vars_to_avoid
   */
  public void reset(Condition conds_with_vars_to_avoid, Action actions_with_vars_to_avoid) {
    // reset counts, and increment the gensym number
    for (int i = 0; i < 26; i++) {
      gensymed_variable_count[i] = 1;
    }

    current_variable_gensym_number++;
    if (current_variable_gensym_number == Integer.MAX_VALUE) {
      syms.reset_variable_gensym_numbers();
      current_variable_gensym_number = 1;
    }

    // mark all variables in the given conds and actions
    Marker tc_number = DefaultMarker.create();
    ListHead<Variable> var_list = ListHead.newInstance();

    Condition.addAllVariables(conds_with_vars_to_avoid, tc_number, var_list);
    Action.addAllVariables(actions_with_vars_to_avoid, tc_number, var_list);

    for (ListItem<Variable> var = var_list.first; var != null; var = var.next) {
      var.item.gensym_number = current_variable_gensym_number;
    }
  }

  public Variable generate_new_variable(String prefix) {
    char first_letter = prefix.charAt(0);
    if (Character.isLetter(first_letter)) {
      first_letter = Character.toLowerCase(first_letter);
    } else {
      first_letter = 'v';
    }

    Variable New = null;
    while (true) {
      String name = "<" + prefix + gensymed_variable_count[first_letter - 'a']++ + ">";

      New = syms.make_variable(name);
      if (New.gensym_number != current_variable_gensym_number) {
        break;
      }
    }

    New.current_binding_value = null;
    New.gensym_number = current_variable_gensym_number;
    return New;
  }
}
