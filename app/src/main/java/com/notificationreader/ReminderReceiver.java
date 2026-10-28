package com.notificationreader;

import android.content.*;
import java.util.concurrent.*;

/** Restores daily one-shot alarms on reboot, upgrade, clock/timezone and exact-access changes. */
public class ReminderReceiver extends BroadcastReceiver {
  private static final ExecutorService worker = Executors.newSingleThreadExecutor();

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) return;
    PendingResult result = goAsync();
    Context c = context.getApplicationContext();
    worker.execute(
        () -> {
          try {
            if (AppReminders.FIRE.equals(intent.getAction())) AppReminders.deliver(c, intent);
            else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                || Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())
                || android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
                    .equals(intent.getAction())) AppReminders.scheduleAll(c);
          } catch (RuntimeException e) {
            // Access can be revoked between checking it and querying usage/posting the
            // notification.
            Rules.prefs(c)
                .edit()
                .putString(
                    "reminder_error",
                    "A reminder could not run. Check Usage access, notifications and alarm"
                        + " permissions.")
                .apply();
          } finally {
            result.finish();
          }
        });
  }
}
