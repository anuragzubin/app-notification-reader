package com.notificationreader;

import static org.junit.Assert.*;

import java.time.*;
import org.junit.Test;

public class DailyReminderPlanTest {
  private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

  private long time(String value) {
    return ZonedDateTime.parse(value).toInstant().toEpochMilli();
  }

  @Test
  public void eachAppCanHaveAnIndependentTimeTodayOrTomorrow() {
    long now = time("2026-10-28T09:00:00+05:30[Asia/Kolkata]");
    assertEquals(
        time("2026-10-28T20:00:00+05:30[Asia/Kolkata]"),
        DailyReminderPlan.next(now, 20 * 60, INDIA));
    assertEquals(
        time("2026-10-29T08:00:00+05:30[Asia/Kolkata]"),
        DailyReminderPlan.next(now, 8 * 60, INDIA));
  }

  @Test
  public void afterFiringAtScheduledMinuteNextIsTomorrow() {
    long now = time("2026-10-28T20:00:00+05:30[Asia/Kolkata]");
    assertEquals(
        time("2026-10-29T20:00:00+05:30[Asia/Kolkata]"), DailyReminderPlan.next(now, 1200, INDIA));
  }

  @Test
  public void delayedAlarmMayDeliverTodayButNeverFromYesterday() {
    long now = time("2026-10-28T20:15:00+05:30[Asia/Kolkata]");
    assertTrue(DailyReminderPlan.due(now, 1200, "2026-10-28", "", INDIA));
    assertFalse(DailyReminderPlan.due(now, 1200, "2026-10-27", "", INDIA));
    assertFalse(DailyReminderPlan.due(now, 1200, "2026-10-28", "2026-10-28", INDIA));
    assertFalse(DailyReminderPlan.due(now, 1300, "2026-10-28", "", INDIA));
  }

  @Test
  public void springForwardUsesNextValidLocalTime() {
    ZoneId zone = ZoneId.of("America/New_York");
    long now = time("2026-03-08T01:00:00-05:00[America/New_York]");
    assertEquals(
        time("2026-03-08T03:30:00-04:00[America/New_York]"),
        DailyReminderPlan.next(now, 150, zone));
  }

  @Test
  public void autumnDayDoesNotAssumeTwentyFourHours() {
    ZoneId zone = ZoneId.of("America/New_York");
    long now = time("2026-10-31T09:00:00-04:00[America/New_York]");
    long next = DailyReminderPlan.next(now, 540, zone);
    assertEquals(time("2026-11-01T09:00:00-05:00[America/New_York]"), next);
    assertEquals(25 * 60 * 60 * 1000L, next - now);
  }

  @Test
  public void todayAndMidnightUsePhoneZoneNotUtcDate() {
    long now = Instant.parse("2026-10-28T20:00:00Z").toEpochMilli();
    assertEquals("2026-10-29", DailyReminderPlan.day(now, INDIA));
    assertEquals(
        Instant.parse("2026-10-28T18:30:00Z").toEpochMilli(),
        DailyReminderPlan.midnight(now, INDIA));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsInvalidTimes() {
    DailyReminderPlan.next(0, 1440, INDIA);
  }
}
