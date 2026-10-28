package com.notificationreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.widget.*;
import java.util.*;

/** Visible system voice input for a wake-activated command; never starts a microphone service. */
public class VoiceCommandActivity extends Activity {
  static Activity visibleActivity;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private String token;
  private boolean submitted, captureStarted;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    token = getIntent().getStringExtra("token");
    if (!HandsFreeService.commandUiValid(token)) {
      finish();
      return;
    }
    getSystemService(NotificationManager.class).cancel(52003);
    TextView label = new TextView(this);
    label.setText(
        "Voice command\n"
            + "Say Open followed by an app name.\n"
            + "Your notification readouts resume after this dialog.");
    label.setTextSize(20);
    label.setPadding(32, 64, 32, 32);
    setContentView(label);
    captureStarted = state != null && state.getBoolean("capture_started", false);
    String heard = getIntent().getStringExtra("heard");
    if (heard != null && !heard.isEmpty())
      showResult(new ArrayList<>(Collections.singletonList(heard)));
    else if (!captureStarted) {
      Intent speech =
          new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
              .putExtra(
                  RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
              .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
              .putExtra(
                  RecognizerIntent.EXTRA_PROMPT,
                  "Say Open Deepstash, Open Duolingo, Open Traya, or another allowed app.");
      handler.postDelayed(
          () -> {
            if (isFinishing()
                || !HandsFreeService.commandUiValid(token)
                || getSystemService(KeyguardManager.class).isDeviceLocked()) {
              cancel();
              return;
            }
            captureStarted = true;
            try {
              startActivityForResult(speech, 1);
            } catch (ActivityNotFoundException | SecurityException e) {
              new AlertDialog.Builder(this)
                  .setTitle("Voice input unavailable")
                  .setMessage(
                      "Check Android voice input settings. You can turn off Show command dialog to"
                          + " use the background recognizer instead.")
                  .setPositiveButton("Close", (d, w) -> cancel())
                  .setOnCancelListener(d -> cancel())
                  .show();
            }
          },
          500);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    visibleActivity = this;
  }

  @Override
  protected void onPause() {
    if (visibleActivity == this) visibleActivity = null;
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putBoolean("capture_started", captureStarted);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    if (code != 1) return;
    ArrayList<String> words =
        data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
    if (result != RESULT_OK || words == null || words.isEmpty()) {
      cancel();
      return;
    }
    // Retain only in this activity; never persist command transcripts.
    getIntent()
        .putExtra(
            "heard",
            VoiceCommand.selectCommand(
                words,
                Rules.prefs(this).getString("wake_phrase", "Hey Priority"),
                false,
                true,
                InstalledApps.speechAliases(this)));
    showResult(words);
  }

  private void showResult(ArrayList<String> words) {
    if (!HandsFreeService.commandUiValid(token)) {
      finish();
      return;
    }
    String selected =
        VoiceCommand.selectCommand(
            words,
            Rules.prefs(this).getString("wake_phrase", "Hey Priority"),
            false,
            true,
            InstalledApps.speechAliases(this));
    String after =
        VoiceCommand.afterWake(
            selected, Rules.prefs(this).getString("wake_phrase", "Hey Priority"));
    String target =
        AppSpeech.target(after != null ? after : selected, InstalledApps.speechAliases(this));
    String text =
        "Android heard:\n"
            + selected
            + (target == null
                ? "\n\nPassing this command to Priority Voice…"
                : "\n\nMatched: " + target + "\nOpening shortly…");
    new AlertDialog.Builder(this)
        .setTitle("Command heard")
        .setMessage(text)
        .setNegativeButton("Cancel", (d, w) -> cancel())
        .setOnCancelListener(d -> cancel())
        .show();
    handler.postDelayed(
        () -> {
          if (submitted || isFinishing()) return;
          submitted = true;
          boolean accepted = HandsFreeService.submitCommandUi(token, words);
          if (!accepted)
            Toast.makeText(
                    this,
                    "Command canceled or expired. Say the wake phrase again.",
                    Toast.LENGTH_LONG)
                .show();
          finish();
        },
        2000);
  }

  private void cancel() {
    if (!submitted) {
      submitted = true;
      HandsFreeService.submitCommandUi(token, null);
    }
    finish();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (visibleActivity == this) visibleActivity = null;
    if (isFinishing() && !submitted) HandsFreeService.submitCommandUi(token, null);
    super.onDestroy();
  }
}
