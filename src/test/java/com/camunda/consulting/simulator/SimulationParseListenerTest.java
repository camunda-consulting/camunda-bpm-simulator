package com.camunda.consulting.simulator;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.complete;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.execute;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.job;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.task;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SimulationParseListenerTest {

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
  @Deployment(resources = "parseListenerTestModel.bpmn")
  public void shouldStripListeners() {

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("parseListenerTest");
    assertThat(processInstance).isStarted();
    complete(task());

    execute(job()); // multi-inst 1
    execute(job()); // multi-inst 2

    assertThat(processInstance).variables() //
        .doesNotContainKey("startEventExecutionStartListenerExecuted") //
        .doesNotContainKey("executionStartListenerExecuted") //
        .doesNotContainKey("executionEndListenerExecuted") //
        .doesNotContainKey("taskCreateListenerExecuted") //
        .doesNotContainKey("executionEndListenerOnBoundaryInMultiInstanceExecuted");
    assertThat(processInstance).variables() //
        .containsKey("implementationKept") //
        .containsKey("taskListenerKept") //
        .containsKey("endListenerKept");

    complete(task());

    assertThat(processInstance).isEnded();
  }

}
