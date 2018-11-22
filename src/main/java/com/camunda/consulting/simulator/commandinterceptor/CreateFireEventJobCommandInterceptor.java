package com.camunda.consulting.simulator.commandinterceptor;

import java.util.Date;
import java.util.Optional;

import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.CachedDbEntity;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityCache;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityState;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.jobhandler.FireEventJobHandler;
import com.camunda.consulting.simulator.listener.AbstractTimerJobCreator;

public class CreateFireEventJobCommandInterceptor extends CommandInterceptor {
  static final Logger LOG = LoggerFactory.getLogger(CreateFireEventJobCommandInterceptor.class);

  FireEventJobCreator fireEventJobCreator = new FireEventJobCreator();

  @Override
  public <T> T execute(Command<T> command) {
    // execute command first
    final T result = next.execute(command);

    // after that check all new EventSubscriptions for timer expressions
    DbEntityCache cache = Context.getCommandContext().getDbEntityManager().getDbEntityCache();
    for (EventSubscriptionEntity subscriptionEntity : cache.getEntitiesByType(EventSubscriptionEntity.class)) {
      CachedDbEntity cachedEntity = cache.getCachedEntity(subscriptionEntity);
      if (DbEntityState.TRANSIENT.equals(cachedEntity.getEntityState())) {
        fireEventJobCreator.checkTimerExpressionAndCreateJob(subscriptionEntity);
      }
    }

    return result;
  }

  class FireEventJobCreator extends AbstractTimerJobCreator {
    public void checkTimerExpressionAndCreateJob(EventSubscriptionEntity eventSubscription) {
      // we only want to have timers for events an execution is waiting for = not start events
      if (eventSubscription.getExecution() == null) {
        return;
      }
      LOG.debug("creating job for " + eventSubscription.getActivityId());
      
      ExecutionEntity execution = eventSubscription.getExecution();
      ActivityImpl activity = eventSubscription.getActivity();

      Optional<Expression> nextFireExpression = getCachedNextFireExpression(execution, activity.getActivityId());

      // create timer job only if we have the time configured... makes sense
      if (nextFireExpression.isPresent()) {
        createTimerJob(execution, FireEventJobHandler.TYPE, (Date) nextFireExpression.get().getValue(execution),
            new FireEventJobHandler.FireEventJobHandlerConfiguration(eventSubscription.getEventType(), eventSubscription.getEventName()));
      }
    }
  };

}
