package com.camunda.consulting.simulator;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.bpmn.deployer.BpmnDeployer;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.persistence.deploy.Deployer;
import org.camunda.bpm.model.bpmn.instance.BaseElement;

import com.camunda.consulting.simulator.commandinterceptor.CreateFireEventJobCommandInterceptor;
import com.camunda.consulting.simulator.jobhandler.CompleteExternalTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.CompleteUserTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.FireEventJobHandler;
import com.camunda.consulting.simulator.jobhandler.StartProcessInstanceJobHandler;
import com.camunda.consulting.simulator.modding.SimulatingBpmnDeployer;
import com.camunda.consulting.simulator.modding.SimulationParseListener;

public class SimulatorPlugin implements ProcessEnginePlugin {

  private static ProcessEngineConfigurationImpl processEngineConfiguration;
  private static ProcessEngine processEngine;
  private static SimulatingBpmnDeployer bpmnDeployer;

  public static ProcessEngine getProcessEngine() {
    return processEngine;
  }

  public static ProcessEngineConfigurationImpl getProcessEngineConfiguration() {
    return processEngineConfiguration;
  }

  public static SimulatingBpmnDeployer getBpmnDeployer() {
    return bpmnDeployer;
  }
  
  public static Object evaluateExpression(String expression, VariableScope scope) {
    return processEngineConfiguration.getExpressionManager().createExpression(expression).getValue(scope);
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    SimulatorPlugin.processEngineConfiguration = processEngineConfiguration;

    List<BpmnParseListener> parseListeners = processEngineConfiguration.getCustomPreBPMNParseListeners();
    if (parseListeners == null) {
      parseListeners = new ArrayList<>();
      processEngineConfiguration.setCustomPreBPMNParseListeners(parseListeners);
    }
    parseListeners.add(new SimulationParseListener());

    @SuppressWarnings("rawtypes")
    List<JobHandler> customJobHandlers = processEngineConfiguration.getCustomJobHandlers();
    if (customJobHandlers == null) {
      customJobHandlers = new ArrayList<>();
      processEngineConfiguration.setCustomJobHandlers(customJobHandlers);
    }
    customJobHandlers.add(new CompleteUserTaskJobHandler());
    customJobHandlers.add(new FireEventJobHandler());
    customJobHandlers.add(new CompleteExternalTaskJobHandler());
    customJobHandlers.add(new StartProcessInstanceJobHandler());

    List<CommandInterceptor> postCommandInterceptors = processEngineConfiguration.getCustomPostCommandInterceptorsTxRequired();
    if (postCommandInterceptors == null) {
      postCommandInterceptors = new ArrayList<>();
      processEngineConfiguration.setCustomPostCommandInterceptorsTxRequired(postCommandInterceptors);
    }
    postCommandInterceptors.add(new CreateFireEventJobCommandInterceptor());
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    List<Deployer> deployers = processEngineConfiguration.getDeployers();
    BpmnDeployer originalBpmnDeployer = null;
    int position = 0;
    for (Deployer deployer : deployers) {
      if (deployer instanceof BpmnDeployer) {
        originalBpmnDeployer = (BpmnDeployer) deployer;
        break;
      }
      position++;
    }
    if (originalBpmnDeployer == null) {
      throw new RuntimeException("No BpmnDeployer found.");
    }

    bpmnDeployer = new SimulatingBpmnDeployer(originalBpmnDeployer);
    deployers.set(position, bpmnDeployer);

    processEngineConfiguration.getDeploymentCache().setDeployers(deployers);
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    SimulatorPlugin.processEngine = processEngine;
  }

}
