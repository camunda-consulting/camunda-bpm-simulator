package com.camunda.consulting.simulator.jobhandler;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.TimerEventJobHandler;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;

public class ClaimUserTaskJobHandler implements JobHandler<ClaimUserTaskJobHandler.ClaimUserTaskJobHandlerConfiguration> {

  public static final String TYPE = "simulateClaimUserTask";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public void execute(ClaimUserTaskJobHandlerConfiguration configuration, ExecutionEntity execution, CommandContext commandContext, String tenantId) {
    String taskId = configuration.getTaskId();
    String userId = configuration.getUserId();
    // eventually we created a claim job, but before it gets executed the usertask is completed
    if (execution.getProcessEngineServices().getTaskService().createTaskQuery().taskId(taskId).list().size() > 0) {
	execution.getProcessEngineServices().getTaskService().claim(taskId, userId);	
    }
  }

  @Override
  public ClaimUserTaskJobHandlerConfiguration newConfiguration(String canonicalString) {
      String[] configParts = canonicalString.split("\\" + TimerEventJobHandler.JOB_HANDLER_CONFIG_PROPERTY_DELIMITER);
      if (configParts.length != 2) {
        throw new ProcessEngineException("Illegal simulator claim user task job handler configuration: '" + canonicalString
            + "': expecting two part configuration seperated by '" + TimerEventJobHandler.JOB_HANDLER_CONFIG_PROPERTY_DELIMITER + "'.");
      }
      
      return new ClaimUserTaskJobHandlerConfiguration(configParts[0], configParts[1]);
  }

  @Override
  public void onDelete(ClaimUserTaskJobHandlerConfiguration configuration, JobEntity jobEntity) {
    // do nothing
  }

  public static class ClaimUserTaskJobHandlerConfiguration implements JobHandlerConfiguration {

    private final String taskId;
    private final String userId;

    public ClaimUserTaskJobHandlerConfiguration(String taskId, String userId) {
      this.taskId = taskId;
      this.userId = userId;
    }
    
    String getUserId() {
	return userId;
    }

    String getTaskId() {
      return taskId;
    }

    @Override
    public String toCanonicalString() {
      return taskId + TimerEventJobHandler.JOB_HANDLER_CONFIG_PROPERTY_DELIMITER + userId;
    }
  }

}
