package com.camunda.consulting.simulator.jobhandler;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.complete;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.historyService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.taskService;
import static org.junit.Assert.assertEquals;

import java.util.List;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.task;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
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

@Deployment(resources = "userTaskClaimModel.bpmn")
public class ClaimUserTaskJobHandlerTest {

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
    Mocks.register("generator", new PayloadGenerator());
  }

  @Test
  public void shouldExecuteClaimTaskJob() {

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("userTaskClaimComplete");
    assertThat(processInstance).isStarted().isWaitingAt("Task_1");
    complete(task());
    assertThat(processInstance).isWaitingAt("Task_2");

    SimulationExecutor.execute(DateTime.now().minusMinutes(5).toDate(), DateTime.now().plusMinutes(5).toDate());
    
    List<Task> tasks = taskService().createTaskQuery().taskAssignee("felix").list();
    assertEquals(tasks.size(), 1);
  }
  
  @Test
  public void shouldExecuteClaimAndCompleteTaskJob() {
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("userTaskClaimComplete");
    assertThat(processInstance).isStarted().isWaitingAt("Task_1");
    complete(task());
    assertThat(processInstance).isWaitingAt("Task_2");
    complete(task());
    
    SimulationExecutor.execute(DateTime.now().minusMinutes(5).toDate(), DateTime.now().plusMinutes(5).toDate());

    assertThat(processInstance).isEnded();
    
    List<UserOperationLogEntry> claimEntries = historyService().createUserOperationLogQuery().operationType("Claim").property("assignee").orderByTimestamp().desc().list();
    
    // two claims
    assertEquals(2, claimEntries.size());
    // our second assign to jim
    assertEquals("jim", claimEntries.get(0).getNewValue());
  }

}