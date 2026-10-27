package com.notificationreader;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

public class ReplyActivity extends Activity {
  private String token;
  private EditText editor;
  private TextView status;
  private TextToSpeech tts;
  private boolean speechReady, dictating;
  private long nextRenew;
  private CallMonitor calls;
  private final ReplyDraft draft = new ReplyDraft();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable poll =
      new Runnable() {
        public void run() {
          if (System.currentTimeMillis() >= nextRenew) holdReadouts();
          if (!allowed()) {
            if (tts != null) tts.stop();
            if (dictating) {
              finishActivity(501);
              dictating = false;
            }
          }
          handler.postDelayed(this, 1000);
        }
      };

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(LinearLayout root, String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextColor(getColor(R.color.ink));
    v.setTextSize(size);
    v.setPadding(0, dp(8), 0, dp(8));
    root.addView(v);
    return v;
  }

  private Button button(LinearLayout root, String title, View.OnClickListener click) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextColor(getColor(R.color.on_accent));
    b.setMinHeight(dp(52));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(getColor(R.color.accent));
    bg.setCornerRadius(dp(16));
    b.setBackground(bg);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(12);
    root.addView(b, lp);
    b.setOnClickListener(click);
    return b;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    token = getIntent().getStringExtra("token");
    calls =
        new CallMonitor(
            this,
            handler,
            blocked -> {
              if (blocked && tts != null) tts.stop();
            });
    calls.start();
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
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(24), dp(20), dp(24), dp(24));
    root.setBackgroundColor(getColor(R.color.background));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(root);
    scroll.setBackgroundColor(getColor(R.color.background));
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets i =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
          v.setPadding(i.left, i.top, i.right, i.bottom);
          return insets;
        });
    setContentView(scroll);
    scroll.requestApplyInsets();
    text(root, "Reply by voice", 28);
    ReplyRegistry.Target target = ReplyRegistry.get(this, token);
    if (target == null || !target.ready) {
      text(
          root,
          "This reply expired or its WhatsApp notification changed or was cleared. Wait for a new"
              + " eligible direct-message readout.",
          16);
      button(root, "Close", v -> finish());
      return;
    }
    text(
        root,
        "To "
            + target.label
            + (target.packageName.equals("com.whatsapp.w4b")
                ? " · WhatsApp Business"
                : " · WhatsApp"),
        20);
    text(root, target.value, 14);
    text(root, "Message: " + target.preview, 14);
    text(
        root,
        "Dictate or type a text reply. Review the wording and recipient before confirming Send."
            + " Your speech recognition provider may process audio online.",
        15);
    editor = new EditText(this);
    editor.setHint("Your reply…");
    editor.setTextColor(getColor(R.color.ink));
    editor.setHintTextColor(getColor(R.color.muted));
    editor.setTextSize(18);
    editor.setMinLines(3);
    editor.setGravity(Gravity.TOP);
    editor.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    editor.setContentDescription("Editable reply draft");
    root.addView(editor, new LinearLayout.LayoutParams(-1, -2));
    editor.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            draft.edit(s.toString());
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    if (state != null) editor.setText(state.getString("draft", ""));
    status = text(root, "Nothing is sent until you review and confirm.", 14);
    button(root, "Dictate reply", v -> dictate());
    button(root, "Read draft aloud", v -> readDraft());
    button(root, "Review & send", v -> review());
    button(root, "Cancel", v -> finish());
    poll.run();
  }

  private void holdReadouts() {
    nextRenew = System.currentTimeMillis() + 30000;
    Rules.prefs(this)
        .edit()
        .putLong("reply_composing_until", System.currentTimeMillis() + 120000)
        .apply();
  }

  @Override
  protected void onStart() {
    super.onStart();
    holdReadouts();
  }

  @Override
  protected void onStop() {
    if (tts != null) tts.stop();
    if (!dictating) {
      handler.removeCallbacks(poll);
      Rules.prefs(this).edit().putLong("reply_composing_until", 0).apply();
    }
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.removeCallbacks(poll);
    handler.post(poll);
  }

  private boolean allowed() {
    return calls != null
        && !calls.isBlocked()
        && !QuietHours.active(this)
        && Rules.prefs(this).getBoolean("enabled", true)
        && !getSystemService(KeyguardManager.class).isDeviceLocked();
  }

  private boolean check() {
    if (!allowed()) {
      status.setText(
          "Unlock your phone, end any call, and enable alerts outside quiet hours before"
              + " replying.");
      return false;
    }
    ReplyRegistry.Target t = ReplyRegistry.get(this, token);
    if (t == null || !t.ready) {
      status.setText(
          "This WhatsApp notification changed, was cleared, or expired. The draft has not been"
              + " sent.");
      return false;
    }
    return true;
  }

  private void dictate() {
    if (!check()) return;
    if (tts != null) tts.stop();
    Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    speech.putExtra(
        RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    speech.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate your WhatsApp reply");
    speech.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    try {
      dictating = true;
      startActivityForResult(speech, 501);
    } catch (ActivityNotFoundException | SecurityException e) {
      dictating = false;
      status.setText("Speech recognition is unavailable. You can type your reply instead.");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 501) return;
    dictating = false;
    if (result != RESULT_OK || data == null) return;
    if (!check()) return;
    ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
    if (results == null || results.isEmpty()) {
      status.setText("No speech recognized. Try again or type a reply.");
      return;
    }
    editor.setText(results.get(0));
    editor.setSelection(editor.length());
    status.setText("Check the transcription before sending.");
    readDraft();
  }

  private void readDraft() {
    if (!check()) return;
    android.media.AudioManager audio = getSystemService(android.media.AudioManager.class);
    android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
    if (audio.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL
        || nm.getCurrentInterruptionFilter()
            != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
      status.setText("Silent mode / Do Not Disturb is on. Review the draft on screen.");
      return;
    }
    if (speechReady)
      tts.speak(
          "Draft reply. " + editor.getText() + ". Review and confirm Send on screen.",
          TextToSpeech.QUEUE_FLUSH,
          null,
          "draft");
    else status.setText("Voice is not ready. You can review the draft on screen.");
  }

  private void review() {
    if (!check()) return;
    String snapshot = editor.getText().toString().trim();
    if (snapshot.isEmpty()) {
      editor.setError("Dictate or type a reply first.");
      return;
    }
    if (tts != null) tts.stop();
    ReplyRegistry.Target t = ReplyRegistry.get(this, token);
    if (t == null) return;
    new AlertDialog.Builder(this)
        .setTitle("Send text to " + t.label + "?")
        .setMessage(snapshot)
        .setNegativeButton("Keep editing", null)
        .setPositiveButton(
            "Send",
            (dialog, which) -> {
              if (!check() || !draft.confirm(snapshot) || !draft.beginSend()) return;
              try {
                ReplyRegistry.send(this, token, snapshot);
                status.setText("Reply handed to WhatsApp. Delivery is managed by WhatsApp.");
                editor.setEnabled(false);
              } catch (PendingIntent.CanceledException
                  | IllegalStateException
                  | SecurityException e) {
                draft.failed();
                status.setText(
                    "Could not hand off the reply. The notification may no longer support Reply."
                        + " Nothing was confirmed as sent.");
              }
            })
        .show();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    if (editor != null) state.putString("draft", editor.getText().toString());
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    Rules.prefs(this).edit().putLong("reply_composing_until", 0).apply();
    handler.removeCallbacksAndMessages(null);
    if (calls != null) calls.close();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }
}
