package com.notificationreader;

import android.Manifest;
import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.time.ZoneId;

final class AppReminders {
  static final String FIRE = "com.notificationreader.APP_REMINDER";

  static boolean enabled(Context c, AppCatalog.App app) {
    return Rules.prefs(c).getBoolean("reminder_enabled:" + app.id, false);
  }

  static int minute(Context c, AppCatalog.App app) {
    return Math.max(0, Math.min(1439, Rules.prefs(c).getInt("reminder_minute:" + app.id, 20 * 60)));
  }

  static boolean usageAllowed(Context c) {
    return c.getSystemService(AppOpsManager.class)
            .unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName())
        == AppOpsManager.MODE_ALLOWED;
  }

  static boolean precise(Context c) {
    return Build.VERSION.SDK_INT < 31
        || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
  }

  static PendingIntent alarm(Context c, AppCatalog.App app, long when) {
    Intent intent =
        new Intent(c, ReminderReceiver.class)
            .setAction(FIRE)
            .setData(android.net.Uri.parse("priorityvoice://reminder/" + app.id))
            .putExtra("app", app.id)
            .putExtra("minute", minute(c, app))
            .putExtra("day", DailyReminderPlan.day(when, ZoneId.systemDefault()));
    return PendingIntent.getBroadcast(
        c,
        app.id.hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  static void schedule(Context c, AppCatalog.App app) {
    AlarmManager manager = c.getSystemService(AlarmManager.class);
    long when =
        DailyReminderPlan.next(System.currentTimeMillis(), minute(c, app), ZoneId.systemDefault());
    PendingIntent pending = alarm(c, app, when);
    manager.cancel(pending);
    if (!enabled(c, app)) return;
    try {
      if (precise(c)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
      else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
    } catch (SecurityException e) {
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
    }
  }

  static void scheduleAll(Context c) {
    for (AppCatalog.App app : AppCatalog.ALL) schedule(c, app);
  }

  static void cancelNotice(Context c, AppCatalog.App app) {
    c.getSystemService(NotificationManager.class)
        .cancel(53000 + java.util.Arrays.asList(AppCatalog.ALL).indexOf(app));
  }

  static boolean openedToday(Context c, String packageName, long now) {
    // Usage access detects manual opens too. A launch request is never counted as a successful
    // open.
    UsageEvents events =
        c.getSystemService(UsageStatsManager.class)
            .queryEvents(DailyReminderPlan.midnight(now, ZoneId.systemDefault()), now);
    if (events == null) throw new IllegalStateException("Usage data unavailable");
    UsageEvents.Event event = new UsageEvents.Event();
    while (events.hasNextEvent()) {
      events.getNextEvent(event);
      if (packageName.equals(event.getPackageName())
          && event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) return true;
    }
    return false;
  }

  static void deliver(Context c, Intent alarm) {
    AppCatalog.App app = AppCatalog.find(alarm.getStringExtra("app"));
    if (app == null) return;
    int scheduledMinute = alarm.getIntExtra("minute", -1);
    String expectedDay = alarm.getStringExtra("day");
    // Save the incoming extras before scheduling updates this app's PendingIntent.
    schedule(c, app);
    long now = System.currentTimeMillis();
    if (!enabled(c, app)
        || scheduledMinute != minute(c, app)
        || !DailyReminderPlan.due(
            now,
            scheduledMinute,
            expectedDay,
            Rules.prefs(c).getString("reminder_sent:" + app.id, ""),
            ZoneId.systemDefault())) return;
    if (!Rules.prefs(c).getBoolean("enabled", true)
        || QuietHours.active(c)
        || !Rules.prefs(c).getBoolean("open_" + app.id, true)
        || !usageAllowed(c)) return;
    if (Build.VERSION.SDK_INT >= 33
        && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return;
    String pkg = InstalledApps.packageFor(c, app);
    if (pkg == null) return;
    if (openedToday(c, pkg, now)) {
      cancelNotice(c, app);
      return;
    }
    // Settings/access may change while the usage query runs.
    if (!enabled(c, app)
        || !Rules.prefs(c).getBoolean("enabled", true)
        || QuietHours.active(c)
        || !Rules.prefs(c).getBoolean("open_" + app.id, true)
        || !usageAllowed(c)) return;
    Intent open = c.getPackageManager().getLaunchIntentForPackage(pkg);
    if (open == null) return;
    NotificationManager manager = c.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "app_reminders", "Daily app reminders", NotificationManager.IMPORTANCE_DEFAULT));
    PendingIntent pending =
        PendingIntent.getActivity(
            c,
            app.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    manager.notify(
        53000 + java.util.Arrays.asList(AppCatalog.ALL).indexOf(app),
        new Notification.Builder(c, "app_reminders")
            .setSmallIcon(R.drawable.ic_reply)
            .setContentTitle("Time for " + app.label)
            .setContentText("You haven't opened " + app.label + " today. Tap to open.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(DailyReminderPlan.next(now, 0, ZoneId.systemDefault()) - now)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build());
    Rules.prefs(c)
        .edit()
        .putString("reminder_sent:" + app.id, DailyReminderPlan.day(now, ZoneId.systemDefault()))
        .apply();
  }
}
