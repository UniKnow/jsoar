/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 22, 2008
 */
package org.jsoar.kernel.rete;

import org.jsoar.kernel.Production;

/**
 * data for production nodes only
 *
 * <p>rete.cpp:383
 *
 * @author ray
 */
class ProductionNodeData implements BReteNodeData {
  enum AssertListType {
    O_LIST, // 0     /* moved here from soarkernel.h.  only used in rete.cpp */
    I_LIST // 1     /*   values for prod->OPERAND_which_assert_list */
  }

  public Production prod; /* the production */
  NodeVarNames parents_nvn; /* records variable names */

  // TODO: I think both of these fields belong in a Soar-specific sub-class
  // or something to decouple generic rete from Soar.
  MatchSetChange tentative_assertions; // pending MS changes
  MatchSetChange tentative_retractions;

  /**
   * Moved here from Production since it's only ever used by rete.
   *
   * <p>production.h:OPERAND_which_assert_list
   */
  AssertListType OPERAND_which_assert_list = AssertListType.O_LIST;

  /**
   * Moved here from Production since it's only ever used by rete.
   *
   * <p>RPM test workaround for bug #139 (old Soar Bugzilla)
   *
   * <p>production.h:already_fired
   */
  boolean justificationAlreadyFired = false;

  public ProductionNodeData() {}

  public ProductionNodeData(ProductionNodeData other) {
    this.prod = other.prod;
    // TODO this.parents_nvn = other.parents_nvn (.copy())
    this.tentative_assertions = other.tentative_assertions;
    this.tentative_retractions = other.tentative_retractions;
  }
  /** @return Shallow copy of this object */
  public ProductionNodeData copy() {
    return new ProductionNodeData(this);
  }
}
