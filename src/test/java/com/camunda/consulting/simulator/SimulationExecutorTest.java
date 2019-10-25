package com.camunda.consulting.simulator;

import static org.assertj.core.condition.AnyOf.anyOf;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.init;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.historyService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;

import java.util.List;

import org.apache.ibatis.logging.LogFactory;
import org.assertj.core.api.Condition;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case starting an in-memory database-backed Process Engine.
 */
@Deployment(resources = "executorTestModel.bpmn")
public class SimulationExecutorTest {

  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
    // have it already there when engine is built
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
  public void testBasicCycleStart() {
    SimulationExecutor.execute(DateTime.now().plusHours(1).toDate(), DateTime.now().plusHours(2).toDate());

    long count = historyService().createHistoricProcessInstanceQuery().processDefinitionKey("oneStartPerMinute").completed().count();
    assertThat(count).isEqualTo(60);
  }

  @Test
  public void testHistoricSimulation() {
    SimulationExecutor.execute(DateTime.now().plusHours(-2).toDate(), DateTime.now().plusHours(-1).toDate());
    long count = historyService().createHistoricProcessInstanceQuery().processDefinitionKey("oneStartPerMinute").completed().count();
    assertThat(count).isEqualTo(60);
  }

  @Test
  @Deployment(resources="stopSimulationAfterRedeployment.bpmn")
  public void testSimulationStoppedAfterRedeployment() throws InterruptedException {
    SimulationExecutor.execute(DateTime.now().plusHours(-2).toDate(), DateTime.now().plusHours(-1).plusMinutes(1).toDate());
    long count = historyService().createHistoricProcessInstanceQuery().processDefinitionKey("redeployment").completed().count();
    assertThat(count).isEqualTo(60);
    //assert that after start of instance it's completed
    ProcessInstance pi = rule.getProcessEngine().getRuntimeService().startProcessInstanceByKey("redeployment", "A-123"); 
    
    SimulationExecutor.execute(DateTime.now().toDate(), DateTime.now().plusSeconds(10).toDate());
    count = historyService().createHistoricProcessInstanceQuery().processDefinitionKey("redeployment").completed().count();
    assertThat(count).isEqualTo(61);
    
    //redeployment, should not complete it afterwards
    SimulatorPlugin.resetProcessEngineElements();

    rule.getProcessEngine().getRepositoryService().createDeployment() 
        .addClasspathResource("stopSimulationAfterRedeployment.bpmn")
        .deploy();

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("redeployment", "A-234");

    ProcessEngineConfigurationImpl processEngineConfigurationImpl = (ProcessEngineConfigurationImpl) rule.getProcessEngine().getProcessEngineConfiguration();
    processEngineConfigurationImpl.getJobExecutor().start();
    Thread.sleep(1_000);
    processEngineConfigurationImpl.getJobExecutor().shutdown();

    assertThat(processInstance).isNotEnded();
    
    // re-activate again
    SimulatorPlugin.setProcessEngineElements();
  }
  
  @Test
  @Deployment(resources = "simulateStartTestModel.bpmn")
  public void testSimulateStartEventWithBusinessKeyAndPayload() {
    SimulationExecutor.execute(DateTime.now().plusHours(-1).toDate(), DateTime.now().toDate());
    long count = historyService().createHistoricProcessInstanceQuery().processDefinitionKey("oneStartPerMinute").completed().count();
    List<HistoricProcessInstance> list = historyService().createHistoricProcessInstanceQuery().processDefinitionKey("oneStartPerMinute").completed().list();
    for (HistoricProcessInstance historicProcessInstance : list) {
      assertThat(historicProcessInstance.getBusinessKey()).is(anyOf(new StartsWithCondition("none"), new StartsWithCondition("message")));
    }
    long countFirst = 0;
    long countSecond = 0;
    long countFourth = 0;
    long countThird = 0;
    List<HistoricVariableInstance> list2 = historyService().createHistoricVariableInstanceQuery().processDefinitionKey("oneStartPerMinute").list();
    for (HistoricVariableInstance historicVariableInstance : list2) {
      if (historicVariableInstance.getName().equals("first")) {
        countFirst++;
        assertThat(historicVariableInstance.getValue()).isEqualTo((int) 1);
      }
      if (historicVariableInstance.getName().equals("second")) {
        countSecond++;
        assertThat(historicVariableInstance.getValue()).isEqualTo("2");
      }
      if (historicVariableInstance.getName().equals("fourth")) {
        countFourth++;
        assertThat(historicVariableInstance.getValue()).isEqualTo("4");
      }
      if (historicVariableInstance.getName().equals("third")) {
        countThird++;
        assertThat(historicVariableInstance.getValue()).isEqualTo("message");
      }
    }
    // from none start event
    assertThat(countFirst).isEqualTo(59);
    assertThat(countSecond).isEqualTo(59);
    // to test simGeneratePayload on start event
    assertThat(countFourth).isEqualTo(59);

    // from message start event
    assertThat(countThird).isEqualTo(29);

    // both
    assertThat(count).isEqualTo(88);
  }

  public class StartsWithCondition extends Condition<String> {

    String prefix;

    public StartsWithCondition(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public boolean matches(String value) {
      return value.startsWith(prefix);
    }

  }
}
