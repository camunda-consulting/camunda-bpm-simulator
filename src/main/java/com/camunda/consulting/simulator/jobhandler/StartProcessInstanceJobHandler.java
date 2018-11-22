package com.camunda.consulting.simulator.jobhandler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.el.StartProcessVariableScope;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;
import org.camunda.bpm.engine.impl.persistence.deploy.cache.DeploymentCache;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TimerEntity;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.SimulatorPlugin;
import com.camunda.consulting.simulator.property.ModelPropertyUtil;
import com.camunda.consulting.simulator.property.Work;

public class StartProcessInstanceJobHandler implements JobHandler<StartProcessInstanceJobHandler.StartProcessInstanceJobConfiguration> {
  private static final Logger LOG = LoggerFactory.getLogger(StartProcessInstanceJobHandler.class);

  public static final String TYPE = "simulateStartProcessInstance";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public void execute(StartProcessInstanceJobHandler.StartProcessInstanceJobConfiguration configuration, ExecutionEntity execution,
      CommandContext commandContext, String tenantId) {
    final StartProcessVariableScope scope = StartProcessVariableScope.getSharedInstance();
    TimerEntity currentJob = (TimerEntity) commandContext.getCurrentJob();
    DeploymentCache deploymentCache = Context.getProcessEngineConfiguration().getDeploymentCache();
    BpmnModelInstance modelInstance = deploymentCache.findBpmnModelInstanceForProcessDefinition(currentJob.getProcessDefinitionId());
    ModelElementInstance startEvent = modelInstance.getModelElementById(configuration.getStartEventId());

    reschedule(currentJob, startEvent);

    // prepare business key
    String businessKey = null;
    Optional<String> simInitBusinessKey = ModelPropertyUtil.readCamundaProperty((BaseElement) startEvent,
        ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_INIT_BUSINESS_KEY);
    if (simInitBusinessKey.isPresent()) {
      businessKey = SimulatorPlugin.evaluateExpression(simInitBusinessKey.get(), scope).toString();
    }

    // prepare variables
    Work[] workList = ModelPropertyUtil.getPayloadValuesOrdered((BaseElement) startEvent, ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_INIT_PAYLOAD);
    Map<String, Object> variables = new HashMap<>(workList.length);
    for (Work work : workList) {
      LOG.debug("Setting initial variable with name evaluated from '{}' to value evaluated from '{}'", work.getVariableExpression(), work.getValueExpression());

      String variableName = work.getVariableExpression().getValue(scope).toString().trim();
      Object value = work.getValueExpression().getValue(scope);

      LOG.debug("Setting initial variable '{}' to '{}'", variableName, value);

      variables.put(variableName, value);
    }

    LOG.debug("Start Instance: " + currentJob.getProcessDefinitionId() + " - " + configuration.getStartEventId());
    commandContext.getProcessEngineConfiguration().getRuntimeService() //
        .createProcessInstanceById(currentJob.getProcessDefinitionId()) //
        .businessKey(businessKey) //
        .setVariables(variables).startBeforeActivity(configuration.getStartEventId()) //
        .execute();
  }

  private void reschedule(TimerEntity currentJob, ModelElementInstance startEvent) {
    Optional<String> nextFire = ModelPropertyUtil.getNextFire(startEvent);
    if (!nextFire.isPresent()) {
      throw new RuntimeException("This job should only exist for start events with simulation property set");
    }
    Object duedate = SimulatorPlugin.evaluateExpression(nextFire.get(), StartProcessVariableScope.getSharedInstance());
    if (!(duedate instanceof Date)) {
      throw new RuntimeException("Next simulation expression does not evaluate to date: " + nextFire.get());
    }
    currentJob.createNewTimerJob((Date) duedate);
  }

  @Override
  public StartProcessInstanceJobConfiguration newConfiguration(String canonicalString) {
    return new StartProcessInstanceJobConfiguration(canonicalString);
  }

  @Override
  public void onDelete(StartProcessInstanceJobHandler.StartProcessInstanceJobConfiguration configuration, JobEntity jobEntity) {
    // nothing
  }

  public static class StartProcessInstanceJobConfiguration implements JobHandlerConfiguration {
    String startEventId;

    public StartProcessInstanceJobConfiguration(String startEventId) {
      this.startEventId = startEventId;
    }

    @Override
    public String toCanonicalString() {
      return startEventId;
    }

    public String getStartEventId() {
      return startEventId;
    }

  }

}
