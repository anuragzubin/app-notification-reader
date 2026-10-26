package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class DailyQuietPeriodTest {
  @Test
  public void daytimeStartInclusiveStopExclusive() {
    assertFalse(DailyQuietPeriod.contains(8 * 60 + 59, 9 * 60, 17 * 60));
    assertTrue(DailyQuietPeriod.contains(9 * 60, 9 * 60, 17 * 60));
    assertTrue(DailyQuietPeriod.contains(16 * 60 + 59, 9 * 60, 17 * 60));
    assertFalse(DailyQuietPeriod.contains(17 * 60, 9 * 60, 17 * 60));
  }

  @Test
  public void overnightIncludesBothSidesOfMidnight() {
    assertFalse(DailyQuietPeriod.contains(21 * 60 + 59, 22 * 60, 7 * 60));
    assertTrue(DailyQuietPeriod.contains(22 * 60, 22 * 60, 7 * 60));
    assertTrue(DailyQuietPeriod.contains(23 * 60 + 59, 22 * 60, 7 * 60));
    assertTrue(DailyQuietPeriod.contains(0, 22 * 60, 7 * 60));
    assertTrue(DailyQuietPeriod.contains(6 * 60 + 59, 22 * 60, 7 * 60));
    assertFalse(DailyQuietPeriod.contains(7 * 60, 22 * 60, 7 * 60));
    assertFalse(DailyQuietPeriod.contains(12 * 60, 22 * 60, 7 * 60));
  }

  @Test
  public void midnightBoundariesAndSingleMinuteIntervals() {
    assertTrue(DailyQuietPeriod.contains(0, 0, 1));
    assertFalse(DailyQuietPeriod.contains(1, 0, 1));
    assertTrue(DailyQuietPeriod.contains(1439, 1439, 0));
    assertFalse(DailyQuietPeriod.contains(0, 1439, 0));
  }

  @Test
  public void equalTimesAreInvalidRatherThanMutingAllDay() {
    assertFalse(DailyQuietPeriod.contains(600, 600, 600));
    assertFalse(DailyQuietPeriod.contains(0, 600, 600));
  }
}
