package com.camunda.consulting.simulator.jobhandler;

import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExternalTaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;

public class CompleteExternalTaskJobHandler implements JobHandler<CompleteExternalTaskJobHandler.CompleteExternalTaskJobHandlerConfiguration> {

  public static final String TYPE = "simulateCompleteExternalTask";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public void execute(CompleteExternalTaskJobHandlerConfiguration configuration, ExecutionEntity execution, CommandContext commandContext, String tenantId) {

    String executionId = configuration.getExecutionId();

    List<ExternalTaskEntity> externalTasks = commandContext.getExternalTaskManager().findExternalTasksByExecutionId(executionId);

    if (externalTasks.size() > 1) {
      throw new ProcessEngineException("More than one External Task was found for this execution");
    }

    externalTasks.stream().findFirst().ifPresent(task -> {
      task.lock("camunda-bpm-simulator", 10000);
      // TODO: Create another job that completes after a certain amount of time
      task.complete(Collections.emptyMap(), Collections.emptyMap());
    });

  }

  @Override
  public CompleteExternalTaskJobHandlerConfiguration newConfiguration(String canonicalString) {
    return new CompleteExternalTaskJobHandlerConfiguration(canonicalString);
  }

  @Override
  public void onDelete(CompleteExternalTaskJobHandlerConfiguration configuration, JobEntity jobEntity) {
    // do nothing
  }

  public static class CompleteExternalTaskJobHandlerConfiguration implements JobHandlerConfiguration {

    private final String executionId;

    public CompleteExternalTaskJobHandlerConfiguration(String executionId) {
      this.executionId = executionId;
    }

    String getExecutionId() {
      return executionId;
    }

    @Override
    public String toCanonicalString() {
      return executionId;
    }
  }

}
