package com.camunda.consulting.simulator.property;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.jgrapht.graph.DirectedAcyclicGraph;

import com.camunda.consulting.simulator.listener.PayloadGeneratorListener;

public class ModelPropertyUtil {

  public static final String CAMUNDA_PROPERTY_SIM_NEXT_FIRE = "simNextFire";
  public static final String CAMUNDA_PROPERTY_SIM_NEXT_COMPLETE = "simNextComplete";
  public static final String CAMUNDA_PROPERTY_SIM_NEXT_CLAIM = "simNextClaim";
  public static final String CAMUNDA_PROPERTY_SIM_CLAIM_USER = "simClaimUser";
  public static final String CAMUNDA_PROPERTY_SIM_GENERATE_PAYLOAD = "simGeneratePayload";
  public static final String CAMUNDA_PROPERTY_SIM_INIT_PAYLOAD = "simInitPayload";
  public static final String CAMUNDA_PROPERTY_SIM_INIT_BUSINESS_KEY = "simInitBusinessKey";
  public static final String CAMUNDA_PROPERTY_SIM_CALL_REAL_IMPLEMENTATION = "simCallRealImplementation";
  public static final String CAMUNDA_PROPERTY_SIM_KEEP_LISTENERS = "simKeepListeners";
  public static final String[] TRUE = {"true", "True", "yes", "Yes"};

  public static Map<BaseElement, Map<String,Work[]>> workByElementAndPropertyCache = new HashMap<>();

  public static boolean isTrue(Optional<String> propertyValue) {
    return propertyValue.isPresent() && isTrue(propertyValue.get());
  }
  
  public static boolean isTrue(String propertyValue) {
    return Arrays.asList(TRUE).contains(propertyValue);
  }

  public static Optional<String> getNextFire(ModelElementInstance elementInstance) {
    return readCamundaProperty((BaseElement) elementInstance, CAMUNDA_PROPERTY_SIM_NEXT_FIRE);
  }

  public static Optional<String> getNextFire(Element xmlElement) {
    return readCamundaProperty(xmlElement, CAMUNDA_PROPERTY_SIM_NEXT_FIRE);
  }
  
  public static Optional<String> getNextComplete(ModelElementInstance elementInstance) {
      return readCamundaProperty((BaseElement) elementInstance, CAMUNDA_PROPERTY_SIM_NEXT_COMPLETE);
  }
  
  public static Optional<String> getNextClaim(ModelElementInstance elementInstance) {
      return readCamundaProperty((BaseElement) elementInstance, CAMUNDA_PROPERTY_SIM_NEXT_CLAIM);
  }
  
  public static Optional<String> getClaimUser(ModelElementInstance elementInstance) {
      return readCamundaProperty((BaseElement) elementInstance, CAMUNDA_PROPERTY_SIM_CLAIM_USER);
  }

  public static Optional<String> readCamundaProperty(Element xmlElement, String propertyName) {
    Element extentionsElement = xmlElement.element("extensionElements");
    if (extentionsElement == null) {
      return Optional.empty();
    }
    Element camundaProperties = extentionsElement.elementNS(BpmnParse.CAMUNDA_BPMN_EXTENSIONS_NS, "properties");
    if (camundaProperties == null) {
      return Optional.empty();
    }

    List<Element> propertys = camundaProperties.elements("property");
    for (Element property : propertys) {
      if (propertyName.equals(property.attribute("name"))) {
        return Optional.of(property.attribute("value"));
      }
    }
    return Optional.empty();
  }

  public static Optional<String> readCamundaProperty(BaseElement modelElementInstance, String propertyName) {
    if (modelElementInstance.getExtensionElements() == null) {
      return Optional.empty();
    }
    return queryCamundaPropertyValues(modelElementInstance, propertyName).findFirst();
  }

  public static Collection<String> readCamundaPropertyMulti(BaseElement modelElementInstance, String propertyName) {
    if (modelElementInstance.getExtensionElements() == null) {
      return Collections.emptyList();
    }
    return queryCamundaPropertyValues(modelElementInstance, propertyName).collect(Collectors.toList());
  }

  protected static Stream<String> queryCamundaPropertyValues(BaseElement modelElementInstance, String propertyName) {
    return modelElementInstance.getExtensionElements().getElementsQuery().filterByType(CamundaProperties.class).list().stream() //
        .map(CamundaProperties::getCamundaProperties) //
        .flatMap(Collection::stream) //
        .filter(property -> property.getCamundaName().equals(propertyName)) //
        .map(CamundaProperty::getCamundaValue) //
        .filter(Objects::nonNull) //
    ;
  }

  /*
   * Cached read of "simulateSetVariable"-extensions for the given element.
   */
  public static Work[] getPayloadValuesOrdered(BaseElement element, String camundaPropertyName) {
    Work[] values = null;
    Map<String, Work[]> byProperty = workByElementAndPropertyCache.get(element);
    if (byProperty == null) {
      byProperty = new HashMap<>();
      workByElementAndPropertyCache.put(element,byProperty);
    }
    values = byProperty.get(camundaPropertyName);
    if (values == null) {
      String[] expressions = readCamundaPropertyMulti(element, camundaPropertyName).toArray(new String[] {});
      values = new Work[expressions.length];
  
      DirectedAcyclicGraph<Work, Object> graph = new DirectedAcyclicGraph<>(Object.class);
      for (int i = 0; i < expressions.length; i++) {
        values[i] = new Work(expressions[i]);
        graph.addVertex(values[i]);
      }
      for (Work currentWork : values) {
        for (Work otherWork : values) {
          if (currentWork.getValueExpression().getExpressionText().matches(".*\\W" + Pattern.quote(otherWork.getVariableExpression().getExpressionText()) + "\\W.*")) {
            try {
              graph.addEdge(otherWork, currentWork);
            } catch (IllegalArgumentException e) {
              PayloadGeneratorListener.LOG.warn("Possible cycle in simulateSetVariable-dependencies detected when checking '{}'", currentWork.getValueExpression());
            }
          }
        }
      }
  
      int i = 0;
      for (Iterator<Work> iterator = graph.iterator(); iterator.hasNext();) {
        Work next = iterator.next();
        values[i++] = next;
      }
      byProperty.put(camundaPropertyName, values);
    }
    return values;
  }

}
