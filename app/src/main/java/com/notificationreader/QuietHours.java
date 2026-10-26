package com.notificationreader;

import android.content.Context;
import java.util.Calendar;

final class QuietHours {
  static boolean enabled(Context context) {
    return Rules.prefs(context).getBoolean("quiet_enabled", false);
  }

  static int start(Context context) {
    return Rules.prefs(context).getInt("quiet_start", 22 * 60);
  }

  static int stop(Context context) {
    return Rules.prefs(context).getInt("quiet_stop", 7 * 60);
  }

  static boolean active(Context context) {
    if (!enabled(context)) return false;
    Calendar now = Calendar.getInstance();
    return DailyQuietPeriod.contains(
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
        start(context),
        stop(context));
  }

  static String time(Context context, int minute) {
    Calendar date = Calendar.getInstance();
    date.set(Calendar.HOUR_OF_DAY, minute / 60);
    date.set(Calendar.MINUTE, minute % 60);
    return android.text.format.DateFormat.getTimeFormat(context).format(date.getTime());
  }
}
