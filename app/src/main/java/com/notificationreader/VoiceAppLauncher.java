package com.notificationreader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import java.util.*;

/** Explicit allowlist only. Chat links open a composer; they never send a message. */
final class VoiceAppLauncher {
  static String open(Context context, String target, boolean floatingVisible) {
    String app = target;
    String contact = null;
    if (target.startsWith("whatsapp chat with ")) {
      app = "whatsapp";
      contact = target.substring("whatsapp chat with ".length());
    } else if (target.startsWith("whatsapp chat of ")) {
      app = "whatsapp";
      contact = target.substring("whatsapp chat of ".length());
    }
    String packageName;
    switch (app) {
      case "spotify":
        packageName = "com.spotify.music";
        break;
      case "whatsapp":
        packageName = "com.whatsapp";
        break;
      case "gmail":
        packageName = "com.google.android.gm";
        break;
      default:
        return "You can open Spotify, WhatsApp, Gmail, or WhatsApp chat with a saved contact.";
    }
    if (!Rules.prefs(context).getBoolean("open_" + app, true))
      return "Opening " + app + " is disabled in Settings.";
    Intent intent;
    if (contact != null) {
      List<Rules.Rule> matches = new ArrayList<>();
      for (Rules.Rule rule : Rules.load(context)) {
        if (rule.type.equals("WhatsApp")
            && (VoiceCommand.normalize(rule.label).equals(contact)
                || VoiceCommand.normalize(rule.value).equals(contact))) matches.add(rule);
      }
      if (matches.size() != 1)
        return matches.isEmpty()
            ? "No saved WhatsApp contact matched that name."
            : "That name matches multiple contacts. Give them unique names in the app.";
      String phone =
          VoiceCommand.internationalPhone(
              Rules.prefs(context)
                  .getString("chat_number:" + matches.get(0).value, matches.get(0).value));
      if (phone == null)
        return "Set this contact's WhatsApp chat number in Senders, starting with plus and country"
            + " code. A display name alone cannot open their WhatsApp chat.";
      intent =
          new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone))
              .setPackage(packageName);
    } else {
      intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
      if (intent == null) return "Please install " + app + " first.";
    }
    // A package-scoped WhatsApp deep link is attempted directly; a preflight resolver
    // can reject valid links due to device-specific package visibility/default rules.
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      MainActivity visible = MainActivity.visibleActivity;
      if (visible != null) {
        visible.startActivity(intent);
        return "Opening " + app + ".";
      }
      // Android 15+ permits background launches with an actually visible permitted overlay.
      if (floatingVisible && android.provider.Settings.canDrawOverlays(context)) {
        postShortcut(context, intent, app);
        context.startActivity(intent);
        return "Opening " + app + ".";
      }
    } catch (ActivityNotFoundException | SecurityException e) {
      // Always leave an explicit user-initiated route available.
    }
    postShortcut(context, intent, app);
    return "Tap the Open "
        + app
        + " notification to continue. Allow the floating control for hands-free opening.";
  }

  static void postShortcut(Context context, Intent intent, String app) {
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "open_apps", "Voice app opening", NotificationManager.IMPORTANCE_DEFAULT));
    PendingIntent open =
        PendingIntent.getActivity(
            context,
            52002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    manager.notify(
        52002,
        new Notification.Builder(context, "open_apps")
            .setSmallIcon(R.drawable.ic_reply)
            .setContentTitle("Open " + app)
            .setContentText("Tap to open your requested app or chat.")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build());
  }
}
