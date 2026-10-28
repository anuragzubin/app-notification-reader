package com.notificationreader;

import java.time.*;

/** Phone-local calendar dates, not fixed 24-hour intervals (DST and timezone safe). */
final class DailyReminderPlan {
  static long next(long nowMillis, int minute, ZoneId zone) {
    if (minute < 0 || minute >= 1440) throw new IllegalArgumentException("Invalid time");
    ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
    LocalDate date = now.toLocalDate();
    ZonedDateTime candidate = date.atTime(minute / 60, minute % 60).atZone(zone);
    if (!candidate.toInstant().isAfter(now.toInstant()))
      candidate = date.plusDays(1).atTime(minute / 60, minute % 60).atZone(zone);
    return candidate.toInstant().toEpochMilli();
  }

  static String day(long nowMillis, ZoneId zone) {
    return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString();
  }

  static long midnight(long nowMillis, ZoneId zone) {
    return Instant.ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli();
  }

  static boolean due(long nowMillis, int minute, String expectedDay, String sentDay, ZoneId zone) {
    if (minute < 0 || minute >= 1440) return false;
    ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
    String today = now.toLocalDate().toString();
    return today.equals(expectedDay)
        && !today.equals(sentDay)
        && now.getHour() * 60 + now.getMinute() >= minute;
  }
}
