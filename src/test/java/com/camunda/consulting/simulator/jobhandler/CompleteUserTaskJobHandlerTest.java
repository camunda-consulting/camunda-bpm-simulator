package com.camunda.consulting.simulator.jobhandler;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.complete;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.task;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.camunda.consulting.simulator.PayloadGenerator;
import com.camunda.consulting.simulator.SimulationExecutor;
import com.camunda.consulting.simulator.TestHelper;

@Deployment(resources = "userTaskCompleteModel.bpmn")
public class CompleteUserTaskJobHandlerTest {

  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  @Before
  public void setup() {
    if (BpmPlatform.getDefaultProcessEngine() == null) {
      RuntimeContainerDelegate.INSTANCE.get().registerProcessEngine(rule.getProcessEngine());
    }
    init(rule.getProcessEngine());
    TestHelper.removeCustomJobs(rule.getProcessEngine());
  }

  @Test
  public void shouldExecuteCompleteTaskJob() {

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("userTaskComplete");
    assertThat(processInstance).isStarted().isWaitingAt("Task_1");
    complete(task());
    assertThat(processInstance).isWaitingAt("Task_2");

    SimulationExecutor.execute(DateTime.now().minusMinutes(5).toDate(), DateTime.now().plusMinutes(5).toDate());

    assertThat(processInstance).isEnded();

  }

}