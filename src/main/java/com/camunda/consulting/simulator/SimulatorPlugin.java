package com.camunda.consulting.simulator;

import java.beans.FeatureDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.bpmn.deployer.BpmnDeployer;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.el.ExpressionManager;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.javax.el.CompositeELResolver;
import org.camunda.bpm.engine.impl.javax.el.ELContext;
import org.camunda.bpm.engine.impl.javax.el.ELResolver;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.persistence.deploy.Deployer;

import com.camunda.consulting.simulator.commandinterceptor.CreateFireEventJobCommandInterceptor;
import com.camunda.consulting.simulator.jobhandler.ClaimUserTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.CompleteExternalTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.CompleteUserTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.FireEventJobHandler;
import com.camunda.consulting.simulator.jobhandler.StartProcessInstanceJobHandler;
import com.camunda.consulting.simulator.modding.SimulatingBpmnDeployer;
import com.camunda.consulting.simulator.modding.SimulationParseListener;

public class SimulatorPlugin implements ProcessEnginePlugin {

  public static final String PAYLOAD_GENERATOR_BEAN_NAME = "g";

  private static boolean processEngineIsModified = false;

  private static Object payloadGenerator;

  private static List<JobHandler> originalCustomJobHandlers;
  private static List<JobHandler> simulationCustomJobHandlers;
  private static List<CommandInterceptor> originalPostCommandInterceptors;
  private static List<CommandInterceptor> simulationPostCommandInterceptors;
  private static List<Deployer> originalDeployers;
  private static List<Deployer> simulationDeployers;

  static {
    setPayloadGenerator(new PayloadGenerator());
  }

  private static SimulatingBpmnDeployer simulatingBpmnDeployer;

  public static void setPayloadGenerator(Object payloadGenerator) {
    SimulatorPlugin.payloadGenerator = payloadGenerator;
  }

  public static ProcessEngineConfigurationImpl getProcessEngineConfiguration() {
    return (ProcessEngineConfigurationImpl) getProcessEngine().getProcessEngineConfiguration();
  }

  public static ProcessEngine getProcessEngine() {
    return BpmPlatform.getDefaultProcessEngine();
  }

  public static SimulatingBpmnDeployer getSimulationBpmnDeployer() {
    return simulatingBpmnDeployer;
  }

  public static Object evaluateExpression(String expression, VariableScope scope) {
    return getProcessEngineConfiguration().getExpressionManager().createExpression(expression).getValue(scope);
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    List<JobHandler> customJobHandlers = processEngineConfiguration.getCustomJobHandlers();
    if (originalCustomJobHandlers == null) {
      originalCustomJobHandlers = new ArrayList<>();
      processEngineConfiguration.setCustomJobHandlers(originalCustomJobHandlers);
      simulationCustomJobHandlers = new ArrayList<>();
    } else {
      originalCustomJobHandlers = new ArrayList<>(customJobHandlers);
      simulationCustomJobHandlers = new ArrayList<>(originalCustomJobHandlers);
    }
    simulationCustomJobHandlers.add(new CompleteUserTaskJobHandler());
    simulationCustomJobHandlers.add(new ClaimUserTaskJobHandler());
    simulationCustomJobHandlers.add(new FireEventJobHandler());
    simulationCustomJobHandlers.add(new CompleteExternalTaskJobHandler());
    simulationCustomJobHandlers.add(new StartProcessInstanceJobHandler());

    List<CommandInterceptor> postCommandInterceptors = processEngineConfiguration.getCustomPostCommandInterceptorsTxRequired();
    if (originalPostCommandInterceptors == null) {
      originalPostCommandInterceptors = new ArrayList<>();
      processEngineConfiguration.setCustomPostCommandInterceptorsTxRequired(originalPostCommandInterceptors);
      simulationPostCommandInterceptors = new ArrayList<>();
    } else {
      originalPostCommandInterceptors = new ArrayList<>(postCommandInterceptors);
      simulationPostCommandInterceptors = new ArrayList<>(originalPostCommandInterceptors);
    }
    simulationPostCommandInterceptors.add(new CreateFireEventJobCommandInterceptor());

    preAlterProcessEngine(processEngineConfiguration);
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    // we don't want to revert that ever
    addPayloadGeneratorExpressionResolution(processEngineConfiguration);

    List<Deployer> deployers = processEngineConfiguration.getDeployers();
    originalDeployers = new ArrayList<>(deployers);
    simulationDeployers = new ArrayList<>(originalDeployers.size());
    originalDeployers.forEach(deployer -> {
      if (deployer instanceof BpmnDeployer) {
        simulatingBpmnDeployer = new SimulatingBpmnDeployer((BpmnDeployer) deployer);
        simulationDeployers.add(simulatingBpmnDeployer);
      } else {
        simulationDeployers.add(deployer);
      }
    });

    postAlterProcessEngine(processEngineConfiguration);
  }

  public static void alterProcessEngine() {
    preAlterProcessEngine(getProcessEngineConfiguration());
    postAlterProcessEngine(getProcessEngineConfiguration());
  }

  private static void preAlterProcessEngine(ProcessEngineConfigurationImpl processEngineConfiguration) {
    processEngineConfiguration.getCustomJobHandlers().clear();
    processEngineConfiguration.getCustomJobHandlers().addAll(simulationCustomJobHandlers);
    processEngineConfiguration.getCustomPostCommandInterceptorsTxRequired().clear();
    processEngineConfiguration.getCustomPostCommandInterceptorsTxRequired().addAll(simulationPostCommandInterceptors);
  }

  private static void postAlterProcessEngine(ProcessEngineConfigurationImpl processEngineConfiguration) {
    processEngineConfiguration.getDeployers().clear();
    processEngineConfiguration.getDeployers().addAll(simulationDeployers);
    processEngineConfiguration.getDeploymentCache().setDeployers(simulationDeployers);
  }

  public static void resetProcessEngine() {
    resetProcessEngine(getProcessEngineConfiguration());
  }

  private static void resetProcessEngine(ProcessEngineConfigurationImpl processEngineConfiguration) {
    processEngineConfiguration.getCustomJobHandlers().clear();
    processEngineConfiguration.getCustomJobHandlers().addAll(originalCustomJobHandlers);
    processEngineConfiguration.getCustomPostCommandInterceptorsTxRequired().clear();
    processEngineConfiguration.getCustomPostCommandInterceptorsTxRequired().addAll(originalPostCommandInterceptors);
    processEngineConfiguration.getDeployers().clear();
    processEngineConfiguration.getDeployers().addAll(originalDeployers);
    processEngineConfiguration.getDeploymentCache().setDeployers(originalDeployers);
  }


  private void addPayloadGeneratorExpressionResolution(ProcessEngineConfigurationImpl processEngineConfiguration) {
    ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();

    CompositeELResolver compositeElResolver;
    try {
      Method method = ExpressionManager.class.getDeclaredMethod("getCachedElResolver", (Class<?>[]) null);
      method.setAccessible(true);
      Object invoke = method.invoke(expressionManager, new Object[]{});

      compositeElResolver = (CompositeELResolver) invoke;
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new RuntimeException("Unable to get cached el resolver", e);
    }

    compositeElResolver.add(new ELResolver() {

      @Override
      public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return Object.class;
      }

      @Override
      public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
      }

      @Override
      public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
      }

      @Override
      public Object getValue(ELContext context, Object base, Object property) {
        if (PAYLOAD_GENERATOR_BEAN_NAME.equals(property)) {
          context.setPropertyResolved(true);
          return payloadGenerator;
        }
        return null;
      }

      @Override
      public boolean isReadOnly(ELContext context, Object base, Object property) {
        return false;
      }

      @Override
      public void setValue(ELContext context, Object base, Object property, Object value) {
      }
    });
  }


  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
  }
}
