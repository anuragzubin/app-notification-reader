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
  private long wakeUntil;
  private boolean listenScheduled, systemRecognizer, wakeAcknowledgement;
  private int recognizerGeneration;
  private final WakeEvidence wakeEvidence = new WakeEvidence();
  private android.view.View floatingControl;

  private void showFloatingControl() {
    if (!android.provider.Settings.canDrawOverlays(this)) return;
    android.widget.Button button = new android.widget.Button(this);
    button.setText("Voice • Stop");
    button.setContentDescription("Stop voice listening");
    button.setOnClickListener(v -> stopSelf());
    android.view.WindowManager.LayoutParams params =
        new android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
    params.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
    try {
      getSystemService(android.view.WindowManager.class).addView(button, params);
      floatingControl = button;
    } catch (SecurityException | android.view.WindowManager.BadTokenException e) {
      floatingControl = null;
    }
  }

  private void hideFloatingControl() {
    if (floatingControl == null) return;
    try {
      getSystemService(android.view.WindowManager.class).removeView(floatingControl);
    } catch (IllegalArgumentException ignored) {
    }
    floatingControl = null;
  }

  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener readoutChanged =
      (prefs, key) -> {
        if ("reader_speaking_until".equals(key) && readerSpeaking()) {
          wakeUntil = 0;
          wakeAcknowledgement = false;
          releaseMicrophone();
        }
        if ("reply_offer_token".equals(key)) collectOffer();
        if ("reply_route_revision".equals(key)) discardInvalidOffers();
        if ("auto_reply_offer".equals(key) && !prefs.getBoolean("auto_reply_offer", true)) {
          offers.clear();
          if (flow.stage == HandsFreeFlow.Stage.OFFER) cancelConversation();
        }
      };
  private long lastResult, renew, turnDeadline;
  private final String audioOwner = "hands-free:" + UUID.randomUUID();
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (closed) return;
          boolean allowed = allowed();
          if (readerSpeaking() && listening) releaseMicrophone();
          if (!allowed && !paused) {
            paused = true;
            wakeUntil = 0;
            wakeAcknowledgement = false;
            hideFloatingControl();
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
            showFloatingControl();
            notice("Listening · say Send text to a listed contact.");
            scheduleListen(700);
          }
          if ((speaking || flow.stage != HandsFreeFlow.Stage.COMMAND)
              && SystemClock.elapsedRealtime() > turnDeadline
              && turnDeadline > 0) {
            cancelConversation();
            notice("Reply timed out. Notification readouts are available again.");
          }
          if ((speaking || flow.stage != HandsFreeFlow.Stage.COMMAND)
              && SystemClock.elapsedRealtime() > renew) hold(true);
          if (listening && SystemClock.elapsedRealtime() - lastResult > 30000) {
            cancelRecognition();
            flow.reset();
            prompt("Reply canceled because no response was heard.");
          }
          if (floatingControl != null
              && !android.provider.Settings.canDrawOverlays(HandsFreeService.this))
            hideFloatingControl();
          if (allowed && !paused) askAfterReadouts();
          if (allowed && !paused && !speaking && !listening && !listenScheduled && ready)
            scheduleListen(500);
          handler.postDelayed(this, 500);
        }
      };
  private final Runnable listenTask =
      () -> {
        listenScheduled = false;
        listen();
      };

  private void cancelConversation() {
    wakeAcknowledgement = false;
    wakeUntil = 0;
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
    wakeUntil = 0;
    flow.offer(target.token, target.label);
    prompt("Do you want to reply to " + target.label + "? Say Yes or No.");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Rules.prefs(this).edit().remove("hands_free_error").remove("last_recognition_status").apply();
    systemRecognizer = !Rules.prefs(this).getBoolean("prefer_device_recognition", false);
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
    if (allowed()) showFloatingControl();
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      fail("No speech recognition provider is installed. Check Android voice input settings.");
      return;
    }
    createRecognizer();
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
                      + " Say Yes to reply, then Yes after the draft readback to send. "
                      + (Rules.prefs(this).getBoolean("wake_enabled", false)
                          ? "For new commands say "
                              + Rules.prefs(this).getString("wake_phrase", "Hey Priority")
                              + ". "
                          : "")
                      + "Say Stop"
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
    turnDeadline = SystemClock.elapsedRealtime() + 30000;
    if (wakeAcknowledgement) {
      wakeAcknowledgement = false;
      wakeUntil = SystemClock.elapsedRealtime() + 15000;
    }
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
        || AudioSessions.busyExcept(audioOwner)) return false;
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
    listenScheduled = !closed;
    if (!closed) handler.postDelayed(listenTask, delay);
  }

  private void listen() {
    if (closed || !ready || speaking || listening || !allowed() || readerSpeaking()) return;
    Intent speech =
        new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    try {
      wakeEvidence.reset();
      listening = true;
      lastResult = SystemClock.elapsedRealtime();
      if (recognizer == null) createRecognizer();
      recognizer.startListening(speech);
    } catch (SecurityException | IllegalStateException e) {
      listening = false;
      fail("Microphone unavailable. Start hands-free mode again from the app.");
    }
  }

  private void createRecognizer() {
    try {
      if (!systemRecognizer
          && Build.VERSION.SDK_INT >= 31
          && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
      else recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    } catch (UnsupportedOperationException e) {
      recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }
    final int generation = ++recognizerGeneration;
    recognizer.setRecognitionListener(
        new RecognitionListener() {
          private boolean current() {
            return generation == recognizerGeneration && !closed;
          }

          public void onResults(Bundle b) {
            if (current()) HandsFreeService.this.onResults(b);
          }

          public void onError(int e) {
            if (current()) HandsFreeService.this.onError(e);
          }

          public void onReadyForSpeech(Bundle b) {
            if (current()) HandsFreeService.this.onReadyForSpeech(b);
          }

          public void onBeginningOfSpeech() {}

          public void onRmsChanged(float f) {}

          public void onBufferReceived(byte[] b) {}

          public void onEndOfSpeech() {}

          public void onPartialResults(Bundle b) {
            if (current()) HandsFreeService.this.onPartialResults(b);
          }

          public void onEvent(int t, Bundle b) {}
        });
  }

  private void releaseMicrophone() {
    recognizerGeneration++;
    cancelRecognition();
    if (recognizer != null) {
      recognizer.destroy();
      recognizer = null;
    }
  }

  private void cancelRecognition() {
    handler.removeCallbacks(listenTask);
    listenScheduled = false;
    wakeEvidence.reset();
    listening = false;
    if (recognizer != null) recognizer.cancel();
  }

  private void prompt(String words) {
    cancelRecognition();
    if (!ready || !allowed()) {
      wakeAcknowledgement = false;
      flow.reset();
      hold(false);
      return;
    }
    hold(true);
    speaking = true;
    turnDeadline =
        SystemClock.elapsedRealtime() + Math.min(120000, Math.max(15000, words.length() * 100L));
    promptId = UUID.randomUUID().toString();
    notice("Speaking · wait for the spoken prompt to finish before answering.");
    final String id = promptId;
    ReadoutHandoff.start(
        handler::postDelayed,
        this::releaseMicrophone,
        () -> !closed && ready && id.equals(promptId) && allowed(),
        () -> {
          if (tts.speak(words, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
            speaking = false;
            hold(false);
            stopSelf();
          }
        });
  }

  private void hold(boolean active) {
    renew = SystemClock.elapsedRealtime() + 10000;
    if (active) AudioSessions.hold(this, audioOwner);
    else AudioSessions.release(this, audioOwner);
  }

  private void handle(String words) {
    if (VoiceCommand.stop(words)) {
      stopSelf();
      return;
    }
    if (flow.stage == HandsFreeFlow.Stage.COMMAND
        && (Rules.prefs(this).getBoolean("wake_enabled", false)
            || VoiceCommand.afterWake(
                    words, Rules.prefs(this).getString("wake_phrase", "Hey Priority"))
                != null)) {
      String command =
          VoiceCommand.afterWake(words, Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
      if (command != null) {
        recognitionStatus("Wake phrase matched.");
        words = command;
        wakeUntil = SystemClock.elapsedRealtime() + 15000;
        if (command.isEmpty()) {
          wakeAcknowledgement = true;
          prompt("I'm listening. What would you like to open or do?");
          return;
        }
      } else if (SystemClock.elapsedRealtime() >= wakeUntil) {
        recognitionStatus(
            "Words received, but wake phrase not matched. Use Test wake phrase to see what Android"
                + " hears.");
        notice(
            "Speech detected, but wake phrase not matched. Say "
                + Rules.prefs(this).getString("wake_phrase", "Hey Priority")
                + ".");
        scheduleListen(650);
        return;
      }
      wakeUntil = 0;
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
      String open = VoiceCommand.openTarget(words);
      if (open != null) {
        releaseMicrophone();
        // Leave a tap fallback even if an OEM silently blocks the overlay launch.
        String result =
            VoiceAppLauncher.open(this, open, floatingControl != null && floatingControl.isShown());
        Rules.prefs(this).edit().putString("last_launch_status", result).apply();
        prompt(result);
        return;
      }
      String spoken = VoiceCommand.recipient(words);
      if (spoken == null) {
        notice("Command not understood. Try Open WhatsApp, Open Gmail, or Open Spotify.");
        prompt(
            "I didn't understand the command. Please say the wake phrase and Open WhatsApp, Open"
                + " Gmail, or Open Spotify.");
        return;
      }
      if (spoken.isEmpty()) {
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
    boolean partialWake = wakeEvidence.consume();
    releaseMicrophone();
    ArrayList<String> text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (text == null || text.isEmpty()) {
      if (partialWake && flow.stage == HandsFreeFlow.Stage.COMMAND) {
        handle(Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
        return;
      }
      recognitionStatus("The speech provider returned no words.");
      scheduleListen(800);
      return;
    }
    handle(
        flow.stage == HandsFreeFlow.Stage.COMMAND
            ? VoiceCommand.selectCommand(
                text,
                Rules.prefs(this).getString("wake_phrase", "Hey Priority"),
                Rules.prefs(this).getBoolean("wake_enabled", false),
                SystemClock.elapsedRealtime() < wakeUntil)
            : text.get(0));
  }

  @Override
  public void onError(int error) {
    if (!listening || closed) return;
    boolean partialWake = wakeEvidence.consume();
    releaseMicrophone();
    if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        && partialWake
        && flow.stage == HandsFreeFlow.Stage.COMMAND
        && allowed()
        && !readerSpeaking()) {
      // Final transcription failed. Only acknowledge; never execute a partial app/send command.
      handle(Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
      return;
    }
    recognitionStatus(recognitionError(error));
    if (!systemRecognizer
        && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
            || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            || error == SpeechRecognizer.ERROR_SERVER
            || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
      systemRecognizer = true;
      notice(
          "On-device recognition unavailable for this language. Trying your system speech"
              + " provider.");
      scheduleListen(1000);
      return;
    }
    notice("Speech recognition: " + recognitionError(error));
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
  public void onReadyForSpeech(Bundle b) {
    notice(
        flow.stage != HandsFreeFlow.Stage.COMMAND
            ? "Listening for your reply or confirmation."
            : Rules.prefs(this).getBoolean("wake_enabled", false)
                    && SystemClock.elapsedRealtime() >= wakeUntil
                ? "Ready · say "
                    + Rules.prefs(this).getString("wake_phrase", "Hey Priority")
                    + ", open WhatsApp."
                : "Ready · say Open WhatsApp, Open Gmail, or Open Spotify.");
  }

  private String recognitionError(int error) {
    switch (error) {
      case SpeechRecognizer.ERROR_NO_MATCH:
        return "No words recognized; listening again.";
      case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
        return "No speech heard; listening again.";
      case SpeechRecognizer.ERROR_AUDIO:
        return "Microphone audio error.";
      case SpeechRecognizer.ERROR_NETWORK:
      case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
        return "Speech provider network error.";
      case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
      case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
        return "English India speech model unavailable. Check voice input languages.";
      case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
        return "Speech provider busy; retrying.";
      default:
        return "Provider error " + error + ".";
    }
  }

  @Override
  public void onBeginningOfSpeech() {}

  @Override
  public void onRmsChanged(float f) {}

  @Override
  public void onBufferReceived(byte[] b) {}

  @Override
  public void onEndOfSpeech() {}

  @Override
  public void onPartialResults(Bundle b) {
    if (listening
        && !speaking
        && flow.stage == HandsFreeFlow.Stage.COMMAND
        && allowed()
        && !readerSpeaking())
      wakeEvidence.partial(
          b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
          Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
  }

  private void recognitionStatus(String message) {
    Rules.prefs(this).edit().putString("last_recognition_status", message).apply();
  }

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
    Rules.prefs(this).edit().putString("hands_free_status", words).apply();
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
    hideFloatingControl();
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
