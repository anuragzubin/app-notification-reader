package com.notificationreader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import java.util.*;

/** User-started microphone foreground session. Never starts listening from a background readout. */
public class HandsFreeService extends Service implements RecognitionListener {
  static final String STOP = "com.notificationreader.STOP_HANDS_FREE";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private SpeechRecognizer recognizer;
  private TextToSpeech tts;
  private CallMonitor calls;
  private final HandsFreeFlow flow = new HandsFreeFlow();
  private final ReplyOfferQueue offers = new ReplyOfferQueue();
  private boolean ready, listening, speaking, paused, closed;
  private int errors, attempts;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener readoutChanged =
      (prefs, key) -> {
        if ("reader_speaking_until".equals(key) && readerSpeaking()) cancelRecognition();
        if ("reply_offer_token".equals(key)) collectOffer();
        if ("reply_route_revision".equals(key)) discardInvalidOffers();
        if ("auto_reply_offer".equals(key) && !prefs.getBoolean("auto_reply_offer", true)) {
          offers.clear();
          if (flow.stage == HandsFreeFlow.Stage.OFFER) cancelConversation();
        }
      };
  private long lastResult, renew;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (closed) return;
          boolean allowed = allowed();
          if (readerSpeaking() && listening) cancelRecognition();
          if (!allowed && !paused) {
            paused = true;
            cancelRecognition();
            promptId = null;
            if (tts != null) tts.stop();
            speaking = false;
            flow.reset();
            offers.clear();
            hold(false);
            notice("Paused · unlock your phone, end calls and enable alerts outside quiet hours.");
          }
          if (allowed && paused) {
            paused = false;
            notice("Listening · say Send text to a listed contact.");
            scheduleListen(700);
          }
          if (flow.stage != HandsFreeFlow.Stage.COMMAND && System.currentTimeMillis() > renew)
            hold(true);
          if (listening && SystemClock.elapsedRealtime() - lastResult > 30000) {
            cancelRecognition();
            flow.reset();
            prompt("Reply canceled because no response was heard.");
          }
          if (allowed && !paused) askAfterReadouts();
          if (allowed && !paused && !speaking && !listening && ready) scheduleListen(500);
          handler.postDelayed(this, 500);
        }
      };
  private final Runnable listenTask = this::listen;

  private void cancelConversation() {
    cancelRecognition();
    if (tts != null) tts.stop();
    promptId = null;
    speaking = false;
    flow.reset();
    hold(false);
  }

  private void collectOffer() {
    if (!Rules.prefs(this).getBoolean("auto_reply_offer", true) || !allowed()) return;
    String token = Rules.prefs(this).getString("reply_offer_token", "");
    ReplyRegistry.Target target = ReplyRegistry.get(this, token);
    if (target != null && target.ready)
      offers.add(target.stream, target.token, SystemClock.elapsedRealtime());
  }

  private void discardInvalidOffers() {
    for (ReplyOfferQueue.Offer offer : offers.snapshot())
      if (ReplyRegistry.get(this, offer.token) == null) offers.remove(offer.token);
    if (flow.token != null && ReplyRegistry.get(this, flow.token) == null) cancelConversation();
  }

  private void askAfterReadouts() {
    if (!ready
        || !allowed()
        || readerSpeaking()
        || speaking
        || flow.stage != HandsFreeFlow.Stage.COMMAND
        || !Rules.prefs(this).getBoolean("auto_reply_offer", true)) return;
    discardInvalidOffers();
    ReplyOfferQueue.Offer next = offers.take(SystemClock.elapsedRealtime());
    if (next == null) return;
    ReplyRegistry.Target target = ReplyRegistry.get(this, next.token);
    if (target == null || !target.ready) return;
    attempts = 0;
    flow.offer(target.token, target.label);
    prompt("Do you want to reply to " + target.label + "? Say Yes or No.");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Rules.prefs(this).edit().remove("hands_free_error").apply();
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "hands_free", "Hands-free microphone", NotificationManager.IMPORTANCE_LOW));
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      stopSelf();
      return;
    }
    try {
      startForeground(
          51001,
          notification("Starting hands-free listening…"),
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    } catch (SecurityException | IllegalStateException e) {
      stopSelf();
      return;
    }
    Rules.prefs(this).registerOnSharedPreferenceChangeListener(readoutChanged);
    calls =
        new CallMonitor(
            this,
            handler,
            busy -> {
              if (busy) {
                cancelRecognition();
                promptId = null;
                if (tts != null) tts.stop();
                speaking = false;
                flow.reset();
                offers.clear();
                hold(false);
              }
            });
    calls.start();
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      fail("No speech recognition provider is installed. Check Android voice input settings.");
      return;
    }
    try {
      if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
      else recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    } catch (UnsupportedOperationException e) {
      recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }
    recognizer.setRecognitionListener(this);
    tts =
        new TextToSpeech(
            this,
            status -> {
              ready = status == TextToSpeech.SUCCESS;
              if (!ready) {
                fail("Speech output is unavailable. Check Android text-to-speech settings.");
                return;
              }
              tts.setAudioAttributes(
                  new android.media.AudioAttributes.Builder()
                      .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                      .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                      .build());
              prompt(
                  "Hands free is ready. After WhatsApp readouts I will ask if you want to reply."
                      + " Say Yes to reply, then Yes after the draft readback to send. Say Stop"
                      + " listening to finish.");
            });
    tts.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          public void onStart(String id) {}

          public void onDone(String id) {
            handler.post(() -> finishPrompt(id));
          }

          public void onError(String id) {
            handler.post(
                () -> {
                  if (!id.equals(promptId) || closed) return;
                  speaking = false;
                  flow.reset();
                  hold(false);
                  fail("Speech output failed. Check Android text-to-speech settings.");
                });
          }
        });
    Rules.prefs(this).edit().putBoolean("hands_free_running", true).apply();
    handler.post(tick);
  }

  private String promptId;

  private void finishPrompt(String id) {
    if (!id.equals(promptId) || closed) return;
    speaking = false;
    if (flow.stage == HandsFreeFlow.Stage.COMMAND) hold(false);
    scheduleListen(650);
  }

  private boolean allowed() {
    if (calls == null
        || calls.isBlocked()
        || getSystemService(KeyguardManager.class).isDeviceLocked()
        || QuietHours.active(this)
        || !Rules.prefs(this).getBoolean("enabled", true)) return false;
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        || Rules.prefs(this).getLong("reply_composing_until", 0) > System.currentTimeMillis())
      return false;
    AudioManager a = getSystemService(AudioManager.class);
    return a.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
        && getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
            == NotificationManager.INTERRUPTION_FILTER_ALL;
  }

  private boolean readerSpeaking() {
    return Rules.prefs(this).getLong("reader_speaking_until", 0) > System.currentTimeMillis();
  }

  private void scheduleListen(long delay) {
    handler.removeCallbacks(listenTask);
    if (!closed) handler.postDelayed(listenTask, delay);
  }

  private void listen() {
    if (closed || !ready || speaking || listening || !allowed() || readerSpeaking()) return;
    Intent speech =
        new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
    try {
      listening = true;
      lastResult = SystemClock.elapsedRealtime();
      recognizer.startListening(speech);
    } catch (SecurityException | IllegalStateException e) {
      listening = false;
      fail("Microphone unavailable. Start hands-free mode again from the app.");
    }
  }

  private void cancelRecognition() {
    handler.removeCallbacks(listenTask);
    listening = false;
    if (recognizer != null) recognizer.cancel();
  }

  private void prompt(String words) {
    cancelRecognition();
    if (!ready || !allowed()) {
      flow.reset();
      hold(false);
      return;
    }
    hold(true);
    speaking = true;
    promptId = UUID.randomUUID().toString();
    notice(words);
    if (tts.speak(words, TextToSpeech.QUEUE_FLUSH, null, promptId) == TextToSpeech.ERROR) {
      speaking = false;
      hold(false);
      stopSelf();
    }
  }

  private void hold(boolean active) {
    renew = System.currentTimeMillis() + 30000;
    Rules.prefs(this)
        .edit()
        .putLong("hands_free_composing_until", active ? System.currentTimeMillis() + 120000 : 0)
        .apply();
  }

  private void handle(String words) {
    if (VoiceCommand.stop(words)) {
      stopSelf();
      return;
    }
    if (VoiceCommand.cancel(words)) {
      flow.reset();
      prompt("Canceled. Say Send text to a contact, or Stop listening.");
      return;
    }
    if (flow.stage == HandsFreeFlow.Stage.OFFER) {
      if (VoiceCommand.no(words)) {
        flow.reset();
        prompt("Okay. No reply sent.");
        return;
      }
      if (flow.acceptOffer(words)) {
        attempts = 0;
        prompt("What would you like to say to " + flow.name + "?");
        return;
      }
      if (++attempts >= 3) {
        flow.reset();
        prompt("No reply sent.");
      } else prompt("Do you want to reply? Say Yes or No.");
      return;
    }
    if (flow.stage == HandsFreeFlow.Stage.COMMAND) {
      String spoken = VoiceCommand.recipient(words);
      if (spoken == null) {
        scheduleListen(650);
        return;
      }
      List<Rules.Rule> matches = new ArrayList<>();
      for (Rules.Rule r : Rules.load(this))
        if (r.type.equals("WhatsApp")
            && r.voiceReply
            && (VoiceCommand.normalize(r.label).equals(spoken)
                || VoiceCommand.normalize(r.value).equals(spoken))) matches.add(r);
      if (matches.size() != 1) {
        prompt(
            matches.isEmpty()
                ? "No enabled WhatsApp contact matched. Use a saved contact name with Reply by"
                    + " voice enabled."
                : "That name matches more than one contact. Give them unique spoken names in the"
                    + " app.");
        return;
      }
      Rules.Rule rule = matches.get(0);
      ReplyRegistry.Target target = ReplyRegistry.forContact(this, rule.value);
      if (target == null) {
        prompt(
            "WhatsApp has no live Reply action for "
                + rule.label
                + ". I cannot send a new chat message through WhatsApp's notification interface."
                + " Wait for a new direct message and readout, then try again.");
        return;
      }
      offers.remove(target.token);
      flow.begin(target.token, rule.label);
      attempts = 0;
      prompt("What would you like to say to " + flow.name + "?");
      return;
    }
    if (flow.stage == HandsFreeFlow.Stage.MESSAGE) {
      if (!flow.dictate(words)) {
        flow.reset();
        prompt("No valid message was captured. Please start again.");
        return;
      }
      attempts = 0;
      prompt(
          "Text to "
              + flow.name
              + ". "
              + flow.message
              + ". Is that correct? Say Yes to send, or No to record your message again.");
      return;
    }
    if (VoiceCommand.no(words)) {
      flow.rerecord();
      attempts = 0;
      prompt("Please say your message again.");
      return;
    }
    if (!(VoiceCommand.yes(words) || VoiceCommand.send(words))) {
      if (++attempts >= 3) {
        flow.reset();
        prompt("Canceled. Please start again.");
      } else prompt("Is that correct? Say Yes to send, No to record again, or Cancel.");
      return;
    }
    String token = flow.token, message = flow.message;
    if (!allowed() || readerSpeaking() || !flow.confirm(words)) {
      flow.reset();
      prompt("Reply canceled. Please try again when available.");
      return;
    }
    try {
      ReplyRegistry.send(this, token, message);
      flow.reset();
      prompt("Reply handed to WhatsApp. You can say Send text to another listed contact.");
    } catch (PendingIntent.CanceledException | IllegalStateException | SecurityException e) {
      flow.reset();
      prompt(
          "The WhatsApp Reply action changed or expired. No successful handoff was confirmed."
              + " Please wait for a new message.");
    }
  }

  @Override
  public void onResults(Bundle results) {
    if (!listening || speaking || closed) return;
    listening = false;
    if (!allowed() || readerSpeaking()) {
      flow.reset();
      hold(false);
      return;
    }
    errors = 0;
    ArrayList<String> text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (text == null || text.isEmpty()) {
      scheduleListen(800);
      return;
    }
    handle(text.get(0));
  }

  @Override
  public void onError(int error) {
    if (!listening || closed) return;
    listening = false;
    if (flow.stage != HandsFreeFlow.Stage.COMMAND
        && (error == SpeechRecognizer.ERROR_NO_MATCH
            || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        && ++attempts >= 3) {
      flow.reset();
      prompt("Reply canceled because no response was heard.");
      return;
    }
    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        || error == SpeechRecognizer.ERROR_CLIENT) {
      fail("Microphone session stopped. Open the app to restart it.");
      return;
    }
    if (error != SpeechRecognizer.ERROR_NO_MATCH
        && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        && ++errors >= 5) {
      fail("Speech recognition is unavailable. Check voice input and restart hands-free mode.");
      return;
    }
    scheduleListen(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 2000 : 1000);
  }

  @Override
  public void onReadyForSpeech(Bundle b) {}

  @Override
  public void onBeginningOfSpeech() {}

  @Override
  public void onRmsChanged(float f) {}

  @Override
  public void onBufferReceived(byte[] b) {}

  @Override
  public void onEndOfSpeech() {}

  @Override
  public void onPartialResults(Bundle b) {}

  @Override
  public void onEvent(int type, Bundle b) {}

  private Notification notification(String words) {
    Intent stop = new Intent(this, HandsFreeService.class).setAction(STOP);
    PendingIntent action =
        PendingIntent.getService(
            this, 0, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new Notification.Builder(this, "hands_free")
        .setSmallIcon(R.drawable.ic_reply)
        .setContentTitle("Hands-free voice commands")
        .setContentText(words)
        .setStyle(new Notification.BigTextStyle().bigText(words))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setContentIntent(open)
        .addAction(new Notification.Action.Builder(null, "Stop listening", action).build())
        .build();
  }

  private void fail(String reason) {
    Rules.prefs(this).edit().putString("hands_free_error", reason).apply();
    notice(reason);
    stopSelf();
  }

  private void notice(String words) {
    getSystemService(NotificationManager.class).notify(51001, notification(words));
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent != null && STOP.equals(intent.getAction())) stopSelf();
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    closed = true;
    Rules.prefs(this).unregisterOnSharedPreferenceChangeListener(readoutChanged);
    handler.removeCallbacksAndMessages(null);
    cancelRecognition();
    if (recognizer != null) recognizer.destroy();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    if (calls != null) calls.close();
    hold(false);
    Rules.prefs(this).edit().putBoolean("hands_free_running", false).apply();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
