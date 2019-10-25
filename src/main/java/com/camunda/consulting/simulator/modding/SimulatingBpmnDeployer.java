package com.camunda.consulting.simulator.modding;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.camunda.bpm.engine.impl.bpmn.deployer.BpmnDeployer;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParser;
import org.camunda.bpm.engine.impl.cfg.DefaultBpmnParseFactory;
import org.camunda.bpm.engine.impl.cmd.DeleteJobsCmd;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.el.StartProcessVariableScope;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TimerEntity;
import org.camunda.bpm.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.jobhandler.StartProcessInstanceJobHandler;

public class SimulatingBpmnDeployer extends BpmnDeployer {
  private static final Logger LOG = LoggerFactory.getLogger(SimulatingBpmnDeployer.class);

  public SimulatingBpmnDeployer(BpmnDeployer original) {
    this.bpmnParser = new BpmnParser(original.getBpmnParser().getExpressionManager(), new DefaultBpmnParseFactory());
    this.bpmnParser.getParseListeners().addAll(original.getBpmnParser().getParseListeners());
    this.bpmnParser.getParseListeners().add(new SimulationParseListener());
    this.expressionManager = original.getExpressionManager();
    this.idGenerator = original.getIdGenerator();
  }

  // make it public to call it from SimulationExecutor
  @Override
  public void adjustStartEventSubscriptions(ProcessDefinitionEntity newLatestProcessDefinition, ProcessDefinitionEntity oldLatestProcessDefinition) {
    super.adjustStartEventSubscriptions(newLatestProcessDefinition, oldLatestProcessDefinition);

    LOG.debug("Adding start event simulation timers.");
    removeObsoleteStartEventSimulationJobs(newLatestProcessDefinition);
    addStartEventSimulationJobs(newLatestProcessDefinition);
  }

  @SuppressWarnings("unchecked")
  protected void addStartEventSimulationJobs(ProcessDefinitionEntity processDefinition) {
    Map<String, String> map = (Map<String, String>) processDefinition.getProperty(SimulationParseListener.PROPERTYNAME_SIMULATE_START_EVENT);
    if (map == null) {
      // no start events to simulate
      return;
    }
    for (Entry<String, String> entry : map.entrySet()) {
      String activityId = entry.getKey();
      String simNextFire = entry.getValue();

      Date duedate = (Date) expressionManager.createExpression(simNextFire).getValue(StartProcessVariableScope.getSharedInstance());

      TimerEntity timer = new TimerEntity();

      timer.setDeploymentId(processDefinition.getDeploymentId());
      timer.setProcessDefinitionId(processDefinition.getId());
      timer.setProcessDefinitionKey(processDefinition.getKey());
      timer.setTenantId(processDefinition.getTenantId());
      timer.setDuedate(duedate);

      timer.setJobHandlerType(StartProcessInstanceJobHandler.TYPE);
      timer.setJobHandlerConfigurationRaw(activityId);

      LOG.debug("Create start process instance job for: " + processDefinition.getKey());
      Context.getCommandContext().getJobManager().schedule(timer);
    }
  }

  protected void removeObsoleteStartEventSimulationJobs(ProcessDefinitionEntity processDefinition) {
    List<Job> jobsToDelete = getJobManager().findJobsByHandlerType(StartProcessInstanceJobHandler.TYPE);
    for (Iterator<Job> iterator = jobsToDelete.iterator(); iterator.hasNext(); ) {
      Job job = (Job) iterator.next();
      if (!job.getProcessDefinitionKey().equals(processDefinition.getKey())) {
        iterator.remove();
      }
    }

    for (Job job : jobsToDelete) {
      LOG.debug("Delete start process instance job for: " + processDefinition.getKey());
      new DeleteJobsCmd(job.getId()).execute(Context.getCommandContext());
    }
  }
}
