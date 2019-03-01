package com.camunda.consulting.simulator.listener;

import java.util.Date;
import java.util.Optional;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TimerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.property.CachedPropertyUtil;
import com.camunda.consulting.simulator.property.ModelPropertyUtil;

public class AbstractTimerJobCreator {
    static final Logger LOG = LoggerFactory.getLogger(AbstractTimerJobCreator.class);

    public AbstractTimerJobCreator() {
	super();
    }

    protected Optional<Expression> getCachedNextCompleteExpression(DelegateExecution execution, String activityId) {
	return CachedPropertyUtil.getExpression(execution, activityId,
		ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_NEXT_COMPLETE);
    }

    protected Optional<Expression> getCachedNextClaimExpression(DelegateExecution execution, String activityId) {
	return CachedPropertyUtil.getExpression(execution, activityId,
		ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_NEXT_CLAIM);
    }

    protected Optional<Expression> getCachedClaimUserExpression(DelegateExecution execution, String activityId) {
	return CachedPropertyUtil.getExpression(execution, activityId,
		ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_CLAIM_USER);
    }

    protected Optional<Expression> getCachedNextFireExpression(DelegateExecution execution, String activityId) {
	return CachedPropertyUtil.getExpression(execution, activityId,
		ModelPropertyUtil.CAMUNDA_PROPERTY_SIM_NEXT_FIRE);
    }

    protected void createTimerJob(ExecutionEntity execution, String jobHandlertype, Date duedate,
	    JobHandlerConfiguration jobHandlerConfiguration) {
	TimerEntity timer = new TimerEntity();
	ProcessDefinitionEntity processDefinition = execution.getProcessDefinition();

	timer.setExecution(execution);
	timer.setDuedate(duedate);
	timer.setJobHandlerType(jobHandlertype);
	timer.setProcessDefinitionKey(processDefinition.getKey());
	timer.setDeploymentId(processDefinition.getDeploymentId());
	timer.setJobHandlerConfiguration(jobHandlerConfiguration);

	Context.getCommandContext().getJobManager().schedule(timer);
    }
}