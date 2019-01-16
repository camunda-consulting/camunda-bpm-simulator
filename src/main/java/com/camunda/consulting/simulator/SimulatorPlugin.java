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
import com.camunda.consulting.simulator.jobhandler.CompleteExternalTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.CompleteUserTaskJobHandler;
import com.camunda.consulting.simulator.jobhandler.FireEventJobHandler;
import com.camunda.consulting.simulator.jobhandler.StartProcessInstanceJobHandler;
import com.camunda.consulting.simulator.modding.SimulatingBpmnDeployer;
import com.camunda.consulting.simulator.modding.SimulationParseListener;

public class SimulatorPlugin implements ProcessEnginePlugin {

  public static final String PAYLOAD_GENERATOR_BEAN_NAME = "g";

  private static Object payloadGenerator;

  static {
    setPayloadGenerator(new PayloadGenerator());
  }

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
    List<Deployer> deployers = getProcessEngineConfiguration().getDeployers();
    SimulatingBpmnDeployer bpmnDeployer = null;
    for (Deployer deployer : deployers) {
      if (deployer instanceof SimulatingBpmnDeployer) {
        bpmnDeployer = (SimulatingBpmnDeployer) deployer;
        break;
      }
    }
    if (bpmnDeployer == null) {
      throw new RuntimeException("No SimulationBpmnDeployer found. Probably simulation plugin was not initialized correctly.");
    }
    return bpmnDeployer;
  }

  public static Object evaluateExpression(String expression, VariableScope scope) {
    return getProcessEngineConfiguration().getExpressionManager().createExpression(expression).getValue(scope);
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
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

    replaceBpmnDeployer(processEngineConfiguration);
    addPayloadGeneratorExpressionResolution(processEngineConfiguration);

  }

  private void addPayloadGeneratorExpressionResolution(ProcessEngineConfigurationImpl processEngineConfiguration) {
    ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();

    CompositeELResolver compositeElResolver;
    try {
      Method method = ExpressionManager.class.getDeclaredMethod("getCachedElResolver", (Class<?>[]) null);
      method.setAccessible(true);
      Object invoke = method.invoke(expressionManager, new Object[] {});

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

  private void replaceBpmnDeployer(ProcessEngineConfigurationImpl processEngineConfiguration) {
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

    SimulatingBpmnDeployer bpmnDeployer = new SimulatingBpmnDeployer(originalBpmnDeployer);
    deployers.set(position, bpmnDeployer);

    processEngineConfiguration.getDeploymentCache().setDeployers(deployers);
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
  }
}
