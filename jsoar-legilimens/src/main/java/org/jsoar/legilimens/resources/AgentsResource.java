/*
 * Copyright (c) 2009 Dave Ray <daveray@gmail.com>
 *
 * Created on Oct 9, 2009
 */
package org.jsoar.legilimens.resources;

import java.util.Map;

/** @author ray */
public class AgentsResource extends BaseResource {
  /* (non-Javadoc)
   * @see org.jsoar.legilimens.BaseResource#setTemplateAttributes(java.util.Map)
   */
  @Override
  public void setTemplateAttributes(Map<String, Object> attrs) {
    super.setTemplateAttributes(attrs);
    attrs.put("agents", getLegilimens().getAgents());
  }
}
