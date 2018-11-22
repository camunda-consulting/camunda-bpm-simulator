package com.camunda.consulting.simulator.listener;

import java.util.Date;
import java.util.Optional;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

import com.camunda.consulting.simulator.jobhandler.CompleteExternalTaskJobHandler;

public class ExternalTaskJobCreateListener extends AbstractTimerJobCreator implements ExecutionListener {

  private static ExternalTaskJobCreateListener INSTANCE = null;

  public static ExternalTaskJobCreateListener instance() {
    if (INSTANCE == null) {
      INSTANCE = new ExternalTaskJobCreateListener();
    }
    return INSTANCE;
  }

  @Override
  public void notify(DelegateExecution execution) throws Exception {

    Optional<Expression> nextFireExpression = getCachedNextFireExpression(execution, execution.getCurrentActivityId());

    if (nextFireExpression.isPresent()) {
      Date dueDate = (Date) nextFireExpression.get().getValue(execution);
      createJobForExternalTaskCompletion(execution, dueDate);
    }

  }

  private void createJobForExternalTaskCompletion(DelegateExecution execution, Date dueDate) {

    String jobHandlertype = CompleteExternalTaskJobHandler.TYPE;
    CompleteExternalTaskJobHandler.CompleteExternalTaskJobHandlerConfiguration jobHandlerConfiguration = new CompleteExternalTaskJobHandler.CompleteExternalTaskJobHandlerConfiguration(
        execution.getId());

    createTimerJob((ExecutionEntity) execution, jobHandlertype, dueDate, jobHandlerConfiguration);

  }

}
