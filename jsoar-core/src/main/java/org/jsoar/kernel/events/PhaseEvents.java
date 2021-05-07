/*
 * Copyright (c) 2008  Dave Ray <daveray@gmail.com>
 *
 * Created on Oct 22, 2008
 */
package org.jsoar.kernel.events;

import java.util.EnumMap;
import java.util.Map;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.Phase;

/**
 * Definitions of various phase events. Note that clients should generally access event classes
 * through {@link Phase#getBeforeEvent()} and {@link Phase#getAfterEvent()}
 *
 * @author ray
 */
public class PhaseEvents {
  private PhaseEvents() {}

  /**
   * Create a map of immutable "before phase" events for the given agent
   *
   * @param agent The agent
   * @return Map from phase to "before phase" event objects
   */
  public static Map<Phase, AbstractPhaseEvent> createBeforeEvents(Agent agent) {
    EnumMap<Phase, AbstractPhaseEvent> events = new EnumMap<Phase, AbstractPhaseEvent>(Phase.class);
    events.put(Phase.APPLY, new BeforeApply(agent));
    events.put(Phase.DECISION, new BeforeDecision(agent));
    events.put(Phase.INPUT, new BeforeInput(agent));
    events.put(Phase.OUTPUT, new BeforeOutput(agent));
    events.put(Phase.PROPOSE, new BeforePropose(agent));

    assert events.size() == Phase.values().length;

    return events;
  }

  /**
   * Create a map of immutable "after phase" events for the given agent
   *
   * @param agent The agent
   * @return Map from phase to "after phase" event objects
   */
  public static Map<Phase, AbstractPhaseEvent> createAfterEvents(Agent agent) {
    EnumMap<Phase, AbstractPhaseEvent> events = new EnumMap<Phase, AbstractPhaseEvent>(Phase.class);
    events.put(Phase.APPLY, new AfterApply(agent));
    events.put(Phase.DECISION, new AfterDecision(agent));
    events.put(Phase.INPUT, new AfterInput(agent));
    events.put(Phase.OUTPUT, new AfterOutput(agent));
    events.put(Phase.PROPOSE, new AfterPropose(agent));

    assert events.size() == Phase.values().length;

    return events;
  }

  public static class BeforeInput extends AbstractPhaseEvent {
    public BeforeInput(Agent agent) {
      super(agent, Phase.INPUT, true);
    }
  }

  public static class AfterInput extends AbstractPhaseEvent {
    public AfterInput(Agent agent) {
      super(agent, Phase.INPUT, false);
    }
  }

  public static class BeforeOutput extends AbstractPhaseEvent {
    public BeforeOutput(Agent agent) {
      super(agent, Phase.OUTPUT, true);
    }
  }

  public static class AfterOutput extends AbstractPhaseEvent {
    public AfterOutput(Agent agent) {
      super(agent, Phase.OUTPUT, false);
    }
  }

  public static class BeforeApply extends AbstractPhaseEvent {
    public BeforeApply(Agent agent) {
      super(agent, Phase.APPLY, true);
    }
  }

  public static class AfterApply extends AbstractPhaseEvent {
    public AfterApply(Agent agent) {
      super(agent, Phase.APPLY, false);
    }
  }

  public static class BeforePropose extends AbstractPhaseEvent {
    public BeforePropose(Agent agent) {
      super(agent, Phase.PROPOSE, true);
    }
  }

  public static class AfterPropose extends AbstractPhaseEvent {
    public AfterPropose(Agent agent) {
      super(agent, Phase.PROPOSE, false);
    }
  }
  /**
   * callback.h::BEFORE_DECISION_PHASE_CALLBACK
   *
   * @author ray
   */
  public static class BeforeDecision extends AbstractPhaseEvent {
    public BeforeDecision(Agent agent) {
      super(agent, Phase.INPUT, true);
    }
  }

  public static class AfterDecision extends AbstractPhaseEvent {
    public AfterDecision(Agent agent) {
      super(agent, Phase.INPUT, false);
    }
  }
}
