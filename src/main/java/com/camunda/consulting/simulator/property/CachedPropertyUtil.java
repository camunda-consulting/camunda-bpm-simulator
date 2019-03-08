package com.camunda.consulting.simulator.property;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.consulting.simulator.SimulatorPlugin;

public class CachedPropertyUtil {
    static final Logger LOG = LoggerFactory.getLogger(CachedPropertyUtil.class);

    // property -> process definition id -> activity id -> maybe expression
    private static Map<String, Map<String, Map<String, Optional<Expression>>>> expressionCache = new HashMap<>();

    public static Optional<Expression> getExpression(DelegateExecution execution, String activityId, String property) {
	Map<String, Map<String, Optional<Expression>>> propertyExpressionCache = expressionCache.get(property);
	if (propertyExpressionCache == null) {
	    propertyExpressionCache = new HashMap<>();
	    expressionCache.put(property, propertyExpressionCache);
	}

	Map<String, Optional<Expression>> activityIdToExpression = expressionCache.get(property)
		.get(execution.getProcessDefinitionId());
	if (activityIdToExpression == null) {
	    activityIdToExpression = new HashMap<>();
	    propertyExpressionCache.put(execution.getProcessDefinitionId(), activityIdToExpression);
	}
	Optional<Expression> expression = activityIdToExpression.get(activityId);
	if (expression == null) {
	    ModelElementInstance modelElementInstance = execution.getBpmnModelInstance()
		    .getModelElementById(activityId);
	    Optional<String> modelProperty = ModelPropertyUtil.readCamundaProperty((BaseElement) modelElementInstance,
		    property);
	    expression = modelProperty
		    .map(SimulatorPlugin.getProcessEngineConfiguration().getExpressionManager()::createExpression);
	    activityIdToExpression.put(activityId, expression);
	    LOG.debug("Return new expression");
	} else {
	    LOG.debug("Return cached expression");
	}
	return expression;
    }
}
