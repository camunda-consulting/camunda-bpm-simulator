package com.camunda.consulting.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.complete;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.execute;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.historyService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.job;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.managementService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.task;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.impl.ManagementServiceImpl;
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
    init(rule.getProcessEngine());
    TestHelper.removeCustomJobs(rule.getProcessEngine());
    Mocks.register("generator", new PayloadGenerator());
  }

  @Test
  @Deployment(resources = "parseListenerTestModel.bpmn")
  public void shouldStripListeners() {

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("parseListenerTest");
    assertThat(processInstance).isStarted();
    complete(task());

    execute(job()); // multi-inst 1
    execute(job()); // multi-inst 2

    assertThat(processInstance).isEnded();

    // check for 4 because of multi-instance stuff
    // TODO: make the check better
    assertThat(historyService().createHistoricVariableInstanceQuery().count()).isEqualTo(4);

  }

}
