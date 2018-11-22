package com.camunda.consulting.simulator.listener;

import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.property.ModelPropertyUtil;
import com.camunda.consulting.simulator.property.Work;

public class PayloadGeneratorListener extends AbstractTimerJobCreator implements ExecutionListener {
  public static final Logger LOG = LoggerFactory.getLogger(PayloadGeneratorListener.class);

  private static PayloadGeneratorListener INSTANCE = null;
  public static Map<BaseElement, Work[]> generatePayloadPropertyCache = new HashMap<>();

  public static PayloadGeneratorListener instance() {
    if (INSTANCE == null) {
      INSTANCE = new PayloadGeneratorListener();
    }
    return INSTANCE;
  }

  @Override
  public void notify(DelegateExecution execution) throws Exception {
    Work[] workList = ModelPropertyUtil.getPayloadValuesOrdered(execution.getBpmnModelElementInstance(), ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_GENERATE_PAYLOAD);

    for (Work work : workList) {

      LOG.debug("Setting variable with name evaluated from '{}' to value evaluated from '{}'", work.getVariableExpression(), work.getValueExpression());

      String variableName = work.getVariableExpression().getValue(execution).toString().trim();
      Object value = work.getValueExpression().getValue(execution);

      LOG.debug("Setting variable '{}' to '{}'", variableName, value);

      execution.setVariable(variableName, value);
    }
  }
}
