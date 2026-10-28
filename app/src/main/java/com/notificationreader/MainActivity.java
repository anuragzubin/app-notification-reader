package com.notificationreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.util.List;

public class MainActivity extends Activity {
  static MainActivity visibleActivity;
  private LinearLayout layout, list, navigation;
  private TextToSpeech tts;
  private boolean speechReady;
  private final android.os.Handler quietUiHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable quietUiTick =
      new Runnable() {
        @Override
        public void run() {
          if (QuietHours.active(MainActivity.this)) {
            if (tts != null) tts.stop();
            if (testRing != null) testRing.stop();
          }
          quietUiHandler.postDelayed(this, 1000);
        }
      };
  private CallMonitor testCallMonitor;
  private android.media.Ringtone testRing;
  private int page;
  private String query = "";
  private TextView missedCount, handsFreeStatus, readoutHealth;
  private Button missedPlay;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener missedChanged =
      (prefs, key) -> {
        if (MissedStore.KEY.equals(key)) updateMissedCount();
        if ("reader_heartbeat".equals(key)
            || "readout_status".equals(key)
            || "last_notification_check".equals(key)) updateReadoutHealth();
        if ("hands_free_running".equals(key)
            || "hands_free_error".equals(key)
            || "hands_free_status".equals(key)
            || "last_launch_status".equals(key)
            || "last_recognition_status".equals(key)) updateHandsFreeStatus();
      };

  private int pad(int x) {
    return Math.round(x * getResources().getDisplayMetrics().density);
  }

  private int color(int id) {
    return getColor(id);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private GradientDrawable shape(int fill, int radius, boolean border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(pad(radius));
    if (border) d.setStroke(pad(1), color(R.color.line));
    return d;
  }

  private TextView label(String value, int size, int ink, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color(ink));
    t.setFontFeatureSettings("kern");
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setLineSpacing(pad(3), 1f);
    return t;
  }

  private void gap(LinearLayout parent, int height) {
    View v = new View(this);
    parent.addView(v, new LinearLayout.LayoutParams(1, pad(height)));
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column();
    c.setPadding(pad(20), pad(20), pad(20), pad(20));
    c.setBackground(shape(color(R.color.surface), 24, true));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = pad(14);
    parent.addView(c, lp);
    return c;
  }

  private void copy(LinearLayout parent, String title, String detail) {
    parent.addView(label(title, 18, R.color.ink, true));
    gap(parent, 6);
    parent.addView(label(detail, 14, R.color.muted, false));
  }

  private void section(String heading) {
    gap(layout, 12);
    layout.addView(label(heading, 20, R.color.ink, true));
    gap(layout, 14);
  }

  private Button action(
      LinearLayout parent, String title, boolean primary, View.OnClickListener clicked) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setTextColor(color(primary ? R.color.on_accent : R.color.accent));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(color(R.color.line)),
            shape(color(primary ? R.color.accent : R.color.soft), 16, false),
            null));
    b.setMinHeight(pad(52));
    b.setMinimumHeight(pad(52));
    b.setPadding(pad(16), pad(12), pad(16), pad(12));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = pad(12);
    parent.addView(b, lp);
    b.setOnClickListener(clicked);
    return b;
  }

  private void divider(LinearLayout parent) {
    View d = new View(this);
    d.setBackgroundColor(color(R.color.line));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, pad(1));
    lp.topMargin = pad(8);
    lp.bottomMargin = pad(8);
    parent.addView(d, lp);
  }

  private Switch toggle(
      LinearLayout parent, String title, String subtitle, String owner, boolean checked) {
    LinearLayout r = row();
    r.setPadding(0, pad(10), 0, pad(10));
    LinearLayout words = column();
    words.addView(label(title, 16, R.color.ink, true));
    if (!subtitle.isEmpty()) {
      gap(words, 3);
      words.addView(label(subtitle, 13, R.color.muted, false));
    }
    r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    Switch control = new Switch(this);
    control.setChecked(checked);
    control.setContentDescription(owner.isEmpty() ? title : title + " for " + owner);
    control.setMinHeight(pad(48));
    control.setMinWidth(pad(56));
    control.setPadding(pad(10), 0, 0, 0);
    int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
    control.setThumbTintList(
        new ColorStateList(states, new int[] {color(R.color.accent), color(R.color.muted)}));
    control.setTrackTintList(
        new ColorStateList(states, new int[] {color(R.color.soft), color(R.color.line)}));
    words.setOnClickListener(v -> control.setChecked(!control.isChecked()));
    r.addView(control);
    parent.addView(r);
    return control;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (state != null) {
      page = state.getInt("page", 0);
      query = state.getString("query", "");
    }
    // Ignore/remove pause timestamps left by older app versions.
    Rules.prefs(this)
        .edit()
        .remove("reply_composing_until")
        .remove("hands_free_composing_until")
        .apply();
    AppReminders.scheduleAll(this);
    initializeTestSpeech();
    testCallMonitor =
        new CallMonitor(
            this,
            new android.os.Handler(android.os.Looper.getMainLooper()),
            blocked -> {
              if (blocked) {
                if (tts != null) tts.stop();
                if (testRing != null) testRing.stop();
              }
            });
    testCallMonitor.start();
    quietUiTick.run();
    Rules.prefs(this).registerOnSharedPreferenceChangeListener(missedChanged);
    LinearLayout root = column();
    root.setBackgroundColor(color(R.color.background));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars =
              insets.getInsets(
                  WindowInsets.Type.systemBars()
                      | WindowInsets.Type.displayCutout()
                      | WindowInsets.Type.ime());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    layout = column();
    layout.setPadding(pad(24), pad(24), pad(24), pad(16));
    scroll.addView(layout);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    navigation = row();
    navigation.setPadding(pad(16), pad(10), pad(16), pad(10));
    navigation.setBackgroundColor(color(R.color.surface));
    root.addView(navigation);
    setContentView(root);
    root.requestApplyInsets();
    showPage(false);
  }

  private boolean notificationsAllowed() {
    String allowed =
        Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
    return allowed != null && allowed.contains(getPackageName() + "/");
  }

  private boolean phoneAllowed() {
    return checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
        == android.content.pm.PackageManager.PERMISSION_GRANTED;
  }

  private void showPage(boolean animate) {
    layout.animate().cancel();
    layout.removeAllViews();
    layout.setFocusableInTouchMode(true);
    layout.requestFocus();
    list = null;
    missedCount = null;
    handsFreeStatus = null;
    readoutHealth = null;
    missedPlay = null;
    TextView eyebrow = label("P R I O R I T Y   V O I C E", 11, R.color.accent, true);
    eyebrow.setContentDescription("Priority Voice");
    layout.addView(eyebrow);
    gap(layout, 12);
    layout.addView(
        label(
            new String[] {"A little less noise.", "Your inner circle.", "Make it your own."}[page],
            30,
            R.color.ink,
            true));
    gap(layout, 8);
    layout.addView(
        label(
            new String[] {
                  "Stay present. Hear what matters.",
                  "Personal alerts for your people.",
                  "Simple choices. A calmer day."
                }
                [page],
            15,
            R.color.muted,
            false));
    gap(layout, 24);
    if (page == 0) home();
    else if (page == 1) senders();
    else preferences();
    navigation.removeAllViews();
    String[] tabs = {"Home", "Senders", "Settings"};
    for (int i = 0; i < tabs.length; i++) {
      final int destination = i;
      Button tab = new Button(this);
      tab.setText(tabs[i]);
      tab.setAllCaps(false);
      tab.setTextSize(14);
      tab.setMinHeight(pad(48));
      tab.setTextColor(color(page == i ? R.color.accent : R.color.muted));
      tab.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(color(R.color.line)),
              shape(color(page == i ? R.color.soft : R.color.surface), 18, false),
              null));
      tab.setSelected(page == i);
      tab.setContentDescription(tabs[i] + (page == i ? ", selected" : ""));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(pad(3), 0, pad(3), 0);
      navigation.addView(tab, lp);
      tab.setOnClickListener(
          v -> {
            if (page != destination) {
              page = destination;
              query = "";
              showPage(true);
              ((ScrollView) layout.getParent()).smoothScrollTo(0, 0);
            }
          });
    }
    if (animate
        && Settings.Global.getFloat(
                getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            > 0) {
      layout.setAlpha(0f);
      layout.animate().alpha(1f).setDuration(180).start();
    } else layout.setAlpha(1f);
  }

  private void initializeTestSpeech() {
    speechReady = false;
    tts =
        new TextToSpeech(
            this,
            result -> {
              speechReady = result == TextToSpeech.SUCCESS;
              if (speechReady)
                tts.setAudioAttributes(
                    new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            });
  }

  private void updateReadoutHealth() {
    if (readoutHealth == null) return;
    long last = Rules.prefs(this).getLong("reader_heartbeat", 0);
    String state =
        !notificationsAllowed()
            ? "Notification access is off."
            : (last == 0 || System.currentTimeMillis() - last > 15000)
                ? "No recent listener connection. Reconnect notification access."
                : Rules.prefs(this).getString("readout_status", "Starting notification readouts…");
    if (last > 0 && System.currentTimeMillis() - last <= 15000) {
      if (!Rules.prefs(this).getBoolean("enabled", true)) state = "All-contact alerts are off.";
      else if (QuietHours.active(this)) state = "Quiet hours are active.";
      else if (AudioSessions.busy())
        state = "Paused for an active reply. Readouts resume when it ends.";
    }
    readoutHealth.setText(
        state
            + "\n"
            + Rules.prefs(this)
                .getString("last_notification_check", "Waiting for a new matching notification."));
  }

  private void readoutHealthCard() {
    LinearLayout health = card(layout);
    health.addView(label("Readout status", 18, R.color.ink, true));
    readoutHealth = label("", 14, R.color.muted, false);
    health.addView(readoutHealth);
    updateReadoutHealth();
    action(
        health,
        "Reset audio & reconnect",
        false,
        v -> {
          stopService(new Intent(this, HandsFreeService.class));
          AudioSessions.clear(this);
          if (testRing != null) testRing.stop();
          if (tts != null) {
            tts.stop();
            tts.shutdown();
          }
          initializeTestSpeech();
          Rules.prefs(this)
              .edit()
              .remove("reply_composing_until")
              .remove("hands_free_composing_until")
              .putLong("audio_reset_requested", System.nanoTime())
              .apply();
          android.service.notification.NotificationListenerService.requestRebind(
              new android.content.ComponentName(this, ReaderService.class));
          Toast.makeText(
                  this,
                  "Audio reset requested. Hands-free is stopped. Send a new WhatsApp or email"
                      + " notification to test readouts.",
                  Toast.LENGTH_LONG)
              .show();
        });
    action(
        health,
        "Notification access settings",
        false,
        v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
  }

  private void masterSwitch(LinearLayout parent) {
    Switch all =
        toggle(
            parent,
            "Alerts for all contacts",
            "Sound and readouts for every saved sender.",
            "",
            Rules.prefs(this).getBoolean("enabled", true));
    all.setOnCheckedChangeListener(
        (button, checked) -> {
          Rules.prefs(this).edit().putBoolean("enabled", checked).apply();
          if (!checked) {
            if (tts != null) tts.stop();
            if (testRing != null) testRing.stop();
          }
          Toast.makeText(
                  this,
                  checked
                      ? "All-contact alerts on. Individual settings and quiet hours still apply."
                      : "All-contact alerts off. Your sender settings are saved.",
                  Toast.LENGTH_SHORT)
              .show();
        });
  }

  private void home() {
    LinearLayout hero = card(layout);
    hero.setBackground(shape(color(R.color.soft), 24, false));
    hero.addView(label("A softer way to stay connected", 20, R.color.ink, true));
    gap(hero, 8);
    hero.addView(
        label("Your chosen people, gently brought to your attention.", 15, R.color.muted, false));
    gap(hero, 12);
    masterSwitch(hero);
    action(
        hero,
        "Hands-free voice commands",
        false,
        v -> {
          page = 2;
          showPage(true);
        });
    readoutHealthCard();
    LinearLayout access = card(layout);
    boolean ready = notificationsAllowed();
    copy(
        access,
        ready ? "You're connected" : "One step to get started",
        ready
            ? "Notification access is enabled. Your sender preferences are ready to use."
            : "Allow notification access to hear alerts from the people you choose.");
    if (!ready)
      action(
          access,
          "Connect notifications",
          true,
          v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    if (!phoneAllowed()) action(access, "Enable call-aware pauses", false, v -> requestPhone());
    section("In your circle");
    int count = Rules.load(this).size();
    LinearLayout people = card(layout);
    copy(
        people,
        count == 1 ? "1 priority sender" : count + " priority senders",
        count == 0
            ? "Start with someone you always want to hear from."
            : "Choose sound, sender name and message readout for each person.");
    action(
        people,
        count == 0 ? "Add your first sender" : "Manage senders",
        true,
        v -> {
          page = 1;
          showPage(true);
          if (count == 0) addRule();
        });
    LinearLayout missed = card(layout);
    missed.addView(label("Pick up where you left off", 20, R.color.ink, true));
    gap(missed, 8);
    missedCount = label("", 14, R.color.muted, false);
    missed.addView(missedCount);
    missedPlay = action(missed, "Play missed readouts", true, v -> playMissed());
    action(
        missed,
        "Clear saved readouts",
        false,
        v ->
            new AlertDialog.Builder(this)
                .setTitle("Clear missed readouts?")
                .setMessage("This deletes saved previews and cancels their pending playback.")
                .setPositiveButton(
                    "Clear",
                    (d, w) -> {
                      if (notificationsAllowed())
                        Rules.prefs(this)
                            .edit()
                            .putLong("clear_missed_requested", System.nanoTime())
                            .apply();
                      else MissedStore.clear(this);
                    })
                .setNegativeButton("Keep", null)
                .show());
    gap(missed, 10);
    missed.addView(
        label(
            "Call-deferred previews are saved privately for up to 24 hours (100 maximum), even if"
                + " you clear phone notifications. Successful readouts are removed.",
            12,
            R.color.muted,
            false));
    updateMissedCount();
    LinearLayout test = card(layout);
    copy(test, "Find your sound", "A quick check of your notification tone and voice.");
    action(test, "Try sound & voice", false, v -> testVoice());
    gap(layout, 8);
    layout.addView(
        label(
            "Made for quieter moments. Readouts wait while you're on a call.",
            13,
            R.color.muted,
            false));
  }

  private void senders() {
    LinearLayout master = card(layout);
    masterSwitch(master);
    action(layout, "+  Add sender", true, v -> addRule());
    gap(layout, 16);
    EditText search = new EditText(this);
    search.setSingleLine();
    search.setMinHeight(pad(48));
    search.setTextSize(15);
    search.setTextColor(color(R.color.ink));
    search.setHintTextColor(color(R.color.muted));
    search.setHint("Find a name, number or email");
    search.setContentDescription("Search priority senders");
    search.setPadding(pad(16), pad(14), pad(16), pad(14));
    search.setBackground(shape(color(R.color.surface), 16, true));
    search.setText(query);
    layout.addView(search, new LinearLayout.LayoutParams(-1, -2));
    gap(layout, 18);
    list = column();
    layout.addView(list);
    render();
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            query = s.toString();
            render();
          }

          public void afterTextChanged(android.text.Editable s) {}
        });
    action(layout, "Help with sender matching", false, v -> showHelp());
  }

  private void handsFreeCard() {
    section("Hands-free commands");
    LinearLayout card = card(layout);
    copy(
        card,
        "Send text with your voice",
        "After a contact's WhatsApp readouts, hear “Do you want to reply?” Say Yes, dictate, then"
            + " say Yes after the draft readback to send.");
    Switch automatic =
        toggle(
            card,
            "Ask after WhatsApp readouts",
            "After readouts, ask now if unlocked or wait until you unlock. Then listen for Yes or"
                + " No.",
            "",
            Rules.prefs(this).getBoolean("auto_reply_offer", true));
    automatic.setOnCheckedChangeListener(
        (button, checked) ->
            Rules.prefs(this).edit().putBoolean("auto_reply_offer", checked).apply());
    Switch provider =
        toggle(
            card,
            "Prefer on-device speech recognition",
            "Off uses your normal phone speech provider, which may need internet. Restart listening"
                + " after changing.",
            "",
            Rules.prefs(this).getBoolean("prefer_device_recognition", false));
    provider.setOnCheckedChangeListener(
        (button, checked) ->
            Rules.prefs(this).edit().putBoolean("prefer_device_recognition", checked).apply());
    action(card, "Test wake phrase", false, v -> testWakePhrase());
    Switch wake =
        toggle(
            card,
            "Require wake phrase",
            "Say the phrase before a new command. Reply questions still accept Yes or No.",
            "",
            Rules.prefs(this).getBoolean("wake_enabled", false));
    wake.setOnCheckedChangeListener(
        (button, checked) -> Rules.prefs(this).edit().putBoolean("wake_enabled", checked).apply());
    action(
        card,
        "Change wake phrase",
        false,
        v -> {
          LinearLayout box = new LinearLayout(this);
          box.setOrientation(LinearLayout.VERTICAL);
          EditText phrase = field(box, "Wake phrase", "Hey Priority");
          phrase.setText(Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
          AlertDialog dialog =
              new AlertDialog.Builder(this)
                  .setTitle("Wake phrase")
                  .setView(box)
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton("Save", null)
                  .create();
          dialog.setOnShowListener(
              d ->
                  dialog
                      .getButton(AlertDialog.BUTTON_POSITIVE)
                      .setOnClickListener(
                          b -> {
                            String value = VoiceCommand.normalize(phrase.getText().toString());
                            if (value.length() < 3 || value.length() > 50 || !value.contains(" ")) {
                              phrase.setError("Use at least two words, up to 50 characters.");
                              return;
                            }
                            Rules.prefs(this).edit().putString("wake_phrase", value).apply();
                            dialog.dismiss();
                          }));
          dialog.show();
        });
    copy(
        card,
        "Allowed apps",
        "Say Open followed by WhatsApp, Gmail, Spotify, Traya, Deepstash or Duolingo; or say Open"
            + " WhatsApp chat with Anita. A saved chat needs a full phone number with +country"
            + " code. Opening a chat does not send a message.");
    for (AppCatalog.App item : AppCatalog.ALL) {
      String app = item.label;
      Switch permitted =
          toggle(
              card,
              "Allow " + app,
              "",
              "",
              Rules.prefs(this).getBoolean("open_" + app.toLowerCase(java.util.Locale.ROOT), true));
      permitted.setOnCheckedChangeListener(
          (button, checked) ->
              Rules.prefs(this)
                  .edit()
                  .putBoolean("open_" + app.toLowerCase(java.util.Locale.ROOT), checked)
                  .apply());
    }
    action(
        card,
        "Allow floating control for hands-free app opening",
        false,
        v -> {
          try {
            startActivity(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
          } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Overlay settings unavailable on this device.", Toast.LENGTH_LONG)
                .show();
          }
        });
    copy(
        card,
        "Wake listening",
        "Default phrase: Hey Priority. Start listening below after changing permissions. The phone"
            + " must be unlocked. A small Stop voice control stays visible when overlay permission"
            + " is allowed. Without it, background app-opening commands show a tap-to-open"
            + " notification. This uses speech recognition, not a low-power always-on wake"
            + " detector; recognition may use your provider's network and battery.");
    handsFreeStatus = label("", 14, R.color.accent, true);
    card.addView(handsFreeStatus);
    updateHandsFreeStatus();
    for (AppCatalog.App item : AppCatalog.ALL) {
      String app = item.label;
      action(
          card,
          "Test opening " + app,
          false,
          v -> {
            String result =
                VoiceAppLauncher.open(this, app.toLowerCase(java.util.Locale.ROOT), false);
            Rules.prefs(this).edit().putString("last_launch_status", result).apply();
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
          });
    }
    action(card, "Start hands-free listening", true, v -> startHandsFree());
    action(
        card,
        "Stop listening",
        false,
        v -> {
          stopService(new Intent(this, HandsFreeService.class));
          Toast.makeText(this, "Hands-free listening stopped.", Toast.LENGTH_SHORT).show();
        });
    gap(card, 10);
    card.addView(
        label(
            "Readouts still work while locked. Start this listening session before locking; valid"
                + " reply offers wait until you unlock. Voice replies need a saved WhatsApp contact"
                + " with Reply by voice on and a live Reply notification after a readout. Sending"
                + " still requires a live notification Reply action; opening a chat alone cannot"
                + " send a message. Say “Cancel” to discard or “Stop listening” to finish."
                + " Listening uses battery; your speech provider may use the network. No recordings"
                + " are saved by this app.",
            13,
            R.color.muted,
            false));
  }

  private void updateHandsFreeStatus() {
    if (handsFreeStatus == null) return;
    handsFreeStatus.setText(
        Rules.prefs(this).getBoolean("hands_free_running", false)
            ? Rules.prefs(this)
                .getString(
                    "hands_free_status", "Session active · check the microphone notification.")
            : Rules.prefs(this).getString("hands_free_error", "Listening is off."));
    String recognition = Rules.prefs(this).getString("last_recognition_status", "");
    if (!recognition.isEmpty()) handsFreeStatus.append("\nLast recognition: " + recognition);
    String launch = Rules.prefs(this).getString("last_launch_status", "");
    if (!launch.isEmpty()) handsFreeStatus.append("\nLast app command: " + launch);
  }

  private void testWakePhrase() {
    stopService(new Intent(this, HandsFreeService.class));
    if (tts != null) tts.stop();
    if (testRing != null) testRing.stop();
    Intent test =
        new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(
                android.speech.RecognizerIntent.EXTRA_PROMPT,
                "Say only your wake phrase: "
                    + Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
    AudioSessions.hold(this, "wake-phrase-test");
    try {
      startActivityForResult(test, 77);
    } catch (android.content.ActivityNotFoundException | SecurityException e) {
      AudioSessions.release(this, "wake-phrase-test");
      new AlertDialog.Builder(this)
          .setTitle("Speech input unavailable")
          .setMessage(
              "No usable system speech input screen was found. Check Settings → General management"
                  + " → Keyboard list and default → Voice input, and install or enable a speech"
                  + " provider.")
          .setPositiveButton("OK", null)
          .show();
    }
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    if (code != 77) return;
    AudioSessions.release(this, "wake-phrase-test");
    java.util.ArrayList<String> words =
        data == null
            ? null
            : data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
    if (result != RESULT_OK || words == null || words.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle("No words returned")
          .setMessage(
              "The phone's speech input returned no words. Check microphone access, your voice"
                  + " input provider and its English language support. Listening is stopped;"
                  + " restart it after testing.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    String phrase = Rules.prefs(this).getString("wake_phrase", "Hey Priority");
    boolean matched = false;
    StringBuilder heard = new StringBuilder();
    for (int i = 0; i < Math.min(words.size(), 5); i++) {
      String candidate = words.get(i);
      heard.append("• ").append(candidate).append("\n");
      if (VoiceCommand.afterWake(candidate, phrase) != null) matched = true;
    }
    String first = VoiceCommand.normalize(words.get(0));
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle(matched ? "Wake phrase recognized" : "Wake phrase did not match")
            .setMessage(
                "Android heard:\n"
                    + heard
                    + "\nConfigured phrase: "
                    + phrase
                    + "\n\n"
                    + "Listening is stopped. Start it again after this test. These test words are"
                    + " not saved unless you choose to use the first result as your phrase.")
            .setPositiveButton("Done", null);
    if (first.contains(" ") && first.length() >= 3 && first.length() <= 50)
      dialog.setNeutralButton(
          "Use first result as phrase",
          (d, w) -> {
            Rules.prefs(this).edit().putString("wake_phrase", first).apply();
            Toast.makeText(this, "Wake phrase saved: " + first, Toast.LENGTH_LONG).show();
          });
    dialog.show();
  }

  private void startHandsFree() {
    if (!Rules.prefs(this).getBoolean("enabled", true) || QuietHours.active(this)) {
      Toast.makeText(
              this, "Enable all-contact alerts outside quiet hours first.", Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (!phoneAllowed()) {
      Toast.makeText(
              this,
              "Allow Phone permission first so hands-free mode can pause during calls.",
              Toast.LENGTH_LONG)
          .show();
      requestPhone();
      return;
    }
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, 75);
      return;
    }
    if (android.os.Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 76);
      return;
    }
    try {
      startForegroundService(new Intent(this, HandsFreeService.class));
      Toast.makeText(
              this, "Hands-free session starting. Keep the phone unlocked.", Toast.LENGTH_LONG)
          .show();
    } catch (SecurityException | IllegalStateException e) {
      Toast.makeText(
              this,
              "Android blocked microphone startup. Check microphone permission and start again"
                  + " while the app is open.",
              Toast.LENGTH_LONG)
          .show();
    }
  }

  private void quietHoursCard() {
    section("Quiet hours");
    LinearLayout quiet = card(layout);
    copy(
        quiet,
        "A daily pause",
        "Mute Priority Voice sounds and readouts between your chosen times, every day until you"
            + " turn this off.");
    Switch enabled =
        toggle(
            quiet,
            "Scheduled quiet hours",
            "Repeats daily using your phone's local time.",
            "",
            QuietHours.enabled(this));
    enabled.setOnCheckedChangeListener(
        (button, checked) -> Rules.prefs(this).edit().putBoolean("quiet_enabled", checked).apply());
    final int[] times = {QuietHours.start(this), QuietHours.stop(this)};
    TextView applied =
        label(
            "Applied: " + QuietHours.time(this, times[0]) + " – " + QuietHours.time(this, times[1]),
            14,
            R.color.accent,
            true);
    quiet.addView(applied);
    Button start = action(quiet, "Start · " + QuietHours.time(this, times[0]), false, null);
    Button stop = action(quiet, "Stop · " + QuietHours.time(this, times[1]), false, null);
    start.setOnClickListener(
        v ->
            new android.app.TimePickerDialog(
                    this,
                    (picker, hour, minute) -> {
                      times[0] = hour * 60 + minute;
                      start.setText("Start · " + QuietHours.time(this, times[0]));
                    },
                    times[0] / 60,
                    times[0] % 60,
                    android.text.format.DateFormat.is24HourFormat(this))
                .show());
    stop.setOnClickListener(
        v ->
            new android.app.TimePickerDialog(
                    this,
                    (picker, hour, minute) -> {
                      times[1] = hour * 60 + minute;
                      stop.setText("Stop · " + QuietHours.time(this, times[1]));
                    },
                    times[1] / 60,
                    times[1] % 60,
                    android.text.format.DateFormat.is24HourFormat(this))
                .show());
    action(
        quiet,
        "Apply & enable",
        true,
        v -> {
          if (times[0] == times[1]) {
            Toast.makeText(this, "Choose different start and stop times.", Toast.LENGTH_LONG)
                .show();
            return;
          }
          Rules.prefs(this)
              .edit()
              .putInt("quiet_start", times[0])
              .putInt("quiet_stop", times[1])
              .putBoolean("quiet_enabled", true)
              .apply();
          enabled.setChecked(true);
          applied.setText(
              "Applied: "
                  + QuietHours.time(this, times[0])
                  + " – "
                  + QuietHours.time(this, times[1]));
          Toast.makeText(
                  this, "Quiet hours will repeat daily until switched off.", Toast.LENGTH_LONG)
              .show();
        });
    gap(quiet, 12);
    quiet.addView(
        label(
            "Overnight periods work too. New alerts during quiet hours are silenced, without a"
                + " catch-up readout. Saved missed readouts stay available afterward. WhatsApp and"
                + " Gmail may still play their own sounds.",
            13,
            R.color.muted,
            false));
  }

  private void appRemindersDialog() {
    LinearLayout box = column();
    box.setPadding(pad(16), pad(8), pad(16), pad(16));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Daily app reminders & links")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .create();
    copy(
        box,
        "Only if not opened today",
        "Each app has its own daily time. Usage access checks actual app opens, including manual"
            + " opens. Reminders respect Alerts for all contacts, Quiet hours and Allowed apps. No"
            + " usage history is saved or uploaded.");
    box.addView(
        label(
            "Usage access: "
                + (AppReminders.usageAllowed(this) ? "allowed" : "needed")
                + " · Timing: "
                + (AppReminders.precise(this)
                    ? "precise alarms allowed"
                    : "may be delayed by Android"),
            13,
            R.color.accent,
            true));
    action(
        box,
        "Allow Usage access",
        false,
        v -> {
          dialog.dismiss();
          openReminderPermission(
              new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                  .setData(android.net.Uri.parse("package:" + getPackageName())));
        });
    if (android.os.Build.VERSION.SDK_INT >= 31)
      action(
          box,
          "Allow precise reminder times",
          false,
          v -> {
            dialog.dismiss();
            openReminderPermission(
                new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + getPackageName())));
          });
    box.addView(
        label(
            "After granting access, return here and enable each reminder. When precise alarms are"
                + " unavailable, Android may deliver reminders later. Force-stopping the app"
                + " prevents alarms until it is opened again.",
            12,
            R.color.muted,
            false));
    String error = Rules.prefs(this).getString("reminder_error", "");
    if (!error.isEmpty()) box.addView(label(error, 12, R.color.muted, false));
    for (AppCatalog.App app : AppCatalog.ALL) {
      LinearLayout appCard = card(box);
      String pkg = InstalledApps.packageFor(this, app);
      copy(
          appCard,
          app.label,
          pkg == null
              ? "App not linked. Install it or choose the installed app below."
              : "Linked to " + pkg);
      action(
          appCard,
          "Choose installed app",
          false,
          v ->
              chooseInstalledApp(
                  app,
                  () -> {
                    dialog.dismiss();
                    appRemindersDialog();
                  }));
      Switch enabled =
          toggle(
              appCard,
              "Daily reminder",
              "Remind only if this app has not been opened today.",
              app.label,
              AppReminders.enabled(this, app));
      enabled.setOnCheckedChangeListener(
          (button, checked) -> {
            if (checked
                && (!AppReminders.usageAllowed(this)
                    || InstalledApps.packageFor(this, app) == null)) {
              button.setChecked(false);
              Toast.makeText(
                      this,
                      "Allow Usage access and link the installed app, then enable its reminder.",
                      Toast.LENGTH_LONG)
                  .show();
              return;
            }
            if (checked
                && android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
              button.setChecked(false);
              requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 78);
              return;
            }
            Rules.prefs(this).edit().putBoolean("reminder_enabled:" + app.id, checked).apply();
            if (!checked) AppReminders.cancelNotice(this, app);
            AppReminders.schedule(this, app);
          });
      Button time =
          action(
              appCard,
              "Time · " + QuietHours.time(this, AppReminders.minute(this, app)),
              false,
              null);
      time.setOnClickListener(
          v -> {
            int minute = AppReminders.minute(this, app);
            new android.app.TimePickerDialog(
                    this,
                    (picker, hour, min) -> {
                      int value = hour * 60 + min;
                      Rules.prefs(this).edit().putInt("reminder_minute:" + app.id, value).apply();
                      AppReminders.schedule(this, app);
                      time.setText("Time · " + QuietHours.time(this, value));
                    },
                    minute / 60,
                    minute % 60,
                    android.text.format.DateFormat.is24HourFormat(this))
                .show();
          });
    }
    dialog.show();
  }

  private void openReminderPermission(Intent intent) {
    try {
      startActivity(intent);
    } catch (android.content.ActivityNotFoundException | SecurityException e) {
      Toast.makeText(
              this,
              "Open Android Settings → Apps → Special access to grant this permission.",
              Toast.LENGTH_LONG)
          .show();
    }
  }

  private void chooseInstalledApp(AppCatalog.App app, Runnable refresh) {
    android.content.pm.PackageManager pm = getPackageManager();
    java.util.TreeMap<String, String> choices =
        new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (android.content.pm.ResolveInfo info : InstalledApps.launchers(this)) {
      String pkg = info.activityInfo.packageName;
      if (pkg.equals(getPackageName())) continue;
      choices.put(info.loadLabel(pm).toString() + "\n" + pkg, pkg);
    }
    java.util.ArrayList<String> names = new java.util.ArrayList<>(),
        packages = new java.util.ArrayList<>();
    names.add("Use automatic detection");
    packages.add("");
    for (java.util.Map.Entry<String, String> entry : choices.entrySet()) {
      if (packages.contains(entry.getValue())) continue;
      names.add(entry.getKey());
      packages.add(entry.getValue());
    }
    new AlertDialog.Builder(this)
        .setTitle("Select installed " + app.label)
        .setItems(
            names.toArray(new String[0]),
            (d, which) -> {
              if (which == 0) Rules.prefs(this).edit().remove("app_package:" + app.id).apply();
              else
                Rules.prefs(this)
                    .edit()
                    .putString("app_package:" + app.id, packages.get(which))
                    .apply();
              AppReminders.cancelNotice(this, app);
              AppReminders.schedule(this, app);
              refresh.run();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void preferences() {
    section("Your apps");
    LinearLayout apps = card(layout);
    copy(
        apps,
        "Daily reminders & installed apps",
        "Set a separate daily time for WhatsApp, Gmail, Spotify, Traya, Deepstash and Duolingo."
            + " Remind only when you have not opened the app today.");
    action(apps, "Daily app reminders & app links", true, v -> appRemindersDialog());
    handsFreeCard();
    quietHoursCard();
    section("Connections");
    LinearLayout access = card(layout);
    copy(
        access,
        "Notification access",
        notificationsAllowed()
            ? "Connected — ready to receive your chosen alerts."
            : "Not connected — allow access to start receiving alerts.");
    action(
        access,
        notificationsAllowed() ? "Manage access" : "Connect notifications",
        false,
        v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    divider(access);
    copy(
        access,
        "Pause for phone calls",
        phoneAllowed()
            ? "Phone permission enabled. Readouts wait until the call ends."
            : "Allow Phone permission to detect ringing and active calls.");
    action(
        access,
        phoneAllowed() ? "Manage Phone permission" : "Allow Phone permission",
        false,
        v -> requestPhone());
    section("Listening preferences");
    LinearLayout listening = card(layout);
    Switch reactions =
        toggle(
            listening,
            "Quiet reactions",
            "Mute spoken WhatsApp reactions, keeping their sound.",
            "",
            Rules.prefs(this).getBoolean("mute_reactions", false));
    reactions.setOnCheckedChangeListener(
        (b, c) -> Rules.prefs(this).edit().putBoolean("mute_reactions", c).apply());
    listening.addView(
        label(
            "Recognizes English reaction notifications. Regular messages still speak.",
            12,
            R.color.muted,
            false));
    divider(listening);
    action(listening, "Try sound & voice", false, v -> testVoice());
    action(
        listening,
        "Voice & language settings",
        false,
        v -> startActivity(new Intent("com.android.settings.TTS_SETTINGS")));
    section("Good to know");
    LinearLayout privacy = card(layout);
    copy(
        privacy,
        "Private by design",
        "No account or Internet permission. Call-deferred previews are saved privately for up to 24"
            + " hours so you can replay them. Notification access can expose private messages;"
            + " grant it only if you trust this app. Spoken text is sent to your chosen Android"
            + " speech engine.");
    divider(privacy);
    copy(
        privacy,
        "A pause for real life",
        "An interrupted readout restarts after the call. Only the latest waiting message per sender"
            + " is kept. Silent mode and Do Not Disturb are respected.");
    action(privacy, "Sender matching & Samsung tips", false, v -> showHelp());
    gap(layout, 8);
    layout.addView(label("PRIORITY VOICE  /  2.1", 11, R.color.muted, true));
  }

  private void render() {
    if (list == null) return;
    list.removeAllViews();
    List<Rules.Rule> rules = Rules.load(this);
    int found = 0;
    for (Rules.Rule r : rules) {
      if (!Rules.normalize(r.label + " " + r.value + " " + r.type + " " + r.alias)
          .contains(Rules.normalize(query))) continue;
      found++;
      LinearLayout c = card(list), header = row();
      TextView avatar =
          label(
              r.label.isEmpty()
                  ? "?"
                  : r.label
                      .substring(0, r.label.offsetByCodePoints(0, 1))
                      .toUpperCase(java.util.Locale.ROOT),
              20,
              R.color.accent,
              true);
      avatar.setGravity(Gravity.CENTER);
      avatar.setBackground(shape(color(R.color.soft), 18, false));
      header.addView(avatar, new LinearLayout.LayoutParams(pad(48), pad(48)));
      LinearLayout identity = column();
      identity.setPadding(pad(12), 0, 0, 0);
      identity.addView(label(r.label, 18, R.color.ink, true));
      identity.addView(label(r.type, 12, R.color.accent, true));
      header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
      c.addView(header);
      gap(c, 12);
      TextView address = label(r.value, 14, R.color.muted, false);
      address.setTextIsSelectable(true);
      c.addView(address);
      if (!r.alias.isEmpty()) {
        gap(c, 4);
        c.addView(label("Also matches: " + r.alias, 12, R.color.muted, false));
      }
      divider(c);
      Switch sound = toggle(c, "Notification sound", "A gentle heads-up.", r.label, r.sound);
      Switch sender = toggle(c, "Read sender name", "Hear who it's from.", r.label, r.readSender);
      Switch message =
          toggle(c, "Read message aloud", "Hear the message preview.", r.label, r.readMessage);
      sound.setOnCheckedChangeListener(
          (button, checked) -> updateAlerts(r, checked, sender.isChecked(), message.isChecked()));
      sender.setOnCheckedChangeListener(
          (button, checked) -> updateAlerts(r, sound.isChecked(), checked, message.isChecked()));
      message.setOnCheckedChangeListener(
          (button, checked) -> updateAlerts(r, sound.isChecked(), sender.isChecked(), checked));
      if (r.type.equals("WhatsApp")) {
        action(
            c,
            "Set WhatsApp chat number",
            false,
            v -> {
              LinearLayout box = column();
              EditText phone = field(box, "Chat number · optional", "+91 98765 43210");
              phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
              phone.setText(
                  Rules.prefs(this)
                      .getString(
                          "chat_number:" + r.value,
                          VoiceCommand.internationalPhone(r.value) != null ? r.value : ""));
              copy(
                  box,
                  "Keep your notification match",
                  "This number only opens the chat. Your existing sender name and readout settings"
                      + " stay the same. Clear it to remove the saved chat number.");
              AlertDialog dialog =
                  new AlertDialog.Builder(this)
                      .setTitle(r.label)
                      .setView(box)
                      .setNegativeButton("Cancel", null)
                      .setPositiveButton("Save", null)
                      .create();
              dialog.setOnShowListener(
                  d ->
                      dialog
                          .getButton(AlertDialog.BUTTON_POSITIVE)
                          .setOnClickListener(
                              b -> {
                                String number = phone.getText().toString().trim();
                                if (!number.isEmpty()
                                    && VoiceCommand.internationalPhone(number) == null) {
                                  phone.setError("Use +country code and the full phone number.");
                                  return;
                                }
                                if (number.isEmpty())
                                  Rules.prefs(this).edit().remove("chat_number:" + r.value).apply();
                                else
                                  Rules.prefs(this)
                                      .edit()
                                      .putString("chat_number:" + r.value, number)
                                      .apply();
                                dialog.dismiss();
                              }));
              dialog.show();
            });
        Switch reply =
            toggle(
                c,
                "Reply by voice",
                "Ask to reply after readouts; dictate and confirm by voice.",
                r.label,
                r.voiceReply);
        reply.setOnCheckedChangeListener(
            (button, checked) -> {
              List<Rules.Rule> saved = Rules.load(this);
              for (int index = 0; index < saved.size(); index++) {
                Rules.Rule item = saved.get(index);
                if (item.type.equals(r.type) && item.value.equals(r.value)) {
                  saved.set(
                      index,
                      new Rules.Rule(
                          item.type,
                          item.value,
                          item.label,
                          item.alias,
                          item.sound,
                          item.readSender,
                          item.readMessage,
                          checked));
                  break;
                }
              }
              Rules.save(this, saved);
              if (checked
                  && android.os.Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                      != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(
                    new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 74);
              if (!checked) ReplyRegistry.revokeContact(this, r.value);
            });
        action(
            c,
            "Reply to latest readout",
            false,
            v -> {
              ReplyRegistry.Target target = ReplyRegistry.forContact(this, r.value);
              if (target == null) {
                Toast.makeText(
                        this,
                        "No reply is available. Enable Reply by voice, then receive a new direct"
                            + " WhatsApp message with a Reply action and let its readout finish.",
                        Toast.LENGTH_LONG)
                    .show();
                return;
              }
              startActivity(new Intent(this, ReplyActivity.class).putExtra("token", target.token));
            });
      }
      Button remove =
          action(
              c,
              "Remove sender",
              false,
              v ->
                  new AlertDialog.Builder(this)
                      .setTitle("Remove " + r.label + "?")
                      .setMessage("Their personal alert preferences will be removed.")
                      .setPositiveButton(
                          "Remove",
                          (dialog, which) -> {
                            List<Rules.Rule> current = Rules.load(this);
                            current.removeIf(
                                item -> item.type.equals(r.type) && item.value.equals(r.value));
                            Rules.save(this, current);
                            if (r.type.equals("WhatsApp")) {
                              Rules.prefs(this).edit().remove("chat_number:" + r.value).apply();
                              ReplyRegistry.revokeContact(this, r.value);
                            }
                            render();
                          })
                      .setNegativeButton("Keep sender", null)
                      .show());
      remove.setTextColor(color(R.color.danger));
      remove.setContentDescription("Remove " + r.label);
    }
    if (found == 0) {
      LinearLayout empty = card(list);
      copy(
          empty,
          rules.isEmpty() ? "Your circle starts here" : "No matching senders",
          rules.isEmpty()
              ? "Add a WhatsApp contact or email sender, then choose how you'd like to hear from"
                  + " them."
              : "Try another name, number or email address.");
    }
  }

  private void updateMissedCount() {
    if (missedCount == null) return;
    int count = MissedStore.load(this).size();
    missedCount.setText(
        count == 0
            ? "You're all caught up. No missed readouts saved."
            : count + (count == 1 ? " missed readout is saved." : " missed readouts are saved."));
    if (missedPlay != null) {
      missedPlay.setEnabled(count > 0);
      missedPlay.setAlpha(count > 0 ? 1f : 0.5f);
    }
  }

  private void playMissed() {
    if (QuietHours.active(this)) {
      Toast.makeText(
              this,
              "Quiet hours are active. Turn them off or wait until the stop time.",
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (!notificationsAllowed()) {
      Toast.makeText(this, "Connect notification access first.", Toast.LENGTH_LONG).show();
      return;
    }
    if (!Rules.prefs(this).getBoolean("enabled", true)) {
      Toast.makeText(this, "Turn on Alerts for all contacts first.", Toast.LENGTH_LONG).show();
      return;
    }
    android.media.AudioManager audio = getSystemService(android.media.AudioManager.class);
    android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
    if (!testCallMonitor.isBlocked()
        && (audio.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL
            || nm.getCurrentInterruptionFilter()
                != android.app.NotificationManager.INTERRUPTION_FILTER_ALL)) {
      Toast.makeText(
              this,
              "Turn off silent mode / Do Not Disturb before playing missed readouts.",
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    Rules.prefs(this).edit().putLong("replay_requested", System.nanoTime()).apply();
    Toast.makeText(
            this,
            testCallMonitor.isBlocked()
                ? "Missed readouts will play after your call."
                : "Playing saved readouts using your current sender settings.",
            Toast.LENGTH_LONG)
        .show();
  }

  private void requestPhone() {
    if (phoneAllowed()) {
      startActivity(
          new Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              android.net.Uri.parse("package:" + getPackageName())));
      return;
    }
    requestPermissions(new String[] {android.Manifest.permission.READ_PHONE_STATE}, 73);
  }

  private void showHelp() {
    new AlertDialog.Builder(this)
        .setTitle("A little help")
        .setMessage(
            "WhatsApp often displays a saved name instead of a phone number. Add the exact name"
                + " shown in its notifications.\n\n"
                + "Gmail may hide email addresses. You can optionally match an exact sender display"
                + " name, but names cannot verify an email identity.\n\n"
                + "On Samsung, allow message previews and remove Priority Voice from sleeping apps"
                + " if alerts stop. Android may hide sensitive content.\n\n"
                + "Phone permission is required for reliable incoming-call detection. No call audio"
                + " or phone numbers are stored. Deferred readouts are held in memory;"
                + " force-stopping the app can discard them.")
        .setPositiveButton("Got it", null)
        .show();
  }

  private void testVoice() {
    if (!Rules.prefs(this).getBoolean("enabled", true)) {
      Toast.makeText(this, "Turn on Alerts for all contacts first.", Toast.LENGTH_LONG).show();
      return;
    }
    if (QuietHours.active(this)) {
      Toast.makeText(
              this,
              "Quiet hours are active. Turn them off to test sound and voice.",
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (testCallMonitor.isBlocked()) {
      Toast.makeText(this, "Voice testing is paused during calls.", Toast.LENGTH_LONG).show();
      return;
    }
    android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
    android.app.NotificationManager nm =
        (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (audio.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL
        || nm.getCurrentInterruptionFilter()
            != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
      Toast.makeText(
              this, "Turn off silent mode / Do Not Disturb to test alerts.", Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (testRing != null) testRing.stop();
    testRing =
        android.media.RingtoneManager.getRingtone(
            this,
            android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION));
    if (testRing != null) testRing.play();
    if (speechReady)
      tts.speak(
          "Priority Voice is ready. You have a priority message.",
          TextToSpeech.QUEUE_FLUSH,
          null,
          "test");
    else
      Toast.makeText(
              this, "Speech is not ready. Check voice & language settings.", Toast.LENGTH_LONG)
          .show();
  }

  @Override
  protected void onResume() {
    super.onResume();
    visibleActivity = this;
    if (testCallMonitor != null) testCallMonitor.start();
    Rules.prefs(this).edit().putLong("refresh_call_monitor", System.nanoTime()).apply();
    if (layout != null) showPage(false);
  }

  @Override
  protected void onPause() {
    if (visibleActivity == this) visibleActivity = null;
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("page", page);
    state.putString("query", query);
    super.onSaveInstanceState(state);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == 75 || code == 76) {
      if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
        startHandsFree();
      else
        Toast.makeText(
                this,
                "Microphone and notification permissions are needed for the visible hands-free"
                    + " session.",
                Toast.LENGTH_LONG)
            .show();
    }
    if (code == 78)
      Toast.makeText(
              this,
              "Return to Daily app reminders and enable the reminders you want.",
              Toast.LENGTH_LONG)
          .show();
    if (code == 73) {
      if (testCallMonitor != null) testCallMonitor.start();
      Rules.prefs(this).edit().putLong("refresh_call_monitor", System.nanoTime()).apply();
      android.service.notification.NotificationListenerService.requestRebind(
          new android.content.ComponentName(this, ReaderService.class));
      showPage(false);
      Toast.makeText(
              this,
              phoneAllowed()
                  ? "Call-aware pauses are enabled."
                  : "Phone permission is needed for reliable call detection. You can enable it in"
                      + " Android app settings.",
              Toast.LENGTH_LONG)
          .show();
    }
  }

  private void updateAlerts(Rules.Rule rule, boolean sound, boolean sender, boolean message) {
    List<Rules.Rule> current = Rules.load(this);
    for (int i = 0; i < current.size(); i++) {
      Rules.Rule r = current.get(i);
      if (r.type.equals(rule.type) && r.value.equals(rule.value)) {
        current.set(
            i,
            new Rules.Rule(
                r.type, r.value, r.label, r.alias, sound, sender, message, r.voiceReply));
        Rules.save(this, current);
        return;
      }
    }
  }

  private EditText field(LinearLayout box, String title, String hint) {
    gap(box, 16);
    TextView caption = label(title, 13, R.color.ink, true);
    box.addView(caption);
    gap(box, 6);
    EditText field = new EditText(this);
    field.setId(View.generateViewId());
    caption.setLabelFor(field.getId());
    field.setHint(hint);
    field.setTextSize(15);
    field.setSingleLine();
    field.setMinHeight(pad(52));
    field.setTextColor(color(R.color.ink));
    field.setHintTextColor(color(R.color.muted));
    field.setPadding(pad(14), pad(12), pad(14), pad(12));
    field.setBackground(shape(color(R.color.background), 14, true));
    box.addView(field, new LinearLayout.LayoutParams(-1, -2));
    return field;
  }

  private void addRule() {
    LinearLayout box = column();
    box.setPadding(pad(24), pad(8), pad(24), pad(16));
    box.addView(label("Choose someone you'd like to hear from.", 14, R.color.muted, false));
    gap(box, 16);
    TextView source = label("Notification source", 13, R.color.ink, true);
    box.addView(source);
    Spinner type = new Spinner(this);
    type.setId(View.generateViewId());
    source.setLabelFor(type.getId());
    type.setMinimumHeight(pad(48));
    type.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"WhatsApp", "Email"}));
    box.addView(type);
    EditText value = field(box, "Sender", "Exact contact name or full phone number");
    EditText label = field(box, "Name to read aloud · optional", "For example, Alex");
    LinearLayout emailFields = column();
    box.addView(emailFields);
    EditText alias =
        field(emailFields, "Display name fallback · optional", "Exact name shown by Gmail");
    gap(emailFields, 8);
    emailFields.addView(
        label(
            "Only use this if the address is hidden. A name cannot verify an email identity.",
            12,
            R.color.muted,
            false));
    type.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> parent) {}

          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            boolean email = position == 1;
            emailFields.setVisibility(email ? View.VISIBLE : View.GONE);
            value.setHint(email ? "sender@example.com" : "Exact contact name or full phone number");
            value.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                    | (email
                        ? android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        : android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS));
          }
        });
    ScrollView form = new ScrollView(this);
    form.addView(box);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Add to your circle")
            .setView(form)
            .setPositiveButton("Add sender", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String s = value.getText().toString().trim(),
                          t = type.getSelectedItem().toString();
                      if (s.isEmpty()
                          || (t.equals("Email")
                              && !android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches())) {
                        value.setError(
                            "Enter a valid " + (t.equals("Email") ? "email address" : "sender"));
                        return;
                      }
                      List<Rules.Rule> rules = Rules.load(this);
                      for (Rules.Rule r : rules)
                        if (r.type.equals(t)
                            && Rules.normalize(r.value).equals(Rules.normalize(s))) {
                          value.setError("Already added");
                          return;
                        }
                      String name = label.getText().toString().trim();
                      rules.add(
                          new Rules.Rule(
                              t,
                              s,
                              name.isEmpty() ? s : name,
                              t.equals("Email") ? alias.getText().toString().trim() : ""));
                      Rules.save(this, rules);
                      query = "";
                      dialog.dismiss();
                      showPage(false);
                    }));
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(shape(color(R.color.surface), 28, false));
      dialog
          .getWindow()
          .setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
  }

  @Override
  protected void onDestroy() {
    quietUiHandler.removeCallbacks(quietUiTick);
    Rules.prefs(this).unregisterOnSharedPreferenceChangeListener(missedChanged);
    if (testCallMonitor != null) testCallMonitor.close();
    if (testRing != null) testRing.stop();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }
}
