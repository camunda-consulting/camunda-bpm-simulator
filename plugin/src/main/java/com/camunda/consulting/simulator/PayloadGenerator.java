package com.camunda.consulting.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.Variables.SerializationDataFormats;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayloadGenerator {
  private static Logger LOG = LoggerFactory.getLogger(PayloadGenerator.class);

  private Map<String, NormalDistribution> normalDistributionRegistry = new HashMap<>();

  private NormalDistribution getNormalDistribution(String name, double mean, double standardDeviation) {
    NormalDistribution normalDistribution = normalDistributionRegistry.get(name);
    if (normalDistribution == null) {
      normalDistribution = new NormalDistribution(mean, standardDeviation);
      normalDistributionRegistry.put(name, normalDistribution);
    }
    if (mean != normalDistribution.getMean() || standardDeviation != normalDistribution.getStandardDeviation()) {
      throw new RuntimeException("You cannot create two normal distribution with the same name (and different mean and deviation): " + name);
    }
    return normalDistribution;
  }

  // private NormalDistribution getNormalDistribution(String name) {
  // NormalDistribution normalDistribution =
  // normalDistributionRegistry.get(name);
  // if (normalDistribution == null) {
  // throw new RuntimeException("You have to create a normal distribution first
  // with mean and deviation.");
  // }
  // return normalDistribution;
  // }

  /**
   * Treats null as false. Treats numbers to be true iff greater than 0. Treats
   * strings to be true if their lower-case version equals "1", "true" or "yes".
   * Anything unknown is treated as false.
   * 
   * @param o
   *          some object
   * @return true or false
   */
  public Boolean toBoolean(Object o) {
    if (o == null)
      return false;
    if (o instanceof Boolean)
      return (Boolean) o;
    if (o instanceof Number)
      return ((Number) o).doubleValue() > 0;
    if (o instanceof String)
      return ((String) o).toLowerCase().equals("true") || o.equals("1") || ((String) o).toLowerCase().equals("yes");
    return false;
  }

  /**
   * Handles null, Date, Calendar and String (by DateFormat.parse). Anything
   * else will result in null.
   * 
   * @param o
   *          some object
   * @return the parsed date
   */
  public Date toDate(Object o) {
    if (o == null)
      return null;
    if (o instanceof Date)
      return (Date) o;
    if (o instanceof Calendar)
      return ((Calendar) o).getTime();
    if (o instanceof String)
      try {
        return DateFormat.getDateTimeInstance().parse((String) o);
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    return null;
  }

  public Double toDouble(Object o) {
    if (o == null)
      return null;
    if (o instanceof Number)
      return ((Number) o).doubleValue();
    return Double.parseDouble(o.toString());
  }

  public Integer toInt(Object o) {
    if (o == null)
      return null;
    if (o instanceof Number)
      return ((Number) o).intValue();
    return Integer.parseInt(o.toString());
  }

  public Long toLong(Object o) {
    if (o == null)
      return null;
    if (o instanceof Number)
      return ((Number) o).longValue();
    return Long.parseLong(o.toString());
  }

  public Short toShort(Object o) {
    if (o == null)
      return null;
    if (o instanceof Number)
      return ((Number) o).shortValue();
    return Short.parseShort(o.toString());
  }

  public String toString(Object o) {
    if (o == null)
      return null;
    return o.toString();
  }

  String[] firstnamesFemale = null;
  String[] firstnamesMale = null;

  public String firstnameFemale() {
    if (firstnamesFemale == null) {
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/firstnames-female.txt")))) {
        firstnamesFemale = buffer.lines().toArray(String[]::new);
      } catch (IOException e) {
        LOG.error("Could not load female first names");
        firstnamesFemale = new String[] { "Jane" };
      }
    }
    return firstnamesFemale[(int) (Math.random() * firstnamesFemale.length)];
  }

  public String firstnameMale() {
    if (firstnamesMale == null) {
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/firstnames-male.txt")))) {
        firstnamesMale = buffer.lines().toArray(String[]::new);
      } catch (IOException e) {
        LOG.error("Could not load male first names");
        firstnamesMale = new String[] { "John" };
      }
    }
    return firstnamesMale[(int) (Math.random() * firstnamesMale.length)];
  }

  /**
   * Female/male 50:50
   * 
   * @return some first name
   */
  public String firstname() {
    return Math.random() < 0.5 ? firstnameFemale() : firstnameMale();
  }

  String[] surnamesGerman = null;
  String[] surnamesEnglish = null;

  public String surnameGerman() {
    if (surnamesGerman == null) {
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/surnames-de.txt")))) {
        surnamesGerman = buffer.lines().toArray(String[]::new);
      } catch (IOException e) {
        LOG.error("Could not load german surnames");
        surnamesGerman = new String[] { "Mustermann" };
      }
    }
    return surnamesGerman[(int) (Math.random() * surnamesGerman.length)];
  }

  public String surnameEnglish() {
    if (surnamesEnglish == null) {
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/surnames-en.txt")))) {
        surnamesEnglish = buffer.lines().toArray(String[]::new);
      } catch (IOException e) {
        LOG.error("Could not load english surnames");
        surnamesEnglish = new String[] { "Doe" };
      }
    }
    return surnamesEnglish[(int) (Math.random() * surnamesEnglish.length)];
  }

  /**
   * Evenly distributed birth date, minAge and maxAge in respect to current
   * simulation time.
   * 
   * @param minAge
   *          minimum age
   * @param maxAge
   *          maximum age
   * @return a birthdate
   */
  public Date uniformBirthdate(int minAge, int maxAge) {
    Calendar calMin = Calendar.getInstance();
    calMin.setTime(ClockUtil.getCurrentTime());
    calMin.set(Calendar.HOUR_OF_DAY, 0);
    calMin.set(Calendar.MINUTE, 0);
    calMin.set(Calendar.SECOND, 0);
    calMin.set(Calendar.MILLISECOND, 0);
    Calendar calMax = (Calendar) calMin.clone();
    calMin.add(Calendar.YEAR, -maxAge);
    calMax.add(Calendar.YEAR, -minAge);
    long minMillis = calMin.getTimeInMillis();
    long maxMillis = calMax.getTimeInMillis();
    long chosenMillis = minMillis + (long) (Math.random() * (maxMillis - minMillis));
    return Date.from(Instant.ofEpochMilli(chosenMillis));
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs2(T o1, T o2) {
    return (T) uniformFromArray(new Object[] { o1, o2 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs3(T o1, T o2, T o3) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs4(T o1, T o2, T o3, T o4) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3, o4 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs5(T o1, T o2, T o3, T o4, T o5) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3, o4, o5 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs6(T o1, T o2, T o3, T o4, T o5, T o6) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3, o4, o5, o6 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs7(T o1, T o2, T o3, T o4, T o5, T o6, T o7) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3, o4, o5, o6, o7 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs8(T o1, T o2, T o3, T o4, T o5, T o6, T o7, T o8) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3, o4, o5, o6, o7, o8 });
  }

  @SuppressWarnings("unchecked")
  public <T> T uniformFromArgs9(T o1, T o2, T o3, T o4, T o5, T o6, T o7, T o8, T o9) {
    return (T) uniformFromArray(new Object[] { o1, o2, o3, o4, o5, o6, o7, o8, o9 });
  }

  public Object uniformFromArray(Object[] objects) {
    if (objects == null || objects.length == 0)
      return null;
    return objects[(int) (Math.random() * objects.length)];
  }

  public Object uniformFromList(List<?> objects) {
    if (objects == null || objects.size() == 0)
      return null;
    return objects.get((int) (Math.random() * objects.size()));
  }

  /**
   * As always min lq returnValue le max.
   * 
   * @param min
   *          minimum value
   * @param max
   *          maximum value
   * @return a random value
   */
  public Short uniformShort(short min, short max) {
    return (short) (min + (short) Math.floor(((Math.random() * (max - min)))));
  }

  /**
   * As always min lq returnValue le max.
   * 
   * @param min
   *          minimum value
   * @param max
   *          maximum value
   * @return a random value
   */
  public Integer uniformInt(int min, int max) {
    return min + (int) Math.floor(((Math.random() * (max - min))));
  }

  /**
   * Returns a normally distributed value around a mean value with standard
   * deviation. A name of the distribution must be given to keep the state.
   * 
   * @param distributionName
   * @param mean
   * @param standardDeviation
   * @return
   */
  public Double normal(String distributionName, double mean, double standardDeviation) {
    return getNormalDistribution(distributionName, mean, standardDeviation).sample();
  }

  /**
   * As always min lq returnValue le max.
   * 
   * @param min
   *          minimum value
   * @param max
   *          maximum value
   * @return a random value
   */
  public Long uniformLong(long min, long max) {
    return min + (long) Math.floor(((Math.random() * (max - min))));
  }

  /**
   * As always min lq returnValue le max.
   * 
   * @param min
   *          minimum value
   * @param max
   *          maximum value
   * @return a random value
   */
  public Double uniformDouble(double min, double max) {
    return min + Math.random() * (max - min);
  }

  public Boolean uniformBoolean() {
    return Math.random() < 0.5;
  }

  public Boolean uniformBooleanByProbability(double probability) {
    return Math.random() < probability;
  }

  public FileValue smallPdf(String name) {
    try (InputStream data = getClass().getResourceAsStream("/mockument.pdf");) {
      return Variables.fileValue(name.toLowerCase().endsWith(".pdf") ? name : name + ".pdf").mimeType("application/pdf").file(data).create();
    } catch (IOException e) {
      LOG.error("Could not load mockument");
      return Variables.fileValue("Error loading content").create();
    }
  }

  public Object ifthenelse(Object condition, Object whenTrue, Object whenFalse) {
    return toBoolean(condition) ? whenTrue : whenFalse;
  }

  private Long uniqueNumber = 1l;

  public Long uniqueNumber() {
    return uniqueNumber++;
  }

  public String email(String name, String company) {
    return name.trim().toLowerCase().replaceAll("\\s", "").replaceAll("\\W", ".") + "@" + company.trim().toLowerCase().replaceAll("\\W", "-") + ".com";
  }

  public String format1(String pattern, Object o1) {
    return String.format(pattern, o1);
  }

  public String format2(String pattern, Object o1, Object o2) {
    return String.format(pattern, o1, o2);
  }

  public String format3(String pattern, Object o1, Object o2, Object o3) {
    return String.format(pattern, o1, o2, o3);
  }

  public String format4(String pattern, Object o1, Object o2, Object o3, Object o4) {
    return String.format(pattern, o1, o2, o3, o4);
  }

  public String format5(String pattern, Object o1, Object o2, Object o3, Object o4, Object o5) {
    return String.format(pattern, o1, o2, o3, o4, o5);
  }

  public String format6(String pattern, Object o1, Object o2, Object o3, Object o4, Object o5, Object o6) {
    return String.format(pattern, o1, o2, o3, o4, o5, o6);
  }

  public String format7(String pattern, Object o1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7) {
    return String.format(pattern, o1, o2, o3, o4, o5, o6, o7);
  }

  public String format8(String pattern, Object o1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7, Object o8) {
    return String.format(pattern, o1, o2, o3, o4, o5, o6, o7, o8);
  }

  public String format9(String pattern, Object o1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7, Object o8, Object o9) {
    return String.format(pattern, o1, o2, o3, o4, o5, o6, o7, o8, o9);
  }

  public TypedValue json(Object o) {
    return Variables.objectValue(o).serializationDataFormat(SerializationDataFormats.JSON).create();
  }

  public TypedValue xml(Object o) {
    return Variables.objectValue(o).serializationDataFormat(SerializationDataFormats.XML).create();
  }

  public TypedValue java(Object o) {
    return Variables.objectValue(o).serializationDataFormat(SerializationDataFormats.JAVA).create();
  }

  public <T> List<T> listFromArray(T[] items) {
    return Arrays.asList(items);
  }

  public List<?> emptyList() {
    return Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs1(T o1) {
    return (List<T>) listFromArray(new Object[] { o1 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs2(T o1, T o2) {
    return (List<T>) listFromArray(new Object[] { o1, o2 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs3(T o1, T o2, T o3) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs4(T o1, T o2, T o3, T o4) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3, o4 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs5(T o1, T o2, T o3, T o4, T o5) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3, o4, o5 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs6(T o1, T o2, T o3, T o4, T o5, T o6) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3, o4, o5, o6 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs7(T o1, T o2, T o3, T o4, T o5, T o6, T o7) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3, o4, o5, o6, o7 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs8(T o1, T o2, T o3, T o4, T o5, T o6, T o7, T o8) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3, o4, o5, o6, o7, o8 });
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> listFromArgs9(T o1, T o2, T o3, T o4, T o5, T o6, T o7, T o8, T o9) {
    return (List<T>) listFromArray(new Object[] { o1, o2, o3, o4, o5, o6, o7, o8, o9 });
  }

  /**
   * Current process engine's time.
   * 
   * @return
   */
  public Date now() {
    return ClockUtil.getCurrentTime();
  }

  /**
   * Current process engine's time plus given amount of milliseconds (negative
   * values allowed).
   * 
   * @return
   */
  public Date nowPlusMillis(int millis) {
    return nowPlusPeriod(Duration.ofMillis(millis));
  }

  /**
   * Current process engine's time plus given amount of seconds (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusSeconds(int seconds) {
    return nowPlusPeriod(Duration.ofSeconds(seconds));
  }

  /**
   * Current process engine's time plus given amount of minutes (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusMinutes(int minutes) {
    return nowPlusPeriod(Duration.ofMinutes(minutes));
  }

  /**
   * Current process engine's time plus given amount of hours (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusHours(int hours) {
    return nowPlusPeriod(Duration.ofHours(hours));
  }

  /**
   * Current process engine's time plus given amount of days (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusDays(int days) {
    return nowPlusPeriod(Period.ofDays(days));
  }

  /**
   * Current process engine's time plus given amount of weeks (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusWeeks(int weeks) {
    return nowPlusPeriod(Period.ofWeeks(weeks));
  }

  /**
   * Current process engine's time plus given amount of months (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusMonths(int months) {
    return nowPlusPeriod(Period.ofMonths(months));
  }

  /**
   * Current process engine's time plus given amount of years (negative values
   * allowed).
   * 
   * @return
   */
  public Date nowPlusYears(int years) {
    return nowPlusPeriod(Period.ofYears(years));
  }

  public Date nowPlusPeriod(TemporalAmount amount) {
    return Date
        .from(LocalDateTime.ofInstant(ClockUtil.getCurrentTime().toInstant(), ZoneId.systemDefault()).plus(amount).atZone(ZoneId.systemDefault()).toInstant());
  }

  /**
   * Calculates the next time based on current time such that consecutive
   * calling of this function provides approximately 'times' results per day
   * between 'morning' and 'evening'.
   * 
   * @param uniqueName
   *          To identify the distribution used
   * @param morning
   *          in format 'hh:mm'
   * @param evening
   *          in format 'hh:mm'
   * @param times
   *          must be at least 1
   * @return
   */
  public Date timesPerDay(String uniqueName, String morning, String evening, long times) {
    if (times < 1) {
      throw new IllegalArgumentException("times must be at least 1");
    }

    final LocalTime morningTime = LocalTime.parse(morning);
    final LocalTime eveningTime = LocalTime.parse(evening);

    final LocalDateTime now = LocalDateTime.ofInstant(ClockUtil.getCurrentTime().toInstant(), ZoneId.systemDefault());
    final LocalDateTime todayMorning = now.with(morningTime);
    final LocalDateTime todayEvening = todayMorning.with(eveningTime);

    final long intervalNanos = todayMorning.until(todayEvening, ChronoUnit.NANOS) / times;
    final long randomizedIntervalNanos = normal(uniqueName, intervalNanos, intervalNanos / 3).longValue();
    
    LocalDateTime nextSample = now.plusNanos(randomizedIntervalNanos);
    if (nextSample.isBefore(todayMorning)) {
      nextSample = todayMorning.plusNanos(randomizedIntervalNanos/2);
    }
    if (nextSample.isAfter(todayEvening)) {
      final LocalDateTime tomorrowMorning = todayMorning.plusDays(1);
      final LocalDateTime tomorrowEvening = todayEvening.plusDays(1);

      long overhangNanos = todayEvening.until(nextSample, ChronoUnit.NANOS);
      nextSample = tomorrowMorning.plusNanos(overhangNanos);

      // in theory, it is possible to be again behind the evening hours, so...
      if (nextSample.isAfter(tomorrowEvening)) {
        final LocalDateTime dayAfterTomorrowMorning = tomorrowMorning.plusDays(1);
        overhangNanos = tomorrowEvening.until(nextSample, ChronoUnit.NANOS);
        nextSample = dayAfterTomorrowMorning.plusNanos(overhangNanos);
      }
    }

    return Date.from(nextSample.atZone(ZoneId.systemDefault()).toInstant());
  }

  /**
   * Returns an integer that linearly increases from start to end of a history
   * simulation between min and max. If no history simulation is running, always
   * max is returned.
   * 
   * @param min
   * @param max
   * @return
   */
  public Integer linearBySimulationTime(int min, int max) {
    return min + (int) (SimulationExecutor.getProgress() * (max - min));
  }
}
