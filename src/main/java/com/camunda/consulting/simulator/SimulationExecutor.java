package com.camunda.consulting.simulator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.ProcessDefinitionQueryImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulationExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(SimulationExecutor.class);

  // TODO make configurable
  public static final int METRIC_WRITE_INTERVAL_MINUTES = 15;

  private static double progress = 1;

  public static double getProgress() {
    return progress;
  }

  public static void execute(Date start, Date end) {

    ProcessEngineConfigurationImpl processEngineConfigurationImpl = SimulatorPlugin.getProcessEngineConfiguration();
    ProcessEngine processEngine = SimulatorPlugin.getProcessEngine();
    CommandExecutor commandExecutor = processEngineConfigurationImpl.getCommandExecutorTxRequired();

    runWithPreparedEngineConfiguration(processEngineConfigurationImpl, commandExecutor, () -> {

      ClockUtil.setCurrentTime(start);
      progress = 0;
      LocalDateTime lastMetricUpdate = null;

      updateStartTimersForCurrentTime(commandExecutor);

      Optional<Job> job;
      do {
        // execute all jobs that are due before current time
        do {
          // work around engine "bug"
          makeTimeGoBy();

          // by setting the processEngineConfigurationImpl.setJobExecutor*
          // properties we can be sure to get the next job with minimum due date
          List<JobEntity> jobs = commandExecutor.execute(new Command<List<JobEntity>>() {
            @Override
            public List<JobEntity> execute(CommandContext commandContext) {
              return commandContext.getJobManager().findNextJobsToExecute(new Page(0, 1));
            }
          });
          job = jobs.stream().map(jobEntity -> (Job) jobEntity).findFirst();
          job.map(Job::getId).ifPresent(processEngine.getManagementService()::executeJob);

          // write metrics from time to time
          final LocalDateTime simulationTime = ClockUtil.getCurrentTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
          if (lastMetricUpdate == null || lastMetricUpdate.plusMinutes(METRIC_WRITE_INTERVAL_MINUTES).isBefore(simulationTime)) {
            lastMetricUpdate = simulationTime;
            processEngineConfigurationImpl.getDbMetricsReporter().reportNow();
          }
        } while (job.isPresent() && (job.get().getDuedate() == null || !job.get().getDuedate().after(end)));

        // get the next job that is due after current time and adjust clock to
        // its due date
        job = processEngine.getManagementService().createJobQuery().active().withRetriesLeft().orderByJobDuedate().asc().listPage(0, 1).stream().findFirst();
        job.map(Job::getDuedate).ifPresent(ClockUtil::setCurrentTime);
        progress = Math.min(1, (ClockUtil.getCurrentTime().getTime() - start.getTime()) / (double) (end.getTime() - start.getTime()));

        LOG.debug("Advance simulation time to: " + ClockUtil.getCurrentTime());
      } while (job.isPresent() && (job.get().getDuedate() == null || !job.get().getDuedate().after(end)));
    });
  }

  private static void runWithPreparedEngineConfiguration(ProcessEngineConfigurationImpl processEngineConfigurationImpl, CommandExecutor commandExecutor,
      Runnable runnable) {

    boolean metrics = processEngineConfigurationImpl.isMetricsEnabled() && processEngineConfigurationImpl.isDbMetricsReporterActivate();
    boolean jobExecutorEnabled = processEngineConfigurationImpl.getJobExecutor().isActive();

    boolean jobExecutorAcquireByPriority = processEngineConfigurationImpl.isJobExecutorAcquireByPriority();
    boolean jobExecutorPreferTimerJobs = processEngineConfigurationImpl.isJobExecutorPreferTimerJobs();
    boolean jobExecutorAcquireByDueDate = processEngineConfigurationImpl.isJobExecutorAcquireByDueDate();

    if (jobExecutorEnabled) {
      processEngineConfigurationImpl.getJobExecutor().shutdown();
    }

    if (metrics) {
      processEngineConfigurationImpl.getDbMetricsReporter().setReporterId("DEMO-DATA-GENERATOR");
    }

    processEngineConfigurationImpl.setJobExecutorAcquireByPriority(false);
    processEngineConfigurationImpl.setJobExecutorPreferTimerJobs(false);
    processEngineConfigurationImpl.setJobExecutorAcquireByDueDate(true);

    try {
      runnable.run();
    } finally {
      ClockUtil.reset();
      progress = 1;
      
      updateStartTimersForCurrentTime(commandExecutor);

      if (metrics) {
        processEngineConfigurationImpl.getDbMetricsReporter().reportNow();

        processEngineConfigurationImpl.getDbMetricsReporter()
            .setReporterId(processEngineConfigurationImpl.getMetricsReporterIdProvider().provideId(processEngineConfigurationImpl.getProcessEngine()));
      }
      processEngineConfigurationImpl.setJobExecutorAcquireByPriority(jobExecutorAcquireByPriority);
      processEngineConfigurationImpl.setJobExecutorPreferTimerJobs(jobExecutorPreferTimerJobs);
      processEngineConfigurationImpl.setJobExecutorAcquireByDueDate(jobExecutorAcquireByDueDate);
      if (jobExecutorEnabled) {
        processEngineConfigurationImpl.getJobExecutor().start();
      }
    }
  }

  private static void updateStartTimersForCurrentTime(CommandExecutor commandExecutor) {
    commandExecutor.execute(new Command<Void>() {
      @Override
      public Void execute(CommandContext commandContext) {
        List<ProcessDefinition> list = new ProcessDefinitionQueryImpl(commandExecutor).executeList(commandContext, null);
        list.stream().map(ProcessDefinition::getKey).collect(Collectors.toSet()).forEach(key -> {
          ProcessDefinitionEntity definition = commandContext.getProcessEngineConfiguration().getDeploymentCache()
              .findDeployedLatestProcessDefinitionByKey(key);

          SimulatorPlugin.getSimulationBpmnDeployer().adjustStartEventSubscriptions(definition, definition);

        });
        return null;
      }
    });
  }

  private static void makeTimeGoBy() {
    /*
     * Caused by DurationHelper.getDateAfterRepeat: return next.before(date) ?
     * null : next;
     * 
     * This leads to endless loop if we call a timer job at exactly the time it
     * will schedule next. Cannot be handled by engine, because there is no
     * "counter" in the database for executions - it has to trust the clock on
     * the wall.
     * 
     * Hence, we solve that by advancing the time as it would happen in real
     * live systems...
     */
    Calendar cal = Calendar.getInstance();
    cal.setTime(ClockUtil.getCurrentTime());
    cal.add(Calendar.MILLISECOND, 1);
    ClockUtil.setCurrentTime(cal.getTime());
  }
}
