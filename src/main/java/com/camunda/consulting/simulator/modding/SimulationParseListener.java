package com.camunda.consulting.simulator.modding;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.engine.delegate.DelegateListener;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.IntermediateThrowNoneEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.TaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.helper.BpmnProperties;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.core.model.CoreModelElement;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.pvm.process.TransitionImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.camunda.bpm.engine.impl.variable.VariableDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.listener.ExternalTaskJobCreateListener;
import com.camunda.consulting.simulator.listener.PayloadGeneratorListener;
import com.camunda.consulting.simulator.listener.UserTaskClaimJobCreateListener;
import com.camunda.consulting.simulator.listener.UserTaskCompleteJobCreateListener;
import com.camunda.consulting.simulator.property.ModelPropertyUtil;

@SuppressWarnings("deprecation")
public class SimulationParseListener implements BpmnParseListener {

  private static final Logger LOG = LoggerFactory.getLogger(SimulationParseListener.class);
  public static final String PROPERTYNAME_SIMULATE_START_EVENT = "simulateStartEvent";
  public static final String PROPERTYNAME_KEEP_LISTENERS = "simKeepListeners";

  public static class NoOpActivityBehavior extends TaskActivityBehavior {
  }

  @Override
  public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
    checkKeepListeners(processElement, processDefinition);
    stripExecutionListeners(processDefinition);
  }

  @Override
  public void parseStartEvent(Element startEventElement, ScopeImpl scope, ActivityImpl startEventActivity) {
    checkKeepListeners(startEventElement, startEventActivity);
    addPayloadGeneratingListener(startEventActivity);

    if (scope instanceof ProcessDefinitionEntity) {
      // only create simulating timers for none, conditional, message and signal
      // start events
      final String activityType = startEventActivity.getProperties().get(BpmnProperties.TYPE);
      if (Arrays.asList(new String[]{ActivityTypes.START_EVENT, ActivityTypes.START_EVENT_CONDITIONAL, ActivityTypes.START_EVENT_MESSAGE,
          ActivityTypes.START_EVENT_SIGNAL}).contains(activityType)) {
        Optional<String> simNextFire = ModelPropertyUtil.getNextFire(startEventElement);
        if (simNextFire.isPresent()) {
          LOG.debug("Adding simulating start timer for start event " + startEventElement.attribute("id"));
          @SuppressWarnings("unchecked")
          Map<String, String> map = (Map<String, String>) scope.getProperty(PROPERTYNAME_SIMULATE_START_EVENT);
          if (map == null) {
            map = new HashMap<>();
            scope.setProperty(PROPERTYNAME_SIMULATE_START_EVENT, map);
          }
          map.put(startEventActivity.getActivityId(), simNextFire.get());
        }
      }
    }
  }

  @Override
  public void parseExclusiveGateway(Element exclusiveGwElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(exclusiveGwElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseInclusiveGateway(Element inclusiveGwElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(inclusiveGwElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseParallelGateway(Element parallelGwElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(parallelGwElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseScriptTask(Element scriptTaskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(scriptTaskElement, activity);
    addPayloadGeneratingListener(activity);

    replaceBehaviourByNoOp(scriptTaskElement, activity);
  }

  @Override
  public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(serviceTaskElement, activity);
    addPayloadGeneratingListener(activity);

    String type = serviceTaskElement.attributeNS(BpmnParse.CAMUNDA_BPMN_EXTENSIONS_NS, BpmnParse.TYPE);
    if ("external".equalsIgnoreCase(type)) {
      addExternalTaskCompleteJobCreatingListener(activity);
    } else {
      // strip behavior for everything but external task
      replaceBehaviourByNoOp(serviceTaskElement, activity);
    }
  }

  @Override
  public void parseBusinessRuleTask(Element businessRuleTaskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(businessRuleTaskElement, activity);
    addPayloadGeneratingListener(activity);

    // strip behavior for everything but DMN
    String decisionRef = businessRuleTaskElement.attributeNS(BpmnParse.CAMUNDA_BPMN_EXTENSIONS_NS, "decisionRef");
    if (decisionRef == null) {
      replaceBehaviourByNoOp(businessRuleTaskElement, activity);
    }
  }

  @Override
  public void parseTask(Element taskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(taskElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseManualTask(Element manualTaskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(manualTaskElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
    boolean keepListeners = checkKeepListeners(userTaskElement, activity);
    addPayloadGeneratingListener(activity);

    if (!keepListeners) {
      ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition().getTaskListeners().clear();
    }

    addUserTaskCompleteJobCreatingListener(activity);
    addUserTaskClaimJobCreatingListener(activity);

    // since claim and complete timers are attached to execution, the task has to be a scope to get rid of the timer
    // jobs when the task is leaved
    activity.setScope(true);
  }

  @Override
  public void parseEndEvent(Element endEventElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(endEventElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseBoundaryTimerEventDefinition(Element timerEventDefinition, boolean interrupting, ActivityImpl timerActivity) {
  }

  @Override
  public void parseBoundaryErrorEventDefinition(Element errorEventDefinition, boolean interrupting, ActivityImpl activity,
                                                ActivityImpl nestedErrorEventActivity) {
  }

  @Override
  public void parseSubProcess(Element subProcessElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(subProcessElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseCallActivity(Element callActivityElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(callActivityElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseProperty(Element propertyElement, VariableDeclaration variableDeclaration, ActivityImpl activity) {
  }

  @Override
  public void parseSequenceFlow(Element sequenceFlowElement, ScopeImpl scopeElement, TransitionImpl transition) {
    checkKeepListeners(sequenceFlowElement, transition);
  }

  @Override
  public void parseSendTask(Element sendTaskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(sendTaskElement, activity);
    addPayloadGeneratingListener(activity);
    replaceBehaviourByNoOp(sendTaskElement, activity);
  }

  @Override
  public void parseMultiInstanceLoopCharacteristics(Element activityElement, Element multiInstanceLoopCharacteristicsElement, ActivityImpl activity) {
  }

  @Override
  public void parseIntermediateTimerEventDefinition(Element timerEventDefinition, ActivityImpl timerActivity) {
  }

  @Override
  public void parseRootElement(Element rootElement, List<ProcessDefinitionEntity> processDefinitions) {
  }

  @Override
  public void parseReceiveTask(Element receiveTaskElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(receiveTaskElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseIntermediateSignalCatchEventDefinition(Element signalEventDefinition, ActivityImpl signalActivity) {
  }

  @Override
  public void parseIntermediateMessageCatchEventDefinition(Element messageEventDefinition, ActivityImpl nestedActivity) {
  }

  @Override
  public void parseBoundarySignalEventDefinition(Element signalEventDefinition, boolean interrupting, ActivityImpl signalActivity) {
  }

  @Override
  public void parseEventBasedGateway(Element eventBasedGwElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(eventBasedGwElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseTransaction(Element transactionElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(transactionElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseCompensateEventDefinition(Element compensateEventDefinition, ActivityImpl compensationActivity) {
  }

  @Override
  public void parseIntermediateThrowEvent(Element intermediateEventElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(intermediateEventElement, activity);
    addPayloadGeneratingListener(activity);

    // strip behavior only for message throw events
    Element messageEventDefinitionElement = intermediateEventElement.element(BpmnParse.MESSAGE_EVENT_DEFINITION);
    if (messageEventDefinitionElement != null) {
      activity.setActivityBehavior(new IntermediateThrowNoneEventActivityBehavior());
    }
  }

  @Override
  public void parseIntermediateCatchEvent(Element intermediateEventElement, ScopeImpl scope, ActivityImpl activity) {
    checkKeepListeners(intermediateEventElement, activity);
    addPayloadGeneratingListener(activity);
  }

  @Override
  public void parseBoundaryEvent(Element boundaryEventElement, ScopeImpl scopeElement, ActivityImpl nestedActivity) {
    checkKeepListeners(boundaryEventElement, nestedActivity);
    addPayloadGeneratingListener(nestedActivity);
  }

  @Override
  public void parseBoundaryMessageEventDefinition(Element element, boolean interrupting, ActivityImpl messageActivity) {

  }

  @Override
  public void parseBoundaryEscalationEventDefinition(Element escalationEventDefinition, boolean interrupting, ActivityImpl boundaryEventActivity) {
  }

  @Override
  public void parseBoundaryConditionalEventDefinition(Element element, boolean interrupting, ActivityImpl conditionalActivity) {
  }

  @Override
  public void parseIntermediateConditionalEventDefinition(Element conditionalEventDefinition, ActivityImpl conditionalActivity) {
  }

  @Override
  public void parseConditionalStartEventForEventSubprocess(Element element, ActivityImpl conditionalActivity, boolean interrupting) {
    checkKeepListeners(element, conditionalActivity);
    addPayloadGeneratingListener(conditionalActivity);
  }

  private void addPayloadGeneratingListener(ActivityImpl activity) {
    activity.addBuiltInListener(ExecutionListener.EVENTNAME_END, PayloadGeneratorListener.instance());
  }

  private void addExternalTaskCompleteJobCreatingListener(ScopeImpl scope) {
    scope.addBuiltInListener(ExecutionListener.EVENTNAME_START, ExternalTaskJobCreateListener.instance());
  }

  private void addUserTaskCompleteJobCreatingListener(ActivityImpl activity) {

    ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition()
        .addTaskListener(TaskListener.EVENTNAME_CREATE, UserTaskCompleteJobCreateListener.instance());

  }

  private void addUserTaskClaimJobCreatingListener(ActivityImpl activity) {

    ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition()
        .addTaskListener(TaskListener.EVENTNAME_CREATE, UserTaskClaimJobCreateListener.instance());

  }

  private void replaceBehaviourByNoOp(Element element, ActivityImpl activity) {
    // we only replace the behavior if simCallRealImplementation is not
    // explicitly set to true
    Optional<String> property = ModelPropertyUtil.readCamundaProperty(element, ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_CALL_REAL_IMPLEMENTATION);
    if (!ModelPropertyUtil.isTrue(property)) {
      activity.setActivityBehavior(new NoOpActivityBehavior());
    }
  }

  private boolean checkKeepListeners(Element processElement, CoreModelElement processDefinition) {
    Optional<String> property = ModelPropertyUtil.readCamundaProperty(processElement, ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_KEEP_LISTENERS);
    if (ModelPropertyUtil.isTrue(property)) {
      processDefinition.setProperty(PROPERTYNAME_KEEP_LISTENERS, true);
      return true;
    }
    return false;
  }

  private void stripExecutionListeners(ProcessDefinitionEntity processDefinitionEntity) {

    processDefinitionEntity.getActivities().forEach(this::stripExecutionListeners);

    stripNonBuiltinListeners(processDefinitionEntity);

  }

  private void stripExecutionListeners(ActivityImpl activity) {
    stripNonBuiltinListeners(activity);

    List<ActivityImpl> children = activity.getActivities();

    // stop recursion
    if (children.isEmpty()) {
      return;
    }

    // recurse
    children.stream().forEach(this::stripExecutionListeners);

  }

  private void stripNonBuiltinListeners(CoreModelElement element) {
    // only strip listeners if not explicitly wanted to be kept by
    // simKeepListeners
    final Object keepListeners = element.getProperty(PROPERTYNAME_KEEP_LISTENERS);
    if (keepListeners == null || !keepListeners.equals(true)) {
      element.getListeners().keySet().forEach(eventType -> {
        for (Iterator<DelegateListener<?>> i = element.getListeners(eventType).iterator(); i.hasNext(); ) {
          DelegateListener<?> currentListener = i.next();
          if (!element.getBuiltInListeners(eventType).contains(currentListener)) {
            i.remove();
          }
        }
      });
    }
  }

}
