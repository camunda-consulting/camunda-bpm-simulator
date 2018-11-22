package com.camunda.consulting.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.joda.time.DateTime;
import org.junit.Test;

import com.camunda.consulting.simulator.PayloadGenerator;

public class PayloadGeneratorTest {

  PayloadGenerator g = new PayloadGenerator();

  @Test
  public void generateNormalNumbers() {
    assertThat(g.normal("a", 15, 3)).isBetween(0.0, 30.0);
    assertThat(g.nowPlusHours(2)).isBetween(DateTime.now().plusHours(1).toDate(), DateTime.now().plusHours(3).toDate());
  }

  @Test
  public void timesPerDayTest() {
    LocalDateTime start = LocalDateTime.now().minusDays(10).with(LocalTime.MIN);
    LocalDateTime end = LocalDateTime.now().with(LocalTime.MIN);

    long counter = 0;
    ClockUtil.setCurrentTime(Date.from(start.atZone(ZoneId.systemDefault()).toInstant()));
    try {
      while (end.atZone(ZoneId.systemDefault()).toInstant().isAfter(ClockUtil.getCurrentTime().toInstant())) {
        Date nextFire = g.timesPerDay("test", "08:00", "18:00", 10);
        ClockUtil.setCurrentTime(nextFire);
        counter++;
      }
    } finally {
      ClockUtil.reset();
    }
    assertThat(counter).isBetween(90l, 110l);
  }
}
