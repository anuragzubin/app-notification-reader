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
  private TextView missedCount;
  private Button missedPlay;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener missedChanged =
      (prefs, key) -> {
        if (MissedStore.KEY.equals(key)) updateMissedCount();
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

  private void preferences() {
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
    layout.addView(label("PRIORITY VOICE  /  1.7", 11, R.color.muted, true));
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
    if (testCallMonitor != null) testCallMonitor.start();
    Rules.prefs(this).edit().putLong("refresh_call_monitor", System.nanoTime()).apply();
    if (layout != null) showPage(false);
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
        current.set(i, new Rules.Rule(r.type, r.value, r.label, r.alias, sound, sender, message));
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
