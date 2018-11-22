package com.camunda.consulting.simulator.jobhandler;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.SignalEventReceivedBuilderImpl;
import org.camunda.bpm.engine.impl.cmd.MessageEventReceivedCmd;
import org.camunda.bpm.engine.impl.cmd.SignalEventReceivedCmd;
import org.camunda.bpm.engine.impl.event.EventType;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.TimerEventJobHandler;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FireEventJobHandler implements JobHandler<FireEventJobHandler.FireEventJobHandlerConfiguration> {
  private static final Logger LOG = LoggerFactory.getLogger(FireEventJobHandler.class);

  public static final String TYPE = "simulateFireEvent";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public void execute(FireEventJobHandlerConfiguration configuration, ExecutionEntity execution, CommandContext commandContext, String tenantId) {
    LOG.debug("Firing " + configuration.getEventType() + " : " + configuration.getEventName());
    if (EventType.MESSAGE.name().equals(configuration.getEventType())) {
      new MessageEventReceivedCmd(configuration.getEventName(), execution.getId(), null).execute(commandContext);
    }
    if (EventType.SIGNAL.name().equals(configuration.getEventType())) {
      new SignalEventReceivedCmd(
          (SignalEventReceivedBuilderImpl) new SignalEventReceivedBuilderImpl(null, configuration.getEventName()).executionId(execution.getId()))
              .execute(commandContext);
    }
  }

  @Override
  public FireEventJobHandlerConfiguration newConfiguration(String canonicalString) {
    String[] configParts = canonicalString.split("\\" + TimerEventJobHandler.JOB_HANDLER_CONFIG_PROPERTY_DELIMITER);
    if (configParts.length != 2) {
      throw new ProcessEngineException("Illegal simulator fire event job handler configuration: '" + canonicalString
          + "': expecting two part configuration seperated by '" + TimerEventJobHandler.JOB_HANDLER_CONFIG_PROPERTY_DELIMITER + "'.");
    }
    return new FireEventJobHandlerConfiguration(configParts[0], configParts[1]);
  }

  @Override
  public void onDelete(FireEventJobHandlerConfiguration configuration, JobEntity jobEntity) {
    // do nothing
  }

  public static class FireEventJobHandlerConfiguration implements JobHandlerConfiguration {

    final String eventName;
    final String eventType;

    public FireEventJobHandlerConfiguration(String eventType, String eventName) {
      this.eventName = eventName;
      this.eventType = eventType;
    }

    public String getEventType() {
      return eventType;
    }

    public String getEventName() {
      return eventName;
    }

    @Override
    public String toCanonicalString() {
      return eventType + TimerEventJobHandler.JOB_HANDLER_CONFIG_PROPERTY_DELIMITER + eventName;
    }
  }

}
