/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 22, 2008
 */
package org.jsoar.kernel.rete;

import org.jsoar.kernel.Production;

/**
 * Represents a node in the rete network.
 *
 * <p>A node can be a member of several linked lists:
 *
 * <ul>
 *   <li>Child list of the node's parent. Use {@link #first_child} and {@link #next_sibling}
 *   <li>List of nodes from an {@link AlphaMemory}. Use {@link PosNegNodeData#next_from_alpha_mem}
 *       and {@link PosNegNodeData#prev_from_alpha_mem}
 * </ul>
 *
 * <p>rete.cpp:401
 *
 * @author ray
 */
public class ReteNode {
  ReteNodeType node_type; // tells what kind of node this is

  int left_hash_loc_field_num; // used only on hashed nodes, field_num: 0=id, 1=attr, 2=value
  int left_hash_loc_levels_up; // 0=current node's alphamem, 1=parent's, etc.
  /*final*/ int node_id; // used for hash function

  ReteNode parent; // points to parent node
  ReteNode first_child; // used for dll of all children
  ReteNode next_sibling; // regardless of unlinking status

  private AReteNodeData a = null;
  private BReteNodeData b = null;

  private ReteNode(ReteNodeType type, int node_id) {
    this.node_type = type;
    this.node_id = node_id;

    if (type.bnode_is_positive() && type != ReteNodeType.P_BNODE) {
      a = new PosNodeData(this);
    } else {
      a = new NonPosNodeData();
    }

    if (type == ReteNodeType.P_BNODE) {
      b = new ProductionNodeData();
    } else if (type == ReteNodeType.CN_BNODE || type == ReteNodeType.CN_PARTNER_BNODE) {
      b = new ConjunctiveNegationNodeData();
    } else if (type == ReteNodeType.MEMORY_BNODE || type == ReteNodeType.UNHASHED_MEMORY_BNODE) {
      b = new BetaMemoryNodeData();
    } else {
      b = new PosNegNodeData();
    }

    validateUnions();
  }

  private ReteNode(ReteNode other) {
    this.node_type = other.node_type;
    this.left_hash_loc_levels_up = other.left_hash_loc_levels_up;
    this.left_hash_loc_field_num = other.left_hash_loc_field_num;
    this.node_id = other.node_id;
    this.parent = other.parent;
    this.first_child = other.first_child;
    this.next_sibling = other.next_sibling;
    this.a = other.a != null ? other.a.copy() : null;
    this.b = other.b != null ? other.b.copy() : null;

    validateUnions();
  }

  private void validateUnions() {
    // Enforce a and b "unions"
    assert a != null;
    assert b != null;
  }

  public PosNodeData a_pos() {
    return (PosNodeData) a;
  }

  public NonPosNodeData a_np() {
    return (NonPosNodeData) a;
  }

  public PosNegNodeData b_posneg() {
    return (PosNegNodeData) b;
  }

  BetaMemoryNodeData b_mem() {
    return (BetaMemoryNodeData) b;
  }

  ConjunctiveNegationNodeData b_cn() {
    return (ConjunctiveNegationNodeData) b;
  }

  public ProductionNodeData b_p() {
    return (ProductionNodeData) b;
  }

  /**
   * rete.cpp:432:real_parent_node
   *
   * @return real parent node of this node
   */
  public ReteNode real_parent_node() {
    return (node_type.bnode_is_bottom_of_split_mp() ? parent.parent : parent);
  }

  /**
   * rete.cpp:448:node_is_right_unlinked
   *
   * @return true if this node is right unlinked
   */
  public boolean node_is_right_unlinked() {
    return b_posneg().node_is_right_unlinked;
    // return (((unsigned long)((node)->b.posneg.next_from_alpha_mem)) & 1);
  }

  /** rete.cpp:455:mark_node_as_right_unlinked */
  private void mark_node_as_right_unlinked() {
    b_posneg().node_is_right_unlinked = true;
    // (node)->b.posneg.next_from_alpha_mem = static_cast<rete_node_struct *>((void *)1);
  }

  /** rete.cpp:483:relink_to_right_mem */
  /*package*/ void relink_to_right_mem() {
    /* find first ancestor that's linked */
    ReteNode rtrm_ancestor = b_posneg().nearest_ancestor_with_same_am;
    ReteNode rtrm_prev;
    while (rtrm_ancestor != null && rtrm_ancestor.node_is_right_unlinked()) {
      rtrm_ancestor = rtrm_ancestor.b_posneg().nearest_ancestor_with_same_am;
    }
    if (rtrm_ancestor != null) {
      /* insert just before that ancestor */
      rtrm_prev = rtrm_ancestor.b_posneg().prev_from_alpha_mem;
      this.b_posneg().next_from_alpha_mem = rtrm_ancestor;
      this.b_posneg().prev_from_alpha_mem = rtrm_prev;
      rtrm_ancestor.b_posneg().prev_from_alpha_mem = this;
      if (rtrm_prev != null) {
        rtrm_prev.b_posneg().next_from_alpha_mem = this;
      } else {
        this.b_posneg().alpha_mem_.beta_nodes = this;
      }
    } else {
      /* no such ancestor, insert at tail of list */
      rtrm_prev = this.b_posneg().alpha_mem_.last_beta_node;
      this.b_posneg().next_from_alpha_mem = null;
      this.b_posneg().prev_from_alpha_mem = rtrm_prev;
      this.b_posneg().alpha_mem_.last_beta_node = this;
      if (rtrm_prev != null) {
        rtrm_prev.b_posneg().next_from_alpha_mem = this;
      } else {
        this.b_posneg().alpha_mem_.beta_nodes = this;
      }
    }
    this.b_posneg().node_is_right_unlinked = false;
  }

  /** rete.cpp:512:unlink_from_right_mem */
  /*package*/ void unlink_from_right_mem() {
    if (this.b_posneg().next_from_alpha_mem == null) {
      this.b_posneg().alpha_mem_.last_beta_node = this.b_posneg().prev_from_alpha_mem;
    }
    // The code below is an expansion of this remove_from_dll macro...
    //        remove_from_dll (this.b_posneg.alpha_mem_.beta_nodes, this,
    //                         b.posneg.next_from_alpha_mem,
    //                         b.posneg.prev_from_alpha_mem);
    if (this.b_posneg().next_from_alpha_mem != null) {
      this.b_posneg().next_from_alpha_mem.b_posneg().prev_from_alpha_mem =
          this.b_posneg().prev_from_alpha_mem;
    }
    if (this.b_posneg().prev_from_alpha_mem != null) {
      this.b_posneg().prev_from_alpha_mem.b_posneg().next_from_alpha_mem =
          this.b_posneg().next_from_alpha_mem;
    } else {
      this.b_posneg().alpha_mem_.beta_nodes = this.b_posneg().next_from_alpha_mem;
    }

    mark_node_as_right_unlinked();
  }

  /**
   * rete.cpp:532:node_is_left_unlinked
   *
   * @return True if this node is left unlinked
   */
  /*package*/ boolean node_is_left_unlinked() {
    return a_pos().node_is_left_unlinked;
    // return (((unsigned long)((node)->a.pos.next_from_beta_mem)) & 1);
  }

  /** rete.cpp:539:mark_node_as_left_unlinked */
  /*package*/ void mark_node_as_left_unlinked() {
    a_pos().node_is_left_unlinked = true;
    // (node)->a.pos.next_from_beta_mem = static_cast<rete_node_struct *>((void *)1);
  }

  /** rete.cpp:547:relink_to_left_mem */
  /*package*/ void relink_to_left_mem() {
    a_pos().from_beta_mem.insertAtHead(parent.b_mem().first_linked_child);
    a_pos().node_is_left_unlinked = false;
  }

  /** rete.cpp:555:unlink_from_left_mem */
  /*package*/ void unlink_from_left_mem() {
    a_pos().from_beta_mem.remove(parent.b_mem().first_linked_child);
    mark_node_as_left_unlinked();
  }

  /** rete.cpp:570:make_mp_bnode_left_unlinked */
  /*package*/ void make_mp_bnode_left_unlinked() {
    this.a_np().is_left_unlinked = true;
  }

  /** rete.cpp:575:make_mp_bnode_left_linked */
  /*package*/ void make_mp_bnode_left_linked() {
    this.a_np().is_left_unlinked = false;
  }

  /**
   * rete.cpp:580:mp_bnode_is_left_unlinked
   *
   * @return true if left unlinked
   */
  /*package*/ boolean mp_bnode_is_left_unlinked() {
    return this.a_np().is_left_unlinked;
  }

  /**
   * Splices a given node out of its parent's list of children. This would be a lot easier if the
   * children lists were doubly-linked, but that would take up a lot of extra space.
   *
   * <p>rete.cpp:1744:remove_node_from_parents_list_of_children
   */
  /*package*/ void remove_node_from_parents_list_of_children() {
    ReteNode prev_sibling = this.parent.first_child;
    if (prev_sibling == this) {
      this.parent.first_child = this.next_sibling;
      return;
    }
    while (prev_sibling.next_sibling != this) {
      prev_sibling = prev_sibling.next_sibling;
    }
    prev_sibling.next_sibling = this.next_sibling;
  }

  /**
   * Scans up the net and finds the first (i.e., nearest) ancestor node that uses a given alpha_mem.
   * Returns that node, or NIL if none exists. For the returned node, {@link
   * ReteNodeType#bnode_is_posneg()} will always be true.
   *
   * <p>rete.cpp:1824:nearest_ancestor_with_same_am
   *
   * @param am
   * @return nearest ancestor
   */
  ReteNode nearest_ancestor_with_same_am(AlphaMemory am) {
    ReteNode node = this;
    while (node.node_type != ReteNodeType.DUMMY_TOP_BNODE) {
      if (node.node_type == ReteNodeType.CN_BNODE) node = node.b_cn().partner.parent;
      else node = node.real_parent_node();
      if (node.node_type.bnode_is_posneg() && (node.b_posneg().alpha_mem_ == am)) return node;
    }
    return null;
  }

  static ReteNode createDummy() {
    return new ReteNode(ReteNodeType.DUMMY_TOP_BNODE, 0);
  }

  /**
   * Make a new beta memory node, return a pointer to it.
   *
   * <p>rete.cpp:1840:make_new_mem_node
   *
   * @param rete the owning rete
   * @param parent the parent node
   * @param node_type the type of node to create
   * @param left_hash_loc left hash location
   * @return new memory node
   */
  static ReteNode make_new_mem_node(
      Rete rete, ReteNode parent, ReteNodeType node_type, VarLocation left_hash_loc) {
    ReteNode node = new ReteNode(node_type, rete.get_next_beta_node_id());

    node.parent = parent;
    node.next_sibling = parent.first_child;
    parent.first_child = node;

    /* These hash fields are not used for unhashed node types */
    node.left_hash_loc_field_num = left_hash_loc.field_num;
    node.left_hash_loc_levels_up = left_hash_loc.levels_up;

    /* --- call new node's add_left routine with all the parent's tokens --- */
    rete.update_node_with_matches_from_above(node);

    return node;
  }

  /**
   * Make a new positive join node, return a pointer to it.
   *
   * <p>rete.cpp:1873:make_new_positive_node
   *
   * @param rete
   * @param parent_mem
   * @param node_type
   * @param am
   * @param rt
   * @param prefer_left_unlinking
   * @return a new positive node
   */
  static ReteNode make_new_positive_node(
      Rete rete,
      ReteNode parent_mem,
      ReteNodeType node_type,
      AlphaMemory am,
      ReteTest rt,
      boolean prefer_left_unlinking) {
    ReteNode node = new ReteNode(node_type, 0);

    node.parent = parent_mem;
    node.next_sibling = parent_mem.first_child;
    parent_mem.first_child = node;
    node.relink_to_left_mem();
    node.b_posneg().other_tests = rt;
    node.b_posneg().alpha_mem_ = am;
    node.b_posneg().nearest_ancestor_with_same_am = node.nearest_ancestor_with_same_am(am);
    node.relink_to_right_mem();

    // don't need to force WM through new node yet, as it's just a join
    // node with no children

    // unlink the join node from one side if possible
    if (parent_mem.a_np().tokens == null) {
      node.unlink_from_right_mem();
    }
    if (am.right_mems == null && !node.node_is_right_unlinked()) {
      node.unlink_from_left_mem();
    }
    if (prefer_left_unlinking && (parent_mem.a_np().tokens == null) && am.right_mems == null) {
      node.relink_to_right_mem();
      node.unlink_from_left_mem();
    }

    return node;
  }

  /**
   * Split a given MP node into separate M and P nodes, return a pointer to the new Memory node.
   *
   * <p>That is, splits an {@link ReteNodeType#MP_BNODE} or {@link ReteNodeType#UNHASHED_MP_BNODE}
   * node into two nodes:
   *
   * <ul>
   *   <li>A new {@link ReteNodeType#MEMORY_BNODE} (possibly unhashed) in the same position as the
   *       input node. This node is returned from the method.
   *   <li>A new {@link ReteNodeType#POSITIVE_BNODE} (possibly unhashed) as a child of the memory
   *       node. This node inherits the children of the input node.
   * </ul>
   *
   * <p>rete.cpp:1916:split_mp_node
   *
   * @param mp_node the node to split
   * @return a new memory bnode, possible unhashed.
   */
  static ReteNode split_mp_node(Rete rete, ReteNode mp_node) {
    assert mp_node.node_type == ReteNodeType.MP_BNODE
        || mp_node.node_type == ReteNodeType.UNHASHED_MP_BNODE;

    // determine appropriate node types for new M and P nodes
    final ReteNodeType mem_node_type;
    final ReteNodeType node_type;
    if (mp_node.node_type == ReteNodeType.MP_BNODE) {
      node_type = ReteNodeType.POSITIVE_BNODE;
      mem_node_type = ReteNodeType.MEMORY_BNODE;
    } else {
      node_type = ReteNodeType.UNHASHED_POSITIVE_BNODE;
      mem_node_type = ReteNodeType.UNHASHED_MEMORY_BNODE;
    }

    // save a copy of the MP data, then kill the MP node
    final ReteNode parent = mp_node.parent;
    mp_node.remove_node_from_parents_list_of_children();

    // create the new memory node
    final ReteNode mem_node = new ReteNode(mem_node_type, mp_node.node_id);

    // Insert the memory node in the position of the original MP node
    mem_node.parent = parent;
    mem_node.next_sibling = parent.first_child;
    parent.first_child = mem_node;

    mem_node.left_hash_loc_field_num = mp_node.left_hash_loc_field_num;
    mem_node.left_hash_loc_levels_up = mp_node.left_hash_loc_levels_up;

    // Transfer the MP node's tokens to new memory node
    mem_node.a_np().tokens = mp_node.a_np().tokens;
    for (Token t = mp_node.a_np().tokens; t != null; t = t.next_of_node) {
      t.node = mem_node;
    }

    final boolean mpIsLeftUnlinked = mp_node.mp_bnode_is_left_unlinked();

    // the old MP node will get transmogrified into the new Pos node
    final ReteNode pos_node = mp_node; // TODO new ReteNode(node_type, mp_node.node_id);
    // transmogrify the old MP node into the new Pos node
    pos_node.node_type = node_type;
    pos_node.a = new PosNodeData(pos_node);

    // Make the pos node a child of the new memory node
    pos_node.parent = mem_node;
    mem_node.first_child = pos_node;

    // Inherit the children of the input MP node
    pos_node.first_child = mp_node.first_child;

    /*
    TODO: Necessary when "new ReteNode(...)" above is enabled
    for(ReteNode child = pos_node.first_child; child != null; child = child.next_sibling)
    {
        child.parent = pos_node;
    }
    */

    pos_node.next_sibling = null; // The new pos node has no siblings
    pos_node.b = mp_node.b_posneg();
    pos_node.relink_to_left_mem(); /* for now, but might undo this below */

    // set join node's unlinking status according to mp_copy's
    if (mpIsLeftUnlinked) {
      pos_node.unlink_from_left_mem();
    }

    // Make sure nothing got screwed up
    mem_node.validateUnions();
    pos_node.validateUnions();

    return mem_node;
  }

  /**
   * Merge a given Memory node and its one positive join child into an MP node, returning a pointer
   * to the MP node. That is,
   *
   * <p>{@link ReteNodeType#MEMORY_BNODE} + {@link ReteNodeType#POSITIVE_BNODE} becomes a {@link
   * ReteNodeType#MP_BNODE}.
   *
   * <p>{@link ReteNodeType#UNHASHED_MEMORY_BNODE} + {@link ReteNodeType#UNHASHED_POSITIVE_BNODE}
   * becomes a {@link ReteNodeType#UNHASHED_MP_BNODE}.
   *
   * <p>rete.cpp:1979:merge_into_mp_node
   *
   * @param mem_node
   * @return new node resulting from merge
   */
  static ReteNode merge_into_mp_node(Rete rete, ReteNode mem_node) {

    final ReteNode pos_node = mem_node.first_child;

    final boolean posNodeIsLeftUnlinked = pos_node.node_is_left_unlinked();
    final ReteNode parent = mem_node.parent;

    // sanity check: Mem node must have exactly one child
    if (pos_node == null || pos_node.next_sibling != null) {
      throw new IllegalArgumentException(
          "Internal error: tried to merge_into_mp_node, but <>1 child");
    }

    assert (mem_node.node_type == ReteNodeType.MEMORY_BNODE
            && pos_node.node_type == ReteNodeType.POSITIVE_BNODE)
        || (mem_node.node_type == ReteNodeType.UNHASHED_MEMORY_BNODE
            && pos_node.node_type == ReteNodeType.UNHASHED_POSITIVE_BNODE);

    // determine appropriate node type for new MP node
    final ReteNodeType node_type;
    if (mem_node.node_type == ReteNodeType.MEMORY_BNODE) {
      node_type = ReteNodeType.MP_BNODE;
    } else {
      node_type = ReteNodeType.UNHASHED_MP_BNODE;
    }

    // the old Pos node gets transmogrified into the new MP node
    final ReteNode mp_node = pos_node;
    mp_node.node_type = node_type;
    mp_node.node_id = mem_node.node_id;
    mp_node.b = pos_node.b_posneg(); // inherit posneg from pos_copy
    // assert mp_node.a_np == null;
    mp_node.a = new NonPosNodeData();

    // transfer the Mem node's tokens to the MP node
    mp_node.a_np().tokens = mem_node.a_np().tokens;
    for (Token t = mem_node.a_np().tokens; t != null; t = t.next_of_node) {
      t.node = mp_node;
    }

    mp_node.left_hash_loc_field_num = mem_node.left_hash_loc_field_num;
    mp_node.left_hash_loc_levels_up = mem_node.left_hash_loc_levels_up;

    // replace the Mem node with the new MP node in the network
    mp_node.parent = parent;
    mp_node.next_sibling = parent.first_child;
    parent.first_child = mp_node;
    mp_node.first_child = pos_node.first_child;

    // Now throw away the mem node
    mem_node.remove_node_from_parents_list_of_children();

    // set MP node's unlinking status according to pos_copy's
    mp_node.make_mp_bnode_left_linked();
    if (posNodeIsLeftUnlinked) {
      mp_node.make_mp_bnode_left_unlinked();
    }

    mp_node.validateUnions();

    return mp_node;
  }

  /**
   * Create a new MP node
   *
   * <p>rete.cpp:2043:make_new_mp_node
   *
   * @param rete
   * @param parent
   * @param node_type
   * @param left_hash_loc
   * @param am
   * @param rt
   * @param prefer_left_unlinking
   * @return new MP node
   */
  static ReteNode make_new_mp_node(
      Rete rete,
      ReteNode parent,
      ReteNodeType node_type,
      VarLocation left_hash_loc,
      AlphaMemory am,
      ReteTest rt,
      boolean prefer_left_unlinking) {
    ReteNodeType mem_node_type = null, pos_node_type = null;

    if (node_type == ReteNodeType.MP_BNODE) {
      pos_node_type = ReteNodeType.POSITIVE_BNODE;
      mem_node_type = ReteNodeType.MEMORY_BNODE;
    } else {
      pos_node_type = ReteNodeType.UNHASHED_POSITIVE_BNODE;
      mem_node_type = ReteNodeType.UNHASHED_MEMORY_BNODE;
    }
    // First create the parent memory node
    ReteNode mem_node = make_new_mem_node(rete, parent, mem_node_type, left_hash_loc);
    // Next create the child positive join node
    @SuppressWarnings("unused")
    ReteNode pos_node =
        make_new_positive_node(rete, mem_node, pos_node_type, am, rt, prefer_left_unlinking);

    // Now smash them together into an MP node
    return merge_into_mp_node(rete, mem_node);
  }

  /**
   * Make a new negative node and return it
   *
   * <p>rete.cpp:2069:make_new_negative_node
   *
   * @param rete
   * @param parent
   * @param node_type
   * @param left_hash_loc
   * @param am
   * @param rt
   * @return new negative node
   */
  static ReteNode make_new_negative_node(
      Rete rete,
      ReteNode parent,
      ReteNodeType node_type,
      VarLocation left_hash_loc,
      AlphaMemory am,
      ReteTest rt) {
    ReteNode node = new ReteNode(node_type, rete.get_next_beta_node_id());

    node.parent = parent;
    node.next_sibling = parent.first_child;
    parent.first_child = node;
    node.left_hash_loc_field_num = left_hash_loc.field_num;
    node.left_hash_loc_levels_up = left_hash_loc.levels_up;
    node.b_posneg().other_tests = rt;
    node.b_posneg().alpha_mem_ = am;
    node.b_posneg().nearest_ancestor_with_same_am = node.nearest_ancestor_with_same_am(am);
    node.relink_to_right_mem();

    // call new node's add_left routine with all the parent's tokens
    rete.update_node_with_matches_from_above(node);

    // if no tokens arrived from parent, unlink the node
    if (node.a_np().tokens == null) {
      node.unlink_from_right_mem();
    }

    return node;
  }

  /**
   * Make new CN and CN_PARTNER nodes, return a pointer to the CN node.
   *
   * <p>rete.cpp:2107:make_new_cn_node
   *
   * @param rete
   * @param parent
   * @param bottom_of_subconditions
   * @return new conjuctive negation node
   */
  static ReteNode make_new_cn_node(Rete rete, ReteNode parent, ReteNode bottom_of_subconditions) {
    // Find top node in the subconditions branch
    ReteNode ncc_subconditions_top_node = null;
    for (ReteNode node = bottom_of_subconditions; node != parent; node = node.parent) {
      ncc_subconditions_top_node = node;
    }

    final ReteNode node = new ReteNode(ReteNodeType.CN_BNODE, rete.get_next_beta_node_id());
    final ReteNode partner = new ReteNode(ReteNodeType.CN_PARTNER_BNODE, 0);

    /*
     * NOTE: for improved efficiency, <node> should be on the parent's
     * children list *after* the ncc subcontitions top node
     */
    ncc_subconditions_top_node.remove_node_from_parents_list_of_children();
    node.parent = parent;
    node.next_sibling = parent.first_child;
    ncc_subconditions_top_node.next_sibling = node;
    parent.first_child = ncc_subconditions_top_node;
    node.first_child = null;

    node.b_cn().partner = partner;

    partner.parent = bottom_of_subconditions;
    partner.next_sibling = bottom_of_subconditions.first_child;
    bottom_of_subconditions.first_child = partner;
    partner.first_child = null;
    partner.b_cn().partner = node;

    // call partner's add_left routine with all the parent's tokens
    rete.update_node_with_matches_from_above(partner);
    // call new node's add_left routine with all the parent's tokens
    rete.update_node_with_matches_from_above(node);

    return node;
  }

  /**
   * Make a new production node, return a pointer to it.
   *
   * <p>Does not handle the following tasks:
   *
   * <ul>
   *   <li>filling in {@code p_node->b.p.parents_nvn} or discarding chunk variable names
   *   <li>filling in stuff on new_prod (except does fill in {@code new_prod->p_node})
   *   <li>using {@code update_node_with_matches_from_above (p_node)} or handling an initial
   *       refracted instantiation
   * </ul>
   *
   * <p>rete.cpp:2163:make_new_production_node
   *
   * @param rete
   * @param parent
   * @param new_prod
   * @return new P-node
   */
  static ReteNode make_new_production_node(Rete rete, ReteNode parent, Production new_prod) {
    final ReteNode p_node = new ReteNode(ReteNodeType.P_BNODE, 0);

    new_prod.setReteNode(rete, p_node);
    p_node.parent = parent;
    p_node.next_sibling = parent.first_child;
    parent.first_child = p_node;
    p_node.first_child = null;
    p_node.b_p().prod = new_prod;
    return p_node;
  }

  /**
   * Create a new dummy matches node with the given parent
   *
   * @param parent The parent node
   * @return New dummy matches node
   */
  public static ReteNode createMatchesNode(ReteNode parent) {
    ReteNode dummy = new ReteNode(ReteNodeType.DUMMY_MATCHES_BNODE, 0);
    dummy.parent = parent;
    return dummy;
  }

  /**
   * rete.cpp:2218:deallocate_rete_node
   *
   * @param rete
   * @param node
   */
  static void deallocate_rete_node(Rete rete, ReteNode node) {
    // don't deallocate the dummy top node
    if (node == rete.dummy_top_node) return;

    // sanity check
    if (node.node_type == ReteNodeType.P_BNODE) {
      throw new IllegalArgumentException("deallocate_rete_node() called on p-node");
    }

    ReteNode parent = node.parent;

    // if a cn node, deallocate its partner first
    if (node.node_type == ReteNodeType.CN_BNODE) {
      deallocate_rete_node(rete, node.b_cn().partner);
    }

    // clean up any tokens at the node
    if (!node.node_type.bnode_is_bottom_of_split_mp()) {
      while (node.a_np().tokens != null) {
        rete.remove_token_and_subtree(node.a_np().tokens);
      }
    }

    // stuff for posneg nodes only
    if (node.node_type.bnode_is_posneg()) {
      node.b_posneg().other_tests = null;

      // right unlink the node, cleanup alpha memory
      if (!node.node_is_right_unlinked()) {
        node.unlink_from_right_mem();
      }
      node.b_posneg().alpha_mem_.remove_ref_to_alpha_mem(rete);
    }

    // remove the node from its parent's list
    node.remove_node_from_parents_list_of_children();

    // for unmerged pos. nodes: unlink, maybe merge its parent
    if (node.node_type.bnode_is_bottom_of_split_mp()) {
      if (!node.node_is_left_unlinked()) {
        node.unlink_from_left_mem();
      }
      // if parent is mem node with just one child, merge them
      if (parent.first_child != null && parent.first_child.next_sibling == null) {
        merge_into_mp_node(rete, parent);
        parent = null;
      }
    }

    // if parent has no other children, deallocate it, and recurse
    /*
     * Added check to make sure that parent wasn't deallocated in previous
     * merge
     */
    if (parent != null && parent.first_child == null) {
      deallocate_rete_node(rete, parent);
    }
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return node_id + ":" + node_type;
  }
}
