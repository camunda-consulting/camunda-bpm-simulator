package com.camunda.consulting.simulator.listener;

import java.util.Date;
import java.util.Optional;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

import com.camunda.consulting.simulator.jobhandler.ClaimUserTaskJobHandler;

public class UserTaskClaimJobCreateListener extends AbstractTimerJobCreator implements TaskListener {

  private static UserTaskClaimJobCreateListener INSTANCE = null;

  public static TaskListener instance() {
    if (INSTANCE == null) {
      INSTANCE = new UserTaskClaimJobCreateListener();
    }
    return INSTANCE;
  }

  @Override
  public void notify(DelegateTask task) {
    Optional<Expression> nextClaimExpression = getCachedNextClaimExpression(task.getExecution(), task.getTaskDefinitionKey());
    Optional<Expression> claimUserExpression = getCachedClaimUserExpression(task.getExecution(), task.getTaskDefinitionKey());
    
    if (nextClaimExpression.isPresent() && claimUserExpression.isPresent()) {
      Date dueDate = (Date) nextClaimExpression.get().getValue(task.getExecution());
      String userId = (String) claimUserExpression.get().getValue(task.getExecution());
      createJobForUserTaskClaim(task, userId, dueDate);
    }
  }

  private void createJobForUserTaskClaim(DelegateTask task, String userId, Date dueDate) {

    String jobHandlertype = ClaimUserTaskJobHandler.TYPE;
    ClaimUserTaskJobHandler.ClaimUserTaskJobHandlerConfiguration jobHandlerConfiguration = new ClaimUserTaskJobHandler.ClaimUserTaskJobHandlerConfiguration(
        task.getId(), userId);

    createTimerJob((ExecutionEntity) task.getExecution(), jobHandlertype, dueDate, jobHandlerConfiguration);

  }

}
