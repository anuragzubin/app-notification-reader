package com.notificationreader;

import android.content.*;
import android.content.pm.*;
import java.util.*;

final class InstalledApps {
  static Intent launcherQuery() {
    return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  }

  static List<ResolveInfo> launchers(Context c) {
    return c.getPackageManager().queryIntentActivities(launcherQuery(), 0);
  }

  static String packageFor(Context c, AppCatalog.App app) {
    PackageManager pm = c.getPackageManager();
    String saved = Rules.prefs(c).getString("app_package:" + app.id, "");
    if (!saved.isEmpty()) return pm.getLaunchIntentForPackage(saved) == null ? null : saved;
    if (!app.defaultPackage.isEmpty() && pm.getLaunchIntentForPackage(app.defaultPackage) != null)
      return app.defaultPackage;
    // Resolve a single exact installed label rather than guessing an unverified package ID.
    Set<String> matches = new HashSet<>();
    for (ResolveInfo info : launchers(c))
      if (VoiceCommand.normalize(info.loadLabel(pm).toString()).equals(app.id))
        matches.add(info.activityInfo.packageName);
    return matches.size() == 1 ? matches.iterator().next() : null;
  }
}
