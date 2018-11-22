package com.camunda.consulting.simulator.property;

import org.camunda.bpm.engine.impl.el.Expression;

import com.camunda.consulting.simulator.SimulatorPlugin;

public class Work {
  Expression variableExpression;
  Expression valueExpression;

  public Work(String setVariableValue) {
    String[] split = setVariableValue.split("=", 2);

    if (split.length != 2) {
      throw new RuntimeException("Expression does not evaluate to proper simulateSetVariable command: " + setVariableValue);
    }

    variableExpression = SimulatorPlugin.getProcessEngineConfiguration().getExpressionManager().createExpression(split[0]);
    valueExpression = SimulatorPlugin.getProcessEngineConfiguration().getExpressionManager().createExpression(split[1]);
  }
  
  public Expression getVariableExpression() {
    return variableExpression;
  }
  
  public Expression getValueExpression() {
    return valueExpression;
  }
}