package com.camunda.consulting.simulator.listener;

import java.util.Date;
import java.util.Optional;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

import com.camunda.consulting.simulator.jobhandler.CompleteUserTaskJobHandler;

public class UserTaskCompleteJobCreateListener extends AbstractTimerJobCreator implements TaskListener {

  private static UserTaskCompleteJobCreateListener INSTANCE = null;

  public static TaskListener instance() {
    if (INSTANCE == null) {
      INSTANCE = new UserTaskCompleteJobCreateListener();
    }
    return INSTANCE;
  }

  @Override
  public void notify(DelegateTask task) {

    Optional<Expression> nextCompleteExpression = getCachedNextCompleteExpression(task.getExecution(), task.getTaskDefinitionKey());

    // backwards compatibility (user task next fire)
    if (!nextCompleteExpression.isPresent()) {
	nextCompleteExpression = getCachedNextFireExpression(task.getExecution(), task.getTaskDefinitionKey());
    }
    
    if (nextCompleteExpression.isPresent()) {
      Date dueDate = (Date) nextCompleteExpression.get().getValue(task.getExecution());
      createJobForUserTaskCompletion(task, dueDate);
    }
  }

  private void createJobForUserTaskCompletion(DelegateTask task, Date dueDate) {

    String jobHandlertype = CompleteUserTaskJobHandler.TYPE;
    CompleteUserTaskJobHandler.CompleteUserTaskJobHandlerConfiguration jobHandlerConfiguration = new CompleteUserTaskJobHandler.CompleteUserTaskJobHandlerConfiguration(
        task.getId());

    createTimerJob((ExecutionEntity) task.getExecution(), jobHandlertype, dueDate, jobHandlerConfiguration);

  }

}
