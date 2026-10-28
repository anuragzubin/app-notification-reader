package com.notificationreader;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.*;
import org.json.JSONException;
import org.json.JSONObject;

public class ReaderService extends NotificationListenerService {
  private TextToSpeech tts;
  private boolean ready;
  private String speakingId;
  private boolean callBlocked;
  private boolean quietActive;
  private boolean replyComposing;
  private final Runnable quietTick =
      new Runnable() {
        @Override
        public void run() {
          enforceQuietHours();
          enforceReplyComposition();
          handler.postDelayed(this, 1000);
        }
      };
  private CallMonitor callMonitor;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
      permissionChanged =
          (prefs, key) -> {
            if ("reply_composing_until".equals(key) || "hands_free_composing_until".equals(key))
              this.handler.post(this::enforceReplyComposition);
            if ("enabled".equals(key) && !prefs.getBoolean("enabled", true))
              this.handler.post(this::stopAllAlerts);
            if ("refresh_call_monitor".equals(key) && callMonitor != null) callMonitor.start();
            if (key != null && key.startsWith("quiet_")) this.handler.post(this::enforceQuietHours);
            if ("replay_requested".equals(key)) this.handler.post(this::replayMissed);
            if ("clear_missed_requested".equals(key)) this.handler.post(this::clearMissed);
          };
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final NotificationHistory history = new NotificationHistory();
  private Ringtone ring;
  private long connectedAt;
  private long sequence;
  private final Map<String, Long> latest = new HashMap<>();
  private final Map<String, Rules.Rule> speechRules = new HashMap<>();
  private final Map<String, MissedReadouts.Entry> events = new HashMap<>();
  private final Map<String, MissedReadouts.Entry> utteranceEvents = new HashMap<>();
  private final SpeechBacklog speechBacklog = new SpeechBacklog();

  @Override
  public void onCreate() {
    super.onCreate();
    clearSpeaking();
    restoreHistory("identities", history.identities);
    restoreHistory("newest", history.newest);
    tts =
        new TextToSpeech(
            this,
            s -> {
              ready = s == TextToSpeech.SUCCESS;
              if (ready) {
                handler.post(
                    () -> {
                      replayMissed();
                      speakNext();
                    });
                tts.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
              }
            });
    configureSpeechCallbacks();
    callMonitor =
        new CallMonitor(
            this,
            handler,
            blocked -> {
              callBlocked = blocked;
              if (blocked) {
                rememberQueued();
                speechBacklog.pause();
                tts.stop();
                clearSpeaking();
                if (ring != null) ring.stop();
              } else {
                handler.postDelayed(
                    () -> {
                      replayMissed();
                      speakNext();
                    },
                    500);
              }
            });
    Rules.prefs(this).registerOnSharedPreferenceChangeListener(permissionChanged);
    callMonitor.start();
    quietTick.run();
  }

  private void clearSpeaking() {
    speakingId = null;
    Rules.prefs(this).edit().putLong("reader_speaking_until", 0).apply();
  }

  private void configureSpeechCallbacks() {
    tts.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          @Override
          public void onStart(String id) {
            handler.post(
                () -> {
                  speakingId = id;
                  Rules.prefs(ReaderService.this)
                      .edit()
                      .putLong("reader_speaking_until", System.currentTimeMillis() + 120000)
                      .apply();
                });
          }

          @Override
          public void onDone(String id) {
            finish(id, true);
          }

          @Override
          public void onError(String id) {
            finish(id, false);
          }

          private void finish(String id, boolean success) {
            handler.post(
                () -> {
                  if (id.equals(speakingId)) clearSpeaking();
                  MissedReadouts.Entry event = utteranceEvents.remove(id);
                  if (success && event != null) {
                    MissedStore.remove(ReaderService.this, event.id);
                    ReplyRegistry.offerAfterReadout(ReaderService.this, event.id);
                  }
                  speechBacklog.complete(id);
                  speakNext();
                });
          }
        });
  }

  private void speakNext() {
    if (!ready
        || ReplyRegistry.composing(this)
        || QuietHours.active(this)
        || callBlocked
        || (callMonitor != null && callMonitor.isBlocked())) return;
    SpeechBacklog.Item next = speechBacklog.next();
    if (next == null) return;
    Rules.Rule selected = speechRules.get(next.sender);
    Rules.Rule current = selected == null ? null : currentRule(selected);
    if (!Rules.prefs(this).getBoolean("enabled", true)
        || current == null
        || !current.speech
        || (events.containsKey(next.sender)
            && events.get(next.sender).reaction
            && Rules.prefs(this).getBoolean("mute_reactions", false))
        || !audibleAllowed()) {
      speechBacklog.complete(next.id);
      handler.post(this::speakNext);
      return;
    }
    String text =
        SpeechText.compose(
            current.readSender,
            current.readMessage,
            current.type.equals("WhatsApp") ? "WhatsApp message" : "Email",
            current.label,
            next.text);
    MissedReadouts.Entry event = events.get(next.sender);
    if (event != null) utteranceEvents.put(next.id, event);
    if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, next.id) == TextToSpeech.ERROR) {
      utteranceEvents.remove(next.id);
      speechBacklog.complete(next.id);
      handler.post(this::speakNext);
    }
  }

  @Override
  public void onListenerConnected() {
    if (callMonitor != null) callMonitor.start();
    connectedAt = System.currentTimeMillis();
    // Seed undismissed notifications without playing them, including after an app update.
    StatusBarNotification[] active = getActiveNotifications();
    if (active != null) for (StatusBarNotification n : active) process(n, true);
    if (Rules.prefs(this).getLong("clear_missed_requested", 0)
        != Rules.prefs(this).getLong("clear_missed_handled", 0)) clearMissed();
    replayMissed();
  }

  private String str(Bundle b, String key) {
    CharSequence s = b.getCharSequence(key);
    return s == null ? "" : s.toString();
  }

  @Override
  public void onNotificationPosted(StatusBarNotification sbn) {
    process(sbn, false);
  }

  private void process(StatusBarNotification sbn, boolean baseline) {
    if (!baseline && sbn.getPostTime() < connectedAt) return;
    String pkg = sbn.getPackageName();
    boolean whatsapp = pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b");
    boolean email =
        Arrays.asList(
                "com.google.android.gm",
                "com.samsung.android.email.provider",
                "com.microsoft.office.outlook",
                "com.android.email",
                "eu.faircode.email",
                "net.thunderbird.android",
                "com.fsck.k9")
            .contains(pkg);
    if (!whatsapp && !email) return;
    Notification n = sbn.getNotification();
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || n.extras == null) return;
    Bundle e = n.extras;
    String title = str(e, Notification.EXTRA_TITLE), body = str(e, Notification.EXTRA_TEXT);
    // Expanded email text may contain older undismissed emails; prefer the current text.
    if (body.isEmpty()) body = str(e, Notification.EXTRA_BIG_TEXT);
    List<String> senders = new ArrayList<>();
    senders.add(title);
    long messageTime = email ? Math.max(0, n.when) : 0;
    if (whatsapp) {
      List<Notification.MessagingStyle.Message> messages =
          Notification.MessagingStyle.Message.getMessagesFromBundleArray(
              e.getParcelableArray(Notification.EXTRA_MESSAGES));
      if (!messages.isEmpty()) {
        Notification.MessagingStyle.Message m =
            Collections.max(
                messages,
                Comparator.comparingLong(Notification.MessagingStyle.Message::getTimestamp));
        senders.clear();
        if (android.os.Build.VERSION.SDK_INT >= 28 && m.getSenderPerson() != null) {
          android.app.Person p = m.getSenderPerson();
          if (p.getName() != null) senders.add(p.getName().toString());
          if (p.getUri() != null && p.getUri().startsWith("tel:"))
            senders.add(p.getUri().substring(4));
        } else if (m.getSender() != null) senders.add(m.getSender().toString());
        if (m.getText() != null) body = m.getText().toString();
        messageTime = m.getTimestamp();
      }
    }
    Rules.Rule match = null;
    for (Rules.Rule r : Rules.load(this)) {
      if (!r.type.equals(whatsapp ? "WhatsApp" : "Email")) continue;
      for (String sender : senders)
        if (Rules.matches(r.value, sender)
            || (!r.alias.isEmpty() && Rules.normalize(r.alias).equals(Rules.normalize(sender)))) {
          match = r;
          break;
        }
      if (match != null) break;
    }
    if (match == null || body.trim().isEmpty()) return;
    // Do not include sbn.getKey(): apps can repost old content under a new key.
    // Without a message timestamp, identical email content remains suppressed in the bounded
    // history.
    String conversation = pkg + "|" + Rules.normalize(match.value);
    boolean fresh = history.accept(conversation, body, messageTime);
    persistHistory();
    if (!baseline && fresh) ReplyRegistry.revokeStream(this, conversation);
    if (baseline
        || !fresh
        || !Rules.prefs(this).getBoolean("enabled", true)
        || QuietHours.active(this)) return;
    final boolean isReaction = whatsapp && ReactionDetector.isReaction(body);
    MissedReadouts.Entry event =
        new MissedReadouts.Entry(
            NotificationHistory.hash(conversation + "|" + messageTime + "|" + body),
            conversation,
            match.type,
            match.value,
            body,
            System.currentTimeMillis(),
            isReaction);
    ReplyRegistry.capture(this, sbn, match, event.id, conversation, body);
    // Save immediately, before the sound delay or per-sender queue replacement.
    if (match.speech
        && !(isReaction && Rules.prefs(this).getBoolean("mute_reactions", false))
        && (callBlocked || callMonitor.isBlocked())) MissedStore.add(this, event);
    if (speechBacklog.cancel(conversation)) {
      tts.stop();
      clearSpeaking();
    }
    events.put(conversation, event);
    // Other senders can continue while this sender's newest alert is prepared.
    speakNext();
    final String speech = body;
    final Rules.Rule selected = match;
    final long generation = ++sequence;
    latest.put(conversation, generation);
    final boolean reaction = whatsapp && ReactionDetector.isReaction(body);
    handler.post(
        () -> {
          if (!Rules.prefs(this).getBoolean("enabled", true)
              || QuietHours.active(this)
              || latest.getOrDefault(conversation, 0L) != generation) return;
          Rules.Rule current = currentRule(selected);
          if (current == null) return;
          final Ringtone alertRing;
          if (current.sound && audibleAllowed() && !callMonitor.isBlocked()) {
            if (ring != null) ring.stop();
            ring =
                RingtoneManager.getRingtone(
                    this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            alertRing = ring;
            if (alertRing != null) alertRing.play();
          } else {
            alertRing = null;
          }
          handler.postDelayed(
              () -> {
                if (alertRing != null) alertRing.stop();
                Rules.Rule latest = currentRule(selected);
                if (latest != null
                    && !QuietHours.active(this)
                    && latest.speech
                    && this.latest.getOrDefault(conversation, 0L) == generation
                    && Rules.prefs(this).getBoolean("enabled", true)
                    && !(reaction && Rules.prefs(this).getBoolean("mute_reactions", false))) {
                  if (callBlocked || callMonitor.isBlocked()) MissedStore.add(this, event);
                  speechRules.put(conversation, selected);
                  if (speechBacklog.offer(conversation, speech)) {
                    tts.stop();
                    clearSpeaking();
                  }
                  speakNext();
                }
              },
              current.sound ? 1000 : 0);
        });
  }

  private void rememberQueued() {
    for (SpeechBacklog.Item item : speechBacklog.snapshot()) {
      MissedReadouts.Entry event = events.get(item.sender);
      if (event != null) MissedStore.add(this, event);
    }
    utteranceEvents.clear();
  }

  private void clearMissed() {
    Set<String> ids = new HashSet<>();
    for (MissedReadouts.Entry e : MissedStore.load(this)) ids.add(e.id);
    for (SpeechBacklog.Item item : speechBacklog.snapshot()) {
      MissedReadouts.Entry e = events.get(item.sender);
      if (e != null && ids.contains(e.id) && speechBacklog.cancel(item.sender)) {
        tts.stop();
        clearSpeaking();
      }
    }
    // Invalidate sound-delay callbacks too, so they cannot restore a just-cleared item.
    for (MissedReadouts.Entry e : events.values())
      if (ids.contains(e.id)) latest.put(e.stream, ++sequence);
    utteranceEvents.entrySet().removeIf(entry -> ids.contains(entry.getValue().id));
    MissedStore.clear(this);
    Rules.prefs(this)
        .edit()
        .putLong("clear_missed_handled", Rules.prefs(this).getLong("clear_missed_requested", 0))
        .putLong("replay_handled", Rules.prefs(this).getLong("replay_requested", 0))
        .apply();
    speakNext();
  }

  private void replayMissed() {
    long requested = Rules.prefs(this).getLong("replay_requested", 0);
    if (requested == 0 || requested == Rules.prefs(this).getLong("replay_handled", 0)) return;
    if (!ready
        || QuietHours.active(this)
        || callBlocked
        || (callMonitor != null && callMonitor.isBlocked())
        || !Rules.prefs(this).getBoolean("enabled", true)
        || !audibleAllowed()) return;
    Rules.prefs(this).edit().putLong("replay_handled", requested).apply();
    Set<String> queued = new HashSet<>();
    for (SpeechBacklog.Item item : speechBacklog.snapshot()) {
      MissedReadouts.Entry e = events.get(item.sender);
      if (e != null) queued.add(e.id);
    }
    for (MissedReadouts.Entry e : MissedStore.load(this)) {
      if (queued.contains(e.id)) continue;
      Rules.Rule selected = null;
      for (Rules.Rule rule : Rules.load(this))
        if (rule.type.equals(e.type) && rule.value.equals(e.value)) {
          selected = rule;
          break;
        }
      if (selected == null
          || !selected.speech
          || (e.reaction && Rules.prefs(this).getBoolean("mute_reactions", false))) continue;
      String slot = "missed:" + e.id;
      speechRules.put(slot, selected);
      events.put(slot, e);
      speechBacklog.offer(slot, e.body);
    }
    speakNext();
  }

  private void enforceReplyComposition() {
    boolean composing = ReplyRegistry.composing(this);
    if (composing && !replyComposing) {
      speechBacklog.pause();
      utteranceEvents.clear();
      if (tts != null) tts.stop();
      clearSpeaking();
      if (ring != null) ring.stop();
    }
    boolean resume = replyComposing && !composing;
    replyComposing = composing;
    if (resume) handler.post(this::speakNext);
  }

  private void enforceQuietHours() {
    boolean quiet = QuietHours.active(this);
    if (quiet && !quietActive) {
      stopAllAlerts();
    }
    quietActive = quiet;
  }

  private void stopAllAlerts() {
    ReplyRegistry.clear(this);
    speechBacklog.clear();
    utteranceEvents.clear();
    latest.replaceAll((sender, generation) -> ++sequence);
    if (tts != null) tts.stop();
    clearSpeaking();
    if (ring != null) ring.stop();
    Rules.prefs(this)
        .edit()
        .putLong("replay_handled", Rules.prefs(this).getLong("replay_requested", 0))
        .apply();
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification sbn) {
    ReplyRegistry.revokeNotification(this, sbn.getKey());
  }

  private boolean audibleAllowed() {
    AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    return !ReplyRegistry.composing(this)
        && !QuietHours.active(this)
        && audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
        && nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL;
  }

  private Rules.Rule currentRule(Rules.Rule selected) {
    for (Rules.Rule r : Rules.load(this))
      if (r.type.equals(selected.type) && r.value.equals(selected.value)) return r;
    return null;
  }

  private void restoreHistory(String key, Map<String, Long> target) {
    try {
      JSONObject object =
          new JSONObject(
              getSharedPreferences("notification_history", MODE_PRIVATE).getString(key, "{}"));
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String id = keys.next();
        target.put(id, object.getLong(id));
      }
    } catch (JSONException ignored) {
    }
  }

  private void persistHistory() {
    getSharedPreferences("notification_history", MODE_PRIVATE)
        .edit()
        .putString("identities", new JSONObject(history.identities).toString())
        .putString("newest", new JSONObject(history.newest).toString())
        .apply();
  }

  @Override
  public void onDestroy() {
    Rules.prefs(this).unregisterOnSharedPreferenceChangeListener(permissionChanged);
    if (callMonitor != null) callMonitor.close();
    handler.removeCallbacksAndMessages(null);
    if (ring != null) ring.stop();
    clearSpeaking();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }
}
