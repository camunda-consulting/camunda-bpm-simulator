package com.camunda.consulting.simulator;

import java.util.LinkedList;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cmd.DeleteJobsCmd;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.runtime.Job;

import com.camunda.consulting.simulator.jobhandler.CompleteExternalTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.CompleteUserTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.FireEventJobHandler;
import com.camunda.consulting.simulator.jobhandler.StartProcessInstanceJobHandler;

public class TestHelper {
  public static void removeCustomJobs(ProcessEngine processEngine) {
    ProcessEngineConfigurationImpl pec = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    CommandExecutor commandExecutor = pec.getCommandExecutorTxRequired();
    commandExecutor.execute(new Command<Void>() {

      @Override
      public Void execute(CommandContext commandContext) {
        List<Job> jobsToDelete = new LinkedList<>();
        jobsToDelete.addAll(commandContext.getJobManager().findJobsByHandlerType(CompleteExternalTaskJobHandler.TYPE));
        jobsToDelete.addAll(commandContext.getJobManager().findJobsByHandlerType(CompleteUserTaskJobHandler.TYPE));
        jobsToDelete.addAll(commandContext.getJobManager().findJobsByHandlerType(FireEventJobHandler.TYPE));
        jobsToDelete.addAll(commandContext.getJobManager().findJobsByHandlerType(StartProcessInstanceJobHandler.TYPE));
        for (Job job : jobsToDelete) {
          new DeleteJobsCmd(job.getId()).execute(commandContext);
        }
        return null;
      }
    });
  }
}
