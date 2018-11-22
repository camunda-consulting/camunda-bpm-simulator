package com.camunda.consulting.simulator.jobhandler;

import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;

public class CompleteUserTaskJobHandler implements JobHandler<CompleteUserTaskJobHandler.CompleteUserTaskJobHandlerConfiguration> {

  public static final String TYPE = "simulateCompleteUserTask";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public void execute(CompleteUserTaskJobHandlerConfiguration configuration, ExecutionEntity execution, CommandContext commandContext, String tenantId) {
    String taskId = configuration.getTaskId();
    execution.getProcessEngineServices().getTaskService().complete(taskId);
  }

  @Override
  public CompleteUserTaskJobHandlerConfiguration newConfiguration(String canonicalString) {
    return new CompleteUserTaskJobHandlerConfiguration(canonicalString);
  }

  @Override
  public void onDelete(CompleteUserTaskJobHandlerConfiguration configuration, JobEntity jobEntity) {
    // do nothing
  }

  public static class CompleteUserTaskJobHandlerConfiguration implements JobHandlerConfiguration {

    private final String taskId;

    public CompleteUserTaskJobHandlerConfiguration(String taskId) {
      this.taskId = taskId;
    }

    String getTaskId() {
      return taskId;
    }

    @Override
    public String toCanonicalString() {
      return taskId;
    }
  }

}
