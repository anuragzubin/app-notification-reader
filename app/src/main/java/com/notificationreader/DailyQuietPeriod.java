package com.notificationreader;

/** Daily local wall-clock interval: start included, stop excluded, overnight supported. */
final class DailyQuietPeriod {
  static boolean contains(int minute, int start, int stop) {
    if (start == stop) return false; // Invalid interval; the form rejects equal times.
    return start < stop ? minute >= start && minute < stop : minute >= start || minute < stop;
  }
}
