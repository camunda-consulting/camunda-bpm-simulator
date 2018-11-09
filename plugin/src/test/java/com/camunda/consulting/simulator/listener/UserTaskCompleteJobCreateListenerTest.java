package com.camunda.consulting.simulator.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.jobQuery;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.task;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.camunda.consulting.simulator.PayloadGenerator;
import com.camunda.consulting.simulator.TestHelper;

@Deployment(resources = "userTaskCompleteModel.bpmn")
public class UserTaskCompleteJobCreateListenerTest {

  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  @Before
  public void setup() {
    init(rule.getProcessEngine());
    TestHelper.removeCustomJobs(rule.getProcessEngine());
    Mocks.register("generator", new PayloadGenerator());
  }

  @Test
  public void shouldNotCreateJobIfNoExpressionExists() {

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("userTaskComplete");
    assertThat(processInstance).isStarted().isWaitingAt("Task_1");

    assertThat(jobQuery().count()).isEqualTo(0);
  }

  @Test
  public void shouldCreateCustomJob() {

    ProcessInstance processInstance = runtimeService().createProcessInstanceByKey("userTaskComplete").startAfterActivity("Task_1").execute();
    assertThat(processInstance).isStarted().isWaitingAt("Task_2");

    // Did we create a custom job?
    assertThat(jobQuery().count()).isEqualTo(1);

    // Does the job belong to our user tasK?
    String jobHandlerConfig = ((JobEntity) jobQuery().singleResult()).getJobHandlerConfigurationRaw();
    assertThat(jobHandlerConfig).isEqualTo(task().getId());

  }

}