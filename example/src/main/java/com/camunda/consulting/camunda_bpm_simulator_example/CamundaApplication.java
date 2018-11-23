package com.camunda.consulting.camunda_bpm_simulator_example;

import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.joda.time.DateTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.camunda.consulting.simulator.PayloadGenerator;
import com.camunda.consulting.simulator.SimulationExecutor;
import com.camunda.consulting.simulator.SimulatorPlugin;

@SpringBootApplication
@EnableProcessApplication("camunda-bpm-simulator-example")
public class CamundaApplication {

  public static void main(String... args) {
    SpringApplication.run(CamundaApplication.class, args);

    SimulationExecutor.execute(DateTime.now().minusMonths(1).toDate(), DateTime.now().toDate());
  }

  /**
   * Makes the PayloadGenerator available in expressions and scripts.
   */
  @Bean
  public PayloadGenerator generator() {
    return new PayloadGenerator();
  }

  @Bean
  public SimulatorPlugin simulatorPlugin() {
    return new SimulatorPlugin();
  }
}
