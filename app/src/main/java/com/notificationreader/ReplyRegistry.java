package com.notificationreader;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.util.*;

/** Short-lived, in-memory WhatsApp reply capabilities. Never persisted or opened to other apps. */
final class ReplyRegistry {
  static final long LIFETIME = 15 * 60 * 1000L;
  private static final LinkedHashMap<String, Target> targets = new LinkedHashMap<>();

  static final class Target {
    final String token = UUID.randomUUID().toString();
    final String event, stream, notificationKey, value, label, packageName, preview;
    final PendingIntent intent;
    final RemoteInput input;
    final long created = android.os.SystemClock.elapsedRealtime();
    boolean ready, sending;

    Target(
        String event,
        String stream,
        String key,
        Rules.Rule rule,
        PendingIntent intent,
        RemoteInput input,
        String packageName,
        String preview) {
      this.event = event;
      this.stream = stream;
      notificationKey = key;
      value = rule.value;
      label = rule.label;
      this.intent = intent;
      this.input = input;
      this.packageName = packageName;
      this.preview = preview.substring(0, Math.min(preview.length(), 160));
    }

    int notificationId() {
      return token.hashCode();
    }
  }

  static boolean composing(Context context) {
    return Rules.prefs(context).getLong("reply_composing_until", 0) > System.currentTimeMillis();
  }

  static synchronized void capture(
      Context c,
      android.service.notification.StatusBarNotification sbn,
      Rules.Rule rule,
      String event,
      String stream,
      String preview) {
    revokeStream(c, stream);
    if (!rule.voiceReply || !rule.type.equals("WhatsApp")) return;
    Notification n = sbn.getNotification();
    // An absent group flag is ambiguous; never risk replying to a group on a participant's behalf.
    if (!n.extras.containsKey("android.isGroupConversation")
        || n.extras.getBoolean("android.isGroupConversation")) return;
    if (n.actions == null) return;
    for (Notification.Action action : n.actions) {
      if (action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_REPLY
          || action.actionIntent == null
          || !sbn.getPackageName().equals(action.actionIntent.getCreatorPackage())) continue;
      RemoteInput[] inputs = action.getRemoteInputs();
      if (inputs == null) continue;
      for (RemoteInput input : inputs)
        if (input.getAllowFreeFormInput()) {
          Target target =
              new Target(
                  event,
                  stream,
                  sbn.getKey(),
                  rule,
                  action.actionIntent,
                  input,
                  sbn.getPackageName(),
                  preview);
          targets.put(target.token, target);
          while (targets.size() > 20) revoke(c, targets.keySet().iterator().next());
          return;
        }
    }
  }

  static synchronized Target get(Context c, String token) {
    Target t = targets.get(token);
    if (t == null) return null;
    if (android.os.SystemClock.elapsedRealtime() - t.created > LIFETIME) {
      revoke(c, token);
      return null;
    }
    for (Rules.Rule rule : Rules.load(c))
      if (rule.type.equals("WhatsApp") && rule.value.equals(t.value) && rule.voiceReply) return t;
    revoke(c, token);
    return null;
  }

  static synchronized Target forContact(Context c, String value) {
    Target newest = null;
    for (Target t : new ArrayList<>(targets.values()))
      if (t.value.equals(value)
          && t.ready
          && get(c, t.token) != null
          && (newest == null || t.created > newest.created)) newest = t;
    return newest;
  }

  static synchronized void offerAfterReadout(Context c, String event) {
    if (!Rules.prefs(c).getBoolean("enabled", true) || QuietHours.active(c)) return;
    for (Target t : new ArrayList<>(targets.values())) {
      if (!t.event.equals(event) || get(c, t.token) == null) continue;
      t.ready = true;
      if (android.os.Build.VERSION.SDK_INT >= 33
          && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
              != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
      NotificationManager manager = c.getSystemService(NotificationManager.class);
      manager.createNotificationChannel(
          new NotificationChannel(
              "voice_replies", "WhatsApp voice replies", NotificationManager.IMPORTANCE_LOW));
      Intent open =
          new Intent(c, ReplyActivity.class)
              .putExtra("token", t.token)
              .setData(android.net.Uri.parse("priorityvoice://reply/" + t.token));
      PendingIntent pending =
          PendingIntent.getActivity(
              c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      Notification prompt =
          new Notification.Builder(c, "voice_replies")
              .setSmallIcon(R.drawable.ic_reply)
              .setContentTitle("Reply to " + t.label)
              .setContentText("Tap to dictate, review and confirm a text reply.")
              .setContentIntent(pending)
              .setAutoCancel(true)
              .setOnlyAlertOnce(true)
              .setVisibility(Notification.VISIBILITY_PRIVATE)
              .setTimeoutAfter(LIFETIME)
              .build();
      manager.notify(t.notificationId(), prompt);
      return;
    }
  }

  static synchronized void send(Context c, String token, String draft)
      throws PendingIntent.CanceledException {
    Target t = get(c, token);
    if (t == null || !t.ready || t.sending || draft.trim().isEmpty())
      throw new IllegalStateException("This reply is no longer available. Wait for a new message.");
    if (!Rules.prefs(c).getBoolean("enabled", true) || QuietHours.active(c))
      throw new IllegalStateException("Enable alerts and turn off quiet hours before replying.");
    if (c.getSystemService(KeyguardManager.class).isDeviceLocked())
      throw new IllegalStateException("Unlock your phone before replying.");
    t.sending = true;
    try {
      Bundle results = new Bundle();
      results.putCharSequence(t.input.getResultKey(), draft.trim());
      Intent fill = new Intent();
      RemoteInput.addResultsToIntent(new RemoteInput[] {t.input}, fill, results);
      RemoteInput.setResultsSource(fill, RemoteInput.SOURCE_FREE_FORM_INPUT);
      t.intent.send(c, 0, fill);
      revoke(c, token);
    } finally {
      t.sending = false;
    }
  }

  static synchronized void revokeNotification(Context c, String key) {
    for (Target t : new ArrayList<>(targets.values()))
      if (t.notificationKey.equals(key)) revoke(c, t.token);
  }

  static synchronized void revokeContact(Context c, String value) {
    for (Target t : new ArrayList<>(targets.values()))
      if (t.value.equals(value)) revoke(c, t.token);
  }

  static synchronized void revokeStream(Context c, String stream) {
    for (Target t : new ArrayList<>(targets.values()))
      if (t.stream.equals(stream)) revoke(c, t.token);
  }

  private static void revoke(Context c, String token) {
    Target t = targets.remove(token);
    if (t != null) c.getSystemService(NotificationManager.class).cancel(t.notificationId());
  }

  static synchronized void clear(Context c) {
    for (String token : new ArrayList<>(targets.keySet())) revoke(c, token);
  }
}
